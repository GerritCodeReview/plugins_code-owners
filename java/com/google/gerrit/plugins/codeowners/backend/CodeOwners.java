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

import static java.util.Objects.requireNonNull;

import com.google.common.base.Throwables;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;

/**
 * API to read code owner configurations.
 *
 * <p>All calls which read code owners from code owner configurations are gathered here. Other
 * classes should always use this class instead of accessing {@link CodeOwnerBackend} directly.
 *
 * <p>Read logic that is common for all {@link CodeOwnerBackend}s is implemented in this class so
 * that we avoid code repetition in the code owner backends.
 */
@Singleton
public class CodeOwners implements CodeOwnerConfigLoader {
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final CodeOwnerMetrics codeOwnerMetrics;

  @Inject
  CodeOwners(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerMetrics codeOwnerMetrics) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.codeOwnerMetrics = codeOwnerMetrics;
  }

  @Override
  public Optional<CodeOwnerConfig> get(CodeOwnerConfig.Key codeOwnerConfigKey, ObjectId revision) {
    requireNonNull(codeOwnerConfigKey, "codeOwnerConfigKey");
    requireNonNull(revision, "revision");
    codeOwnerMetrics.countCodeOwnerConfigReads.increment();
    CodeOwnerBackend codeOwnerBackend =
        codeOwnersPluginConfiguration
            .getProjectConfig(codeOwnerConfigKey.project())
            .getBackend(codeOwnerConfigKey.branchNameKey().branch());
    try (Timer1.Context<String> ctx =
        codeOwnerMetrics.loadCodeOwnerConfig.start(codeOwnerBackend.getClass().getSimpleName())) {
      return codeOwnerBackend.getCodeOwnerConfig(codeOwnerConfigKey, revision);
    }
  }

  @Override
  public Optional<CodeOwnerConfig> getFromCurrentRevision(CodeOwnerConfig.Key codeOwnerConfigKey) {
    requireNonNull(codeOwnerConfigKey, "codeOwnerConfigKey");
    codeOwnerMetrics.countCodeOwnerConfigReads.increment();
    CodeOwnerBackend codeOwnerBackend =
        codeOwnersPluginConfiguration
            .getProjectConfig(codeOwnerConfigKey.project())
            .getBackend(codeOwnerConfigKey.branchNameKey().branch());
    try (Timer1.Context<String> ctx =
        codeOwnerMetrics.loadCodeOwnerConfig.start(codeOwnerBackend.getClass().getSimpleName())) {
      return codeOwnerBackend.getCodeOwnerConfig(codeOwnerConfigKey, /* revision= */ null);
    }
  }

  /**
   * Returns the absolute file path of the specified code owner config.
   *
   * <p>This method is useful to resolve the file path if the {@link CodeOwnerConfig.Key#fileName()}
   * in the code owner config key is not set.
   *
   * <p>The specified code owner config may or may not exist.
   *
   * @param codeOwnerConfigKey the key of the code owner config for which the file path should be
   *     returned
   * @return the absolute file path of the specified code owner config
   */
  public Path getFilePath(CodeOwnerConfig.Key codeOwnerConfigKey) {
    requireNonNull(codeOwnerConfigKey, "codeOwnerConfigKey");
    CodeOwnerBackend codeOwnerBackend =
        codeOwnersPluginConfiguration
            .getProjectConfig(codeOwnerConfigKey.project())
            .getBackend(codeOwnerConfigKey.branchNameKey().branch());
    return codeOwnerBackend.getFilePath(codeOwnerConfigKey);
  }

  /**
   * Checks whether the given exception was caused by a non-parseable code owner config ({@link
   * ConfigInvalidException}). If yes, the {@link ConfigInvalidException} is returned. If no, {@link
   * Optional#empty()} is returned.
   */
  public static Optional<ConfigInvalidException> getInvalidConfigCause(Throwable e) {
    return Throwables.getCausalChain(e).stream()
        .filter(t -> t instanceof ConfigInvalidException)
        .map(t -> (ConfigInvalidException) t)
        .findFirst();
  }
}
