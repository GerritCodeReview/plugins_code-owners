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

package com.google.gerrit.plugins.codeowners.backend;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import com.google.gerrit.server.IdentifiedUser;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

/** Tests for {@link com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackendId}. */
public class CodeOwnerBackendIdTest extends AbstractCodeOwnersTest {
  @Test
  public void getBackendIdForCodeOwnersBackendClass() throws Exception {
    assertThat(CodeOwnerBackendId.getBackendId(FindOwnersBackend.class))
        .isEqualTo(FindOwnersBackend.ID);
    assertThat(CodeOwnerBackendId.getBackendId(ProtoBackend.class)).isEqualTo(ProtoBackend.ID);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> CodeOwnerBackendId.getBackendId(TestCodeOwnerBackend.class));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format("unknown code owner backend: %s", TestCodeOwnerBackend.class.getName()));
  }

  private static class TestCodeOwnerBackend implements CodeOwnerBackend {
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
    public boolean isCodeOwnerConfigFile(NameKey project, String fileName) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Path getFilePath(CodeOwnerConfig.Key codeOwnerConfigKey) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<PathExpressionMatcher> getPathExpressionMatcher(BranchNameKey branchNameKey) {
      return Optional.empty();
    }
  }
}
