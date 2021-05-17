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
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerApprovalCheck} with ALL_USERS as fallback code owners. */
public class CodeOwnerApprovalCheckWithAllUsersAsFallbackCodeOwnersTest
    extends AbstractCodeOwnersTest {
  @Inject private ChangeNotes.Factory changeNotesFactory;
  @Inject private RequestScopeOperations requestScopeOperations;

  private CodeOwnerApprovalCheck codeOwnerApprovalCheck;
  private CodeOwnerConfigOperations codeOwnerConfigOperations;

  /** Returns a {@code gerrit.config} that configures all users as fallback code owners. */
  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setEnum(
        "plugin",
        "code-owners",
        GeneralConfig.KEY_FALLBACK_CODE_OWNERS,
        FallbackCodeOwners.ALL_USERS);
    return cfg;
  }

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerApprovalCheck = plugin.getSysInjector().getInstance(CodeOwnerApprovalCheck.class);
    codeOwnerConfigOperations =
        plugin.getSysInjector().getInstance(CodeOwnerConfigOperations.class);
  }

  @Test
  public void notApprovedByFallbackCodeOwnerIfCodeOwnersDefined() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "CodeOwner", /* displayName= */ null);
    setAsRootCodeOwners(codeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file is not approved yet.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a fallback code owner as reviewer.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Verify that the file is not approved (fallback code owner doesn't apply since a code owner is
    // defined).
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a Code-Review+1 (= code owner approval) from a fallback code owner.
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    // Verify that the file is not approved (fallback code owner doesn't apply since a code owner is
    // defined).
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void notImplicitlyApprovedByFallbackCodeOwnerIfCodeOwnersDefined() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "CodeOwner", /* displayName= */ null);
    setAsRootCodeOwners(codeOwner);

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file is not approved yet.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void notApprovedByFallbackCodeOwnerIfNonResolvableCodeOwnersDefined() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail("non-resolvable-code-owner@example.com")
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file is not approved yet.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a fallback code owner as reviewer.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Verify that the file is not approved (fallback code owner doesn't apply since a code owner is
    // defined).
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a Code-Review+1 (= code owner approval) from a fallback code owner.
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    // Verify that the file is not approved (fallback code owner doesn't apply since a code owner is
    // defined).
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void notImplicitlyApprovedByFallbackCodeOwnerIfNonResolvableCodeOwnersDefined()
      throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail("non-resolvable-code-owner@example.com")
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file is not approved yet.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void approvedByFallbackCodeOwner() throws Exception {
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file is not approved yet (the change owner is a code owner, but
    // implicit approvals are disabled).
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a user who is fallback code owner as reviewer.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Verify that the status is pending now .
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.PENDING,
                String.format(
                    "reviewer %s is a fallback code owner (all users are fallback code owners)",
                    ChangeMessagesUtil.getAccountTemplate(user.id()))));

    // Add a Code-Review+1 (= code owner approval) from a fallback code owner.
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    // Verify that the status is approved now
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "approved by %s who is a fallback code owner"
                        + " (all users are fallback code owners)",
                    ChangeMessagesUtil.getAccountTemplate(user.id()))));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void implicitlyApprovedByFallbackCodeOwner() throws Exception {
    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file is approved (the change owner is a code owner and implicit approvals are
    // enabled).
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(
                path,
                CodeOwnerStatus.APPROVED,
                String.format(
                    "implicitly approved by the patch set uploader %s who is a fallback code"
                        + " owner (all users are fallback code owners)",
                    ChangeMessagesUtil.getAccountTemplate(admin.id()))));
  }

  @Test
  public void notApprovedByFallbackCodeOwnerIfParentCodeOwnersIgnored() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .ignoreParentCodeOwners()
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file is not approved yet.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a fallback code owner as reviewer.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Verify that the file is not approved (fallback code owner doesn't apply since a code owner is
    // defined).
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a Code-Review+1 (= code owner approval) from a fallback code owner.
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    // Verify that the file is not approved (fallback code owner doesn't apply since a code owner is
    // defined).
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void notImplicitlyApprovedByFallbackCodeOwnerIfParentCodeOwnersIgnored() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .ignoreParentCodeOwners()
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file is not approved yet.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void notApprovedByFallbackCodeOwnerIfImportCannotBeResolved() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addImport(
            CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/non-existing/OWNERS"))
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file is not approved yet.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a fallback code owner as reviewer.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Verify that the file is not approved (fallback code owner doesn't apply since a code owner is
    // defined).
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a Code-Review+1 (= code owner approval) from a fallback code owner.
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    // Verify that the file is not approved (fallback code owner doesn't apply since a code owner is
    // defined).
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void notImplicitlyApprovedByFallbackCodeOwnerIfImportCannotBeResolved() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addImport(
            CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/non-existing/OWNERS"))
        .create();

    Path path = Paths.get("/foo/bar.baz");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file is not approved yet.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  public void notApprovedByFallbackCodeOwnerIfPerFileImportCannotBeResolved() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression("*.md")
                .addImport(
                    CodeOwnerConfigReference.create(
                        CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/bar/OWNERS"))
                .autoBuild())
        .create();

    Path path = Paths.get("/foo/bar.md");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file is not approved yet.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a fallback code owner as reviewer.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Verify that the file is not approved (fallback code owner doesn't apply since a code owner is
    // defined).
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a Code-Review+1 (= code owner approval) from a fallback code owner.
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);

    // Verify that the file is not approved (fallback code owner doesn't apply since a code owner is
    // defined).
    fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
    assertThatCollection(fileCodeOwnerStatuses)
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void notImplicitlyApprovedByFallbackCodeOwnerIfPerFileImportCannotBeResolved()
      throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression("*.md")
                .addImport(
                    CodeOwnerConfigReference.create(
                        CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/bar/OWNERS"))
                .autoBuild())
        .create();

    Path path = Paths.get("/foo/bar.md");
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    // Verify that the file is not approved yet.
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses = getFileCodeOwnerStatuses(changeId);
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
