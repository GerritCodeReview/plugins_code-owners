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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusSubject.assertThatCollection;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.DeleteBranchesInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.cache.PerThreadCache;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.util.AccountTemplateUtil;
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
 * CodeOwnerApprovalCheck#getFileStatusesForAccounts(ChangeNotes,
 * com.google.gerrit.entities.PatchSet, ImmutableSet)} is covered by {@link
 * CodeOwnerApprovalCheckForAccountTest}.
 */
public class CodeOwnerApprovalCheckTest extends AbstractCodeOwnersTest {
  @Inject private ChangeNotes.Factory changeNotesFactory;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private PerThreadProjectCache.Factory perThreadProjectCacheFactory;

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
            () ->
                codeOwnerApprovalCheck.getFileStatusesAsSet(
                    /* changeNotes= */ null, /* start= */ 0, /* limit= */ 0));
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

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
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

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
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

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
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

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
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

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.PENDING,
                String.format(
                    "reviewer %s is a code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));
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

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.modification(
                path,
                CodeOwnerStatus.PENDING,
                String.format(
                    "reviewer %s is a code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));
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

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.deletion(
                path,
                CodeOwnerStatus.PENDING,
                String.format(
                    "reviewer %s is a code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));
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

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.rename(
                oldPath,
                CodeOwnerStatus.PENDING,
                String.format(
                    "reviewer %s is a code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id())),
                newPath,
                CodeOwnerStatus.INSUFFICIENT_REVIEWERS,
                /* reasonNewPath= */ null));
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

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.rename(
                oldPath,
                CodeOwnerStatus.INSUFFICIENT_REVIEWERS,
                /* reasonOldPath= */ null,
                newPath,
                CodeOwnerStatus.PENDING,
                String.format(
                    "reviewer %s is a code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));
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

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));
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

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.modification(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));
  }

  @Test
  public void getStatusForFileDeletion_approved() throws Exception {
    setAsRootCodeOwners(user);

    Path path = Paths.get("/foo/bar.baz");
    String changeId = createChangeWithFileDeletion(path);

    // Add a Code-Review+1 from a code owner (by default this counts as code owner approval).
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.deletion(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));
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

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.rename(
                oldPath,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id())),
                newPath,
                CodeOwnerStatus.INSUFFICIENT_REVIEWERS,
                /* reasonNewPath= */ null));
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

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.rename(
                oldPath,
                CodeOwnerStatus.INSUFFICIENT_REVIEWERS,
                /* reasonOldPath= */ null,
                newPath,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));
  }

  @Test
  public void getStatusForFileAddition_noImplicitApproval() throws Exception {
    testImplicitApprovalOnGetStatusForFileAddition(
        /* implicitApprovalsEnabled= */ false, /* uploaderMatchesChangeOwner= */ true);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void getStatusForFileAddition_noImplicitApproval_uploaderDoesntMatchChangeOwner()
      throws Exception {
    testImplicitApprovalOnGetStatusForFileAddition(
        /* implicitApprovalsEnabled= */ true, /* uploaderMatchesChangeOwner= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void getStatusForFileAddition_withImplicitApproval() throws Exception {
    testImplicitApprovalOnGetStatusForFileAddition(
        /* implicitApprovalsEnabled= */ true, /* uploaderMatchesChangeOwner= */ true);
  }

  private void testImplicitApprovalOnGetStatusForFileAddition(
      boolean implicitApprovalsEnabled, boolean uploaderMatchesChangeOwner) throws Exception {
    TestAccount changeOwner = admin;
    TestAccount otherCodeOwner =
        accountCreator.create(
            "code_owner", "code.owner@example.com", "Code Owner", /* displayName= */ null);

    setAsRootCodeOwners(changeOwner, otherCodeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    if (uploaderMatchesChangeOwner) {
      amendChange(changeOwner, changeId);
    } else {
      amendChange(otherCodeOwner, changeId);
    }

    FileCodeOwnerStatus expectedFileCodeOwnerStatus;
    if (implicitApprovalsEnabled && uploaderMatchesChangeOwner) {
      expectedFileCodeOwnerStatus =
          FileCodeOwnerStatus.addition(
              path,
              CodeOwnerStatus.APPROVED,
              String.format(
                  "implicitly approved by the patch set uploader %s who is a code owner",
                  AccountTemplateUtil.getAccountTemplate(changeOwner.id())));
    } else {
      expectedFileCodeOwnerStatus =
          FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    }

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses).containsExactly(expectedFileCodeOwnerStatus);
  }

  @Test
  public void getStatusForFileModification_noImplicitApproval() throws Exception {
    testImplicitApprovalOnGetStatusForFileModification(
        /* implicitApprovalsEnabled= */ false, /* uploaderMatchesChangeOwner= */ true);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void getStatusForFileModification_noImplicitApproval_uploaderDoesntMatchChangeOwner()
      throws Exception {
    testImplicitApprovalOnGetStatusForFileModification(
        /* implicitApprovalsEnabled= */ true, /* uploaderMatchesChangeOwner= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void getStatusForFileModification_withImplicitApproval() throws Exception {
    testImplicitApprovalOnGetStatusForFileModification(
        /* implicitApprovalsEnabled= */ true, /* uploaderMatchesChangeOwner= */ true);
  }

  private void testImplicitApprovalOnGetStatusForFileModification(
      boolean implicitApprovalsEnabled, boolean uploaderMatchesChangeOwner) throws Exception {
    TestAccount changeOwner = admin;
    TestAccount otherCodeOwner =
        accountCreator.create(
            "code_owner", "code.owner@example.com", "Code Owner", /* displayName= */ null);

    setAsRootCodeOwners(changeOwner, otherCodeOwner);

    Path path = Paths.get("/foo/bar.baz");
    createChange("Test Change", JgitPath.of(path).get(), "file content");
    String changeId =
        createChange("Change Modifying A File", JgitPath.of(path).get(), "new file content")
            .getChangeId();

    if (uploaderMatchesChangeOwner) {
      amendChange(changeOwner, changeId);
    } else {
      amendChange(otherCodeOwner, changeId);
    }

    FileCodeOwnerStatus expectedFileCodeOwnerStatus;
    if (implicitApprovalsEnabled && uploaderMatchesChangeOwner) {
      expectedFileCodeOwnerStatus =
          FileCodeOwnerStatus.modification(
              path,
              CodeOwnerStatus.APPROVED,
              String.format(
                  "implicitly approved by the patch set uploader %s who is a code owner",
                  AccountTemplateUtil.getAccountTemplate(changeOwner.id())));
    } else {
      expectedFileCodeOwnerStatus =
          FileCodeOwnerStatus.modification(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    }

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses).containsExactly(expectedFileCodeOwnerStatus);
  }

  @Test
  public void getStatusForFileDeletion_noImplicitApproval() throws Exception {
    testImplicitApprovalOnGetStatusForFileDeletion(
        /* implicitApprovalsEnabled= */ false, /* uploaderMatchesChangeOwner= */ true);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void getStatusForFileDeletion_noImplicitApproval_uploaderDoesntMatchChangeOwner()
      throws Exception {
    testImplicitApprovalOnGetStatusForFileDeletion(
        /* implicitApprovalsEnabled= */ true, /* uploaderMatchesChangeOwner= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void getStatusForFileDeletion_withImplicitApproval() throws Exception {
    testImplicitApprovalOnGetStatusForFileDeletion(
        /* implicitApprovalsEnabled= */ true, /* uploaderMatchesChangeOwner= */ true);
  }

  private void testImplicitApprovalOnGetStatusForFileDeletion(
      boolean implicitApprovalsEnabled, boolean uploaderMatchesChangeOwner) throws Exception {
    TestAccount changeOwner = admin;
    TestAccount otherCodeOwner =
        accountCreator.create(
            "code_owner", "code.owner@example.com", "Code Owner", /* displayName= */ null);

    setAsRootCodeOwners(changeOwner, otherCodeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId = createChangeWithFileDeletion(path);

    if (uploaderMatchesChangeOwner) {
      amendChange(changeOwner, changeId);
    } else {
      amendChange(otherCodeOwner, changeId);
    }

    FileCodeOwnerStatus expectedFileCodeOwnerStatus;
    if (implicitApprovalsEnabled && uploaderMatchesChangeOwner) {
      expectedFileCodeOwnerStatus =
          FileCodeOwnerStatus.deletion(
              path,
              CodeOwnerStatus.APPROVED,
              String.format(
                  "implicitly approved by the patch set uploader %s who is a code owner",
                  AccountTemplateUtil.getAccountTemplate(changeOwner.id())));
    } else {
      expectedFileCodeOwnerStatus =
          FileCodeOwnerStatus.deletion(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    }

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses).containsExactly(expectedFileCodeOwnerStatus);
  }

  @Test
  public void getStatusForFileRename_noImplicitApprovalOnOldPath() throws Exception {
    testImplicitApprovalOnGetStatusForFileRenameOnOldPath(
        /* implicitApprovalsEnabled= */ false, /* uploaderMatchesChangeOwner= */ true);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void getStatusForFileRename_noImplicitApprovalOnOldPath_uploaderDoesntMatchChangeOwner()
      throws Exception {
    testImplicitApprovalOnGetStatusForFileRenameOnOldPath(
        /* implicitApprovalsEnabled= */ true, /* uploaderMatchesChangeOwner= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void getStatusForFileRename_withImplicitApprovalOnOldPath() throws Exception {
    testImplicitApprovalOnGetStatusForFileRenameOnOldPath(
        /* implicitApprovalsEnabled= */ true, /* uploaderMatchesChangeOwner= */ true);
  }

  private void testImplicitApprovalOnGetStatusForFileRenameOnOldPath(
      boolean implicitApprovalsEnabled, boolean uploaderMatchesChangeOwner) throws Exception {
    TestAccount changeOwner = admin;
    TestAccount otherCodeOwner =
        accountCreator.create(
            "code_owner", "code.owner@example.com", "Code Owner", /* displayName= */ null);

    setAsCodeOwners("/foo/bar/", changeOwner, otherCodeOwner);

    Path oldPath = Paths.get("/foo/bar/abc.txt");
    Path newPath = Paths.get("/foo/baz/abc.txt");
    String changeId = createChangeWithFileRename(oldPath, newPath);

    if (uploaderMatchesChangeOwner) {
      amendChange(changeOwner, changeId);
    } else {
      amendChange(otherCodeOwner, changeId);
    }

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    FileCodeOwnerStatus expectedFileCodeOwnerStatus;
    if (implicitApprovalsEnabled && uploaderMatchesChangeOwner) {
      expectedFileCodeOwnerStatus =
          FileCodeOwnerStatus.rename(
              oldPath,
              CodeOwnerStatus.APPROVED,
              String.format(
                  "implicitly approved by the patch set uploader %s who is a code owner",
                  AccountTemplateUtil.getAccountTemplate(changeOwner.id())),
              newPath,
              CodeOwnerStatus.INSUFFICIENT_REVIEWERS,
              /* reasonNewPath= */ null);
    } else {
      expectedFileCodeOwnerStatus =
          FileCodeOwnerStatus.rename(
              oldPath,
              CodeOwnerStatus.INSUFFICIENT_REVIEWERS,
              newPath,
              CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    }

    assertThatCollection(fileCodeOwnerStatuses).containsExactly(expectedFileCodeOwnerStatus);
  }

  @Test
  public void testImplicitApprovalOnGetStatusForFileRenameOnNewPath() throws Exception {
    testImplicitApprovalOnGetStatusForFileRenameOnNewPath(
        /* implicitApprovalsEnabled= */ false, /* uploaderMatchesChangeOwner= */ true);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void getStatusForFileRename_noImplicitApprovalOnNewPath_uploaderDoesntMatchChangeOwner()
      throws Exception {
    testImplicitApprovalOnGetStatusForFileRenameOnNewPath(
        /* implicitApprovalsEnabled= */ true, /* uploaderMatchesChangeOwner= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void getStatusForFileRename_withImplicitApprovalOnNewPath() throws Exception {
    testImplicitApprovalOnGetStatusForFileRenameOnNewPath(
        /* implicitApprovalsEnabled= */ true, /* uploaderMatchesChangeOwner= */ true);
  }

  private void testImplicitApprovalOnGetStatusForFileRenameOnNewPath(
      boolean implicitApprovalsEnabled, boolean uploaderMatchesChangeOwner) throws Exception {
    TestAccount changeOwner = admin;
    TestAccount otherCodeOwner =
        accountCreator.create(
            "code_owner", "code.owner@example.com", "Code Owner", /* displayName= */ null);

    setAsCodeOwners("/foo/baz/", changeOwner, otherCodeOwner);

    Path oldPath = Paths.get("/foo/bar/abc.txt");
    Path newPath = Paths.get("/foo/baz/abc.txt");
    String changeId = createChangeWithFileRename(oldPath, newPath);

    if (uploaderMatchesChangeOwner) {
      amendChange(changeOwner, changeId);
    } else {
      amendChange(otherCodeOwner, changeId);
    }

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    FileCodeOwnerStatus expectedFileCodeOwnerStatus;
    if (implicitApprovalsEnabled && uploaderMatchesChangeOwner) {
      expectedFileCodeOwnerStatus =
          FileCodeOwnerStatus.rename(
              oldPath,
              CodeOwnerStatus.INSUFFICIENT_REVIEWERS,
              /* reasonOldPath= */ null,
              newPath,
              CodeOwnerStatus.APPROVED,
              String.format(
                  "implicitly approved by the patch set uploader %s who is a code owner",
                  AccountTemplateUtil.getAccountTemplate(changeOwner.id())));
    } else {
      expectedFileCodeOwnerStatus =
          FileCodeOwnerStatus.rename(
              oldPath,
              CodeOwnerStatus.INSUFFICIENT_REVIEWERS,
              newPath,
              CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    }

    assertThatCollection(fileCodeOwnerStatuses).containsExactly(expectedFileCodeOwnerStatus);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void
      getStatusForFileAddition_noImplicitlyApprovalByPatchSetUploaderThatDoesntOwnTheChange()
          throws Exception {
    setAsRootCodeOwners(admin);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();
    amendChange(user, changeId);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
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
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add an approval by a user that is a code owner only through the global code ownership.
    approve(changeId);

    // Check that the file is approved now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a code owner (all users are code owners)",
                    AccountTemplateUtil.getAccountTemplate(admin.id()))));
  }

  @Test
  public void everyoneIsCodeOwner_noImplicitApproval() throws Exception {
    testImplicitlyApprovedWhenEveryoneIsCodeOwner(
        /* implicitApprovalsEnabled= */ false, /* uploaderMatchesChangeOwner= */ true);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void everyoneIsCodeOwner_noImplicitApproval_uploaderDoesntMatchChangeOwner()
      throws Exception {
    testImplicitlyApprovedWhenEveryoneIsCodeOwner(
        /* implicitApprovalsEnabled= */ true, /* uploaderMatchesChangeOwner= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void everyoneIsCodeOwner_withImplicitApproval() throws Exception {
    testImplicitlyApprovedWhenEveryoneIsCodeOwner(
        /* implicitApprovalsEnabled= */ true, /* uploaderMatchesChangeOwner= */ true);
  }

  private void testImplicitlyApprovedWhenEveryoneIsCodeOwner(
      boolean implicitApprovalsEnabled, boolean uploaderMatchesChangeOwner) throws Exception {
    TestAccount changeOwner = admin;
    TestAccount otherCodeOwner = user;

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

    if (uploaderMatchesChangeOwner) {
      amendChange(changeOwner, changeId);
    } else {
      amendChange(otherCodeOwner, changeId);
    }

    FileCodeOwnerStatus expectedFileCodeOwnerStatus;
    if (implicitApprovalsEnabled && uploaderMatchesChangeOwner) {
      expectedFileCodeOwnerStatus =
          FileCodeOwnerStatus.addition(
              path,
              CodeOwnerStatus.APPROVED,
              String.format(
                  "implicitly approved by the patch set uploader %s who is a code owner"
                      + " (all users are code owners)",
                  AccountTemplateUtil.getAccountTemplate(changeOwner.id())));
    } else {
      expectedFileCodeOwnerStatus =
          FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    }

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses).containsExactly(expectedFileCodeOwnerStatus);
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
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a user as reviewer that is a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Check that the status of the file is PENDING now.
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.PENDING,
                String.format(
                    "reviewer %s is a code owner (all users are code owners)",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));
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
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
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
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a global code owner",
                    AccountTemplateUtil.getAccountTemplate(bot.id()))));
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.globalCodeOwner",
      values = {"bot@example.com", "otherBot@example.com"})
  public void globalCodeOwner_noImplicitApproval() throws Exception {
    testImplicitlyApprovedByGlobalCodeOwner(
        /* implicitApprovalsEnabled= */ false, /* uploaderMatchesChangeOwner= */ true);
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.globalCodeOwner",
      values = {"bot@example.com", "otherBot@example.com"})
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void globalCodeOwner_noImplicitApproval_uploaderDoesntMatchChangeOwner() throws Exception {
    testImplicitlyApprovedByGlobalCodeOwner(
        /* implicitApprovalsEnabled= */ true, /* uploaderMatchesChangeOwner= */ false);
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.globalCodeOwner",
      values = {"bot@example.com", "otherBot@example.com"})
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void globalCodeOwner_withImplicitApproval() throws Exception {
    testImplicitlyApprovedByGlobalCodeOwner(
        /* implicitApprovalsEnabled= */ true, /* uploaderMatchesChangeOwner= */ true);
  }

  private void testImplicitlyApprovedByGlobalCodeOwner(
      boolean implicitApprovalsEnabled, boolean uploaderMatchesChangeOwner) throws Exception {
    TestAccount bot =
        accountCreator.create("bot", "bot@example.com", "Bot", /* displayName= */ null);
    TestAccount otherBot =
        accountCreator.create(
            "other_bot", "otherBot@example.com", "Other Bot", /* displayName= */ null);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(bot, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    if (uploaderMatchesChangeOwner) {
      amendChange(bot, changeId);
    } else {
      amendChange(otherBot, changeId);
    }

    FileCodeOwnerStatus expectedFileCodeOwnerStatus;
    if (implicitApprovalsEnabled && uploaderMatchesChangeOwner) {
      expectedFileCodeOwnerStatus =
          FileCodeOwnerStatus.addition(
              path,
              CodeOwnerStatus.APPROVED,
              String.format(
                  "implicitly approved by the patch set uploader %s who is a global code"
                      + " owner",
                  AccountTemplateUtil.getAccountTemplate(bot.id())));
    } else {
      expectedFileCodeOwnerStatus =
          FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    }

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses).containsExactly(expectedFileCodeOwnerStatus);
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
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add the bot approve as reviewer.
    gApi.changes().id(changeId).addReviewer(bot.email());

    // Check that the status of the file is PENDING now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.PENDING,
                String.format(
                    "reviewer %s is a global code owner",
                    AccountTemplateUtil.getAccountTemplate(bot.id()))));

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
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a global code owner",
                    AccountTemplateUtil.getAccountTemplate(bot.id()))));
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
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add an approval by a user that is a code owner only through the global code ownership.
    approve(changeId);

    // Check that the file is approved now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a global code owner"
                        + " (all users are global code owners)",
                    AccountTemplateUtil.getAccountTemplate(admin.id()))));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  public void everyoneIsGlobalCodeOwner_noImplicitApproval() throws Exception {
    testImplicitlyApprovedByGlobalCodeOwnerWhenEveryoneIsGlobalCodeOwner(
        /* implicitApprovalsEnabled= */ false, /* uploaderMatchesChangeOwner= */ true);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void everyoneIsGlobalCodeOwner_noImplicitApproval_uploaderDoesntMatchChangeOwner()
      throws Exception {
    testImplicitlyApprovedByGlobalCodeOwnerWhenEveryoneIsGlobalCodeOwner(
        /* implicitApprovalsEnabled= */ true, /* uploaderMatchesChangeOwner= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void everyoneIsGlobalCodeOwner_withImplicitApproval() throws Exception {
    testImplicitlyApprovedByGlobalCodeOwnerWhenEveryoneIsGlobalCodeOwner(
        /* implicitApprovalsEnabled= */ true, /* uploaderMatchesChangeOwner= */ true);
  }

  private void testImplicitlyApprovedByGlobalCodeOwnerWhenEveryoneIsGlobalCodeOwner(
      boolean implicitApprovalsEnabled, boolean uploaderMatchesChangeOwner) throws Exception {
    TestAccount changeOwner = admin;
    TestAccount otherCodeOwner = user;
    // Create a change as a user that is a code owner only through the global code ownership.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    if (uploaderMatchesChangeOwner) {
      amendChange(changeOwner, changeId);
    } else {
      amendChange(otherCodeOwner, changeId);
    }

    FileCodeOwnerStatus expectedFileCodeOwnerStatus;
    if (implicitApprovalsEnabled && uploaderMatchesChangeOwner) {
      expectedFileCodeOwnerStatus =
          FileCodeOwnerStatus.addition(
              path,
              CodeOwnerStatus.APPROVED,
              String.format(
                  "implicitly approved by the patch set uploader %s who is a global code owner"
                      + " (all users are global code owners)",
                  AccountTemplateUtil.getAccountTemplate(changeOwner.id())));
    } else {
      expectedFileCodeOwnerStatus =
          FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    }

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses).containsExactly(expectedFileCodeOwnerStatus);
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
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a user as reviewer that is a code owner only through the global code ownership.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Check that the status of the file is PENDING now.
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.PENDING,
                String.format(
                    "reviewer %s is a global code owner (all users are global code owners)",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));
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

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    // The expected status is APPROVED since 'user' which is configured as code owner on the root
    // level approved the change.
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));
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
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path1, CodeOwnerStatus.INSUFFICIENT_REVIEWERS),
            FileCodeOwnerStatus.addition(path2, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add an override approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // With Owners-Override approval the expected status is APPROVED.
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path1,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "override approval Owners-Override+1 by %s is present",
                    AccountTemplateUtil.getAccountTemplate(admin.id()))),
            FileCodeOwnerStatus.addition(
                path2,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "override approval Owners-Override+1 by %s is present",
                    AccountTemplateUtil.getAccountTemplate(admin.id()))));
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
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path1, CodeOwnerStatus.INSUFFICIENT_REVIEWERS),
            FileCodeOwnerStatus.addition(path2, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add an override approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // With override approval the expected status is APPROVED.
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path1,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "override approval Owners-Override+1 by %s is present",
                    AccountTemplateUtil.getAccountTemplate(admin.id()))),
            FileCodeOwnerStatus.addition(
                path2,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "override approval Owners-Override+1 by %s is present",
                    AccountTemplateUtil.getAccountTemplate(admin.id()))));

    // Delete the override approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 0));

    // Without override approval the expected status is INSUFFICIENT_REVIEWERS.
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path1, CodeOwnerStatus.INSUFFICIENT_REVIEWERS),
            FileCodeOwnerStatus.addition(path2, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add another override approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Another-Override", 1));

    // With override approval the expected status is APPROVED.
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path1,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "override approval Another-Override+1 by %s is present",
                    AccountTemplateUtil.getAccountTemplate(admin.id()))),
            FileCodeOwnerStatus.addition(
                path2,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "override approval Another-Override+1 by %s is present",
                    AccountTemplateUtil.getAccountTemplate(admin.id()))));
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
  public void getStatus_branchDeleted_defaultCodeOwner() throws Exception {
    String branchName = "tempBranch";
    createBranch(BranchNameKey.create(project, branchName));

    // Create a change as a user that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    DeleteBranchesInput input = new DeleteBranchesInput();
    input.branches = ImmutableList.of(branchName);
    gApi.projects().name(project.get()).deleteBranches(input);

    testGetStatusBranchDoesNotExistWithDefaultCodeOwner(changeId, path);
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void getStatus_initialChange_defaultCodeOwner() throws Exception {
    // Create a change as a user that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    testGetStatusBranchDoesNotExistWithDefaultCodeOwner(changeId, path);
  }

  private void testGetStatusBranchDoesNotExistWithDefaultCodeOwner(String changeId, Path path)
      throws Exception {
    setAsDefaultCodeOwners(admin);

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add default code owner as a reviewer.
    gApi.changes().id(changeId).addReviewer(admin.email());

    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.PENDING,
                String.format(
                    "reviewer %s is a default code owner",
                    AccountTemplateUtil.getAccountTemplate(admin.id()))));

    // Approve as default code owner.
    approve(changeId);

    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a default code owner",
                    AccountTemplateUtil.getAccountTemplate(admin.id()))));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "bot@example.com")
  public void getStatus_branchDeleted_globalCodeOwner() throws Exception {
    // Create a bot user that is a global code owner.
    TestAccount bot =
        accountCreator.create("bot", "bot@example.com", "Bot", /* displayName= */ null);

    String branchName = "tempBranch";
    createBranch(BranchNameKey.create(project, branchName));

    // Create a change as a user that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(admin, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    DeleteBranchesInput input = new DeleteBranchesInput();
    input.branches = ImmutableList.of(branchName);
    gApi.projects().name(project.get()).deleteBranches(input);

    testGetStatusBranchDoesNotExistWithGlobalCodeOwner(changeId, path, bot);
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "bot@example.com")
  public void getStatus_initialChange_globalCodeOwner() throws Exception {
    // Create a bot user that is a global code owner.
    TestAccount bot =
        accountCreator.create("bot", "bot@example.com", "Bot", /* displayName= */ null);

    // Create a change as a user that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(admin, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    testGetStatusBranchDoesNotExistWithGlobalCodeOwner(changeId, path, bot);
  }

  private void testGetStatusBranchDoesNotExistWithGlobalCodeOwner(
      String changeId, Path path, TestAccount globalCodeOwner) throws Exception {
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add global code owner as a reviewer.
    gApi.changes().id(changeId).addReviewer(globalCodeOwner.email());

    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.PENDING,
                String.format(
                    "reviewer %s is a global code owner",
                    AccountTemplateUtil.getAccountTemplate(globalCodeOwner.id()))));

    // Approve as default code owner.
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-2, +2))
        .update();
    requestScopeOperations.setApiUser(globalCodeOwner.id());
    approve(changeId);

    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a global code owner",
                    AccountTemplateUtil.getAccountTemplate(globalCodeOwner.id()))));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void getStatus_branchDeleted_override() throws Exception {
    String branchName = "tempBranch";
    createBranch(BranchNameKey.create(project, branchName));

    // Create a change as a user that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(admin, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    DeleteBranchesInput input = new DeleteBranchesInput();
    input.branches = ImmutableList.of(branchName);
    gApi.projects().name(project.get()).deleteBranches(input);

    testGetStatusBranchDoesNotExistWithOverride(changeId, path);
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void getStatus_initialChange_override() throws Exception {
    // Create a change as a user that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(admin, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    testGetStatusBranchDoesNotExistWithOverride(changeId, path);
  }

  private void testGetStatusBranchDoesNotExistWithOverride(String changeId, Path path)
      throws Exception {
    createOwnersOverrideLabel();

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Apply an override
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "override approval Owners-Override+1 by %s is present",
                    AccountTemplateUtil.getAccountTemplate(admin.id()))));
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
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
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
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));

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
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void overridenByStickyApprovalOnOldPatchSet() throws Exception {
    createOwnersOverrideLabel();

    // make the override label sticky
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.copyCondition = "is:ANY";
    gApi.projects().name(project.get()).label("Owners-Override").update(input);

    // Create a change as a user that is not a code owner.
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(user, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved yet.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Apply an override
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // Check that the file is approved now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "override approval Owners-Override+1 by %s is present",
                    AccountTemplateUtil.getAccountTemplate(admin.id()))));

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
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "override approval Owners-Override+1 by %s is present",
                    AccountTemplateUtil.getAccountTemplate(admin.id()))));
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
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
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
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));
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
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Let 'user2' override with Owners-Override+2
    requestScopeOperations.setApiUser(user2.id());
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 2));

    // Check that the file is approved now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "override approval Owners-Override+2 by %s is present",
                    AccountTemplateUtil.getAccountTemplate(user2.id()))));
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
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
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
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a default code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));
  }

  @Test
  public void defaultCodeOwner_noImplicitApproval() throws Exception {
    testImplicitlyApprovedByDefaultCodeOwner(
        /* implicitApprovalsEnabled= */ false, /* uploaderMatchesChangeOwner= */ true);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void defaultCodeOwner_noImplicitApproval_uploaderDoesntMatchChangeOwner()
      throws Exception {
    testImplicitlyApprovedByDefaultCodeOwner(
        /* implicitApprovalsEnabled= */ true, /* uploaderMatchesChangeOwner= */ false);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void defaultCodeOwner_withImplicitApproval() throws Exception {
    testImplicitlyApprovedByDefaultCodeOwner(
        /* implicitApprovalsEnabled= */ true, /* uploaderMatchesChangeOwner= */ true);
  }

  private void testImplicitlyApprovedByDefaultCodeOwner(
      boolean implicitApprovalsEnabled, boolean uploaderMatchesChangeOwner) throws Exception {
    TestAccount changeOwner = admin;
    TestAccount otherCodeOwner =
        accountCreator.create(
            "code_owner", "code.owner@example.com", "Code Owner", /* displayName= */ null);

    setAsDefaultCodeOwners(changeOwner, otherCodeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    if (uploaderMatchesChangeOwner) {
      amendChange(changeOwner, changeId);
    } else {
      amendChange(otherCodeOwner, changeId);
    }

    FileCodeOwnerStatus expectedFileCodeOwnerStatus;
    if (implicitApprovalsEnabled && uploaderMatchesChangeOwner) {
      expectedFileCodeOwnerStatus =
          FileCodeOwnerStatus.addition(
              path,
              CodeOwnerStatus.APPROVED,
              String.format(
                  "implicitly approved by the patch set uploader %s who is a default code owner",
                  AccountTemplateUtil.getAccountTemplate(changeOwner.id())));
    } else {
      expectedFileCodeOwnerStatus =
          FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    }

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses).containsExactly(expectedFileCodeOwnerStatus);
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
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add the default code owner as reviewer.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Check that the status of the file is PENDING now.
    requestScopeOperations.setApiUser(admin.id());
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.PENDING,
                String.format(
                    "reviewer %s is a default code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));

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
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a default code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));
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
        getFileCodeOwnerStatuses(changeIdOfRevert);
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
        getFileCodeOwnerStatuses(changeIdOfRevert);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.deletion(
                path,
                CodeOwnerStatus.APPROVED,
                "change is a pure revert and is exempted from requiring code owner approvals"));
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
        getFileCodeOwnerStatuses(revertChange.changeId);
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
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "patch set uploader %s is exempted from requiring code owner approvals",
                    AccountTemplateUtil.getAccountTemplate(exemptedUser.id()))));

    // Amend the change by another user, so that the other non-exempted user becomes the last
    // uploader.
    amendChange(user, changeId);

    // Check that the file is no longer approved since the uploader is not exempted from requiring
    // code owner approvals.
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  @TestProjectInput(submitType = SubmitType.REBASE_ALWAYS)
  public void implicitApproval_rebaseAlways() throws Exception {
    TestAccount changeOwner = admin;
    setAsRootCodeOwners(changeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // The change is implicitly approved because the change owner and uploader is a code owner.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "implicitly approved by the patch set uploader %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(changeOwner.id()))));

    // Allow all users to approve and submit changes.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            TestProjectUpdate.allowLabel("Code-Review")
                .range(0, 2)
                .ref("refs/*")
                .group(REGISTERED_USERS)
                .build())
        .add(allow(Permission.SUBMIT).ref("refs/*").group(REGISTERED_USERS))
        .update();

    // Add a Code-Review+2 vote from a non code owner to satisfy the submit requirement for the
    // Code-Review label.
    requestScopeOperations.setApiUser(user.id());
    approve(changeId);

    // Submit the change as a non code owner.
    gApi.changes().id(changeId).current().submit();

    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    assertThat(changeInfo.status).isEqualTo(ChangeStatus.MERGED);

    // Since the submit type is REBASE_ALWAYS we expect that a new patch set was added that has the
    // submitter as uploader.
    assertThat(changeInfo.revisions).hasSize(2);
    assertThat(changeInfo.revisions.get(changeInfo.currentRevision).uploader._accountId)
        .isEqualTo(user.id().get());

    // Asking for the code owner status now reports the file with status INSUFFICIENT_REVIEWERS
    // because the implicit approval does not apply to the newly created patch set (since the
    // uploader of the latest patch set is not a code owner and doesn't match the change owner).
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void missingProjectOfUnresolvedImportsIsCached() throws Exception {
    // Create a root code owner config with an unresolvable import from a non-existing project.
    Project.NameKey nonExistingProject = Project.nameKey("non-existing");
    CodeOwnerConfigReference nonResolvablecodeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                codeOwnerConfigOperations
                    .codeOwnerConfig(CodeOwnerConfig.Key.create(nonExistingProject, "master", "/"))
                    .getFilePath())
            .setProject(nonExistingProject)
            .build();
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .addImport(nonResolvablecodeOwnerConfigReference)
        .create();

    // Create a change with 2 files, so that the root code owner config is evaluated 2 times (once
    // per file).
    Path path1 = Paths.get("/foo/bar.txt");
    Path path2 = Paths.get("/foo/baz.txt");
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Change Adding Files",
            ImmutableMap.of(
                JgitPath.of(path1).get(), "file content",
                JgitPath.of(path2).get(), "file content"));
    String changeId = push.to("refs/for/master").getChangeId();

    // Add a reviewer that is a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Get the code owner status for all files in the change.
    ChangeNotes changeNotes = getChangeNotes(changeId);
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses;
    // Create the PerThreadCache. This is needed because we invoke CodeOwnerApprovalCheck directly,
    // rather than making a REST call in which case the framework would take care to create it.
    try (PerThreadCache threadLocalCache = PerThreadCache.create()) {
      fileCodeOwnerStatuses =
          codeOwnerApprovalCheck.getFileStatusesAsSet(changeNotes, /* start= */ 0, /* limit= */ 0);
      // Verify that the non-existing project was cached as missing project
      assertThat(perThreadProjectCacheFactory.getOrCreate().getMissingProjects())
          .containsExactly(nonExistingProject);
    }

    // Verify that the correct result was returned.
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path1,
                CodeOwnerStatus.PENDING,
                String.format(
                    "reviewer %s is a code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id()))),
            FileCodeOwnerStatus.addition(
                path2,
                CodeOwnerStatus.PENDING,
                String.format(
                    "reviewer %s is a code owner",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));
  }

  private ImmutableSet<FileCodeOwnerStatus> getFileCodeOwnerStatuses(String changeId)
      throws Exception {
    return codeOwnerApprovalCheck.getFileStatusesAsSet(
        getChangeNotes(changeId), /* start= */ 0, /* limit= */ 0);
  }

  private ChangeNotes getChangeNotes(String changeId) throws Exception {
    return changeNotesFactory.create(project, Change.id(gApi.changes().id(changeId).get()._number));
  }
}
