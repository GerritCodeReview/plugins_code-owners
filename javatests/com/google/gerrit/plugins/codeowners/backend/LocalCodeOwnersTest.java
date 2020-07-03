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

import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Key;
import com.google.inject.util.Providers;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link LocalCodeOwners}. */
public class LocalCodeOwnersTest extends AbstractCodeOwnersTest {
  private LocalCodeOwners localCodeOwners;
  private DynamicMap<CodeOwnerBackend> codeOwnerBackends;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    localCodeOwners = plugin.getSysInjector().getInstance(LocalCodeOwners.class);
    codeOwnerBackends =
        plugin.getSysInjector().getInstance(new Key<DynamicMap<CodeOwnerBackend>>() {});
  }

  @Test
  public void cannotGetLocalCodeOwnersForNullCodeOwnerConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> localCodeOwners.get(null, Paths.get("/foo/bar/baz.md")));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfig");
  }

  @Test
  public void cannotGetLocalCodeOwnersForNullPath() throws Exception {
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerBuilder().build();
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> localCodeOwners.get(codeOwnerConfig, null));
    assertThat(npe).hasMessageThat().isEqualTo("absolutePath");
  }

  @Test
  public void cannotGetLocalCodeOwnersForRelativePath() throws Exception {
    String relativePath = "foo/bar/baz.md";
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerBuilder().build();
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> localCodeOwners.get(codeOwnerConfig, Paths.get(relativePath)));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("path %s must be absolute", relativePath));
  }

  @Test
  public void getEmptyLocalCodeOwners() throws Exception {
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerBuilder().build();
    assertThat(localCodeOwners.get(codeOwnerConfig, Paths.get("/foo/bar/baz.md"))).isEmpty();
  }

  @Test
  public void getLocalCodeOwnersIfNoPathExpressionsAreUsed() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerBuilder()
            .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email(), user.email()))
            .build();
    assertThat(localCodeOwners.get(codeOwnerConfig, Paths.get("/foo/bar/baz.md")))
        .comparingElementsUsing(hasEmail())
        .containsExactly(admin.email(), user.email());
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = TestCodeOwnerBackend.ID)
  public void getLocalCodeOwnersReturnsCodeOwnersFromMatchingCodeOwnerSets() throws Exception {
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
      assertThat(localCodeOwners.get(codeOwnerConfig, Paths.get("/foo/bar/baz.md")))
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
      assertThat(localCodeOwners.get(codeOwnerConfig, Paths.get("/foo/bar/baz.md"))).isEmpty();
    }
  }

  @Test
  public void cannotMatchAgainstNullCodeOwnerSet() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                LocalCodeOwners.matches(
                    null, Paths.get("bar/baz.md"), mock(PathExpressionMatcher.class)));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerSet");
  }

  @Test
  public void cannotMatchNullPath() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                LocalCodeOwners.matches(
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
                LocalCodeOwners.matches(
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
                LocalCodeOwners.matches(
                    CodeOwnerSet.createWithoutPathExpressions(admin.email()),
                    Paths.get("bar/baz.md"),
                    null));
    assertThat(npe).hasMessageThat().isEqualTo("matcher");
  }

  @Test
  public void codeOwnerSetWithoutPathExpressionsMatchesAnyPath() throws Exception {
    PathExpressionMatcher pathExpressionMatcher = mock(PathExpressionMatcher.class);
    assertThat(
            LocalCodeOwners.matches(
                CodeOwnerSet.createWithoutPathExpressions(admin.email()),
                Paths.get("bar/baz.md"),
                pathExpressionMatcher))
        .isTrue();
  }

  @Test
  public void codeOwnerSetMatchesIfPathExpressionMatches() throws Exception {
    PathExpressionMatcher pathExpressionMatcher = mock(PathExpressionMatcher.class);
    when(pathExpressionMatcher.matches(anyString(), any(Path.class))).thenReturn(true);
    assertThat(
            LocalCodeOwners.matches(
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
            LocalCodeOwners.matches(
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
            LocalCodeOwners.matches(
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
    return () -> registrationHandle.remove();
  }

  private static class TestCodeOwnerBackend implements CodeOwnerBackend {
    static final String ID = "test-backend";

    @Nullable private final PathExpressionMatcher pathExpressionMatcher;

    TestCodeOwnerBackend(PathExpressionMatcher pathExpressionMatcher) {
      this.pathExpressionMatcher = pathExpressionMatcher;
    }

    @Override
    public Optional<CodeOwnerConfig> getCodeOwnerConfig(CodeOwnerConfig.Key codeOwnerConfigKey) {
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
