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

import java.util.Optional;

/**
 * Interface for code owner backends.
 *
 * <p>Allows to implement different syntaxes and storages for code owner configurations.
 */
public interface CodeOwnersBackend {
  /**
   * Gets the code owner config for the given key if it exists.
   *
   * @param codeOwnerConfigKey the code owner config key for which the code owner config should be
   *     returned
   * @return code owner config for the given key if it exists, otherwise {@link Optional#empty()}
   */
  Optional<CodeOwnerConfig> getCodeOwnerConfig(CodeOwnerConfig.Key codeOwnerConfigKey);
}
