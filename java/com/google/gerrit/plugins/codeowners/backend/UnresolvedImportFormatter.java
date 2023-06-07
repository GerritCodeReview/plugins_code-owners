// Copyright (C) 2021 The Android Open Source Project
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

import com.google.gerrit.plugins.codeowners.backend.config.BackendConfig;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;

/** Class to format an {@link CodeOwnerConfigImport} as a user-readable string. */
@Singleton
public class UnresolvedImportFormatter {
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final PerThreadProjectCache.Factory perThreadProjectCacheFactory;
  private final BackendConfig backendConfig;

  @Inject
  UnresolvedImportFormatter(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      PerThreadProjectCache.Factory perThreadProjectCacheFactory,
      BackendConfig backendConfig) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.perThreadProjectCacheFactory = perThreadProjectCacheFactory;
    this.backendConfig = backendConfig;
  }

  /** Returns a user-readable string representation of the given unresolved import. */
  public String format(CodeOwnerConfigImport unresolvedImport) {
    return String.format(
        "The import of %s:%s:%s in %s:%s:%s cannot be resolved: %s",
        unresolvedImport.keyOfImportedCodeOwnerConfig().project(),
        unresolvedImport.keyOfImportedCodeOwnerConfig().shortBranchName(),
        getFilePath(unresolvedImport.keyOfImportedCodeOwnerConfig()),
        unresolvedImport.keyOfImportingCodeOwnerConfig().project(),
        unresolvedImport.keyOfImportingCodeOwnerConfig().shortBranchName(),
        getFilePath(unresolvedImport.keyOfImportingCodeOwnerConfig()),
        unresolvedImport
            .errorMessage()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format(
                            "unresolved import %s must have an error message", unresolvedImport))));
  }

  public Path getFilePath(CodeOwnerConfig.Key codeOwnerConfigKey) {
    return getBackend(codeOwnerConfigKey).getFilePath(codeOwnerConfigKey);
  }

  /**
   * Returns the code owner backend for the given code owner config key.
   *
   * <p>If the project of the code owner config key doesn't exist, the default code owner backend is
   * returned.
   */
  private CodeOwnerBackend getBackend(CodeOwnerConfig.Key codeOwnerConfigKey) {
    // For unresolved imports the project may not exist. Trying to get the project config for
    // non-existing projects fails, hence check whether the project exists before trying to access
    // the project config and fall back to the default code owner backend if the project doesn't
    // exist.
    if (perThreadProjectCacheFactory.getOrCreate().isPresent(codeOwnerConfigKey.project())) {
      return codeOwnersPluginConfiguration
          .getProjectConfig(codeOwnerConfigKey.project())
          .getBackend(codeOwnerConfigKey.branchNameKey().branch());
    }
    return backendConfig.getDefaultBackend();
  }
}
