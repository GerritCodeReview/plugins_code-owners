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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import java.io.IOException;
import java.util.stream.StreamSupport;
import org.junit.Before;
import org.junit.Test;

/**
 * Base class for testing {@link CodeOwnerConfigParser}s.
 *
 * <p>All {@link CodeOwnersBackend}s implement {@link CodeOwnerConfigParser} that need to be tested.
 * To avoid code duplication for these tests, the common parts are implemented in this class.
 */
public abstract class AbstractCodeOwnerConfigParserTest extends AbstractCodeOwnersTest {
  /** Callback interface that allows to assert a code owner config. */
  @FunctionalInterface
  protected interface CodeOwnerConfigAsserter {
    void doAssert(CodeOwnerConfig codeOwnerConfig);
  }

  // Emails for testing, sorted alphabetically.
  protected static final String EMAIL_1 = "admin@test.com";
  protected static final String EMAIL_2 = "jdoe@test.com";
  protected static final String EMAIL_3 = "jroe@test.com";

  protected CodeOwnerConfigParser codeOwnerConfigParser;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerConfigParser = plugin.getSysInjector().getInstance(getCodeOwnerConfigParserClass());
  }

  /** Must return the {@link CodeOwnerConfigParser} class that should be tested. */
  protected abstract Class<? extends CodeOwnerConfigParser> getCodeOwnerConfigParserClass();

  /**
   * Must return the expected code owner config string for a code owner config with the given
   * parameters.
   *
   * <p>The order of the emails must be preserved in the code owner config string.
   *
   * @param ignoreParentCodeOwners whether code owners from parent code owner configs (code owner
   *     configs in parent folders) should be ignored
   * @param emails emails that should be assigned as code owners
   * @return the expected code owner config string for a code owner config with the given parameters
   */
  protected abstract String getCodeOwnerConfig(boolean ignoreParentCodeOwners, String... emails);

  /**
   * Returns the expected code owner config string for a code owner config with the given emails as
   * code owners.
   *
   * @param emails emails that should be assigned as code owners
   * @return the expected code owner config string for a code owner config with the given emails as
   *     code owners
   */
  protected String getCodeOwnerConfig(String... emails) {
    return getCodeOwnerConfig(false, emails);
  }

  @Test
  public void cannotParseIfCodeOwnerConfigKeyIsNull() throws Exception {
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> codeOwnerConfigParser.parse(null, ""));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfigKey");
  }

  @Test
  public void cannotFormatNullCodeOwnerConfig() throws Exception {
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> codeOwnerConfigParser.formatAsString(null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfig");
  }

  @Test
  public void emptyCodeOwnerConfig() throws Exception {
    assertParseAndFormat(
        "", codeOwnerConfig -> assertThat(codeOwnerConfig).hasCodeOwnersThat().isEmpty());
  }

  @Test
  public void nullCodeOwnerConfig() throws Exception {
    assertParseAndFormat(
        null, codeOwnerConfig -> assertThat(codeOwnerConfig).hasCodeOwnersThat().isEmpty(), "");
  }

  @Test
  public void codeOwnerConfigWithOneEmail() throws Exception {
    assertParseAndFormat(
        getCodeOwnerConfig(EMAIL_1),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1));
  }

  @Test
  public void codeOwnerConfigWithMultipleEmails() throws Exception {
    assertParseAndFormat(
        getCodeOwnerConfig(EMAIL_1, EMAIL_2, EMAIL_3),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly(EMAIL_1, EMAIL_2, EMAIL_3));
  }

  @Test
  public void codeOwnerConfigWithTrailingLineBreak() throws Exception {
    assertParseAndFormat(
        getCodeOwnerConfig(EMAIL_1, EMAIL_2, EMAIL_3) + "\n",
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly(EMAIL_1, EMAIL_2, EMAIL_3),
        getCodeOwnerConfig(EMAIL_1, EMAIL_2, EMAIL_3));
  }

  @Test
  public void codeOwnerConfigWithWindowsLineBreaks() throws Exception {
    assertParseAndFormat(
        getCodeOwnerConfig(EMAIL_1, EMAIL_2, EMAIL_3).replaceAll("\n", "\r\n"),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly(EMAIL_1, EMAIL_2, EMAIL_3),
        getCodeOwnerConfig(EMAIL_1, EMAIL_2, EMAIL_3));
  }

  @Test
  public void codeOwnerConfigWithEmptyLines() throws Exception {
    assertParseAndFormat(
        modifyLines(getCodeOwnerConfig(EMAIL_1, EMAIL_2, EMAIL_3), line -> line + "\n"),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly(EMAIL_1, EMAIL_2, EMAIL_3),
        getCodeOwnerConfig(EMAIL_1, EMAIL_2, EMAIL_3));
  }

  @Test
  public void codeOwnerConfigWithWhitespace() throws Exception {
    assertParseAndFormat(
        modifyLines(getCodeOwnerConfig(EMAIL_1, EMAIL_2, EMAIL_3), line -> " \t" + line + " \t"),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly(EMAIL_1, EMAIL_2, EMAIL_3),
        getCodeOwnerConfig(EMAIL_1, EMAIL_2, EMAIL_3));
  }

  @Test
  public void codeOwnerConfigWithDuplicateEmails() throws Exception {
    assertParseAndFormat(
        getCodeOwnerConfig(EMAIL_1, EMAIL_2, EMAIL_1),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1, EMAIL_2),
        getCodeOwnerConfig(EMAIL_1, EMAIL_2));
  }

  @Test
  public void codeOwnerConfigWithNonSortedEmails() throws Exception {
    assertParseAndFormat(
        getCodeOwnerConfig(EMAIL_3, EMAIL_2, EMAIL_1),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly(EMAIL_1, EMAIL_2, EMAIL_3),
        getCodeOwnerConfig(EMAIL_1, EMAIL_2, EMAIL_3));
  }

  /**
   * Parses the given code owner config, asserts the parsed code owner config, formats the parsed
   * code owner configuration back into a string and asserts the result of the formatting.
   *
   * @param codeOwnerConfig the code owner config that should be parsed and that is expected as
   *     result of formatting the parsed code owner config
   * @param codeOwnerConfigAsserter asserter that asserts the parsed code owner config
   */
  protected void assertParseAndFormat(
      String codeOwnerConfig, CodeOwnerConfigAsserter codeOwnerConfigAsserter) throws IOException {
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
  protected void assertParseAndFormat(
      String codeOwnerConfig,
      CodeOwnerConfigAsserter codeOwnerConfigAsserter,
      String expectedConfig)
      throws IOException {
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

  @Test
  public void codeOwnerConfigWithCommentLines_CommentLinesAreIgnored() throws Exception {
    assertParseAndFormat(
        modifyLines(
            getCodeOwnerConfig(EMAIL_1, EMAIL_2, EMAIL_3), line -> line + "\n# foo.bar@test.com"),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly(EMAIL_1, EMAIL_2, EMAIL_3),
        getCodeOwnerConfig(EMAIL_1, EMAIL_2, EMAIL_3));
  }

  @Test
  public void codeOwnerConfigWithoutIgnoreParentCodeOwners() throws Exception {
    assertParseAndFormat(
        getCodeOwnerConfig(EMAIL_1),
        codeOwnerConfig -> {
          assertThat(codeOwnerConfig).hasIgnoreParentCodeOwnersThat().isFalse();
          assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1);
        },
        getCodeOwnerConfig(EMAIL_1));
  }

  @Test
  public void codeOwnerConfigWithIgnoreParentCodeOwners() throws Exception {
    assertParseAndFormat(
        getCodeOwnerConfig(true, EMAIL_1),
        codeOwnerConfig -> {
          assertThat(codeOwnerConfig).hasIgnoreParentCodeOwnersThat().isTrue();
          assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1);
        },
        getCodeOwnerConfig(true, EMAIL_1));
  }

  @Test
  public void codeOwnerConfigWithOnlyIgnoreParentCodeOwners() throws Exception {
    assertParseAndFormat(
        getCodeOwnerConfig(true),
        codeOwnerConfig -> {
          assertThat(codeOwnerConfig).hasIgnoreParentCodeOwnersThat().isTrue();
          assertThat(codeOwnerConfig).hasCodeOwnersThat().isEmpty();
        },
        getCodeOwnerConfig(true));
  }

  private static String modifyLines(
      String codeOwnerConfigAsString, Function<String, String> lineModifier) {
    return StreamSupport.stream(
            Splitter.on('\n').split(codeOwnerConfigAsString).spliterator(), false)
        .map(lineModifier::apply)
        .collect(joining("\n"));
  }
}
