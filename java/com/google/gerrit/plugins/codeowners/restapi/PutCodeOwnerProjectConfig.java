// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.plugins.codeowners.restapi;

import static com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS;
import static com.google.gerrit.plugins.codeowners.backend.config.StatusConfig.KEY_DISABLED;
import static com.google.gerrit.plugins.codeowners.backend.config.StatusConfig.KEY_DISABLED_BRANCH;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerProjectConfigInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerProjectConfigInput;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfigValidator;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersProjectConfigFile;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

/**
 * REST endpoint that updates the code owner project configuration.
 *
 * <p>This REST endpoint handles {@code PUT /projects/<project-name>/code_owners.project_config}
 * requests.
 */
@Singleton
public class PutCodeOwnerProjectConfig
    implements RestModifyView<ProjectResource, CodeOwnerProjectConfigInput> {
  private final String pluginName;
  private final PermissionBackend permissionBackend;
  private final GitRepositoryManager repoManager;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final ProjectCache projectCache;
  private final CodeOwnersPluginConfigValidator codeOwnersPluginConfigValidator;
  private final CodeOwnerProjectConfigJson codeOwnerProjectConfigJson;

  @Inject
  public PutCodeOwnerProjectConfig(
      @PluginName String pluginName,
      PermissionBackend permissionBackend,
      GitRepositoryManager repoManager,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      ProjectCache projectCache,
      CodeOwnersPluginConfigValidator codeOwnersPluginConfigValidator,
      CodeOwnerProjectConfigJson codeOwnerProjectConfigJson) {
    this.pluginName = pluginName;
    this.permissionBackend = permissionBackend;
    this.repoManager = repoManager;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectCache = projectCache;
    this.codeOwnersPluginConfigValidator = codeOwnersPluginConfigValidator;
    this.codeOwnerProjectConfigJson = codeOwnerProjectConfigJson;
  }

  @Override
  public Response<CodeOwnerProjectConfigInfo> apply(
      ProjectResource projectResource, CodeOwnerProjectConfigInput input)
      throws RestApiException, PermissionBackendException, IOException, ConfigInvalidException {
    // This REST endpoint requires the caller to be a project owner.
    permissionBackend
        .currentUser()
        .project(projectResource.getNameKey())
        .check(ProjectPermission.WRITE_CONFIG);

    try (Repository repo = repoManager.openRepository(projectResource.getNameKey());
        MetaDataUpdate metaDataUpdate =
            metaDataUpdateFactory.get().create(projectResource.getNameKey())) {
      metaDataUpdate.setMessage(String.format("Update %s configuration", pluginName));

      CodeOwnersProjectConfigFile codeOwnersProjectConfigFile = new CodeOwnersProjectConfigFile();
      codeOwnersProjectConfigFile.load(projectResource.getNameKey(), repo);
      Config codeOwnersConfig = codeOwnersProjectConfigFile.getConfig();

      if (input.disabled != null) {
        codeOwnersConfig.setBoolean(
            SECTION_CODE_OWNERS, /* subsection= */ null, KEY_DISABLED, input.disabled);
      }

      if (input.disabledBranches != null) {
        codeOwnersConfig.setStringList(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            KEY_DISABLED_BRANCH,
            input.disabledBranches);
      }

      validateConfig(projectResource.getProjectState(), codeOwnersConfig);

      codeOwnersProjectConfigFile.commit(metaDataUpdate);
      projectCache.evict(projectResource.getNameKey());
    }

    CodeOwnerProjectConfigInfo updatedCodeOwnerProjectConfigInfo =
        codeOwnerProjectConfigJson.format(projectResource);
    return Response.created(updatedCodeOwnerProjectConfigInfo);
  }

  private void validateConfig(ProjectState projectState, Config codeOwnersConfig)
      throws BadRequestException {
    ImmutableList<CommitValidationMessage> validationMessages =
        codeOwnersPluginConfigValidator.validateConfig(
            projectState, CodeOwnersProjectConfigFile.FILE_NAME, codeOwnersConfig);
    if (!validationMessages.isEmpty()) {
      StringBuilder exceptionMessage = new StringBuilder();
      exceptionMessage.append("invalid config:\n");
      validationMessages.forEach(
          validationMessage ->
              exceptionMessage.append(String.format("* %s\n", validationMessage.getMessage())));
      throw new BadRequestException(exceptionMessage.toString());
    }
  }
}
