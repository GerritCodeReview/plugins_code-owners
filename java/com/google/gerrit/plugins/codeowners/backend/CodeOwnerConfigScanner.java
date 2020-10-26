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
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/** Class to scan a branch for code owner config files. */
@Singleton
public class CodeOwnerConfigScanner {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepositoryManager repoManager;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;

  @Inject
  CodeOwnerConfigScanner(
      GitRepositoryManager repoManager,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration) {
    this.repoManager = repoManager;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
  }

  /**
   * Whether there is at least one code owner config file in the given project and branch.
   *
   * @param branchNameKey the project and branch for which if should be checked if it contains any
   *     code owner config file
   * @return {@code true} if there is at least one code owner config file in the given project and
   *     branch, otherwise {@code false}
   */
  public boolean containsAnyCodeOwnerConfigFile(BranchNameKey branchNameKey) {
    AtomicBoolean found = new AtomicBoolean(false);
    visit(
        branchNameKey,
        codeOwnerConfig -> {
          found.set(true);
          return false;
        },
        (codeOwnerConfigFilePath, configInvalidException) -> found.set(true));
    return found.get();
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
    visit(branchNameKey, codeOwnerConfigVisitor, invalidCodeOwnerConfigCallback, null);
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

    CodeOwnerBackend codeOwnerBackend = codeOwnersPluginConfiguration.getBackend(branchNameKey);
    logger.atFine().log(
        "scanning code owner files in branch %s of project %s (path glob = %s)",
        branchNameKey.branch(), branchNameKey.project(), pathGlob);

    try (Repository repository = repoManager.openRepository(branchNameKey.project());
        RevWalk rw = new RevWalk(repository);
        CodeOwnerConfigTreeWalk treeWalk =
            new CodeOwnerConfigTreeWalk(
                codeOwnerBackend, branchNameKey, repository, rw, pathGlob)) {
      while (treeWalk.next()) {
        CodeOwnerConfig codeOwnerConfig;
        try {
          codeOwnerConfig = treeWalk.getCodeOwnerConfig();
        } catch (StorageException storageException) {
          Optional<ConfigInvalidException> configInvalidException =
              getInvalidConfigCause(storageException);
          if (!configInvalidException.isPresent()) {
            // Propagate any failure that is not related to the contents of the code owner config.
            throw storageException;
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
      throw new StorageException(
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
