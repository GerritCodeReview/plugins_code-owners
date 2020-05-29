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

package com.google.gerrit.plugins.codeowners.acceptance.testsuite;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations.PerCodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigUpdate;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnersUpdate;
import com.google.gerrit.server.ServerInitiated;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerConfigOperationsImpl}. */
public class CodeOwnerConfigOperationsImplTest extends AbstractCodeOwnersTest {
  private CodeOwnerConfigOperations codeOwnerConfigOperations;

  private Provider<CodeOwnersUpdate> codeOwnersUpdate;

  @Before
  public void setUp() {
    codeOwnerConfigOperations =
        plugin.getSysInjector().getInstance(CodeOwnerConfigOperationsImpl.class);
    codeOwnersUpdate =
        plugin
            .getSysInjector()
            .getInstance(
                Key.get(new TypeLiteral<Provider<CodeOwnersUpdate>>() {}, ServerInitiated.class));
  }

  @Test
  public void getNonExistingCodeOwnerConfig() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    PerCodeOwnerConfigOperations perCodeOwnerConfigOperations =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey);
    assertThat(perCodeOwnerConfigOperations.exists()).isFalse();
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> perCodeOwnerConfigOperations.get());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("code owner config %s does not exist", codeOwnerConfigKey));
  }

  @Test
  public void getExistingCodeOwnerConfig() throws Exception {
    CodeOwnerConfig codeOwnerConfig = createArbitraryCodeOwnerConfig();
    PerCodeOwnerConfigOperations perCodeOwnerConfigOperations =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfig.key());
    assertThat(perCodeOwnerConfigOperations.exists()).isTrue();
    assertThat(perCodeOwnerConfigOperations.get()).isEqualTo(codeOwnerConfig);
  }

  private CodeOwnerConfig createArbitraryCodeOwnerConfig() {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfigUpdate codeOwnerConfigUpdate =
        CodeOwnerConfigUpdate.builder()
            .setCodeOwnerModification(
                codeOwners -> ImmutableSet.of(CodeOwnerReference.create(admin.email())))
            .build();
    return codeOwnersUpdate
        .get()
        .upsertCodeOwnerConfig(codeOwnerConfigKey, codeOwnerConfigUpdate)
        .orElseThrow(() -> new IllegalArgumentException("code owner config was not created."));
  }
}
