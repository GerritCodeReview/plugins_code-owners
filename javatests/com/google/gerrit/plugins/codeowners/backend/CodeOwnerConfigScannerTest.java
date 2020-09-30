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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.plugins.codeowners.JgitPath;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.config.StatusConfig;
import java.nio.file.Paths;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

/** Tests for {@link CodeOwnerConfigScanner}. */
public class CodeOwnerConfigScannerTest extends AbstractCodeOwnersTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Mock private CodeOwnerConfigVisitor visitor;
  @Mock private InvalidCodeOwnerConfigCallback invalidCodeOwnerConfigCallback;

  private CodeOwnerConfigOperations codeOwnerConfigOperations;
  private CodeOwnerConfigScanner codeOwnerConfigScanner;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerConfigOperations =
        plugin.getSysInjector().getInstance(CodeOwnerConfigOperations.class);
    codeOwnerConfigScanner = plugin.getSysInjector().getInstance(CodeOwnerConfigScanner.class);
  }

  @Test
  public void cannotVisitCodeOwnerConfigsForNullBranch() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> codeOwnerConfigScanner.visit(null, visitor, invalidCodeOwnerConfigCallback));
    assertThat(npe).hasMessageThat().isEqualTo("branchNameKey");
  }

  @Test
  public void cannotVisitCodeOwnerConfigsWithNullVisitor() throws Exception {
    BranchNameKey branchNameKey = BranchNameKey.create(project, "master");
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwnerConfigScanner.visit(branchNameKey, null, invalidCodeOwnerConfigCallback));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfigVisitor");
  }

  @Test
  public void cannotVisitCodeOwnerConfigsWithNullCallback() throws Exception {
    BranchNameKey branchNameKey = BranchNameKey.create(project, "master");
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> codeOwnerConfigScanner.visit(branchNameKey, visitor, null));
    assertThat(npe).hasMessageThat().isEqualTo("invalidCodeOwnerConfigCallback");
  }

  @Test
  public void cannotVisitCodeOwnerConfigsForNonExistingBranch() throws Exception {
    BranchNameKey branchNameKey = BranchNameKey.create(project, "non-existing");
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                codeOwnerConfigScanner.visit(
                    branchNameKey, visitor, invalidCodeOwnerConfigCallback));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "branch %s of project %s not found", branchNameKey.branch(), project.get()));
  }

  @Test
  public void visitorNotInvokedIfNoCodeOwnerConfigFilesExists() throws Exception {
    visit();
    verifyZeroInteractions(visitor);
    verifyZeroInteractions(invalidCodeOwnerConfigCallback);
  }

  @Test
  public void visitorNotInvokedForNonCodeOwnerConfigFiles() throws Exception {
    // Create some non code owner config files.
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      Ref ref = testRepo.getRepository().exactRef("refs/heads/master");
      RevCommit head = testRepo.getRevWalk().parseCommit(ref.getObjectId());
      testRepo.update(
          "refs/heads/master",
          testRepo
              .commit()
              .parent(head)
              .message("Add some non code owner config files")
              .add("owners.txt", "some content")
              .add("owners", "some content")
              .add("foo/bar/owners.txt", "some content")
              .add("foo/bar/owners", "some content"));
    }

    visit();
    verifyZeroInteractions(visitor);
    verifyZeroInteractions(invalidCodeOwnerConfigCallback);
  }

  @Test
  public void visitorNotInvokedForInvalidCodeOwnerConfigFiles() throws Exception {
    createInvalidCodeOwnerConfig("/OWNERS");

    visit();
    verifyZeroInteractions(visitor);

    // Verify that we received the expected callbacks for the invalid code onwer config.
    Mockito.verify(invalidCodeOwnerConfigCallback)
        .onInvalidCodeOwnerConfig(eq(Paths.get("/OWNERS")), any(ConfigInvalidException.class));
    verifyNoMoreInteractions(invalidCodeOwnerConfigCallback);
  }

  @Test
  public void visitorInvokedForValidCodeOwnerConfigFilesEvenIfInvalidCodeOwnerConfigFileExist()
      throws Exception {
    createInvalidCodeOwnerConfig("/OWNERS");

    // Create a valid code owner config file.
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(admin.email())
            .create();

    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true);
    visit();

    // Verify that we received the expected callbacks.
    Mockito.verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).get());
    verifyNoMoreInteractions(visitor);

    // Verify that we received the expected callbacks for the invalid code onwer config.
    Mockito.verify(invalidCodeOwnerConfigCallback)
        .onInvalidCodeOwnerConfig(eq(Paths.get("/OWNERS")), any(ConfigInvalidException.class));
    verifyNoMoreInteractions(invalidCodeOwnerConfigCallback);
  }

  @Test
  public void visitorInvokedForCodeOwnerConfigFileAtRoot() throws Exception {
    testVisitorInvoked("/", "OWNERS");
  }

  @Test
  public void visitorInvokedForCodeOwnerConfigFileWithPostFixAtRoot() throws Exception {
    testVisitorInvoked("/", "OWNERS_post_fix");
  }

  @Test
  public void visitorInvokedForCodeOwnerConfigFileWithPreFixAtRoot() throws Exception {
    testVisitorInvoked("/", "pre_fix_OWNERS");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fileExtension", value = "foo")
  public void visitorInvokedForCodeOwnerConfigFileWithExtensionAtRoot() throws Exception {
    testVisitorInvoked("/", "OWNERS.foo");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fileExtension", value = "foo")
  public void visitorInvokedForCodeOwnerConfigFileWithPostFixAndExtensionAtRoot() throws Exception {
    testVisitorInvoked("/", "OWNERS_post_fix.foo");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fileExtension", value = "foo")
  public void visitorInvokedForCodeOwnerConfigFileWithPreFixAndExtensionAtRoot() throws Exception {
    testVisitorInvoked("/", "pre_fix_OWNERS.foo");
  }

  @Test
  public void visitorInvokedForCodeOwnerConfigFileInSubfolder() throws Exception {
    testVisitorInvoked("/foo/bar/", "OWNERS");
  }

  @Test
  public void visitorInvokedForCodeOwnerConfigFileWithPostFixInSubfolder() throws Exception {
    testVisitorInvoked("/foo/bar/", "OWNERS_post_fix");
  }

  @Test
  public void visitorInvokedForCodeOwnerConfigFileWithPreFixInSubfolder() throws Exception {
    testVisitorInvoked("/foo/bar/", "pre_fix_OWNERS");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fileExtension", value = "foo")
  public void visitorInvokedForCodeOwnerConfigFileWithExtensionInSubfolder() throws Exception {
    testVisitorInvoked("/foo/bar/", "OWNERS.foo");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fileExtension", value = "foo")
  public void visitorInvokedForCodeOwnerConfigFileWithPostFixAndExtensionInSubfolder()
      throws Exception {
    testVisitorInvoked("/foo/bar/", "OWNERS_post_fix.foo");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fileExtension", value = "foo")
  public void visitorInvokedForCodeOwnerConfigFileWithPreFixAndExtensionInSubfolder()
      throws Exception {
    testVisitorInvoked("/foo/bar/", "pre_fix_OWNERS.foo");
  }

  private void testVisitorInvoked(String folderPath, String fileName) throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath(folderPath)
            .fileName(fileName)
            .addCodeOwnerEmail(admin.email())
            .create();

    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true);
    visit();

    // Verify that we received the expected callbacks.
    Mockito.verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).get());
    verifyNoMoreInteractions(visitor);

    verifyZeroInteractions(invalidCodeOwnerConfigCallback);
  }

  @Test
  public void visitorIsInvokedForAllCodeOwnerConfigFiles() throws Exception {
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key fooCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key fooBarCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(admin.email())
            .create();

    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true);
    visit();

    // Verify that we received the expected callbacks.
    InOrder orderVerifier = Mockito.inOrder(visitor);
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(rootCodeOwnerConfigKey).get());
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(fooCodeOwnerConfigKey).get());
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(fooBarCodeOwnerConfigKey).get());
    verifyNoMoreInteractions(visitor);

    verifyZeroInteractions(invalidCodeOwnerConfigCallback);
  }

  @Test
  public void visitorCanStopTheIterationOverCodeOwnerConfigsByReturningFalse() throws Exception {
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key fooCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(admin.email())
            .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .fileName("OWNERS")
        .addCodeOwnerEmail(admin.email())
        .create();

    // Return true for the first time the visitor is invoked, and false for all further invocations.
    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true).thenReturn(false);
    visit();

    // Verify that we received the callbacks in the right order, starting from the folder of the
    // given path up to the root folder. We expect only 2 callbacks, since the visitor returns false
    // for the second invocation.
    InOrder orderVerifier = Mockito.inOrder(visitor);
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(rootCodeOwnerConfigKey).get());
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(fooCodeOwnerConfigKey).get());
    verifyNoMoreInteractions(visitor);

    verifyZeroInteractions(invalidCodeOwnerConfigCallback);
  }

  @Test
  public void containsNoCodeOwnerConfigFile() throws Exception {
    assertThat(
            codeOwnerConfigScanner.containsAnyCodeOwnerConfigFile(
                BranchNameKey.create(project, "master")))
        .isFalse();
  }

  @Test
  public void containsACodeOwnerConfigFile() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .fileName("OWNERS")
        .addCodeOwnerEmail(admin.email())
        .create();

    assertThat(
            codeOwnerConfigScanner.containsAnyCodeOwnerConfigFile(
                BranchNameKey.create(project, "master")))
        .isTrue();
  }

  @Test
  public void containsACodeOwnerConfigFile_invalidCodeOwnerConfigFileExists() throws Exception {
    createInvalidCodeOwnerConfig("/OWNERS");

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .fileName("OWNERS")
        .addCodeOwnerEmail(admin.email())
        .create();

    assertThat(
            codeOwnerConfigScanner.containsAnyCodeOwnerConfigFile(
                BranchNameKey.create(project, "master")))
        .isTrue();
  }

  private void visit() {
    codeOwnerConfigScanner.visit(
        BranchNameKey.create(project, "master"), visitor, invalidCodeOwnerConfigCallback);
  }

  private void createInvalidCodeOwnerConfig(String path) throws Exception {
    disableCodeOwnersForProject(project);
    String changeId =
        createChange("Add invalid code owners file", JgitPath.of(path).get(), "INVALID")
            .getChangeId();
    approve(changeId);
    gApi.changes().id(changeId).current().submit();
    setCodeOwnersConfig(project, null, StatusConfig.KEY_DISABLED, "false");
  }
}
