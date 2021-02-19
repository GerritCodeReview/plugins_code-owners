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
import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.plugins.codeowners.testing.RequiredApprovalSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.git.ObjectIds;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackendId;
import com.google.gerrit.plugins.codeowners.backend.FallbackCodeOwners;
import com.google.gerrit.plugins.codeowners.backend.config.BackendConfig;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig;
import com.google.gerrit.plugins.codeowners.backend.config.OverrideApprovalConfig;
import com.google.gerrit.plugins.codeowners.backend.config.RequiredApproval;
import com.google.gerrit.plugins.codeowners.backend.config.RequiredApprovalConfig;
import com.google.gerrit.plugins.codeowners.backend.config.StatusConfig;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@code com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfigValidator}.
 *
 * <p>Unit tests for {@code
 * com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfigValidator} are contained in
 * {@code com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfigValidatorTest}.
 */
public class CodeOwnersPluginConfigValidatorIT extends AbstractCodeOwnersIT {
  private CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnersPluginConfiguration =
        plugin.getSysInjector().getInstance(CodeOwnersPluginConfiguration.class);
  }

  @Test
  public void cannotUploadNonParseableConfig() throws Exception {
    fetchRefsMetaConfig();

    setCodeOwnersConfig("INVALID");

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus())
        .isEqualTo(Status.REJECTED_OTHER_REASON);
    assertThat(r.getMessages())
        .contains(
            String.format(
                "Invalid config file code-owners.config in project %s in branch %s",
                project, RefNames.REFS_CONFIG));
    assertThat(r.getMessages()).contains("Invalid line in config file");
  }

  @Test
  public void setDisabledForProject() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setBoolean(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        StatusConfig.KEY_DISABLED,
        /* value= */ true);
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus()).isEqualTo(Status.OK);
    assertThat(codeOwnersPluginConfiguration.isDisabled(project)).isTrue();
  }

  @Test
  public void configureDisabledBranch() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        StatusConfig.KEY_DISABLED_BRANCH,
        "refs/heads/master");
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus()).isEqualTo(Status.OK);
    assertThat(codeOwnersPluginConfiguration.isDisabled(BranchNameKey.create(project, "master")))
        .isTrue();
  }

  @Test
  public void cannotSetInvalidValueForDisabledForProject() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        StatusConfig.KEY_DISABLED,
        "INVALID");
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus())
        .isEqualTo(Status.REJECTED_OTHER_REASON);
    assertThat(r.getMessages())
        .contains(
            "Disabled value 'INVALID' that is configured in code-owners.config"
                + " (parameter codeOwners.disabled) is invalid.");
  }

  @Test
  public void cannotConfigureInvalidDisabledBranch() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        StatusConfig.KEY_DISABLED_BRANCH,
        "^refs/heads/[");
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus())
        .isEqualTo(Status.REJECTED_OTHER_REASON);
    assertThat(r.getMessages())
        .contains(
            "Disabled branch '^refs/heads/[' that is configured in code-owners.config (parameter"
                + " codeOwners.disabledBranch) is invalid: Unclosed character class");
  }

  @Test
  public void configureBackendForProject() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        BackendConfig.KEY_BACKEND,
        CodeOwnerBackendId.PROTO.getBackendId());
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus()).isEqualTo(Status.OK);
    assertThat(codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")))
        .isInstanceOf(ProtoBackend.class);
  }

  @Test
  public void configureBackendForBranch() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        "master",
        BackendConfig.KEY_BACKEND,
        CodeOwnerBackendId.PROTO.getBackendId());
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus()).isEqualTo(Status.OK);
    assertThat(codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")))
        .isInstanceOf(ProtoBackend.class);
  }

  @Test
  public void cannotConfigureInvalidBackendForProject() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        BackendConfig.KEY_BACKEND,
        "INVALID");
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus())
        .isEqualTo(Status.REJECTED_OTHER_REASON);
    assertThat(r.getMessages())
        .contains(
            "Code owner backend 'INVALID' that is configured in code-owners.config"
                + " (parameter codeOwners.backend) not found.");
  }

  @Test
  public void cannotConfigureInvalidBackendForBranch() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        "master",
        BackendConfig.KEY_BACKEND,
        "INVALID");
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus())
        .isEqualTo(Status.REJECTED_OTHER_REASON);
    assertThat(r.getMessages())
        .contains(
            "Code owner backend 'INVALID' that is configured in code-owners.config"
                + " (parameter codeOwners.master.backend) not found.");
  }

  @Test
  public void configureRequiredApproval() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        RequiredApprovalConfig.KEY_REQUIRED_APPROVAL,
        "Code-Review+2");
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus()).isEqualTo(Status.OK);
    RequiredApproval requiredApproval = codeOwnersPluginConfiguration.getRequiredApproval(project);
    assertThat(requiredApproval).hasLabelNameThat().isEqualTo("Code-Review");
    assertThat(requiredApproval).hasValueThat().isEqualTo(2);
  }

  @Test
  public void cannotConfigureInvalidRequiredApproval() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        RequiredApprovalConfig.KEY_REQUIRED_APPROVAL,
        "INVALID");
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus())
        .isEqualTo(Status.REJECTED_OTHER_REASON);
    assertThat(r.getMessages())
        .contains(
            String.format(
                "Required approval 'INVALID' that is configured in code-owners.config (parameter"
                    + " codeOwners.%s) is invalid: Invalid format, expected"
                    + " '<label-name>+<label-value>'.",
                RequiredApprovalConfig.KEY_REQUIRED_APPROVAL));
  }

  @Test
  public void allRequiredApprovalsAreValidated() throws Exception {
    fetchRefsMetaConfig();

    ImmutableList<String> invalidValues = ImmutableList.of("INVALID", "ALSO_INVALID");
    Config cfg = new Config();
    cfg.setStringList(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        RequiredApprovalConfig.KEY_REQUIRED_APPROVAL,
        invalidValues);
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus())
        .isEqualTo(Status.REJECTED_OTHER_REASON);
    for (String invalidValue : invalidValues) {
      assertThat(r.getMessages())
          .contains(
              String.format(
                  "Required approval '%s' that is configured in code-owners.config (parameter"
                      + " codeOwners.%s) is invalid: Invalid format, expected"
                      + " '<label-name>+<label-value>'.",
                  invalidValue, RequiredApprovalConfig.KEY_REQUIRED_APPROVAL));
    }
  }

  @Test
  public void configureOverrideApproval() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        OverrideApprovalConfig.KEY_OVERRIDE_APPROVAL,
        "Code-Review+2");
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus()).isEqualTo(Status.OK);
    ImmutableSet<RequiredApproval> overrideApproval =
        codeOwnersPluginConfiguration.getOverrideApproval(project);
    assertThat(overrideApproval).hasSize(1);
    assertThat(overrideApproval).element(0).hasLabelNameThat().isEqualTo("Code-Review");
    assertThat(overrideApproval).element(0).hasValueThat().isEqualTo(2);
  }

  @Test
  public void cannotConfigureInvalidOverrideApproval() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        OverrideApprovalConfig.KEY_OVERRIDE_APPROVAL,
        "INVALID");
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus())
        .isEqualTo(Status.REJECTED_OTHER_REASON);
    assertThat(r.getMessages())
        .contains(
            String.format(
                "Required approval 'INVALID' that is configured in code-owners.config (parameter"
                    + " codeOwners.%s) is invalid: Invalid format, expected"
                    + " '<label-name>+<label-value>'.",
                OverrideApprovalConfig.KEY_OVERRIDE_APPROVAL));
  }

  @Test
  public void allOverrideApprovalsAreValidated() throws Exception {
    fetchRefsMetaConfig();

    ImmutableList<String> invalidValues = ImmutableList.of("INVALID", "ALSO_INVALID");
    Config cfg = new Config();
    cfg.setStringList(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        OverrideApprovalConfig.KEY_OVERRIDE_APPROVAL,
        invalidValues);
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus())
        .isEqualTo(Status.REJECTED_OTHER_REASON);
    for (String invalidValue : invalidValues) {
      assertThat(r.getMessages())
          .contains(
              String.format(
                  "Required approval '%s' that is configured in code-owners.config (parameter"
                      + " codeOwners.%s) is invalid: Invalid format, expected"
                      + " '<label-name>+<label-value>'.",
                  invalidValue, OverrideApprovalConfig.KEY_OVERRIDE_APPROVAL));
    }
  }

  @Test
  public void defineAndConfigureOverrideLabelInSameCommit() throws Exception {
    fetchRefsMetaConfig();

    RevCommit head = getHead(testRepo.getRepository(), RefNames.REFS_CONFIG);
    RevObject blob = testRepo.get(head.getTree(), "project.config");
    byte[] data = testRepo.getRepository().open(blob).getCachedBytes(Integer.MAX_VALUE);
    String projectConfigText = RawParseUtils.decode(data);

    Config projectConfig = new Config();
    projectConfig.fromText(projectConfigText);
    String labelName = "Owners-Override";
    projectConfig.setString("label", labelName, "function", "NoOp");
    projectConfig.setStringList(
        "label", labelName, "value", ImmutableList.of("0 Not Override", "+1 Override"));

    Config codeOwnersConfig = new Config();
    codeOwnersConfig.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        OverrideApprovalConfig.KEY_OVERRIDE_APPROVAL,
        "Owners-Override+1");

    RevCommit commit =
        testRepo.update(
            RefNames.REFS_CONFIG,
            testRepo
                .commit()
                .parent(head)
                .message("Add test code owner config")
                .author(admin.newIdent())
                .committer(admin.newIdent())
                .add("code-owners.config", codeOwnersConfig.toText())
                .add("project.config", projectConfig.toText()));

    testRepo.reset(commit);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus()).isEqualTo(Status.OK);
    ImmutableSet<RequiredApproval> overrideApproval =
        codeOwnersPluginConfiguration.getOverrideApproval(project);
    assertThat(overrideApproval).hasSize(1);
    assertThat(overrideApproval).element(0).hasLabelNameThat().isEqualTo("Owners-Override");
    assertThat(overrideApproval).element(0).hasValueThat().isEqualTo(1);
  }

  @Test
  public void configureMergeCommitStrategy() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setEnum(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        GeneralConfig.KEY_MERGE_COMMIT_STRATEGY,
        MergeCommitStrategy.ALL_CHANGED_FILES);
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus()).isEqualTo(Status.OK);
    assertThat(codeOwnersPluginConfiguration.getMergeCommitStrategy(project))
        .isEqualTo(MergeCommitStrategy.ALL_CHANGED_FILES);
  }

  @Test
  public void cannotSetInvalidMergeCommitStrategy() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        GeneralConfig.KEY_MERGE_COMMIT_STRATEGY,
        "INVALID");
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus())
        .isEqualTo(Status.REJECTED_OTHER_REASON);
    assertThat(r.getMessages())
        .contains(
            "Merge commit strategy 'INVALID' that is configured in code-owners.config"
                + " (parameter codeOwners.mergeCommitStrategy) is invalid.");
  }

  @Test
  public void configureFallbackCodeOwners() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setEnum(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        GeneralConfig.KEY_FALLBACK_CODE_OWNERS,
        FallbackCodeOwners.ALL_USERS);
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus()).isEqualTo(Status.OK);
    assertThat(codeOwnersPluginConfiguration.getFallbackCodeOwners(project))
        .isEqualTo(FallbackCodeOwners.ALL_USERS);
  }

  @Test
  public void cannotSetInvalidFallbackCodeOwners() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        GeneralConfig.KEY_FALLBACK_CODE_OWNERS,
        "INVALID");
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus())
        .isEqualTo(Status.REJECTED_OTHER_REASON);
    assertThat(r.getMessages())
        .contains(
            "The value for fallback code owners 'INVALID' that is configured in code-owners.config"
                + " (parameter codeOwners.fallbackCodeOwners) is invalid.");
  }

  @Test
  public void configureMaxPathsInChangeMessages() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setInt(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        GeneralConfig.KEY_MAX_PATHS_IN_CHANGE_MESSAGES,
        50);
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus()).isEqualTo(Status.OK);
    assertThat(codeOwnersPluginConfiguration.getMaxPathsInChangeMessages(project)).isEqualTo(50);
  }

  @Test
  public void cannotSetInvalidMaxPathsInChangeMessages() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        GeneralConfig.KEY_MAX_PATHS_IN_CHANGE_MESSAGES,
        "INVALID");
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus())
        .isEqualTo(Status.REJECTED_OTHER_REASON);
    assertThat(r.getMessages())
        .contains(
            "The value for max paths in change messages 'INVALID' that is configured in"
                + " code-owners.config (parameter codeOwners.maxPathsInChangeMessages) is invalid.");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabledBranch", value = "refs/meta/config")
  public void validationDoesntFailOnRebaseChange_unrelatedChange() throws Exception {
    // Create two changes for refs/meta/config both with the same parent.
    GitUtil.fetch(testRepo, RefNames.REFS_CONFIG + ":config");
    testRepo.reset("config");
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "Change 1", "a.txt", "content");
    PushOneCommit.Result r = push.to("refs/for/" + RefNames.REFS_CONFIG);
    r.assertOkStatus();
    String changeId1 = r.getChangeId();

    testRepo.reset("config");
    push = pushFactory.create(admin.newIdent(), testRepo, "Change 2", "b.txt", "content");
    r = push.to("refs/for/" + RefNames.REFS_CONFIG);
    r.assertOkStatus();
    String changeId2 = r.getChangeId();

    // Approve and submit the first change
    approve(changeId1);
    gApi.changes().id(changeId1).current().submit();

    // Rebase the second change, throws an exception if the code owner plugin config validation
    // fails.
    gApi.changes().id(changeId2).rebase();

    // Second change should have 2 patch sets now.
    ChangeInfo changeInfo = gApi.changes().id(changeId2).get(CURRENT_REVISION);
    assertThat(changeInfo.revisions.get(changeInfo.currentRevision)._number).isEqualTo(2);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabledBranch", value = "refs/meta/config")
  public void validationDoesntFailOnRebaseChange_changeThatUpdatesTheCodeOwnersConfig()
      throws Exception {
    // Create two changes for refs/meta/config both with the same parent.
    GitUtil.fetch(testRepo, RefNames.REFS_CONFIG + ":config");
    testRepo.reset("config");
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "Change 1", "a,txt", "content");
    PushOneCommit.Result r = push.to("refs/for/" + RefNames.REFS_CONFIG);
    r.assertOkStatus();
    String changeId1 = r.getChangeId();

    testRepo.reset("config");
    Config cfg = new Config();
    cfg.setEnum(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        GeneralConfig.KEY_FALLBACK_CODE_OWNERS,
        FallbackCodeOwners.ALL_USERS);
    push =
        pushFactory.create(
            admin.newIdent(), testRepo, "Change 2", "code-owners.config", cfg.toText());
    r = push.to("refs/for/" + RefNames.REFS_CONFIG);
    r.assertOkStatus();
    String changeId2 = r.getChangeId();

    // Approve and submit the first change
    approve(changeId1);
    gApi.changes().id(changeId1).current().submit();

    // Rebase the second change, throws an exception if the code owner plugin config validation
    // fails.
    gApi.changes().id(changeId2).rebase();

    // Second change should have 2 patch sets now.
    ChangeInfo changeInfo = gApi.changes().id(changeId2).get(CURRENT_REVISION);
    assertThat(changeInfo.revisions.get(changeInfo.currentRevision)._number).isEqualTo(2);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabledBranch", value = "refs/meta/config")
  public void validationFailsOnRebaseChange_changeThatCreatesInvalidCodeOwnerConfig()
      throws Exception {
    // Create two changes for refs/meta/config both with the same parent.
    GitUtil.fetch(testRepo, RefNames.REFS_CONFIG + ":config");
    testRepo.reset("config");
    Config cfg = new Config();
    cfg.setEnum(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        GeneralConfig.KEY_FALLBACK_CODE_OWNERS,
        FallbackCodeOwners.NONE);
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(), testRepo, "Change 1", "code-owners.config", cfg.toText());
    PushOneCommit.Result r = push.to("refs/for/" + RefNames.REFS_CONFIG);
    r.assertOkStatus();
    String changeId1 = r.getChangeId();

    testRepo.reset("config");
    cfg = new Config();
    cfg.setEnum(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        GeneralConfig.KEY_FALLBACK_CODE_OWNERS,
        FallbackCodeOwners.ALL_USERS);
    push =
        pushFactory.create(
            admin.newIdent(), testRepo, "Change 2", "code-owners.config", cfg.toText());
    r = push.to("refs/for/" + RefNames.REFS_CONFIG);
    r.assertOkStatus();
    String changeId2 = r.getChangeId();

    // Approve and submit the first change
    approve(changeId1);
    gApi.changes().id(changeId1).current().submit();

    // Rebase the second change with allowing conflicts. This results in a code-owners.config that
    // contains conflict markers and hence is rejected as invalid.
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.allowConflicts = true;
    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(changeId2).rebase(rebaseInput));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            String.format(
                "Invalid config file code-owners.config in project %s in branch %s",
                project, RefNames.REFS_CONFIG));
  }

  @Test
  public void validatorForProjectConfigIsInvokedBeforeCodeOwnersConfigValidator() throws Exception {
    fetchRefsMetaConfig();
    Config cfg = new Config();
    cfg.setEnum(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        GeneralConfig.KEY_FALLBACK_CODE_OWNERS,
        FallbackCodeOwners.ALL_USERS);
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Change 1",
            ImmutableMap.of("code-owners.config", cfg.toText(), "project.config", "INVALID"));
    PushOneCommit.Result r = push.to("refs/for/" + RefNames.REFS_CONFIG);

    // The invalid project.config is rejected by the project config validator in Gerrit core before
    // CodeOwnersPluginConfigValidator is invoked (if CodeOwnersPluginConfigValidator would be
    // invoked first it would fail with a ConfigInvalidException and the message would be "internal
    // error").
    r.assertMessage(
        String.format(
            "commit %s: invalid project configuration",
            ObjectIds.abbreviateName(r.getCommit(), testRepo.getRevWalk().getObjectReader())));
  }

  private void fetchRefsMetaConfig() throws Exception {
    fetch(testRepo, RefNames.REFS_CONFIG + ":" + RefNames.REFS_CONFIG);
    testRepo.reset(RefNames.REFS_CONFIG);
  }

  private PushResult pushRefsMetaConfig() throws Exception {
    return pushHead(testRepo, RefNames.REFS_CONFIG);
  }

  private void setCodeOwnersConfig(Config codeOwnersConfig) throws Exception {
    setCodeOwnersConfig(codeOwnersConfig.toText());
  }

  private void setCodeOwnersConfig(String codeOwnersConfig) throws Exception {
    RevCommit head = getHead(testRepo.getRepository(), RefNames.REFS_CONFIG);
    RevCommit commit =
        testRepo.update(
            RefNames.REFS_CONFIG,
            testRepo
                .commit()
                .parent(head)
                .message("Add test code owner config")
                .author(admin.newIdent())
                .committer(admin.newIdent())
                .add("code-owners.config", codeOwnersConfig));

    testRepo.reset(commit);
  }
}
