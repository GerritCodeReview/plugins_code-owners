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

import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import com.google.gerrit.plugins.codeowners.config.BackendConfig;
import org.junit.Before;
import org.junit.Test;

/**
 * Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerConfigFiles} REST endpoint.
 */
public class GetCodeOwnerConfigFilesIT extends AbstractCodeOwnersIT {
  private BackendConfig backendConfig;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    backendConfig = plugin.getSysInjector().getInstance(BackendConfig.class);
  }

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
            getCodeOwnerConfigFilePath(codeOwnerConfigKey1),
            getCodeOwnerConfigFilePath(codeOwnerConfigKey2),
            getCodeOwnerConfigFilePath(codeOwnerConfigKey3))
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
            getCodeOwnerConfigFilePath(codeOwnerConfigKey2),
            getCodeOwnerConfigFilePath(codeOwnerConfigKey1))
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
            getCodeOwnerConfigFilePath(codeOwnerConfigKey1),
            getCodeOwnerConfigFilePath(codeOwnerConfigKey2))
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

  private String getCodeOwnerConfigFilePath(CodeOwnerConfig.Key codeOwnerConfigKey) {
    return backendConfig.getDefaultBackend().getFilePath(codeOwnerConfigKey).toString();
  }

  private String getCodeOwnerConfigFileName() {
    CodeOwnerBackend backend = backendConfig.getDefaultBackend();
    if (backend instanceof FindOwnersBackend) {
      return FindOwnersBackend.CODE_OWNER_CONFIG_FILE_NAME;
    } else if (backend instanceof ProtoBackend) {
      return ProtoBackend.CODE_OWNER_CONFIG_FILE_NAME;
    }
    throw new IllegalStateException("unknown code owner backend: " + backend.getClass().getName());
  }
}
