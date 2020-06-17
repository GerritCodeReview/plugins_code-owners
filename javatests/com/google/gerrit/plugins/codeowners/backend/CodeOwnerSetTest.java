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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerSetSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import java.nio.file.Paths;
import org.junit.Test;

/** Tests for {@link CodeOwnerSet}. */
public class CodeOwnerSetTest {
  @Test
  public void addCodeOwners() throws Exception {
    CodeOwnerReference codeOwner1 = CodeOwnerReference.create("jdoe@example.com");
    CodeOwnerReference codeOwner2 = CodeOwnerReference.create("jroe@example.com");
    CodeOwnerSet codeOwnerSet =
        CodeOwnerSet.builder().addCodeOwner(codeOwner1).addCodeOwner(codeOwner2).build();
    assertThat(codeOwnerSet).hasCodeOwnersThat().containsExactly(codeOwner1, codeOwner2);
  }

  @Test
  public void addDuplicateCodeOwners() throws Exception {
    CodeOwnerReference codeOwner = CodeOwnerReference.create("jdoe@example.com");
    CodeOwnerSet codeOwnerSet =
        CodeOwnerSet.builder().addCodeOwner(codeOwner).addCodeOwner(codeOwner).build();
    assertThat(codeOwnerSet).hasCodeOwnersThat().containsExactly(codeOwner);
  }

  @Test
  public void cannotAddNullAsCodeOwner() throws Exception {
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> CodeOwnerSet.builder().addCodeOwner(null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerReference");
  }

  @Test
  public void addCodeOwnersByEmail() throws Exception {
    String codeOwnerEmail1 = "jdoe@example.com";
    String codeOwnerEmail2 = "jroe@example.com";
    CodeOwnerSet codeOwnerSet =
        CodeOwnerSet.builder()
            .addCodeOwnerEmail(codeOwnerEmail1)
            .addCodeOwnerEmail(codeOwnerEmail2)
            .build();
    assertThat(codeOwnerSet)
        .hasCodeOwnersEmailsThat()
        .containsExactly(codeOwnerEmail1, codeOwnerEmail2);
  }

  @Test
  public void addDuplicateCodeOwnersByEmail() throws Exception {
    String codeOwnerEmail = "jdoe@example.com";
    CodeOwnerSet codeOwnerSet =
        CodeOwnerSet.builder()
            .addCodeOwnerEmail(codeOwnerEmail)
            .addCodeOwnerEmail(codeOwnerEmail)
            .build();
    assertThat(codeOwnerSet).hasCodeOwnersEmailsThat().containsExactly(codeOwnerEmail);
  }

  @Test
  public void cannotAddNullAsCodeOwnerEmail() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> CodeOwnerSet.builder().addCodeOwnerEmail(null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerEmail");
  }

  @Test
  public void cannotMatchNullPath() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> CodeOwnerSet.createForEmails("foo.bar@test.com").matches(null));
    assertThat(npe).hasMessageThat().isEqualTo("path");
  }

  @Test
  public void cannotMatchNullAbsolutePath() throws Exception {
    String absolutePath = "/foo/bar/baz.md";
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                CodeOwnerSet.createForEmails("foo.bar@test.com").matches(Paths.get(absolutePath)));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("path %s must be relative", absolutePath));
  }
}
