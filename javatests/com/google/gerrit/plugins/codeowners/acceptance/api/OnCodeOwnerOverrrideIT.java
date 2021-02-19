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
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.DeleteBranchesInput;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.HashMap;
import java.util.regex.Pattern;
import org.junit.Test;

/** Acceptance test for {@code com.google.gerrit.plugins.codeowners.backend.OnCodeOwnerOverride}. */
public class OnCodeOwnerOverrrideIT extends AbstractCodeOwnersIT {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  @Test
  @GerritConfig(name = "plugin.code-owners.disabled", value = "true")
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeMessageNotExtendedIfCodeOwnersFuctionalityIsDisabled() throws Exception {
    createOwnersOverrideLabel();

    String changeId = createChange().getChangeId();

    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1: Owners-Override+1");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeMessageExtendedIfCodeOwnersOverrideIsApplied() throws Exception {
    createOwnersOverrideLabel();

    String changeId = createChange().getChangeId();

    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Owners-Override+1\n\n"
                    + "By voting Owners-Override+1 the code-owners submit requirement is overridden by %s\n",
                admin.fullName()));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeMessageNotExtendedIfCodeOwnersOverrideIsReApplied() throws Exception {
    createOwnersOverrideLabel();

    String changeId = createChange().getChangeId();

    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    int messageCount = gApi.changes().id(changeId).get().messages.size();

    // Apply the Owners-Override+1 approval again
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // Check that a no new change message was added.
    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(messages.size()).isEqualTo(messageCount);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeMessageExtendedIfCodeOwnersOverrideIsAppliedByOtherUser() throws Exception {
    createOwnersOverrideLabel();

    String changeId = createChange().getChangeId();

    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Owners-Override+1\n\n"
                    + "By voting Owners-Override+1 the code-owners submit requirement is overridden by %s\n",
                admin.fullName()));

    // Apply the Owners-Override+1 approval by another user
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Owners-Override+1\n\n"
                    + "By voting Owners-Override+1 the code-owners submit requirement is overridden by %s\n",
                user.fullName()));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeMessageExtendedIfCodeOwnersOverrideIsUpgraded() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+2", "Override", "+1", "Override", " 0", "No Override");
    gApi.projects().name(project.get()).label("Owners-Override").create(input).get();

    // Allow to vote on the Owners-Override label.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            TestProjectUpdate.allowLabel("Owners-Override")
                .range(0, 2)
                .ref("refs/*")
                .group(REGISTERED_USERS)
                .build())
        .update();

    String changeId = createChange().getChangeId();

    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // Upgrade the approval from Owners-Override+1 to Owners-Override+2
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 2));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Owners-Override+2\n\n"
                    + "By voting Owners-Override+2 the code-owners submit requirement is still"
                    + " overridden by %s\n",
                admin.fullName()));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeMessageExtendedIfCodeOwnersOverrideIsDowngraded() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+2", "Override", "+1", "Override", " 0", "No Override");
    gApi.projects().name(project.get()).label("Owners-Override").create(input).get();

    // Allow to vote on the Owners-Override label.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            TestProjectUpdate.allowLabel("Owners-Override")
                .range(0, 2)
                .ref("refs/*")
                .group(REGISTERED_USERS)
                .build())
        .update();

    String changeId = createChange().getChangeId();

    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 2));

    // Downgrade the approval from Owners-Override+2 to Owners-Override+1
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Owners-Override+1\n\n"
                    + "By voting Owners-Override+1 the code-owners submit requirement is still"
                    + " overridden by %s\n",
                admin.fullName()));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeMessageExtendedIfCodeOwnersOverrideIsRemoved() throws Exception {
    createOwnersOverrideLabel();

    String changeId = createChange().getChangeId();

    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // Remove the override approval
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 0));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: -Owners-Override\n\n"
                    + "By removing the Owners-Override vote the code-owners submit requirement is"
                    + " no longer overridden by %s\n",
                admin.fullName()));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeMessageExtendedIfCodeOwnersOverrideIsChangedToNegativeValue() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Override", " 0", "No Override", "-1", "No Override");
    gApi.projects().name(project.get()).label("Owners-Override").create(input).get();

    // Allow to vote on the Owners-Override label.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            TestProjectUpdate.allowLabel("Owners-Override")
                .range(-1, 1)
                .ref("refs/*")
                .group(REGISTERED_USERS)
                .build())
        .update();

    String changeId = createChange().getChangeId();

    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // Vote with Owners-Override-1
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", -1));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Owners-Override-1\n\n"
                    + "By voting Owners-Override-1 the code-owners submit requirement is no longer"
                    + " overridden by %s\n",
                admin.fullName()));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeMessageNotExtendedIfNonCodeOwnersOverrideIsApplied() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Approval", " 0", "No Approval");
    gApi.projects().name(project.get()).label("Other").create(input).get();

    // Allow to vote on the Owners-Override label.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            TestProjectUpdate.allowLabel("Other")
                .range(0, 1)
                .ref("refs/*")
                .group(REGISTERED_USERS)
                .build())
        .update();

    String changeId = createChange().getChangeId();

    gApi.changes().id(changeId).current().review(new ReviewInput().label("Other", 1));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1: Other+1");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeMessageExtendedIfCodeOwnersOverrideIsAppliedTogetherWithComment()
      throws Exception {
    createOwnersOverrideLabel();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    ReviewInput.CommentInput commentInput = new ReviewInput.CommentInput();
    commentInput.line = 1;
    commentInput.message = "some comment";
    commentInput.path = path;
    ReviewInput reviewInput = new ReviewInput();
    reviewInput.labels = new HashMap<>();
    reviewInput.labels.put("Owners-Override", (short) 1);
    reviewInput.comments = new HashMap<>();
    reviewInput.comments.put(commentInput.path, Lists.newArrayList(commentInput));
    gApi.changes().id(changeId).current().review(reviewInput);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Owners-Override+1\n\n"
                    + "(1 comment)\n\n"
                    + "By voting Owners-Override+1 the code-owners submit requirement is"
                    + " overridden by %s\n",
                admin.fullName()));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeMessageNotExtendedIfCodeOwnersOverrideIsAppliedOnOldPatchSet()
      throws Exception {
    createOwnersOverrideLabel();

    String changeId = createChange().getChangeId();

    // create a second patch set
    amendChange(changeId);

    // vote on the first patch set
    gApi.changes().id(changeId).revision(1).review(new ReviewInput().label("Owners-Override", 1));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1: Owners-Override+1");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeMessageExtendedIfDestinationBranchWasDeleted() throws Exception {
    createOwnersOverrideLabel();

    String branchName = "tempBranch";
    createBranch(BranchNameKey.create(project, branchName));

    String changeId = createChange("refs/for/" + branchName).getChangeId();

    DeleteBranchesInput input = new DeleteBranchesInput();
    input.branches = ImmutableList.of(branchName);
    gApi.projects().name(project.get()).deleteBranches(input);

    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Owners-Override+1\n\n"
                    + "By voting Owners-Override+1 the code-owners submit requirement is overridden by %s\n",
                admin.fullName()));
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.overrideApproval",
      values = {"Owners-Override+1", "Global-Override+1"})
  public void changeMessageExtendedIfMultipleCodeOwnersOverridesAreAppliedTogether()
      throws Exception {
    createOwnersOverrideLabel();
    createOwnersOverrideLabel("Global-Override");

    String changeId = createChange().getChangeId();

    ReviewInput reviewInput = new ReviewInput();
    reviewInput.labels = new HashMap<>();
    reviewInput.labels.put("Owners-Override", (short) 1);
    reviewInput.labels.put("Global-Override", (short) 1);
    gApi.changes().id(changeId).current().review(reviewInput);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .matches(
            Pattern.quote("Patch Set 1: ")
                + "("
                + Pattern.quote("Owners-Override+1 Global-Override+1")
                + "|"
                + Pattern.quote("Global-Override+1 Owners-Override+1")
                + ")"
                + Pattern.quote(
                    String.format(
                        "\n\nBy voting Global-Override+1 the code-owners submit requirement is"
                            + " overridden by %s\n\n"
                            + "By voting Owners-Override+1 the code-owners submit requirement is"
                            + " overridden by %s\n",
                        admin.fullName(), admin.fullName())));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeMessageExtendedIfCodeOwnersOverrideAndCodeOwnerApprovalAreAppliedTogether()
      throws Exception {
    setAsRootCodeOwners(admin);

    createOwnersOverrideLabel();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    ReviewInput reviewInput = new ReviewInput();
    reviewInput.labels = new HashMap<>();
    reviewInput.labels.put("Owners-Override", (short) 1);
    reviewInput.labels.put("Code-Review", (short) 1);
    gApi.changes().id(changeId).current().review(reviewInput);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).startsWith("Patch Set 1: ");
    assertThat(Iterables.getLast(messages).message)
        .containsMatch(
            "("
                + Pattern.quote("Owners-Override+1 Code-Review+1")
                + "|"
                + Pattern.quote("Code-Review+1 Owners-Override+1")
                + ")");
    assertThat(Iterables.getLast(messages).message)
        .contains(
            String.format(
                "By voting Owners-Override+1 the code-owners submit requirement is"
                    + " overridden by %s\n",
                admin.fullName()));
    assertThat(Iterables.getLast(messages).message)
        .contains(
            String.format(
                "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                admin.fullName(), path));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeMessageExtendedIfCodeOwnerOverrideIsIgnoredDueToSelfApproval()
      throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Override", " 0", "No Override");
    input.ignoreSelfApproval = true;
    gApi.projects().name(project.get()).label("Owners-Override").create(input);

    // Allow to vote on the Owners-Override label.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            TestProjectUpdate.allowLabel("Owners-Override")
                .range(0, 1)
                .ref("refs/*")
                .group(REGISTERED_USERS)
                .build())
        .update();

    String changeId = createChange().getChangeId();

    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            "Patch Set 1: Owners-Override+1\n\n"
                + "The vote Owners-Override+1 is ignored as code-owner override since the label"
                + " doesn't allow self approval of the patch set uploader.\n");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeMessageExtendedIfUpgradedCodeOwnerOverrideIsIgnoredDueToSelfApproval()
      throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+2", "Override", "+1", "Override", " 0", "No Override");
    input.ignoreSelfApproval = true;
    gApi.projects().name(project.get()).label("Owners-Override").create(input);

    // Allow to vote on the Owners-Override label.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            TestProjectUpdate.allowLabel("Owners-Override")
                .range(0, 2)
                .ref("refs/*")
                .group(REGISTERED_USERS)
                .build())
        .update();

    String changeId = createChange().getChangeId();

    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // Upgrade the approval from Owners-Override+1 to Owners-Override+2
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 2));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            "Patch Set 1: Owners-Override+2\n\n"
                + "The vote Owners-Override+2 is ignored as code-owner override since the label"
                + " doesn't allow self approval of the patch set uploader.\n");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeMessageExtendedIfDowngradedCodeOwnerOverrideIsIgnoredDueToSelfApproval()
      throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+2", "Override", "+1", "Override", " 0", "No Override");
    input.ignoreSelfApproval = true;
    gApi.projects().name(project.get()).label("Owners-Override").create(input);

    // Allow to vote on the Owners-Override label.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            TestProjectUpdate.allowLabel("Owners-Override")
                .range(0, 2)
                .ref("refs/*")
                .group(REGISTERED_USERS)
                .build())
        .update();

    String changeId = createChange().getChangeId();

    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 2));

    // Downgrade the approval from Owners-Override+2 to Owners-Override+1
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            "Patch Set 1: Owners-Override+1\n\n"
                + "The vote Owners-Override+1 is ignored as code-owner override since the label"
                + " doesn't allow self approval of the patch set uploader.\n");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeMessageNotExtendedIfIgnoredCodeOwnerOverrideIsRemoved() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Override", " 0", "No Override");
    input.ignoreSelfApproval = true;
    gApi.projects().name(project.get()).label("Owners-Override").create(input);

    // Allow to vote on the Owners-Override label.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            TestProjectUpdate.allowLabel("Owners-Override")
                .range(0, 1)
                .ref("refs/*")
                .group(REGISTERED_USERS)
                .build())
        .update();

    String changeId = createChange().getChangeId();

    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // Remove the override approval
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 0));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1: -Owners-Override");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void changeMessageExtendedIfNonSelfApprovalCodeOwnerOverrideIsApplied() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Override", " 0", "No Override");
    input.ignoreSelfApproval = true;
    gApi.projects().name(project.get()).label("Owners-Override").create(input);

    // Allow to vote on the Owners-Override label.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            TestProjectUpdate.allowLabel("Owners-Override")
                .range(0, 1)
                .ref("refs/*")
                .group(REGISTERED_USERS)
                .build())
        .update();

    String changeId = createChange().getChangeId();

    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Owners-Override+1\n\n"
                    + "By voting Owners-Override+1 the code-owners submit requirement is overridden by %s\n",
                user.fullName()));
  }
}
