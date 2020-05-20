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

import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigSubject.assertThat;

import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import org.junit.Test;

/** Tests for {@link CodeOwnerConfigParser}. */
public class CodeOwnerConfigParserTest {
  // Emails for testing, sorted alphabetically.
  private static final String EMAIL_1 = "admin@test.com";
  private static final String EMAIL_2 = "jdoe@test.com";
  private static final String EMAIL_3 = "jroe@test.com";

  @Test
  public void emptyCodeOwnerConfig() throws Exception {
    CodeOwnerConfig codeOwnerConfig = parse("");
    assertThat(codeOwnerConfig).hasCodeOwnersThat().isEmpty();
  }

  @Test
  public void codeOwnerConfigWithOneEmail() throws Exception {
    String email = "admin@test.com";
    CodeOwnerConfig codeOwnerConfig = parse(email);
    assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(email);
  }

  @Test
  public void codeOwnerConfigWithMultipleEmails() throws Exception {
    CodeOwnerConfig codeOwnerConfig = parse(String.format("%s\n%s\n%s", EMAIL_1, EMAIL_2, EMAIL_3));
    assertThat(codeOwnerConfig)
        .hasCodeOwnersEmailsThat()
        .containsExactly(EMAIL_1, EMAIL_2, EMAIL_3);
  }

  @Test
  public void codeOwnerConfigWithTrailingLineBreak() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        parse(String.format("%s\n%s\n%s\n", EMAIL_1, EMAIL_2, EMAIL_3));
    assertThat(codeOwnerConfig)
        .hasCodeOwnersEmailsThat()
        .containsExactly(EMAIL_1, EMAIL_2, EMAIL_3);
  }

  @Test
  public void codeOwnerConfigWithWindowsLineBreaks() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        parse(String.format("%s\r\n%s\r\n%s", EMAIL_1, EMAIL_2, EMAIL_3));
    assertThat(codeOwnerConfig)
        .hasCodeOwnersEmailsThat()
        .containsExactly(EMAIL_1, EMAIL_2, EMAIL_3);
  }

  @Test
  public void codeOwnerConfigWithEmptyLines() throws Exception {
    CodeOwnerConfig codeOwnerConfig = parse(String.format("\n%s\n\n%s", EMAIL_1, EMAIL_2));
    assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1, EMAIL_2);
  }

  @Test
  public void codeOwnerConfigWithWhitespace() throws Exception {
    CodeOwnerConfig codeOwnerConfig = parse(String.format("\t %s\t \n \t%s\t ", EMAIL_1, EMAIL_2));
    assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1, EMAIL_2);
  }

  @Test
  public void codeOwnerConfigWithDuplicateEmails() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        parse(String.format("%s\n%s\n%s\n%s", EMAIL_1, EMAIL_1, EMAIL_2, EMAIL_1));
    assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1, EMAIL_2);
  }

  @Test
  public void codeOwnerConfigWithNonSortedEmails() throws Exception {
    CodeOwnerConfig codeOwnerConfig = parse(String.format("%s\n%s", EMAIL_2, EMAIL_1));
    assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1, EMAIL_2);
  }

  // TODO: add tests with invalid emails, e.g. "@test.com", "admin@", "admin@test@com".

  @Test
  public void codeOwnerConfigWithInvalidLines_InvalidLinesAreIgnored() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        parse(String.format("%s\nINVALID\nNOT_AN_EMAIL\n%s", EMAIL_1, EMAIL_2));
    assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1, EMAIL_2);
  }

  /**
   * Parses the given code owner config.
   *
   * @param codeOwnerConfig the code owner config that should be parsed
   * @return the parsed code owner config
   */
  private CodeOwnerConfig parse(String codeOwnerConfig) {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(Project.nameKey("project"), "master", "/");
    return CodeOwnerConfigParser.parse(codeOwnerConfigKey, codeOwnerConfig);
  }
}
