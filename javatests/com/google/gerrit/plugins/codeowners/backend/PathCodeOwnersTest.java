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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerSetSubject.hasEmail;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.TestPathExpressions;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link PathCodeOwners}. */
public class PathCodeOwnersTest extends AbstractCodeOwnersTest {
  @Inject private ProjectOperations projectOperations;

  private CodeOwnerConfigOperations codeOwnerConfigOperations;
  private PathCodeOwners.Factory pathCodeOwnersFactory;
  private DynamicMap<CodeOwnerBackend> codeOwnerBackends;
  private Provider<TransientCodeOwnerConfigCache> transientCodeOwnerConfigCacheProvider;
  private TestPathExpressions testPathExpressions;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerConfigOperations =
        plugin.getSysInjector().getInstance(CodeOwnerConfigOperations.class);
    pathCodeOwnersFactory = plugin.getSysInjector().getInstance(PathCodeOwners.Factory.class);
    codeOwnerBackends =
        plugin.getSysInjector().getInstance(new Key<DynamicMap<CodeOwnerBackend>>() {});
    transientCodeOwnerConfigCacheProvider =
        plugin.getSysInjector().getInstance(new Key<Provider<TransientCodeOwnerConfigCache>>() {});
    testPathExpressions = plugin.getSysInjector().getInstance(TestPathExpressions.class);
  }

  @Test
  public void createPathCodeOwnersForCodeOwnerConfig() throws Exception {
    PathCodeOwners pathCodeOwners =
        pathCodeOwnersFactory.createWithoutCache(
            createCodeOwnerBuilder().build(), Path.of("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isNotNull();
  }

  @Test
  public void cannotCreatePathCodeOwnersForNullCodeOwnerConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                pathCodeOwnersFactory.createWithoutCache(
                    /* codeOwnerConfig= */ null, Path.of("/foo/bar/baz.md")));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfig");
  }

  @Test
  public void cannotCreatePathCodeOwnersForCodeOwnerConfigWithNullPath() throws Exception {
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerBuilder().build();
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                pathCodeOwnersFactory.createWithoutCache(
                    codeOwnerConfig, /* absolutePath= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("path");
  }

  @Test
  public void cannotCreatePathCodeOwnersForCodeOwnerConfigWithRelativePath() throws Exception {
    String relativePath = "foo/bar/baz.md";
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerBuilder().build();
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> pathCodeOwnersFactory.createWithoutCache(codeOwnerConfig, Path.of(relativePath)));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("path %s must be absolute", relativePath));
  }

  @Test
  public void createPathCodeOwnersForCodeOwnerConfigKey() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            codeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isPresent();
  }

  @Test
  public void cannotCreatePathCodeOwnersForNullCache() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                pathCodeOwnersFactory.create(
                    /* transientCodeOwnerConfigCache= */ null,
                    CodeOwnerConfig.Key.create(
                        BranchNameKey.create(project, "master"), Path.of("/")),
                    projectOperations.project(project).getHead("master"),
                    Path.of("/foo/bar/baz.md")));
    assertThat(npe).hasMessageThat().isEqualTo("transientCodeOwnerConfigCache");
  }

  @Test
  public void cannotCreatePathCodeOwnersForNullCodeOwnerConfigKey() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                pathCodeOwnersFactory.create(
                    transientCodeOwnerConfigCacheProvider.get(),
                    /* codeOwnerConfigKey= */ null,
                    projectOperations.project(project).getHead("master"),
                    Path.of("/foo/bar/baz.md")));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfigKey");
  }

  @Test
  public void cannotCreatePathCodeOwnersForNullRevision() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                pathCodeOwnersFactory.create(
                    transientCodeOwnerConfigCacheProvider.get(),
                    CodeOwnerConfig.Key.create(
                        BranchNameKey.create(project, "master"), Path.of("/")),
                    /* revision= */ null,
                    Path.of("/foo/bar/baz.md")));
    assertThat(npe).hasMessageThat().isEqualTo("revision");
  }

  @Test
  public void cannotCreatePathCodeOwnersForCodeOwnerConfigKeyWithNullPath() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .create();

    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                pathCodeOwnersFactory.create(
                    transientCodeOwnerConfigCacheProvider.get(),
                    codeOwnerConfigKey,
                    projectOperations.project(project).getHead("master"),
                    /* absolutePath= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("path");
  }

  @Test
  public void cannotCreatePathForCodeOwnerConfigKeyWithRelativePath() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .create();

    String relativePath = "foo/bar/baz.md";
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                pathCodeOwnersFactory.create(
                    transientCodeOwnerConfigCacheProvider.get(),
                    codeOwnerConfigKey,
                    projectOperations.project(project).getHead("master"),
                    Path.of(relativePath)));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("path %s must be absolute", relativePath));
  }

  @Test
  public void getEmptyPathCodeOwners() throws Exception {
    CodeOwnerConfig emptyCodeOwnerConfig = createCodeOwnerBuilder().build();
    PathCodeOwners pathCodeOwners =
        pathCodeOwnersFactory.createWithoutCache(emptyCodeOwnerConfig, Path.of("/foo/bar/baz.md"));
    assertThat(pathCodeOwners.resolveCodeOwnerConfig().getPathCodeOwners()).isEmpty();
    assertThat(pathCodeOwners.resolveCodeOwnerConfig().resolvedImports()).isEmpty();
    assertThat(pathCodeOwners.resolveCodeOwnerConfig().unresolvedImports()).isEmpty();
  }

  @Test
  public void getPathCodeOwnersIfNoPathExpressionsAreUsed() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerBuilder()
            .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email(), user.email()))
            .build();
    PathCodeOwners pathCodeOwners =
        pathCodeOwnersFactory.createWithoutCache(codeOwnerConfig, Path.of("/foo/bar/baz.md"));
    assertThat(pathCodeOwners.resolveCodeOwnerConfig().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = TestCodeOwnerBackend.ID)
  public void getPathCodeOwnersReturnsCodeOwnersFromMatchingCodeOwnerSets() throws Exception {
    PathExpressionMatcher pathExpressionMatcher = mock(PathExpressionMatcher.class);

    // Create a matching code owner set.
    CodeOwnerSet matchingCodeOwnerSet1 =
        CodeOwnerSet.builder().addPathExpression("*.md").addCodeOwnerEmail(admin.email()).build();
    when(pathExpressionMatcher.matches(eq("*.md"), any(Path.class))).thenReturn(true);

    // Create another matching code owner set.
    CodeOwnerSet matchingCodeOwnerSet2 =
        CodeOwnerSet.builder().addPathExpression("baz.*").addCodeOwnerEmail(user.email()).build();
    when(pathExpressionMatcher.matches(eq("baz.*"), any(Path.class))).thenReturn(true);

    // Create a non-matching code owner set.
    CodeOwnerSet nonMatchingCodeOwnerSet =
        CodeOwnerSet.builder()
            .addPathExpression("*.txt")
            .addCodeOwnerEmail("foo@example.com")
            .addCodeOwnerEmail("bar@example.com")
            .build();
    when(pathExpressionMatcher.matches(eq("*.txt"), any(Path.class))).thenReturn(false);

    try (AutoCloseable registration = registerTestBackend(pathExpressionMatcher)) {
      CodeOwnerConfig codeOwnerConfig =
          createCodeOwnerBuilder()
              .addCodeOwnerSet(matchingCodeOwnerSet1)
              .addCodeOwnerSet(matchingCodeOwnerSet2)
              .addCodeOwnerSet(nonMatchingCodeOwnerSet)
              .build();
      PathCodeOwners pathCodeOwners =
          pathCodeOwnersFactory.createWithoutCache(codeOwnerConfig, Path.of("/foo/bar/baz.md"));
      assertThat(pathCodeOwners.resolveCodeOwnerConfig().getPathCodeOwners())
          .comparingElementsUsing(hasEmail())
          .containsExactly(admin.email(), user.email());
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = TestCodeOwnerBackend.ID)
  public void codeOwnerSetsWithPathExpressionsAreIgnoredIfBackendDoesntSupportPathExpressions()
      throws Exception {
    try (AutoCloseable registration = registerTestBackend(/* pathExpressionMatcher= */ null)) {
      CodeOwnerConfig codeOwnerConfig =
          createCodeOwnerBuilder()
              .addCodeOwnerSet(
                  CodeOwnerSet.builder()
                      .addPathExpression("*.md")
                      .addCodeOwnerEmail(admin.email())
                      .build())
              .build();
      PathCodeOwners pathCodeOwners =
          pathCodeOwnersFactory.createWithoutCache(codeOwnerConfig, Path.of("/foo/bar/baz.md"));
      assertThat(pathCodeOwners.resolveCodeOwnerConfig().getPathCodeOwners()).isEmpty();
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = TestCodeOwnerBackend.ID)
  public void
      getPathCodeOwnersOmitsGlobalCodeOwnersIfMatchingPerFileCodeOwnerSetIgnoresParentCodeOwners()
          throws Exception {
    PathExpressionMatcher pathExpressionMatcher = mock(PathExpressionMatcher.class);

    // Create a matching per file code owner set that ignores parent code owners.
    CodeOwnerSet perFileCodeOwnerSet =
        CodeOwnerSet.builder()
            .setIgnoreGlobalAndParentCodeOwners()
            .addPathExpression("*.md")
            .addCodeOwnerEmail(admin.email())
            .build();
    when(pathExpressionMatcher.matches(eq("*.md"), any(Path.class))).thenReturn(true);

    // Create a global code owner set.
    CodeOwnerSet globalCodeOwnerSet =
        CodeOwnerSet.builder().addCodeOwnerEmail(user.email()).build();

    try (AutoCloseable registration = registerTestBackend(pathExpressionMatcher)) {
      CodeOwnerConfig codeOwnerConfig =
          createCodeOwnerBuilder()
              .addCodeOwnerSet(perFileCodeOwnerSet)
              .addCodeOwnerSet(globalCodeOwnerSet)
              .build();
      PathCodeOwners pathCodeOwners =
          pathCodeOwnersFactory.createWithoutCache(codeOwnerConfig, Path.of("/foo/bar/baz.md"));
      assertThat(pathCodeOwners.resolveCodeOwnerConfig().getPathCodeOwners())
          .comparingElementsUsing(hasEmail())
          .containsExactly(admin.email());
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = TestCodeOwnerBackend.ID)
  public void
      getPathCodeOwnersIncludesGlobalCodeOwnersIfMatchingPerFileCodeOwnerSetDoesNotIgnoreParentCodeOwners()
          throws Exception {
    PathExpressionMatcher pathExpressionMatcher = mock(PathExpressionMatcher.class);

    // Create a matching per file code owner set that doesn't ignore parent code owners.
    CodeOwnerSet perFileCodeOwnerSet =
        CodeOwnerSet.builder()
            .setIgnoreGlobalAndParentCodeOwners(false)
            .addPathExpression("*.md")
            .addCodeOwnerEmail(admin.email())
            .build();
    when(pathExpressionMatcher.matches(eq("*.md"), any(Path.class))).thenReturn(true);

    // Create a global code owner set.
    CodeOwnerSet globalCodeOwnerSet =
        CodeOwnerSet.builder().addCodeOwnerEmail(user.email()).build();

    try (AutoCloseable registration = registerTestBackend(pathExpressionMatcher)) {
      CodeOwnerConfig codeOwnerConfig =
          createCodeOwnerBuilder()
              .addCodeOwnerSet(perFileCodeOwnerSet)
              .addCodeOwnerSet(globalCodeOwnerSet)
              .build();
      PathCodeOwners pathCodeOwners =
          pathCodeOwnersFactory.createWithoutCache(codeOwnerConfig, Path.of("/foo/bar/baz.md"));
      assertThat(pathCodeOwners.resolveCodeOwnerConfig().getPathCodeOwners())
          .comparingElementsUsing(hasEmail())
          .containsExactly(admin.email(), user.email());
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = TestCodeOwnerBackend.ID)
  public void
      getPathCodeOwnersIncludesCodeOwnersFromAllMatchingPerFileCodeOwnerSetsIfOneIgnoresParentCodeOwners()
          throws Exception {
    PathExpressionMatcher pathExpressionMatcher = mock(PathExpressionMatcher.class);

    // Create a matching per file code owner set that ignores parent code owners.
    CodeOwnerSet perFileCodeOwnerSet1 =
        CodeOwnerSet.builder()
            .setIgnoreGlobalAndParentCodeOwners()
            .addPathExpression("*.md")
            .addCodeOwnerEmail(admin.email())
            .build();
    when(pathExpressionMatcher.matches(eq("*.md"), any(Path.class))).thenReturn(true);

    // Create another matching per-file code owner set that does not ignore parent code owners.
    CodeOwnerSet perFileCodeOwnerSet2 =
        CodeOwnerSet.builder().addPathExpression("baz.*").addCodeOwnerEmail(user.email()).build();
    when(pathExpressionMatcher.matches(eq("baz.*"), any(Path.class))).thenReturn(true);

    try (AutoCloseable registration = registerTestBackend(pathExpressionMatcher)) {
      CodeOwnerConfig codeOwnerConfig =
          createCodeOwnerBuilder()
              .addCodeOwnerSet(perFileCodeOwnerSet1)
              .addCodeOwnerSet(perFileCodeOwnerSet2)
              .build();
      PathCodeOwners pathCodeOwners =
          pathCodeOwnersFactory.createWithoutCache(codeOwnerConfig, Path.of("/foo/bar/baz.md"));
      assertThat(pathCodeOwners.resolveCodeOwnerConfig().getPathCodeOwners())
          .comparingElementsUsing(hasEmail())
          .containsExactly(admin.email(), user.email());
    }
  }

  @Test
  public void checkThatParentCodeOwnersAreIgnoredIfCodeOwnerConfigIgnoresParentCodeOwners()
      throws Exception {
    PathCodeOwners pathCodeOwners =
        pathCodeOwnersFactory.createWithoutCache(
            createCodeOwnerBuilder().setIgnoreParentCodeOwners().build(),
            Path.of("/foo/bar/baz.md"));
    assertThat(pathCodeOwners.resolveCodeOwnerConfig().ignoreParentCodeOwners()).isTrue();
  }

  @Test
  public void checkThatParentCodeOwnersAreNotIgnoredIfCodeOwnerConfigDoesNotIgnoreParentCodeOwners()
      throws Exception {
    PathCodeOwners pathCodeOwners =
        pathCodeOwnersFactory.createWithoutCache(
            createCodeOwnerBuilder().setIgnoreParentCodeOwners(false).build(),
            Path.of("/foo/bar/baz.md"));
    assertThat(pathCodeOwners.resolveCodeOwnerConfig().ignoreParentCodeOwners()).isFalse();
  }

  @Test
  public void checkThatParentCodeOwnersAreIgnoredIfMatchingCodeOwnerSetIgnoresParentCodeOwners()
      throws Exception {
    PathCodeOwners pathCodeOwners =
        pathCodeOwnersFactory.createWithoutCache(
            createCodeOwnerBuilder()
                .addCodeOwnerSet(
                    CodeOwnerSet.builder()
                        .setIgnoreGlobalAndParentCodeOwners()
                        .addPathExpression("*.md")
                        .build())
                .build(),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners.resolveCodeOwnerConfig().ignoreParentCodeOwners()).isTrue();
  }

  @Test
  public void
      checkThatParentCodeOwnersAreNotIgnoredIfNonMatchingCodeOwnerSetIgnoresParentCodeOwners()
          throws Exception {
    PathCodeOwners pathCodeOwners =
        pathCodeOwnersFactory.createWithoutCache(
            createCodeOwnerBuilder()
                .addCodeOwnerSet(
                    CodeOwnerSet.builder()
                        .setIgnoreGlobalAndParentCodeOwners()
                        .addPathExpression("*.txt")
                        .build())
                .build(),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners.resolveCodeOwnerConfig().ignoreParentCodeOwners()).isFalse();
  }

  @Test
  public void nonResolveableImportIsIgnored() throws Exception {
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        CodeOwnerConfig.Key.create(project, "master", "/non-existing/", "OWNERS");
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig);

    // create importing config with non-resolveable import
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addImport(codeOwnerConfigReference)
            .addCodeOwnerSet(CodeOwnerSet.builder().addCodeOwnerEmail(admin.email()).build())
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global code owner from the importing code owner config, the
    // non-resolveable import is silently ignored
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwnersResult.resolvedImports()).isEmpty();
    assertThat(pathCodeOwnersResult.unresolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createUnresolvedImport(
                importingCodeOwnerConfig,
                keyOfImportedCodeOwnerConfig,
                codeOwnerConfigReference,
                String.format(
                    "code owner config does not exist (revision = %s)",
                    projectOperations
                        .project(keyOfImportedCodeOwnerConfig.project())
                        .getHead(keyOfImportedCodeOwnerConfig.branchNameKey().branch())
                        .name())));
  }

  @Test
  public void importOfNonCodeOwnerConfigFileIsIgnored() throws Exception {
    // create a file that looks like a code owner config file, but which has a name that is not
    // allowed as code owner config file
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .fileName("FOO")
            .addCodeOwnerEmail(user.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig);

    // create config with import of non code owner config file
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addImport(codeOwnerConfigReference)
            .addCodeOwnerSet(CodeOwnerSet.builder().addCodeOwnerEmail(admin.email()).build())
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global code owner from the importing code owner config, the
    // import of the non code owner config file is silently ignored
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwnersResult.resolvedImports()).isEmpty();
    assertThat(pathCodeOwnersResult.unresolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createUnresolvedImport(
                importingCodeOwnerConfig,
                keyOfImportedCodeOwnerConfig,
                codeOwnerConfigReference,
                String.format(
                    "code owner config does not exist (revision = %s)",
                    projectOperations
                        .project(keyOfImportedCodeOwnerConfig.project())
                        .getHead(keyOfImportedCodeOwnerConfig.branchNameKey().branch())
                        .name())));
  }

  @Test
  public void importOfCodeOwnerConfigFileWithFileExtensionIsIgnored() throws Exception {
    // Create a code owner config file with a file extension. This file is only considered as a code
    // owner config file if either the file extension matches the configured file extension (config
    // parameter fileExtension) or file extensions are enabled for code owner config files (config
    // paramater enableCodeOwnerConfigFilesWithFileExtensions). Both is not the case here, hence any
    // import of this file in another code owner config file should get ignored.
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .fileName("OWNERS.foo")
            .addCodeOwnerEmail(user.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig);

    // create the importing config
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addImport(codeOwnerConfigReference)
            .addCodeOwnerSet(CodeOwnerSet.builder().addCodeOwnerEmail(admin.email()).build())
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global code owner from the importing code owner config, the
    // import of the code owner config file with the file extension is silently ignored since it is
    // not considered as a code owner config file
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwnersResult.resolvedImports()).isEmpty();
    assertThat(pathCodeOwnersResult.unresolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createUnresolvedImport(
                importingCodeOwnerConfig,
                keyOfImportedCodeOwnerConfig,
                codeOwnerConfigReference,
                String.format(
                    "code owner config does not exist (revision = %s)",
                    projectOperations
                        .project(keyOfImportedCodeOwnerConfig.project())
                        .getHead(keyOfImportedCodeOwnerConfig.branchNameKey().branch())
                        .name())));
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.enableCodeOwnerConfigFilesWithFileExtensions",
      value = "true")
  public void importOfCodeOwnerConfigFileWithFileExtension() throws Exception {
    // Create a code owner config file with a file extension. This file is considered as a code
    // owner config file since file extensions for code owner config files are enabled (paramater
    // enableCodeOwnerConfigFilesWithFileExtensions).
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .fileName("OWNERS.FOO")
            .addCodeOwnerEmail(user.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig);

    // create the importing config
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addImport(codeOwnerConfigReference)
            .addCodeOwnerSet(CodeOwnerSet.builder().addCodeOwnerEmail(admin.email()).build())
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global code owner from the importing code owner config and the global
    // code owner from the imported code owner config
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void importGlobalCodeOwners_importModeAll() throws Exception {
    testImportGlobalCodeOwners(CodeOwnerConfigImportMode.ALL);
  }

  @Test
  public void importGlobalCodeOwners_importModeGlobalCodeOwnerSetsOnly() throws Exception {
    testImportGlobalCodeOwners(CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY);
  }

  private void testImportGlobalCodeOwners(CodeOwnerConfigImportMode importMode) throws Exception {
    // create imported config with global code owner
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(importMode, keyOfImportedCodeOwnerConfig);

    // create importing config with global code owner and import
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global code owners from the importing and the imported code owner
    // config
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void importPerFileCodeOwners_importModeAll() throws Exception {
    // create imported config with matching per-file code owner
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("*.md")
                    .addCodeOwnerEmail(user.email())
                    .build())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig);

    // create importing config with matching per-file code owner and import
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("*.md")
                    .addCodeOwnerEmail(admin.email())
                    .build())
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the matching per-file code owners from the importing and the imported
    // code owner config
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void nonMatchingPerFileCodeOwnersAreNotImported_importModeAll() throws Exception {
    // create imported config with non-matching per-file code owner
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("*.txt")
                    .addCodeOwnerEmail(user.email())
                    .build())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig);

    // create importing config with matching per-file code owner and import
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("*.md")
                    .addCodeOwnerEmail(admin.email())
                    .build())
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we only get the matching per-file code owners from the importing code owner
    // config, the per-file code owners from the imported code owner config are not relevant since
    // they do not match
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void perFileCodeOwnersAreNotImported_importModeGlobalCodeOwnerSetsOnly() throws Exception {
    // create imported config with matching per-file code owner
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("*.md")
                    .addCodeOwnerEmail(user.email())
                    .build())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig);

    // create importing config with matching per-file code owner and import
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("*.md")
                    .addCodeOwnerEmail(admin.email())
                    .build())
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we only get the matching per-file code owners from the importing code owner
    // config, the matching per-file code owners from the imported code owner config are not
    // relevant with import mode GLOBAL_CODE_OWNER_SETS_ONLY
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void
      importIgnoreGlobalAndParentCodeOwnersFlagFromMatchingPerFileCodeOwnerSet_importModeAll()
          throws Exception {
    // create imported config with matching per-file code owner that has the
    // ignoreGlobalAndParentCodeOwners flag set to true
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .setIgnoreGlobalAndParentCodeOwners()
                    .addPathExpression("*.md")
                    .addCodeOwnerEmail(user.email())
                    .build())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig);

    // create importing config with global code owner and import
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we only get the matching per-file code owners from the imported code owner
    // config, the global code owners from the importing code owner config are not relevant since
    // the matching per-file code owner set in the imported code owner config has the
    // ignoreGlobalAndParentCodeOwners flag set to true which causes global code owners to be
    // ignored, in addition this flag causes parent code owners to be ignored
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(user.email());
    assertThat(pathCodeOwnersResult.ignoreParentCodeOwners()).isTrue();
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void
      ignoreGlobalAndParentCodeOwnersFlagIsNotImportedFromNonMatchingPerFileCodeOwnerSet_importModeAll()
          throws Exception {
    // create imported config with non-matching per-file code owner that has the
    // ignoreGlobalAndParentCodeOwners flag set to true
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .setIgnoreGlobalAndParentCodeOwners()
                    .addPathExpression("*.txt")
                    .addCodeOwnerEmail(user.email())
                    .build())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig);

    // create importing config with global code owner and import
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we only get the global code owners from the importing code owner config, the
    // per-file code owners from the imported code owner config and its
    // ignoreGlobalAndParentCodeOwners flag are not relevant since the per-file code owner set does
    // not match
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwnersResult.ignoreParentCodeOwners()).isFalse();
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void ignoreGlobalAndParentCodeOwnersFlagIsNotImported_importModeGlobalCodeOwnerSetsOnly()
      throws Exception {
    // create imported config with matching per-file code owner that has the
    // ignoreGlobalAndParentCodeOwners flag set to true
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .setIgnoreGlobalAndParentCodeOwners()
                    .addPathExpression("*.md")
                    .addCodeOwnerEmail(user.email())
                    .build())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig);

    // create importing config with global code owner and import
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we only get the global code owners from the importing code owner config, the
    // matching per-file code owners from the imported code owner config and its
    // ignoreGlobalAndParentCodeOwners flag are not relevant with import mode
    // GLOBAL_CODE_OWNER_SETS_ONLY
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwnersResult.ignoreParentCodeOwners()).isFalse();
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void importIgnoreParentCodeOwnersFlag_importModeAll() throws Exception {
    // create imported config with the ignoreParentCodeOnwers flag set to true
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .ignoreParentCodeOwners()
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig);

    // create importing config
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: ignoreParentCodeOwners is true because the ignoreParentCodeOwners flag in the
    // imported code owner config is set to true
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.ignoreParentCodeOwners()).isTrue();
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void ignoreParentCodeOwnersFlagNotImported_importModeGlobalCodeOwnerSetsOnly()
      throws Exception {
    // create imported config with the ignoreParentCodeOnwers flag set to true
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .ignoreParentCodeOwners()
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig);

    // create importing config
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: ignoreParentCodeOwners is false because the ignoreParentCodeOwners flag in the
    // imported code owner config is not relevant with import mode GLOBAL_CODE_OWNER_SETS_ONLY
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.ignoreParentCodeOwners()).isFalse();
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void importsOfImportedCodeOwnerConfigAreResolved_importModeAll() throws Exception {
    testImportsOfImportedCodeOwnerConfigAreResolved(CodeOwnerConfigImportMode.ALL);
  }

  @Test
  public void importsOfImportedCodeOwnerConfigAreResolved_importModeGlobalCodeOwnerSetsOnly()
      throws Exception {
    testImportsOfImportedCodeOwnerConfigAreResolved(
        CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY);
  }

  private void testImportsOfImportedCodeOwnerConfigAreResolved(CodeOwnerConfigImportMode importMode)
      throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create config with global code owner that is imported by the imported config
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/baz/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user2.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference2 =
        createCodeOwnerConfigReference(importMode, keyOfImportedCodeOwnerConfig2);

    // create imported config with global code owner and import
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user.email())
            .addImport(codeOwnerConfigReference2)
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference1 =
        createCodeOwnerConfigReference(importMode, keyOfImportedCodeOwnerConfig1);

    // create importing config with global code owner and import
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference1)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig1 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig1).get();
    CodeOwnerConfig importedCodeOwnerConfig2 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig2).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing code owner config, the imported code
    // owner config and the code owner config that is imported by the imported code owner config
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email(), user2.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig1, codeOwnerConfigReference1),
            CodeOwnerConfigImport.createResolvedImport(
                importedCodeOwnerConfig1, importedCodeOwnerConfig2, codeOwnerConfigReference2));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void
      onlyGlobalCodeOwnersAreImportedForTransitiveImportsIfImportModeIsGlobalCodeOwnerSetsOnly()
          throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create config with per file code owner that is imported by the imported config
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/baz/")
            .fileName("OWNERS")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("foo.md")
                    .addCodeOwnerEmail(user2.email())
                    .build())
            .create();

    // create imported config with global code owner and import with import mode ALL
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user.email())
            .addImport(
                createCodeOwnerConfigReference(
                    CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig2))
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference1 =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig1);

    // create importing config with global code owner and import with import mode
    // GLOBAL_CODE_OWNER_SETS_ONLY
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference1)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig1 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig1).get();
    CodeOwnerConfig importedCodeOwnerConfig2 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig2).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing code owner config and the imported
    // code owner config but not the per file code owner from the code owner config that is imported
    // by the imported code owner config
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig1, codeOwnerConfigReference1),
            CodeOwnerConfigImport.createResolvedImport(
                importedCodeOwnerConfig1,
                importedCodeOwnerConfig2,
                createCodeOwnerConfigReference(
                    CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                    keyOfImportedCodeOwnerConfig2)));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void importCodeOwnerConfigWithPostFix() throws Exception {
    testImportCodeOwnerConfigWithNameExtension("OWNERS_post_fix");
  }

  @Test
  public void importCodeOwnerConfigWithPostFixThatContainsHyphen() throws Exception {
    testImportCodeOwnerConfigWithNameExtension("OWNERS_post-fix");
  }

  @Test
  public void importCodeOwnerConfigWithPreFix() throws Exception {
    testImportCodeOwnerConfigWithNameExtension("pre_fix_OWNERS");
  }

  @Test
  public void importCodeOwnerConfigWithPreFixThatContainsHyphen() throws Exception {
    testImportCodeOwnerConfigWithNameExtension("pre-fix_OWNERS");
  }

  private void testImportCodeOwnerConfigWithNameExtension(String nameOfImportedCodeOwnerConfig)
      throws Exception {
    // create imported config with global code owner
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName(nameOfImportedCodeOwnerConfig)
            .addCodeOwnerEmail(user.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig);

    // create importing config with global code owner and import
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global code owners from the importing and the imported code owner
    // config
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void cyclicImports() throws Exception {
    CodeOwnerConfigReference codeOwnerConfigReference2 =
        CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/OWNERS");

    // create imported config with global code owner and that imports the importing config
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user.email())
            .addImport(codeOwnerConfigReference2)
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference1 =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig);

    // create importing config with global code owner and import
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference1)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing and the imported code owner config
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference1),
            CodeOwnerConfigImport.createResolvedImport(
                importedCodeOwnerConfig, importingCodeOwnerConfig, codeOwnerConfigReference2));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void importsAreResolvedFromSameRevision() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create importing config with global code owner and import
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(
                CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/bar/OWNERS"))
            .create();

    // create imported config with global code owner
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .addCodeOwnerEmail(user.email())
            .create();

    // remember the revision
    RevCommit oldRevision = projectOperations.project(project).getHead("master");

    // update imported config and add one additional global code owner
    codeOwnerConfigOperations
        .codeOwnerConfig(keyOfImportedCodeOwnerConfig)
        .forUpdate()
        .codeOwnerSetsModification(CodeOwnerSetModification.addToOnlySet(user2.email()))
        .update();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            oldRevision,
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing and the imported code owner config
    // as they were defined at oldRevision
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
  }

  @Test
  public void importWithRelativePath() throws Exception {
    // create imported config with global code owner
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/baz/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig);

    // create importing config with global code owner and import with relative path
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing and the imported code owner config
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void importFromNonExistingProjectIsIgnored() throws Exception {
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        CodeOwnerConfig.Key.create(Project.nameKey("non-existing"), "master", "/bar/", "OWNERS");
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig);

    // create importing config with global code owner and import from non-existing project
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing code owner config
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwnersResult.resolvedImports()).isEmpty();
    assertThat(pathCodeOwnersResult.unresolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createUnresolvedImport(
                importingCodeOwnerConfig,
                keyOfImportedCodeOwnerConfig,
                codeOwnerConfigReference,
                String.format("project %s not found", keyOfImportedCodeOwnerConfig.project())));
  }

  @Test
  public void importFromHiddenProjectIsIgnored() throws Exception {
    // create a hidden project with a code owner config file
    Project.NameKey hiddenProject = projectOperations.newProject().name("hidden-project").create();
    ConfigInput configInput = new ConfigInput();
    configInput.state = ProjectState.HIDDEN;
    gApi.projects().name(hiddenProject.get()).config(configInput);
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(hiddenProject)
            .branch("master")
            .folderPath("/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig);

    // create importing config with global code owner and import from the hidden project
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing code owner config, the global code
    // owners from the imported code owner config are ignored since the project that contains the
    // code owner config is hidden
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwnersResult.resolvedImports()).isEmpty();
    assertThat(pathCodeOwnersResult.unresolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createUnresolvedImport(
                importingCodeOwnerConfig,
                keyOfImportedCodeOwnerConfig,
                codeOwnerConfigReference,
                String.format(
                    "state of project %s doesn't permit read",
                    keyOfImportedCodeOwnerConfig.project())));
  }

  @Test
  public void importFromNonExistingBranchIsIgnored() throws Exception {
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        CodeOwnerConfig.Key.create(project, "non-existing", "/bar/", "OWNERS");
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig);

    // create importing config with global code owner and import from non-existing branch
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing code owner config
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwnersResult.resolvedImports()).isEmpty();
    assertThat(pathCodeOwnersResult.unresolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createUnresolvedImport(
                importingCodeOwnerConfig,
                keyOfImportedCodeOwnerConfig,
                codeOwnerConfigReference,
                "code owner config does not exist (revision = current)"));
  }

  @Test
  public void importFromOtherProject() throws Exception {
    Project.NameKey otherProject = projectOperations.newProject().create();

    // create imported config with global code owner
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(otherProject)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig);

    // create importing config with global code owner and import with relative path
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing and the imported code owner config
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void importFromOtherProjectIsResolvedFromSameBranch() throws Exception {
    Project.NameKey otherProject = projectOperations.newProject().create();

    // Create other branches in project.
    String branchName = "foo";
    createBranch(BranchNameKey.create(project, branchName));

    // Create other branches in other project.
    createBranch(BranchNameKey.create(otherProject, branchName));

    // create imported config with global code owner
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(otherProject)
            .branch(branchName)
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(CodeOwnerConfigImportMode.ALL, "/bar/OWNERS")
            .setProject(otherProject)
            .build();

    // create importing config with global code owner and import with relative path
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branchName)
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead(branchName),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing and the imported code owner config
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void importFromOtherBranch() throws Exception {
    // Create other branch.
    String otherBranch = "foo";
    createBranch(BranchNameKey.create(project, otherBranch));

    // create imported config with global code owner
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(otherBranch)
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig);

    // create importing config with global code owner and import with relative path
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing and the imported code owner config
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void importFromOtherProjectAndBranch() throws Exception {
    Project.NameKey otherProject = projectOperations.newProject().create();

    // Create other branch.
    String otherBranch = "foo";
    createBranch(BranchNameKey.create(otherProject, otherBranch));

    // create imported config with global code owner
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(otherProject)
            .branch(otherBranch)
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig);

    // create importing config with global code owner and import with relative path
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing and the imported code owner config
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void nonResolveablePerFileImportIsIgnored() throws Exception {
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        CodeOwnerConfig.Key.create(project, "master", "/non-existing/", "OWNERS");
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig);

    // create importing config with non-resolveable per file import
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addCodeOwnerEmail(admin.email())
                    .addPathExpression("foo.md")
                    .addImport(codeOwnerConfigReference)
                    .build())
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the per file code owner from the importing code owner config, the
    // non-resolveable per file import is silently ignored
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwnersResult.resolvedImports()).isEmpty();
    assertThat(pathCodeOwnersResult.unresolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createUnresolvedImport(
                importingCodeOwnerConfig,
                keyOfImportedCodeOwnerConfig,
                codeOwnerConfigReference,
                String.format(
                    "code owner config does not exist (revision = %s)",
                    projectOperations
                        .project(keyOfImportedCodeOwnerConfig.project())
                        .getHead(keyOfImportedCodeOwnerConfig.branchNameKey().branch())
                        .name())));
  }

  @Test
  public void perFileImport() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create imported config with ignoreParentCodeOwners = true, a global code owner and a per file
    // code owner
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .ignoreParentCodeOwners()
            .addCodeOwnerEmail(user.email())
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("foo.md")
                    .addCodeOwnerEmail(user2.email())
                    .build())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig);

    // create importing config with per code owner and per file import
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("foo.md")
                    .addCodeOwnerEmail(admin.email())
                    .addImport(codeOwnerConfigReference)
                    .build())
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the per file code owners from the importing and the global code owner
    // from the imported code owner config, but not the per file code owner from the imported code
    // owner config
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());

    // Expectation: the ignoreParentCodeOwners flag from the imported code owner config is ignored
    assertThat(pathCodeOwnersResult.ignoreParentCodeOwners()).isFalse();

    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void importsOfPerFileImportedCodeOwnerConfigAreResolved() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create config with global code owner that is imported by the imported config
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/baz/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user2.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference2 =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig2);

    // create imported config with global code owner and global import
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user.email())
            .addImport(codeOwnerConfigReference2)
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference1 =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig1);

    // create importing config with per file code owner and per file import
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("foo.md")
                    .addCodeOwnerEmail(admin.email())
                    .addImport(codeOwnerConfigReference1)
                    .build())
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig1 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig1).get();
    CodeOwnerConfig importedCodeOwnerConfig2 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig2).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing code owner config, the imported code
    // owner config and the code owner config that is imported by the imported code owner config
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email(), user2.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig1, codeOwnerConfigReference1),
            CodeOwnerConfigImport.createResolvedImport(
                importedCodeOwnerConfig1, importedCodeOwnerConfig2, codeOwnerConfigReference2));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void onlyGlobalCodeOwnersAreImportedForTransitivePerFileImports() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create config with per file code owner that is imported by the imported config
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/baz/")
            .fileName("OWNERS")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("foo.md")
                    .addCodeOwnerEmail(user2.email())
                    .build())
            .create();

    // create imported config with per global owner and global import with mode ALL
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user.email())
            .addImport(
                createCodeOwnerConfigReference(
                    CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig2))
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference1 =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig1);

    // create importing config with per file code owner and per file import
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("foo.md")
                    .addCodeOwnerEmail(admin.email())
                    .addImport(codeOwnerConfigReference1)
                    .build())
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig1 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig1).get();
    CodeOwnerConfig importedCodeOwnerConfig2 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig2).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing code owner config and the imported
    // code owner config, but not the per file code owner from the code owner config that is
    // imported by the imported code owner config
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig1, codeOwnerConfigReference1),
            CodeOwnerConfigImport.createResolvedImport(
                importedCodeOwnerConfig1,
                importedCodeOwnerConfig2,
                createCodeOwnerConfigReference(
                    CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                    keyOfImportedCodeOwnerConfig2)));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void onlyMatchingTransitivePerFileImportsAreImported() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create config with global code owner that is imported by the imported config for *.md files
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/md/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference2 =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig2);

    // create config with global code owner that is imported by the imported config for *.txt files
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig3 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/txt/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user2.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference3 =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig3);

    // create imported config with 2 per file imports, one for *.md files and one for *.txt
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("*.md")
                    .addImport(codeOwnerConfigReference2)
                    .build())
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("*.txt")
                    .addImport(codeOwnerConfigReference3)
                    .build())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference1 =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig1);

    // create importing config with global import
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addImport(codeOwnerConfigReference1)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig1 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig1).get();
    CodeOwnerConfig importedCodeOwnerConfig2 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig2).get();
    CodeOwnerConfig importedCodeOwnerConfig3 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig3).get();

    // Expectation for foo.xyz file: code owners is empty since foo.xyz neither matches *.md nor
    // *.txt
    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.xyz"));
    assertThat(pathCodeOwners).isPresent();
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners()).isEmpty();
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig1, codeOwnerConfigReference1));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();

    // Expectation for foo.md file: code owners contains only user since foo.md only matches *.md
    pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();
    pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(user.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig1, codeOwnerConfigReference1),
            CodeOwnerConfigImport.createResolvedImport(
                importedCodeOwnerConfig1, importedCodeOwnerConfig2, codeOwnerConfigReference2));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();

    // Expectation for foo.txt file: code owners contains only user2 since foo.txt only matches
    // *.txt
    pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.txt"));
    assertThat(pathCodeOwners).isPresent();
    pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(user2.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig1, codeOwnerConfigReference1),
            CodeOwnerConfigImport.createResolvedImport(
                importedCodeOwnerConfig1, importedCodeOwnerConfig3, codeOwnerConfigReference3));
  }

  @Test
  public void cannotMatchAgainstNullCodeOwnerSet() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                PathCodeOwners.matches(
                    /* codeOwnerSet= */ null,
                    Path.of("bar/baz.md"),
                    mock(PathExpressionMatcher.class)));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerSet");
  }

  @Test
  public void cannotMatchNullPath() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                PathCodeOwners.matches(
                    CodeOwnerSet.createWithoutPathExpressions(admin.email()),
                    /* relativePath= */ null,
                    mock(PathExpressionMatcher.class)));
    assertThat(npe).hasMessageThat().isEqualTo("relativePath");
  }

  @Test
  public void cannotMatchAbsolutePath() throws Exception {
    String absolutePath = "/foo/bar/baz.md";
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                PathCodeOwners.matches(
                    CodeOwnerSet.createWithoutPathExpressions(admin.email()),
                    Path.of(absolutePath),
                    mock(PathExpressionMatcher.class)));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("path %s must be relative", absolutePath));
  }

  @Test
  public void cannotMatchWithNullPathExpressionMatcher() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                PathCodeOwners.matches(
                    CodeOwnerSet.createWithoutPathExpressions(admin.email()),
                    Path.of("bar/baz.md"),
                    /* matcher= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("matcher");
  }

  @Test
  public void cannotCheckIfCodeOwnerSetWithoutPathExpressionsMatches() throws Exception {
    PathExpressionMatcher pathExpressionMatcher = mock(PathExpressionMatcher.class);
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                PathCodeOwners.matches(
                    CodeOwnerSet.createWithoutPathExpressions(admin.email()),
                    Path.of("bar/baz.md"),
                    pathExpressionMatcher));
    assertThat(exception).hasMessageThat().isEqualTo("code owner set must have path expressions");
  }

  @Test
  public void codeOwnerSetMatchesIfPathExpressionMatches() throws Exception {
    PathExpressionMatcher pathExpressionMatcher = mock(PathExpressionMatcher.class);
    when(pathExpressionMatcher.matches(anyString(), any(Path.class))).thenReturn(true);
    assertThat(
            PathCodeOwners.matches(
                CodeOwnerSet.builder()
                    .addPathExpression("*.md")
                    .addCodeOwnerEmail(admin.email())
                    .build(),
                Path.of("bar/baz.md"),
                pathExpressionMatcher))
        .isTrue();
  }

  @Test
  public void codeOwnerSetDoesntMatchIfPathExpressionDoesntMatch() throws Exception {
    PathExpressionMatcher pathExpressionMatcher = mock(PathExpressionMatcher.class);
    when(pathExpressionMatcher.matches(anyString(), any(Path.class))).thenReturn(false);
    assertThat(
            PathCodeOwners.matches(
                CodeOwnerSet.builder()
                    .addPathExpression("*.md")
                    .addCodeOwnerEmail(admin.email())
                    .build(),
                Path.of("bar/baz.md"),
                pathExpressionMatcher))
        .isFalse();
  }

  @Test
  public void codeOwnerSetMatchesIfAnyPathExpressionMatches() throws Exception {
    PathExpressionMatcher pathExpressionMatcher = mock(PathExpressionMatcher.class);
    when(pathExpressionMatcher.matches(anyString(), any(Path.class)))
        .thenReturn(true)
        .thenReturn(false);
    assertThat(
            PathCodeOwners.matches(
                CodeOwnerSet.builder()
                    .addPathExpression("*.md")
                    .addPathExpression("config/*")
                    .addPathExpression("build/*")
                    .addCodeOwnerEmail(admin.email())
                    .build(),
                Path.of("bar/baz.md"),
                pathExpressionMatcher))
        .isTrue();
  }

  @Test
  public void perFileRuleThatIgnoresGlobalCodeOwnersCanImportGlobalCodeOwnersFromOtherFile()
      throws Exception {
    // create imported config with a global code owner
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig);

    // create importing config that:
    // * has a global code owner
    // * has a per-file import for md files
    // * ignores global and parent code owners for md files
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression(testPathExpressions.matchFileType("md"))
                    .setIgnoreGlobalAndParentCodeOwners()
                    .addImport(codeOwnerConfigReference)
                    .build())
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global code owner from the imported code owner config (since it is
    // imported by a matching per-file rule), the global code owner from the importing code owner
    // config is ignored (since the matching per-file rule ignores parent and global code owners)
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(user.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void
      perFileRuleThatIgnoresGlobalCodeOwnersCanTransitivelyImportGlobalCodeOwnersFromOtherFile()
          throws Exception {
    // create transitively imported config with a global code owner
    CodeOwnerConfig.Key keyOfBarCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user.email())
            .create();
    CodeOwnerConfigReference barCodeOwnerConfigReference =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfBarCodeOwnerConfig);

    // create imported config the imports the another config
    CodeOwnerConfig.Key keyOfFooCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .fileName("OWNERS")
            .addImport(barCodeOwnerConfigReference)
            .create();
    CodeOwnerConfigReference fooCodeOwnerConfigReference =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfFooCodeOwnerConfig);

    // create importing config that:
    // * has a global code owner
    // * has a per-file import for md files
    // * ignores global and parent code owners for md files
    CodeOwnerConfig.Key keyOfRootCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression(testPathExpressions.matchFileType("md"))
                    .setIgnoreGlobalAndParentCodeOwners()
                    .addImport(fooCodeOwnerConfigReference)
                    .build())
            .create();

    CodeOwnerConfig rootCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfRootCodeOwnerConfig).get();
    CodeOwnerConfig fooCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfFooCodeOwnerConfig).get();
    CodeOwnerConfig barCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfBarCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfRootCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global code owner from the transitively imported code owner config
    // (since it is imported via a matching per-file rule), the global code owner from the importing
    // code owner config is ignored (since the matching per-file rule ignores parent and global code
    // owners)
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(user.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                rootCodeOwnerConfig, fooCodeOwnerConfig, fooCodeOwnerConfigReference),
            CodeOwnerConfigImport.createResolvedImport(
                fooCodeOwnerConfig, barCodeOwnerConfig, barCodeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void
      perFileRuleThatIsImportedByAGlobalImportIsRespectedIfALocalPerFileRuleIgnoresGlobalCodeOwners()
          throws Exception {
    TestAccount user2 = accountCreator.user2();
    TestAccount user3 =
        accountCreator.create("user3", "user3@example.com", "User3", /* displayName= */ null);

    // create imported config that has a matching per-file rule
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user2.email())
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression(testPathExpressions.matchFileType("md"))
                    .addCodeOwnerEmail(user3.email())
                    .build())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, keyOfImportedCodeOwnerConfig);

    // create importing config that has a global import with mode ALL and a per-file rule for md
    // files that ignores global and parent code owners
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference)
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression(testPathExpressions.matchFileType("md"))
                    .setIgnoreGlobalAndParentCodeOwners()
                    .addCodeOwnerEmail(user.email())
                    .build())
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the code owner from the matching per-file rule in the importing code
    // owner config and the code owner from the matching per-file rule in the imported code owner
    // config, the global code owners are ignored since there is a matching per-file rule that
    // ignores parent and global code owners
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(user.email(), user3.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig, codeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void transitiveImportsAcrossProjects() throws Exception {
    TestAccount user2 = accountCreator.user2();

    Project.NameKey otherProject = projectOperations.newProject().create();

    // create transitively imported config in other project
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(otherProject)
            .branch("master")
            .folderPath("/baz/")
            .fileName("OWNERS")
            .addCodeOwnerSet(CodeOwnerSet.builder().addCodeOwnerEmail(user2.email()).build())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference2 =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig2);

    // create imported config in other project
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(otherProject)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user.email())
            .addImport(codeOwnerConfigReference2)
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference1 =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig1);

    // create importing config
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference1)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig1 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig1).get();
    CodeOwnerConfig importedCodeOwnerConfig2 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig2).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing code owner config and from the
    // directly and transitively imported code owner configs in the other project
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email(), user2.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig1, codeOwnerConfigReference1),
            CodeOwnerConfigImport.createResolvedImport(
                importedCodeOwnerConfig1, importedCodeOwnerConfig2, codeOwnerConfigReference2));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void transitiveImportsWithRelativePaths() throws Exception {
    TestAccount user2 = accountCreator.user2();

    Project.NameKey otherProject = projectOperations.newProject().create();

    // create transitively imported config
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(otherProject)
            .branch("master")
            .folderPath("/bar/baz/")
            .fileName("OWNERS")
            .addCodeOwnerSet(CodeOwnerSet.builder().addCodeOwnerEmail(user2.email()).build())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference2 =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig2);

    // create imported config
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(otherProject)
            .branch("master")
            .folderPath("/bar/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user.email())
            .addImport(codeOwnerConfigReference2)
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference1 =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig1);

    // create importing config
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(codeOwnerConfigReference1)
            .create();

    CodeOwnerConfig importingCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get();
    CodeOwnerConfig importedCodeOwnerConfig1 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig1).get();
    CodeOwnerConfig importedCodeOwnerConfig2 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig2).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            keyOfImportingCodeOwnerConfig,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing code owner config and from the
    // directly and transitively imported code owner configs
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email(), user2.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                importingCodeOwnerConfig, importedCodeOwnerConfig1, codeOwnerConfigReference1),
            CodeOwnerConfigImport.createResolvedImport(
                importedCodeOwnerConfig1, importedCodeOwnerConfig2, codeOwnerConfigReference2));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  @Test
  public void transitiveImportsOfPerFileCodeOwners() throws Exception {
    TestAccount mdCodeOwner =
        accountCreator.create(
            "mdCodeOwner", "mdCodeOwner@example.com", "Md Code Owner", /* displayName= */ null);

    CodeOwnerConfigReference barCodeOwnerConfigReference =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.ALL, "/bar/" + getCodeOwnerConfigFileName());
    CodeOwnerConfig.Key fooCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .fileName("OWNERS")
            .addImport(barCodeOwnerConfigReference)
            .create();

    CodeOwnerConfigReference bazCodeOwnerConfigReference =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
            "/baz/" + getCodeOwnerConfigFileName());
    CodeOwnerConfig.Key barCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .fileName("OWNERS")
            .folderPath("/bar/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression(testPathExpressions.matchFileType("md"))
                    .addImport(bazCodeOwnerConfigReference)
                    .build())
            .create();

    CodeOwnerConfig.Key bazCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/baz/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(mdCodeOwner.email())
            .create();

    CodeOwnerConfig fooCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(fooCodeOwnerConfigKey).get();
    CodeOwnerConfig barCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(barCodeOwnerConfigKey).get();
    CodeOwnerConfig bazCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(bazCodeOwnerConfigKey).get();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            fooCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Path.of("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the per-file code owner of the code owner config that is transitively
    // imported.
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig();
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(mdCodeOwner.email());
    assertThat(pathCodeOwnersResult.resolvedImports())
        .containsExactly(
            CodeOwnerConfigImport.createResolvedImport(
                fooCodeOwnerConfig, barCodeOwnerConfig, barCodeOwnerConfigReference),
            CodeOwnerConfigImport.createResolvedImport(
                barCodeOwnerConfig, bazCodeOwnerConfig, bazCodeOwnerConfigReference));
    assertThat(pathCodeOwnersResult.unresolvedImports()).isEmpty();
  }

  private CodeOwnerConfig.Builder createCodeOwnerBuilder() {
    return CodeOwnerConfig.builder(
        CodeOwnerConfig.Key.create(BranchNameKey.create(project, "master"), Path.of("/")),
        ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
  }

  private AutoCloseable registerTestBackend(@Nullable PathExpressionMatcher pathExpressionMatcher) {
    RegistrationHandle registrationHandle =
        ((PrivateInternals_DynamicMapImpl<CodeOwnerBackend>) codeOwnerBackends)
            .put(
                "gerrit",
                TestCodeOwnerBackend.ID,
                Providers.of(new TestCodeOwnerBackend(pathExpressionMatcher)));
    return registrationHandle::remove;
  }

  private static class TestCodeOwnerBackend implements CodeOwnerBackend {
    static final String ID = "test-backend";

    @Nullable private final PathExpressionMatcher pathExpressionMatcher;

    TestCodeOwnerBackend(PathExpressionMatcher pathExpressionMatcher) {
      this.pathExpressionMatcher = pathExpressionMatcher;
    }

    @Override
    public Optional<CodeOwnerConfig> getCodeOwnerConfig(
        CodeOwnerConfig.Key codeOwnerConfigKey, @Nullable ObjectId revision) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<CodeOwnerConfig> upsertCodeOwnerConfig(
        CodeOwnerConfig.Key codeOwnerConfigKey,
        CodeOwnerConfigUpdate codeOwnerConfigUpdate,
        @Nullable IdentifiedUser currentUser) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<PathExpressionMatcher> getPathExpressionMatcher(BranchNameKey branchNameKey) {
      return Optional.ofNullable(pathExpressionMatcher);
    }

    @Override
    public boolean isCodeOwnerConfigFile(NameKey project, String fileName) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Path getFilePath(CodeOwnerConfig.Key codeOwnerConfigKey) {
      return Paths.get(codeOwnerConfigKey.folderPath().toString(), "OWNERS");
    }
  }
}
