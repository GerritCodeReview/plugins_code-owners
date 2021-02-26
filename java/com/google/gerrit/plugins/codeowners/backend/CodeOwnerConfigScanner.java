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

import static com.google.gerrit.plugins.codeowners.backend.CodeOwners.getInvalidConfigCause;
import static java.util.Objects.requireNonNull;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Class to scan a branch for code owner config files.
 *
 * <p>Whether the scan includes the code owner config file at the root of {@code refs/meta/config}
 * branch that contains the default code owners for the whole repository can be controlled via
 * {@link #includeDefaultCodeOwnerConfig(boolean)}.
 */
public class CodeOwnerConfigScanner {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    CodeOwnerConfigScanner create();
  }

  private final GitRepositoryManager repoManager;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;

  private boolean includeDefaultCodeOwnerConfig = true;

  @Inject
  CodeOwnerConfigScanner(
      GitRepositoryManager repoManager,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration) {
    this.repoManager = repoManager;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
  }

  /**
   * Whether the scan should include the code owner config file at the root of {@code
   * refs/meta/config} branch that contains the default code owners for the whole repository.
   */
  public CodeOwnerConfigScanner includeDefaultCodeOwnerConfig(
      boolean includeDefaultCodeOwnerConfig) {
    this.includeDefaultCodeOwnerConfig = includeDefaultCodeOwnerConfig;
    return this;
  }

  /**
   * Visits all code owner config files in the given project and branch.
   *
   * @param branchNameKey the project and branch for which the code owner config files should be
   *     visited
   * @param codeOwnerConfigVisitor the callback that is invoked for each code owner config file
   * @param invalidCodeOwnerConfigCallback callback that is invoked for invalid code owner config
   *     files
   */
  public void visit(
      BranchNameKey branchNameKey,
      CodeOwnerConfigVisitor codeOwnerConfigVisitor,
      InvalidCodeOwnerConfigCallback invalidCodeOwnerConfigCallback) {
    visit(
        branchNameKey,
        codeOwnerConfigVisitor,
        invalidCodeOwnerConfigCallback,
        /* pathGlob= */ null);
  }

  /**
   * Visits all code owner config files in the given project and branch.
   *
   * @param branchNameKey the project and branch for which the code owner config files should be
   *     visited
   * @param codeOwnerConfigVisitor the callback that is invoked for each code owner config file
   * @param invalidCodeOwnerConfigCallback callback that is invoked for invalid code owner config
   *     files
   * @param pathGlob optional Java NIO glob that the paths of code owner config files must match
   */
  public void visit(
      BranchNameKey branchNameKey,
      CodeOwnerConfigVisitor codeOwnerConfigVisitor,
      InvalidCodeOwnerConfigCallback invalidCodeOwnerConfigCallback,
      @Nullable String pathGlob) {
    requireNonNull(branchNameKey, "branchNameKey");
    requireNonNull(codeOwnerConfigVisitor, "codeOwnerConfigVisitor");
    requireNonNull(invalidCodeOwnerConfigCallback, "invalidCodeOwnerConfigCallback");

    CodeOwnerBackend codeOwnerBackend =
        codeOwnersPluginConfiguration
            .getProjectConfig(branchNameKey.project())
            .getBackend(branchNameKey.branch());
    logger.atFine().log(
        "scanning code owner files in branch %s of project %s (path glob = %s)",
        branchNameKey.branch(), branchNameKey.project(), pathGlob);

    if (includeDefaultCodeOwnerConfig && !RefNames.REFS_CONFIG.equals(branchNameKey.branch())) {
      logger.atFine().log("Scanning code owner config file in %s", RefNames.REFS_CONFIG);
      Optional<CodeOwnerConfig> metaCodeOwnerConfig =
          codeOwnerBackend.getCodeOwnerConfig(
              CodeOwnerConfig.Key.createWithFileName(
                  codeOwnerBackend, branchNameKey.project(), RefNames.REFS_CONFIG, "/"),
              /** revision */
              null);
      if (metaCodeOwnerConfig.isPresent()) {
        boolean visitFurtherCodeOwnerConfigFiles =
            codeOwnerConfigVisitor.visit(metaCodeOwnerConfig.get());
        if (!visitFurtherCodeOwnerConfigFiles) {
          // By returning false the callback told us to not visit any further code owner config
          // files, hence we are done and do not need to search for further code owner config files
          // in the branch.
          return;
        }
      }
    }

    try (Repository repository = repoManager.openRepository(branchNameKey.project());
        RevWalk rw = new RevWalk(repository);
        CodeOwnerConfigTreeWalk treeWalk =
            new CodeOwnerConfigTreeWalk(
                codeOwnerBackend, branchNameKey, repository, rw, pathGlob)) {
      while (treeWalk.next()) {
        CodeOwnerConfig codeOwnerConfig;
        try {
          codeOwnerConfig = treeWalk.getCodeOwnerConfig();
        } catch (CodeOwnersInternalServerErrorException codeOwnersInternalServerErrorException) {
          Optional<ConfigInvalidException> configInvalidException =
              getInvalidConfigCause(codeOwnersInternalServerErrorException);
          if (!configInvalidException.isPresent()) {
            // Propagate any failure that is not related to the contents of the code owner config.
            throw codeOwnersInternalServerErrorException;
          }

          // The code owner config is invalid and cannot be parsed.
          invalidCodeOwnerConfigCallback.onInvalidCodeOwnerConfig(
              treeWalk.getFilePath(), configInvalidException.get());
          continue;
        }

        boolean visitFurtherCodeOwnerConfigFiles = codeOwnerConfigVisitor.visit(codeOwnerConfig);
        if (!visitFurtherCodeOwnerConfigFiles) {
          break;
        }
      }
    } catch (IOException e) {
      throw new CodeOwnersInternalServerErrorException(
          String.format(
              "Failed to scan for code owner configs in branch %s of project %s",
              branchNameKey.branch(), branchNameKey.project()),
          e);
    }
  }

  /**
   * Returns an {@link InvalidCodeOwnerConfigCallback} instance that ignores invalid code owner
   * config files.
   */
  public static InvalidCodeOwnerConfigCallback ignoreInvalidCodeOwnerConfigFiles() {
    return (codeOwnerConfigFilePath, configInvalidException) ->
        logger.atFine().log("ignoring invalid code owner config file %s", codeOwnerConfigFilePath);
  }
}
