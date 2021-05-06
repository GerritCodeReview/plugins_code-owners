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
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.HashMap;
import org.junit.Test;

/** Acceptance test for {@code com.google.gerrit.plugins.codeowners.backend.OnCodeOwnerApproval}. */
public class OnCodeOwnerApprovalIT extends AbstractCodeOwnersIT {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  @Test
  @GerritConfig(name = "plugin.code-owners.disabled", value = "true")
  public void changeMessageNotExtendedIfCodeOwnersFuctionalityIsDisabled() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1: Code-Review+1");
  }

  @Test
  public void changeMessageListsNewlyApprovedPaths() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path));
  }

  @Test
  public void changeMessageNotExtended_sameCodeOwnerApprovalAppliedAgain() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    recommend(changeId);

    int messageCount = gApi.changes().id(changeId).get().messages.size();

    // Apply the Code-Review+1 approval again
    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    // Check that no new change message was added.
    assertThat(messages.size()).isEqualTo(messageCount);

    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path));
  }

  @Test
  public void changeMessageNotExtended_sameCodeOwnerApprovalAppliedAgainTogetherWithOtherLabel()
      throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Other", " 0", "Approved");
    gApi.projects().name(project.get()).label("Other").create(input).get();

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

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path));

    // Apply the Code-Review+1 approval again and add an unrelated vote (Code-Review+1 is ignored).
    ReviewInput reviewInput = ReviewInput.recommend();
    reviewInput.labels.put("Other", (short) 1);
    gApi.changes().id(changeId).current().review(reviewInput);

    messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1: Other+1");
  }

  @Test
  public void changeMessageNotExtended_sameCodeOwnerApprovalAppliedAgainTogetherWithComment()
      throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Other", " 0", "Approved");
    gApi.projects().name(project.get()).label("Other").create(input).get();

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

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path));

    // Apply the Code-Review+1 approval again and add a comment (Code-Review +1 is ignored)
    ReviewInput.CommentInput commentInput = new ReviewInput.CommentInput();
    commentInput.line = 1;
    commentInput.message = "some comment";
    commentInput.path = path;
    ReviewInput reviewInput = ReviewInput.recommend();
    reviewInput.comments = new HashMap<>();
    reviewInput.comments.put(commentInput.path, Lists.newArrayList(commentInput));
    gApi.changes().id(changeId).current().review(reviewInput);

    messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1:\n\n(1 comment)");
  }

  @Test
  public void changeMessageNotExtended_sameCodeOwnerApprovalAppliedByOtherCodeOwner()
      throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .addCodeOwnerEmail(user.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path));

    // Apply the Code-Review+1 by another code owner
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(user.id()), path));
  }

  @Test
  public void changeMessageListsPathsThatAreStillApproved_approvalUpgraded() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    recommend(changeId);

    // Upgrade the approval from Code-Review+1 to Code-Review+2
    approve(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+2\n\n"
                    + "By voting Code-Review+2 the following files are still code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void
      changeMessageListsPathsThatAreStillApproved_approvalDowngraded_implicitApprovalsEnabled()
          throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    approve(changeId);

    // Downgrade the approval from Code-Review+2 to Code-Review+1
    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are still explicitly code-owner"
                    + " approved by %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void
      changeMessageListsPathsThatAreStillApproved_approvalUpgraded_implicitApprovalsEnabled()
          throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    recommend(changeId);

    // Upgrade the approval from Code-Review+1 to Code-Review+2
    approve(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+2\n\n"
                    + "By voting Code-Review+2 the following files are still explicitly code-owner"
                    + " approved by %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path));
  }

  @Test
  public void changeMessageListsPathsThatAreStillApproved_approvalDowngraded() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    approve(changeId);

    // Downgrade the approval from Code-Review+2 to Code-Review+1
    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are still code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path));
  }

  @Test
  public void changeMessageListsPathsThatAreNoLongerApproved_voteRemoved() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    recommend(changeId);

    // Remove the approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Code-Review", 0));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: -Code-Review\n\n"
                    + "By removing the Code-Review vote the following files are no longer"
                    + " code-owner approved by %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path));
  }

  @Test
  public void changeMessageListsPathsThatAreNoLongerApproved_voteChangedToNegativeValue()
      throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    recommend(changeId);

    // Vote with a negative value.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Code-Review", -1));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review-1\n\n"
                    + "By voting Code-Review-1 the following files are no longer code-owner"
                    + " approved by %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path));
  }

  @Test
  public void changeMessageListsOnlyApprovedPaths() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path1 = "foo/bar.baz";
    String path2 = "foo/baz.bar";
    String changeId =
        createChange(
                "Test Change",
                ImmutableMap.of(
                    path1,
                    "file content",
                    path2,
                    "file content",
                    "bar/foo.baz",
                    "file content",
                    "bar/baz.foo",
                    "file content"))
            .getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path1, path2));
  }

  @Test
  public void changeMessageListsOnlyApprovedPaths_fileRenamed() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    // createChangeWithFileRename creates a change with 2 patch sets
    String oldPath = "foo/bar.baz";
    String newPath = "bar/baz.bar";
    String changeId = createChangeWithFileRename(oldPath, newPath);

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 2: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), oldPath));
  }

  @Test
  public void changeMessageNotExtendedIfUserOwnsNoneOfTheFiles() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String changeId =
        createChange(
                "Test Change",
                ImmutableMap.of("bar/foo.baz", "file content", "bar/baz.foo", "file content"))
            .getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1: Code-Review+1");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "Owners-Approval+1")
  public void changeMessageNotExtendedForNonCodeOwnerApproval() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Owner Approval", " 0", "No Owner Approval");
    gApi.projects().name(project.get()).label("Owners-Approval").create(input).get();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String changeId = createChange("Test Change", "foo/bar.baz", "file content").getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1: Code-Review+1");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void changeMessageListsNewlyApprovedPathsIfTheyWereAlreadyImplicitlyApproved()
      throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now explicitly code-owner"
                    + " approved by %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void changeMessageListsPathsThatAreNoLongerExplicitlyApproved_voteRemoved()
      throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    recommend(changeId);

    // Remove the approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Code-Review", 0));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: -Code-Review\n\n"
                    + "By removing the Code-Review vote the following files are no longer"
                    + " explicitly code-owner approved by %s:\n"
                    + "* %s\n"
                    + "\n"
                    + "The listed files are still implicitly approved by %s.\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()),
                path,
                ChangeMessagesUtil.getAccountTemplate(admin.id())));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void changeMessageListsPathsThatAreNoLongerExplicitlyApproved_voteChangedToNegativeValue()
      throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    recommend(changeId);

    // Vote with a negative value.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Code-Review", -1));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review-1\n\n"
                    + "By voting Code-Review-1 the following files are no longer explicitly"
                    + " code-owner approved by %s:\n"
                    + "* %s\n"
                    + "\n"
                    + "The listed files are still implicitly approved by %s.\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()),
                path,
                ChangeMessagesUtil.getAccountTemplate(admin.id())));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void changeMessageListsNewlyApprovedPaths_noImplicitApprovalButImplicitApprovalsEnabled()
      throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange(user, "Test Change", path, "file content").getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void
      changeMessageListsPathsThatAreNoLongerApproved_voteRemoved_noImplicitApprovalButImplicitApprovalsEnabled()
          throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange(user, "Test Change", path, "file content").getChangeId();

    recommend(changeId);

    // Remove the approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Code-Review", 0));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: -Code-Review\n\n"
                    + "By removing the Code-Review vote the following files are no longer"
                    + " code-owner approved by %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void
      changeMessageListsPathsThatAreNoLongerApproved_voteChangedToNegativeValue_noImplicitApprovalButImplicitApprovalsEnabled()
          throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange(user, "Test Change", path, "file content").getChangeId();

    recommend(changeId);

    // Vote with a negative value.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Code-Review", -1));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review-1\n\n"
                    + "By voting Code-Review-1 the following files are no longer code-owner"
                    + " approved by %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxPathsInChangeMessages", value = "4")
  public void pathsInChangeMessageAreLimited_limitNotReached() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path1 = "foo/bar.baz";
    String path2 = "foo/baz.bar";
    String path3 = "bar/foo.baz";
    String path4 = "bar/baz.foo";
    String changeId =
        createChange(
                "Test Change",
                ImmutableMap.of(
                    path1,
                    "file content",
                    path2,
                    "file content",
                    path3,
                    "file content",
                    path4,
                    "file content"))
            .getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n"
                    + "* %s\n"
                    + "* %s\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path4, path3, path1, path2));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxPathsInChangeMessages", value = "3")
  public void pathsInChangeMessageAreLimited_limitReached() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path1 = "foo/bar.baz";
    String path2 = "foo/baz.bar";
    String path3 = "bar/foo.baz";
    String path4 = "bar/baz.foo";
    String path5 = "baz/foo.bar";
    String changeId =
        createChange(
                "Test Change",
                ImmutableMap.of(
                    path1,
                    "file content",
                    path2,
                    "file content",
                    path3,
                    "file content",
                    path4,
                    "file content",
                    path5,
                    "file content"))
            .getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n"
                    + "* %s\n"
                    + "* %s\n"
                    + "(more files)\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path4, path3, path5));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxPathsInChangeMessages", value = "0")
  public void pathsInChangeMessagesDisabled() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String changeId =
        createChange(
                "Test Change",
                ImmutableMap.of(
                    "foo/bar.baz",
                    "file content",
                    "foo/baz.bar",
                    "file content",
                    "bar/foo.baz",
                    "file content",
                    "bar/baz.foo",
                    "file content"))
            .getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1: Code-Review+1");
  }

  @Test
  public void changeMessageListsNewlyApprovedPathsIfCommentsAreAddedOnPostReview()
      throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    ReviewInput.CommentInput commentInput = new ReviewInput.CommentInput();
    commentInput.line = 1;
    commentInput.message = "some comment";
    commentInput.path = path;
    ReviewInput reviewInput = ReviewInput.recommend();
    reviewInput.comments = new HashMap<>();
    reviewInput.comments.put(commentInput.path, Lists.newArrayList(commentInput));
    gApi.changes().id(changeId).current().review(reviewInput);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "(1 comment)\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path));
  }

  @Test
  public void changeMessageNotExtendedIfUsersPostsOnOldPatchSet() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String changeId = createChange("Test Change", "foo/bar.baz", "file content").getChangeId();

    // create a second patch set
    amendChange(changeId);

    // vote on the first patch set
    gApi.changes().id(changeId).revision(1).review(ReviewInput.recommend());

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1: Code-Review+1");
  }

  @Test
  public void changeMessageNotExtendedIfDestinationBranchWasDeleted() throws Exception {
    String branchName = "tempBranch";
    createBranch(BranchNameKey.create(project, branchName));

    String changeId = createChange("refs/for/" + branchName).getChangeId();

    DeleteBranchesInput input = new DeleteBranchesInput();
    input.branches = ImmutableList.of(branchName);
    gApi.projects().name(project.get()).deleteBranches(input);

    // Approve by a code-owner.
    recommend(changeId);

    // If the destination branch of the change no longer exits, the owned paths cannot be computed.
    // Hence the change message cannot be extended in this case.
    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1: Code-Review+1");
  }

  @Test
  public void changeMessageExtendedIfCodeOwnerApprovalIsIgnoredDueToSelfApproval()
      throws Exception {
    setAsRootCodeOwners(admin);

    LabelDefinitionInput input = new LabelDefinitionInput();
    input.ignoreSelfApproval = true;
    gApi.projects().name(allProjects.get()).label("Code-Review").update(input);

    String changeId = createChange().getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            "Patch Set 1: Code-Review+1\n\n"
                + "The vote Code-Review+1 is ignored as code-owner approval since the label"
                + " doesn't allow self approval of the patch set uploader.\n");
  }

  @Test
  public void changeMessageExtendedIfUpgradedCodeOwnerApprovalIsIgnoredDueToSelfApproval()
      throws Exception {
    setAsRootCodeOwners(admin);

    LabelDefinitionInput input = new LabelDefinitionInput();
    input.ignoreSelfApproval = true;
    gApi.projects().name(allProjects.get()).label("Code-Review").update(input);

    String changeId = createChange().getChangeId();

    recommend(changeId);

    // Upgrade the approval from Code-Review+1 to Code-Review+2
    approve(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            "Patch Set 1: Code-Review+2\n\n"
                + "The vote Code-Review+2 is ignored as code-owner approval since the label"
                + " doesn't allow self approval of the patch set uploader.\n");
  }

  @Test
  public void changeMessageExtendedIfDowngradedCodeOwnerApprovalIsIgnoredDueToSelfApproval()
      throws Exception {
    setAsRootCodeOwners(admin);

    LabelDefinitionInput input = new LabelDefinitionInput();
    input.ignoreSelfApproval = true;
    gApi.projects().name(allProjects.get()).label("Code-Review").update(input);

    String changeId = createChange().getChangeId();

    approve(changeId);

    // Downgrade the approval from Code-Review+2 to Code-Review+1
    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            "Patch Set 1: Code-Review+1\n\n"
                + "The vote Code-Review+1 is ignored as code-owner approval since the label"
                + " doesn't allow self approval of the patch set uploader.\n");
  }

  @Test
  public void changeMessageNotExtendedIfIgnoredCodeOwnerApprovalIsRemoved() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.ignoreSelfApproval = true;
    gApi.projects().name(allProjects.get()).label("Code-Review").update(input);

    String changeId = createChange().getChangeId();

    recommend(changeId);

    // Remove the code-owner approval
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Code-Review", 0));

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1: -Code-Review");
  }

  @Test
  public void changeMessageExtendedIfNonSelfApprovalCodeOwnerApprovalIsApplied() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    LabelDefinitionInput input = new LabelDefinitionInput();
    input.ignoreSelfApproval = true;
    gApi.projects().name(allProjects.get()).label("Code-Review").update(input);

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(user.id()), path));
  }

  @Test
  public void changeMessageNotExtendedIfNonApprovalIsDowngraded() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    // Apply the Code-Review-1.
    gApi.changes().id(changeId).current().review(ReviewInput.dislike());

    // Change Code-Review-1 to Code-Review-2
    gApi.changes().id(changeId).current().review(ReviewInput.reject());

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;

    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1: Code-Review-2");
  }
}
