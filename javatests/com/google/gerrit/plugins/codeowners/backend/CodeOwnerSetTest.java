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

import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import org.junit.Test;

/** Tests for {@link CodeOwnerSet}. */
public class CodeOwnerSetTest extends AbstractCodeOwnersTest {
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
        assertThrows(
            NullPointerException.class,
            () -> CodeOwnerSet.builder().addCodeOwner(/* codeOwnerReference= */ null));
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
            NullPointerException.class,
            () -> CodeOwnerSet.builder().addCodeOwnerEmail(/* email= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerEmail");
  }

  @Test
  public void addPathExpressions() throws Exception {
    String pathExpression1 = "*.md";
    String pathExpression2 = "config/*";
    CodeOwnerSet codeOwnerSet =
        CodeOwnerSet.builder()
            .addPathExpression(pathExpression1)
            .addPathExpression(pathExpression2)
            .build();
    assertThat(codeOwnerSet)
        .hasPathExpressionsThat()
        .containsExactly(pathExpression1, pathExpression2);
  }

  @Test
  public void addDuplicatePathExpressions() throws Exception {
    String pathExpression = "*.md";
    CodeOwnerSet codeOwnerSet =
        CodeOwnerSet.builder()
            .addPathExpression(pathExpression)
            .addPathExpression(pathExpression)
            .build();
    assertThat(codeOwnerSet).hasPathExpressionsThat().containsExactly(pathExpression);
  }

  @Test
  public void cannotAddNullAsPathExpression() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> CodeOwnerSet.builder().addPathExpression(/* pathExpression= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pathExpression");
  }

  @Test
  public void cannotCreateCodeOwnerSetThatIgnoresParentCodeOwnersAndHasNoPathExpressions()
      throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> CodeOwnerSet.builder().setIgnoreGlobalAndParentCodeOwners().build());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "ignoreGlobalAndParentCodeOwners = true is not allowed for code owner set without"
                + " path expressions");
  }

  @Test
  public void canCreateCodeOwnerSetThatIgnoresParentCodeOwnersIfPathExpressionHaveBeenSet()
      throws Exception {
    CodeOwnerSet codeOwnerSet =
        CodeOwnerSet.builder()
            .setIgnoreGlobalAndParentCodeOwners()
            .addPathExpression("*.md")
            .build();
    assertThat(codeOwnerSet).hasIgnoreGlobalAndParentCodeOwnersThat().isTrue();
    assertThat(codeOwnerSet).hasPathExpressionsThat().isNotEmpty();
  }

  @Test
  public void cannotCreateCodeOwnerSetWithImportsIfNoPathExpressionsHaveBeenSet() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                CodeOwnerSet.builder()
                    .addImport(
                        CodeOwnerConfigReference.create(
                            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                            "foo/bar/OWNERS"))
                    .build());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("imports are not allowed for code owner set without path expressions");
  }

  @Test
  public void cannotCreateCodeOwnerSetWithImportsThatUseNonGlobalCodeOwnerSetsOnlyImportMode()
      throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                CodeOwnerSet.builder()
                    .addImport(
                        CodeOwnerConfigReference.create(
                            CodeOwnerConfigImportMode.ALL, "foo/bar/OWNERS"))
                    .addPathExpression("*.md")
                    .build());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "imports in code owner set must have have import mode %s",
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY.name()));
  }

  @Test
  public void canCreateCodeOwnerSetWithImports() throws Exception {
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "foo/bar/OWNERS");
    CodeOwnerSet codeOwnerSet =
        CodeOwnerSet.builder()
            .addImport(codeOwnerConfigReference)
            .addPathExpression("*.md")
            .build();
    assertThat(codeOwnerSet).hasImportsThat().containsExactly(codeOwnerConfigReference);
    assertThat(codeOwnerSet).hasPathExpressionsThat().isNotEmpty();
  }
}
