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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerConfigFileInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImport;
import com.google.gerrit.plugins.codeowners.backend.UnresolvedImportFormatter;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.WebLinks;
import com.google.inject.Inject;
import java.util.List;

/** Collection of routines to populate {@link CodeOwnerConfigFileInfo}. */
public class CodeOwnerConfigFileJson {
  private final WebLinks webLinks;
  private final UnresolvedImportFormatter unresolvedImportFormatter;

  @Inject
  CodeOwnerConfigFileJson(WebLinks webLinks, UnresolvedImportFormatter unresolvedImportFormatter) {
    this.webLinks = webLinks;
    this.unresolvedImportFormatter = unresolvedImportFormatter;
  }

  /**
   * Formats the provided code owner config file information as a {@link CodeOwnerConfigFileInfo}.
   *
   * @param codeOwnerConfig the code owner config
   * @param resolvedImports code owner config files which have been successfully imported directly
   *     or indirectly
   * @param unresolvedImports code owner config files which are imported directly or indirectly but
   *     couldn't be resolved
   * @return the provided {@link com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig.Key}
   *     as {@link CodeOwnerConfigFileInfo}
   */
  public CodeOwnerConfigFileInfo format(
      CodeOwnerConfig codeOwnerConfig,
      List<CodeOwnerConfigImport> resolvedImports,
      List<CodeOwnerConfigImport> unresolvedImports) {
    requireNonNull(codeOwnerConfig, "codeOwnerConfig");
    requireNonNull(resolvedImports, "resolvedImports");
    requireNonNull(unresolvedImports, "unresolvedImports");

    CodeOwnerConfigFileInfo info =
        format(codeOwnerConfig.key(), resolvedImports, unresolvedImports);

    ImmutableList<WebLinkInfo> fileLinks =
        webLinks.getFileLinks(
            info.project,
            info.branch,
            codeOwnerConfig.revision().getName(),
            JgitPath.of(info.path).get());
    info.webLinks = !fileLinks.isEmpty() ? fileLinks : null;

    return info;
  }

  private CodeOwnerConfigFileInfo format(
      CodeOwnerConfig.Key codeOwnerConfigKey,
      List<CodeOwnerConfigImport> resolvedImports,
      List<CodeOwnerConfigImport> unresolvedImports) {
    requireNonNull(codeOwnerConfigKey, "codeOwnerConfigKey");
    requireNonNull(resolvedImports, "resolvedImports");
    requireNonNull(unresolvedImports, "unresolvedImports");

    CodeOwnerConfigFileInfo info = new CodeOwnerConfigFileInfo();

    info.project = codeOwnerConfigKey.project().get();
    info.branch = codeOwnerConfigKey.branchNameKey().branch();
    info.path = unresolvedImportFormatter.getFilePath(codeOwnerConfigKey).toString();

    ImmutableList<CodeOwnerConfigFileInfo> unresolvedImportInfos =
        unresolvedImports.stream()
            .filter(
                unresolvedImport ->
                    unresolvedImport.importingCodeOwnerConfig().key().equals(codeOwnerConfigKey))
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
                    resolvedImport.importingCodeOwnerConfig().key().equals(codeOwnerConfigKey))
            .map(
                resolvedImport -> {
                  checkState(
                      resolvedImport.importedCodeOwnerConfig().isPresent(),
                      "no imported code owner config for resolved import");
                  CodeOwnerConfigFileInfo resolvedCodeOwnerConfigFileInfo =
                      format(
                          resolvedImport.importedCodeOwnerConfig().get(),
                          removeImportEntriesFor(resolvedImports, codeOwnerConfigKey),
                          removeImportEntriesFor(unresolvedImports, codeOwnerConfigKey));
                  resolvedCodeOwnerConfigFileInfo.importMode =
                      resolvedImport.codeOwnerConfigReference().importMode();
                  return resolvedCodeOwnerConfigFileInfo;
                })
            .collect(toImmutableList());
    info.imports = !importInfos.isEmpty() ? importInfos : null;

    return info;
  }

  private ImmutableList<CodeOwnerConfigImport> removeImportEntriesFor(
      List<CodeOwnerConfigImport> imports, CodeOwnerConfig.Key codeOwnerConfigKey) {
    return imports.stream()
        .filter(i -> !i.importingCodeOwnerConfig().key().equals(codeOwnerConfigKey))
        .collect(toImmutableList());
  }
}
