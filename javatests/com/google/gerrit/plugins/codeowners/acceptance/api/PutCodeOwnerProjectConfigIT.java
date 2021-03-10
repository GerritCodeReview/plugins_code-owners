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

package com.google.gerrit.plugins.codeowners.acceptance.api;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.RequiredApprovalSubject.assertThat;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerProjectConfigInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerProjectConfigInput;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.FallbackCodeOwners;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig;
import com.google.gerrit.plugins.codeowners.backend.config.RequiredApproval;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerConfigValidationPolicy;
import com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.restapi.project.DeleteRef;
import com.google.inject.Inject;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.PutCodeOwnerProjectConfig} REST endpoint.
 *
 * <p>Further tests for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.PutCodeOwnerProjectConfig} REST endpoint that
 * require using the REST API are implemented in {@link
 * com.google.gerrit.plugins.codeowners.acceptance.restapi.PutCodeOwnerProjectConfigRestIT}.
 */
public class PutCodeOwnerProjectConfigIT extends AbstractCodeOwnersIT {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private DeleteRef deleteRef;

  private CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;

  @Before
  public void setup() throws Exception {
    codeOwnersPluginConfiguration =
        plugin.getSysInjector().getInstance(CodeOwnersPluginConfiguration.class);
  }

  @Test
  public void requiresCallerToBeProjectOwner() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    AuthException authException =
        assertThrows(
            AuthException.class,
            () ->
                projectCodeOwnersApiFactory
                    .project(project)
                    .updateConfig(new CodeOwnerProjectConfigInput()));
    assertThat(authException).hasMessageThat().isEqualTo("write refs/meta/config not permitted");
  }

  @Test
  public void disableAndReenableCodeOwnersFunctionality() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).isDisabled()).isFalse();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.disabled = true;
    CodeOwnerProjectConfigInfo updatedConfig =
        projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.status.disabled).isTrue();
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).isDisabled()).isTrue();

    input.disabled = false;
    updatedConfig = projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.status.disabled).isNull();
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).isDisabled()).isFalse();
  }

  @Test
  public void setDisabledBranches() throws Exception {
    createBranch(BranchNameKey.create(project, "foo"));
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).isDisabled("master"))
        .isFalse();
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).isDisabled("foo")).isFalse();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.disabledBranches = ImmutableList.of("refs/heads/master", "refs/heads/foo");
    CodeOwnerProjectConfigInfo updatedConfig =
        projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.status.disabledBranches)
        .containsExactly("refs/heads/master", "refs/heads/foo");
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).isDisabled("master"))
        .isTrue();
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).isDisabled("foo")).isTrue();

    input = new CodeOwnerProjectConfigInput();
    input.disabledBranches = ImmutableList.of();
    updatedConfig = projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.status.disabledBranches).isNull();
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).isDisabled("master"))
        .isFalse();
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).isDisabled("foo")).isFalse();
  }

  @Test
  public void setDisabledBranchesRegEx() throws Exception {
    createBranch(BranchNameKey.create(project, "foo"));
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).isDisabled("master"))
        .isFalse();
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).isDisabled("foo")).isFalse();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.disabledBranches = ImmutableList.of("refs/heads/*");
    CodeOwnerProjectConfigInfo updatedConfig =
        projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.status.disabledBranches)
        .containsExactly("refs/heads/master", "refs/heads/foo");
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).isDisabled("master"))
        .isTrue();
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).isDisabled("foo")).isTrue();
  }

  @Test
  public void setDisabledBranchThatDoesntExist() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).isDisabled("foo")).isFalse();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.disabledBranches = ImmutableList.of("refs/heads/foo");
    CodeOwnerProjectConfigInfo updatedConfig =
        projectCodeOwnersApiFactory.project(project).updateConfig(input);
    // status.disabledBranches does only contain existing branches
    assertThat(updatedConfig.status.disabledBranches).isNull();
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).isDisabled("foo")).isTrue();

    createBranch(BranchNameKey.create(project, "foo"));
    assertThat(projectCodeOwnersApiFactory.project(project).getConfig().status.disabledBranches)
        .containsExactly("refs/heads/foo");
  }

  @Test
  public void cannotSetInvalidDisabledBranch() throws Exception {
    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.disabledBranches = ImmutableList.of("^refs/heads/[");
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> projectCodeOwnersApiFactory.project(project).updateConfig(input));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "invalid config:\n"
                + "* Disabled branch '^refs/heads/[' that is configured in code-owners.config"
                + " (parameter codeOwners.disabledBranch) is invalid: Unclosed character class");
  }

  @Test
  public void setFileExtension() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getFileExtension())
        .isEmpty();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.fileExtension = "foo";
    CodeOwnerProjectConfigInfo updatedConfig =
        projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.general.fileExtension).isEqualTo("foo");
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getFileExtension())
        .value()
        .isEqualTo("foo");

    input.fileExtension = "";
    updatedConfig = projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.general.fileExtension).isNull();
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getFileExtension())
        .isEmpty();
  }

  @Test
  public void setRequiredApproval() throws Exception {
    RequiredApproval requiredApproval =
        codeOwnersPluginConfiguration.getProjectConfig(project).getRequiredApproval();
    assertThat(requiredApproval).hasLabelNameThat().isEqualTo("Code-Review");
    assertThat(requiredApproval).hasValueThat().isEqualTo(1);

    String otherLabel = "Other";
    LabelDefinitionInput labelInput = new LabelDefinitionInput();
    labelInput.values = ImmutableMap.of("+2", "Approval", "+1", "LGTM", " 0", "No Vote");
    gApi.projects().name(project.get()).label(otherLabel).create(labelInput).get();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.requiredApproval = otherLabel + "+2";
    CodeOwnerProjectConfigInfo updatedConfig =
        projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.requiredApproval.label).isEqualTo(otherLabel);
    assertThat(updatedConfig.requiredApproval.value).isEqualTo(2);
    requiredApproval =
        codeOwnersPluginConfiguration.getProjectConfig(project).getRequiredApproval();
    assertThat(requiredApproval).hasLabelNameThat().isEqualTo(otherLabel);
    assertThat(requiredApproval).hasValueThat().isEqualTo(2);

    input.requiredApproval = "";
    updatedConfig = projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.requiredApproval.label).isEqualTo("Code-Review");
    assertThat(updatedConfig.requiredApproval.value).isEqualTo(1);
    requiredApproval =
        codeOwnersPluginConfiguration.getProjectConfig(project).getRequiredApproval();
    assertThat(requiredApproval).hasLabelNameThat().isEqualTo("Code-Review");
    assertThat(requiredApproval).hasValueThat().isEqualTo(1);
  }

  @Test
  public void setInvalidRequiredApproval() throws Exception {
    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.requiredApproval = "Non-Existing-Label+2";
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> projectCodeOwnersApiFactory.project(project).updateConfig(input));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            String.format(
                "invalid config:\n"
                    + "* Required approval 'Non-Existing-Label+2' that is configured in"
                    + " code-owners.config (parameter codeOwners.requiredApproval) is invalid:"
                    + " Label Non-Existing-Label doesn't exist for project %s.",
                project));
  }

  @Test
  public void setOverrideApproval() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getOverrideApproval())
        .isEmpty();

    String overrideLabel1 = "Bypass-Owners";
    String overrideLabel2 = "Owners-Override";
    createOwnersOverrideLabel(overrideLabel1);
    createOwnersOverrideLabel(overrideLabel2);

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.overrideApprovals = ImmutableList.of(overrideLabel1 + "+1", overrideLabel2 + "+1");
    CodeOwnerProjectConfigInfo updatedConfig =
        projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.overrideApproval).hasSize(2);
    assertThat(updatedConfig.overrideApproval.get(0).label).isEqualTo(overrideLabel1);
    assertThat(updatedConfig.overrideApproval.get(0).value).isEqualTo(1);
    assertThat(updatedConfig.overrideApproval.get(1).label).isEqualTo(overrideLabel2);
    assertThat(updatedConfig.overrideApproval.get(1).value).isEqualTo(1);
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getOverrideApproval())
        .hasSize(2);

    input.overrideApprovals = ImmutableList.of();
    updatedConfig = projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.overrideApproval).isNull();
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getOverrideApproval())
        .isEmpty();
  }

  @Test
  public void setInvalidOverrideApproval() throws Exception {
    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.overrideApprovals = ImmutableList.of("Non-Existing-Label+2");
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> projectCodeOwnersApiFactory.project(project).updateConfig(input));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            String.format(
                "invalid config:\n"
                    + "* Required approval 'Non-Existing-Label+2' that is configured in"
                    + " code-owners.config (parameter codeOwners.overrideApproval) is invalid:"
                    + " Label Non-Existing-Label doesn't exist for project %s.",
                project));
  }

  @Test
  public void setFallbackCodeOwners() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getFallbackCodeOwners())
        .isEqualTo(FallbackCodeOwners.NONE);

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.fallbackCodeOwners = FallbackCodeOwners.ALL_USERS;
    CodeOwnerProjectConfigInfo updatedConfig =
        projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.general.fallbackCodeOwners).isEqualTo(FallbackCodeOwners.ALL_USERS);
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getFallbackCodeOwners())
        .isEqualTo(FallbackCodeOwners.ALL_USERS);

    input.fallbackCodeOwners = FallbackCodeOwners.NONE;
    updatedConfig = projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.general.fallbackCodeOwners).isEqualTo(FallbackCodeOwners.NONE);
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getFallbackCodeOwners())
        .isEqualTo(FallbackCodeOwners.NONE);
  }

  @Test
  public void setGlobalCodeOwners() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getGlobalCodeOwners())
        .isEmpty();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.globalCodeOwners = ImmutableList.of(user.email(), "foo.bar@example.com");
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(
            codeOwnersPluginConfiguration.getProjectConfig(project).getGlobalCodeOwners().stream()
                .map(CodeOwnerReference::email)
                .collect(toImmutableSet()))
        .containsExactly(user.email(), "foo.bar@example.com");

    input.globalCodeOwners = ImmutableList.of();
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getGlobalCodeOwners())
        .isEmpty();
  }

  @Test
  public void setExemptedUsers() throws Exception {
    TestAccount user2 = accountCreator.user2();

    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getExemptedAccounts())
        .isEmpty();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.exemptedUsers = ImmutableList.of(user.email(), user2.email());
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getExemptedAccounts())
        .containsExactly(user.id(), user2.id());

    input.exemptedUsers = ImmutableList.of();
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getExemptedAccounts())
        .isEmpty();
  }

  @Test
  public void setMergeCommitStrategy() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getMergeCommitStrategy())
        .isEqualTo(MergeCommitStrategy.ALL_CHANGED_FILES);

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.mergeCommitStrategy = MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION;
    CodeOwnerProjectConfigInfo updatedConfig =
        projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.general.mergeCommitStrategy)
        .isEqualTo(MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION);
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getMergeCommitStrategy())
        .isEqualTo(MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION);

    input.mergeCommitStrategy = MergeCommitStrategy.ALL_CHANGED_FILES;
    updatedConfig = projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.general.mergeCommitStrategy)
        .isEqualTo(MergeCommitStrategy.ALL_CHANGED_FILES);
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getMergeCommitStrategy())
        .isEqualTo(MergeCommitStrategy.ALL_CHANGED_FILES);
  }

  @Test
  public void setImplicitApprovals() throws Exception {
    assertThat(
            codeOwnersPluginConfiguration.getProjectConfig(project).areImplicitApprovalsEnabled())
        .isFalse();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.implicitApprovals = true;
    CodeOwnerProjectConfigInfo updatedConfig =
        projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.general.implicitApprovals).isTrue();
    assertThat(
            codeOwnersPluginConfiguration.getProjectConfig(project).areImplicitApprovalsEnabled())
        .isTrue();

    input.implicitApprovals = false;
    updatedConfig = projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.general.implicitApprovals).isNull();
    assertThat(
            codeOwnersPluginConfiguration.getProjectConfig(project).areImplicitApprovalsEnabled())
        .isFalse();
  }

  @Test
  public void setOverrideInfoUrl() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getOverrideInfoUrl())
        .isEmpty();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.overrideInfoUrl = "http://foo.bar";
    CodeOwnerProjectConfigInfo updatedConfig =
        projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.general.overrideInfoUrl).isEqualTo("http://foo.bar");
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getOverrideInfoUrl())
        .value()
        .isEqualTo("http://foo.bar");

    input.overrideInfoUrl = "";
    updatedConfig = projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.general.overrideInfoUrl).isNull();
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).getOverrideInfoUrl())
        .isEmpty();
  }

  @Test
  public void setReadOnly() throws Exception {
    assertThat(
            codeOwnersPluginConfiguration.getProjectConfig(project).areCodeOwnerConfigsReadOnly())
        .isFalse();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.readOnly = true;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(
            codeOwnersPluginConfiguration.getProjectConfig(project).areCodeOwnerConfigsReadOnly())
        .isTrue();

    input.readOnly = false;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(
            codeOwnersPluginConfiguration.getProjectConfig(project).areCodeOwnerConfigsReadOnly())
        .isFalse();
  }

  @Test
  public void setExemptPureReverts() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).arePureRevertsExempted())
        .isFalse();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.exemptPureReverts = true;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).arePureRevertsExempted())
        .isTrue();

    input.exemptPureReverts = false;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).arePureRevertsExempted())
        .isFalse();
  }

  @Test
  public void setEnableValidationOnCommitReceived() throws Exception {
    assertThat(
            codeOwnersPluginConfiguration
                .getProjectConfig(project)
                .getCodeOwnerConfigValidationPolicyForCommitReceived("master"))
        .isEqualTo(CodeOwnerConfigValidationPolicy.TRUE);

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.enableValidationOnCommitReceived = CodeOwnerConfigValidationPolicy.FALSE;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(
            codeOwnersPluginConfiguration
                .getProjectConfig(project)
                .getCodeOwnerConfigValidationPolicyForCommitReceived("master"))
        .isEqualTo(CodeOwnerConfigValidationPolicy.FALSE);

    input.enableValidationOnCommitReceived = CodeOwnerConfigValidationPolicy.TRUE;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(
            codeOwnersPluginConfiguration
                .getProjectConfig(project)
                .getCodeOwnerConfigValidationPolicyForCommitReceived("master"))
        .isEqualTo(CodeOwnerConfigValidationPolicy.TRUE);
  }

  @Test
  public void setEnableValidationOnSubmit() throws Exception {
    assertThat(
            codeOwnersPluginConfiguration
                .getProjectConfig(project)
                .getCodeOwnerConfigValidationPolicyForSubmit("master"))
        .isEqualTo(CodeOwnerConfigValidationPolicy.TRUE);

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.enableValidationOnSubmit = CodeOwnerConfigValidationPolicy.FALSE;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(
            codeOwnersPluginConfiguration
                .getProjectConfig(project)
                .getCodeOwnerConfigValidationPolicyForSubmit("master"))
        .isEqualTo(CodeOwnerConfigValidationPolicy.FALSE);

    input.enableValidationOnSubmit = CodeOwnerConfigValidationPolicy.TRUE;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(
            codeOwnersPluginConfiguration
                .getProjectConfig(project)
                .getCodeOwnerConfigValidationPolicyForSubmit("master"))
        .isEqualTo(CodeOwnerConfigValidationPolicy.TRUE);
  }

  @Test
  public void setRejectNonResolvableCodeOwners() throws Exception {
    assertThat(
            codeOwnersPluginConfiguration.getProjectConfig(project).rejectNonResolvableCodeOwners())
        .isTrue();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.rejectNonResolvableCodeOwners = false;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(
            codeOwnersPluginConfiguration.getProjectConfig(project).rejectNonResolvableCodeOwners())
        .isFalse();

    input.rejectNonResolvableCodeOwners = true;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(
            codeOwnersPluginConfiguration.getProjectConfig(project).rejectNonResolvableCodeOwners())
        .isTrue();
  }

  @Test
  public void setRejectNonResolvableImports() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).rejectNonResolvableImports())
        .isTrue();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.rejectNonResolvableImports = false;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).rejectNonResolvableImports())
        .isFalse();

    input.rejectNonResolvableImports = true;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).rejectNonResolvableImports())
        .isTrue();
  }

  @Test
  public void setMaxPathsInChangeMessages() throws Exception {
    assertThat(
            codeOwnersPluginConfiguration.getProjectConfig(project).getMaxPathsInChangeMessages())
        .isEqualTo(GeneralConfig.DEFAULT_MAX_PATHS_IN_CHANGE_MESSAGES);

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.maxPathsInChangeMessages = 10;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(
            codeOwnersPluginConfiguration.getProjectConfig(project).getMaxPathsInChangeMessages())
        .isEqualTo(10);

    input.maxPathsInChangeMessages = 0;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(
            codeOwnersPluginConfiguration.getProjectConfig(project).getMaxPathsInChangeMessages())
        .isEqualTo(0);

    input.maxPathsInChangeMessages = GeneralConfig.DEFAULT_MAX_PATHS_IN_CHANGE_MESSAGES;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(
            codeOwnersPluginConfiguration.getProjectConfig(project).getMaxPathsInChangeMessages())
        .isEqualTo(GeneralConfig.DEFAULT_MAX_PATHS_IN_CHANGE_MESSAGES);
  }

  @Test
  @UseClockStep
  public void checkCommitData() throws Exception {
    RevCommit head1 = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.disabled = true;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);

    // Check message, author and committer.
    RevCommit head2 = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);
    assertThat(head2).isNotEqualTo(head1);
    assertThat(head2.getFullMessage()).isEqualTo("Update code-owners configuration");
    assertThat(head2.getAuthorIdent().getEmailAddress()).isEqualTo(admin.email());
    assertThat(head2.getCommitterIdent().getName()).isEqualTo("Gerrit Code Review");

    input.disabled = false;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);

    // Check that timestamps differ for each commit.
    RevCommit head3 = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);
    assertThat(head3).isNotEqualTo(head2);
    assertThat(head3.getAuthorIdent().getWhen()).isGreaterThan(head2.getAuthorIdent().getWhen());
    assertThat(head3.getCommitterIdent().getWhen())
        .isGreaterThan(head2.getCommitterIdent().getWhen());
  }

  @Test
  public void noOpUpdate() throws Exception {
    RevCommit oldHead = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);
    projectCodeOwnersApiFactory.project(project).updateConfig(new CodeOwnerProjectConfigInput());
    RevCommit newHead = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);
    assertThat(newHead).isEqualTo(oldHead);
  }

  @Test
  public void updateConfigWhenRefsMetaConfigIsMissing() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    deleteRef.deleteSingleRef(projectState, RefNames.REFS_CONFIG);

    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).isDisabled()).isFalse();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.disabled = true;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);

    assertThat(codeOwnersPluginConfiguration.getProjectConfig(project).isDisabled()).isTrue();
  }
}
