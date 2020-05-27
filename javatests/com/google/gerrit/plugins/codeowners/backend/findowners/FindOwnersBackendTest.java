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
import static com.google.common.truth.Truth8.assertThat;

import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.testing.findowners.FindOwnersTestUtil;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link FindOwnersBackend}. */
public class FindOwnersBackendTest extends AbstractCodeOwnersTest {
  private FindOwnersTestUtil findOwnersTestUtil;
  private FindOwnersBackend findOwnersBackend;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    findOwnersTestUtil = plugin.getSysInjector().getInstance(FindOwnersTestUtil.class);
    findOwnersBackend = plugin.getSysInjector().getInstance(FindOwnersBackend.class);
  }

  @Test
  public void getNonExistingCodeOwnerConfig() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "master", "/non-existing/");
    assertThat(findOwnersBackend.getCodeOwnerConfig(codeOwnerConfigKey)).isEmpty();
  }

  @Test
  public void getCodeOwnerConfigFromNonExistingBranch() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "non-existing", "/");
    assertThat(findOwnersBackend.getCodeOwnerConfig(codeOwnerConfigKey)).isEmpty();
  }

  @Test
  public void getCodeOwnerConfig() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfigInRepository =
        CodeOwnerConfig.builder(codeOwnerConfigKey).addCodeOwnerEmail(admin.email()).build();
    findOwnersTestUtil.writeCodeOwnerConfig(codeOwnerConfigInRepository);

    Optional<CodeOwnerConfig> codeOwnerConfig =
        findOwnersBackend.getCodeOwnerConfig(codeOwnerConfigKey);
    assertThat(codeOwnerConfig).isPresent();
    assertThat(codeOwnerConfig.get()).isEqualTo(codeOwnerConfigInRepository);
  }
}
