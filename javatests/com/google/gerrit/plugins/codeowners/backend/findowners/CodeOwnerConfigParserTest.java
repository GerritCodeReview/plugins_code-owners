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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigSubject.assertThat;

import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerConfigParser}. */
public class CodeOwnerConfigParserTest extends AbstractCodeOwnersTest {
  /** Callback interface that allows to assert a code owner config. */
  @FunctionalInterface
  interface CodeOwnerConfigAsserter {
    void doAssert(CodeOwnerConfig codeOwnerConfig);
  }

  private CodeOwnerConfigParser codeOwnerConfigParser;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerConfigParser = plugin.getSysInjector().getInstance(CodeOwnerConfigParser.class);
  }

  @Test
  public void emptyCodeOwnerConfig() throws Exception {
    assertParseAndFormat(
        "", codeOwnerConfig -> assertThat(codeOwnerConfig).hasCodeOwnersThat().isEmpty());
  }

  @Test
  public void codeOwnerConfigWithOneEmail() throws Exception {
    assertParseAndFormat(
        "admin@test.com",
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly("admin@test.com"));
  }

  @Test
  public void codeOwnerConfigWithMultipleEmails() throws Exception {
    assertParseAndFormat(
        "admin@test.com\njdoe@test.com\njroe@test.com",
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly("admin@test.com", "jdoe@test.com", "jroe@test.com"));
  }

  @Test
  public void codeOwnerConfigWithTrailingLineBreak() throws Exception {
    assertParseAndFormat(
        "admin@test.com\njdoe@test.com\njroe@test.com\n",
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly("admin@test.com", "jdoe@test.com", "jroe@test.com"),
        "admin@test.com\njdoe@test.com\njroe@test.com");
  }

  @Test
  public void codeOwnerConfigWithWindowsLineBreaks() throws Exception {
    assertParseAndFormat(
        "admin@test.com\r\njdoe@test.com\r\njroe@test.com",
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly("admin@test.com", "jdoe@test.com", "jroe@test.com"),
        "admin@test.com\njdoe@test.com\njroe@test.com");
  }

  @Test
  public void codeOwnerConfigWithEmptyLines() throws Exception {
    assertParseAndFormat(
        "\nadmin@test.com\n\nuser@test.com",
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly("admin@test.com", "user@test.com"),
        "admin@test.com\nuser@test.com");
  }

  @Test
  public void codeOwnerConfigWithWhitespace() throws Exception {
    assertParseAndFormat(
        "\t admin@test.com\t \n \tuser@test.com\t ",
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly("admin@test.com", "user@test.com"),
        "admin@test.com\nuser@test.com");
  }

  @Test
  public void codeOwnerConfigWithDuplicateEmails() throws Exception {
    assertParseAndFormat(
        "admin@test.com\nadmin@test.com\nuser@test.com\nadmin@test.com",
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly("admin@test.com", "user@test.com"),
        "admin@test.com\nuser@test.com");
  }

  @Test
  public void codeOwnerConfigWithNonSortedEmails() throws Exception {
    assertParseAndFormat(
        "user@test.com\nadmin@test.com",
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly("admin@test.com", "user@test.com"),
        "admin@test.com\nuser@test.com");
  }

  @Test
  public void codeOwnerConfigWithInvalidEmails_InvalidEmailsAreIgnored() throws Exception {
    assertParseAndFormat(
        "admin@test.com\n@test.com\nadmin@\nadmin@test@com\nuser@test.com",
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly("admin@test.com", "user@test.com"),
        "admin@test.com\nuser@test.com");
  }

  @Test
  public void codeOwnerConfigWithInvalidLines_InvalidLinesAreIgnored() throws Exception {
    assertParseAndFormat(
        "admin@test.com\nINVALID\nNOT_AN_EMAIL\nuser@test.com",
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly("admin@test.com", "user@test.com"),
        "admin@test.com\nuser@test.com");
  }

  @Test
  public void codeOwnerConfigWithCommentLines_CommentLinesAreIgnored() throws Exception {
    assertParseAndFormat(
        "# a@test.com\nadmin@test.com\n # b@test.com\nuser@test.com\n#c@test.com",
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly("admin@test.com", "user@test.com"),
        "admin@test.com\nuser@test.com");
  }

  @Test
  public void
      codeOwnerConfigWithInlineComments_LinesWithInlineCommentsAreConsideredAsInvalidAndAreIgnored()
          throws Exception {
    assertParseAndFormat(
        "foo.bar@test.com # Foo Bar\nadmin@test.com\nuser@test.com",
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly("admin@test.com", "user@test.com"),
        "admin@test.com\nuser@test.com");
  }

  /**
   * Parses the given code owner config, asserts the parsed code owner config, formats the parsed
   * code owner configuration back into a string and asserts the result of the formatting.
   *
   * @param codeOwnerConfig the code owner config that should be parsed and that is expected as
   *     result of formatting the parsed code owner config
   * @param codeOwnerConfigAsserter asserter that asserts the parsed code owner config
   */
  private void assertParseAndFormat(
      String codeOwnerConfig, CodeOwnerConfigAsserter codeOwnerConfigAsserter) {
    assertParseAndFormat(codeOwnerConfig, codeOwnerConfigAsserter, codeOwnerConfig);
  }

  /**
   * Parses the given code owner config, asserts the parsed code owner config, formats the parsed
   * code owner configuration back into a string and asserts the result of the formatting.
   *
   * @param codeOwnerConfig the code owner config that should be parsed
   * @param codeOwnerConfigAsserter asserter that asserts the parsed code owner config
   * @param expectedConfig the code owner config that is expected as result of formatting the parsed
   *     code owner config
   */
  private void assertParseAndFormat(
      String codeOwnerConfig,
      CodeOwnerConfigAsserter codeOwnerConfigAsserter,
      String expectedConfig) {
    // Parse the provided code owner config.
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(Project.nameKey("project"), "master", "/");
    CodeOwnerConfig parsedCodeOwnerConfig =
        codeOwnerConfigParser.parse(codeOwnerConfigKey, codeOwnerConfig);

    // Assert the parsed code owner config.
    codeOwnerConfigAsserter.doAssert(parsedCodeOwnerConfig);

    // Format the parsed code owner config and assert the formatted code owner config.
    assertThat(codeOwnerConfigParser.format(parsedCodeOwnerConfig)).isEqualTo(expectedConfig);
  }
}
