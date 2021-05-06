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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.DeleteBranchesInput;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.server.ChangeMessagesUtil;
import java.util.Collection;
import org.junit.Test;

/**
 * Acceptance test for {@code com.google.gerrit.plugins.codeowners.backend.CodeOwnersOnAddReviewer}.
 */
public class CodeOwnersOnAddReviewerIT extends AbstractCodeOwnersIT {
  @Test
  @GerritConfig(name = "plugin.code-owners.disabled", value = "true")
  public void noChangeMessageAddedIfCodeOwners
  ctionalityIsDisabled() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    String changeId = createChange("Test Change", "foo/bar.baz", "file content").getChangeId();

    gApi.changes().id(changeId).addReviewer(user.email());

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).isEqualTo("Uploaded patch set 1.");
  }

  @Test
  public void noChangeMessageAddedIfReviewerIsNotACodeOwner() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String changeId = createChange("Test Change", "foo/bar.baz", "file content").getChangeId();

    gApi.changes().id(changeId).addReviewer(user.email());

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).isEqualTo("Uploaded patch set 1.");
  }

  @Test
  public void changeMessageListsOwnedPaths() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    gApi.changes().id(changeId).addReviewer(user.email());

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "%s who was added as reviewer owns the following files:\n* %s\n",
                ChangeMessagesUtil.getAccountTemplate(user.id()), path));
  }

  @Test
  public void changeMessageListsOnlyOwnedPaths() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
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

    gApi.changes().id(changeId).addReviewer(user.email());

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "%s who was added as reviewer owns the following files:\n* %s\n* %s\n",
                ChangeMessagesUtil.getAccountTemplate(user.id()), path1, path2));
  }

  @Test
  public void noChangeMessageAddedIfSameCodeOwnerIsAddedAsReviewerAgain() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    gApi.changes().id(changeId).addReviewer(user.email());

    int messageCount = gApi.changes().id(changeId).get().messages.size();

    // Add the same code owner as reviewer again.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Check that no new change message was added.
    assertThat(gApi.changes().id(changeId).get().messages.size()).isEqualTo(messageCount);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxPathsInChangeMessages", value = "4")
  public void pathsInChangeMessageAreLimited_limitNotReached() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
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

    gApi.changes().id(changeId).addReviewer(user.email());

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "%s who was added as reviewer owns the following files:\n"
                    + "* %s\n"
                    + "* %s\n"
                    + "* %s\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(user.id()), path4, path3, path1, path2));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxPathsInChangeMessages", value = "3")
  public void pathsInChangeMessageAreLimited_limitReached() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
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

    gApi.changes().id(changeId).addReviewer(user.email());

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "%s who was added as reviewer owns the following files:\n"
                    + "* %s\n"
                    + "* %s\n"
                    + "* %s\n"
                    + "(more files)\n",
                ChangeMessagesUtil.getAccountTemplate(user.id()), path4, path3, path5));
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

    gApi.changes().id(changeId).addReviewer(user.email());

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).isEqualTo("Uploaded patch set 1.");
  }

  @Test
  public void noChangeMessageAddedIfDestinationBranchWasDeleted() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();

    String branchName = "tempBranch";
    createBranch(BranchNameKey.create(project, branchName));

    String changeId = createChange("refs/for/" + branchName).getChangeId();

    DeleteBranchesInput input = new DeleteBranchesInput();
    input.branches = ImmutableList.of(branchName);
    gApi.projects().name(project.get()).deleteBranches(input);

    gApi.changes().id(changeId).addReviewer(user.email());

    // If the destination branch of the change no longer exits, the owned paths cannot be computed.
    // Hence no change message is added in this case.
    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).isEqualTo("Uploaded patch set 1.");
  }

  @Test
  public void changeMessageListsOwnedPathsIfReviewerIsAddedViaPostReview() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    String path = "foo/bar.baz";
    String changeId = createChange("Test Change", path, "file content").getChangeId();

    // Add reviewer via PostReview.
    gApi.changes().id(changeId).current().review(ReviewInput.create().reviewer(user.email()));
    gApi.changes().id(changeId).addReviewer(user.email());

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "%s who was added as reviewer owns the following files:\n* %s\n",
                ChangeMessagesUtil.getAccountTemplate(user.id()), path));
  }

  @Test
  public void reviewerAndCodeOwnerApprovalAddedAtTheSameTime() throws Exception {
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

    // 'admin' grants a code owner approval (Code-Review+1) and adds 'user' as reviewer.
    gApi.changes().id(changeId).current().review(ReviewInput.recommend().reviewer(user.email()));

    // We expect that 2 changes messages are added:
    // 1. change message listing the paths that were approved by voting Code-Review+1
    // 2. change message listing the paths owned by the new reviewer
    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.get(messages, messages.size() - 2).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                ChangeMessagesUtil.getAccountTemplate(admin.id()), path));
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "%s who was added as reviewer owns the following files:\n* %s\n",
                ChangeMessagesUtil.getAccountTemplate(user.id()), path));
  }
}
