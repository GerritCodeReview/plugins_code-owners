// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.plugins.codeowners.acceptance.testsuite;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigUpdate;
import com.google.gerrit.plugins.codeowners.backend.GlobMatcher;
import com.google.gerrit.plugins.codeowners.backend.PathExpressionMatcher;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Key;
import com.google.inject.util.Providers;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link TestPathExpressions}. */
public class TestPathExpressionsTest extends AbstractCodeOwnersTest {
  private TestPathExpressions testPathExpressions;
  private DynamicMap<CodeOwnerBackend> codeOwnerBackends;

  @Before
  public void setUp() {
    testPathExpressions = plugin.getSysInjector().getInstance(TestPathExpressions.class);
    codeOwnerBackends =
        plugin.getSysInjector().getInstance(new Key<DynamicMap<CodeOwnerBackend>>() {});
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = "find-owners")
  public void matchFileType_findOwnersBackend() throws Exception {
    assertThat(testPathExpressions.matchFileType("md")).isEqualTo("*.md");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = "proto")
  public void matchFileType_protoBackend() throws Exception {
    assertThat(testPathExpressions.matchFileType("md")).isEqualTo("....md");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = TestCodeOwnerBackend.ID)
  public void matchFileType_backendWithGlobMatcher() throws Exception {
    try (AutoCloseable registration = registerTestBackend(GlobMatcher.INSTANCE)) {
      assertThat(testPathExpressions.matchFileType("md")).isEqualTo("**.md");
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = TestCodeOwnerBackend.ID)
  public void matchFileType_backendWithUnknownMatcher() throws Exception {
    PathExpressionMatcher pathExpressionMatcher =
        new PathExpressionMatcher() {
          @Override
          public boolean matches(String pathExpression, Path relativePath) {
            return false;
          }
        };
    try (AutoCloseable registration = registerTestBackend(pathExpressionMatcher)) {
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> testPathExpressions.matchFileType("md"));
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo(
              String.format(
                  "path expression matcher %s not supported",
                  pathExpressionMatcher.getClass().getName()));
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = TestCodeOwnerBackend.ID)
  public void matchFileType_backendWithoutMatcher() throws Exception {
    TestCodeOwnerBackend testCodeOwnerBackend =
        new TestCodeOwnerBackend(/* pathExpressionMatcher= */ null);
    try (AutoCloseable registration = registerTestBackend(testCodeOwnerBackend)) {
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> testPathExpressions.matchFileType("md"));
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo(
              String.format(
                  "code owner backend %s doesn't support path expressions",
                  testCodeOwnerBackend.getClass().getName()));
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = "find-owners")
  public void matchAllFilesInSubfolder_findOwnersBackend() throws Exception {
    assertThat(testPathExpressions.matchAllFilesInSubfolder("foo")).isEqualTo("foo/**");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = "proto")
  public void matchAllFilesInSubfolder_protoBackend() throws Exception {
    assertThat(testPathExpressions.matchAllFilesInSubfolder("foo")).isEqualTo("foo/...");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = TestCodeOwnerBackend.ID)
  public void matchAllFilesInSubfolder_backendWithGlobMatcher() throws Exception {
    try (AutoCloseable registration = registerTestBackend(GlobMatcher.INSTANCE)) {
      assertThat(testPathExpressions.matchAllFilesInSubfolder("foo")).isEqualTo("foo/**");
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = TestCodeOwnerBackend.ID)
  public void matchAllFilesInSubfolder_backendWithUnknownMatcher() throws Exception {
    PathExpressionMatcher pathExpressionMatcher =
        new PathExpressionMatcher() {
          @Override
          public boolean matches(String pathExpression, Path relativePath) {
            return false;
          }
        };
    try (AutoCloseable registration = registerTestBackend(pathExpressionMatcher)) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> testPathExpressions.matchAllFilesInSubfolder("foo"));
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo(
              String.format(
                  "path expression matcher %s not supported",
                  pathExpressionMatcher.getClass().getName()));
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = TestCodeOwnerBackend.ID)
  public void matchAllFilesInSubfolder_backendWithoutMatcher() throws Exception {
    TestCodeOwnerBackend testCodeOwnerBackend =
        new TestCodeOwnerBackend(/* pathExpressionMatcher= */ null);
    try (AutoCloseable registration = registerTestBackend(testCodeOwnerBackend)) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> testPathExpressions.matchAllFilesInSubfolder("foo"));
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo(
              String.format(
                  "code owner backend %s doesn't support path expressions",
                  testCodeOwnerBackend.getClass().getName()));
    }
  }

  private AutoCloseable registerTestBackend(PathExpressionMatcher pathExpressionMatcher) {
    return registerTestBackend(new TestCodeOwnerBackend(pathExpressionMatcher));
  }

  private AutoCloseable registerTestBackend(CodeOwnerBackend codeOwnerBackend) {
    RegistrationHandle registrationHandle =
        ((PrivateInternals_DynamicMapImpl<CodeOwnerBackend>) codeOwnerBackends)
            .put("gerrit", TestCodeOwnerBackend.ID, Providers.of(codeOwnerBackend));
    return registrationHandle::remove;
  }

  private static class TestCodeOwnerBackend implements CodeOwnerBackend {
    static final String ID = "test-backend";

    @Nullable private final PathExpressionMatcher pathExpressionMatcher;

    public TestCodeOwnerBackend(@Nullable PathExpressionMatcher pathExpressionMatcher) {
      this.pathExpressionMatcher = pathExpressionMatcher;
    }

    @Override
    public boolean isCodeOwnerConfigFile(NameKey project, String fileName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<CodeOwnerConfig> getCodeOwnerConfig(
        CodeOwnerConfig.Key codeOwnerConfigKey, ObjectId revision) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Path getFilePath(CodeOwnerConfig.Key codeOwnerConfigKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<CodeOwnerConfig> upsertCodeOwnerConfig(
        CodeOwnerConfig.Key codeOwnerConfigKey,
        CodeOwnerConfigUpdate codeOwnerConfigUpdate,
        IdentifiedUser currentUser) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<PathExpressionMatcher> getPathExpressionMatcher(BranchNameKey branchNameKey) {
      return Optional.ofNullable(pathExpressionMatcher);
    }
  }
}
