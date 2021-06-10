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

package com.google.gerrit.plugins.codeowners.acceptance.api;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerBranchConfigInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackendId;
import com.google.gerrit.plugins.codeowners.backend.FallbackCodeOwners;
import com.google.gerrit.plugins.codeowners.backend.config.BackendConfig;
import com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig;
import com.google.gerrit.plugins.codeowners.backend.config.OverrideApprovalConfig;
import com.google.gerrit.plugins.codeowners.backend.config.RequiredApprovalConfig;
import com.google.gerrit.plugins.codeowners.backend.config.StatusConfig;
import com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy;
import org.junit.Before;
import org.junit.Test;

/**
 * Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerBranchConfig} REST endpoint.
 *
 * <p>Further tests for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerBranchConfig} REST endpoint that require
 * using the REST API are implemented in {@link
 * com.google.gerrit.plugins.codeowners.acceptance.restapi.GetCodeOwnerBranchConfigRestIT}.
 */
public class GetCodeOwnerBranchConfigIT extends AbstractCodeOwnersIT {
  @Before
  public void setup() throws Exception {
    backendConfig = plugin.getSysInjector().getInstance(BackendConfig.class);
  }

  @Test
  public void cannotGetConfigForHiddenProject() throws Exception {
    ConfigInput configInput = new ConfigInput();
    configInput.state = ProjectState.HIDDEN;
    gApi.projects().name(project.get()).config(configInput);

    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class,
            () -> projectCodeOwnersApiFactory.project(project).branch("master").getConfig());
    assertThat(exception).hasMessageThat().isEqualTo("project state HIDDEN does not permit read");
  }

  @Test
  public void getDefaultConfig() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    CodeOwnerBranchConfigInfo codeOwnerBranchConfigInfo =
        projectCodeOwnersApiFactory.project(project).branch("master").getConfig();
    assertThat(codeOwnerBranchConfigInfo.general.fileExtension).isNull();
    assertThat(codeOwnerBranchConfigInfo.general.mergeCommitStrategy)
        .isEqualTo(MergeCommitStrategy.ALL_CHANGED_FILES);
    assertThat(codeOwnerBranchConfigInfo.general.fallbackCodeOwners)
        .isEqualTo(FallbackCodeOwners.NONE);
    assertThat(codeOwnerBranchConfigInfo.general.implicitApprovals).isNull();
    assertThat(codeOwnerBranchConfigInfo.general.overrideInfoUrl).isNull();
    assertThat(codeOwnerBranchConfigInfo.disabled).isNull();
    assertThat(codeOwnerBranchConfigInfo.backendId)
        .isEqualTo(CodeOwnerBackendId.getBackendId(backendConfig.getDefaultBackend().getClass()));
    assertThat(codeOwnerBranchConfigInfo.requiredApproval.label)
        .isEqualTo(RequiredApprovalConfig.DEFAULT_LABEL);
    assertThat(codeOwnerBranchConfigInfo.requiredApproval.value)
        .isEqualTo(RequiredApprovalConfig.DEFAULT_VALUE);
    assertThat(codeOwnerBranchConfigInfo.overrideApproval).isNull();
  }

  @Test
  public void getConfigWithConfiguredFileExtension() throws Exception {
    configureFileExtension(project, "foo");
    CodeOwnerBranchConfigInfo codeOwnerBranchConfigInfo =
        projectCodeOwnersApiFactory.project(project).branch("master").getConfig();
    assertThat(codeOwnerBranchConfigInfo.general.fileExtension).isEqualTo("foo");
  }

  @Test
  public void getConfigWithConfiguredOverrideInfoUrl() throws Exception {
    configureOverrideInfoUrl(project, "http://foo.example.com");
    CodeOwnerBranchConfigInfo codeOwnerBranchConfigInfo =
        projectCodeOwnersApiFactory.project(project).branch("master").getConfig();
    assertThat(codeOwnerBranchConfigInfo.general.overrideInfoUrl)
        .isEqualTo("http://foo.example.com");
  }

  @Test
  public void getConfigWithConfiguredMergeCommitStrategy() throws Exception {
    configureMergeCommitStrategy(project, MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION);
    CodeOwnerBranchConfigInfo codeOwnerBranchConfigInfo =
        projectCodeOwnersApiFactory.project(project).branch("master").getConfig();
    assertThat(codeOwnerBranchConfigInfo.general.mergeCommitStrategy)
        .isEqualTo(MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION);
  }

  @Test
  public void getConfigWithConfiguredFallbackCodeOwners() throws Exception {
    configureFallbackCodeOwners(project, FallbackCodeOwners.ALL_USERS);
    CodeOwnerBranchConfigInfo codeOwnerBranchConfigInfo =
        projectCodeOwnersApiFactory.project(project).branch("master").getConfig();
    assertThat(codeOwnerBranchConfigInfo.general.fallbackCodeOwners)
        .isEqualTo(FallbackCodeOwners.ALL_USERS);
  }

  @Test
  public void getConfigForBranchOfDisabledProject() throws Exception {
    disableCodeOwnersForProject(project);
    CodeOwnerBranchConfigInfo codeOwnerBranchConfigInfo =
        projectCodeOwnersApiFactory.project(project).branch("master").getConfig();
    assertThat(codeOwnerBranchConfigInfo.disabled).isTrue();
    assertThat(codeOwnerBranchConfigInfo.general).isNull();
    assertThat(codeOwnerBranchConfigInfo.backendId).isNull();
    assertThat(codeOwnerBranchConfigInfo.requiredApproval).isNull();
    assertThat(codeOwnerBranchConfigInfo.overrideApproval).isNull();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabled", value = "true")
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "INVALID")
  public void getConfigForBranchOfDisabledProject_invalidPluginConfig() throws Exception {
    CodeOwnerBranchConfigInfo codeOwnerBranchConfigInfo =
        projectCodeOwnersApiFactory.project(project).branch("master").getConfig();
    assertThat(codeOwnerBranchConfigInfo.disabled).isTrue();
    assertThat(codeOwnerBranchConfigInfo.general).isNull();
    assertThat(codeOwnerBranchConfigInfo.backendId).isNull();
    assertThat(codeOwnerBranchConfigInfo.requiredApproval).isNull();
    assertThat(codeOwnerBranchConfigInfo.overrideApproval).isNull();
  }

  @Test
  public void getConfigForDisabledBranch() throws Exception {
    configureDisabledBranch(project, "refs/heads/master");
    CodeOwnerBranchConfigInfo codeOwnerBranchConfigInfo =
        projectCodeOwnersApiFactory.project(project).branch("master").getConfig();
    assertThat(codeOwnerBranchConfigInfo.disabled).isTrue();
    assertThat(codeOwnerBranchConfigInfo.general).isNull();
    assertThat(codeOwnerBranchConfigInfo.backendId).isNull();
    assertThat(codeOwnerBranchConfigInfo.requiredApproval).isNull();
    assertThat(codeOwnerBranchConfigInfo.overrideApproval).isNull();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabledBranch", value = "refs/heads/master")
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "INVALID")
  public void getConfigForDisabledBranch_invalidPluginConfig() throws Exception {
    CodeOwnerBranchConfigInfo codeOwnerBranchConfigInfo =
        projectCodeOwnersApiFactory.project(project).branch("master").getConfig();
    assertThat(codeOwnerBranchConfigInfo.disabled).isTrue();
    assertThat(codeOwnerBranchConfigInfo.general).isNull();
    assertThat(codeOwnerBranchConfigInfo.backendId).isNull();
    assertThat(codeOwnerBranchConfigInfo.requiredApproval).isNull();
    assertThat(codeOwnerBranchConfigInfo.overrideApproval).isNull();
  }

  @Test
  public void getConfigWithConfiguredBackend() throws Exception {
    String otherBackendId = getOtherCodeOwnerBackend(backendConfig.getDefaultBackend());
    configureBackend(project, otherBackendId);
    CodeOwnerBranchConfigInfo codeOwnerBranchConfigInfo =
        projectCodeOwnersApiFactory.project(project).branch("master").getConfig();
    assertThat(codeOwnerBranchConfigInfo.backendId).isEqualTo(otherBackendId);
  }

  @Test
  public void getConfigWithConfiguredBackendForBranch() throws Exception {
    String otherBackendId = getOtherCodeOwnerBackend(backendConfig.getDefaultBackend());
    configureBackend(project, "refs/heads/master", otherBackendId);
    CodeOwnerBranchConfigInfo codeOwnerBranchConfigInfo =
        projectCodeOwnersApiFactory.project(project).branch("master").getConfig();
    assertThat(codeOwnerBranchConfigInfo.backendId).isEqualTo(otherBackendId);
  }

  @Test
  public void getConfigWithConfiguredRequiredApproval() throws Exception {
    configureRequiredApproval(project, "Code-Review+2");
    CodeOwnerBranchConfigInfo codeOwnerBranchConfigInfo =
        projectCodeOwnersApiFactory.project(project).branch("master").getConfig();
    assertThat(codeOwnerBranchConfigInfo.requiredApproval.label).isEqualTo("Code-Review");
    assertThat(codeOwnerBranchConfigInfo.requiredApproval.value).isEqualTo(2);
  }

  @Test
  public void getConfigWithConfiguredOverrideApproval() throws Exception {
    configureOverrideApproval(project, "Code-Review+2");
    CodeOwnerBranchConfigInfo codeOwnerBranchConfigInfo =
        projectCodeOwnersApiFactory.project(project).branch("master").getConfig();
    assertThat(codeOwnerBranchConfigInfo.overrideApproval).hasSize(1);
    assertThat(codeOwnerBranchConfigInfo.overrideApproval.get(0).label).isEqualTo("Code-Review");
    assertThat(codeOwnerBranchConfigInfo.overrideApproval.get(0).value).isEqualTo(2);
  }

  @Test
  public void getConfigWithMultipleConfiguredOverrideApproval() throws Exception {
    createOwnersOverrideLabel();
    setCodeOwnersConfig(
        project,
        /* subsection= */ null,
        OverrideApprovalConfig.KEY_OVERRIDE_APPROVAL,
        ImmutableList.of("Owners-Override+1", "Code-Review+2"));
    CodeOwnerBranchConfigInfo codeOwnerBranchConfigInfo =
        projectCodeOwnersApiFactory.project(project).branch("master").getConfig();
    assertThat(codeOwnerBranchConfigInfo.overrideApproval).hasSize(2);
    assertThat(codeOwnerBranchConfigInfo.overrideApproval.get(0).label).isEqualTo("Code-Review");
    assertThat(codeOwnerBranchConfigInfo.overrideApproval.get(0).value).isEqualTo(2);
    assertThat(codeOwnerBranchConfigInfo.overrideApproval.get(1).label)
        .isEqualTo("Owners-Override");
    assertThat(codeOwnerBranchConfigInfo.overrideApproval.get(1).value).isEqualTo(1);
  }

  @Test
  public void getConfigWithEnabledImplicitApprovals() throws Exception {
    configureImplicitApprovals(project);
    CodeOwnerBranchConfigInfo codeOwnerBranchConfigInfo =
        projectCodeOwnersApiFactory.project(project).branch("master").getConfig();
    assertThat(codeOwnerBranchConfigInfo.general.implicitApprovals).isTrue();
  }

  private void configureFileExtension(Project.NameKey project, String fileExtension)
      throws Exception {
    setCodeOwnersConfig(
        project, /* subsection= */ null, GeneralConfig.KEY_FILE_EXTENSION, fileExtension);
  }

  private void configureOverrideInfoUrl(Project.NameKey project, String overrideInfoUrl)
      throws Exception {
    setCodeOwnersConfig(
        project, /* subsection= */ null, GeneralConfig.KEY_OVERRIDE_INFO_URL, overrideInfoUrl);
  }

  private void configureMergeCommitStrategy(
      Project.NameKey project, MergeCommitStrategy mergeCommitStrategy) throws Exception {
    setCodeOwnersConfig(
        project,
        /* subsection= */ null,
        GeneralConfig.KEY_MERGE_COMMIT_STRATEGY,
        mergeCommitStrategy.name());
  }

  private void configureFallbackCodeOwners(
      Project.NameKey project, FallbackCodeOwners fallbackCodeOwners) throws Exception {
    setCodeOwnersConfig(
        project,
        /* subsection= */ null,
        GeneralConfig.KEY_FALLBACK_CODE_OWNERS,
        fallbackCodeOwners.name());
  }

  private void configureDisabledBranch(Project.NameKey project, String disabledBranch)
      throws Exception {
    setCodeOwnersConfig(
        project, /* subsection= */ null, StatusConfig.KEY_DISABLED_BRANCH, disabledBranch);
  }

  private void configureBackend(Project.NameKey project, String backendName) throws Exception {
    configureBackend(project, /* branch= */ null, backendName);
  }

  private void configureBackend(
      Project.NameKey project, @Nullable String branch, String backendName) throws Exception {
    setCodeOwnersConfig(project, branch, BackendConfig.KEY_BACKEND, backendName);
  }

  private void configureRequiredApproval(Project.NameKey project, String requiredApproval)
      throws Exception {
    setCodeOwnersConfig(
        project,
        /* subsection= */ null,
        RequiredApprovalConfig.KEY_REQUIRED_APPROVAL,
        requiredApproval);
  }

  private void configureOverrideApproval(Project.NameKey project, String overrideApproval)
      throws Exception {
    setCodeOwnersConfig(
        project,
        /* subsection= */ null,
        OverrideApprovalConfig.KEY_OVERRIDE_APPROVAL,
        overrideApproval);
  }

  private void configureImplicitApprovals(Project.NameKey project) throws Exception {
    setCodeOwnersConfig(
        project, /* subsection= */ null, GeneralConfig.KEY_ENABLE_IMPLICIT_APPROVALS, "true");
  }

  /** Returns the ID of a code owner backend that is not the given backend. */
  private String getOtherCodeOwnerBackend(CodeOwnerBackend codeOwnerBackend) {
    for (CodeOwnerBackendId codeOwnerBackendId : CodeOwnerBackendId.values()) {
      if (!codeOwnerBackendId.getCodeOwnerBackendClass().equals(codeOwnerBackend.getClass())) {
        return codeOwnerBackendId.getBackendId();
      }
    }
    throw new IllegalStateException(
        String.format("couldn't find other backend than %s", codeOwnerBackend.getClass()));
  }
}
