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
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.change.TestChange;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.DeleteBranchesInput;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.server.util.AccountTemplateUtil;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.Test;

/**
 * Acceptance test for {@code com.google.gerrit.plugins.codeowners.backend.OnCodeOwnerApproval}.
 *
 * <p>For tests the change message that is posted when a code owner approval is applied, is added
 * synchronously by default (see {@link AbstractCodeOwnersIT #defaultConfig()}). Tests that want to
 * verify the asynchronous posting of this change message need to set {@code
 * plugin.code-owners.enableAsyncMessageOnCodeOwnerApproval=true} in {@code gerrit.config}
 * explicitly (by using the {@link GerritConfig} annotation).
 */
public class OnCodeOwnerApprovalIT extends AbstractCodeOwnersIT {
  private static String TEST_PATH = "foo/bar.baz";
  private static String TEST_PATH_ESCAPED = "`foo/bar.baz`";

  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private ChangeOperations changeOperations;

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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1: Code-Review+1");
  }

  @Test
  public void changeMessageNotExtendedIfInvalidCodeOwnerConfigFilesExist() throws Exception {
    createNonParseableCodeOwnerConfig(getCodeOwnerConfigFileName());

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                AccountTemplateUtil.getAccountTemplate(admin.id()), TEST_PATH_ESCAPED));
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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

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
                AccountTemplateUtil.getAccountTemplate(admin.id()), TEST_PATH_ESCAPED));
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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                AccountTemplateUtil.getAccountTemplate(admin.id()), TEST_PATH_ESCAPED));

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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                AccountTemplateUtil.getAccountTemplate(admin.id()), TEST_PATH_ESCAPED));

    // Apply the Code-Review+1 approval again and add a comment (Code-Review +1 is ignored)
    ReviewInput.CommentInput commentInput = new ReviewInput.CommentInput();
    commentInput.line = 1;
    commentInput.message = "some comment";
    commentInput.path = TEST_PATH;
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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                AccountTemplateUtil.getAccountTemplate(admin.id()), TEST_PATH_ESCAPED));

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
                AccountTemplateUtil.getAccountTemplate(user.id()), TEST_PATH_ESCAPED));
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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

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
                AccountTemplateUtil.getAccountTemplate(admin.id()), TEST_PATH_ESCAPED));
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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

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
                AccountTemplateUtil.getAccountTemplate(admin.id()), TEST_PATH_ESCAPED));
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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

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
                AccountTemplateUtil.getAccountTemplate(admin.id()), TEST_PATH_ESCAPED));
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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

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
                AccountTemplateUtil.getAccountTemplate(admin.id()), TEST_PATH_ESCAPED));
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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

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
                AccountTemplateUtil.getAccountTemplate(admin.id()), TEST_PATH_ESCAPED));
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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

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
                AccountTemplateUtil.getAccountTemplate(admin.id()), TEST_PATH_ESCAPED));
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

    String testPath1 = "foo/bar.baz";
    String testPath1Escaped = "`foo/bar.baz`";
    String testPath2 = "foo/baz.bar";
    String testPath2Escaped = "`foo/baz.bar`";
    String changeId =
        createChange(
                "Test Change",
                ImmutableMap.of(
                    testPath1,
                    "file content",
                    testPath2,
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
                AccountTemplateUtil.getAccountTemplate(admin.id()),
                testPath1Escaped,
                testPath2Escaped));
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
    String oldPathEscaped = "`foo/bar.baz`";
    String newPath = "bar/baz.bar";
    TestChange change = createChangeWithFileRename(oldPath, newPath);

    recommend(change.changeId());

    Collection<ChangeMessageInfo> messages = gApi.changes().id(change.id()).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set %s: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                changeOperations.change(change.id()).currentPatchset().get().patchsetId().get(),
                AccountTemplateUtil.getAccountTemplate(admin.id()),
                oldPathEscaped));
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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now explicitly code-owner"
                    + " approved by %s:\n"
                    + "* %s\n",
                AccountTemplateUtil.getAccountTemplate(admin.id()), TEST_PATH_ESCAPED));
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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

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
                AccountTemplateUtil.getAccountTemplate(admin.id()),
                TEST_PATH_ESCAPED,
                AccountTemplateUtil.getAccountTemplate(admin.id())));
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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

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
                AccountTemplateUtil.getAccountTemplate(admin.id()),
                TEST_PATH_ESCAPED,
                AccountTemplateUtil.getAccountTemplate(admin.id())));
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

    String changeId = createChange(user, "Test Change", TEST_PATH, "file content").getChangeId();

    recommend(changeId);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s:\n"
                    + "* %s\n",
                AccountTemplateUtil.getAccountTemplate(admin.id()), TEST_PATH_ESCAPED));
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

    String changeId = createChange(user, "Test Change", TEST_PATH, "file content").getChangeId();

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
                AccountTemplateUtil.getAccountTemplate(admin.id()), TEST_PATH_ESCAPED));
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

    String changeId = createChange(user, "Test Change", TEST_PATH, "file content").getChangeId();

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
                AccountTemplateUtil.getAccountTemplate(admin.id()), TEST_PATH_ESCAPED));
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

    String testPath1 = "foo/bar.baz";
    String testPath1Escaped = "`foo/bar.baz`";
    String testPath2 = "foo/baz.bar";
    String testPath2Escaped = "`foo/baz.bar`";
    String testPath3 = "bar/foo.baz";
    String testPath3Escaped = "`bar/foo.baz`";
    String testPath4 = "bar/baz.foo";
    String testPath4Escaped = "`bar/baz.foo`";
    String changeId =
        createChange(
                "Test Change",
                ImmutableMap.of(
                    testPath1,
                    "file content",
                    testPath2,
                    "file content",
                    testPath3,
                    "file content",
                    testPath4,
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
                AccountTemplateUtil.getAccountTemplate(admin.id()),
                testPath4Escaped,
                testPath3Escaped,
                testPath1Escaped,
                testPath2Escaped));
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

    String testPath1 = "foo/bar.baz";
    String testPath2 = "foo/baz.bar";
    String testPath3 = "bar/foo.baz";
    String testPath3Escaped = "`bar/foo.baz`";
    String testPath4 = "bar/baz.foo";
    String testPath4Escaped = "`bar/baz.foo`";
    String testPath5 = "baz/foo.bar";
    String testPath5Escaped = "`baz/foo.bar`";
    String changeId =
        createChange(
                "Test Change",
                ImmutableMap.of(
                    testPath1,
                    "file content",
                    testPath2,
                    "file content",
                    testPath3,
                    "file content",
                    testPath4,
                    "file content",
                    testPath5,
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
                AccountTemplateUtil.getAccountTemplate(admin.id()),
                testPath4Escaped,
                testPath3Escaped,
                testPath5Escaped));
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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

    ReviewInput.CommentInput commentInput = new ReviewInput.CommentInput();
    commentInput.line = 1;
    commentInput.message = "some comment";
    commentInput.path = TEST_PATH;
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
                AccountTemplateUtil.getAccountTemplate(admin.id()), TEST_PATH_ESCAPED));
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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

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
                AccountTemplateUtil.getAccountTemplate(user.id()), TEST_PATH_ESCAPED));
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

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

    // Apply the Code-Review-1.
    gApi.changes().id(changeId).current().review(ReviewInput.dislike());

    // Change Code-Review-1 to Code-Review-2
    gApi.changes().id(changeId).current().review(ReviewInput.reject());

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;

    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1: Code-Review-2");
  }

  @Test
  public void extendedChangeMessageIsIncludedInEmailNotification() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

    // Do the voting as a different user to trigger an email notification (if the only recipient is
    // also the sender the email is omitted).
    requestScopeOperations.setApiUser(user.id());

    sender.clear();

    recommend(changeId);

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.body())
        .contains(
            String.format(
                "Patch Set 1: Code-Review+1\n\n"
                    + "By voting Code-Review+1 the following files are now code-owner approved by"
                    + " %s <%s>:\n"
                    + "* %s\n",
                user.fullName(), user.email(), TEST_PATH_ESCAPED));
  }

  @Test
  public void markdownCharactersInPathsAreEscaped() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();

    testMarkdownCharactersInPathsAreEscaped('`', user);
  }

  private void testMarkdownCharactersInPathsAreEscaped(
      char markdownCharacter, TestAccount codeOwner) throws Exception {
    String testPath = markdownCharacter + "foo" + markdownCharacter + ".bar";
    String testPathEscaped = "`\\" + markdownCharacter + "foo\\" + markdownCharacter + ".bar`";

    String changeId = createChange("Test Change", testPath, "file content").getChangeId();

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
                AccountTemplateUtil.getAccountTemplate(codeOwner.id()), testPathEscaped));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableAsyncMessageOnCodeOwnerApproval", value = "true")
  public void changeMessageListsNewlyApprovedPaths_async() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    String changeId = createChange("Test Change", TEST_PATH, "file content").getChangeId();

    int numberOfChangeMessages = gApi.changes().id(changeId).get().messages.size();

    recommend(changeId);

    // expect that 2 changes messages are posted, one for applying the approval and one to inform
    // about the owned paths
    int expectedNumberOfChangeMessages = numberOfChangeMessages + 2;

    assertAsyncChangeMessage(
        changeId,
        String.format(
            "By voting Code-Review+1 the following files are now code-owner approved by"
                + " %s:\n"
                + "* %s\n",
            AccountTemplateUtil.getAccountTemplate(admin.id()), TEST_PATH_ESCAPED),
        expectedNumberOfChangeMessages);
  }

  private void assertAsyncChangeMessage(
      String changeId, String expectedChangeMessage, int expectedNumberOfChangeMessages)
      throws Exception {
    assertAsync(
        () -> {
          Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
          assertThat(Iterables.getLast(messages).message).isEqualTo(expectedChangeMessage);
          assertThat(messages).hasSize(expectedNumberOfChangeMessages);
          return null;
        });
  }

  @CanIgnoreReturnValue
  private <T> T assertAsync(Callable<T> assertion) throws Exception {
    return RetryerBuilder.<T>newBuilder()
        .retryIfException(t -> true)
        .withStopStrategy(
            StopStrategies.stopAfterDelay(Duration.ofSeconds(3).toMillis(), MILLISECONDS))
        .build()
        .call(() -> assertion.call());
  }
}
