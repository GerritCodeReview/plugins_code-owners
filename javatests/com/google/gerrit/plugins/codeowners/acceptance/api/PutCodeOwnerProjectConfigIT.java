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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.RequiredApprovalSubject.assertThat;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.RequiredApproval;
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
    assertThat(codeOwnersPluginConfiguration.isDisabled(project)).isFalse();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.disabled = true;
    CodeOwnerProjectConfigInfo updatedConfig =
        projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.status.disabled).isTrue();
    assertThat(codeOwnersPluginConfiguration.isDisabled(project)).isTrue();

    input.disabled = false;
    updatedConfig = projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.status.disabled).isNull();
    assertThat(codeOwnersPluginConfiguration.isDisabled(project)).isFalse();
  }

  @Test
  public void setDisabledBranches() throws Exception {
    BranchNameKey masterBranch = BranchNameKey.create(project, "master");
    BranchNameKey fooBranch = BranchNameKey.create(project, "foo");

    createBranch(fooBranch);
    assertThat(codeOwnersPluginConfiguration.isDisabled(masterBranch)).isFalse();
    assertThat(codeOwnersPluginConfiguration.isDisabled(fooBranch)).isFalse();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.disabledBranches = ImmutableList.of("refs/heads/master", "refs/heads/foo");
    CodeOwnerProjectConfigInfo updatedConfig =
        projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.status.disabledBranches)
        .containsExactly("refs/heads/master", "refs/heads/foo");
    assertThat(codeOwnersPluginConfiguration.isDisabled(masterBranch)).isTrue();
    assertThat(codeOwnersPluginConfiguration.isDisabled(fooBranch)).isTrue();

    input = new CodeOwnerProjectConfigInput();
    input.disabledBranches = ImmutableList.of();
    updatedConfig = projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.status.disabledBranches).isNull();
    assertThat(codeOwnersPluginConfiguration.isDisabled(masterBranch)).isFalse();
    assertThat(codeOwnersPluginConfiguration.isDisabled(fooBranch)).isFalse();
  }

  @Test
  public void setDisabledBranchesRegEx() throws Exception {
    BranchNameKey masterBranch = BranchNameKey.create(project, "master");
    BranchNameKey fooBranch = BranchNameKey.create(project, "foo");

    createBranch(fooBranch);
    assertThat(codeOwnersPluginConfiguration.isDisabled(masterBranch)).isFalse();
    assertThat(codeOwnersPluginConfiguration.isDisabled(fooBranch)).isFalse();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.disabledBranches = ImmutableList.of("refs/heads/*");
    CodeOwnerProjectConfigInfo updatedConfig =
        projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.status.disabledBranches)
        .containsExactly("refs/heads/master", "refs/heads/foo");
    assertThat(codeOwnersPluginConfiguration.isDisabled(masterBranch)).isTrue();
    assertThat(codeOwnersPluginConfiguration.isDisabled(fooBranch)).isTrue();
  }

  @Test
  public void setDisabledBranchThatDoesntExist() throws Exception {
    BranchNameKey fooBranch = BranchNameKey.create(project, "foo");

    assertThat(codeOwnersPluginConfiguration.isDisabled(fooBranch)).isFalse();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.disabledBranches = ImmutableList.of("refs/heads/foo");
    CodeOwnerProjectConfigInfo updatedConfig =
        projectCodeOwnersApiFactory.project(project).updateConfig(input);
    // status.disabledBranches does only contain existing branches
    assertThat(updatedConfig.status.disabledBranches).isNull();
    assertThat(codeOwnersPluginConfiguration.isDisabled(fooBranch)).isTrue();

    createBranch(fooBranch);
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
    assertThat(codeOwnersPluginConfiguration.getFileExtension(project)).isEmpty();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.fileExtension = "foo";
    CodeOwnerProjectConfigInfo updatedConfig =
        projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.general.fileExtension).isEqualTo("foo");
    assertThat(codeOwnersPluginConfiguration.getFileExtension(project)).value().isEqualTo("foo");

    input.fileExtension = "";
    updatedConfig = projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.general.fileExtension).isNull();
    assertThat(codeOwnersPluginConfiguration.getFileExtension(project)).isEmpty();
  }

  @Test
  public void setRequiredApproval() throws Exception {
    RequiredApproval requiredApproval = codeOwnersPluginConfiguration.getRequiredApproval(project);
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
    requiredApproval = codeOwnersPluginConfiguration.getRequiredApproval(project);
    assertThat(requiredApproval).hasLabelNameThat().isEqualTo(otherLabel);
    assertThat(requiredApproval).hasValueThat().isEqualTo(2);

    input.requiredApproval = "";
    updatedConfig = projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.requiredApproval.label).isEqualTo("Code-Review");
    assertThat(updatedConfig.requiredApproval.value).isEqualTo(1);
    requiredApproval = codeOwnersPluginConfiguration.getRequiredApproval(project);
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
    assertThat(codeOwnersPluginConfiguration.getOverrideApproval(project)).isEmpty();

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
    assertThat(codeOwnersPluginConfiguration.getOverrideApproval(project)).hasSize(2);

    input.overrideApprovals = ImmutableList.of();
    updatedConfig = projectCodeOwnersApiFactory.project(project).updateConfig(input);
    assertThat(updatedConfig.overrideApproval).isNull();
    assertThat(codeOwnersPluginConfiguration.getOverrideApproval(project)).isEmpty();
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

    assertThat(codeOwnersPluginConfiguration.isDisabled(project)).isFalse();

    CodeOwnerProjectConfigInput input = new CodeOwnerProjectConfigInput();
    input.disabled = true;
    projectCodeOwnersApiFactory.project(project).updateConfig(input);

    assertThat(codeOwnersPluginConfiguration.isDisabled(project)).isTrue();
  }
}
