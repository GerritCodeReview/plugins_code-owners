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
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestMetricMaker;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.DeleteBranchesInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerStatusInfo;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.testing.SubmitRequirementInfoSubject;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/** Acceptance test for {@code com.google.gerrit.plugins.codeowners.backend.CodeOwnerSubmitRule}. */
public class CodeOwnerSubmitRuleIT extends AbstractCodeOwnersIT {
  @Inject private ProjectOperations projectOperations;
  @Inject private TestMetricMaker testMetricMaker;

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
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().get();
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
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().get();
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
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().get();
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
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().get();
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

  @Test
  @GerritConfig(name = "plugin.code-owners.exemptedUser", value = "exempted-user@example.com")
  public void changeIsSubmittableIfUserIsExcempted() throws Exception {
    TestAccount exemptedUser =
        accountCreator.create(
            "exemptedUser", "exempted-user@example.com", "Exempted User", /* displayName= */ null);

    PushOneCommit.Result r = createChange(exemptedUser, "Some Change", "foo.txt", "some content");
    String changeId = r.getChangeId();

    // Apply Code-Review+2 by a non-code-owner to satisfy the MaxWithBlock function of the
    // Code-Review label.
    approve(changeId);

    ChangeInfo changeInfo =
        gApi.changes()
            .id(changeId)
            .get(
                ListChangesOption.SUBMITTABLE,
                ListChangesOption.ALL_REVISIONS,
                ListChangesOption.CURRENT_ACTIONS);
    assertThat(changeInfo.submittable).isTrue();

    // Check that the submit button is enabled.
    assertThat(changeInfo.revisions.get(r.getCommit().getName()).actions.get("submit").enabled)
        .isTrue();

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
  public void changeIsNotSubmittableIfOwnersFileIsNonParsable() throws Exception {
    testChangeIsNotSubmittableIfOwnersFileIsNonParsable(/* invalidCodeOwnerConfigInfoUrl= */ null);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.invalidCodeOwnerConfigInfoUrl", value = "http://foo.bar")
  public void changeIsNotSubmittableIfOwnersFileIsNonParsable_withInvalidCodeOwnerConfigInfoUrl()
      throws Exception {
    testChangeIsNotSubmittableIfOwnersFileIsNonParsable("http://foo.bar");
  }

  private void testChangeIsNotSubmittableIfOwnersFileIsNonParsable(
      @Nullable String invalidCodeOwnerConfigInfoUrl) throws Exception {
    // Add a non-parsable code owner config.
    String nameOfInvalidCodeOwnerConfigFile = getCodeOwnerConfigFileName();
    createNonParseableCodeOwnerConfig(nameOfInvalidCodeOwnerConfigFile);

    PushOneCommit.Result r = createChange("Some Change", "foo.txt", "some content");
    String changeId = r.getChangeId();

    // Apply Code-Review+2 to satisfy the MaxWithBlock function of the Code-Review label.
    approve(changeId);

    ChangeInfo changeInfo =
        gApi.changes()
            .id(changeId)
            .get(
                ListChangesOption.SUBMITTABLE,
                ListChangesOption.ALL_REVISIONS,
                ListChangesOption.CURRENT_ACTIONS);
    assertThat(changeInfo.submittable).isFalse();

    // Check that the submit button is not visible.
    assertThat(changeInfo.revisions.get(r.getCommit().getName()).actions.get("submit")).isNull();

    // Check the submit requirement.
    assertThatCollection(changeInfo.requirements).isEmpty();

    // Try to submit the change.
    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class, () -> gApi.changes().id(changeId).current().submit());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Failed to submit 1 change due to the following problems:\n"
                    + "Change %s: submit rule error: Failed to evaluate code owner statuses for"
                    + " patch set 1 of change %s (cause: invalid code owner config file '%s'"
                    + " (project = %s, branch = master):\n  %s).%s",
                changeInfo._number,
                changeInfo._number,
                JgitPath.of(nameOfInvalidCodeOwnerConfigFile).getAsAbsolutePath(),
                project,
                getParsingErrorMessageForNonParseableCodeOwnerConfig(),
                invalidCodeOwnerConfigInfoUrl != null
                    ? String.format("\nFor help check %s.", invalidCodeOwnerConfigInfoUrl)
                    : ""));
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.mergeCommitStrategy",
      value = "FILES_WITH_CONFLICT_RESOLUTION")
  public void changeIsSubmittableIfAutoMergeIsNotPresent() throws Exception {
    setAsDefaultCodeOwners(admin);

    // Create another branch
    String branchName = "foo";
    BranchInput branchInput = new BranchInput();
    branchInput.ref = branchName;
    branchInput.revision = projectOperations.project(project).getHead("master").name();
    gApi.projects().name(project.get()).branch(branchInput.ref).create(branchInput);

    // Create 2 parent commits.
    ObjectId initial = projectOperations.project(project).getHead("master");

    PushOneCommit.Result p1 =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "parent 1",
                ImmutableMap.of("foo", "foo-1.2", "bar", "bar-1.2"))
            .to("refs/for/master");
    RevCommit parent1 = p1.getCommit();
    approve(p1.getChangeId());
    gApi.changes().id(p1.getChangeId()).current().submit();

    testRepo.reset(initial);
    PushOneCommit.Result p2 =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "parent 2",
                ImmutableMap.of("foo", "foo-2.2", "bar", "bar-2.2"))
            .to("refs/for/foo");
    RevCommit parent2 = p2.getCommit();
    approve(p2.getChangeId());
    gApi.changes().id(p2.getChangeId()).current().submit();

    // Create the merge commit.
    PushOneCommit m =
        pushFactory.create(
            admin.newIdent(), testRepo, "merge", ImmutableMap.of("foo", "foo-1", "bar", "bar-2"));
    m.setParents(ImmutableList.of(parent1, parent2));
    PushOneCommit.Result r = m.to("refs/for/master");
    r.assertOkStatus();
    String changeId = r.getChangeId();

    // Apply Code-Review+2 by a non-code-owner to satisfy the MaxWithBlock function of the
    // Code-Review label.
    approve(changeId);

    // Delete the auto-merge ref so that the auto-merge needs to be computed in-memory when the
    // code-owners submit rule is executed.
    deleteAutoMergeBranch(r.getCommit());

    ChangeInfo changeInfo =
        gApi.changes()
            .id(changeId)
            .get(
                ListChangesOption.SUBMITTABLE,
                ListChangesOption.ALL_REVISIONS,
                ListChangesOption.CURRENT_ACTIONS);
    assertThat(changeInfo.submittable).isTrue();

    // Check that the submit button is enabled.
    assertThat(changeInfo.revisions.get(r.getCommit().getName()).actions.get("submit").enabled)
        .isTrue();

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
  public void submitRuleIsInvokedOnlyOnceWhenGettingChangeDetails() throws Exception {
    PushOneCommit.Result r = createChange("Some Change", "foo.txt", "some content");
    String changeId = r.getChangeId();

    testMetricMaker.reset();
    gApi.changes()
        .id(changeId)
        .get(ListChangesOption.ALL_REVISIONS, ListChangesOption.CURRENT_ACTIONS);

    // Submit rules are computed freshly, but only once.
    assertThat(testMetricMaker.getCount("plugins/code-owners/count_code_owner_submit_rule_runs"))
        .isEqualTo(1);
  }

  @Test
  public void submitRuleIsNotInvokedWhenQueryingChange() throws Exception {
    PushOneCommit.Result r = createChange("Some Change", "foo.txt", "some content");
    String changeId = r.getChangeId();

    testMetricMaker.reset();
    gApi.changes()
        .query(changeId)
        .withOptions(ListChangesOption.ALL_REVISIONS, ListChangesOption.CURRENT_ACTIONS)
        .get();

    // Submit rule evaluation results from the change index are reused
    assertThat(testMetricMaker.getCount("plugins/code-owners/count_code_owner_submit_rule_runs"))
        .isEqualTo(0);
  }

  private void deleteAutoMergeBranch(ObjectId mergeCommit) throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      RefUpdate ru = repo.updateRef(RefNames.refsCacheAutomerge(mergeCommit.name()));
      ru.setForceUpdate(true);
      assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);
      assertThat(repo.exactRef(RefNames.refsCacheAutomerge(mergeCommit.name()))).isNull();
    }
  }
}
