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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Test;

/** Tests for {@link CodeOwnerConfigReference}. */
public class CodeOwnerConfigReferenceTest extends AbstractCodeOwnersTest {
  @Test
  public void branchCannotBeSetToNull() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                CodeOwnerConfigReference.builder(CodeOwnerConfigImportMode.ALL, "foo/OWNERS")
                    .setBranch((String) null));
    assertThat(npe).hasMessageThat().isEqualTo("branch");
  }

  @Test
  public void cannotCreateCodeOwnerConfigReferenceWithShortBranchName() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                CodeOwnerConfigReference.builder(CodeOwnerConfigImportMode.ALL, "foo/OWNERS")
                    .setBranch(Optional.of("master"))
                    .build());
    assertThat(exception).hasMessageThat().isEqualTo("branch must be full name: master");
  }

  @Test
  public void absoluteFilePathCanBeSpecifiedInDifferentFormats() throws Exception {
    Path expectedPath = Path.of("/foo/OWNERS");
    for (String inputPath : new String[] {"/foo/OWNERS", "//foo/OWNERS"}) {
      Path path =
          CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, inputPath).filePath();
      assertWithMessage(inputPath).that(path).isEqualTo(expectedPath);
      assertThat(path.isAbsolute()).isTrue();
    }
  }

  @Test
  public void relativeFilePathCanBeSpecified() throws Exception {
    Path path =
        CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "foo/OWNERS").filePath();
    assertThat(path).isEqualTo(Path.of("foo/OWNERS"));
    assertThat(path.isAbsolute()).isFalse();
  }
}
