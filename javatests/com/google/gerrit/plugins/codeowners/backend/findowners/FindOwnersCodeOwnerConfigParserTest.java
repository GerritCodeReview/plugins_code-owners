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
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.plugins.codeowners.backend.AbstractCodeOwnerConfigParserTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportMode;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParseException;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigReferenceSubject;
import com.google.gerrit.plugins.codeowners.testing.CodeOwnerSetSubject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
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

    codeOwnerConfig
        .imports()
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
                      "%s %s%s%s\n",
                      keyword,
                      codeOwnerConfigReference
                          .project()
                          .map(Project.NameKey::get)
                          .map(projectName -> projectName + ":")
                          .orElse(""),
                      codeOwnerConfigReference.branch().map(branch -> branch + ":").orElse(""),
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
      for (CodeOwnerConfigReference codeOwnerConfigReference : codeOwnerSet.imports()) {
        b.append(
            String.format(
                "per-file %s=file: %s%s%s\n",
                codeOwnerSet.pathExpressions().stream().sorted().collect(joining(",")),
                codeOwnerConfigReference
                    .project()
                    .map(Project.NameKey::get)
                    .map(projectName -> projectName + ":")
                    .orElse(""),
                codeOwnerConfigReference.branch().map(branch -> branch + ":").orElse(""),
                codeOwnerConfigReference.filePath()));
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
  public void cannotParseCodeOwnerConfigWithInvalidEmails() throws Exception {
    CodeOwnerConfigParseException exception =
        assertThrows(
            CodeOwnerConfigParseException.class,
            () ->
                codeOwnerConfigParser.parse(
                    TEST_REVISION,
                    CodeOwnerConfig.Key.create(project, "master", "/"),
                    getCodeOwnerConfig(
                        EMAIL_1, "@example.com", "admin@", "admin@example@com", EMAIL_2)));
    assertThat(exception.getFullMessage(FindOwnersBackend.CODE_OWNER_CONFIG_FILE_NAME))
        .isEqualTo(
            String.format(
                "invalid code owner config file '/OWNERS' (project = %s, branch = master):\n"
                    + "  invalid line: @example.com\n"
                    + "  invalid line: admin@\n"
                    + "  invalid line: admin@example@com",
                project));
  }

  @Test
  public void cannotParseCodeOwnerConfigWithInvalidLines() throws Exception {
    CodeOwnerConfigParseException exception =
        assertThrows(
            CodeOwnerConfigParseException.class,
            () ->
                codeOwnerConfigParser.parse(
                    TEST_REVISION,
                    CodeOwnerConfig.Key.create(project, "master", "/"),
                    getCodeOwnerConfig(EMAIL_1, "INVALID", "NOT_AN_EMAIL", EMAIL_2)));
    assertThat(exception.getFullMessage(FindOwnersBackend.CODE_OWNER_CONFIG_FILE_NAME))
        .isEqualTo(
            String.format(
                "invalid code owner config file '/OWNERS' (project = %s, branch = master):\n"
                    + "  invalid line: INVALID\n"
                    + "  invalid line: NOT_AN_EMAIL",
                project));
  }

  @Test
  public void codeOwnerConfigWithComment() throws Exception {
    assertParseAndFormat(
        getCodeOwnerConfig(EMAIL_1, EMAIL_2 + " # some comment", EMAIL_3),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnerSetsThat()
                .onlyElement()
                .hasCodeOwnersEmailsThat()
                .containsExactly(EMAIL_1, EMAIL_2, EMAIL_3),
        // inline comments are dropped
        getCodeOwnerConfig(EMAIL_1, EMAIL_2, EMAIL_3));
  }

  @Test
  public void perFileCodeOwnerConfigWithComment() throws Exception {
    assertParseAndFormat(
        "per-file foo=" + EMAIL_1 + "," + EMAIL_2 + "," + EMAIL_3 + " # some comment",
        codeOwnerConfig -> {
          CodeOwnerSetSubject codeOwnerSetSubject =
              assertThat(codeOwnerConfig).hasCodeOwnerSetsThat().onlyElement();
          codeOwnerSetSubject.hasPathExpressionsThat().containsExactly("foo");
          codeOwnerSetSubject.hasCodeOwnersEmailsThat().containsExactly(EMAIL_1, EMAIL_2, EMAIL_3);
        },
        // inline comments are dropped
        getCodeOwnerConfig(
            /* ignoreParentCodeOwners= */ false,
            CodeOwnerSet.builder()
                .addPathExpression("foo")
                .addCodeOwnerEmail(EMAIL_1)
                .addCodeOwnerEmail(EMAIL_2)
                .addCodeOwnerEmail(EMAIL_3)
                .build()));
  }

  @Test
  public void setNoParentWithComment() throws Exception {
    assertParseAndFormat(
        "set noparent # some comment",
        codeOwnerConfig -> {
          assertThat(codeOwnerConfig).hasIgnoreParentCodeOwnersThat().isTrue();
          assertThat(codeOwnerConfig).hasCodeOwnerSetsThat().isEmpty();
        },
        // inline comments are dropped
        getCodeOwnerConfig(
            CodeOwnerConfig.builder(
                    CodeOwnerConfig.Key.create(project, "master", "/"), TEST_REVISION)
                .setIgnoreParentCodeOwners()
                .build()));
  }

  @Test
  public void importCodeOwnerConfigWithComment() throws Exception {
    Path path = Paths.get("/foo/bar/OWNERS");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(CodeOwnerConfigImportMode.ALL, path).build();
    assertParseAndFormat(
        "include " + path + " # some comment",
        codeOwnerConfig -> {
          CodeOwnerConfigReferenceSubject codeOwnerConfigReferenceSubject =
              assertThat(codeOwnerConfig).hasImportsThat().onlyElement();
          codeOwnerConfigReferenceSubject.hasProjectThat().isEmpty();
          codeOwnerConfigReferenceSubject.hasBranchThat().isEmpty();
          codeOwnerConfigReferenceSubject.hasFilePathThat().isEqualTo(path);
        },
        // inline comments are dropped
        getCodeOwnerConfig(codeOwnerConfigReference));
  }

  @Test
  public void codeOwnerConfigWithAnnotations() throws Exception {
    assertParseAndFormat(
        getCodeOwnerConfig(
            EMAIL_1,
            EMAIL_2 + " #{FOO_BAR}#{BAR_BAZ} #NO_ANNOTATION, #{FOO} #{bar} #{bAz} other comment",
            EMAIL_3),
        codeOwnerConfig -> {
          CodeOwnerSetSubject codeOwnerSetSubject =
              assertThat(codeOwnerConfig).hasCodeOwnerSetsThat().onlyElement();
          codeOwnerSetSubject.hasCodeOwnersEmailsThat().containsExactly(EMAIL_1, EMAIL_2, EMAIL_3);
          codeOwnerSetSubject
              .hasAnnotationsThat()
              .containsExactly(EMAIL_2, ImmutableSet.of("FOO_BAR", "BAR_BAZ", "FOO", "bar", "bAz"));
        },
        // annotations are sorted alphabetically, the normal comment is dropped
        EMAIL_1
            + "\n"
            + EMAIL_2
            + " #{BAR_BAZ} #{FOO} #{FOO_BAR} #{bAz} #{bar}\n"
            + EMAIL_3
            + "\n");
  }

  @Test
  public void codeOwnerConfigWithAnnotationsOnAllUsersWildcard() throws Exception {
    assertParseAndFormat(
        getCodeOwnerConfig(
            "* #{FOO_BAR}#{BAR_BAZ} #NO_ANNOTATION, #{FOO} #{bar} #{bAz} other comment"),
        codeOwnerConfig -> {
          CodeOwnerSetSubject codeOwnerSetSubject =
              assertThat(codeOwnerConfig).hasCodeOwnerSetsThat().onlyElement();
          codeOwnerSetSubject.hasCodeOwnersEmailsThat().containsExactly("*");
          codeOwnerSetSubject
              .hasAnnotationsThat()
              .containsExactly("*", ImmutableSet.of("FOO_BAR", "BAR_BAZ", "FOO", "bar", "bAz"));
        },
        // annotations are sorted alphabetically, the normal comment is dropped
        "* #{BAR_BAZ} #{FOO} #{FOO_BAR} #{bAz} #{bar}\n");
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
  public void importCodeOwnerConfigFromSameProjectAndBranch() throws Exception {
    Path path = Paths.get("/foo/bar/OWNERS");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(CodeOwnerConfigImportMode.ALL, path).build();
    assertParseAndFormat(
        getCodeOwnerConfig(codeOwnerConfigReference),
        codeOwnerConfig -> {
          CodeOwnerConfigReferenceSubject codeOwnerConfigReferenceSubject =
              assertThat(codeOwnerConfig).hasImportsThat().onlyElement();
          codeOwnerConfigReferenceSubject.hasProjectThat().isEmpty();
          codeOwnerConfigReferenceSubject.hasBranchThat().isEmpty();
          codeOwnerConfigReferenceSubject.hasFilePathThat().isEqualTo(path);
        });
  }

  @Test
  public void importCodeOwnerConfigFromOtherProject() throws Exception {
    String otherProject = "otherProject";
    Path path = Paths.get("/foo/bar/OWNERS");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(CodeOwnerConfigImportMode.ALL, path)
            .setProject(Project.nameKey(otherProject))
            .build();
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
  public void cannotFormatCodeOwnerConfigWithImportThatSpecifiesBranchWithoutProject()
      throws Exception {
    Path path = Paths.get("/foo/bar/OWNERS");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(CodeOwnerConfigImportMode.ALL, path)
            .setBranch("refs/heads/foo")
            .build();
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(
                CodeOwnerConfig.Key.create(Project.nameKey("project"), "master", "/"),
                TEST_REVISION)
            .addImport(codeOwnerConfigReference)
            .build();
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> codeOwnerConfigParser.formatAsString(codeOwnerConfig));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "project is required if branch is specified: %s", codeOwnerConfigReference));
  }

  @Test
  public void importCodeOwnerConfigFromOtherBranch_fullName() throws Exception {
    testImportCodeOwnerConfigFromOtherBranch("refs/heads/foo");
  }

  @Test
  public void importCodeOwnerConfigFromOtherBranch_shortName() throws Exception {
    testImportCodeOwnerConfigFromOtherBranch("foo");
  }

  private void testImportCodeOwnerConfigFromOtherBranch(String branchName) throws Exception {
    Path path = Paths.get("/foo/bar/OWNERS");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(CodeOwnerConfigImportMode.ALL, path)
            .setProject(project)
            .setBranch(branchName)
            .build();
    assertParseAndFormat(
        getCodeOwnerConfig(codeOwnerConfigReference),
        codeOwnerConfig -> {
          CodeOwnerConfigReferenceSubject codeOwnerConfigReferenceSubject =
              assertThat(codeOwnerConfig).hasImportsThat().onlyElement();
          codeOwnerConfigReferenceSubject.hasProjectThat().value().isEqualTo(project.get());
          codeOwnerConfigReferenceSubject
              .hasBranchThat()
              .value()
              .isEqualTo(RefNames.fullName(branchName));
          codeOwnerConfigReferenceSubject.hasFilePathThat().isEqualTo(path);
        });
  }

  @Test
  public void importCodeOwnerConfigWithImportModeAll() throws Exception {
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(CodeOwnerConfigImportMode.ALL, "/foo/bar/OWNERS").build();
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
  public void importCodeOwnerConfigWithImportModeGlobalCodeOwnerSetsOnly() throws Exception {
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/foo/bar/OWNERS")
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

  @Test
  public void importMultipleCodeOwnerConfigs() throws Exception {
    Path path1 = Paths.get("/foo/bar/OWNERS");
    CodeOwnerConfigReference codeOwnerConfigReference1 =
        CodeOwnerConfigReference.builder(CodeOwnerConfigImportMode.ALL, path1).build();
    Path path2 = Paths.get("/foo/baz/OWNERS");
    CodeOwnerConfigReference codeOwnerConfigReference2 =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, path2)
            .build();
    assertParseAndFormat(
        getCodeOwnerConfig(codeOwnerConfigReference1, codeOwnerConfigReference2),
        codeOwnerConfig -> {
          assertThat(codeOwnerConfig).hasImportsThat().hasSize(2);
          assertThat(codeOwnerConfig)
              .hasImportsThat()
              .element(0)
              .hasFilePathThat()
              .isEqualTo(path1);
          assertThat(codeOwnerConfig)
              .hasImportsThat()
              .element(1)
              .hasFilePathThat()
              .isEqualTo(path2);
        });
  }

  @Test
  public void perFileCodeOwnerConfigWithAnnotations() throws Exception {
    assertParseAndFormat(
        "per-file foo="
            + EMAIL_1
            + ","
            + EMAIL_2
            + ","
            + EMAIL_3
            + " #{FOO_BAR}#{BAR_BAZ} #NO_ANNOTATION, #{FOO} #{bar} #{bAz} other comment",
        codeOwnerConfig -> {
          CodeOwnerSetSubject codeOwnerSetSubject =
              assertThat(codeOwnerConfig).hasCodeOwnerSetsThat().onlyElement();
          codeOwnerSetSubject.hasPathExpressionsThat().containsExactly("foo");
          codeOwnerSetSubject.hasCodeOwnersEmailsThat().containsExactly(EMAIL_1, EMAIL_2, EMAIL_3);
          codeOwnerSetSubject
              .hasAnnotationsThat()
              .containsExactly(
                  EMAIL_1,
                  ImmutableSet.of("FOO_BAR", "BAR_BAZ", "FOO", "bar", "bAz"),
                  EMAIL_2,
                  ImmutableSet.of("FOO_BAR", "BAR_BAZ", "FOO", "bar", "bAz"),
                  EMAIL_3,
                  ImmutableSet.of("FOO_BAR", "BAR_BAZ", "FOO", "bar", "bAz"));
        },
        // annotations are sorted alphabetically, the normal comment is dropped, a newline is added
        "per-file foo="
            + EMAIL_1
            + ","
            + EMAIL_2
            + ","
            + EMAIL_3
            + " #{BAR_BAZ} #{FOO} #{FOO_BAR} #{bAz} #{bar}\n");
  }

  @Test
  public void perFileCodeOwnerConfigImportFromSameProjectAndBranch() throws Exception {
    Path path = Paths.get("/foo/bar/OWNERS");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, path)
            .build();
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"), TEST_REVISION)
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("foo")
                    .addImport(codeOwnerConfigReference)
                    .build())
            .build();

    assertParseAndFormat(
        getCodeOwnerConfig(codeOwnerConfig),
        parsedCodeOwnerConfig -> {
          CodeOwnerSetSubject codeOwnerSetSubject =
              assertThat(parsedCodeOwnerConfig).hasCodeOwnerSetsThat().onlyElement();
          codeOwnerSetSubject.hasPathExpressionsThat().containsExactly("foo");
          CodeOwnerConfigReferenceSubject codeOwnerConfigReferenceSubject =
              codeOwnerSetSubject.hasImportsThat().onlyElement();
          codeOwnerConfigReferenceSubject.hasProjectThat().isEmpty();
          codeOwnerConfigReferenceSubject.hasBranchThat().isEmpty();
          codeOwnerConfigReferenceSubject.hasFilePathThat().isEqualTo(path);
        });
  }

  @Test
  public void perFileCodeOwnerConfigImportFromOtherProject() throws Exception {
    String otherProject = "otherProject";
    Path path = Paths.get("/foo/bar/OWNERS");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, path)
            .setProject(Project.nameKey(otherProject))
            .build();
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"), TEST_REVISION)
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("foo")
                    .addImport(codeOwnerConfigReference)
                    .build())
            .build();

    assertParseAndFormat(
        getCodeOwnerConfig(codeOwnerConfig),
        parsedCodeOwnerConfig -> {
          CodeOwnerSetSubject codeOwnerSetSubject =
              assertThat(parsedCodeOwnerConfig).hasCodeOwnerSetsThat().onlyElement();
          codeOwnerSetSubject.hasPathExpressionsThat().containsExactly("foo");
          CodeOwnerConfigReferenceSubject codeOwnerConfigReferenceSubject =
              codeOwnerSetSubject.hasImportsThat().onlyElement();
          codeOwnerConfigReferenceSubject.hasProjectThat().value().isEqualTo(otherProject);
          codeOwnerConfigReferenceSubject.hasFilePathThat().isEqualTo(path);
        });
  }

  @Test
  public void cannotFormatCodeOwnerConfigWithPerFileImportThatSpecifiesBranchWithoutProject()
      throws Exception {
    Path path = Paths.get("/foo/bar/OWNERS");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, path)
            .setBranch("refs/heads/foo")
            .build();
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(
                CodeOwnerConfig.Key.create(Project.nameKey("project"), "master", "/"),
                TEST_REVISION)
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("foo")
                    .addImport(codeOwnerConfigReference)
                    .build())
            .build();
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> codeOwnerConfigParser.formatAsString(codeOwnerConfig));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "project is required if branch is specified: %s", codeOwnerConfigReference));
  }

  @Test
  public void perFileImportOfCodeOwnerConfigFromOtherBranch_fullName() throws Exception {
    testPerFileImportOfCodeOwnerConfigFromOtherBranch("refs/heads/foo");
  }

  @Test
  public void perFileImportOfCodeOwnerConfigFromOtherBranch_shortName() throws Exception {
    testPerFileImportOfCodeOwnerConfigFromOtherBranch("foo");
  }

  private void testPerFileImportOfCodeOwnerConfigFromOtherBranch(String branchName)
      throws Exception {
    Path path = Paths.get("/foo/bar/OWNERS");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, path)
            .setProject(project)
            .setBranch(branchName)
            .build();
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(
                CodeOwnerConfig.Key.create(Project.nameKey("project"), "master", "/"),
                TEST_REVISION)
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("foo")
                    .addImport(codeOwnerConfigReference)
                    .build())
            .build();
    assertParseAndFormat(
        getCodeOwnerConfig(codeOwnerConfig),
        parsedCodeOwnerConfig -> {
          CodeOwnerSetSubject codeOwnerSetSubject =
              assertThat(parsedCodeOwnerConfig).hasCodeOwnerSetsThat().onlyElement();
          codeOwnerSetSubject.hasPathExpressionsThat().containsExactly("foo");
          CodeOwnerConfigReferenceSubject codeOwnerConfigReferenceSubject =
              codeOwnerSetSubject.hasImportsThat().onlyElement();
          codeOwnerConfigReferenceSubject.hasProjectThat().value().isEqualTo(project.get());
          codeOwnerConfigReferenceSubject
              .hasBranchThat()
              .value()
              .isEqualTo(RefNames.fullName(branchName));
          codeOwnerConfigReferenceSubject.hasFilePathThat().isEqualTo(path);
        });
  }

  @Test
  public void perFileCodeOwnerConfigImportCannotHaveImportModeAll() throws Exception {
    // The 'include' keyword is used to for imports with import mode ALL, but it is not supported
    // for per-file imports. Trying to use it anyway should result in a proper error message.
    String line = "per-file foo=include /foo/bar/OWNERS";
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                codeOwnerConfigParser.parse(
                    TEST_REVISION, CodeOwnerConfig.Key.create(project, "master", "/"), line));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "import mode %s is unsupported for per file import: %s",
                CodeOwnerConfigImportMode.ALL.name(), line));
  }

  @Test
  public void cannotParseCodeOwnerConfigWithPerFileLineThatHasAnInvalidImportKeyword()
      throws Exception {
    // The 'import' keyword doesn't exist
    String invalidLine = "per-file foo=import /foo/bar/OWNERS";

    CodeOwnerConfigParseException exception =
        assertThrows(
            CodeOwnerConfigParseException.class,
            () ->
                codeOwnerConfigParser.parse(
                    TEST_REVISION,
                    CodeOwnerConfig.Key.create(project, "master", "/"),
                    invalidLine));
    assertThat(exception.getFullMessage(FindOwnersBackend.CODE_OWNER_CONFIG_FILE_NAME))
        .isEqualTo(
            String.format(
                "invalid code owner config file '/OWNERS' (project = %s, branch = master):\n"
                    + "  invalid line: %s",
                project, invalidLine));
  }

  @Test
  public void perFileCodeOwnerConfigImportWithImportModeGlobalCodeOwnerSetsOnly() throws Exception {
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/foo/bar/OWNERS")
            .build();
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"), TEST_REVISION)
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("foo")
                    .addImport(codeOwnerConfigReference)
                    .build())
            .build();

    assertParseAndFormat(
        getCodeOwnerConfig(codeOwnerConfig),
        parsedCodeOwnerConfig -> {
          assertThat(parsedCodeOwnerConfig)
              .hasCodeOwnerSetsThat()
              .onlyElement()
              .hasImportsThat()
              .onlyElement()
              .hasImportModeThat()
              .isEqualTo(CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY);
        });
  }

  @Test
  public void replaceEmail_contentCannotBeNull() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                FindOwnersCodeOwnerConfigParser.replaceEmail(
                    /* codeOwnerConfigFileContent= */ null, admin.email(), user.email()));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfigFileContent");
  }

  @Test
  public void replaceEmail_oldEmailCannotBeNull() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                FindOwnersCodeOwnerConfigParser.replaceEmail(
                    "content", /* oldEmail= */ null, user.email()));
    assertThat(npe).hasMessageThat().isEqualTo("oldEmail");
  }

  @Test
  public void replaceEmail_newEmailCannotBeNull() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                FindOwnersCodeOwnerConfigParser.replaceEmail(
                    "content", admin.email(), /* newEmail= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("newEmail");
  }

  @Test
  public void replaceEmail() throws Exception {
    String oldEmail = "old@example.com";
    String newEmail = "new@example.com";

    // In the following test lines '${email} is used as placefolder for the email that we expect to
    // be replaced.
    String[] testLines = {
      // line = email
      "${email}",
      // line = email with leading whitespace
      " ${email}",
      // line = email with trailing whitespace
      "${email} ",
      // line = email with leading and trailing whitespace
      " ${email} ",
      // line = email with comment
      "${email}# comment",
      // line = email with trailing whitespace and comment
      "${email} # comment",
      // line = email with leading whitespace and comment
      " ${email}# comment",
      // line = email with leading and trailing whitespace and comment
      " ${email} # comment",
      // line = email that ends with oldEmail
      "foo" + oldEmail,
      // line = email that starts with oldEmail
      oldEmail + "bar",
      // line = email that contains oldEmail
      "foo" + oldEmail + "bar",
      // line = email that contains oldEmail with leading and trailing whitespace
      " foo" + oldEmail + "bar ",
      // line = email that contains oldEmail with leading and trailing whitespace and comment
      " foo" + oldEmail + "bar # comment",
      // email in comment
      "foo@example.com # ${email}",
      // email in comment that contains oldEmail
      "foo@example.com # foo" + oldEmail + "bar",
      // per-file line with email
      "per-file *.md=${email}",
      // per-file line with email and whitespace
      "per-file *.md = ${email} ",
      // per-file line with multiple emails and old email at first position
      "per-file *.md=${email},foo@example.com,bar@example.com",
      // per-file line with multiple emails and old email at middle position
      "per-file *.md=foo@example.com,${email},bar@example.com",
      // per-file line with multiple emails and old email at last position
      "per-file *.md=foo@example.com,bar@example.com,${email}",
      // per-file line with multiple emails and old email at last position and comment
      "per-file *.md=foo@example.com,bar@example.com,${email}# comment",
      // per-file line with multiple emails and old email at last position and comment with
      // whitespace
      "per-file *.md = foo@example.com, bar@example.com , ${email} # comment",
      // per-file line with multiple emails and old email appearing multiple times
      "per-file *.md=${email},${email}",
      "per-file *.md=${email},${email},${email}",
      "per-file *.md=${email},foo@example.com,${email},bar@example.com,${email}",
      // per-file line with multiple emails and old email appearing multiple times and comment
      "per-file *.md=${email},foo@example.com,${email},bar@example.com,${email}# comment",
      // per-file line with multiple emails and old email appearing multiple times and comment and
      // whitespace
      "per-file *.md= ${email} , foo@example.com , ${email} , bar@example.com , ${email} # comment",
      // per-file line with email that contains old email
      "per-file *.md=for" + oldEmail + "bar",
      // per-file line with multiple emails and one email that contains the old email
      "per-file *.md=for" + oldEmail + "bar,${email}",
    };

    for (String testLine : testLines) {
      String content = testLine.replaceAll(Pattern.quote("${email}"), oldEmail);
      String expectedContent = testLine.replaceAll(Pattern.quote("${email}"), newEmail);
      assertWithMessage(testLine)
          .that(FindOwnersCodeOwnerConfigParser.replaceEmail(content, oldEmail, newEmail))
          .isEqualTo(expectedContent);
    }

    // join all test lines and replace email in all of them at once
    String testContent = Joiner.on("\n").join(testLines);
    String content = testContent.replaceAll(Pattern.quote("${email}"), oldEmail);
    String expectedContent = testContent.replaceAll(Pattern.quote("${email}"), newEmail);
    assertThat(FindOwnersCodeOwnerConfigParser.replaceEmail(content, oldEmail, newEmail))
        .isEqualTo(expectedContent);

    // test that trailing new line is preserved
    assertThat(FindOwnersCodeOwnerConfigParser.replaceEmail(content + "\n", oldEmail, newEmail))
        .isEqualTo(expectedContent + "\n");
  }

  @Test
  public void splitGlobs() throws Exception {
    // empty globs
    assertSplitGlobs("");
    assertSplitGlobs(",", "");

    // single globs
    assertSplitGlobs("BUILD", "BUILD");
    assertSplitGlobs("*.md", "*.md");
    assertSplitGlobs("foo/*", "foo/*");
    assertSplitGlobs("{foo,bar}", "{foo,bar}");
    assertSplitGlobs("{foo,bar}/**", "{foo,bar}/**");
    assertSplitGlobs("{{foo,bar}}", "{{foo,bar}}");
    assertSplitGlobs("foo[1-5]", "foo[1-5]");
    assertSplitGlobs("a[,]b", "a[,]b");
    assertSplitGlobs("a[[,]]b", "a[[,]]b");

    // multiple globs
    assertSplitGlobs("BUILD,*.md,foo/*", "BUILD", "*.md", "foo/*");
    assertSplitGlobs(
        "{foo,bar},{foo,bar}/**,{{foo,bar}}", "{foo,bar}", "{foo,bar}/**", "{{foo,bar}}");
    assertSplitGlobs("foo[1-5],a[,]b,a[[,]]b", "foo[1-5]", "a[,]b", "a[[,]]b");
    assertSplitGlobs("a[,]b,{foo,bar}", "a[,]b", "{foo,bar}");

    // invalid globs
    assertSplitGlobs("{foo,bar", "{foo,bar");
    assertSplitGlobs("[abc,", "[abc,");
    assertSplitGlobs("{foo,bar,a[,]b", "{foo,bar,a[,]b");
  }

  private static void assertSplitGlobs(String commaSeparatedGlobs, String... expectedGlobs) {
    assertThat(FindOwnersCodeOwnerConfigParser.Parser.splitGlobs(commaSeparatedGlobs))
        .asList()
        .containsExactlyElementsIn(expectedGlobs);
  }
}
