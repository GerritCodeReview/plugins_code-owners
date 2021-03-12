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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.TestPathExpressions;
import com.google.gerrit.plugins.codeowners.backend.config.InvalidPluginConfigurationException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.restapi.project.DeleteRef;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.function.Consumer;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

/** Tests for {@link CodeOwnerConfigHierarchy}. */
public class CodeOwnerConfigHierarchyTest extends AbstractCodeOwnersTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Mock private CodeOwnerConfigVisitor visitor;
  @Mock private Consumer<CodeOwnerConfig.Key> parentCodeOwnersIgnoredCallback;

  @Inject private ProjectOperations projectOperations;
  @Inject private DeleteRef deleteRef;

  private CodeOwnerConfigOperations codeOwnerConfigOperations;
  private CodeOwnerConfigHierarchy codeOwnerConfigHierarchy;
  private TestPathExpressions testPathExpressions;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerConfigOperations =
        plugin.getSysInjector().getInstance(CodeOwnerConfigOperations.class);
    codeOwnerConfigHierarchy = plugin.getSysInjector().getInstance(CodeOwnerConfigHierarchy.class);
    testPathExpressions = plugin.getSysInjector().getInstance(TestPathExpressions.class);
  }

  @Test
  public void cannotVisitCodeOwnerConfigsForNullBranch() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwnerConfigHierarchy.visit(
                    /* branchNameKey= */ null,
                    getCurrentRevision(BranchNameKey.create(project, "master")),
                    Paths.get("/foo/bar/baz.md"),
                    visitor));
    assertThat(npe).hasMessageThat().isEqualTo("branch");
  }

  @Test
  public void cannotVisitCodeOwnerConfigsForNullRevision() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwnerConfigHierarchy.visit(
                    BranchNameKey.create(project, "master"),
                    /* revision= */ null,
                    Paths.get("/foo/bar/baz.md"),
                    visitor));
    assertThat(npe).hasMessageThat().isEqualTo("revision");
  }

  @Test
  public void cannotVisitCodeOwnerConfigsForNullPath() throws Exception {
    BranchNameKey branchNameKey = BranchNameKey.create(project, "master");
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwnerConfigHierarchy.visit(
                    branchNameKey,
                    getCurrentRevision(branchNameKey),
                    /* absolutePath= */ null,
                    visitor));
    assertThat(npe).hasMessageThat().isEqualTo("absolutePath");
  }

  @Test
  public void cannotVisitCodeOwnerConfigsForRelativePath() throws Exception {
    BranchNameKey branchNameKey = BranchNameKey.create(project, "master");
    String relativePath = "foo/bar/baz.md";
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                codeOwnerConfigHierarchy.visit(
                    branchNameKey,
                    getCurrentRevision(branchNameKey),
                    Paths.get(relativePath),
                    visitor));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("path %s must be absolute", relativePath));
  }

  @Test
  public void cannotVisitCodeOwnerConfigsWithNullVisitor() throws Exception {
    BranchNameKey branchNameKey = BranchNameKey.create(project, "master");
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwnerConfigHierarchy.visit(
                    branchNameKey,
                    getCurrentRevision(branchNameKey),
                    Paths.get("/foo/bar/baz.md"),
                    /* codeOwnerConfigVisitor= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfigVisitor");
  }

  @Test
  public void cannotVisitCodeOwnerConfigsWithNullCallback() throws Exception {
    BranchNameKey branchNameKey = BranchNameKey.create(project, "master");
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwnerConfigHierarchy.visit(
                    branchNameKey,
                    getCurrentRevision(branchNameKey),
                    Paths.get("/foo/bar/baz.md"),
                    visitor,
                    /* parentCodeOwnersIgnoredCallback= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("parentCodeOwnersIgnoredCallback");
  }

  @Test
  public void visitorNotInvokedIfNoCodeOwnerConfigExists() throws Exception {
    visit("master", "/foo/bar/baz.md");
    verifyNoInteractions(visitor);
  }

  @Test
  public void visitorNotInvokedForNonApplyingCodeOwnerConfig() throws Exception {
    String branch = "master";

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(branch)
        .folderPath("/other/")
        .addCodeOwnerEmail(admin.email())
        .create();

    visit(branch, "/foo/bar/baz.md");
    verifyNoInteractions(visitor);
  }

  @Test
  public void visitorInvokedForApplyingCodeOwnerConfigs() throws Exception {
    String branch = "master";

    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key fooCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/foo/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key fooBarCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail(admin.email())
            .create();

    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true);
    visit(branch, "/foo/bar/baz.md");

    // Verify that we received the callbacks in the right order, starting from the folder of the
    // given path up to the root folder.
    InOrder orderVerifier = Mockito.inOrder(visitor);
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(fooBarCodeOwnerConfigKey).get());
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(fooCodeOwnerConfigKey).get());
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(rootCodeOwnerConfigKey).get());
    verifyNoMoreInteractions(visitor);
  }

  @Test
  public void visitorIsNotInvokedForFolderThatHasNoCodeOwnerConfig() throws Exception {
    String branch = "master";

    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key fooBarCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail(admin.email())
            .create();

    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true);
    visit(branch, "/foo/bar/baz.md");

    // Verify that we received the callbacks in the right order, starting from the folder of the
    // given path up to the root folder. There is no callback for the '/foo/' folder as this folder
    // doesn't contain a code owner config file.
    InOrder orderVerifier = Mockito.inOrder(visitor);
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(fooBarCodeOwnerConfigKey).get());
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(rootCodeOwnerConfigKey).get());
    verifyNoMoreInteractions(visitor);
  }

  @Test
  public void visitorInvokedWithCodeOwnerConfigsFromOldRevision() throws Exception {
    String branch = "master";

    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key fooCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/foo/")
            .addCodeOwnerEmail(admin.email())
            .create();

    RevCommit revision1 = projectOperations.project(project).getHead(branch);
    CodeOwnerConfig expectedRootCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(rootCodeOwnerConfigKey).get();
    CodeOwnerConfig expectedFooCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(fooCodeOwnerConfigKey).get();

    // Update one code owner config.
    codeOwnerConfigOperations
        .codeOwnerConfig(fooCodeOwnerConfigKey)
        .forUpdate()
        .ignoreParentCodeOwners()
        .update();

    // Create a new code owner config.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(branch)
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(admin.email())
        .create();

    RevCommit revision2 = projectOperations.project(project).getHead(branch);
    assertThat(revision1).isNotEqualTo(revision2);

    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true);
    codeOwnerConfigHierarchy.visit(
        BranchNameKey.create(project, branch), revision1, Paths.get("/foo/bar/baz.md"), visitor);

    // Verify that we received the callbacks for the code owner configs from the old revison, in
    // the right order, starting from the folder of the given path up to the root folder.
    InOrder orderVerifier = Mockito.inOrder(visitor);
    orderVerifier.verify(visitor).visit(expectedFooCodeOwnerConfig);
    orderVerifier.verify(visitor).visit(expectedRootCodeOwnerConfig);
    verifyNoMoreInteractions(visitor);
  }

  @Test
  public void visitorCanStopTheIterationOverCodeOwnerConfigsByReturningFalse() throws Exception {
    String branch = "master";

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(branch)
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    CodeOwnerConfig.Key fooCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/foo/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key fooBarCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail(admin.email())
            .create();

    // Return true for the first time the visitor is invoked, and false for all further invocations.
    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true).thenReturn(false);
    visit(branch, "/foo/bar/baz.md");

    // Verify that we received the callbacks in the right order, starting from the folder of the
    // given path up to the root folder. We expect only 2 callbacks, since the visitor returns false
    // for the second invocation.
    InOrder orderVerifier = Mockito.inOrder(visitor);
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(fooBarCodeOwnerConfigKey).get());
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(fooCodeOwnerConfigKey).get());
    verifyNoMoreInteractions(visitor);
  }

  @Test
  public void visitorIsNotInvokedForParentCodeOwnerConfigsIfParentCodeOwnersAreIgnored()
      throws Exception {
    String branch = "master";

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(branch)
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(branch)
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    CodeOwnerConfig.Key fooBarCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/foo/bar/")
            .ignoreParentCodeOwners()
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key fooBarBazCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/foo/bar/baz")
            .addCodeOwnerEmail(admin.email())
            .create();

    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true);
    visit(branch, "/foo/bar/baz/README.md");

    // Verify that we received the callbacks in the right order, starting from the folder of the
    // given path up to the root folder. There is no callback for the '/' and '/foo/' folders since
    // the code owner config for the '/foo/bar/' folder defines that parent code owners should be
    // ignored.
    InOrder orderVerifier = Mockito.inOrder(visitor);
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(fooBarBazCodeOwnerConfigKey).get());
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(fooBarCodeOwnerConfigKey).get());
    verifyNoMoreInteractions(visitor);

    verify(parentCodeOwnersIgnoredCallback).accept(fooBarCodeOwnerConfigKey);
    verifyNoMoreInteractions(parentCodeOwnersIgnoredCallback);
  }

  @Test
  public void
      visitorIsNotInvokedForParentCodeOwnerConfigsIfAMatchingCodeOwnerSetIgnoresParentCodeOwners()
          throws Exception {
    String branch = "master";

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(branch)
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(branch)
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    CodeOwnerConfig.Key fooBarCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/foo/bar/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .setIgnoreGlobalAndParentCodeOwners()
                    .addPathExpression(testPathExpressions.matchAllFilesInSubfolder("baz"))
                    .addCodeOwnerEmail(admin.email())
                    .build())
            .create();

    CodeOwnerConfig.Key fooBarBazCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/foo/bar/baz")
            .addCodeOwnerEmail(admin.email())
            .create();

    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true);
    visit(branch, "/foo/bar/baz/README.md");

    // Verify that we received the callbacks in the right order, starting from the folder of the
    // given path up to the root folder. There is no callback for the '/' and '/foo/' folders since
    // the code owner config for the '/foo/bar/' folder contains a matching code owner set that
    // defines that parent code owners should be
    // ignored.
    InOrder orderVerifier = Mockito.inOrder(visitor);
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(fooBarBazCodeOwnerConfigKey).get());
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(fooBarCodeOwnerConfigKey).get());
    verifyNoMoreInteractions(visitor);

    verify(parentCodeOwnersIgnoredCallback).accept(fooBarCodeOwnerConfigKey);
    verifyNoMoreInteractions(parentCodeOwnersIgnoredCallback);
  }

  @Test
  public void
      visitorIsInvokedForParentCodeOwnerConfigsIfANonMatchingCodeOwnerSetIgnoresParentCodeOwners()
          throws Exception {
    String branch = "master";

    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key fooCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/foo/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key fooBarCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/foo/bar/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .setIgnoreGlobalAndParentCodeOwners()
                    .addPathExpression(testPathExpressions.matchFileType("txt"))
                    .addCodeOwnerEmail(admin.email())
                    .build())
            .create();

    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true);
    visit(branch, "/foo/bar/baz.md");

    // Verify that we received the callbacks in the right order, starting from the folder of the
    // given path up to the root folder.
    InOrder orderVerifier = Mockito.inOrder(visitor);
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(fooBarCodeOwnerConfigKey).get());
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(fooCodeOwnerConfigKey).get());
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(rootCodeOwnerConfigKey).get());
    verifyNoMoreInteractions(visitor);

    verifyNoInteractions(parentCodeOwnersIgnoredCallback);
  }

  @Test
  public void visitorInvokedForCodeOwnerConfigInRefsMetaConfig() throws Exception {
    CodeOwnerConfig.Key metaCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(RefNames.REFS_CONFIG)
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .create();

    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true);
    visit("master", "/foo/bar/baz.md");
    verify(visitor).visit(codeOwnerConfigOperations.codeOwnerConfig(metaCodeOwnerConfigKey).get());
    verifyNoMoreInteractions(visitor);
  }

  @Test
  public void visitorInvokedForCodeOwnerConfigInRefsMetaConfigIfItDoesntApply() throws Exception {
    CodeOwnerConfig.Key metaCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(RefNames.REFS_CONFIG)
            .folderPath("/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression(testPathExpressions.matchAllFilesInSubfolder("other"))
                    .addCodeOwnerEmail(admin.email())
                    .build())
            .create();

    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true);
    visit("master", "/foo/bar/baz.md");
    verify(visitor).visit(codeOwnerConfigOperations.codeOwnerConfig(metaCodeOwnerConfigKey).get());
    verifyNoMoreInteractions(visitor);
  }

  @Test
  public void visitorInvokedForCodeOwnerConfigInRefsMetaConfigWithMatchingPathExpression()
      throws Exception {
    CodeOwnerConfig.Key metaCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(RefNames.REFS_CONFIG)
            .folderPath("/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression(testPathExpressions.matchAllFilesInSubfolder("foo"))
                    .addCodeOwnerEmail(admin.email())
                    .build())
            .create();

    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true);
    visit("master", "/foo/bar/baz.md");
    verify(visitor).visit(codeOwnerConfigOperations.codeOwnerConfig(metaCodeOwnerConfigKey).get());
    verifyNoMoreInteractions(visitor);
  }

  @Test
  public void visitorForCodeOwnerConfigInRefsMetaConfigInvokedLast() throws Exception {
    CodeOwnerConfig.Key metaCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(RefNames.REFS_CONFIG)
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression(testPathExpressions.matchAllFilesInSubfolder("other"))
                    .addCodeOwnerEmail(admin.email())
                    .build())
            .create();

    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true);
    visit("master", "/foo/bar/baz.md");

    InOrder orderVerifier = Mockito.inOrder(visitor);
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(rootCodeOwnerConfigKey).get());
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(metaCodeOwnerConfigKey).get());
    verifyNoMoreInteractions(visitor);
  }

  @Test
  public void
      visitorNotInvokedForCodeOwnerConfigInRefsMetaConfigIfRootCodeOwnerConfigIgnoresParent()
          throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(RefNames.REFS_CONFIG)
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .ignoreParentCodeOwners()
            .addCodeOwnerEmail(admin.email())
            .create();

    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true);
    visit("master", "/foo/bar/baz.md");
    verify(visitor).visit(codeOwnerConfigOperations.codeOwnerConfig(rootCodeOwnerConfigKey).get());
    verifyNoMoreInteractions(visitor);

    verify(parentCodeOwnersIgnoredCallback).accept(rootCodeOwnerConfigKey);
    verifyNoMoreInteractions(parentCodeOwnersIgnoredCallback);
  }

  @Test
  public void visitorCanStopTheIterationAtRoot() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(RefNames.REFS_CONFIG)
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .ignoreParentCodeOwners()
            .addCodeOwnerEmail(admin.email())
            .create();

    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(false);
    visit("master", "/foo/bar/baz.md");
    verify(visitor).visit(codeOwnerConfigOperations.codeOwnerConfig(rootCodeOwnerConfigKey).get());
    verifyNoMoreInteractions(visitor);
  }

  @Test
  public void
      visitorIsOnlyInvokedOnceForDefaultCodeOnwerConfigFileIfConfigsInRefsMetaConxfigAreIterated()
          throws Exception {
    CodeOwnerConfig.Key metaCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(RefNames.REFS_CONFIG)
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .create();

    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true);
    visit(RefNames.REFS_CONFIG, "/foo/bar/baz.md");

    // Verify that we received the callback for the code owner config only once.
    verify(visitor).visit(codeOwnerConfigOperations.codeOwnerConfig(metaCodeOwnerConfigKey).get());

    verifyNoMoreInteractions(visitor);
  }

  @Test
  public void refsMetaConfigBranchIsMissing() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    deleteRef.deleteSingleRef(projectState, RefNames.REFS_CONFIG);

    // Visit '/foo/bar/baz.md' in master. This would callback for these OWNERS files if they
    // existed:
    // 1. '/foo/bar/OWNERS' in master
    // 2. '/foo/OWNERS' in master
    // 3. '/OWNERS' in master
    // 4. '/OWNERS' in refs/meta/config (this is where the default code owners are loaded from)
    // This test is making sure that trying to load 4. doesn't fail if refs/meta/config doesn't
    // exist.
    visit("master", "/foo/bar/baz.md");
    verifyNoInteractions(visitor);
  }

  private void visit(String branchName, String path)
      throws InvalidPluginConfigurationException, IOException {
    BranchNameKey branchNameKey = BranchNameKey.create(project, branchName);
    codeOwnerConfigHierarchy.visit(
        branchNameKey,
        getCurrentRevision(branchNameKey),
        Paths.get(path),
        visitor,
        parentCodeOwnersIgnoredCallback);
  }

  private ObjectId getCurrentRevision(BranchNameKey branchNameKey) throws IOException {
    try (Repository repository = repoManager.openRepository(branchNameKey.project());
        RevWalk rw = new RevWalk(repository)) {
      Ref ref = repository.exactRef(branchNameKey.branch());
      checkNotNull(
          ref,
          "branch %s in repository %s not found",
          branchNameKey.branch(),
          branchNameKey.project().get());
      return rw.parseCommit(ref.getObjectId());
    }
  }
}
