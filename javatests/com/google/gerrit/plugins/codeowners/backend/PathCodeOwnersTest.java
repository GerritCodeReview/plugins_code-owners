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

import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.util.Providers;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link PathCodeOwners}. */
public class PathCodeOwnersTest extends AbstractCodeOwnersTest {
  @Inject private ProjectOperations projectOperations;

  private CodeOwnerConfigOperations codeOwnerConfigOperations;
  private PathCodeOwners.Factory pathCodeOwnersFactory;
  private DynamicMap<CodeOwnerBackend> codeOwnerBackends;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerConfigOperations =
        plugin.getSysInjector().getInstance(CodeOwnerConfigOperations.class);
    pathCodeOwnersFactory = plugin.getSysInjector().getInstance(PathCodeOwners.Factory.class);
    codeOwnerBackends =
        plugin.getSysInjector().getInstance(new Key<DynamicMap<CodeOwnerBackend>>() {});
  }

  @Test
  public void createPathCodeOwnersForCodeOwnerConfig() throws Exception {
    PathCodeOwners pathCodeOwners =
        pathCodeOwnersFactory.create(
            createCodeOwnerBuilder().build(), Paths.get("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isNotNull();
  }

  @Test
  public void cannotCreatePathCodeOwnersForNullCodeOwnerConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> pathCodeOwnersFactory.create(null, Paths.get("/foo/bar/baz.md")));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfig");
  }

  @Test
  public void cannotCreatePathCodeOwnersForCodeOwnerConfigWithNullPath() throws Exception {
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerBuilder().build();
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> pathCodeOwnersFactory.create(codeOwnerConfig, null));
    assertThat(npe).hasMessageThat().isEqualTo("path");
  }

  @Test
  public void cannotCreatePathCodeOwnersForCodeOwnerConfigWithRelativePath() throws Exception {
    String relativePath = "foo/bar/baz.md";
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerBuilder().build();
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> pathCodeOwnersFactory.create(codeOwnerConfig, Paths.get(relativePath)));
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
            codeOwnerConfigKey,
            projectOperations.project(project).getHead("master"),
            Paths.get("/foo/bar/baz.md"));
    assertThat(pathCodeOwners).isPresent();
  }

  @Test
  public void cannotCreatePathCodeOwnersForNullCodeOwnerConfigKey() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                pathCodeOwnersFactory.create(
                    null,
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
                    CodeOwnerConfig.Key.create(
                        BranchNameKey.create(project, "master"), Paths.get("/")),
                    null,
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
                    codeOwnerConfigKey,
                    projectOperations.project(project).getHead("master"),
                    null));
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
        pathCodeOwnersFactory.create(emptyCodeOwnerConfig, Paths.get("/foo/bar/baz.md"));
    assertThat(pathCodeOwners.get()).isEmpty();
  }

  @Test
  public void getPathCodeOwnersIfNoPathExpressionsAreUsed() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerBuilder()
            .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email(), user.email()))
            .build();
    PathCodeOwners pathCodeOwners =
        pathCodeOwnersFactory.create(codeOwnerConfig, Paths.get("/foo/bar/baz.md"));
    assertThat(pathCodeOwners.get())
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
          pathCodeOwnersFactory.create(codeOwnerConfig, Paths.get("/foo/bar/baz.md"));
      assertThat(pathCodeOwners.get())
          .comparingElementsUsing(hasEmail())
          .containsExactly(admin.email(), user.email());
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = TestCodeOwnerBackend.ID)
  public void codeOwnerSetsWithPathExpressionsAreIgnoredIfBackendDoesntSupportPathExpressions()
      throws Exception {
    try (AutoCloseable registration = registerTestBackend(null)) {
      CodeOwnerConfig codeOwnerConfig =
          createCodeOwnerBuilder()
              .addCodeOwnerSet(
                  CodeOwnerSet.builder()
                      .addPathExpression("*.md")
                      .addCodeOwnerEmail(admin.email())
                      .build())
              .build();
      PathCodeOwners pathCodeOwners =
          pathCodeOwnersFactory.create(codeOwnerConfig, Paths.get("/foo/bar/baz.md"));
      assertThat(pathCodeOwners.get()).isEmpty();
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
          pathCodeOwnersFactory.create(codeOwnerConfig, Paths.get("/foo/bar/baz.md"));
      assertThat(pathCodeOwners.get())
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
          pathCodeOwnersFactory.create(codeOwnerConfig, Paths.get("/foo/bar/baz.md"));
      assertThat(pathCodeOwners.get())
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
          pathCodeOwnersFactory.create(codeOwnerConfig, Paths.get("/foo/bar/baz.md"));
      assertThat(pathCodeOwners.get())
          .comparingElementsUsing(hasEmail())
          .containsExactly(admin.email(), user.email());
    }
  }

  @Test
  public void checkThatParentCodeOwnersAreIgnoredIfCodeOwnerConfigIgnoresParentCodeOwners()
      throws Exception {
    PathCodeOwners pathCodeOwners =
        pathCodeOwnersFactory.create(
            createCodeOwnerBuilder().setIgnoreParentCodeOwners().build(),
            Paths.get("/foo/bar/baz.md"));
    assertThat(pathCodeOwners.ignoreParentCodeOwners()).isTrue();
  }

  @Test
  public void checkThatParentCodeOwnersAreNotIgnoredIfCodeOwnerConfigDoesNotIgnoreParentCodeOwners()
      throws Exception {
    PathCodeOwners pathCodeOwners =
        pathCodeOwnersFactory.create(
            createCodeOwnerBuilder().setIgnoreParentCodeOwners(false).build(),
            Paths.get("/foo/bar/baz.md"));
    assertThat(pathCodeOwners.ignoreParentCodeOwners()).isFalse();
  }

  @Test
  public void checkThatParentCodeOwnersAreIgnoredIfMatchingCodeOwnerSetIgnoresParentCodeOwners()
      throws Exception {
    PathCodeOwners pathCodeOwners =
        pathCodeOwnersFactory.create(
            createCodeOwnerBuilder()
                .addCodeOwnerSet(
                    CodeOwnerSet.builder()
                        .setIgnoreGlobalAndParentCodeOwners()
                        .addPathExpression("*.md")
                        .build())
                .build(),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners.ignoreParentCodeOwners()).isTrue();
  }

  @Test
  public void
      checkThatParentCodeOwnersAreNotIgnoredIfNonMatchingCodeOwnerSetIgnoresParentCodeOwners()
          throws Exception {
    PathCodeOwners pathCodeOwners =
        pathCodeOwnersFactory.create(
            createCodeOwnerBuilder()
                .addCodeOwnerSet(
                    CodeOwnerSet.builder()
                        .setIgnoreGlobalAndParentCodeOwners()
                        .addPathExpression("*.txt")
                        .build())
                .build(),
            Paths.get("/foo.md"));
    assertThat(pathCodeOwners.ignoreParentCodeOwners()).isFalse();
  }

  @Test
  public void cannotMatchAgainstNullCodeOwnerSet() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                PathCodeOwners.matches(
                    null, Paths.get("bar/baz.md"), mock(PathExpressionMatcher.class)));
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
                    null,
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
                    null));
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

  private CodeOwnerConfig.Builder createCodeOwnerBuilder() {
    return CodeOwnerConfig.builder(
        CodeOwnerConfig.Key.create(BranchNameKey.create(project, "master"), Paths.get("/")));
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
    public Optional<PathExpressionMatcher> getPathExpressionMatcher() {
      return Optional.ofNullable(pathExpressionMatcher);
    }
  }
}
