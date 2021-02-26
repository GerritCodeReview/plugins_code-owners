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

import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.util.Optional;

/**
 * API to write code owner configurations.
 *
 * <p>All calls which write code owners to code owner configurations are gathered here. Other
 * classes should always use this class instead of accessing code owner backends directly.
 *
 * <p>Write logic that is common for all {@link CodeOwnerBackend}s is implemented in this class so
 * that we avoid code repetition in the code owner backends.
 */
public class CodeOwnersUpdate {
  interface Factory {
    /**
     * Creates a {@code CodeOwnersUpdate} which uses the identity of the specified user to mark
     * updates executed by it. For commits, this identity is used as author and committer for all
     * related commits.
     *
     * <p><strong>Note</strong>: Please use this method with care and consider using the {@link
     * com.google.gerrit.server.UserInitiated} annotation on the provider of a {@code
     * CodeOwnersUpdate} instead.
     *
     * @param currentUser the user to which modifications should be attributed
     */
    CodeOwnersUpdate create(IdentifiedUser currentUser);

    /**
     * Creates a {@code CodeOwnersUpdate} which uses the server identity to mark updates executed by
     * it. For commits, this identity is used as author and committer for all related commits.
     *
     * <p><strong>Note</strong>: Please use this method with care and consider using the {@link
     * com.google.gerrit.server.ServerInitiated} annotation on the provider of a {@code
     * CodeOwnersUpdate} instead.
     */
    CodeOwnersUpdate createWithServerIdent();
  }

  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final Optional<IdentifiedUser> currentUser;

  @AssistedInject
  CodeOwnersUpdate(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      @Assisted IdentifiedUser currentUser) {
    this(codeOwnersPluginConfiguration, Optional.of(currentUser));
  }

  @AssistedInject
  CodeOwnersUpdate(CodeOwnersPluginConfiguration codeOwnersPluginConfiguration) {
    this(codeOwnersPluginConfiguration, Optional.empty());
  }

  private CodeOwnersUpdate(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      Optional<IdentifiedUser> currentUser) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.currentUser = currentUser;
  }

  /**
   * Updates/Creates a code owner config.
   *
   * <p>Detects which code owner backend is configurated and delegates the code owner config
   * update/creation to this backend.
   *
   * <p>If the specified code owner config doesn't exist yet, it is created.
   *
   * <p>Fails with {@link IllegalStateException} if the branch in which the code owner config should
   * be updated doesn't exist.
   *
   * @param codeOwnerConfigKey key of the code owner config
   * @param codeOwnerConfigUpdate definition of the update that should be performed
   * @return the updated/created code owner config, {@link Optional#empty()} if the update led to a
   *     deletion of the code owner config or if the creation was a no-op
   */
  public Optional<CodeOwnerConfig> upsertCodeOwnerConfig(
      CodeOwnerConfig.Key codeOwnerConfigKey, CodeOwnerConfigUpdate codeOwnerConfigUpdate) {
    CodeOwnerBackend codeOwnerBackend =
        codeOwnersPluginConfiguration
            .getProjectConfig(codeOwnerConfigKey.project())
            .getBackend(codeOwnerConfigKey.branchNameKey().branch());
    return codeOwnerBackend.upsertCodeOwnerConfig(
        codeOwnerConfigKey, codeOwnerConfigUpdate, currentUser.orElse(null));
  }
}
