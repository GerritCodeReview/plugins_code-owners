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

import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;

/**
 * An aggregation of operations on code owner configs for test purposes.
 *
 * <p>To execute the operations, no Gerrit permissions are necessary.
 *
 * <p><strong>Note:</strong> This interface is not implemented using the REST or extension API.
 * Hence, it cannot be used for testing those APIs.
 */
public interface CodeOwnerConfigOperations {
  /**
   * Starts the fluent chain for querying or modifying a code owner config. Please see the methods
   * of {@link PerCodeOwnerConfigOperations} for details on possible operations.
   *
   * @param codeOwnerConfigKey the key of the code owner config
   * @return an aggregation of operations on a specific code owner config
   */
  PerCodeOwnerConfigOperations codeOwnerConfig(CodeOwnerConfig.Key codeOwnerConfigKey);

  /** An aggregation of methods on a specific code owner config. */
  interface PerCodeOwnerConfigOperations {

    /**
     * Checks whether the code owner config exists.
     *
     * @return {@code true} if the code owner config exists
     */
    boolean exists();

    /**
     * Retrieves the code owner config.
     *
     * <p><strong>Note:</strong> This call will fail with an exception if the requested code owner
     * config doesn't exist. If you want to check for the existence of a code owner config, use
     * {@link #exists()} instead.
     *
     * @return the corresponding {@code CodeOwnerConfig}
     */
    CodeOwnerConfig get();
  }
}
