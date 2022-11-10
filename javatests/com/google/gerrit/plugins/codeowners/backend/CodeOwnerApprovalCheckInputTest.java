// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.plugins.codeowners.backend;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginProjectConfigSnapshot;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerApprovalCheckInput}. */
public class CodeOwnerApprovalCheckInputTest extends AbstractCodeOwnersTest {
  @Inject private ChangeNotes.Factory changeNotesFactory;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  private CodeOwnerApprovalCheckInput.Loader.Factory inputLoaderFactory;
  private CodeOwnerResolver codeOwnerResolver;
  private CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig;
  private TestAccount user2;
  private Change.Id changeId;
  private Change.Key changeKey;
  private Account.Id changeOwner;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    inputLoaderFactory =
        plugin.getSysInjector().getInstance(CodeOwnerApprovalCheckInput.Loader.Factory.class);
    codeOwnerResolver = plugin.getSysInjector().getInstance(CodeOwnerResolver.class);
    CodeOwnersPluginConfiguration codeOwnersPluginConfiguration =
        plugin.getSysInjector().getInstance(CodeOwnersPluginConfiguration.class);
    codeOwnersConfig = codeOwnersPluginConfiguration.getProjectConfig(project);
  }

  @Before
  public void setUp() throws Exception {
    user2 = accountCreator.user2();
    ChangeData changeData = createChange().getChange();
    changeId = changeData.getId();
    changeKey = changeData.change().getKey();
    changeOwner = admin.id();
  }

  @Test
  public void noReviewers() {
    assertThat(loadInput().reviewers()).isEmpty();
  }

  @Test
  public void withReviewers() throws Exception {
    changeApi().addReviewer(user.email());
    changeApi().addReviewer(user2.email());

    // make the change owner a reviewer:
    // the change owner cannot be added as a reviewer, but the change owner becomes a reviewer when
    // they vote on the change
    requestScopeOperations.setApiUser(changeOwner);
    recommend(changeId.toString());

    assertThat(loadInput().reviewers()).containsExactly(user.id(), user2.id(), changeOwner);
  }

  @Test
  public void withReviewers_selfApprovalsIgnored() throws Exception {
    disableSelfCodeReviewApprovals();

    changeApi().addReviewer(user.email());
    changeApi().addReviewer(user2.email());

    // make the change owner a reviewer:
    // the change owner cannot be added as a reviewer, but the change owner becomes a reviewer when
    // they vote on the change
    requestScopeOperations.setApiUser(changeOwner);
    recommend(changeId.toString());

    assertThat(loadInput().reviewers()).containsExactly(user.id(), user2.id());
  }

  @Test
  public void noApprovers() {
    assertThat(loadInput().approvers()).isEmpty();
  }

  @Test
  public void withApprovers() throws Exception {
    // self approve
    requestScopeOperations.setApiUser(changeOwner);
    recommend(changeId.toString());

    // approve as user
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // approve as user2
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId.toString());

    assertThat(loadInput().approvers()).containsExactly(changeOwner, user.id(), user2.id());
  }

  @Test
  public void withApprovers_selfApprovalsIgnored() throws Exception {
    disableSelfCodeReviewApprovals();

    // self approve
    requestScopeOperations.setApiUser(changeOwner);
    recommend(changeId.toString());

    // approve as user
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // approve as user2
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId.toString());

    assertThat(loadInput().approvers()).containsExactly(user.id(), user2.id());
  }

  /** Test that current approvals do not count for computing previous approvers. */
  @Test
  @GerritConfig(name = "plugin.code-owners.enableStickyApprovals", value = "true")
  public void noPreviousApprovers() throws Exception {
    // self approve current patch set
    requestScopeOperations.setApiUser(changeOwner);
    recommend(changeId.toString());

    // approve as user current patch set
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // approve as user2 current patch set
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId.toString());

    assertThat(loadInput().approversFromPreviousPatchSets()).isEmpty();
  }

  /** Test that previous approvals on other labels do not count for computing previous approvers. */
  @Test
  @GerritConfig(name = "plugin.code-owners.enableStickyApprovals", value = "true")
  public void noPreviousApproversIfApprovalIsOnUnrelatedLabel() throws Exception {
    // Create Foo-Review label.
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Approved", " 0", "Not Approved");
    gApi.projects().name(project.get()).label("Foo-Review").create(input).get();

    // Allow to vote on the Foo-Review label.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            TestProjectUpdate.allowLabel("Foo-Review")
                .range(0, 1)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .build())
        .update();

    // approve on Foo-Review label
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(changeId.get()).current().review(new ReviewInput().label("Foo-Review", 1));

    // create a second patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    assertThat(loadInput().approversFromPreviousPatchSets()).isEmpty();
  }

  /**
   * Test that previous votes with insufficient values do not count for computing previous
   * approvers.
   */
  @Test
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "Code-Review+2")
  @GerritConfig(name = "plugin.code-owners.enableStickyApprovals", value = "true")
  public void noPreviousApproversIfVoteIsNotAnApproval() throws Exception {
    // vote with Code-Review+1, but only Code-Review+2 counts as a code owner approval
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // create a second patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    assertThat(loadInput().approversFromPreviousPatchSets()).isEmpty();
  }

  /** Test that previous approvals are ignored if sticky approvals are disabled. */
  @Test
  @GerritConfig(name = "plugin.code-owners.enableStickyApprovals", value = "false")
  public void noPreviousApproversIfEnableStickyApprovalsDisabled() throws Exception {
    // self approve
    requestScopeOperations.setApiUser(changeOwner);
    recommend(changeId.toString());

    // approve as user
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // approve as user2
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId.toString());

    // create a second patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    assertThat(loadInput().approversFromPreviousPatchSets()).isEmpty();
  }

  /** Test that the approvals on the previous patch set count for computing previous approvers. */
  @Test
  @GerritConfig(name = "plugin.code-owners.enableStickyApprovals", value = "true")
  public void withPreviousApprovers() throws Exception {
    // self approve
    requestScopeOperations.setApiUser(changeOwner);
    recommend(changeId.toString());

    // approve as user
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // approve as user2
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId.toString());

    // create a second patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    ArrayListMultimap<PatchSet.Id, Account.Id> expectedPreviousApprovers =
        ArrayListMultimap.create();
    expectedPreviousApprovers.putAll(
        PatchSet.id(changeId, 1), ImmutableSet.of(changeOwner, user.id(), user2.id()));
    assertThat(loadInput().approversFromPreviousPatchSets())
        .containsExactlyEntriesIn(expectedPreviousApprovers);
  }

  /**
   * Test that a self-approval on the previous patch set is ignored for computing previous
   * approvers.
   */
  @Test
  @GerritConfig(name = "plugin.code-owners.enableStickyApprovals", value = "true")
  public void withPreviousApprovers_selfApprovalsIgnored() throws Exception {
    disableSelfCodeReviewApprovals();

    // self approve
    requestScopeOperations.setApiUser(changeOwner);
    recommend(changeId.toString());

    // approve as user
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // approve as user2
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId.toString());

    // create a second patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    ArrayListMultimap<PatchSet.Id, Account.Id> expectedPreviousApprovers =
        ArrayListMultimap.create();
    expectedPreviousApprovers.putAll(
        PatchSet.id(changeId, 1), ImmutableSet.of(user.id(), user2.id()));
    assertThat(loadInput().approversFromPreviousPatchSets())
        .containsExactlyEntriesIn(expectedPreviousApprovers);
  }

  /**
   * Test that the approvals on different previous patch sets count for computing previous
   * approvers.
   */
  @Test
  @GerritConfig(name = "plugin.code-owners.enableStickyApprovals", value = "true")
  public void withPreviousApproversOnDifferentPatchSets() throws Exception {
    // self approve
    requestScopeOperations.setApiUser(changeOwner);
    recommend(changeId.toString());

    // create a second patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    // approve as user
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // create a third patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    // approve as user2
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId.toString());

    // create a 4th patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    ArrayListMultimap<PatchSet.Id, Account.Id> expectedPreviousApprovers =
        ArrayListMultimap.create();
    expectedPreviousApprovers.put(PatchSet.id(changeId, 1), changeOwner);
    expectedPreviousApprovers.put(PatchSet.id(changeId, 2), user.id());
    expectedPreviousApprovers.put(PatchSet.id(changeId, 3), user2.id());
    assertThat(loadInput().approversFromPreviousPatchSets())
        .containsExactlyEntriesIn(expectedPreviousApprovers);
  }

  /** Test that a self-approval on an old patch set is ignored for computing previous approvers. */
  @Test
  @GerritConfig(name = "plugin.code-owners.enableStickyApprovals", value = "true")
  public void withPreviousApproversOnDifferentPatchSets_selfApprovalsIgnored() throws Exception {
    disableSelfCodeReviewApprovals();

    // self approve
    requestScopeOperations.setApiUser(changeOwner);
    recommend(changeId.toString());

    // create a second patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    // approve as user
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // create a third patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    // approve as user2
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId.toString());

    // create a 4th patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    ArrayListMultimap<PatchSet.Id, Account.Id> expectedPreviousApprovers =
        ArrayListMultimap.create();
    expectedPreviousApprovers.put(PatchSet.id(changeId, 2), user.id());
    expectedPreviousApprovers.put(PatchSet.id(changeId, 3), user2.id());
    assertThat(loadInput().approversFromPreviousPatchSets())
        .containsExactlyEntriesIn(expectedPreviousApprovers);
  }

  /**
   * Test that sticky approvals do not count for computing previous approvers (because if the
   * approval is sticky it's a current approval).
   */
  @Test
  @GerritConfig(name = "plugin.code-owners.enableStickyApprovals", value = "true")
  public void noPreviousApproversIfApprovalIsCopied() throws Exception {
    // Make Code-Review approvals sticky
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.copyCondition = "is:ANY";
    gApi.projects().name(allProjects.get()).label("Code-Review").update(input);

    // approve as user
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // create a second patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    assertThat(loadInput().approversFromPreviousPatchSets()).isEmpty();
  }

  /**
   * Test that a previous approval still counts for computing previous approvers if the approver
   * comments on the current patch set without applying a vote.
   */
  @Test
  @GerritConfig(name = "plugin.code-owners.enableStickyApprovals", value = "true")
  public void previousApproversIsPreservedWhenThePreviousApproverCommentsOnTheChange()
      throws Exception {
    // approve as user
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // create a second patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    // check that the previous approver on patch set 1 is found
    ArrayListMultimap<PatchSet.Id, Account.Id> expectedPreviousApprovers =
        ArrayListMultimap.create();
    expectedPreviousApprovers.put(PatchSet.id(changeId, 1), user.id());
    assertThat(loadInput().approversFromPreviousPatchSets())
        .containsExactlyEntriesIn(expectedPreviousApprovers);

    // comment on the change
    requestScopeOperations.setApiUser(user.id());
    ReviewInput reviewInput = ReviewInput.noScore();
    reviewInput.message = "a comment";
    gApi.changes().id(changeId.get()).current().review(reviewInput);

    assertThat(loadInput().approversFromPreviousPatchSets())
        .containsExactlyEntriesIn(expectedPreviousApprovers);
  }

  /**
   * Test that a previous approval doesn't count for computing previous approvers if the approver
   * downgrades the vote.
   */
  @Test
  @GerritConfig(name = "plugin.code-owners.enableStickyApprovals", value = "true")
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "Code-Review+2")
  public void noPreviousApproversIfApprovalIsDowngraded() throws Exception {
    // Allow all users to vote with Code-Review+2.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            TestProjectUpdate.allowLabel("Code-Review")
                .range(0, 2)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .build())
        .update();

    // approve as user
    requestScopeOperations.setApiUser(user.id());
    approve(changeId.toString());

    // create a second patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    // check that the previous approver on patch set 1 is found
    ArrayListMultimap<PatchSet.Id, Account.Id> expectedPreviousApprovers =
        ArrayListMultimap.create();
    expectedPreviousApprovers.put(PatchSet.id(changeId, 1), user.id());
    assertThat(loadInput().approversFromPreviousPatchSets())
        .containsExactlyEntriesIn(expectedPreviousApprovers);

    // change vote from Code-Review+2 to Code-Review+1 as user
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // the Code-Review+1 vote on the current patch set overrode the previous approval
    assertThat(loadInput().approversFromPreviousPatchSets()).isEmpty();

    // create a third patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    // the Code-Review+1 vote on the previous patch set overrode the previous approval
    assertThat(loadInput().approversFromPreviousPatchSets()).isEmpty();
  }

  /**
   * Test that a previous approval doesn't count for computing previous approvers if the approver
   * re-applies the approval (because now it's a current approval).
   */
  @Test
  @GerritConfig(name = "plugin.code-owners.enableStickyApprovals", value = "true")
  public void noPreviousApproversIfApprovalIsReapplied() throws Exception {
    // approve as user
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // create a second patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    // check that the previous approver on patch set 1 is found
    ArrayListMultimap<PatchSet.Id, Account.Id> expectedPreviousApprovers =
        ArrayListMultimap.create();
    expectedPreviousApprovers.put(PatchSet.id(changeId, 1), user.id());
    assertThat(loadInput().approversFromPreviousPatchSets())
        .containsExactlyEntriesIn(expectedPreviousApprovers);

    // re-apply the approval
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    assertThat(loadInput().approversFromPreviousPatchSets()).isEmpty();
  }

  /**
   * Test that only the last previous approval of a user counts for computing previous approvers.
   */
  @Test
  @GerritConfig(name = "plugin.code-owners.enableStickyApprovals", value = "true")
  public void onlyLastPreviousApprovalOfAUserIsConsideredForComputingPreviousApprovers()
      throws Exception {
    // approve as user
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // create a second patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    // re-approve as user
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // create a third patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    ArrayListMultimap<PatchSet.Id, Account.Id> expectedPreviousApprovers =
        ArrayListMultimap.create();
    expectedPreviousApprovers.put(PatchSet.id(changeId, 2), user.id());
    assertThat(loadInput().approversFromPreviousPatchSets())
        .containsExactlyEntriesIn(expectedPreviousApprovers);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableStickyApprovals", value = "true")
  public void noPreviouslyApprovedPatchSets() throws Exception {
    // approve current patch set
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    assertThat(loadInput().previouslyApprovedPatchSetsInReverseOrder()).isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableStickyApprovals", value = "true")
  public void previouslyApprovedPatchSetsAreReturnedInReverseOrder() throws Exception {
    // create a second patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    // approve as user
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // create a third patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    // approve as user2, ignored since overridden on patch set 4
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId.toString());

    // create a 4th patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    // re- approve as user2
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId.toString());

    // create a 5th patch set
    requestScopeOperations.setApiUser(changeOwner);
    amendChange(changeKey.get()).assertOkStatus();

    assertThat(loadInput().previouslyApprovedPatchSetsInReverseOrder())
        .containsExactly(PatchSet.id(changeId, 4), PatchSet.id(changeId, 2));
  }

  @Test
  public void noImplicitApprover() {
    assertThat(loadInput().implicitApprover()).isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void withImplicitApprover() {
    // If implicit approvals are enabled, an implicit approval of the current uploader is assumed.
    // Since the change has only 1 patch set the current uploader is the change owner.
    assertThat(loadInput().implicitApprover()).hasValue(changeOwner);
  }

  @Test
  public void noOverrides() {
    assertThat(loadInput().overrides()).isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void withOverrides() throws Exception {
    createOwnersOverrideLabel();

    ReviewInput reviewInput = new ReviewInput().label("Owners-Override", 1);

    // self override
    requestScopeOperations.setApiUser(changeOwner);
    gApi.changes().id(changeId.toString()).current().review(reviewInput);

    // override as user
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(changeId.toString()).current().review(reviewInput);

    // override as user2
    requestScopeOperations.setApiUser(user2.id());
    gApi.changes().id(changeId.toString()).current().review(reviewInput);

    assertThat(
            loadInput().overrides().stream()
                .map(PatchSetApproval::accountId)
                .collect(toImmutableSet()))
        .containsExactly(changeOwner, user.id(), user2.id());
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void withOverrides_selfApprovalsIgnored() throws Exception {
    createOwnersOverrideLabel();
    disableSelfOwnersOverrideApprovals();

    ReviewInput reviewInput = new ReviewInput().label("Owners-Override", 1);

    // self override
    requestScopeOperations.setApiUser(changeOwner);
    gApi.changes().id(changeId.toString()).current().review(reviewInput);

    // override as user
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(changeId.toString()).current().review(reviewInput);

    // override as user2
    requestScopeOperations.setApiUser(user2.id());
    gApi.changes().id(changeId.toString()).current().review(reviewInput);

    assertThat(
            loadInput().overrides().stream()
                .map(PatchSetApproval::accountId)
                .collect(toImmutableSet()))
        .containsExactly(user.id(), user2.id());
  }

  @Test
  public void noGlobalCodeOwners() {
    CodeOwnerResolverResult globalCodeOwners = loadInput().globalCodeOwners();
    assertThat(globalCodeOwners.codeOwnersAccountIds()).isEmpty();
    assertThat(globalCodeOwners.ownedByAllUsers()).isFalse();
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.globalCodeOwner",
      values = {"user1@example.com", "user2@example.com"})
  public void withGlobalCodeOwners() {
    CodeOwnerResolverResult globalCodeOwners = loadInput().globalCodeOwners();
    assertThat(globalCodeOwners.codeOwnersAccountIds()).containsExactly(user.id(), user2.id());
    assertThat(globalCodeOwners.ownedByAllUsers()).isFalse();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  public void withAllUsersAsGlobalCodeOwners() {
    CodeOwnerResolverResult globalCodeOwners = loadInput().globalCodeOwners();
    assertThat(globalCodeOwners.codeOwnersAccountIds()).isEmpty();
    assertThat(globalCodeOwners.ownedByAllUsers()).isTrue();
  }

  @Test
  public void withFallbackCodeOwnersNone() {
    assertThat(loadInput().fallbackCodeOwners()).isEqualTo(FallbackCodeOwners.NONE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "ALL_USERS")
  public void withFallbackCodeOwnersAllUsers() {
    assertThat(loadInput().fallbackCodeOwners()).isEqualTo(FallbackCodeOwners.ALL_USERS);
  }

  @Test
  public void checkAllOwnersIsFalse() {
    assertThat(loadInput().checkAllOwners()).isFalse();
  }

  @Test
  public void createInputForComputingOwnedPaths_noReviewers() throws Exception {
    changeApi().addReviewer(user.email());
    changeApi().addReviewer(user2.email());

    // CodeOwnerApprovalCheckInput#createForComputingOwnedPaths never sets reviewers
    assertThat(createInputForComputingOwnedPaths(ImmutableSet.of()).reviewers()).isEmpty();
  }

  @Test
  public void createInputForComputingOwnedPaths_noApprovers() throws Exception {
    // self approve
    requestScopeOperations.setApiUser(changeOwner);
    recommend(changeId.toString());

    // approve as user
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // approve as user2
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId.toString());

    // CodeOwnerApprovalCheckInput#createForComputingOwnedPaths always sets approvers to the given
    // accounts
    assertThat(createInputForComputingOwnedPaths(ImmutableSet.of()).approvers()).isEmpty();
  }

  @Test
  public void createInputForComputingOwnedPaths_withApprovers() {
    // CodeOwnerApprovalCheckInput#createForComputingOwnedPaths always sets approvers to the given
    // accounts
    assertThat(
            createInputForComputingOwnedPaths(ImmutableSet.of(admin.id(), user.id(), user2.id()))
                .approvers())
        .containsExactly(admin.id(), user.id(), user2.id());
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void createInputForComputingOwnedPaths_noImplicitApprover() {
    // CodeOwnerApprovalCheckInput#createForComputingOwnedPaths never sets an implicit approver
    assertThat(createInputForComputingOwnedPaths(ImmutableSet.of()).implicitApprover()).isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void createInputForComputingOwnedPaths_noOverride() throws Exception {
    createOwnersOverrideLabel();

    ReviewInput reviewInput = new ReviewInput().label("Owners-Override", 1);

    // self override
    requestScopeOperations.setApiUser(changeOwner);
    gApi.changes().id(changeId.toString()).current().review(reviewInput);

    // override as user
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(changeId.toString()).current().review(reviewInput);

    // override as user2
    requestScopeOperations.setApiUser(user2.id());
    gApi.changes().id(changeId.toString()).current().review(reviewInput);

    // CodeOwnerApprovalCheckInput#createForComputingOwnedPaths never sets overrides
    assertThat(createInputForComputingOwnedPaths(ImmutableSet.of()).overrides()).isEmpty();
  }

  @Test
  public void createInputForComputingOwnedPaths_noGlobalCodeOwners() {
    CodeOwnerResolverResult globalCodeOwners =
        createInputForComputingOwnedPaths(ImmutableSet.of()).globalCodeOwners();
    assertThat(globalCodeOwners.codeOwnersAccountIds()).isEmpty();
    assertThat(globalCodeOwners.ownedByAllUsers()).isFalse();
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.globalCodeOwner",
      values = {"user1@example.com", "user2@example.com"})
  public void createInputForComputingOwnedPaths_withGlobalCodeOwners() {
    CodeOwnerResolverResult globalCodeOwners =
        createInputForComputingOwnedPaths(ImmutableSet.of()).globalCodeOwners();
    assertThat(globalCodeOwners.codeOwnersAccountIds()).containsExactly(user.id(), user2.id());
    assertThat(globalCodeOwners.ownedByAllUsers()).isFalse();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  public void createInputForComputingOwnedPaths_withAllUsersAsGlobalCodeOwners() {
    CodeOwnerResolverResult globalCodeOwners =
        createInputForComputingOwnedPaths(ImmutableSet.of()).globalCodeOwners();
    assertThat(globalCodeOwners.codeOwnersAccountIds()).isEmpty();
    assertThat(globalCodeOwners.ownedByAllUsers()).isTrue();
  }

  @Test
  public void createInputForComputingOwnedPaths_withFallbackCodeOwnersNone() {
    assertThat(createInputForComputingOwnedPaths(ImmutableSet.of()).fallbackCodeOwners())
        .isEqualTo(FallbackCodeOwners.NONE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "ALL_USERS")
  public void createInputForComputingOwnedPaths_withFallbackCodeOwnersAllUsers() {
    assertThat(createInputForComputingOwnedPaths(ImmutableSet.of()).fallbackCodeOwners())
        .isEqualTo(FallbackCodeOwners.ALL_USERS);
  }

  @Test
  public void createInputForComputingOwnedPaths_checkAllOwnersIsTrue() {
    assertThat(createInputForComputingOwnedPaths(ImmutableSet.of()).checkAllOwners()).isTrue();
  }

  private void disableSelfCodeReviewApprovals() throws Exception {
    disableSelfApprovals(allProjects, "Code-Review");
  }

  private void disableSelfOwnersOverrideApprovals() throws Exception {
    disableSelfApprovals(project, "Owners-Override");
  }

  private void disableSelfApprovals(Project.NameKey project, String labelName) throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.ignoreSelfApproval = true;
    gApi.projects().name(project.get()).label(labelName).update(input);
  }

  private ChangeApi changeApi() throws RestApiException {
    return gApi.changes().id(changeId.get());
  }

  private CodeOwnerApprovalCheckInput loadInput() {
    ChangeNotes changeNotes = changeNotesFactory.create(project, changeId);
    return inputLoaderFactory.create(codeOwnersConfig, codeOwnerResolver, changeNotes).load();
  }

  private CodeOwnerApprovalCheckInput createInputForComputingOwnedPaths(
      ImmutableSet<Account.Id> accounts) {
    ChangeNotes changeNotes = changeNotesFactory.create(project, changeId);
    return CodeOwnerApprovalCheckInput.createForComputingOwnedPaths(
        codeOwnersConfig, codeOwnerResolver, changeNotes, accounts);
  }
}
