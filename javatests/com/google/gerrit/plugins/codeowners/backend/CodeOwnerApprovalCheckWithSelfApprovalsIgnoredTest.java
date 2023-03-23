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

import static com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusSubject.assertThatCollection;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.config.OverrideApprovalConfig;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.util.AccountTemplateUtil;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public class CodeOwnerApprovalCheckWithSelfApprovalsIgnoredTest extends AbstractCodeOwnersTest {
  @Inject private ChangeNotes.Factory changeNotesFactory;
  @Inject private RequestScopeOperations requestScopeOperations;

  private CodeOwnerApprovalCheck codeOwnerApprovalCheck;

  /** Returns a {@code gerrit.config} that configures all users as fallback code owners. */
  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = AbstractCodeOwnersTest.defaultConfig();
    cfg.setString(
        "plugin", "code-owners", OverrideApprovalConfig.KEY_OVERRIDE_APPROVAL, "Owners-Override+1");
    return cfg;
  }

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerApprovalCheck = plugin.getSysInjector().getInstance(CodeOwnerApprovalCheck.class);
  }

  @Before
  public void defineOwnersOverrideLabel() throws Exception {
    createOwnersOverrideLabel();
  }

  @Before
  public void disableSelfApprovals() throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.ignoreSelfApproval = true;
    gApi.projects().name(allProjects.get()).label("Code-Review").update(input);
    gApi.projects().name(project.get()).label("Owners-Override").update(input);
  }

  @Test
  public void notApprovedByUploaderWhoIsChangeOwner() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "CodeOwner", /* displayName= */ null);
    setAsRootCodeOwners(codeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(codeOwner, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a self Code-Review+1 (= code owner approval).
    requestScopeOperations.setApiUser(codeOwner.id());
    recommend(changeId);

    // Verify that the file is not approved (since self approvals are ignored).
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void approvedByChangeOwnerThatIsNotUploader() throws Exception {
    TestAccount changeOwner =
        accountCreator.create(
            "changeOwner", "changeOwner@example.com", "ChangeOwner", /* displayName= */ null);
    setAsRootCodeOwners(changeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(changeOwner, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Upload another patch set by another user.
    amendChange(admin, changeId);

    // Verify that the file is not approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a Code-Review+1 (= code owner approval) from the change owner.
    requestScopeOperations.setApiUser(changeOwner.id());
    recommend(changeId);

    // Verify that the file is approved now (since the change owner is not the uploader of the
    // current patch set).
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(changeOwner.id()))));
  }

  @Test
  public void notApprovedByUploader() throws Exception {
    TestAccount changeOwner =
        accountCreator.create(
            "changeOwner", "changeOwner@example.com", "ChangeOwner", /* displayName= */ null);

    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "CodeOwner", /* displayName= */ null);
    setAsRootCodeOwners(codeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(changeOwner, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Upload another patch set by a code owner.
    amendChange(codeOwner, changeId);

    // Verify that the file is not approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add the code owner as reviewer.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Verify that the file is not pending (the code owner is the uploader of the current patch set
    // and self approvals are ignored).
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a Code-Review+1 (= code owner approval) by the code owner.
    requestScopeOperations.setApiUser(codeOwner.id());
    recommend(changeId);

    // Verify that the file is not approved (since the code owner is the uploader of the current
    // patch set and self approvals are ignored).
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void notImplicitlyApproved() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "CodeOwner", /* displayName= */ null);
    setAsRootCodeOwners(codeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(codeOwner, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
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
    setAsRootCodeOwners(codeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(changeOwner, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Upload another patch set by a code owner.
    amendChange(codeOwner, changeId);

    // Verify that the file is not approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "forced")
  public void implicitlyApproved() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "CodeOwner", /* displayName= */ null);
    setAsRootCodeOwners(codeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(codeOwner, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "implicitly approved by the patch set uploader %s who is a code owner",
                    AccountTemplateUtil.getAccountTemplate(codeOwner.id()))));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "forced")
  public void notImplicitlyApprovedByUploader_forcedImplicitApprovals() throws Exception {
    TestAccount changeOwner =
        accountCreator.create(
            "changeOwner", "changeOwner@example.com", "ChangeOwner", /* displayName= */ null);

    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "CodeOwner", /* displayName= */ null);
    setAsRootCodeOwners(codeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(changeOwner, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Upload another patch set by a code owner.
    amendChange(codeOwner, changeId);

    // Verify that the file is not approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void notOverriddenByUploaderWhoIsChangeOwner() throws Exception {
    TestAccount changeOwner =
        accountCreator.create(
            "changeOwner", "changeOwner@example.com", "ChangeOwner", /* displayName= */ null);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(changeOwner, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Verify that the file is not approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add an override approval.
    requestScopeOperations.setApiUser(changeOwner.id());
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // Verify that the file is not approved (since self approvals on the override label are
    // ignored).
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void overridenByChangeOwnerThatIsNotUploader() throws Exception {
    TestAccount changeOwner =
        accountCreator.create(
            "changeOwner", "changeOwner@example.com", "ChangeOwner", /* displayName= */ null);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(changeOwner, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Upload another patch set by another user.
    amendChange(admin, changeId);

    // Verify that the file is not approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add an override approval from the change owner.
    requestScopeOperations.setApiUser(changeOwner.id());
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // Verify that the file is approved now (since the change owner is not the uploader of the
    // current patch set and hence the override counts).
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "override approval Owners-Override+1 by %s is present",
                    AccountTemplateUtil.getAccountTemplate(changeOwner.id()))));
  }

  @Test
  public void notOverridenByUploader() throws Exception {
    TestAccount changeOwner =
        accountCreator.create(
            "changeOwner", "changeOwner@example.com", "ChangeOwner", /* displayName= */ null);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange(changeOwner, "Change Adding A File", JgitPath.of(path).get(), "file content")
            .getChangeId();

    // Upload another patch set by another user.
    amendChange(admin, changeId);

    // Verify that the file is not approved.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add an override approval.
    gApi.changes().id(changeId).current().review(new ReviewInput().label("Owners-Override", 1));

    // Verify that the file is not approved (since the override from the uploader is ignored).
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
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
