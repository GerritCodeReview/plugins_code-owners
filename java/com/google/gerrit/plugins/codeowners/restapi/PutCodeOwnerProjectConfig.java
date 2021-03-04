// Copyright (C) 2021 The Android Open Source Project
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
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_ENABLE_IMPLICIT_APPROVALS;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_ENABLE_VALIDATION_ON_SUBMIT;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_EXEMPTED_USER;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_EXEMPT_PURE_REVERTS;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_FALLBACK_CODE_OWNERS;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_FILE_EXTENSION;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_GLOBAL_CODE_OWNER;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_MAX_PATHS_IN_CHANGE_MESSAGES;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_MERGE_COMMIT_STRATEGY;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_OVERRIDE_INFO_URL;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_READ_ONLY;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_REJECT_NON_RESOLVABLE_IMPORTS;
import static com.google.gerrit.plugins.codeowners.backend.config.OverrideApprovalConfig.KEY_OVERRIDE_APPROVAL;
import static com.google.gerrit.plugins.codeowners.backend.config.RequiredApprovalConfig.KEY_REQUIRED_APPROVAL;
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

      if (input.fileExtension != null) {
        codeOwnersConfig.setString(
            SECTION_CODE_OWNERS, /* subsection= */ null, KEY_FILE_EXTENSION, input.fileExtension);
      }

      if (input.requiredApproval != null) {
        if (input.requiredApproval.isEmpty()) {
          codeOwnersConfig.unset(
              SECTION_CODE_OWNERS, /* subsection= */ null, KEY_REQUIRED_APPROVAL);
        } else {
          codeOwnersConfig.setString(
              SECTION_CODE_OWNERS,
              /* subsection= */ null,
              KEY_REQUIRED_APPROVAL,
              input.requiredApproval);
        }
      }

      if (input.overrideApprovals != null) {
        codeOwnersConfig.setStringList(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            KEY_OVERRIDE_APPROVAL,
            input.overrideApprovals);
      }

      if (input.fallbackCodeOwners != null) {
        codeOwnersConfig.setEnum(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            KEY_FALLBACK_CODE_OWNERS,
            input.fallbackCodeOwners);
      }

      if (input.globalCodeOwners != null) {
        codeOwnersConfig.setStringList(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            KEY_GLOBAL_CODE_OWNER,
            input.globalCodeOwners);
      }

      if (input.exemptedUsers != null) {
        codeOwnersConfig.setStringList(
            SECTION_CODE_OWNERS, /* subsection= */ null, KEY_EXEMPTED_USER, input.exemptedUsers);
      }

      if (input.mergeCommitStrategy != null) {
        codeOwnersConfig.setEnum(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            KEY_MERGE_COMMIT_STRATEGY,
            input.mergeCommitStrategy);
      }

      if (input.implicitApprovals != null) {
        codeOwnersConfig.setBoolean(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            KEY_ENABLE_IMPLICIT_APPROVALS,
            input.implicitApprovals);
      }

      if (input.overrideInfoUrl != null) {
        codeOwnersConfig.setString(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            KEY_OVERRIDE_INFO_URL,
            input.overrideInfoUrl);
      }

      if (input.readOnly != null) {
        codeOwnersConfig.setBoolean(
            SECTION_CODE_OWNERS, /* subsection= */ null, KEY_READ_ONLY, input.readOnly);
      }

      if (input.exemptPureReverts != null) {
        codeOwnersConfig.setBoolean(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            KEY_EXEMPT_PURE_REVERTS,
            input.exemptPureReverts);
      }

      if (input.enableValidationOnCommitReceived != null) {
        codeOwnersConfig.setEnum(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED,
            input.enableValidationOnCommitReceived);
      }

      if (input.enableValidationOnSubmit != null) {
        codeOwnersConfig.setEnum(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            KEY_ENABLE_VALIDATION_ON_SUBMIT,
            input.enableValidationOnSubmit);
      }

      if (input.rejectNonResolvableCodeOwners != null) {
        codeOwnersConfig.setBoolean(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS,
            input.rejectNonResolvableCodeOwners);
      }

      if (input.rejectNonResolvableImports != null) {
        codeOwnersConfig.setBoolean(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            KEY_REJECT_NON_RESOLVABLE_IMPORTS,
            input.rejectNonResolvableImports);
      }

      if (input.maxPathsInChangeMessages != null) {
        codeOwnersConfig.setInt(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            KEY_MAX_PATHS_IN_CHANGE_MESSAGES,
            input.maxPathsInChangeMessages);
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
