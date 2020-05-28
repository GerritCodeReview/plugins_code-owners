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

import com.google.gerrit.plugins.codeowners.CodeOwnersPluginConfiguration;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

/**
 * API to read code owner configurations.
 *
 * <p>All calls which read code owners from code owner configurations are gathered here. Other
 * classes should always use this class instead of accessing {@link CodeOwnersBackend} directly.
 *
 * <p>Read logic that is common for all {@link CodeOwnersBackend}s is implemented in this class so
 * that we avoid code repetition in the code owners backends.
 */
@Singleton
public class CodeOwners {
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;

  @Inject
  CodeOwners(CodeOwnersPluginConfiguration codeOwnersPluginConfiguration) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
  }

  /**
   * Retrieves the code owner config for the given key if it exists.
   *
   * @param codeOwnerConfigKey the key of the code owner config that should be retrieved.
   * @return the code owner config for the given key if it exists, otherwise {@link
   *     Optional#empty()}
   */
  public Optional<CodeOwnerConfig> get(CodeOwnerConfig.Key codeOwnerConfigKey) {
    CodeOwnersBackend codeOwnersBackend =
        codeOwnersPluginConfiguration.getBackend(codeOwnerConfigKey.branch());
    return codeOwnersBackend.getCodeOwnerConfig(codeOwnerConfigKey);
  }
}
