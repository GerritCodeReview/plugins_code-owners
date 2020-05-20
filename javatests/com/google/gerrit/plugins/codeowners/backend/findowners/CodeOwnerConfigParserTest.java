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

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerConfigParser}. */
@TestPlugin(name = "code-owners", sysModule = "com.google.gerrit.plugins.codeowners.Module")
public class CodeOwnerConfigParserTest extends LightweightPluginDaemonTest {
  private CodeOwnerConfigParser codeOwnerConfigParser;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerConfigParser = plugin.getSysInjector().getInstance(CodeOwnerConfigParser.class);
  }

  @Test
  public void emptyCodeOwnerConfig() throws Exception {
    CodeOwnerConfig codeOwnerConfig = parse("");
    assertThat(codeOwnerConfig).hasCodeOwnersThat().isEmpty();
  }

  @Test
  public void codeOwnerConfigWithOneEmail() throws Exception {
    CodeOwnerConfig codeOwnerConfig = parse("admin@test.com");
    assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly("admin@test.com");
  }

  @Test
  public void codeOwnerConfigWithMultipleEmails() throws Exception {
    CodeOwnerConfig codeOwnerConfig = parse("admin@test.com\njdoe@test.com\njroe@test.com");
    assertThat(codeOwnerConfig)
        .hasCodeOwnersEmailsThat()
        .containsExactly("admin@test.com", "jdoe@test.com", "jroe@test.com");
  }

  @Test
  public void codeOwnerConfigWithTrailingLineBreak() throws Exception {
    CodeOwnerConfig codeOwnerConfig = parse("admin@test.com\njdoe@test.com\njroe@test.com\n");
    assertThat(codeOwnerConfig)
        .hasCodeOwnersEmailsThat()
        .containsExactly("admin@test.com", "jdoe@test.com", "jroe@test.com");
  }

  @Test
  public void codeOwnerConfigWithWindowsLineBreaks() throws Exception {
    CodeOwnerConfig codeOwnerConfig = parse("admin@test.com\r\njdoe@test.com\r\njroe@test.com");
    assertThat(codeOwnerConfig)
        .hasCodeOwnersEmailsThat()
        .containsExactly("admin@test.com", "jdoe@test.com", "jroe@test.com");
  }

  @Test
  public void codeOwnerConfigWithEmptyLines() throws Exception {
    CodeOwnerConfig codeOwnerConfig = parse("\nadmin@test.com\n\nuser@test.com");
    assertThat(codeOwnerConfig)
        .hasCodeOwnersEmailsThat()
        .containsExactly("admin@test.com", "user@test.com");
  }

  @Test
  public void codeOwnerConfigWithWhitespace() throws Exception {
    CodeOwnerConfig codeOwnerConfig = parse("\t admin@test.com\t \n \tuser@test.com\t ");
    assertThat(codeOwnerConfig)
        .hasCodeOwnersEmailsThat()
        .containsExactly("admin@test.com", "user@test.com");
  }

  @Test
  public void codeOwnerConfigWithDuplicateEmails() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        parse("admin@test.com\nadmin@test.com\nuser@test.com\nadmin@test.com");
    assertThat(codeOwnerConfig)
        .hasCodeOwnersEmailsThat()
        .containsExactly("admin@test.com", "user@test.com");
  }

  @Test
  public void codeOwnerConfigWithNonSortedEmails() throws Exception {
    CodeOwnerConfig codeOwnerConfig = parse("user@test.com\nadmin@test.com");
    assertThat(codeOwnerConfig)
        .hasCodeOwnersEmailsThat()
        .containsExactly("admin@test.com", "user@test.com");
  }

  @Test
  public void codeOwnerConfigWithInvalidEmails_InvalidEmailsAreIgnored() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        parse("admin@test.com\n@test.com\nadmin@\nadmin@test@com\nuser@test.com");
    assertThat(codeOwnerConfig)
        .hasCodeOwnersEmailsThat()
        .containsExactly("admin@test.com", "user@test.com");
  }

  @Test
  public void codeOwnerConfigWithInvalidLines_InvalidLinesAreIgnored() throws Exception {
    CodeOwnerConfig codeOwnerConfig = parse("admin@test.com\nINVALID\nNOT_AN_EMAIL\nuser@test.com");
    assertThat(codeOwnerConfig)
        .hasCodeOwnersEmailsThat()
        .containsExactly("admin@test.com", "user@test.com");
  }

  @Test
  public void codeOwnerConfigWithCommentLines_CommentLinesAreIgnored() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        parse("# a@test.com\nadmin@test.com\n # b@test.com\nuser@test.com\n#c@test.com");
    assertThat(codeOwnerConfig)
        .hasCodeOwnersEmailsThat()
        .containsExactly("admin@test.com", "user@test.com");
  }

  @Test
  public void
      codeOwnerConfigWithInlineComments_LinesWithInlineCommentsAreConsideredAsInvalidAndAreIgnored()
          throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        parse("foo.bar@test.com # Foo Bar\nadmin@test.com\nuser@test.com");
    assertThat(codeOwnerConfig)
        .hasCodeOwnersEmailsThat()
        .containsExactly("admin@test.com", "user@test.com");
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
    return codeOwnerConfigParser.parse(codeOwnerConfigKey, codeOwnerConfig);
  }
}
