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
import static com.google.common.truth.Truth8.assertThat;
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
import org.eclipse.jgit.revwalk.RevWalk;
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
            createCodeOwnerBuilder().build(), Paths.get("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isNotNull();
  }

  @Test
  public void cannotCreatePathCodeOwnersForNullCodeOwnerConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                pathCodeOwnersFactory.createWithoutCache(
                    /* codeOwnerConfig= */ null, Paths.get("/foo/bar/baz.md")));
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
            () ->
                pathCodeOwnersFactory.createWithoutCache(codeOwnerConfig, Paths.get(relativePath)));
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
            Paths.get("/foo/bar/baz.md"));
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
                        BranchNameKey.create(project, "master"), Paths.get("/")),
                    projectOperations.project(project).getHead("master"),
                    Paths.get("/foo/bar/baz.md")));
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
                    Paths.get("/foo/bar/baz.md")));
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
                        BranchNameKey.create(project, "master"), Paths.get("/")),
                    /* revision= */ null,
                    Paths.get("/foo/bar/baz.md")));
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
                    Paths.get(relativePath)));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("path %s must be absolute", relativePath));
  }

  @Test
  public void getEmptyPathCodeOwners() throws Exception {
    CodeOwnerConfig emptyCodeOwnerConfig = createCodeOwnerBuilder().build();
    PathCodeOwners pathCodeOwners =
        pathCodeOwnersFactory.createWithoutCache(
            emptyCodeOwnerConfig, Paths.get("/foo/bar/baz.md"));
    assertThat(pathCodeOwners.resolveCodeOwnerConfig().get().getPathCodeOwners()).isEmpty();
    assertThat(pathCodeOwners.resolveCodeOwnerConfig().get().hasUnresolvedImports()).isFalse();
  }

  @Test
  public void getPathCodeOwnersIfNoPathExpressionsAreUsed() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerBuilder()
            .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email(), user.email()))
            .build();
    PathCodeOwners pathCodeOwners =
        pathCodeOwnersFactory.createWithoutCache(codeOwnerConfig, Paths.get("/foo/bar/baz.md"));
    assertThat(pathCodeOwners.resolveCodeOwnerConfig().get().getPathCodeOwners())
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
          pathCodeOwnersFactory.createWithoutCache(codeOwnerConfig, Paths.get("/foo/bar/baz.md"));
      assertThat(pathCodeOwners.resolveCodeOwnerConfig().get().getPathCodeOwners())
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
          pathCodeOwnersFactory.createWithoutCache(codeOwnerConfig, Paths.get("/foo/bar/baz.md"));
      assertThat(pathCodeOwners.resolveCodeOwnerConfig().get().getPathCodeOwners()).isEmpty();
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
          pathCodeOwnersFactory.createWithoutCache(codeOwnerConfig, Paths.get("/foo/bar/baz.md"));
      assertThat(pathCodeOwners.resolveCodeOwnerConfig().get().getPathCodeOwners())
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
          pathCodeOwnersFactory.createWithoutCache(codeOwnerConfig, Paths.get("/foo/bar/baz.md"));
      assertThat(pathCodeOwners.resolveCodeOwnerConfig().get().getPathCodeOwners())
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
          pathCodeOwnersFactory.createWithoutCache(codeOwnerConfig, Paths.get("/foo/bar/baz.md"));
      assertThat(pathCodeOwners.resolveCodeOwnerConfig().get().getPathCodeOwners())
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
            Paths.get("/foo/bar/baz.md"));
    assertThat(pathCodeOwners.resolveCodeOwnerConfig().get().ignoreParentCodeOwners()).isTrue();
  }

  @Test
  public void checkThatParentCodeOwnersAreNotIgnoredIfCodeOwnerConfigDoesNotIgnoreParentCodeOwners()
      throws Exception {
    PathCodeOwners pathCodeOwners =
        pathCodeOwnersFactory.createWithoutCache(
            createCodeOwnerBuilder().setIgnoreParentCodeOwners(false).build(),
            Paths.get("/foo/bar/baz.md"));
    assertThat(pathCodeOwners.resolveCodeOwnerConfig().get().ignoreParentCodeOwners()).isFalse();
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
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners.resolveCodeOwnerConfig().get().ignoreParentCodeOwners()).isTrue();
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
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners.resolveCodeOwnerConfig().get().ignoreParentCodeOwners()).isFalse();
  }

  @Test
  public void nonResolveableImportIsIgnored() throws Exception {
    // create importing config with non-resolveable import
    CodeOwnerConfig.Key importingCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addImport(
                CodeOwnerConfigReference.create(
                    CodeOwnerConfigImportMode.ALL, "/non-existing/OWNERS"))
            .addCodeOwnerSet(CodeOwnerSet.builder().addCodeOwnerEmail(admin.email()).build())
            .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            importingCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global code owner from the importing code owner config, the
    // non-resolveable import is silently ignored
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports()).isTrue();
  }

  @Test
  public void importOfNonCodeOwnerConfigFileIsIgnored() throws Exception {
    // create a file that looks like a code owner config file, but which has a name that is not
    // allowed as code owner config file
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .fileName("FOO")
        .addCodeOwnerEmail(user.email())
        .create();

    // create config with import of non code owner config file
    CodeOwnerConfig.Key importingCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addImport(CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/FOO"))
            .addCodeOwnerSet(CodeOwnerSet.builder().addCodeOwnerEmail(admin.email()).build())
            .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            importingCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global code owner from the importing code owner config, the
    // import of the non code owner config file is silently ignored
    PathCodeOwnersResult pathCodeOwnersResult = pathCodeOwners.get().resolveCodeOwnerConfig().get();
    assertThat(pathCodeOwnersResult.getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwnersResult.hasUnresolvedImports()).isTrue();
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
    // create importing config with global code owner and import
    CodeOwnerConfig.Key importingCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(CodeOwnerConfigReference.create(importMode, "/bar/OWNERS"))
            .create();

    // create imported config with global code owner
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .addCodeOwnerEmail(user.email())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            importingCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global code owners from the importing and the imported code owner
    // config
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  @Test
  public void importPerFileCodeOwners_importModeAll() throws Exception {
    // create importing config with matching per-file code owner and import
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
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
            .addImport(
                CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/bar/OWNERS"))
            .create();

    // create imported config with matching per-file code owner
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression("*.md")
                .addCodeOwnerEmail(user.email())
                .build())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the matching per-file code owners from the importing and the imported
    // code owner config
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  @Test
  public void nonMatchingPerFileCodeOwnersAreNotImported_importModeAll() throws Exception {
    // create importing config with matching per-file code owner and import
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
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
            .addImport(
                CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/bar/OWNERS"))
            .create();

    // create imported config with non-matching per-file code owner
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression("*.txt")
                .addCodeOwnerEmail(user.email())
                .build())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we only get the matching per-file code owners from the importing code owner
    // config, the per-file code owners from the imported code owner config are not relevant since
    // they do not match
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  @Test
  public void perFileCodeOwnersAreNotImported_importModeGlobalCodeOwnerSetsOnly() throws Exception {
    // create importing config with matching per-file code owner and import
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
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
            .addImport(
                CodeOwnerConfigReference.create(
                    CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/bar/OWNERS"))
            .create();

    // create imported config with matching per-file code owner
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression("*.md")
                .addCodeOwnerEmail(user.email())
                .build())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we only get the matching per-file code owners from the importing code owner
    // config, the matching per-file code owners from the imported code owner config are not
    // relevant with import mode GLOBAL_CODE_OWNER_SETS_ONLY
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  @Test
  public void
      importIgnoreGlobalAndParentCodeOwnersFlagFromMatchingPerFileCodeOwnerSet_importModeAll()
          throws Exception {
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

    // create imported config with matching per-file code owner that has the
    // ignoreGlobalAndParentCodeOwners flag set to true
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .setIgnoreGlobalAndParentCodeOwners()
                .addPathExpression("*.md")
                .addCodeOwnerEmail(user.email())
                .build())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we only get the matching per-file code owners from the imported code owner
    // config, the global code owners from the importing code owner config are not relevant since
    // the matching per-file code owner set in the imported code owner config has the
    // ignoreGlobalAndParentCodeOwners flag set to true which causes global code owners to be
    // ignored, in addition this flag causes parent code owners to be ignored
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(user.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().ignoreParentCodeOwners())
        .isTrue();
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  @Test
  public void
      ignoreGlobalAndParentCodeOwnersFlagIsNotImportedFromNonMatchingPerFileCodeOwnerSet_importModeAll()
          throws Exception {
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

    // create imported config with non-matching per-file code owner that has the
    // ignoreGlobalAndParentCodeOwners flag set to true
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .setIgnoreGlobalAndParentCodeOwners()
                .addPathExpression("*.txt")
                .addCodeOwnerEmail(user.email())
                .build())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we only get the global code owners from the importing code owner config, the
    // per-file code owners from the imported code owner config and its
    // ignoreGlobalAndParentCodeOwners flag are not relevant since the per-file code owner set does
    // not match
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().ignoreParentCodeOwners())
        .isFalse();
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  @Test
  public void ignoreGlobalAndParentCodeOwnersFlagIsNotImported_importModeGlobalCodeOwnerSetsOnly()
      throws Exception {
    // create importing config with global code owner and import
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(
                CodeOwnerConfigReference.create(
                    CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/bar/OWNERS"))
            .create();

    // create imported config with matching per-file code owner that has the
    // ignoreGlobalAndParentCodeOwners flag set to true
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .setIgnoreGlobalAndParentCodeOwners()
                .addPathExpression("*.md")
                .addCodeOwnerEmail(user.email())
                .build())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we only get the global code owners from the importing code owner config, the
    // matching per-file code owners from the imported code owner config and its
    // ignoreGlobalAndParentCodeOwners flag are not relevant with import mode
    // GLOBAL_CODE_OWNER_SETS_ONLY
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().ignoreParentCodeOwners())
        .isFalse();
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  @Test
  public void importIgnoreParentCodeOwnersFlag_importModeAll() throws Exception {
    // create importing config
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

    // create imported config with the ignoreParentCodeOnwers flag set to true
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .ignoreParentCodeOwners()
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: ignoreParentCodeOwners is true because the ignoreParentCodeOwners flag in the
    // imported code owner config is set to true
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().ignoreParentCodeOwners())
        .isTrue();
  }

  @Test
  public void ignoreParentCodeOwnersFlagNotImported_importModeGlobalCodeOwnerSetsOnly()
      throws Exception {
    // create importing config
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(
                CodeOwnerConfigReference.create(
                    CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/bar/OWNERS"))
            .create();

    // create imported config with the ignoreParentCodeOnwers flag set to true
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .ignoreParentCodeOwners()
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: ignoreParentCodeOwners is false because the ignoreParentCodeOwners flag in the
    // imported code owner config is not relevant with import mode GLOBAL_CODE_OWNER_SETS_ONLY
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().ignoreParentCodeOwners())
        .isFalse();
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

    // create importing config with global code owner and import
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(CodeOwnerConfigReference.create(importMode, "/bar/OWNERS"))
            .create();

    // create imported config with global code owner and import
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .addCodeOwnerEmail(user.email())
        .addImport(CodeOwnerConfigReference.create(importMode, "/baz/OWNERS"))
        .create();

    // create config with global code owner that is imported by the imported config
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/baz/")
        .addCodeOwnerEmail(user2.email())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing code owner config, the imported code
    // owner config and the code owner config that is imported by the imported code owner config
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email(), user2.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  @Test
  public void
      onlyGlobalCodeOwnersAreImportedForTransitiveImportsIfImportModeIsGlobalCodeOwnerSetsOnly()
          throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create importing config with global code owner and import with import mode
    // GLOBAL_CODE_OWNER_SETS_ONLY
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(
                CodeOwnerConfigReference.create(
                    CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/bar/OWNERS"))
            .create();

    // create imported config with global code owner and import with import mode ALL
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .addCodeOwnerEmail(user.email())
        .addImport(CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/baz/OWNERS"))
        .create();

    // create config with per file code owner that is imported by the imported config
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/baz/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression("foo.md")
                .addCodeOwnerEmail(user2.email())
                .build())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing code owner config and the imported
    // code owner config but not the per file code owner from the code owner config that is imported
    // by the imported code owner config
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  @Test
  public void importCodeOwnerConfigWithPostFix() throws Exception {
    testImportCodeOwnerConfigWithNameExtension("OWNERS_post_fix");
  }

  @Test
  public void importCodeOwnerConfigWithPreFix() throws Exception {
    testImportCodeOwnerConfigWithNameExtension("pre_fix_OWNERS");
  }

  private void testImportCodeOwnerConfigWithNameExtension(String nameOfImportedCodeOwnerConfig)
      throws Exception {
    // create importing config with global code owner and import
    CodeOwnerConfig.Key importingCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(
                CodeOwnerConfigReference.create(
                    CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                    "/bar/" + nameOfImportedCodeOwnerConfig))
            .create();

    // create imported config with global code owner
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .fileName(nameOfImportedCodeOwnerConfig)
        .addCodeOwnerEmail(user.email())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            importingCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global code owners from the importing and the imported code owner
    // config
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
  }

  @Test
  public void cyclicImports() throws Exception {
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

    // create imported config with global code owner and that imports the importing config
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .addCodeOwnerEmail(user.email())
        .addImport(CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/OWNERS"))
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing and the imported code owner config
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
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
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing and the imported code owner config
    // as they were defined at oldRevision
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
  }

  @Test
  public void importWithRelativePath() throws Exception {
    // create importing config with global code owner and import with relative path
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail(admin.email())
            .addImport(
                CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "../baz/OWNERS"))
            .create();

    // create imported config with global code owner
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/baz/")
        .addCodeOwnerEmail(user.email())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing and the imported code owner config
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  @Test
  public void importFromNonExistingProjectIsIgnored() throws Exception {
    // create importing config with global code owner and import from non-existing project
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(
                CodeOwnerConfigReference.builder(CodeOwnerConfigImportMode.ALL, "/bar/OWNERS")
                    .setProject(Project.nameKey("non-existing"))
                    .build())
            .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing code owner config
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports()).isTrue();
  }

  @Test
  public void importFromHiddenProjectIsIgnored() throws Exception {
    // create a hidden project with a code owner config file
    Project.NameKey hiddenProject = projectOperations.newProject().name("hidden-project").create();
    ConfigInput configInput = new ConfigInput();
    configInput.state = ProjectState.HIDDEN;
    gApi.projects().name(hiddenProject.get()).config(configInput);
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(hiddenProject)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();

    // create importing config with global code owner and import from the hidden project
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(
                CodeOwnerConfigReference.builder(CodeOwnerConfigImportMode.ALL, "/OWNERS")
                    .setProject(hiddenProject)
                    .build())
            .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing code owner config, the global code
    // owners from the imported code owner config are ignored since the project that contains the
    // code owner config is hidden
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports()).isTrue();
  }

  @Test
  public void importFromNonExistingBranchIsIgnored() throws Exception {
    // create importing config with global code owner and import from non-existing branch
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(
                CodeOwnerConfigReference.builder(CodeOwnerConfigImportMode.ALL, "/bar/OWNERS")
                    .setProject(project)
                    .setBranch("non-existing")
                    .build())
            .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing code owner config
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports()).isTrue();
  }

  @Test
  public void importFromOtherProject() throws Exception {
    Project.NameKey otherProject = projectOperations.newProject().create();

    // create importing config with global code owner and import with relative path
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(
                CodeOwnerConfigReference.builder(CodeOwnerConfigImportMode.ALL, "/bar/OWNERS")
                    .setProject(otherProject)
                    .build())
            .create();

    // create imported config with global code owner
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(otherProject)
        .branch("master")
        .folderPath("/bar/")
        .addCodeOwnerEmail(user.email())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing and the imported code owner config
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  @Test
  public void importFromOtherProjectIsResolvedFromSameBranch() throws Exception {
    Project.NameKey otherProject = projectOperations.newProject().create();

    // Create other branches in project.
    String branchName = "foo";
    createBranch(BranchNameKey.create(project, branchName));

    // Create other branches in other project.
    createBranch(BranchNameKey.create(otherProject, branchName));

    // create importing config with global code owner and import with relative path
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branchName)
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(
                CodeOwnerConfigReference.builder(CodeOwnerConfigImportMode.ALL, "/bar/OWNERS")
                    .setProject(otherProject)
                    .build())
            .create();

    // create imported config with global code owner
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(otherProject)
        .branch(branchName)
        .folderPath("/bar/")
        .addCodeOwnerEmail(user.email())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead(branchName),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing and the imported code owner config
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  @Test
  public void importFromOtherBranch() throws Exception {
    // Create other branch.
    String otherBranch = "foo";
    createBranch(BranchNameKey.create(project, otherBranch));

    // create importing config with global code owner and import with relative path
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(
                CodeOwnerConfigReference.builder(CodeOwnerConfigImportMode.ALL, "/bar/OWNERS")
                    .setProject(project)
                    .setBranch(otherBranch)
                    .build())
            .create();

    // create imported config with global code owner
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(otherBranch)
        .folderPath("/bar/")
        .addCodeOwnerEmail(user.email())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing and the imported code owner config
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  @Test
  public void importFromOtherProjectAndBranch() throws Exception {
    Project.NameKey otherProject = projectOperations.newProject().create();

    // Create other branch.
    String otherBranch = "foo";
    createBranch(BranchNameKey.create(otherProject, otherBranch));

    // create importing config with global code owner and import with relative path
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(
                CodeOwnerConfigReference.builder(CodeOwnerConfigImportMode.ALL, "/bar/OWNERS")
                    .setProject(otherProject)
                    .setBranch(otherBranch)
                    .build())
            .create();

    // create imported config with global code owner
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(otherProject)
        .branch(otherBranch)
        .folderPath("/bar/")
        .addCodeOwnerEmail(user.email())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing and the imported code owner config
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  @Test
  public void nonResolveablePerFileImportIsIgnored() throws Exception {
    // create importing config with non-resolveable per file import
    CodeOwnerConfig.Key importingCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addCodeOwnerEmail(admin.email())
                    .addPathExpression("foo.md")
                    .addImport(
                        CodeOwnerConfigReference.create(
                            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                            "/non-existing/OWNERS"))
                    .build())
            .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            importingCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the per file code owner from the importing code owner config, the
    // non-resolveable per file import is silently ignored
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports()).isTrue();
  }

  @Test
  public void perFileImport() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create importing config with per code owner and per file import
    CodeOwnerConfig.Key importingCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("foo.md")
                    .addCodeOwnerEmail(admin.email())
                    .addImport(
                        CodeOwnerConfigReference.create(
                            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/bar/OWNERS"))
                    .build())
            .create();

    // create imported config with ignoreParentCodeOwners = true, a global code owner and a per file
    // code owner
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .ignoreParentCodeOwners()
        .addCodeOwnerEmail(user.email())
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression("foo.md")
                .addCodeOwnerEmail(user2.email())
                .build())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            importingCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the per file code owners from the importing and the global code owner
    // from the imported code owner config, but not the per file code owner from the imported code
    // owner config
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());

    // Expectation: the ignoreParentCodeOwners flag from the imported code owner config is ignored
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().ignoreParentCodeOwners())
        .isFalse();
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  @Test
  public void importsOfPerFileImportedCodeOwnerConfigAreResolved() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create importing config with per file code owner and per file import
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("foo.md")
                    .addCodeOwnerEmail(admin.email())
                    .addImport(
                        CodeOwnerConfigReference.create(
                            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/bar/OWNERS"))
                    .build())
            .create();

    // create imported config with global code owner and global import
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .addCodeOwnerEmail(user.email())
        .addImport(
            CodeOwnerConfigReference.create(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/baz/OWNERS"))
        .create();

    // create config with global code owner that is imported by the imported config
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/baz/")
        .addCodeOwnerEmail(user2.email())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing code owner config, the imported code
    // owner config and the code owner config that is imported by the imported code owner config
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email(), user2.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  @Test
  public void onlyGlobalCodeOwnersAreImportedForTransitivePerFileImports() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create importing config with per file code owner and per file import
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("foo.md")
                    .addCodeOwnerEmail(admin.email())
                    .addImport(
                        CodeOwnerConfigReference.create(
                            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/bar/OWNERS"))
                    .build())
            .create();

    // create imported config with per global owner and global import with mode ALL
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .addCodeOwnerEmail(user.email())
        .addImport(CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/baz/OWNERS"))
        .create();

    // create config with per file code owner that is imported by the imported config
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/baz/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression("foo.md")
                .addCodeOwnerEmail(user2.email())
                .build())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global owners from the importing code owner config and the imported
    // code owner config, but not the per file code owner from the code owner config that is
    // imported by the imported code owner config
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  @Test
  public void onlyMatchingTransitivePerFileImportsAreImported() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create importing config with global import
    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addImport(
                CodeOwnerConfigReference.create(
                    CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/bar/OWNERS"))
            .create();

    // create imported config with 2 per file imports, one for *.md files and one for *.txt
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression("*.md")
                .addImport(
                    CodeOwnerConfigReference.create(
                        CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/md/OWNERS"))
                .build())
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression("*.txt")
                .addImport(
                    CodeOwnerConfigReference.create(
                        CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/txt/OWNERS"))
                .build())
        .create();

    // create config with global code owner that is imported by the imported config for *.md files
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/md/")
        .addCodeOwnerEmail(user.email())
        .create();

    // create config with global code owner that is imported by the imported config for *.txt files
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/txt/")
        .addCodeOwnerEmail(user2.email())
        .create();

    // Expectation for foo.xyz file: code owners is empty since foo.xyz neither matches *.md nor
    // *.txt
    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.xyz"));
    assertThat(pathCodeOwners).isPresent();
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners()).isEmpty();

    // Expectation for foo.md file: code owners contains only user since foo.md only matches *.md
    pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(user.email());

    // Expectation for foo.txt file: code owners contains only user2 since foo.txt only matches
    // *.txt
    pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            rootCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.txt"));
    assertThat(pathCodeOwners).isPresent();
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(user2.email());
  }

  @Test
  public void cannotMatchAgainstNullCodeOwnerSet() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                PathCodeOwners.matches(
                    /* codeOwnerSet= */ null,
                    Paths.get("bar/baz.md"),
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
                    Paths.get(absolutePath),
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
                    Paths.get("bar/baz.md"),
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
                    Paths.get("bar/baz.md"),
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
                Paths.get("bar/baz.md"),
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
                Paths.get("bar/baz.md"),
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
                Paths.get("bar/baz.md"),
                pathExpressionMatcher))
        .isTrue();
  }

  @Test
  public void perFileRuleThatIgnoresGlobalCodeOwnersCanImportGlobalCodeOwnersFromOtherFile()
      throws Exception {
    // create importing config that:
    // * has a global code owner
    // * has a per-file import for md files
    // * ignores global and parent code owners for md files
    CodeOwnerConfig.Key importingCodeOwnerConfigKey =
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
                    .addImport(
                        CodeOwnerConfigReference.create(
                            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/bar/OWNERS"))
                    .build())
            .create();

    // create imported config with a global code owner
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .addCodeOwnerEmail(user.email())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            importingCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the global code owner from the imported code owner config (since it is
    // imported by a matching per-file rule), the global code owner from the importing code owner
    // config is ignored (since the matching per-file rule ignores parent and global code owners)
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(user.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  @Test
  public void
      perFileRuleThatIsImportedByAGlobalImportIsRespectedIfALocalPerFileRuleIgnoresGlobalCodeOwners()
          throws Exception {
    TestAccount user2 = accountCreator.user2();
    TestAccount user3 =
        accountCreator.create("user3", "user3@example.com", "User3", /* displayName= */ null);

    // create importing config that has a global import with mode ALL and a per-file rule for md
    // files that ignores global and parent code owners
    CodeOwnerConfig.Key importingCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addImport(
                CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/bar/OWNERS"))
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression(testPathExpressions.matchFileType("md"))
                    .setIgnoreGlobalAndParentCodeOwners()
                    .addCodeOwnerEmail(user.email())
                    .build())
            .create();

    // create imported config that has a matching per-file rule
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .addCodeOwnerEmail(user2.email())
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression(testPathExpressions.matchFileType("md"))
                .addCodeOwnerEmail(user3.email())
                .build())
        .create();

    Optional<PathCodeOwners> pathCodeOwners =
        pathCodeOwnersFactory.create(
            transientCodeOwnerConfigCacheProvider.get(),
            importingCodeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners).isPresent();

    // Expectation: we get the code owner from the matching per-file rule in the importing code
    // owner config and the code owner from the matching per-file rule in the imported code owner
    // config, the global code owners are ignored since there is a matching per-file rule that
    // ignores parent and global code owners
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().getPathCodeOwners())
        .comparingElementsUsing(hasEmail())
        .containsExactly(user.email(), user3.email());
    assertThat(pathCodeOwners.get().resolveCodeOwnerConfig().get().hasUnresolvedImports())
        .isFalse();
  }

  private CodeOwnerConfig.Builder createCodeOwnerBuilder() {
    return CodeOwnerConfig.builder(
        CodeOwnerConfig.Key.create(BranchNameKey.create(project, "master"), Paths.get("/")),
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
        CodeOwnerConfig.Key codeOwnerConfigKey,
        @Nullable RevWalk revWalk,
        @Nullable ObjectId revision) {
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
    public Optional<PathExpressionMatcher> getPathExpressionMatcher() {
      return Optional.ofNullable(pathExpressionMatcher);
    }

    @Override
    public boolean isCodeOwnerConfigFile(NameKey project, String fileName) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Path getFilePath(CodeOwnerConfig.Key codeOwnerConfigKey) {
      throw new UnsupportedOperationException("not implemented");
    }
  }
}
