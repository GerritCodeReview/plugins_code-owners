// Copyright (C) 2022 The Android Open Source Project
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

import static com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusSubject.assertThatCollection;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.util.AccountTemplateUtil;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerApprovalCheck} with sticky approvals enabled. */
public class CodeOwnerApprovalCheckWithStickyApprovalsTest extends AbstractCodeOwnersTest {
  @Inject private ChangeNotes.Factory changeNotesFactory;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  private CodeOwnerApprovalCheck codeOwnerApprovalCheck;
  private CodeOwnerConfigOperations codeOwnerConfigOperations;

  /** Returns a {@code gerrit.config} that configures all users as fallback code owners. */
  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = AbstractCodeOwnersTest.defaultConfig();
    cfg.setBoolean(
        "plugin", "code-owners", GeneralConfig.KEY_ENABLE_STICKY_APPROVALS, /* value= */ true);
    return cfg;
  }

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerApprovalCheck = plugin.getSysInjector().getInstance(CodeOwnerApprovalCheck.class);
    codeOwnerConfigOperations =
        plugin.getSysInjector().getInstance(CodeOwnerConfigOperations.class);
  }

  @Test
  public void notApproved_noStickyApproval() throws Exception {
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file is not approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // create a second patch set so that there is a previous patch set
    amendChange(changeId, "Change Adding A File", JgitPath.of(path).get(), "new file content")
        .assertOkStatus();

    // Verify that the file is not approved.
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void notApproved_byPreviousApprovalOfNonCodeOwner() throws Exception {
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // code owner approve as user who is not a code owner
    recommend(changeId);

    // create a second patch set so that the approval becomes an approval on a previous patch set
    amendChange(changeId, "Change Adding A File", JgitPath.of(path).get(), "new file content")
        .assertOkStatus();

    // Verify that the file is not approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void approved_byStickyApprovalOnPreviousPatchSet() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "CodeOwner", /* displayName= */ null);
    setAsRootCodeOwners(codeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // code owner approve
    requestScopeOperations.setApiUser(codeOwner.id());
    recommend(changeId);

    // create a second patch set so that the approval becomes an approval on a previous patch set
    amendChange(changeId, "Change Adding A File", JgitPath.of(path).get(), "new file content")
        .assertOkStatus();

    // Verify that the file is approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved on patch set 1 by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(codeOwner.id()))));
  }

  @Test
  public void approved_byStickyApprovalOnOldPatchSet() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "CodeOwner", /* displayName= */ null);
    setAsRootCodeOwners(codeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // code owner approve
    requestScopeOperations.setApiUser(codeOwner.id());
    recommend(changeId);

    // create several new patch sets so that the approval becomes an approval on an old patch set
    amendChange(changeId, "Change Adding A File", JgitPath.of(path).get(), "new file content")
        .assertOkStatus();
    amendChange(changeId, "Change Adding A File", JgitPath.of(path).get(), "newer file content")
        .assertOkStatus();
    amendChange(changeId, "Change Adding A File", JgitPath.of(path).get(), "newest file content")
        .assertOkStatus();

    // Verify that the file is approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved on patch set 1 by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(codeOwner.id()))));
  }

  @Test
  public void approved_byStickyApprovalsOfDifferentUsersOnDifferentPreviousPatchSets()
      throws Exception {
    TestAccount codeOwner1 =
        accountCreator.create(
            "codeOwner1", "codeOwner1@example.com", "CodeOwner1", /* displayName= */ null);
    setAsCodeOwners("/foo/", codeOwner1);
    TestAccount codeOwner2 =
        accountCreator.create(
            "codeOwner2", "codeOwner2@example.com", "CodeOwner2", /* displayName= */ null);
    setAsCodeOwners("/bar/", codeOwner2);

    Path path1 = Paths.get("/foo/bar.baz");
    Path path2 = Paths.get("/bar/foo.baz");
    String changeId =
        createChange(
                "Change Adding A File",
                ImmutableMap.of(
                    JgitPath.of(path1).get(),
                    "file content",
                    JgitPath.of(path2).get(),
                    "file content"))
            .getChangeId();

    // code owner approve first path
    requestScopeOperations.setApiUser(codeOwner1.id());
    recommend(changeId);

    // create a second patch set so that the approval becomes an approval on a previous patch set
    amendChange(
            changeId,
            "Change Adding A File",
            ImmutableMap.of(
                JgitPath.of(path1).get(),
                "new file content",
                JgitPath.of(path2).get(),
                "new file content"))
        .assertOkStatus();

    // Verify that the path1 is approved, but path2 isn't.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path1,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved on patch set 1 by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(codeOwner1.id()))),
            FileCodeOwnerStatus.addition(path2, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // code owner approve second path
    requestScopeOperations.setApiUser(codeOwner2.id());
    recommend(changeId);

    // create another patch set so that the second approval becomes an approval on a previous patch
    // set
    amendChange(
            changeId,
            "Change Adding A File",
            ImmutableMap.of(
                JgitPath.of(path1).get(),
                "newer file content",
                JgitPath.of(path2).get(),
                "newer file content"))
        .assertOkStatus();

    // Verify that both paths approved now.
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path1,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved on patch set 1 by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(codeOwner1.id()))),
            FileCodeOwnerStatus.addition(
                path2,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved on patch set 2 by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(codeOwner2.id()))));
  }

  @Test
  public void notApproved_byPreviousApprovalThatHasBeenDeleted() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "CodeOwner", /* displayName= */ null);
    setAsRootCodeOwners(codeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // code owner approve
    requestScopeOperations.setApiUser(codeOwner.id());
    recommend(changeId);

    // delete the approval
    adminRestSession
        .delete(
            "/changes/"
                + changeId
                + "/reviewers/"
                + codeOwner.id().toString()
                + "/votes/Code-Review")
        .assertNoContent();

    // create a second patch set so that the deleted approval becomes an approval on a previous
    // patch set
    amendChange(changeId, "Change Adding A File", JgitPath.of(path).get(), "new file content")
        .assertOkStatus();

    // Verify that the file is not approved. The expected status is PENDING since the code owner is
    // a reviewer now.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.PENDING,
                String.format(
                    "reviewer %s is a code owner",
                    AccountTemplateUtil.getAccountTemplate(codeOwner.id()))));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "Code-Review+2")
  public void notApproved_byPreviousApprovalThatHasBeenDowngraded() throws Exception {
    // Allow all users to vote with Code-Review+2.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            TestProjectUpdate.allowLabel("Code-Review")
                .range(0, 2)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .build())
        .update();

    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "CodeOwner", /* displayName= */ null);
    setAsRootCodeOwners(codeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // code owner approve
    requestScopeOperations.setApiUser(codeOwner.id());
    approve(changeId);

    // create a second patch set so that the deleted approval becomes an approval on a previous
    // patch set
    amendChange(changeId, "Change Adding A File", JgitPath.of(path).get(), "new file content")
        .assertOkStatus();

    // downgrade approval
    recommend(changeId);

    // Verify that the file is not approved. The expected status is PENDING since the code owner is
    // a reviewer now.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.PENDING,
                String.format(
                    "reviewer %s is a code owner",
                    AccountTemplateUtil.getAccountTemplate(codeOwner.id()))));

    // create another patch set so that the downgraded approval becomes an approval on a previous
    // patch set
    amendChange(changeId, "Change Adding A File", JgitPath.of(path).get(), "newer file content")
        .assertOkStatus();

    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.PENDING,
                String.format(
                    "reviewer %s is a code owner",
                    AccountTemplateUtil.getAccountTemplate(codeOwner.id()))));
  }

  @Test
  public void approved_reapprovalTrumpsPreviousApproval() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "CodeOwner", /* displayName= */ null);
    setAsRootCodeOwners(codeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // code owner approve
    requestScopeOperations.setApiUser(codeOwner.id());
    recommend(changeId);

    // create a second patch set so that the deleted approval becomes an approval on a previous
    // patch set
    amendChange(changeId, "Change Adding A File", JgitPath.of(path).get(), "new file content")
        .assertOkStatus();

    // re-approve
    recommend(changeId);

    // Verify that the file is approved by the current approval.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(codeOwner.id()))));

    // create another patch set so that the re-approval becomes an approval on a previous patch set
    amendChange(changeId, "Change Adding A File", JgitPath.of(path).get(), "newer file content")
        .assertOkStatus();

    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved on patch set 2 by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(codeOwner.id()))));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void approved_implicitApprovalTrumpsPreviousApproval() throws Exception {
    TestAccount implicitCodeOwner = admin; // the changes is created by the admit user
    setAsRootCodeOwners(implicitCodeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // code owner approve
    requestScopeOperations.setApiUser(implicitCodeOwner.id());
    recommend(changeId);

    // create a second patch set so that the deleted approval becomes an approval on a previous
    // patch set
    amendChange(changeId, "Change Adding A File", JgitPath.of(path).get(), "new file content")
        .assertOkStatus();

    // Verify that the file is approved by the current implicit approval.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "implicitly approved by the patch set uploader %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(implicitCodeOwner.id()))));
  }

  @Test
  public void notApproved_fileThatIsNotPresentInApprovedPatchSetIsNotCoveredByTheApproval()
      throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "CodeOwner", /* displayName= */ null);
    setAsRootCodeOwners(codeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // code owner approve
    requestScopeOperations.setApiUser(codeOwner.id());
    recommend(changeId);

    // create a second patch set that adds a new file
    Path path2 = Paths.get("/foo/abc.xyz");
    amendChange(
            changeId,
            "Change Adding A File",
            ImmutableMap.of(
                JgitPath.of(path).get(),
                "new file content",
                JgitPath.of(path2).get(),
                "file content"))
        .assertOkStatus();

    // Verify that the new file is not approved. The expected status is PENDING since the code owner
    // is a reviewer.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved on patch set 1 by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(codeOwner.id()))),
            FileCodeOwnerStatus.addition(
                path2,
                CodeOwnerStatus.PENDING,
                String.format(
                    "reviewer %s is a code owner",
                    AccountTemplateUtil.getAccountTemplate(codeOwner.id()))));

    // re-approve to cover all files
    recommend(changeId);

    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(codeOwner.id()))),
            FileCodeOwnerStatus.addition(
                path2,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(codeOwner.id()))));
  }

  @Test
  public void approved_byStickyApprovalOnPreviousPatchSet_everyoneIsCodeOwner() throws Exception {
    // Create a code owner config file that makes everyone a code owner.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail("*")
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // code owner approve
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    // create a second patch set so that the approval becomes an approval on a previous patch set
    amendChange(changeId, "Change Adding A File", JgitPath.of(path).get(), "new file content")
        .assertOkStatus();

    // Verify that the file is approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved on patch set 1 by %s who is a code owner (all users are code owners)",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));
  }

  @Test
  public void approved_byStickyApprovalOfDefaultCodeOnPreviousPatchSet() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "CodeOwner", /* displayName= */ null);
    setAsDefaultCodeOwners(codeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // code owner approve
    requestScopeOperations.setApiUser(codeOwner.id());
    recommend(changeId);

    // create a second patch set so that the approval becomes an approval on a previous patch set
    amendChange(changeId, "Change Adding A File", JgitPath.of(path).get(), "new file content")
        .assertOkStatus();

    // Verify that the file is approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved on patch set 1 by %s who is a default code owner",
                    AccountTemplateUtil.getAccountTemplate(codeOwner.id()))));
  }

  @Test
  public void approved_byStickyApprovalOfDefaultCodeOnPreviousPatchSet_everyoneIsDefaultCodeOwner()
      throws Exception {
    // Create a code owner config file that makes everyone a default code owner.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(RefNames.REFS_CONFIG)
        .folderPath("/")
        .addCodeOwnerEmail("*")
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // code owner approve
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    // create a second patch set so that the approval becomes an approval on a previous patch set
    amendChange(changeId, "Change Adding A File", JgitPath.of(path).get(), "new file content")
        .assertOkStatus();

    // Verify that the file is approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved on patch set 1 by %s who is a default code owner"
                        + " (all users are default code owners)",
                    AccountTemplateUtil.getAccountTemplate(user.id()))));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "bot@example.com")
  public void approved_byStickyApprovalOfGlobalCodeOnPreviousPatchSet() throws Exception {
    // Create a bot user that is a global code owner.
    TestAccount bot =
        accountCreator.create("bot", "bot@example.com", "Bot", /* displayName= */ null);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // code owner approve
    requestScopeOperations.setApiUser(bot.id());
    recommend(changeId);

    // create a second patch set so that the approval becomes an approval on a previous patch set
    amendChange(changeId, "Change Adding A File", JgitPath.of(path).get(), "new file content")
        .assertOkStatus();

    // Verify that the file is approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved on patch set 1 by %s who is a global code owner",
                    AccountTemplateUtil.getAccountTemplate(bot.id()))));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  public void approved_byStickyApprovalOfGlobalCodeOnPreviousPatchSet_everyoneIsGlobalCodeOwner()
      throws Exception {
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // code owner approve
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    // create a second patch set so that the approval becomes an approval on a previous patch set
    amendChange(changeId, "Change Adding A File", JgitPath.of(path).get(), "new file content")
        .assertOkStatus();

    // Verify that the file is approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved on patch set 1 by %s who is a global code owner"
                        + " (all users are global code owners)",
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

  private PushOneCommit.Result amendChange(
      String changeId, String subject, Map<String, String> files) throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo, subject, files, changeId);
    return push.to("refs/for/master");
  }
}
