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

package com.google.gerrit.plugins.codeowners.acceptance.api;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import org.junit.Test;

/**
 * Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerConfigFiles} REST endpoint.
 */
public class GetCodeOwnerConfigFilesIT extends AbstractCodeOwnersIT {
  @Test
  public void noCodeOwnerConfigFiles() throws Exception {
    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .branch("master")
                .codeOwnerConfigFiles()
                .paths())
        .isEmpty();
  }

  @Test
  public void defaultCodeOwnerConfigFileIsSkipped() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(RefNames.REFS_CONFIG)
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .branch("master")
                .codeOwnerConfigFiles()
                .paths())
        .isEmpty();
  }

  @Test
  public void getCodeOwnerConfigFiles() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(user.email())
            .create();

    CodeOwnerConfig.Key codeOwnerConfigKey2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key codeOwnerConfigKey3 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail(user.email())
            .create();

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .branch("master")
                .codeOwnerConfigFiles()
                .paths())
        .containsExactly(
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey1).getFilePath(),
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey2).getFilePath(),
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey3).getFilePath())
        .inOrder();
  }

  @Test
  public void getCodeOwnerConfigFilesWithPrefixes() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .fileName("prefix_" + getCodeOwnerConfigFileName())
            .folderPath("/")
            .addCodeOwnerEmail(user.email())
            .create();

    CodeOwnerConfig.Key codeOwnerConfigKey2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .fileName("prefix_" + getCodeOwnerConfigFileName())
            .addCodeOwnerEmail(admin.email())
            .create();

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .branch("master")
                .codeOwnerConfigFiles()
                .paths())
        .containsExactly(
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey2).getFilePath(),
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey1).getFilePath())
        .inOrder();
  }

  @Test
  public void getCodeOwnerConfigFilesWithExtensions() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .fileName(getCodeOwnerConfigFileName() + "_extension")
            .folderPath("/")
            .addCodeOwnerEmail(user.email())
            .create();

    CodeOwnerConfig.Key codeOwnerConfigKey2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .fileName(getCodeOwnerConfigFileName() + "_extension")
            .addCodeOwnerEmail(admin.email())
            .create();

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .branch("master")
                .codeOwnerConfigFiles()
                .paths())
        .containsExactly(
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey1).getFilePath(),
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey2).getFilePath())
        .inOrder();
  }

  @Test
  public void nonCodeOwnerConfigFilesAreNotReturned() throws Exception {
    // create a code owner config file with a name that is not recognized as code owner config file
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .fileName("non-code-owner-config")
        .addCodeOwnerEmail(admin.email())
        .create();

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .branch("master")
                .codeOwnerConfigFiles()
                .paths())
        .isEmpty();
  }

  @Test
  public void getCodeOwnerConfigFilesIfInvalidCodeOwnerConfigFilesExist() throws Exception {
    createNonParseableCodeOwnerConfig(getCodeOwnerConfigFileName());

    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addCodeOwnerEmail(admin.email())
            .create();

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .branch("master")
                .codeOwnerConfigFiles()
                .paths())
        .containsExactly(
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath());
  }

  @Test
  public void includeInvalidCodeOwnerConfigFiles() throws Exception {
    String nameOfInvalidCodeOwnerConfigFile = getCodeOwnerConfigFileName();
    createNonParseableCodeOwnerConfig(nameOfInvalidCodeOwnerConfigFile);

    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addCodeOwnerEmail(admin.email())
            .create();

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .branch("master")
                .codeOwnerConfigFiles()
                .includeNonParsableFiles(true)
                .paths())
        .containsExactly(
            JgitPath.of(nameOfInvalidCodeOwnerConfigFile).getAsAbsolutePath().toString(),
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath());
  }

  @Test
  public void includeNonParsableFilesAndEmailOptionsAreMutuallyExclusive() throws Exception {
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                projectCodeOwnersApiFactory
                    .project(project)
                    .branch("master")
                    .codeOwnerConfigFiles()
                    .includeNonParsableFiles(true)
                    .withEmail(admin.email())
                    .paths());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("the options 'email' and 'include-non-parsable-files' are mutually exclusive");
  }

  @Test
  public void filterByEmail() throws Exception {
    TestAccount user2 = accountCreator.user2();
    TestAccount user3 = accountCreator.create("user3", "user3@example.com", "User3", null);

    CodeOwnerConfig.Key codeOwnerConfigKey1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(user.email())
            .create();

    CodeOwnerConfig.Key codeOwnerConfigKey2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key codeOwnerConfigKey3 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression("foo")
                    .addCodeOwnerEmail(user.email())
                    .build())
            .create();

    CodeOwnerConfig.Key codeOwnerConfigKey4 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/baz/")
            .addCodeOwnerEmail(admin.email())
            .addCodeOwnerEmail(user.email())
            .create();

    CodeOwnerConfig.Key codeOwnerConfigKey5 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/xyz/")
            .addCodeOwnerEmail(admin.email())
            .addCodeOwnerEmail(user2.email())
            .create();

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .branch("master")
                .codeOwnerConfigFiles()
                .withEmail(admin.email())
                .paths())
        .containsExactly(
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey2).getFilePath(),
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey4).getFilePath(),
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey5).getFilePath())
        .inOrder();

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .branch("master")
                .codeOwnerConfigFiles()
                .withEmail(user.email())
                .paths())
        .containsExactly(
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey1).getFilePath(),
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey3).getFilePath(),
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey4).getFilePath())
        .inOrder();

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .branch("master")
                .codeOwnerConfigFiles()
                .withEmail(user2.email())
                .paths())
        .containsExactly(
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey5).getFilePath());

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .branch("master")
                .codeOwnerConfigFiles()
                .withEmail(user3.email())
                .paths())
        .isEmpty();
  }

  @Test
  public void getCodeOwnerConfigFilesWithPathGlob() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();

    CodeOwnerConfig.Key codeOwnerConfigKey2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key codeOwnerConfigKey3 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail(user.email())
            .create();

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .branch("master")
                .codeOwnerConfigFiles()
                .withPath("/foo/bar/" + getCodeOwnerConfigFileName())
                .paths())
        .containsExactly(
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey3).getFilePath());

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .branch("master")
                .codeOwnerConfigFiles()
                .withPath("/foo/bar/*")
                .paths())
        .containsExactly(
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey3).getFilePath())
        .inOrder();

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .branch("master")
                .codeOwnerConfigFiles()
                .withPath("/foo/**")
                .paths())
        .containsExactly(
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey2).getFilePath(),
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey3).getFilePath())
        .inOrder();
  }
}
