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
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.api.BackendInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerBranchConfigInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerProjectConfigInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnersStatusInfo;
import com.google.gerrit.plugins.codeowners.api.GeneralInfo;
import com.google.gerrit.plugins.codeowners.api.RequiredApprovalInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackendId;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.RequiredApproval;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.BranchResource;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.ListBranches;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

@Singleton
public class CodeOwnerProjectConfigJson {
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final Provider<ListBranches> listBranches;

  @Inject
  CodeOwnerProjectConfigJson(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      Provider<ListBranches> listBranches) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.listBranches = listBranches;
  }

  CodeOwnerProjectConfigInfo format(ProjectResource projectResource)
      throws RestApiException, PermissionBackendException, IOException {
    CodeOwnerProjectConfigInfo info = new CodeOwnerProjectConfigInfo();
    info.status = formatStatusInfo(projectResource);

    if (codeOwnersPluginConfiguration.isDisabled(projectResource.getNameKey())) {
      return info;
    }

    info.general = formatGeneralInfo(projectResource.getNameKey());
    info.backend = formatBackendInfo(projectResource);
    info.requiredApproval = formatRequiredApprovalInfo(projectResource.getNameKey());
    info.overrideApproval = formatOverrideApprovalInfo(projectResource.getNameKey());

    return info;
  }

  CodeOwnerBranchConfigInfo format(BranchResource branchResource) {
    CodeOwnerBranchConfigInfo info = new CodeOwnerBranchConfigInfo();

    boolean disabled = codeOwnersPluginConfiguration.isDisabled(branchResource.getBranchKey());
    info.disabled = disabled ? disabled : null;

    if (disabled) {
      return info;
    }

    info.general = formatGeneralInfo(branchResource.getNameKey());
    info.backendId =
        CodeOwnerBackendId.getBackendId(
            codeOwnersPluginConfiguration.getBackend(branchResource.getBranchKey()).getClass());
    info.requiredApproval = formatRequiredApprovalInfo(branchResource.getNameKey());
    info.overrideApproval = formatOverrideApprovalInfo(branchResource.getNameKey());

    return info;
  }

  private GeneralInfo formatGeneralInfo(Project.NameKey projectName) {
    GeneralInfo generalInfo = new GeneralInfo();
    generalInfo.fileExtension =
        codeOwnersPluginConfiguration.getFileExtension(projectName).orElse(null);
    generalInfo.mergeCommitStrategy =
        codeOwnersPluginConfiguration.getMergeCommitStrategy(projectName);
    generalInfo.implicitApprovals =
        codeOwnersPluginConfiguration.areImplicitApprovalsEnabled(projectName) ? true : null;
    generalInfo.overrideInfoUrl =
        codeOwnersPluginConfiguration.getOverrideInfoUrl(projectName).orElse(null);
    generalInfo.fallbackCodeOwners =
        codeOwnersPluginConfiguration.getFallbackCodeOwners(projectName);
    return generalInfo;
  }

  @VisibleForTesting
  CodeOwnersStatusInfo formatStatusInfo(ProjectResource projectResource)
      throws RestApiException, PermissionBackendException, IOException {
    CodeOwnersStatusInfo info = new CodeOwnersStatusInfo();
    info.disabled =
        codeOwnersPluginConfiguration.isDisabled(projectResource.getNameKey()) ? true : null;

    if (info.disabled == null) {
      ImmutableList<BranchNameKey> disabledBranches = getDisabledBranches(projectResource);
      if (!disabledBranches.isEmpty()) {
        info.disabledBranches =
            disabledBranches.stream().map(BranchNameKey::branch).collect(toImmutableList());
      }
    }
    return info;
  }

  @VisibleForTesting
  BackendInfo formatBackendInfo(ProjectResource projectResource)
      throws RestApiException, PermissionBackendException, IOException {
    BackendInfo info = new BackendInfo();
    info.id =
        CodeOwnerBackendId.getBackendId(
            codeOwnersPluginConfiguration.getBackend(projectResource.getNameKey()).getClass());

    ImmutableMap<String, String> idsByBranch =
        getBackendIdsPerBranch(projectResource).entrySet().stream()
            .filter(e -> !e.getValue().equals(info.id))
            .collect(toImmutableMap(e -> e.getKey().branch(), Map.Entry::getValue));
    info.idsByBranch = !idsByBranch.isEmpty() ? idsByBranch : null;

    return info;
  }

  private RequiredApprovalInfo formatRequiredApprovalInfo(Project.NameKey projectName) {
    return formatRequiredApproval(codeOwnersPluginConfiguration.getRequiredApproval(projectName));
  }

  @VisibleForTesting
  @Nullable
  ImmutableList<RequiredApprovalInfo> formatOverrideApprovalInfo(Project.NameKey projectName) {
    ImmutableList<RequiredApprovalInfo> overrideApprovalInfos =
        codeOwnersPluginConfiguration.getOverrideApproval(projectName).stream()
            .sorted(comparing(requiredApproval -> requiredApproval.toString()))
            .map(CodeOwnerProjectConfigJson::formatRequiredApproval)
            .collect(toImmutableList());
    return overrideApprovalInfos.isEmpty() ? null : overrideApprovalInfos;
  }

  @VisibleForTesting
  static RequiredApprovalInfo formatRequiredApproval(RequiredApproval requiredApproval) {
    requireNonNull(requiredApproval, "requiredApproval");

    RequiredApprovalInfo info = new RequiredApprovalInfo();
    info.label = requiredApproval.labelType().getName();
    info.value = requiredApproval.value();
    return info;
  }

  private ImmutableList<BranchNameKey> getDisabledBranches(ProjectResource projectResource)
      throws RestApiException, PermissionBackendException, IOException {
    return branches(projectResource)
        .filter(codeOwnersPluginConfiguration::isDisabled)
        .collect(toImmutableList());
  }

  private ImmutableMap<BranchNameKey, String> getBackendIdsPerBranch(
      ProjectResource projectResource)
      throws RestApiException, PermissionBackendException, IOException {
    return branches(projectResource)
        .collect(
            toImmutableMap(
                Function.identity(),
                branchNameKey ->
                    CodeOwnerBackendId.getBackendId(
                        codeOwnersPluginConfiguration.getBackend(branchNameKey).getClass())));
  }

  private Stream<BranchNameKey> branches(ProjectResource projectResource)
      throws RestApiException, IOException, PermissionBackendException {
    return listBranches.get().apply(projectResource).value().stream()
        .filter(branchInfo -> !"HEAD".equals(branchInfo.ref))
        .map(branchInfo -> BranchNameKey.create(projectResource.getNameKey(), branchInfo.ref));
  }
}
