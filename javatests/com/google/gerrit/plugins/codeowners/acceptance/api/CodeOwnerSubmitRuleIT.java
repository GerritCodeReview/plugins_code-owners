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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerStatusInfoSubject.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.SubmitRequirementInfoSubject.assertThatCollection;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.DeleteBranchesInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerStatusInfo;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.testing.SubmitRequirementInfoSubject;
import org.junit.Test;

/** Acceptance test for {@code com.google.gerrit.plugins.codeowners.backend.CodeOwnerSubmitRule}. */
public class CodeOwnerSubmitRuleIT extends AbstractCodeOwnersIT {

  @Test
  public void changeIsSubmittableIfCodeOwnersFuctionalityIsDisabled() throws Exception {
    disableCodeOwnersForProject(project);

    String changeId = createChange("Test Change", "foo/bar.baz", "file content").getChangeId();

    // Approve by a non-code-owner.
    approve(changeId);

    // Check the submittable flag.
    ChangeInfo changeInfo = gApi.changes().id(changeId).get(ListChangesOption.SUBMITTABLE);
    assertThat(changeInfo.submittable).isTrue();

    // Check that there is no submit requirement.
    assertThat(changeInfo.requirements).isEmpty();

    // Submit the change.
    gApi.changes().id(changeId).current().submit();
    assertThat(gApi.changes().id(changeId).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabled", value = "true")
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "INVALID")
  public void changeIsSubmittableIfCodeOwnersFuctionalityIsDisabled_invalidPluginConfig()
      throws Exception {
    String changeId = createChange("Test Change", "foo/bar.baz", "file content").getChangeId();

    // Approve by a non-code-owner.
    approve(changeId);

    // Check the submittable flag.
    ChangeInfo changeInfo = gApi.changes().id(changeId).get(ListChangesOption.SUBMITTABLE);
    assertThat(changeInfo.submittable).isTrue();

    // Check that there is no submit requirement.
    assertThat(changeInfo.requirements).isEmpty();

    // Submit the change.
    gApi.changes().id(changeId).current().submit();
    assertThat(gApi.changes().id(changeId).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void changeWithInsufficentReviewersIsNotSubmittable() throws Exception {
    String changeId = createChange("Test Change", "foo/bar.baz", "file content").getChangeId();

    // Approve by a non-code-owner.
    approve(changeId);

    // Verify that the code owner status for the changed file is INSUFFICIENT_REVIEWERS.
    CodeOwnerStatusInfo codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus();
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .onlyElement()
        .hasNewPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);

    // Check the submittable flag.
    ChangeInfo changeInfo = gApi.changes().id(changeId).get(ListChangesOption.SUBMITTABLE);
    assertThat(changeInfo.submittable).isFalse();

    // Check the submit requirement.
    SubmitRequirementInfoSubject submitRequirementInfoSubject =
        assertThatCollection(changeInfo.requirements).onlyElement();
    submitRequirementInfoSubject.hasStatusThat().isEqualTo("NOT_READY");
    submitRequirementInfoSubject.hasFallbackTextThat().isEqualTo("Code Owners");
    submitRequirementInfoSubject.hasTypeThat().isEqualTo("code-owners");

    // Try to submit the change.
    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class, () -> gApi.changes().id(changeId).current().submit());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Failed to submit 1 change due to the following problems:\n"
                    + "Change %d: Submit requirement not fulfilled: Code Owners",
                changeInfo._number));
  }

  @Test
  public void changeWithPendingCodeOwnerApprovalsIsNotSubmittable() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    String changeId = createChange("Test Change", "foo/bar.baz", "file content").getChangeId();

    // Add a reviewer that is a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Approve by a non-code-owner.
    approve(changeId);

    // Verify that the code owner status for the changed file is PENDING.
    CodeOwnerStatusInfo codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus();
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .onlyElement()
        .hasNewPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.PENDING);

    // Check the submittable flag.
    ChangeInfo changeInfo = gApi.changes().id(changeId).get(ListChangesOption.SUBMITTABLE);
    assertThat(changeInfo.submittable).isFalse();

    // Check the submit requirement.
    SubmitRequirementInfoSubject submitRequirementInfoSubject =
        assertThatCollection(changeInfo.requirements).onlyElement();
    submitRequirementInfoSubject.hasStatusThat().isEqualTo("NOT_READY");
    submitRequirementInfoSubject.hasFallbackTextThat().isEqualTo("Code Owners");
    submitRequirementInfoSubject.hasTypeThat().isEqualTo("code-owners");

    // Try to submit the change.
    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class, () -> gApi.changes().id(changeId).current().submit());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Failed to submit 1 change due to the following problems:\n"
                    + "Change %d: Submit requirement not fulfilled: Code Owners",
                changeInfo._number));
  }

  @Test
  public void changeWithCodeOwnerApprovalsIsSubmittable() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String changeId = createChange("Test Change", "foo/bar.baz", "file content").getChangeId();

    // Approve by a code-owner.
    approve(changeId);

    // Verify that the code owner status for the changed file is APPROVED.
    CodeOwnerStatusInfo codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus();
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .onlyElement()
        .hasNewPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);

    // Check the submittable flag.
    ChangeInfo changeInfo = gApi.changes().id(changeId).get(ListChangesOption.SUBMITTABLE);
    assertThat(changeInfo.submittable).isTrue();

    // Check the submit requirement.
    SubmitRequirementInfoSubject submitRequirementInfoSubject =
        assertThatCollection(changeInfo.requirements).onlyElement();
    submitRequirementInfoSubject.hasStatusThat().isEqualTo("OK");
    submitRequirementInfoSubject.hasFallbackTextThat().isEqualTo("Code Owners");
    submitRequirementInfoSubject.hasTypeThat().isEqualTo("code-owners");

    // Submit the change.
    gApi.changes().id(changeId).current().submit();
    assertThat(gApi.changes().id(changeId).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeWithOverrideApprovalIsSubmittable() throws Exception {
    createOwnersOverrideLabel();

    String changeId = createChange("Test Change", "foo/bar.baz", "file content").getChangeId();

    // Check that the change is not submittable.
    assertThat(gApi.changes().id(changeId).get(ListChangesOption.SUBMITTABLE).submittable)
        .isFalse();

    // Add an override approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // Verify that the code owner status for the changed file is APPROVED.
    CodeOwnerStatusInfo codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus();
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .onlyElement()
        .hasNewPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);

    // Approve by a non-code-owner to satisfy the Code-Review+2 requirement.
    approve(changeId);

    // Check the submit requirement.
    SubmitRequirementInfoSubject submitRequirementInfoSubject =
        assertThatCollection(gApi.changes().id(changeId).get().requirements).onlyElement();
    submitRequirementInfoSubject.hasStatusThat().isEqualTo("OK");
    submitRequirementInfoSubject.hasFallbackTextThat().isEqualTo("Code Owners");
    submitRequirementInfoSubject.hasTypeThat().isEqualTo("code-owners");

    // Submit the change.
    gApi.changes().id(changeId).current().submit();
    assertThat(gApi.changes().id(changeId).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void changeIsNotSubmittableIfDestinationBranchWasDeleted() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String branchName = "tempBranch";
    createBranch(BranchNameKey.create(project, branchName));

    String changeId = createChange("refs/for/" + branchName).getChangeId();

    // Approve by a code-owner.
    approve(changeId);

    DeleteBranchesInput input = new DeleteBranchesInput();
    input.branches = ImmutableList.of(branchName);
    gApi.projects().name(project.get()).deleteBranches(input);

    ChangeInfo changeInfo = gApi.changes().id(changeId).get(ListChangesOption.SUBMITTABLE);
    assertThat(changeInfo.submittable).isFalse();

    // Check that the submit requirement.
    SubmitRequirementInfoSubject submitRequirementInfoSubject =
        assertThatCollection(changeInfo.requirements).onlyElement();
    submitRequirementInfoSubject.hasStatusThat().isEqualTo("NOT_READY");
    submitRequirementInfoSubject.hasFallbackTextThat().isEqualTo("Code Owners");
    submitRequirementInfoSubject.hasTypeThat().isEqualTo("code-owners");

    // Try to submit the change.
    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class, () -> gApi.changes().id(changeId).current().submit());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("destination branch \"refs/heads/%s\" not found.", branchName));
  }
}
