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
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import java.util.Collection;
import org.junit.Test;

/**
 * Acceptance test for {@code com.google.gerrit.plugins.codeowners.backend.CodeOwnersOnPostReview}.
 */
public class CodeOwnersOnPostReviewIT extends AbstractCodeOwnersIT {
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
                    + "%s is a code owner."
                    + " By voting Code-Review+1 the following paths are now approved by %s:\n"
                    + "* %s\n",
                admin.fullName(), admin.fullName(), path));
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
                    + "%s is a code owner."
                    + " By voting Code-Review+2 the following paths are still approved by %s:\n"
                    + "* %s\n",
                admin.fullName(), admin.fullName(), path));
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
                    + "%s is a code owner."
                    + " By removing the Code-Review vote the following paths are no longer approved by"
                    + " %s:\n"
                    + "* %s\n",
                admin.fullName(), admin.fullName(), path));
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
                    + "%s is a code owner."
                    + " By voting Code-Review-1 the following paths are no longer approved by %s:\n"
                    + "* %s\n",
                admin.fullName(), admin.fullName(), path));
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
                    + "%s is a code owner."
                    + " By voting Code-Review+1 the following paths are now approved by %s:\n"
                    + "* %s\n"
                    + "* %s\n",
                admin.fullName(), admin.fullName(), path1, path2));
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
                    + "%s is a code owner."
                    + " By voting Code-Review+1 the following paths are now approved by %s:\n"
                    + "* %s\n",
                admin.fullName(), admin.fullName(), oldPath));
  }

  @Test
  public void changeMessageNotExtendedIfUserOwnsNonOfTheFiles() throws Exception {
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
}
