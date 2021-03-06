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

  /**
   * Starts the fluent chain to create a code owner config. The returned builder can be used to
   * specify the attributes of the new code owner config. To create the code owner config for real,
   * {@link TestCodeOwnerConfigCreation.Builder#create()} must be called.
   *
   * <p>Example:
   *
   * <pre>
   * CodeOwnerConfigKey createdCodeOwnerConfigKey = codeOwnerConfigOperations
   *     .newCodeOwnerConfig
   *     .project(project)
   *     .branch("master")
   *     .folderPath("/foo/bar/")
   *     .addCodeOwnerEmail("jane.roe@example.com")
   *     .addCodeOwnerEmail("joe.doe@example.com")
   *     .create();
   * </pre>
   *
   * <p>Specifying the project is required, if the project is not set the code owner config will be
   * rejected.
   *
   * <p>If a branch is not specified, {@code master} is used by default.
   *
   * <p>If a folder path is not specified, {@code /} is used by default.
   *
   * <p>Specifying code owner is required, if no code owners are not set the code owner config will
   * be rejected.
   *
   * <p><strong>Note:</strong> If another code owner config with the provided project, branch and
   * folder already exists, the creation of the code owner config will be rejected.
   *
   * @return a builder to create the new code owner config
   */
  TestCodeOwnerConfigCreation.Builder newCodeOwnerConfig();

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
     * <p><strong>Note:</strong> This call will fail with an {@link IllegalStateException} if the
     * requested code owner config doesn't exist. If you want to check for the existence of a code
     * owner config, use {@link #exists()} instead.
     *
     * @return the corresponding {@code CodeOwnerConfig}
     */
    CodeOwnerConfig get();

    /**
     * Retrieves the file path of the code owner config for use in the JGit API (where paths must
     * not start with a '/').
     *
     * <p>Works regardless of whether the code owner config exists.
     *
     * @return the file path of the code owner config
     */
    String getJGitFilePath();

    /**
     * Retrieves the absolute file path of the code owner config.
     *
     * <p>Works regardless of whether the code owner config exists.
     *
     * @return the absolute file path of the code owner config
     */
    String getFilePath();

    /**
     * Retrieves the raw file content of the code owner config.
     *
     * <p><strong>Note:</strong> This call will fail with an {@link IllegalStateException} if the
     * requested code owner config doesn't exist.
     *
     * @return the raw file content of the code owner config
     */
    String getContent() throws Exception;

    /**
     * Starts the fluent chain to update a code owner config. The returned builder can be used to
     * specify how the attributes of the code owner config should be modified. To update the code
     * owner config for real, {@link TestCodeOwnerConfigUpdate.Builder#update()} must be called.
     *
     * <p>Example:
     *
     * <pre>
     * codeOwnerOperations
     *   .codeOwnerConfig(codeOwnerConfigKey)
     *   .forUpdate()
     *   .addCodeOwnerEmail("jane.roe@example.com")
     *   .removeCodeOwnerEmail("joe.doe@example.com")
     *   .update();
     * </pre>
     *
     * <p><strong>Note:</strong> The update will fail with an {@link IllegalStateException} if the
     * code owner config to update doesn't exist. If you want to check for the existence of a code
     * owner config, use {@link #exists()}.
     *
     * @return a builder to update the code owner config
     */
    TestCodeOwnerConfigUpdate.Builder forUpdate();
  }
}
