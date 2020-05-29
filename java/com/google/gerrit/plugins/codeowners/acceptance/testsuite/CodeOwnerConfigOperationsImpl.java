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

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.plugins.codeowners.acceptance.testsuite.TestCodeOwnerConfigCreation.Builder;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig.Key;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigUpdate;
import com.google.gerrit.plugins.codeowners.backend.CodeOwners;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnersUpdate;
import com.google.gerrit.server.ServerInitiated;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Optional;

/**
 * The implementation of {@link CodeOwnerConfigOperations}.
 *
 * <p>There is only one implementation of {@link CodeOwnerConfigOperations}. Nevertheless, we keep
 * the separation between interface and implementation to enhance clarity.
 */
public class CodeOwnerConfigOperationsImpl implements CodeOwnerConfigOperations {
  private final CodeOwners codeOwners;
  private final Provider<CodeOwnersUpdate> codeOwnersUpdate;

  @Inject
  CodeOwnerConfigOperationsImpl(
      CodeOwners codeOwners, @ServerInitiated Provider<CodeOwnersUpdate> codeOwnersUpdate) {
    this.codeOwners = codeOwners;
    this.codeOwnersUpdate = codeOwnersUpdate;
  }

  @Override
  public PerCodeOwnerConfigOperations codeOwnerConfig(Key codeOwnerConfigKey) {
    return new PerCodeOwnerConfigOperationsImpl(codeOwnerConfigKey);
  }

  @Override
  public Builder newCodeOwnerConfig() {
    return TestCodeOwnerConfigCreation.builder(this::createNewCodeOwnerConfig);
  }

  private CodeOwnerConfig.Key createNewCodeOwnerConfig(
      TestCodeOwnerConfigCreation codeOwnerConfigCreation) throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = codeOwnerConfigCreation.key();

    checkState(
        !codeOwnerConfig(codeOwnerConfigKey).exists(),
        "code owner config %s already exists",
        codeOwnerConfigKey);

    if (codeOwnerConfigCreation.isEmpty()) {
      throw new IllegalStateException("code owner config must not be empty");
    }

    return codeOwnersUpdate
        .get()
        .upsertCodeOwnerConfig(
            codeOwnerConfigKey,
            CodeOwnerConfigUpdate.builder()
                .setCodeOwnerModification(codeOwners -> codeOwnerConfigCreation.codeOwners())
                .build())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format("Code owner config %s didn't get created", codeOwnerConfigKey)))
        .key();
  }

  /**
   * The implementation of {@link CodeOwnerConfigOperations.PerCodeOwnerConfigOperations}.
   *
   * <p>There is only one implementation of {@link
   * CodeOwnerConfigOperations.PerCodeOwnerConfigOperations}. Nevertheless, we keep the separation
   * between interface and implementation to enhance clarity.
   */
  private class PerCodeOwnerConfigOperationsImpl implements PerCodeOwnerConfigOperations {
    private final CodeOwnerConfig.Key codeOwnerConfigKey;

    PerCodeOwnerConfigOperationsImpl(CodeOwnerConfig.Key codeOwnerConfigKey) {
      this.codeOwnerConfigKey = codeOwnerConfigKey;
    }

    @Override
    public boolean exists() {
      return getCodeOwnerConfig().isPresent();
    }

    @Override
    public CodeOwnerConfig get() {
      return getCodeOwnerConfig()
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      String.format("code owner config %s does not exist", codeOwnerConfigKey)));
    }

    private Optional<CodeOwnerConfig> getCodeOwnerConfig() {
      return codeOwners.get(codeOwnerConfigKey);
    }
  }
}
