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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusSubject;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerApprovalCheck#getFileStatusesForAccount(ChangeNotes, Account.Id)}. */
public class CodeOwnerApprovalCheckForAccountTest extends AbstractCodeOwnersTest {
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
  public void notApprovedByUser() throws Exception {
    // create arbitrary code owner config to avoid entering the bootstrapping code path in
    // CodeOwnerApprovalCheck
    createArbitraryCodeOwnerConfigFile();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file would not be approved by the user.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesForAccount(getChangeNotes(changeId), user.id());
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
  public void approvalFromOtherCodeOwnerHasNoEffect() throws Exception {
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
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Add a Code-Review+1 (= code owner approval) from the code owner.
    requestScopeOperations.setApiUser(codeOwner.id());
    recommend(changeId);

    // Verify that the file would not be approved by the user.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesForAccount(getChangeNotes(changeId), user.id());
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
  public void approvedByUser() throws Exception {
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

    // Verify that the file would be approved by the user.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesForAccount(getChangeNotes(changeId), user.id());
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
  public void notApprovedByUser_bootstrapping() throws Exception {
    // since no code owner config exists we are entering the bootstrapping code path in
    // CodeOwnerApprovalCheck

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file would not be approved by the user since the user is not a project owner.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesForAccount(getChangeNotes(changeId), user.id());
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
  public void approvedByProjectOwner_bootstrapping() throws Exception {
    // since no code owner config exists we are entering the bootstrapping code path in
    // CodeOwnerApprovalCheck

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file would be approved by the 'admin' user since the 'admin' user is a
    // project owner.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesForAccount(getChangeNotes(changeId), admin.id());
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
  }

  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "ALL_USERS")
  @Test
  public void approvedByFallbackCodeOwner() throws Exception {
    // create arbitrary code owner config to avoid entering the bootstrapping code path in
    // CodeOwnerApprovalCheck
    createArbitraryCodeOwnerConfigFile();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file would be approved by the user since the user is a fallback code owner.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesForAccount(getChangeNotes(changeId), user.id());
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
  }

  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "ALL_USERS")
  @Test
  public void notApprovedByFallbackCodeOwnerIfCodeOwerIsDefined() throws Exception {
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
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file would not be approved by the user since fallback code owners do not
    // apply.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesForAccount(getChangeNotes(changeId), user.id());
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
  }

  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "ALL_USERS")
  @Test
  public void approvedByFallbackCodeOwner_bootstrappingMode() throws Exception {
    // since no code owner config exists we are entering the bootstrapping code path in
    // CodeOwnerApprovalCheck

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file would be approved by the user since the user is a fallback code owner.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesForAccount(getChangeNotes(changeId), user.id());
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
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
