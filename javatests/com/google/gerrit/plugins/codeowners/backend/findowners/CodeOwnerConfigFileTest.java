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

import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigSubject.assertThat;

import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerConfigFile}. */
public class CodeOwnerConfigFileTest extends AbstractCodeOwnersTest {
  private CodeOwnerConfigFile.Factory codeOwnerConfigFileFactory;
  private CodeOwnerConfigParser codeOwnerConfigParser;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerConfigFileFactory =
        plugin.getSysInjector().getInstance(CodeOwnerConfigFile.Factory.class);
    codeOwnerConfigParser = plugin.getSysInjector().getInstance(CodeOwnerConfigParser.class);
  }

  @Test
  public void loadNonExistingCodeOwnerConfigFile() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "master", "/non-existing/");
    CodeOwnerConfigFile codeOwnerConfigFile = codeOwnerConfigFileFactory.load(codeOwnerConfigKey);
    assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();
  }

  @Test
  public void loadCodeOwnerConfigFileFromNonExistingBranch() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "non-existing", "/");
    CodeOwnerConfigFile codeOwnerConfigFile = codeOwnerConfigFileFactory.load(codeOwnerConfigKey);
    assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();
  }

  @Test
  public void loadEmptyCodeOwnerConfigFile() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfig = CodeOwnerConfig.builder(codeOwnerConfigKey).build();
    writeCodeOwnerConfig(codeOwnerConfig);

    CodeOwnerConfigFile codeOwnerConfigFile = codeOwnerConfigFileFactory.load(codeOwnerConfigKey);
    assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isPresent();
    assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig().get()).hasCodeOwnersThat().isEmpty();
  }

  @Test
  public void loadCodeOwnerConfigFile() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(codeOwnerConfigKey).addCodeOwnerEmail(admin.email()).build();
    writeCodeOwnerConfig(codeOwnerConfig);

    CodeOwnerConfigFile codeOwnerConfigFile = codeOwnerConfigFileFactory.load(codeOwnerConfigKey);
    assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isPresent();
    assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig().get())
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email());
  }

  private void writeCodeOwnerConfig(CodeOwnerConfig codeOwnerConfig) throws Exception {
    String formattedCodeOwnerConfig = codeOwnerConfigParser.formatAsString(codeOwnerConfig);
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      testRepo.update(
          codeOwnerConfig.key().ref(),
          testRepo
              .commit()
              .parent(getHead(testRepo.getRepository(), codeOwnerConfig.key().ref()))
              .message("Add test code owner config")
              .add(
                  codeOwnerConfig.key().filePathForJgit(CodeOwnerConfigFile.FILE_NAME),
                  formattedCodeOwnerConfig));
    }
  }
}
