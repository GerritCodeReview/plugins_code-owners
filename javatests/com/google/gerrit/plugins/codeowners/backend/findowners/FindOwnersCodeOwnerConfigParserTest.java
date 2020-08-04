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

package com.google.gerrit.plugins.codeowners.backend.findowners;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigSubject.assertThat;
import static java.util.stream.Collectors.joining;

import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.backend.AbstractCodeOwnerConfigParserTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportMode;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigReferenceSubject;
import com.google.gerrit.plugins.codeowners.testing.CodeOwnerSetSubject;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

/** Tests for {@link FindOwnersCodeOwnerConfigParser}. */
public class FindOwnersCodeOwnerConfigParserTest extends AbstractCodeOwnerConfigParserTest {
  @Override
  protected Class<? extends CodeOwnerConfigParser> getCodeOwnerConfigParserClass() {
    return FindOwnersCodeOwnerConfigParser.class;
  }

  @Override
  protected String getCodeOwnerConfig(CodeOwnerConfig codeOwnerConfig) {
    StringBuilder b = new StringBuilder();
    if (codeOwnerConfig.ignoreParentCodeOwners()) {
      b.append("set noparent\n");
    }

    codeOwnerConfig.imports().stream()
        .forEach(
            codeOwnerConfigReference -> {
              String keyword;
              if (codeOwnerConfigReference.importMode().equals(CodeOwnerConfigImportMode.ALL)) {
                keyword = "include";
              } else {
                keyword = "file:";
              }
              b.append(
                  String.format(
                      "%s %s%s",
                      keyword,
                      codeOwnerConfigReference
                          .project()
                          .map(Project.NameKey::get)
                          .map(projectName -> projectName + ":")
                          .orElse(""),
                      codeOwnerConfigReference.filePath()));
            });

    // global code owners
    for (String email :
        codeOwnerConfig.codeOwnerSets().stream()
            .filter(codeOwnerSet -> codeOwnerSet.pathExpressions().isEmpty())
            .flatMap(codeOwnerSet -> codeOwnerSet.codeOwners().stream())
            .map(CodeOwnerReference::email)
            .sorted()
            .distinct()
            .collect(toImmutableList())) {
      b.append(email).append('\n');
    }

    // per-file code owners
    for (CodeOwnerSet codeOwnerSet :
        codeOwnerConfig.codeOwnerSets().stream()
            .filter(codeOwnerSet -> !codeOwnerSet.pathExpressions().isEmpty())
            .collect(toImmutableList())) {
      if (codeOwnerSet.ignoreGlobalAndParentCodeOwners()) {
        b.append(
            String.format(
                "per-file %s=set noparent\n",
                codeOwnerSet.pathExpressions().stream().sorted().collect(joining(","))));
      }
      if (!codeOwnerSet.codeOwners().isEmpty()) {
        b.append(
            String.format(
                "per-file %s=%s\n",
                codeOwnerSet.pathExpressions().stream().sorted().collect(joining(",")),
                codeOwnerSet.codeOwners().stream()
                    .map(CodeOwnerReference::email)
                    .sorted()
                    .collect(joining(","))));
      }
    }

    return b.toString();
  }

  @Test
  public void codeOwnerConfigWithInvalidEmails_invalidEmailsAreIgnored() throws Exception {
    assertParseAndFormat(
        getCodeOwnerConfig(EMAIL_1, "@example.com", "admin@", "admin@example@com", EMAIL_2),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnerSetsThat()
                .onlyElement()
                .hasCodeOwnersEmailsThat()
                .containsExactly(EMAIL_1, EMAIL_2),
        getCodeOwnerConfig(EMAIL_1, EMAIL_2));
  }

  @Test
  public void codeOwnerConfigWithInvalidLines_invalidLinesAreIgnored() throws Exception {
    assertParseAndFormat(
        getCodeOwnerConfig(EMAIL_1, "INVALID", "NOT_AN_EMAIL", EMAIL_2),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnerSetsThat()
                .onlyElement()
                .hasCodeOwnersEmailsThat()
                .containsExactly(EMAIL_1, EMAIL_2),
        getCodeOwnerConfig(EMAIL_1, EMAIL_2));
  }

  @Test
  public void codeOwnerConfigWithInlineComments() throws Exception {
    assertParseAndFormat(
        getCodeOwnerConfig(EMAIL_1, EMAIL_2 + " # some comment", EMAIL_3),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnerSetsThat()
                .onlyElement()
                .hasCodeOwnersEmailsThat()
                .containsExactly(EMAIL_1, EMAIL_2, EMAIL_3),
        getCodeOwnerConfig(EMAIL_1, EMAIL_2, EMAIL_3));
  }

  @Test
  public void codeOwnerConfigWithNonSortedEmails() throws Exception {
    assertParseAndFormat(
        String.join("\n", EMAIL_3, EMAIL_2, EMAIL_1) + "\n",
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnerSetsThat()
                .onlyElement()
                .hasCodeOwnersEmailsThat()
                .containsExactly(EMAIL_1, EMAIL_2, EMAIL_3),
        getCodeOwnerConfig(EMAIL_1, EMAIL_2, EMAIL_3));
  }

  @Test
  public void setNoParentCanBeSetMultipleTimes() throws Exception {
    assertParseAndFormat(
        getCodeOwnerConfig(true, CodeOwnerSet.createWithoutPathExpressions(EMAIL_1))
            + "\nset noparent\nset noparent",
        codeOwnerConfig -> {
          assertThat(codeOwnerConfig).hasIgnoreParentCodeOwnersThat().isTrue();
          assertThat(codeOwnerConfig)
              .hasCodeOwnerSetsThat()
              .onlyElement()
              .hasCodeOwnersEmailsThat()
              .containsExactly(EMAIL_1);
        },
        getCodeOwnerConfig(true, CodeOwnerSet.createWithoutPathExpressions(EMAIL_1)));
  }

  @Test
  public void codeOwnerSetWithGlobalCodeOwnersIsReturnedFirst() throws Exception {
    CodeOwnerSet perFileCodeOwnerSet =
        CodeOwnerSet.builder().addPathExpression("foo").addCodeOwnerEmail(EMAIL_2).build();
    CodeOwnerSet globalCodeOwnerSet = CodeOwnerSet.createWithoutPathExpressions(EMAIL_1, EMAIL_3);
    assertParseAndFormat(
        getCodeOwnerConfig(false, perFileCodeOwnerSet, globalCodeOwnerSet),
        codeOwnerConfig -> {
          assertThat(codeOwnerConfig)
              .hasCodeOwnerSetsThat()
              .containsExactly(globalCodeOwnerSet, perFileCodeOwnerSet)
              .inOrder();
        },
        getCodeOwnerConfig(false, globalCodeOwnerSet, perFileCodeOwnerSet));
  }

  @Test
  public void setMultipleCodeOwnerSetsWithoutPathExpressions() throws Exception {
    CodeOwnerSet codeOwnerSet1 = CodeOwnerSet.createWithoutPathExpressions(EMAIL_1, EMAIL_3);
    CodeOwnerSet codeOwnerSet2 = CodeOwnerSet.createWithoutPathExpressions(EMAIL_2);
    assertParseAndFormat(
        getCodeOwnerConfig(false, codeOwnerSet1, codeOwnerSet2),
        codeOwnerConfig -> {
          assertThat(codeOwnerConfig)
              .hasCodeOwnerSetsThat()
              .onlyElement()
              .hasCodeOwnersEmailsThat()
              .containsExactly(EMAIL_1, EMAIL_2, EMAIL_3);
        },
        // The code owner sets without path expressions are merged into one code owner set.
        getCodeOwnerConfig(
            false, CodeOwnerSet.createWithoutPathExpressions(EMAIL_1, EMAIL_2, EMAIL_3)));
  }

  @Test
  public void setNoParentForPathExpressions() throws Exception {
    CodeOwnerSet codeOwnerSet =
        CodeOwnerSet.builder()
            .setIgnoreGlobalAndParentCodeOwners()
            .addPathExpression("*.md")
            .addPathExpression("foo")
            .addCodeOwnerEmail(EMAIL_1)
            .addCodeOwnerEmail(EMAIL_2)
            .build();
    assertParseAndFormat(
        getCodeOwnerConfig(false, codeOwnerSet),
        codeOwnerConfig -> {
          // we expect 2 code owner sets:
          // 1. code owner set for line "per-file *.md,foo=set noparent"
          // 2. code owner set for line "per-file *.md,foo=admin@example.com,jdoe@example.com"
          assertThat(codeOwnerConfig).hasCodeOwnerSetsThat().hasSize(2);

          // 1. assert code owner set for line "per-file *.md,foo=set noparent"
          CodeOwnerSetSubject codeOwnerSet1Subject =
              assertThat(codeOwnerConfig).hasCodeOwnerSetsThat().element(0);
          codeOwnerSet1Subject.hasIgnoreGlobalAndParentCodeOwnersThat().isTrue();
          codeOwnerSet1Subject.hasPathExpressionsThat().containsExactly("*.md", "foo");
          codeOwnerSet1Subject.hasCodeOwnersThat().isEmpty();

          // 2. assert code owner set for line "per-file
          // *.md,foo=admin@example.com,jdoe@example.com"
          CodeOwnerSetSubject codeOwnerSet2Subject =
              assertThat(codeOwnerConfig).hasCodeOwnerSetsThat().element(1);
          codeOwnerSet2Subject.hasIgnoreGlobalAndParentCodeOwnersThat().isFalse();
          codeOwnerSet2Subject.hasPathExpressionsThat().containsExactly("*.md", "foo");
          codeOwnerSet2Subject.hasCodeOwnersEmailsThat().containsExactly(EMAIL_1, EMAIL_2);
        });
  }

  @Test
  public void importCodeOwnerConfigFromSameProject() throws Exception {
    Path path = Paths.get("/foo/bar/OWNERS");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(path).build();
    assertParseAndFormat(
        getCodeOwnerConfig(codeOwnerConfigReference),
        codeOwnerConfig -> {
          CodeOwnerConfigReferenceSubject codeOwnerConfigReferenceSubject =
              assertThat(codeOwnerConfig).hasImportsThat().onlyElement();
          codeOwnerConfigReferenceSubject.hasProjectThat().isEmpty();
          codeOwnerConfigReferenceSubject.hasFilePathThat().isEqualTo(path);
        });
  }

  @Test
  public void importCodeOwnerConfigFromOtherProject() throws Exception {
    String otherProject = "otherProject";
    Path path = Paths.get("/foo/bar/OWNERS");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(path).setProject(Project.nameKey(otherProject)).build();
    assertParseAndFormat(
        getCodeOwnerConfig(codeOwnerConfigReference),
        codeOwnerConfig -> {
          CodeOwnerConfigReferenceSubject codeOwnerConfigReferenceSubject =
              assertThat(codeOwnerConfig).hasImportsThat().onlyElement();
          codeOwnerConfigReferenceSubject.hasProjectThat().value().isEqualTo(otherProject);
          codeOwnerConfigReferenceSubject.hasFilePathThat().isEqualTo(path);
        });
  }

  @Test
  public void importCodeOwnerConfigWithImportTypeAll() throws Exception {
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder("/foo/bar/OWNERS")
            .setImportMode(CodeOwnerConfigImportMode.ALL)
            .build();
    assertParseAndFormat(
        getCodeOwnerConfig(codeOwnerConfigReference),
        codeOwnerConfig -> {
          assertThat(codeOwnerConfig)
              .hasImportsThat()
              .onlyElement()
              .hasImportModeThat()
              .isEqualTo(CodeOwnerConfigImportMode.ALL);
        });
  }

  @Test
  public void importCodeOwnerConfigWithImportTypeGlobalCodeOwnerSetsOnly() throws Exception {
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder("/foo/bar/OWNERS")
            .setImportMode(CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY)
            .build();
    assertParseAndFormat(
        getCodeOwnerConfig(codeOwnerConfigReference),
        codeOwnerConfig -> {
          assertThat(codeOwnerConfig)
              .hasImportsThat()
              .onlyElement()
              .hasImportModeThat()
              .isEqualTo(CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY);
        });
  }
}
