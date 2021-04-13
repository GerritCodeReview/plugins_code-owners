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

package com.google.gerrit.plugins.codeowners.backend;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusSubject.assertThatCollection;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.DeleteBranchesInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusSubject;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link CodeOwnerApprovalCheck}.
 *
 * <p>Further tests with fallback code owners are implemented in {@link
 * CodeOwnerApprovalCheckWithAllUsersAsFallbackCodeOwnersTest} and the functionality of {@link
 * CodeOwnerApprovalCheck#getFileStatusesForAccount(ChangeNotes,
 * com.google.gerrit.entities.PatchSet, com.google.gerrit.entities.Account.Id)} is covered by {@link
 * CodeOwnerApprovalCheckForAccountTest}.
 */
public class CodeOwnerApprovalCheckTest extends AbstractCodeOwnersTest {
  @Inject private ChangeNotes.Factory changeNotesFactory;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  private CodeOwnerApprovalCheck codeOwnerApprovalCheck;
  private CodeOwnerConfigOperations codeOwnerConfigOperations;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerApprovalCheck = plugin.getSysInjector().getInstance(CodeOwnerApprovalCheck.class);
    codeOwnerConfigOperations =
        plugin.getSysInjector().getInstance(CodeOwnerConfigOperations.class);
  }

  @Test
  public void cannotGetStatusesForNullChangeNotes() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> codeOwnerApprovalCheck.getFileStatusesAsSet(/* changeNotes= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("changeNotes");
  }

  @Test
  public void getStatusForFileAddition_insufficientReviewers() throws Exception {
    TestAccount user2 = accountCreator.user2();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Add a reviewer that is not a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void getStatusForFileModification_insufficientReviewers() throws Exception {
    TestAccount user2 = accountCreator.user2();

    Path path = Paths.get("/foo/bar.baz");
    createChange("Test Change", JgitPath.of(path).get(), "file content").getChangeId();
    String changeId =
        createChange("Change Modifying A File", JgitPath.of(path).get(), "new file content")
            .getChangeId();

    // Add a reviewer that is not a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.modification(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void getStatusForFileDeletion_insufficientReviewers() throws Exception {
    TestAccount user2 = accountCreator.user2();

    Path path = Paths.get("/foo/bar.baz");
    String changeId = createChangeWithFileDeletion(path);

    // Add a reviewer that is not a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.deletion(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void getStatusForFileRename_insufficientReviewers() throws Exception {
    TestAccount user2 = accountCreator.user2();

    Path oldPath = Paths.get("/foo/old.bar");
    Path newPath = Paths.get("/foo/new.bar");
    String changeId = createChangeWithFileRename(oldPath, newPath);

    // Add a reviewer that is not a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.rename(
                oldPath,
                CodeOwnerStatus.INSUFFICIENT_REVIEWERS,
                newPath,
                CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void getStatusForFileAddition_pending() throws Exception {
    TestAccount user2 = accountCreator.user2();

    setAsRootCodeOwners(user);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Add a reviewer that is a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.PENDING));
  }

  @Test
  public void getStatusForFileModification_pending() throws Exception {
    TestAccount user2 = accountCreator.user2();

    setAsRootCodeOwners(user);

    Path path = Paths.get("/foo/bar.baz");
    createChange("Test Change", JgitPath.of(path).get(), "file content").getChangeId();
    String changeId =
        createChange("Change Modifying A File", JgitPath.of(path).get(), "new file content")
            .getChangeId();

    // Add a reviewer that is a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.modification(path, CodeOwnerStatus.PENDING));
  }

  @Test
  public void getStatusForFileDeletion_pending() throws Exception {
    TestAccount user2 = accountCreator.user2();

    setAsRootCodeOwners(user);

    Path path = Paths.get("/foo/bar.baz");
    String changeId = createChangeWithFileDeletion(path);

    // Add a reviewer that is a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.deletion(path, CodeOwnerStatus.PENDING));
  }

  @Test
  public void getStatusForFileRename_pendingOldPath() throws Exception {
    TestAccount user2 = accountCreator.user2();

    setAsCodeOwners("/foo/bar/", user);

    Path oldPath = Paths.get("/foo/bar/abc.txt");
    Path newPath = Paths.get("/foo/baz/abc.txt");
    String changeId = createChangeWithFileRename(oldPath, newPath);

    // Add a reviewer that is a code owner old path.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.rename(
                oldPath, CodeOwnerStatus.PENDING, newPath, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void getStatusForFileRename_pendingNewPath() throws Exception {
    TestAccount user2 = accountCreator.user2();

    setAsCodeOwners("/foo/baz/", user);

    Path oldPath = Paths.get("/foo/bar/abc.txt");
    Path newPath = Paths.get("/foo/baz/abc.txt");
    String changeId = createChangeWithFileRename(oldPath, newPath);

    // Add a reviewer that is a code owner of the new path.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.rename(
                oldPath, CodeOwnerStatus.INSUFFICIENT_REVIEWERS, newPath, CodeOwnerStatus.PENDING));
  }

  @Test
  public void getStatusForFileAddition_approved() throws Exception {
    setAsRootCodeOwners(user);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Add a Code-Review+1 from a code owner (by default this counts as code owner approval).
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.APPROVED));
  }

  @Test
  public void getStatusForFileModification_approved() throws Exception {
    setAsRootCodeOwners(user);

    Path path = Paths.get("/foo/bar.baz");
    createChange("Test Change", JgitPath.of(path).get(), "file content").getChangeId();
    String changeId =
        createChange("Change Modifying A File", JgitPath.of(path).get(), "new file content")
            .getChangeId();

    // Add a Code-Review+1 from a code owner (by default this counts as code owner approval).
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.modification(path, CodeOwnerStatus.APPROVED));
  }

  @Test
  public void getStatusForFileDeletion_approved() throws Exception {
    setAsRootCodeOwners(user);

    Path path = Paths.get("/foo/bar.baz");
    String changeId = createChangeWithFileDeletion(path);

    // Add a Code-Review+1 from a code owner (by default this counts as code owner approval).
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.deletion(path, CodeOwnerStatus.APPROVED));
  }

  @Test
  public void getStatusForFileRename_approvedOldPath() throws Exception {
    setAsCodeOwners("/foo/bar/", user);

    Path oldPath = Paths.get("/foo/bar/abc.txt");
    Path newPath = Paths.get("/foo/baz/abc.txt");
    String changeId = createChangeWithFileRename(oldPath, newPath);

    // Add a Code-Review+1 from a code owner of the old path (by default this counts as code owner
    // approval).
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.rename(
                oldPath,
                CodeOwnerStatus.APPROVED,
                newPath,
                CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void getStatusForFileRename_approvedNewPath() throws Exception {
    setAsCodeOwners("/foo/baz/", user);

    Path oldPath = Paths.get("/foo/bar/abc.txt");
    Path newPath = Paths.get("/foo/baz/abc.txt");
    String changeId = createChangeWithFileRename(oldPath, newPath);

    // Add a Code-Review+1 from a code owner of the new path (by default this counts as code owner
    // approval).
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.rename(
                oldPath,
                CodeOwnerStatus.INSUFFICIENT_REVIEWERS,
                newPath,
                CodeOwnerStatus.APPROVED));
  }

  @Test
  public void getStatusForFileAddition_noImplicitApprovalByPatchSetUploader() throws Exception {
    testImplicitApprovalByPatchSetUploaderOnGetStatusForFileAddition(
        /* implicitApprovalsEnabled= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void getStatusForFileAddition_withImplicitApprovalByPatchSetUploader() throws Exception {
    testImplicitApprovalByPatchSetUploaderOnGetStatusForFileAddition(
        /* implicitApprovalsEnabled= */ true);
  }

  private void testImplicitApprovalByPatchSetUploaderOnGetStatusForFileAddition(
      boolean implicitApprovalsEnabled) throws Exception {
    setAsRootCodeOwners(user);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();
    amendChange(user, changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                implicitApprovalsEnabled
                    ? CodeOwnerStatus.APPROVED
                    : CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void getStatusForFileModification_noImplicitApprovalByPatchSetUploader() throws Exception {
    testImplicitApprovalByPatchSetUploaderOnGetStatusForFileModification(
        /* implicitApprovalsEnabled= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void getStatusForFileModification_withImplicitApprovalByPatchSetUploader()
      throws Exception {
    testImplicitApprovalByPatchSetUploaderOnGetStatusForFileModification(
        /* implicitApprovalsEnabled= */ true);
  }

  private void testImplicitApprovalByPatchSetUploaderOnGetStatusForFileModification(
      boolean implicitApprovalsEnabled) throws Exception {
    setAsRootCodeOwners(user);

    Path path = Paths.get("/foo/bar.baz");
    createChange("Test Change", JgitPath.of(path).get(), "file content").getChangeId();
    String changeId =
        createChange("Change Modifying A File", JgitPath.of(path).get(), "new file content")
            .getChangeId();
    amendChange(user, changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.modification(
                path,
                implicitApprovalsEnabled
                    ? CodeOwnerStatus.APPROVED
                    : CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void getStatusForFileDeletion_noImplicitApprovalByPatchSetUploader() throws Exception {
    testImplicitApprovalByPatchSetUploaderOnGetStatusForFileDeletion(
        /* implicitApprovalsEnabled= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void getStatusForFileDeletion_withImplicitApprovalByPatchSetUploader() throws Exception {
    testImplicitApprovalByPatchSetUploaderOnGetStatusForFileDeletion(
        /* implicitApprovalsEnabled= */ true);
  }

  private void testImplicitApprovalByPatchSetUploaderOnGetStatusForFileDeletion(
      boolean implicitApprovalsEnabled) throws Exception {
    setAsRootCodeOwners(user);

    Path path = Paths.get("/foo/bar.baz");
    String changeId = createChangeWithFileDeletion(path);
    amendChange(user, changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.deletion(
                path,
                implicitApprovalsEnabled
                    ? CodeOwnerStatus.APPROVED
                    : CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void getStatusForFileRename_noImplicitApprovalByPatchSetUploaderOnOldPath()
      throws Exception {
    testImplicitApprovalByPatchSetUploaderOnStatusForFileRenameOnOldPath(
        /* implicitApprovalsEnabled= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void getStatusForFileRename_withImplicitApprovalByPatchSetUploaderOnOldPath()
      throws Exception {
    testImplicitApprovalByPatchSetUploaderOnStatusForFileRenameOnOldPath(
        /* implicitApprovalsEnabled= */ true);
  }

  private void testImplicitApprovalByPatchSetUploaderOnStatusForFileRenameOnOldPath(
      boolean implicitApprovalsEnabled) throws Exception {
    setAsCodeOwners("/foo/bar/", user);

    Path oldPath = Paths.get("/foo/bar/abc.txt");
    Path newPath = Paths.get("/foo/baz/abc.txt");
    String changeId = createChangeWithFileRename(oldPath, newPath);
    amendChange(user, changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.rename(
                oldPath,
                implicitApprovalsEnabled
                    ? CodeOwnerStatus.APPROVED
                    : CodeOwnerStatus.INSUFFICIENT_REVIEWERS,
                newPath,
                CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void getStatusForFileRename_noImplicitApprovalByPatchSetUploaderOnNewPath()
      throws Exception {
    testImplicitApprovalByPatchSetUploaderOnStatusForFileRenameOnNewPath(
        /* implicitApprovalsEnabled= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void getStatusForFileRename_withImplicitApprovalByPatchSetUploaderOnNewPath()
      throws Exception {
    testImplicitApprovalByPatchSetUploaderOnStatusForFileRenameOnNewPath(
        /* implicitApprovalsEnabled= */ true);
  }

  private void testImplicitApprovalByPatchSetUploaderOnStatusForFileRenameOnNewPath(
      boolean implicitApprovalsEnabled) throws Exception {
    setAsCodeOwners("/foo/baz/", user);

    Path oldPath = Paths.get("/foo/bar/abc.txt");
    Path newPath = Paths.get("/foo/baz/abc.txt");
    String changeId = createChangeWithFileRename(oldPath, newPath);
    amendChange(user, changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.rename(
                oldPath,
                CodeOwnerStatus.INSUFFICIENT_REVIEWERS,
                newPath,
                implicitApprovalsEnabled
                    ? CodeOwnerStatus.APPROVED
                    : CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void getStatusForFileAddition_noImplicitlyApprovalByChangeOwner() throws Exception {
    setAsRootCodeOwners(admin);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();
    amendChange(user, changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void getStatusForFileAddition_noImplicitlyApprovalByPreviousPatchSetUploader()
      throws Exception {
    TestAccount user2 = accountCreator.user2();

    setAsRootCodeOwners(user);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();
    amendChange(user, changeId);
    amendChange(user2, changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void approvedByAnyoneWhenEveryoneIsCodeOwner() throws Exception {
    // Create a code owner config file that makes everyone a code owner.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail("*")
        .create();

    // Create a change.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved yet (the change owner is a code owner, but
    // implicit approvals are disabled).
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add an approval by a user that is a code owner only through the global code ownership.
    approve(changeId);

    // Check that the file is approved now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.APPROVED));
  }

  @Test
  public void everyoneIsCodeOwner_noImplicitApproval() throws Exception {
    testImplicitlyApprovedWhenEveryoneIsCodeOwner(/* implicitApprovalsEnabled= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void everyoneIsCodeOwner_withImplicitApproval() throws Exception {
    testImplicitlyApprovedWhenEveryoneIsCodeOwner(/* implicitApprovalsEnabled= */ true);
  }

  private void testImplicitlyApprovedWhenEveryoneIsCodeOwner(boolean implicitApprovalsEnabled)
      throws Exception {
    // Create a code owner config file that makes everyone a code owner.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail("*")
        .create();

    // Create a change as a user that is a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                implicitApprovalsEnabled
                    ? CodeOwnerStatus.APPROVED
                    : CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void anyReviewerWhenEveryoneIsCodeOwner() throws Exception {
    // Create a code owner config file that makes everyone a code owner.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail("*")
        .create();

    // Create a change as a user that is a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the status of the file is INSUFFICIENT_REVIEWERS (since there is no implicit
    // approval by default).
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a user as reviewer that is a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Check that the status of the file is PENDING now.
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.PENDING));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "bot@example.com")
  public void approvedByGlobalCodeOwner() throws Exception {
    // Create a bot user that is a global code owner.
    TestAccount bot =
        accountCreator.create("bot", "bot@example.com", "Bot", /* displayName= */ null);

    // Create a change as a user that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved yet.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Let the bot approve the change.
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-2, +2))
        .update();
    requestScopeOperations.setApiUser(bot.id());
    approve(changeId);

    // Check that the file is approved now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.APPROVED));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "bot@example.com")
  public void globalCodeOwner_noImplicitApproval() throws Exception {
    testImplicitlyApprovedByGlobalCodeOwner(/* implicitApprovalsEnabled= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "bot@example.com")
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void globalCodeOwner_withImplicitApproval() throws Exception {
    testImplicitlyApprovedByGlobalCodeOwner(/* implicitApprovalsEnabled= */ true);
  }

  private void testImplicitlyApprovedByGlobalCodeOwner(boolean implicitApprovalsEnabled)
      throws Exception {
    TestAccount bot =
        accountCreator.create("bot", "bot@example.com", "Bot", /* displayName= */ null);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(bot, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                implicitApprovalsEnabled
                    ? CodeOwnerStatus.APPROVED
                    : CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "bot@example.com")
  public void globalCodeOwnerAsReviewer() throws Exception {
    // Create a bot user that is a global code owner.
    TestAccount bot =
        accountCreator.create("bot", "bot@example.com", "Bot", /* displayName= */ null);

    // Create a change as a user that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the status of the file is INSUFFICIENT_REVIEWERS.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add the bot approve as reviewer.
    gApi.changes().id(changeId).addReviewer(bot.email());

    // Check that the status of the file is PENDING now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.PENDING));

    // Let the bot approve the change.
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-2, +2))
        .update();
    requestScopeOperations.setApiUser(bot.id());
    approve(changeId);

    // Check that the file is approved now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.APPROVED));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  public void approvedByAnyoneWhenEveryoneIsGlobalCodeOwner() throws Exception {
    // Create a change.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved yet (the change owner is a global code owner, but
    // implicit approvals are disabled).
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add an approval by a user that is a code owner only through the global code ownership.
    approve(changeId);

    // Check that the file is approved now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.APPROVED));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  public void everyoneIsGlobalCodeOwner_noImplicitApproval() throws Exception {
    testImplicitlyApprovedByGlobalCodeOwnerWhenEveryoneIsGlobalCodeOwner(
        /* implicitApprovalsEnabled= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void everyoneIsGlobalCodeOwner_withImplicitApproval() throws Exception {
    testImplicitlyApprovedByGlobalCodeOwnerWhenEveryoneIsGlobalCodeOwner(
        /* implicitApprovalsEnabled= */ true);
  }

  private void testImplicitlyApprovedByGlobalCodeOwnerWhenEveryoneIsGlobalCodeOwner(
      boolean implicitApprovalsEnabled) throws Exception {
    // Create a change as a user that is a code owner only through the global code ownership.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                implicitApprovalsEnabled
                    ? CodeOwnerStatus.APPROVED
                    : CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  public void anyReviewerWhenEveryoneIsGlobalCodeOwner() throws Exception {
    // Create a change as a user that is a code owner only through the global code ownership.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the status of the file is INSUFFICIENT_REVIEWERS (since there is no implicit
    // approval by default).
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a user as reviewer that is a code owner only through the global code ownership.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Check that the status of the file is PENDING now.
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.PENDING));
  }

  @Test
  public void parentCodeOwnerConfigsAreConsidered() throws Exception {
    TestAccount user2 = accountCreator.user2();
    TestAccount user3 =
        accountCreator.create("user3", "user3@example.com", "User3", /* displayName= */ null);

    setAsCodeOwners("/", user);
    setAsCodeOwners("/foo/", user2);
    setAsCodeOwners("/foo/bar/", user3);

    Path path = Paths.get("/foo/bar/baz.txt");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Add a Code-Review+1 from a code owner on root level (by default this counts as code owner
    // approval).
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    // Add code owner from a lower level as reviewer.
    gApi.changes().id(changeId).addReviewer(user2.email());

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    // The expected status is APPROVED since 'user' which is configured as code owner on the root
    // level approved the change.
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.APPROVED));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void getStatus_overrideApprovesAllFiles() throws Exception {
    createOwnersOverrideLabel();

    String path1 = "foo/baz.config";
    String path2 = "bar/baz.config";

    // Create a change.
    String changeId =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "Test Change",
                ImmutableMap.of(
                    path1, "content",
                    path2, "other content"))
            .to("refs/for/master")
            .getChangeId();

    // Without Owners-Override approval the expected status is INSUFFICIENT_REVIEWERS.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path1, CodeOwnerStatus.INSUFFICIENT_REVIEWERS),
            FileCodeOwnerStatus.addition(path2, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add an override approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // With Owners-Override approval the expected status is APPROVED.
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path1, CodeOwnerStatus.APPROVED),
            FileCodeOwnerStatus.addition(path2, CodeOwnerStatus.APPROVED));
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.overrideApproval",
      values = {"Owners-Override+1", "Another-Override+1"})
  public void getStatus_anyOverrideApprovesAllFiles() throws Exception {
    createOwnersOverrideLabel();
    createOwnersOverrideLabel("Another-Override");

    String path1 = "foo/baz.config";
    String path2 = "bar/baz.config";

    // Create a change.
    String changeId =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "Test Change",
                ImmutableMap.of(
                    path1, "content",
                    path2, "other content"))
            .to("refs/for/master")
            .getChangeId();

    // Without override approval the expected status is INSUFFICIENT_REVIEWERS.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path1, CodeOwnerStatus.INSUFFICIENT_REVIEWERS),
            FileCodeOwnerStatus.addition(path2, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add an override approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // With override approval the expected status is APPROVED.
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path1, CodeOwnerStatus.APPROVED),
            FileCodeOwnerStatus.addition(path2, CodeOwnerStatus.APPROVED));

    // Delete the override approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 0));

    // Without override approval the expected status is INSUFFICIENT_REVIEWERS.
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path1, CodeOwnerStatus.INSUFFICIENT_REVIEWERS),
            FileCodeOwnerStatus.addition(path2, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add another override approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Another-Override", 1));

    // With override approval the expected status is APPROVED.
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path1, CodeOwnerStatus.APPROVED),
            FileCodeOwnerStatus.addition(path2, CodeOwnerStatus.APPROVED));
  }

  @Test
  public void cannotCheckIfSubmittableForNullChangeNotes() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> codeOwnerApprovalCheck.isSubmittable(/* changeNotes= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("changeNotes");
  }

  @Test
  public void isSubmittable() throws Exception {
    TestAccount user2 = accountCreator.user2();

    setAsCodeOwners("/foo/", user);
    setAsCodeOwners("/bar/", user2);

    String changeId =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "Test Change",
                ImmutableMap.of(
                    "foo/baz.config", "content",
                    "bar/baz.config", "other content"))
            .to("refs/for/master")
            .getChangeId();

    assertThat(codeOwnerApprovalCheck.isSubmittable(getChangeNotes(changeId))).isFalse();

    // Add a Code-Review+1 from a code owner that approves the first file.
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    assertThat(codeOwnerApprovalCheck.isSubmittable(getChangeNotes(changeId))).isFalse();

    // Add a Code-Review+1 from a code owner that approves the second file.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    assertThat(codeOwnerApprovalCheck.isSubmittable(getChangeNotes(changeId))).isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void isSubmittableIfOverrideIsPresent() throws Exception {
    createOwnersOverrideLabel();

    // Create a change.
    String changeId =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "Test Change",
                ImmutableMap.of(
                    "foo/baz.config", "content",
                    "bar/baz.config", "other content"))
            .to("refs/for/master")
            .getChangeId();

    // Without override approval the change is not submittable.
    assertThat(codeOwnerApprovalCheck.isSubmittable(getChangeNotes(changeId))).isFalse();

    // Add an override approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // With override approval the change is submittable.
    assertThat(codeOwnerApprovalCheck.isSubmittable(getChangeNotes(changeId))).isTrue();
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.overrideApproval",
      values = {"Owners-Override+1", "Another-Override+1"})
  public void isSubmittableIfAnyOverrideIsPresent() throws Exception {
    createOwnersOverrideLabel();
    createOwnersOverrideLabel("Another-Override");

    // Create a change.
    String changeId =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "Test Change",
                ImmutableMap.of(
                    "foo/baz.config", "content",
                    "bar/baz.config", "other content"))
            .to("refs/for/master")
            .getChangeId();

    // Without override approval the change is not submittable.
    assertThat(codeOwnerApprovalCheck.isSubmittable(getChangeNotes(changeId))).isFalse();

    // Add an override approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // With override approval the change is submittable.
    assertThat(codeOwnerApprovalCheck.isSubmittable(getChangeNotes(changeId))).isTrue();

    // Delete the override approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 0));

    // Without override approval the change is not submittable.
    assertThat(codeOwnerApprovalCheck.isSubmittable(getChangeNotes(changeId))).isFalse();

    // Add another override approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Another-Override", 1));

    // With override approval the change is submittable.
    assertThat(codeOwnerApprovalCheck.isSubmittable(getChangeNotes(changeId))).isTrue();
  }

  @Test
  public void getStatus_branchDeleted() throws Exception {
    String branchName = "tempBranch";
    createBranch(BranchNameKey.create(project, branchName));

    String changeId = createChange("refs/for/" + branchName).getChangeId();

    DeleteBranchesInput input = new DeleteBranchesInput();
    input.branches = ImmutableList.of(branchName);
    gApi.projects().name(project.get()).deleteBranches(input);

    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class,
            () -> codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId)));
    assertThat(exception).hasMessageThat().isEqualTo("destination branch not found");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void approvedByStickyApprovalOnOldPatchSet() throws Exception {
    TestAccount user2 = accountCreator.user2();

    setAsRootCodeOwners(user);

    // Create a change as a user that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user2, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved yet.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Let the 'user' approve the change.
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-2, +2))
        .update();
    requestScopeOperations.setApiUser(user.id());
    approve(changeId);

    // Check that the file is approved now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.APPROVED));

    // Change some other file ('user' who uploads the change is a code owner and hence owner
    // approvals are implicit for this change)
    String changeId2 =
        createChange(user, "Change Other File", "other.txt", "file content").getChangeId();
    approve(changeId2);
    gApi.changes().id(changeId2).current().submit();

    // Rebase the first change (trivial rebase).
    gApi.changes().id(changeId).rebase();

    // Check that the approval from 'user' is still there (since Code-Review is sticky on trivial
    // rebase).
    assertThat(gApi.changes().id(changeId).get().labels.get("Code-Review").approved.email)
        .isEqualTo(user.email());

    // Check that the file is still approved.
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.APPROVED));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void overridenByStickyApprovalOnOldPatchSet() throws Exception {
    createOwnersOverrideLabel();

    // make the override label sticky
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.copyAnyScore = true;
    gApi.projects().name(project.get()).label("Owners-Override").update(input);

    // Create a change as a user that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved yet.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Apply an override
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // Check that the file is approved now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.APPROVED));

    // Change some other file and submit the change with an override.
    String changeId2 =
        createChange(user, "Change Other File", "other.txt", "file content").getChangeId();
    approve(changeId2);
    gApi.changes().id(changeId2).current().review(new ReviewInput().label("Owners-Override", 1));
    gApi.changes().id(changeId2).current().submit();

    // Rebase the first change (trivial rebase).
    gApi.changes().id(changeId).rebase();

    // Check that the override is still there (since Owners-Override is sticky).
    assertThat(gApi.changes().id(changeId).get().labels.get("Owners-Override").approved.email)
        .isEqualTo(admin.email());

    // Check that the file is still approved.
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.APPROVED));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "Code-Review+1")
  public void codeReviewPlus2CountsAsApprovalIfCodeReviewPlus1IsRequired() throws Exception {
    TestAccount user2 = accountCreator.user2();

    setAsRootCodeOwners(user);

    // Create a change as 'user2' that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user2, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved yet.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Let 'user' approve the change (vote Code-Review+2)
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-2, +2))
        .update();
    requestScopeOperations.setApiUser(user.id());
    approve(changeId);

    // Check that the file is approved now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.APPROVED));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void ownersOverridePlus2CountsAsOverrideIfOverridePlus1IsRequired() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+2", "Override+2", "+1", "Override", " 0", "No Override");
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

    TestAccount user2 = accountCreator.user2();

    setAsRootCodeOwners(admin);

    // Create a change as 'user' that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved yet.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Let 'user2' override with Owners-Override+2
    requestScopeOperations.setApiUser(user2.id());
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 2));

    // Check that the file is approved now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.APPROVED));
  }

  @Test
  public void approvedByDefaultCodeOwner() throws Exception {
    TestAccount user2 = accountCreator.user2();

    setAsDefaultCodeOwners(user);

    // Create a change as a user that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user2, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved yet.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Let the code owner approve the change.
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-2, +2))
        .update();
    requestScopeOperations.setApiUser(user.id());
    approve(changeId);

    // Check that the file is approved now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.APPROVED));
  }

  @Test
  public void defaultCodeOwner_noImplicitApproval() throws Exception {
    testImplicitlyApprovedByDefaultCodeOwner(/* implicitApprovalsEnabled= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void defaultCodeOwner_withImplicitApproval() throws Exception {
    testImplicitlyApprovedByDefaultCodeOwner(/* implicitApprovalsEnabled= */ true);
  }

  private void testImplicitlyApprovedByDefaultCodeOwner(boolean implicitApprovalsEnabled)
      throws Exception {
    setAsDefaultCodeOwners(user);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatCollection(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                implicitApprovalsEnabled
                    ? CodeOwnerStatus.APPROVED
                    : CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void defaultCodeOwnerAsReviewer() throws Exception {
    TestAccount user2 = accountCreator.user2();

    setAsDefaultCodeOwners(user);

    // Create a change as a user that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user2, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the status of the file is INSUFFICIENT_REVIEWERS.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add the default code owner as reviewer.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Check that the status of the file is PENDING now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.PENDING));

    // Let the default code owner approve the change.
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-2, +2))
        .update();
    requestScopeOperations.setApiUser(user.id());
    approve(changeId);

    // Check that the file is approved now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.APPROVED));
  }

  @Test
  public void pureRevertsAreNotExemptedByDefault() throws Exception {
    setAsRootCodeOwners(admin);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();
    approve(changeId);
    gApi.changes().id(changeId).current().submit();

    // Revert the change
    String changeIdOfRevert = gApi.changes().id(changeId).revert().get().changeId;

    // Check that the file is not approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeIdOfRevert));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.deletion(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.exemptPureReverts", value = "true")
  public void pureRevertsAreExemptedIfConfigured() throws Exception {
    setAsRootCodeOwners(admin);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();
    approve(changeId);
    gApi.changes().id(changeId).current().submit();

    // Revert the change
    String changeIdOfRevert = gApi.changes().id(changeId).revert().get().changeId;

    // Check that the file is approved since it's a pure revert.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeIdOfRevert));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.deletion(path, CodeOwnerStatus.APPROVED));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.exemptPureReverts", value = "true")
  public void nonPureRevertsAreNotExempted() throws Exception {
    setAsRootCodeOwners(admin);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();
    approve(changeId);
    gApi.changes().id(changeId).current().submit();

    // Revert the change
    ChangeInfo revertChange = gApi.changes().id(changeId).revert().get();

    // Amend change to make it a non-pure revert change.
    GitUtil.fetch(
        testRepo,
        RefNames.patchSetRef(PatchSet.id(Change.id(revertChange._number), 1)) + ":revert");
    testRepo.reset("revert");

    amendChange(
        revertChange.changeId,
        "refs/for/master",
        admin,
        testRepo,
        "Revert change",
        JgitPath.of(path).get(),
        "other content");

    // Check that the file is not approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(revertChange.changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.modification(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.exemptedUser", value = "exempted-user@example.com")
  public void changeUploadedByExemptedUserIsApproved() throws Exception {
    TestAccount exemptedUser =
        accountCreator.create(
            "exemptedUser", "exempted-user@example.com", "Exempted User", /* displayName= */ null);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(exemptedUser, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Check that the file is approved since the uploader is exempted from requiring code owner
    // approvals.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.APPROVED));

    // Amend the change by another user, so that the other non-exempted user becomes the last
    // uploader.
    amendChange(user, changeId);

    // Check that the file is no longer approved since the uploader is not exempted from requiring
    // code owner approvals.
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatusesAsSet(getChangeNotes(changeId));
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  private ChangeNotes getChangeNotes(String changeId) throws Exception {
    return changeNotesFactory.create(project, Change.id(gApi.changes().id(changeId).get()._number));
  }
}
