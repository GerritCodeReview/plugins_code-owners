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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusSubject.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusSubject.assertThatStream;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.DeleteBranchesInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.plugins.codeowners.JgitPath;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusSubject;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerApprovalCheck}. */
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
            () -> codeOwnerApprovalCheck.getFileStatuses(/* changeNotes= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("changeNotes");
  }

  @Test
  public void getStatusForFileAddition_insufficientReviewers() throws Exception {
    // create arbitrary code owner config to avoid entering the bootstrapping code path in
    // CodeOwnerApprovalCheck
    createArbitraryCodeOwnerConfigFile();

    TestAccount user2 = accountCreator.user2();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Add a reviewer that is not a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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
    // create arbitrary code owner config to avoid entering the bootstrapping code path in
    // CodeOwnerApprovalCheck
    createArbitraryCodeOwnerConfigFile();

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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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
    // create arbitrary code owner config to avoid entering the bootstrapping code path in
    // CodeOwnerApprovalCheck
    createArbitraryCodeOwnerConfigFile();

    TestAccount user2 = accountCreator.user2();

    Path path = Paths.get("/foo/bar.baz");
    String changeId = createChangeWithFileDeletion(path);

    // Add a reviewer that is not a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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
    // create arbitrary code owner config to avoid entering the bootstrapping code path in
    // CodeOwnerApprovalCheck
    createArbitraryCodeOwnerConfigFile();

    TestAccount user2 = accountCreator.user2();

    Path oldPath = Paths.get("/foo/old.bar");
    Path newPath = Paths.get("/foo/new.bar");
    String changeId = createChangeWithFileRename(oldPath, newPath);

    // Add a reviewer that is not a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(
            implicitApprovalsEnabled
                ? CodeOwnerStatus.APPROVED
                : CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    fileCodeOwnerStatusSubject.hasOldPathStatus().isEmpty();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(
            implicitApprovalsEnabled
                ? CodeOwnerStatus.APPROVED
                : CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    fileCodeOwnerStatusSubject.hasOldPathStatus().isEmpty();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().isEmpty();
    fileCodeOwnerStatusSubject.hasOldPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasOldPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(
            implicitApprovalsEnabled
                ? CodeOwnerStatus.APPROVED
                : CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    fileCodeOwnerStatusSubject.hasChangedFile().isNoRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isDeletion();
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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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
        .isEqualTo(
            implicitApprovalsEnabled
                ? CodeOwnerStatus.APPROVED
                : CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    fileCodeOwnerStatusSubject.hasChangedFile().isRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(newPath);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(
            implicitApprovalsEnabled
                ? CodeOwnerStatus.APPROVED
                : CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
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
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);

    // Add an approval by a user that is a code owner only through the global code ownership.
    approve(changeId);

    // Check that the file is approved now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    fileCodeOwnerStatusSubject = assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(
            implicitApprovalsEnabled
                ? CodeOwnerStatus.APPROVED
                : CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
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
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);

    // Add a user as reviewer that is a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Check that the status of the file is PENDING now.
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    fileCodeOwnerStatusSubject = assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.PENDING);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "bot@example.com")
  public void approvedByGlobalCodeOwner() throws Exception {
    testApprovedByGlobalCodeOwner(/* bootstrappingMode= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "bot@example.com")
  public void approvedByGlobalCodeOwner_bootstrappingMode() throws Exception {
    testApprovedByGlobalCodeOwner(/* bootstrappingMode= */ true);
  }

  private void testApprovedByGlobalCodeOwner(boolean bootstrappingMode) throws Exception {
    // Create a bot user that is a global code owner.
    TestAccount bot =
        accountCreator.create("bot", "bot@example.com", "Bot", /* displayName= */ null);

    if (!bootstrappingMode) {
      // Create a code owner config file so that we are not in the bootstrapping mode.
      codeOwnerConfigOperations
          .newCodeOwnerConfig()
          .project(project)
          .branch("master")
          .folderPath("/foo/")
          .addCodeOwnerEmail(admin.email())
          .create();
    }

    // Create a change as a user that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved yet.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);

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
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    fileCodeOwnerStatusSubject = assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "bot@example.com")
  public void globalCodeOwner_noImplicitApproval() throws Exception {
    testImplicitlyApprovedByGlobalCodeOwner(
        /* implicitApprovalsEnabled= */ false, /* bootstrappingMode= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "bot@example.com")
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void globalCodeOwner_withImplicitApproval() throws Exception {
    testImplicitlyApprovedByGlobalCodeOwner(
        /* implicitApprovalsEnabled= */ true, /* bootstrappingMode= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "bot@example.com")
  public void globalCodeOwner_noImplicitApproval_bootstrappingMode() throws Exception {
    testImplicitlyApprovedByGlobalCodeOwner(
        /* implicitApprovalsEnabled= */ false, /* bootstrappingMode= */ true);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "bot@example.com")
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void globalCodeOwner_withImplicitApproval_bootstrappingMode() throws Exception {
    testImplicitlyApprovedByGlobalCodeOwner(
        /* implicitApprovalsEnabled= */ true, /* bootstrappingMode= */ true);
  }

  private void testImplicitlyApprovedByGlobalCodeOwner(
      boolean implicitApprovalsEnabled, boolean bootstrappingMode) throws Exception {
    TestAccount bot =
        accountCreator.create("bot", "bot@example.com", "Bot", /* displayName= */ null);

    if (!bootstrappingMode) {
      codeOwnerConfigOperations
          .newCodeOwnerConfig()
          .project(project)
          .branch("master")
          .folderPath("/foo/")
          .addCodeOwnerEmail(admin.email())
          .create();
    }

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(bot, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(
            implicitApprovalsEnabled
                ? CodeOwnerStatus.APPROVED
                : CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "bot@example.com")
  public void globalCodeOwnerAsReviewer() throws Exception {
    testGlobalCodeOwnerAsReviewer(/* bootstrappingMode= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "bot@example.com")
  public void globalCodeOwnerAsReviewer_bootstrappingMode() throws Exception {
    testGlobalCodeOwnerAsReviewer(/* bootstrappingMode= */ true);
  }

  private void testGlobalCodeOwnerAsReviewer(boolean bootstrappingMode) throws Exception {
    // Create a bot user that is a global code owner.
    TestAccount bot =
        accountCreator.create("bot", "bot@example.com", "Bot", /* displayName= */ null);

    if (!bootstrappingMode) {
      // Create a code owner config file so that we are not in the bootstrapping mode.
      codeOwnerConfigOperations
          .newCodeOwnerConfig()
          .project(project)
          .branch("master")
          .folderPath("/foo/")
          .addCodeOwnerEmail(admin.email())
          .create();
    }

    // Create a change as a user that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the status of the file is INSUFFICIENT_REVIEWERS.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);

    // Add the bot approve as reviewer.
    gApi.changes().id(changeId).addReviewer(bot.email());

    // Check that the status of the file is PENDING now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    fileCodeOwnerStatusSubject = assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.PENDING);

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
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    fileCodeOwnerStatusSubject = assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  public void approvedByAnyoneWhenEveryoneIsGlobalCodeOwner() throws Exception {
    testApprovedByAnyoneWhenEveryoneIsGlobalCodeOwner(/* bootstrappingMode= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  public void approvedByAnyoneWhenEveryoneIsGlobalCodeOwner_bootstrappingMode() throws Exception {
    testApprovedByAnyoneWhenEveryoneIsGlobalCodeOwner(/* bootstrappingMode= */ true);
  }

  private void testApprovedByAnyoneWhenEveryoneIsGlobalCodeOwner(boolean bootstrappingMode)
      throws Exception {
    if (!bootstrappingMode) {
      // Create a code owner config file so that we are not in the bootstrapping mode.
      codeOwnerConfigOperations
          .newCodeOwnerConfig()
          .project(project)
          .branch("master")
          .folderPath("/foo/")
          .addCodeOwnerEmail(user.email())
          .create();
    }

    // Create a change.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved yet (the change owner is a global code owner, but
    // implicit approvals are disabled).
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);

    // Add an approval by a user that is a code owner only through the global code ownership.
    approve(changeId);

    // Check that the file is approved now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    fileCodeOwnerStatusSubject = assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  public void everyoneIsGlobalCodeOwner_noImplicitApproval() throws Exception {
    testImplicitlyApprovedByGlobalCodeOwnerWhenEveryoneIsGlobalCodeOwner(
        /* implicitApprovalsEnabled= */ false, /* bootstrappingMode= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void everyoneIsGlobalCodeOwner_withImplicitApproval() throws Exception {
    testImplicitlyApprovedByGlobalCodeOwnerWhenEveryoneIsGlobalCodeOwner(
        /* implicitApprovalsEnabled= */ true, /* bootstrappingMode= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  public void everyoneIsGlobalCodeOwner_noImplicitApproval_bootstrappingMode() throws Exception {
    testImplicitlyApprovedByGlobalCodeOwnerWhenEveryoneIsGlobalCodeOwner(
        /* implicitApprovalsEnabled= */ false, /* bootstrappingMode= */ true);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void everyoneIsGlobalCodeOwner_withImplicitApproval_bootstrappingMode() throws Exception {
    testImplicitlyApprovedByGlobalCodeOwnerWhenEveryoneIsGlobalCodeOwner(
        /* implicitApprovalsEnabled= */ true, /* bootstrappingMode= */ true);
  }

  private void testImplicitlyApprovedByGlobalCodeOwnerWhenEveryoneIsGlobalCodeOwner(
      boolean implicitApprovalsEnabled, boolean bootstrappingMode) throws Exception {
    if (!bootstrappingMode) {
      codeOwnerConfigOperations
          .newCodeOwnerConfig()
          .project(project)
          .branch("master")
          .folderPath("/foo/")
          .addCodeOwnerEmail(user.email())
          .create();
    }

    // Create a change as a user that is a code owner only through the global code ownership.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(
            implicitApprovalsEnabled
                ? CodeOwnerStatus.APPROVED
                : CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  public void anyReviewerWhenEveryoneIsGlobalCodeOwner() throws Exception {
    testAnyReviewerWhenEveryoneIsGlobalCodeOwner(/* bootstrappingMode= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  public void anyReviewerWhenEveryoneIsGlobalCodeOwner_bootstrappingMode() throws Exception {
    testAnyReviewerWhenEveryoneIsGlobalCodeOwner(/* bootstrappingMode= */ true);
  }

  private void testAnyReviewerWhenEveryoneIsGlobalCodeOwner(boolean bootstrappingMode)
      throws Exception {
    TestAccount user2 = accountCreator.user2();

    if (!bootstrappingMode) {
      // Create a code owner config file so that we are not in the bootstrapping mode.
      codeOwnerConfigOperations
          .newCodeOwnerConfig()
          .project(project)
          .branch("master")
          .folderPath("/foo/")
          .addCodeOwnerEmail(user2.email())
          .create();
    }

    // Create a change as a user that is a code owner only through the global code ownership.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the status of the file is INSUFFICIENT_REVIEWERS (since there is no implicit
    // approval by default).
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);

    // Add a user as reviewer that is a code owner only through the global code ownership.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Check that the status of the file is PENDING now.
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    fileCodeOwnerStatusSubject = assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.PENDING);
  }

  @Test
  public void parentCodeOwnerConfigsAreConsidered() throws Exception {
    TestAccount user2 = accountCreator.user2();
    TestAccount user3 =
        accountCreator.create("user3", "user3@example.com", "User3", /* displayName= */ null);

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

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    // The expected status is APPROVED since 'user' which is configured as code owner on the root
    // level approved the change.
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void getStatus_overrideApprovesAllFiles() throws Exception {
    // create arbitrary code owner config to avoid entering the bootstrapping code path in
    // CodeOwnerApprovalCheck
    createArbitraryCodeOwnerConfigFile();

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

    // Without Owners-Override approval the expected status is INSUFFICIENT_REVIEWERS.
    for (FileCodeOwnerStatus fileCodeOwnerStatus :
        codeOwnerApprovalCheck
            .getFileStatuses(getChangeNotes(changeId))
            .collect(toImmutableList())) {
      assertThat(fileCodeOwnerStatus)
          .hasNewPathStatus()
          .value()
          .hasStatusThat()
          .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    }

    // Add an override approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // With Owners-Override approval the expected status is APPROVED.
    for (FileCodeOwnerStatus fileCodeOwnerStatus :
        codeOwnerApprovalCheck
            .getFileStatuses(getChangeNotes(changeId))
            .collect(toImmutableList())) {
      assertThat(fileCodeOwnerStatus)
          .hasNewPathStatus()
          .value()
          .hasStatusThat()
          .isEqualTo(CodeOwnerStatus.APPROVED);
    }
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

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .addCodeOwnerEmail(user2.email())
        .create();

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
    // create arbitrary code owner config to avoid entering the bootstrapping code path in
    // CodeOwnerApprovalCheck
    createArbitraryCodeOwnerConfigFile();

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
  public void bootstrappingGetStatus_insufficientReviewers() throws Exception {
    // since no code owner config exists we are entering the bootstrapping code path in
    // CodeOwnerApprovalCheck

    TestAccount user2 = accountCreator.user2();
    TestAccount user3 =
        accountCreator.create("user3", "user3@example.com", "User3", /* displayName= */ null);

    // Create change with a user that is not a project owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Add a reviewer that is not a project owner.
    gApi.changes().id(changeId).addReviewer(user2.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a project owner.
    requestScopeOperations.setApiUser(user3.id());
    recommend(changeId);

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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
  public void bootstrappingGetStatus_pending() throws Exception {
    // since no code owner config exists we are entering the bootstrapping code path in
    // CodeOwnerApprovalCheck

    TestAccount user2 = accountCreator.user2();

    // Create change with a user that is not a project owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Add a reviewer that is a project owner.
    gApi.changes().id(changeId).addReviewer(admin.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a project owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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
  public void bootstrappingGetStatus_approved() throws Exception {
    // since no code owner config exists we are entering the bootstrapping code path in
    // CodeOwnerApprovalCheck

    // Create change with a user that is not a project owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Add a Code-Review+1 from a project owner (by default this counts as code owner approval).
    requestScopeOperations.setApiUser(admin.id());
    recommend(changeId);

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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
  public void bootstrappingGetStatus_noImplicitApprovalByPatchSetUploader() throws Exception {
    testImplicitApprovalByPatchSetUploaderOnBootstrappingGetStatus(
        /* implicitApprovalsEnabled= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void bootstrappingGetStatus_withImplicitApprovalByPatchSetUploader() throws Exception {
    testImplicitApprovalByPatchSetUploaderOnBootstrappingGetStatus(
        /* implicitApprovalsEnabled= */ true);
  }

  private void testImplicitApprovalByPatchSetUploaderOnBootstrappingGetStatus(
      boolean implicitApprovalsEnabled) throws Exception {
    // since no code owner config exists we are entering the bootstrapping code path in
    // CodeOwnerApprovalCheck

    // Create change with a user that is not a project owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Amend change with a user that is a project owner.
    amendChange(admin, changeId);

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(
            implicitApprovalsEnabled
                ? CodeOwnerStatus.APPROVED
                : CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    fileCodeOwnerStatusSubject.hasOldPathStatus().isEmpty();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoRename();
    fileCodeOwnerStatusSubject.hasChangedFile().isNoDeletion();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void bootstrappingGetStatus_noImplicitlyApprovalByChangeOwner() throws Exception {
    // since no code owner config exists we are entering the bootstrapping code path in
    // CodeOwnerApprovalCheck

    // Create change with a user that is a project owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Amend change with a user that is not a project owner.
    amendChange(user, changeId);

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void bootstrappingGetStatus_overrideApprovesAllFiles() throws Exception {
    // since no code owner config exists we are entering the bootstrapping code path in
    // CodeOwnerApprovalCheck

    createOwnersOverrideLabel();

    // Create a change with a user that is not a project owner.
    TestRepository<InMemoryRepository> testRepo = cloneProject(project, user);
    String changeId =
        pushFactory
            .create(
                user.newIdent(),
                testRepo,
                "Test Change",
                ImmutableMap.of(
                    "foo/baz.config", "content",
                    "bar/baz.config", "other content"))
            .to("refs/for/master")
            .getChangeId();

    // Without Owners-Override approval the expected status is INSUFFICIENT_REVIEWERS.
    for (FileCodeOwnerStatus fileCodeOwnerStatus :
        codeOwnerApprovalCheck
            .getFileStatuses(getChangeNotes(changeId))
            .collect(toImmutableList())) {
      assertThat(fileCodeOwnerStatus)
          .hasNewPathStatus()
          .value()
          .hasStatusThat()
          .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    }

    // Add an override approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // With Owners-Override approval the expected status is APPROVED.
    for (FileCodeOwnerStatus fileCodeOwnerStatus :
        codeOwnerApprovalCheck
            .getFileStatuses(getChangeNotes(changeId))
            .collect(toImmutableList())) {
      assertThat(fileCodeOwnerStatus)
          .hasNewPathStatus()
          .value()
          .hasStatusThat()
          .isEqualTo(CodeOwnerStatus.APPROVED);
    }
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
            () -> codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId)));
    assertThat(exception).hasMessageThat().isEqualTo("destination branch not found");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void approvedByStickyApprovalOnOldPatchSet() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // Create a code owner config file with 'user' as code owner
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();

    // Create a change as a user that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user2, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved yet.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);

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
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    fileCodeOwnerStatusSubject = assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);

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
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    fileCodeOwnerStatusSubject = assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "Code-Review+1")
  public void codeReviewPlus2CountsAsApprovalIfCodeReviewPlus1IsRequired() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // Create a code owner config file with 'user' as code owner
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();

    // Create a change as 'user2' that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user2, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved yet.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);

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
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    fileCodeOwnerStatusSubject = assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
  }

  @Test
  public void noBootstrappingIfDefaultCodeOwnerConfigExists() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // Create default code owner config file in refs/meta/config.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(RefNames.REFS_CONFIG)
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();

    // Create a change as a user that is neither a code owner nor a project owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user2, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved yet.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);

    // Let the project owner approve the change.
    requestScopeOperations.setApiUser(admin.id());
    approve(changeId);

    // Verify that the file is still approved yet (since we are not in bootstrapping mode, the
    // project owner doesn't count as code owner).
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    fileCodeOwnerStatusSubject = assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);

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
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    fileCodeOwnerStatusSubject = assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
  }

  @Test
  public void approvedByDefaultCodeOwner() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // Create default code owner config file in refs/meta/config.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(RefNames.REFS_CONFIG)
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();

    // Create a change as a user that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user2, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved yet.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);

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
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    fileCodeOwnerStatusSubject = assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
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
    // Create default code owner config file in refs/meta/config.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(RefNames.REFS_CONFIG)
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(
            implicitApprovalsEnabled
                ? CodeOwnerStatus.APPROVED
                : CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
  }

  @Test
  public void defaultCodeOwnerAsReviewer() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // Create default code owner config file in refs/meta/config.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(RefNames.REFS_CONFIG)
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();

    // Create a change as a user that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user2, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the status of the file is INSUFFICIENT_REVIEWERS.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);

    // Add the default code owner as reviewer.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Check that the status of the file is PENDING now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    fileCodeOwnerStatusSubject = assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.PENDING);

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
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    fileCodeOwnerStatusSubject = assertThatStream(fileCodeOwnerStatuses).onlyElement();
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
