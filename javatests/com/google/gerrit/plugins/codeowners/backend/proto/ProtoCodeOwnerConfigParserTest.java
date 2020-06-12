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

package com.google.gerrit.plugins.codeowners.backend.proto;

import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.backend.AbstractCodeOwnerConfigParserTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParser;
import com.google.protobuf.TextFormat;
import org.junit.Test;

/** Tests for {@link ProtoCodeOwnerConfigParser}. */
public class ProtoCodeOwnerConfigParserTest extends AbstractCodeOwnerConfigParserTest {
  @Override
  protected Class<? extends CodeOwnerConfigParser> getCodeOwnerConfigParserClass() {
    return ProtoCodeOwnerConfigParser.class;
  }

  @Override
  protected String getEmptyCodeOwnerConfig() {
    return "owners_config {\n}\n";
  }

  @Override
  protected String getCodeOwnerConfig(String... emails) {
    StringBuilder b = new StringBuilder();
    b.append("owners_config {\n  owner_sets {\n");
    for (String email : emails) {
      b.append(String.format("    owners {\n      email: \"%s\"\n    }\n", email));
    }
    b.append("  }\n}\n");
    return b.toString();
  }

  @Test
  public void codeOwnerConfigWithInvalidEmails() throws Exception {
    assertParseAndFormat(
        getCodeOwnerConfig("@test.com", "admin@", EMAIL_1, "admin@test@com", EMAIL_2),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig)
                .hasCodeOwnersEmailsThat()
                .containsExactly("@test.com", "admin@", EMAIL_1, "admin@test@com", EMAIL_2),
        getCodeOwnerConfig("@test.com", "admin@", EMAIL_1, "admin@test@com", EMAIL_2));
  }

  @Test
  public void cannotParseCodeOwnerConfigWithInvalidLines() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(Project.nameKey("project"), "master", "/");
    assertThrows(
        TextFormat.ParseException.class,
        () ->
            codeOwnerConfigParser.parse(
                codeOwnerConfigKey, "owners_config {\n  owner_sets {\nINVALID_LINE\n  }\n}\n"));
  }

  @Test
  public void codeOwnerConfigWithInlineComments() throws Exception {
    assertParseAndFormat(
        String.format(
            "owners_config {\n  owner_sets {\n    owners {\n      email: \"%s\" # comment\n    }\n  }\n}\n",
            EMAIL_1),
        codeOwnerConfig ->
            assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(EMAIL_1),
        getCodeOwnerConfig(EMAIL_1));
  }
}
