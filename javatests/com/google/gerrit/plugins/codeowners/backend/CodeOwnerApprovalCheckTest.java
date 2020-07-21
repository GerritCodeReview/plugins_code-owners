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
import static com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusSubject.assertThatSet;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.plugins.codeowners.JgitPath;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusSubject;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerApprovalCheck}. */
public class CodeOwnerApprovalCheckTest extends AbstractCodeOwnersTest {
  @Inject private ChangeNotes.Factory changeNotesFactory;
  @Inject private RequestScopeOperations requestScopeOperations;

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
            NullPointerException.class, () -> codeOwnerApprovalCheck.getFileStatuses(null));
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
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    fileCodeOwnerStatusSubject.hasOldPathStatus().isEmpty();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
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
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    fileCodeOwnerStatusSubject.hasOldPathStatus().isEmpty();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
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
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().isEmpty();
    fileCodeOwnerStatusSubject.hasOldPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasOldPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    fileCodeOwnerStatusSubject.hasChangedFile().isNoRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isDeletion();
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
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(newPath);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    fileCodeOwnerStatusSubject.hasOldPathStatus().value().hasPathThat().isEqualTo(oldPath);
    fileCodeOwnerStatusSubject
        .hasOldPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    fileCodeOwnerStatusSubject.hasChangedFile().isRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
  }

  @Test
  public void getStatusForFileAddition_pending() throws Exception {
    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Add a reviewer that is a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.PENDING);
    fileCodeOwnerStatusSubject.hasOldPathStatus().isEmpty();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
  }

  @Test
  public void getStatusForFileModification_pending() throws Exception {
    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

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
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.PENDING);
    fileCodeOwnerStatusSubject.hasOldPathStatus().isEmpty();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
  }

  @Test
  public void getStatusForFileDeletion_pending() throws Exception {
    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId = createChangeWithFileDeletion(path);

    // Add a reviewer that is a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().isEmpty();
    fileCodeOwnerStatusSubject.hasOldPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasOldPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.PENDING);
    fileCodeOwnerStatusSubject.hasChangedFile().isNoRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isDeletion();
  }

  @Test
  public void getStatusForFileRename_pendingOldPath() throws Exception {
    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(user.email())
        .create();

    Path oldPath = Paths.get("/foo/bar/abc.txt");
    Path newPath = Paths.get("/foo/baz/abc.txt");
    String changeId = createChangeWithFileRename(oldPath, newPath);

    // Add a reviewer that is a code owner old path.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(newPath);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    fileCodeOwnerStatusSubject.hasOldPathStatus().value().hasPathThat().isEqualTo(oldPath);
    fileCodeOwnerStatusSubject
        .hasOldPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.PENDING);
    fileCodeOwnerStatusSubject.hasChangedFile().isRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
  }

  @Test
  public void getStatusForFileRename_pendingNewPath() throws Exception {
    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/baz/")
        .addCodeOwnerEmail(user.email())
        .create();

    Path oldPath = Paths.get("/foo/bar/abc.txt");
    Path newPath = Paths.get("/foo/baz/abc.txt");
    String changeId = createChangeWithFileRename(oldPath, newPath);

    // Add a reviewer that is a code owner of the new path.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(newPath);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.PENDING);
    fileCodeOwnerStatusSubject.hasOldPathStatus().value().hasPathThat().isEqualTo(oldPath);
    fileCodeOwnerStatusSubject
        .hasOldPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    fileCodeOwnerStatusSubject.hasChangedFile().isRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
  }

  @Test
  public void getStatusForFileAddition_approved() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Add a Code-Review+1 from a code owner (by default this counts as code owner approval).
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
    fileCodeOwnerStatusSubject.hasOldPathStatus().isEmpty();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
  }

  @Test
  public void getStatusForFileModification_approved() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    Path path = Paths.get("/foo/bar.baz");
    createChange("Test Change", JgitPath.of(path).get(), "file content").getChangeId();
    String changeId =
        createChange("Change Modifying A File", JgitPath.of(path).get(), "new file content")
            .getChangeId();

    // Add a Code-Review+1 from a code owner (by default this counts as code owner approval).
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
    fileCodeOwnerStatusSubject.hasOldPathStatus().isEmpty();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
  }

  @Test
  public void getStatusForFileDeletion_approved() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId = createChangeWithFileDeletion(path);

    // Add a Code-Review+1 from a code owner (by default this counts as code owner approval).
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().isEmpty();
    fileCodeOwnerStatusSubject.hasOldPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasOldPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
    fileCodeOwnerStatusSubject.hasChangedFile().isNoRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isDeletion();
  }

  @Test
  public void getStatusForFileRename_approvedOldPath() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(user.email())
        .create();

    Path oldPath = Paths.get("/foo/bar/abc.txt");
    Path newPath = Paths.get("/foo/baz/abc.txt");
    String changeId = createChangeWithFileRename(oldPath, newPath);

    // Add a Code-Review+1 from a code owner of the old path (by default this counts as code owner
    // approval).
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(newPath);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    fileCodeOwnerStatusSubject.hasOldPathStatus().value().hasPathThat().isEqualTo(oldPath);
    fileCodeOwnerStatusSubject
        .hasOldPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
    fileCodeOwnerStatusSubject.hasChangedFile().isRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
  }

  @Test
  public void getStatusForFileRename_approvedNewPath() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/baz/")
        .addCodeOwnerEmail(user.email())
        .create();

    Path oldPath = Paths.get("/foo/bar/abc.txt");
    Path newPath = Paths.get("/foo/baz/abc.txt");
    String changeId = createChangeWithFileRename(oldPath, newPath);

    // Add a Code-Review+1 from a code owner of the new path (by default this counts as code owner
    // approval).
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(newPath);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
    fileCodeOwnerStatusSubject.hasOldPathStatus().value().hasPathThat().isEqualTo(oldPath);
    fileCodeOwnerStatusSubject
        .hasOldPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    fileCodeOwnerStatusSubject.hasChangedFile().isRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
  }

  @Test
  public void getStatusForFileAddition_implicitlyApprovedByPatchSetUploader() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();
    amendChange(user, changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getRevisionResource(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
    fileCodeOwnerStatusSubject.hasOldPathStatus().isEmpty();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
  }

  @Test
  public void getStatusForFileModification_implicitlyApprovedByPatchSetUploader() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    Path path = Paths.get("/foo/bar.baz");
    createChange("Test Change", JgitPath.of(path).get(), "file content").getChangeId();
    String changeId =
        createChange("Change Modifying A File", JgitPath.of(path).get(), "new file content")
            .getChangeId();
    amendChange(user, changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getRevisionResource(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
    fileCodeOwnerStatusSubject.hasOldPathStatus().isEmpty();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
  }

  @Test
  public void getStatusForFileDeletion_implicitlyApprovedByPatchSetUploader() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId = createChangeWithFileDeletion(path);
    amendChange(user, changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getRevisionResource(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().isEmpty();
    fileCodeOwnerStatusSubject.hasOldPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasOldPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
    fileCodeOwnerStatusSubject.hasChangedFile().isNoRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isDeletion();
  }

  @Test
  public void getStatusForFileRename_implicitlyApprovedOldPathByPatchSetUploader()
      throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(user.email())
        .create();

    Path oldPath = Paths.get("/foo/bar/abc.txt");
    Path newPath = Paths.get("/foo/baz/abc.txt");
    String changeId = createChangeWithFileRename(oldPath, newPath);
    amendChange(user, changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getRevisionResource(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(newPath);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    fileCodeOwnerStatusSubject.hasOldPathStatus().value().hasPathThat().isEqualTo(oldPath);
    fileCodeOwnerStatusSubject
        .hasOldPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
    fileCodeOwnerStatusSubject.hasChangedFile().isRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
  }

  @Test
  public void getStatusForFileRename_implicitlyApprovedNewPathByPatchSetUploader()
      throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/baz/")
        .addCodeOwnerEmail(user.email())
        .create();

    Path oldPath = Paths.get("/foo/bar/abc.txt");
    Path newPath = Paths.get("/foo/baz/abc.txt");
    String changeId = createChangeWithFileRename(oldPath, newPath);
    amendChange(user, changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getRevisionResource(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(newPath);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
    fileCodeOwnerStatusSubject.hasOldPathStatus().value().hasPathThat().isEqualTo(oldPath);
    fileCodeOwnerStatusSubject
        .hasOldPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    fileCodeOwnerStatusSubject.hasChangedFile().isRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
  }

  @Test
  public void getStatusForFileAddition_noImplicitlyApprovalByChangeOwner() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();
    amendChange(user, changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getRevisionResource(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    fileCodeOwnerStatusSubject.hasOldPathStatus().isEmpty();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
  }

  @Test
  public void getStatusForFileAddition_noImplicitlyApprovalByPreviousPatchSetUploader()
      throws Exception {
    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();
    amendChange(user, changeId);
    amendChange(user2, changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getRevisionResource(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    // we expect state PENDING because 'user' got added as reviewer on upload of the second patch
    // set (the patch set uploader becomes a reviewer if the change is owned by another user).
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.PENDING);
    fileCodeOwnerStatusSubject.hasOldPathStatus().isEmpty();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
  }

  @Test
  public void parentCodeOwnerConfigsAreConsidered() throws Exception {
    TestAccount user2 = accountCreator.user2();
    TestAccount user3 = accountCreator.create("user3", "user3@example.com", "User3", null);

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user2.email())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(user3.email())
        .create();

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
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    // The expected status is APPROVED since 'user' which is configured as code owner on the root
    // level approved the change.
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatSet(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
  }

  private ChangeNotes getChangeNotes(String changeId) throws Exception {
    return changeNotesFactory.create(project, Change.id(gApi.changes().id(changeId).get()._number));
  }
}
