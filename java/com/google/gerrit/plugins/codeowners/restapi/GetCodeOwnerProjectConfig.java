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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerProjectConfigInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackendId;
import com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.config.RequiredApproval;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.ListBranches;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

/**
 * REST endpoint that gets the code owner project configuration.
 *
 * <p>This REST endpoint handles {@code GET /projects/<project-name>/code_owners.project_config}
 * requests.
 */
@Singleton
public class GetCodeOwnerProjectConfig implements RestReadView<ProjectResource> {
  private final Provider<ListBranches> listBranches;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;

  @Inject
  public GetCodeOwnerProjectConfig(
      Provider<ListBranches> listBranches,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration) {
    this.listBranches = listBranches;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
  }

  @Override
  public Response<CodeOwnerProjectConfigInfo> apply(ProjectResource projectResource)
      throws RestApiException, PermissionBackendException, IOException {
    projectResource.getProjectState().checkStatePermitsRead();

    boolean isDisabled = codeOwnersPluginConfiguration.isDisabled(projectResource.getNameKey());
    ImmutableList<BranchNameKey> disabledBranches = getDisabledBranches(projectResource);

    String backendId =
        CodeOwnerBackendId.getBackendId(
            codeOwnersPluginConfiguration.getBackend(projectResource.getNameKey()).getClass());
    ImmutableMap<BranchNameKey, String> backendIdsPerBranch =
        getBackendIdsPerBranch(projectResource);

    RequiredApproval requiredApproval =
        codeOwnersPluginConfiguration.getRequiredApproval(projectResource.getNameKey());
    Optional<RequiredApproval> overrideApproval =
        codeOwnersPluginConfiguration.getOverrideApproval(projectResource.getNameKey());
    return Response.ok(
        CodeOwnerProjectConfigJson.format(
            isDisabled,
            disabledBranches,
            backendId,
            backendIdsPerBranch,
            requiredApproval,
            overrideApproval.orElse(null)));
  }

  private ImmutableList<BranchNameKey> getDisabledBranches(ProjectResource projectResource)
      throws RestApiException, PermissionBackendException, IOException {
    return listBranches.get().apply(projectResource).value().stream()
        .map(branchInfo -> BranchNameKey.create(projectResource.getNameKey(), branchInfo.ref))
        .filter(codeOwnersPluginConfiguration::isDisabled)
        .collect(toImmutableList());
  }

  private ImmutableMap<BranchNameKey, String> getBackendIdsPerBranch(
      ProjectResource projectResource)
      throws RestApiException, PermissionBackendException, IOException {
    return listBranches.get().apply(projectResource).value().stream()
        .map(branchInfo -> BranchNameKey.create(projectResource.getNameKey(), branchInfo.ref))
        .collect(
            toImmutableMap(
                Function.identity(),
                branchNameKey ->
                    CodeOwnerBackendId.getBackendId(
                        codeOwnersPluginConfiguration.getBackend(branchNameKey).getClass())));
  }
}
