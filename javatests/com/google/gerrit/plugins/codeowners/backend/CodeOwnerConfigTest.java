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

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import java.nio.file.Paths;
import org.junit.Test;

/** Tests for {@link CodeOwnerConfig}. */
public class CodeOwnerConfigTest {
  @Test
  public void addCodeOwners() throws Exception {
    CodeOwnerReference codeOwner1 = CodeOwnerReference.create("jdoe@example.com");
    CodeOwnerReference codeOwner2 = CodeOwnerReference.create("jroe@example.com");
    CodeOwnerConfig.Builder codeOwnerConfigBuilder = createCodeOwnerBuilder();
    codeOwnerConfigBuilder.addCodeOwner(codeOwner1);
    codeOwnerConfigBuilder.addCodeOwner(codeOwner2);
    CodeOwnerConfig codeOwnerConfig = codeOwnerConfigBuilder.build();
    assertThat(codeOwnerConfig.codeOwners()).containsExactly(codeOwner1, codeOwner2);
  }

  @Test
  public void addDuplicateCodeOwners() throws Exception {
    CodeOwnerReference codeOwner = CodeOwnerReference.create("jdoe@example.com");
    CodeOwnerConfig.Builder codeOwnerConfigBuilder = createCodeOwnerBuilder();
    codeOwnerConfigBuilder.addCodeOwner(codeOwner);
    codeOwnerConfigBuilder.addCodeOwner(codeOwner);
    CodeOwnerConfig codeOwnerConfig = codeOwnerConfigBuilder.build();
    assertThat(codeOwnerConfig.codeOwners()).containsExactly(codeOwner);
  }

  @Test
  public void cannotAddNullAsaddCodeOwner() throws Exception {
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> createCodeOwnerBuilder().addCodeOwner(null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerReference");
  }

  private static CodeOwnerConfig.Builder createCodeOwnerBuilder() {
    return CodeOwnerConfig.builder(
        CodeOwnerConfig.Key.create(
            BranchNameKey.create(Project.nameKey("project"), "master"), Paths.get("/")));
  }
}
