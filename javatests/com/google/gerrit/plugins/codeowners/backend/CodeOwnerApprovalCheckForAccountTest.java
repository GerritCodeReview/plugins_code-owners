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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusSubject;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.truth.ListSubject;
import com.google.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link CodeOwnerApprovalCheck#getFileStatusesForAccount(ChangeNotes, PatchSet,
 * Account.Id)}.
 */
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
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();
    ChangeNotes changeNotes = getChangeNotes(changeId);

    // Verify that the file would not be approved by the user.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesForAccount(
            changeNotes, changeNotes.getCurrentPatchSet(), user.id());
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
    ChangeNotes changeNotes = getChangeNotes(changeId);

    // Add a Code-Review+1 (= code owner approval) from the code owner.
    requestScopeOperations.setApiUser(codeOwner.id());
    recommend(changeId);

    // Verify that the file would not be approved by the user.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesForAccount(
            changeNotes, changeNotes.getCurrentPatchSet(), user.id());
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
    ChangeNotes changeNotes = getChangeNotes(changeId);

    // Verify that the file would be approved by the user.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesForAccount(
            changeNotes, changeNotes.getCurrentPatchSet(), user.id());
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
  public void approvedByUser_forPatchSets() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    Path path1 = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path1).get(), "file content")
            .getChangeId();

    // amend change and add another file
    Path path2 = Paths.get("/foo/baz.bar");
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "subject",
            ImmutableMap.of(
                JgitPath.of(path1).get(), "file content", JgitPath.of(path2).get(), "file content"),
            changeId);
    push.to("refs/for/master").assertOkStatus();

    ChangeNotes changeNotes = getChangeNotes(changeId);

    // Verify that the file in patch set 1 would be approved by the user.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesForAccount(
            changeNotes,
            changeNotes.getPatchSets().get(PatchSet.id(changeNotes.getChangeId(), 1)),
            user.id());
    FileCodeOwnerStatusSubject fileCodeOwnerStatusSubject =
        assertThatStream(fileCodeOwnerStatuses).onlyElement();
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path1);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);

    // Verify that both files in patch set 2 would be approved by the user.
    fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesForAccount(
            changeNotes,
            changeNotes.getPatchSets().get(PatchSet.id(changeNotes.getChangeId(), 2)),
            user.id());
    ListSubject<FileCodeOwnerStatusSubject, FileCodeOwnerStatus> fileCodeOwnerStatusListSubject =
        assertThatStream(fileCodeOwnerStatuses);
    fileCodeOwnerStatusListSubject.hasSize(2);
    fileCodeOwnerStatusSubject = fileCodeOwnerStatusListSubject.element(0);
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path1);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
    fileCodeOwnerStatusSubject = fileCodeOwnerStatusListSubject.element(1);
    fileCodeOwnerStatusSubject.hasNewPathStatus().value().hasPathThat().isEqualTo(path2);
    fileCodeOwnerStatusSubject
        .hasNewPathStatus()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "PROJECT_OWNERS")
  public void notApprovedByUser_projectOwnersAreFallbackCodeOwner() throws Exception {
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();
    ChangeNotes changeNotes = getChangeNotes(changeId);

    // Verify that the file would not be approved by the user since the user is not a project owner.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesForAccount(
            changeNotes, changeNotes.getCurrentPatchSet(), user.id());
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
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "PROJECT_OWNERS")
  public void approvedByProjectOwner_projectOwnersAreFallbackCodeOwner() throws Exception {
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();
    ChangeNotes changeNotes = getChangeNotes(changeId);

    // Verify that the file would be approved by the 'admin' user since the 'admin' user is a
    // project owner.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesForAccount(
            changeNotes, changeNotes.getCurrentPatchSet(), admin.id());
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
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();
    ChangeNotes changeNotes = getChangeNotes(changeId);

    // Verify that the file would be approved by the user since the user is a fallback code owner.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesForAccount(
            changeNotes, changeNotes.getCurrentPatchSet(), user.id());
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
    ChangeNotes changeNotes = getChangeNotes(changeId);

    // Verify that the file would not be approved by the user since fallback code owners do not
    // apply.
    Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesForAccount(
            changeNotes, changeNotes.getCurrentPatchSet(), user.id());
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
