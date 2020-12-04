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

import static com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusSubject.assertThatStream;

import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
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
import org.junit.Before;
import org.junit.Test;

public class CodeOwnerApprovalCheckWithSelfApprovalsIgnored extends AbstractCodeOwnersTest {
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

  @Before
  public void disableSelfApprovals() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.ignoreSelfApproval = true;
    gApi.projects().name(allProjects.get()).label("Code-Review").update(input);
  }

  @Test
  public void notApprovedByUploaderWhoIsChangeOwner() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "CodeOwner", /* displayName= */ null);

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(codeOwner.email())
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(codeOwner, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved.
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

    // Add a self Code-Review+1 (= code owner approval).
    requestScopeOperations.setApiUser(codeOwner.id());
    recommend(changeId);

    // Verify that the file is not approved (since self approvals are ignored).
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    fileCodeOwnerStatusSubject = assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
  }

  @Test
  public void approvedByChangeOwnerThatIsNotUploader() throws Exception {
    TestAccount changeOwner =
        accountCreator.create(
            "changeOwner", "changeOwner@example.com", "ChangeOwner", /* displayName= */ null);

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(changeOwner.email())
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(changeOwner, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Upload another patch set by another user.
    amendChange(admin, changeId);

    // Verify that the file is not approved.
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

    // Add a Code-Review+1 (= code owner approval) from the change owner.
    requestScopeOperations.setApiUser(changeOwner.id());
    recommend(changeId);

    // Verify that the file is approved now (since the change owner is not the uploader of the
    // current patch set).
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
  public void notApprovedByUploader() throws Exception {
    TestAccount changeOwner =
        accountCreator.create(
            "changeOwner", "changeOwner@example.com", "ChangeOwner", /* displayName= */ null);
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "CodeOwner", /* displayName= */ null);

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(codeOwner.email())
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(changeOwner, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Upload another patch set by a code owner.
    amendChange(codeOwner, changeId);

    // Verify that the file is not approved.
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

    // Add the code owner as reviewer.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Verify that the file is not pending (the code owner is the uploader of the current patch set
    // and self approvals are ignored).
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    fileCodeOwnerStatusSubject = assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);

    // Add a Code-Review+1 (= code owner approval) by the code owner.
    requestScopeOperations.setApiUser(codeOwner.id());
    recommend(changeId);

    // Verify that the file is not approved (since the code owner is the uploader of the current
    // patch set and self approvals are ignored).
    fileCodeOwnerStatuses = codeOwnerApprovalCheck.getFileStatuses(getChangeNotes(changeId));
    fileCodeOwnerStatusSubject = assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void notImplicitlyApprovedByUploaderWhoIsChangeOwner() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "CodeOwner", /* displayName= */ null);

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(codeOwner.email())
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(codeOwner, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved.
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
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void notImplicitlyApprovedByUploader() throws Exception {
    TestAccount changeOwner =
        accountCreator.create(
            "changeOwner", "changeOwner@example.com", "ChangeOwner", /* displayName= */ null);
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "CodeOwner", /* displayName= */ null);

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(codeOwner.email())
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(changeOwner, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Upload another patch set by a code owner.
    amendChange(codeOwner, changeId);

    // Verify that the file is not approved.
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
  }

  private ChangeNotes getChangeNotes(String changeId) throws Exception {
    return changeNotesFactory.create(project, Change.id(gApi.changes().id(changeId).get()._number));
  }
}
