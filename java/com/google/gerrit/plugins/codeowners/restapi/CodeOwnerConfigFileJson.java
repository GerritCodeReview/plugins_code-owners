// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.plugins.codeowners.restapi;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerConfigFileInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.ImportedCodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.config.BackendConfig;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import java.util.List;

/** Collection of routines to populate {@link CodeOwnerConfigFileInfo}. */
public class CodeOwnerConfigFileJson {
  private final ProjectCache projectCache;
  private final BackendConfig backendConfig;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;

  @Inject
  CodeOwnerConfigFileJson(
      ProjectCache projectCache,
      BackendConfig backendConfig,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration) {
    this.projectCache = projectCache;
    this.backendConfig = backendConfig;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
  }

  /**
   * Formats the provided code owner config file information as a {@link CodeOwnerConfigFileInfo}.
   *
   * @param codeOwnerConfigKey the key of the code owner config file as {@link
   *     CodeOwnerConfigFileInfo}
   * @param resolvedImports code owner config files which have been successfully imported directly
   *     or indirectly
   * @param unresolvedImports code owner config files which are imported directly or indirectly but
   *     couldn't be resolved
   * @return the provided {@link com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig.Key}
   *     as {@link CodeOwnerConfigFileInfo}
   */
  public CodeOwnerConfigFileInfo format(
      CodeOwnerConfig.Key codeOwnerConfigKey,
      List<ImportedCodeOwnerConfig> resolvedImports,
      List<ImportedCodeOwnerConfig> unresolvedImports) {
    requireNonNull(codeOwnerConfigKey, "codeOwnerConfigKey");
    requireNonNull(resolvedImports, "resolvedImports");
    requireNonNull(unresolvedImports, "unresolvedImports");

    CodeOwnerConfigFileInfo info = new CodeOwnerConfigFileInfo();

    info.project = codeOwnerConfigKey.project().get();
    info.branch = codeOwnerConfigKey.branchNameKey().branch();
    info.path =
        getBackend(codeOwnerConfigKey.branchNameKey()).getFilePath(codeOwnerConfigKey).toString();

    ImmutableList<CodeOwnerConfigFileInfo> unresolvedImportInfos =
        unresolvedImports.stream()
            .filter(
                unresolvedImport ->
                    unresolvedImport.keyOfImportingCodeOwnerConfig().equals(codeOwnerConfigKey))
            .map(
                unresolvedImport -> {
                  CodeOwnerConfigFileInfo unresolvedCodeOwnerConfigFileInfo =
                      format(
                          unresolvedImport.keyOfImportedCodeOwnerConfig(),
                          /* resolvedImports= */ ImmutableList.of(),
                          /* unresolvedImports= */ ImmutableList.of());
                  unresolvedCodeOwnerConfigFileInfo.importMode =
                      unresolvedImport.codeOwnerConfigReference().importMode();
                  unresolvedCodeOwnerConfigFileInfo.unresolvedErrorMessage =
                      unresolvedImport
                          .errorMessage()
                          .orElseThrow(
                              () ->
                                  new IllegalStateException(
                                      String.format(
                                          "unresolved import %s must have an error message",
                                          unresolvedImport)));
                  return unresolvedCodeOwnerConfigFileInfo;
                })
            .collect(toImmutableList());
    info.unresolvedImports = !unresolvedImportInfos.isEmpty() ? unresolvedImportInfos : null;

    ImmutableList<CodeOwnerConfigFileInfo> importInfos =
        resolvedImports.stream()
            .filter(
                resolvedImport ->
                    resolvedImport.keyOfImportingCodeOwnerConfig().equals(codeOwnerConfigKey))
            .map(
                resolvedImport -> {
                  CodeOwnerConfigFileInfo resolvedCodeOwnerConfigFileInfo =
                      format(
                          resolvedImport.keyOfImportedCodeOwnerConfig(),
                          resolvedImports,
                          unresolvedImports);
                  resolvedCodeOwnerConfigFileInfo.importMode =
                      resolvedImport.codeOwnerConfigReference().importMode();
                  return resolvedCodeOwnerConfigFileInfo;
                })
            .collect(toImmutableList());
    info.imports = !importInfos.isEmpty() ? importInfos : null;

    return info;
  }

  private CodeOwnerBackend getBackend(BranchNameKey branchNameKey) {
    // For unresolved imports the project may not exist. Trying to get the project config for
    // non-existing projects fails, hence check whether the project exists before trying to access
    // the project config and fall back to the default code owner backend if the project doesn't
    // exist.
    if (projectCache.get(branchNameKey.project()).isPresent()) {
      return codeOwnersPluginConfiguration
          .getProjectConfig(branchNameKey.project())
          .getBackend(branchNameKey.branch());
    }

    return backendConfig.getDefaultBackend();
  }
}
