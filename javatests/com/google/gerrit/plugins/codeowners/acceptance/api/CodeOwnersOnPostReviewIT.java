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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import java.util.Collection;
import java.util.HashMap;
import org.junit.Test;

/**
 * Acceptance test for {@code com.google.gerrit.plugins.codeowners.backend.CodeOwnersOnPostReview}.
 */
public class CodeOwnersOnPostReviewIT extends AbstractCodeOwnersIT {
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
                admin.fullName(), path));
  }

  @Test
  public void changeMessageListsPathsThatAreStillApproved() throws Exception {
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
                admin.fullName(), path));
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
                admin.fullName(), path));
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
                admin.fullName(), path));
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
                admin.fullName(), path1, path2));
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
                admin.fullName(), oldPath));
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
                admin.fullName(), path));
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
                admin.fullName(), path, admin.fullName()));
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
                admin.fullName(), path, admin.fullName()));
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
                admin.fullName(), path));
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
                admin.fullName(), path));
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
                admin.fullName(), path));
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
                admin.fullName(), path4, path3, path1, path2));
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
                    + "(2 more files)\n",
                admin.fullName(), path4, path3));
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
    reviewInput.comments = reviewInput.comments = new HashMap<>();
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
                admin.fullName(), path));
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
}
