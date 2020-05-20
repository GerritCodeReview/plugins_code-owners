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

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerConfigParser}. */
@TestPlugin(name = "code-owners", sysModule = "com.google.gerrit.plugins.codeowners.Module")
public class CodeOwnerConfigParserTest extends LightweightPluginDaemonTest {
  /** Callback interface that allows to assert a code owner config. */
  @FunctionalInterface
  interface CodeOwnerConfigAsserter {
    void doAssert(CodeOwnerConfig codeOwnerConfig);
  }

  // Emails for testing, sorted alphabetically.
  private static final String EMAIL_1 = "admin@test.com";
  private static final String EMAIL_2 = "jdoe@test.com";
  private static final String EMAIL_3 = "jroe@test.com";

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
        EMAIL_1,
        codeOwnerConfig ->
            assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1));
  }

  @Test
  public void codeOwnerConfigWithMultipleEmails() throws Exception {
    assertParseAndFormat(
        String.format("%s\n%s\n%s", EMAIL_1, EMAIL_2, EMAIL_3),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly(EMAIL_1, EMAIL_2, EMAIL_3));
  }

  @Test
  public void codeOwnerConfigWithTrailingLineBreak() throws Exception {
    assertParseAndFormat(
        String.format("%s\n%s\n%s\n", EMAIL_1, EMAIL_2, EMAIL_3),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly(EMAIL_1, EMAIL_2, EMAIL_3),
        String.format("%s\n%s\n%s", EMAIL_1, EMAIL_2, EMAIL_3));
  }

  @Test
  public void codeOwnerConfigWithWindowsLineBreaks() throws Exception {
    assertParseAndFormat(
        String.format("%s\r\n%s\r\n%s", EMAIL_1, EMAIL_2, EMAIL_3),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly(EMAIL_1, EMAIL_2, EMAIL_3),
        String.format("%s\n%s\n%s", EMAIL_1, EMAIL_2, EMAIL_3));
  }

  @Test
  public void codeOwnerConfigWithEmptyLines() throws Exception {
    assertParseAndFormat(
        String.format("\n%s\n\n%s", EMAIL_1, EMAIL_2),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1, EMAIL_2),
        String.format("%s\n%s", EMAIL_1, EMAIL_2));
  }

  @Test
  public void codeOwnerConfigWithWhitespace() throws Exception {
    assertParseAndFormat(
        String.format("\t %s\t \n \t%s\t ", EMAIL_1, EMAIL_2),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1, EMAIL_2),
        String.format("%s\n%s", EMAIL_1, EMAIL_2));
  }

  @Test
  public void codeOwnerConfigWithDuplicateEmails() throws Exception {
    assertParseAndFormat(
        String.format("%s\n%s\n%s\n%s", EMAIL_1, EMAIL_1, EMAIL_2, EMAIL_1),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1, EMAIL_2),
        String.format("%s\n%s", EMAIL_1, EMAIL_2));
  }

  @Test
  public void codeOwnerConfigWithNonSortedEmails() throws Exception {
    assertParseAndFormat(
        String.format("%s\n%s", EMAIL_2, EMAIL_1),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1, EMAIL_2),
        String.format("%s\n%s", EMAIL_1, EMAIL_2));
  }

  @Test
  public void codeOwnerConfigWithInvalidEmails_InvalidEmailsAreIgnored() throws Exception {
    assertParseAndFormat(
        String.format("%s\n@test.com\nadmin@\nadmin@test@com\n%s", EMAIL_1, EMAIL_2),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1, EMAIL_2),
        String.format("%s\n%s", EMAIL_1, EMAIL_2));
  }

  @Test
  public void codeOwnerConfigWithInvalidLines_InvalidLinesAreIgnored() throws Exception {
    assertParseAndFormat(
        String.format("%s\nINVALID\nNOT_AN_EMAIL\n%s", EMAIL_1, EMAIL_2),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1, EMAIL_2),
        String.format("%s\n%s", EMAIL_1, EMAIL_2));
  }

  @Test
  public void codeOwnerConfigWithCommentLines_CommentLinesAreIgnored() throws Exception {
    assertParseAndFormat(
        String.format("# a@test.com\n%s\n # b@test.com\n%s\n#c@test.com", EMAIL_1, EMAIL_2),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1, EMAIL_2),
        String.format("%s\n%s", EMAIL_1, EMAIL_2));
  }

  @Test
  public void
      codeOwnerConfigWithInlineComments_LinesWithInlineCommentsAreConsideredAsInvalidAndAreIgnored()
          throws Exception {
    assertParseAndFormat(
        String.format("foo.bar@test.com # Foo Bar\n%s\n%s", EMAIL_1, EMAIL_2),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1, EMAIL_2),
        String.format("%s\n%s", EMAIL_1, EMAIL_2));
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
    assertThat(codeOwnerConfigParser.formatAsString(parsedCodeOwnerConfig))
        .isEqualTo(expectedConfig);
  }
}
