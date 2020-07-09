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

package com.google.gerrit.plugins.codeowners;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

/** Tests for {@link JgitPath}. */
public class JgitPathTest extends AbstractCodeOwnersTest {
  @Test
  public void getJgitPathOfStringPath() throws Exception {
    assertThat(JgitPath.of("foo/bar/OWNERS").get()).isEqualTo("foo/bar/OWNERS");
    assertThat(JgitPath.of("/foo/bar/OWNERS").get()).isEqualTo("foo/bar/OWNERS");
  }

  @Test
  public void cannotGetJgitPathOfNullStringPath() throws Exception {
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> JgitPath.of((String) null));
    assertThat(npe).hasMessageThat().isEqualTo("path");
  }

  @Test
  public void getJgitPathOfPath() throws Exception {
    assertThat(JgitPath.of(Paths.get("foo/bar/OWNERS")).get()).isEqualTo("foo/bar/OWNERS");
    assertThat(JgitPath.of(Paths.get("/foo/bar/OWNERS")).get()).isEqualTo("foo/bar/OWNERS");
  }

  @Test
  public void cannotGetJgitPathOfNullPath() throws Exception {
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> JgitPath.of((Path) null));
    assertThat(npe).hasMessageThat().isEqualTo("path");
  }

  @Test
  public void getPathAsAbsolutePath() throws Exception {
    assertThat(JgitPath.of("foo/bar/OWNERS").getAsAbsolutePath())
        .isEqualTo(Paths.get("/foo/bar/OWNERS"));
  }
}
