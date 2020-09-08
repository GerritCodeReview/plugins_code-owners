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

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Class to visit the code owner configs in a given branch that apply for a given path by following
 * the path hierarchy from the given path up to the root folder.
 */
@Singleton
public class CodeOwnerConfigHierarchy {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PathCodeOwners.Factory pathCodeOwnersFactory;

  @Inject
  CodeOwnerConfigHierarchy(PathCodeOwners.Factory pathCodeOwnersFactory) {
    this.pathCodeOwnersFactory = pathCodeOwnersFactory;
  }

  /**
   * Visits the code owner configs in the given branch that apply for the given path by following
   * the path hierarchy from the given path up to the root folder.
   *
   * @param branch project and branch from which the code owner configs should be visited
   * @param revision the branch revision from which the code owner configs should be loaded
   * @param absolutePath the path for which the code owner configs should be visited; the path must
   *     be absolute; can be the path of a file or folder; the path may or may not exist
   * @param codeOwnerConfigVisitor visitor that should be invoked for the applying code owner
   *     configs
   */
  public void visit(
      BranchNameKey branch,
      ObjectId revision,
      Path absolutePath,
      CodeOwnerConfigVisitor codeOwnerConfigVisitor) {
    requireNonNull(branch, "branch");
    requireNonNull(revision, "revision");
    requireNonNull(absolutePath, "absolutePath");
    requireNonNull(codeOwnerConfigVisitor, "codeOwnerConfigVisitor");
    checkState(absolutePath.isAbsolute(), "path %s must be absolute", absolutePath);

    logger.atFine().log(
        "visiting code owner configs for '%s' in branch '%s' in project '%s' (revision = '%s')",
        absolutePath, branch.shortName(), branch.project(), revision.name());

    // Next path in which we look for a code owner configuration. We start at the given path and
    // then go up the parent hierarchy.
    Path ownerConfigFolder = absolutePath;

    // Iterate over the parent code owner configurations.
    while (ownerConfigFolder != null) {
      // Read code owner config and invoke the codeOwnerConfigVisitor if the code owner config
      // exists.
      logger.atFine().log("inspecting code owner config for %s", ownerConfigFolder);
      Optional<PathCodeOwners> pathCodeOwners =
          pathCodeOwnersFactory.create(
              CodeOwnerConfig.Key.create(branch, ownerConfigFolder), revision, absolutePath);
      if (pathCodeOwners.isPresent()) {
        logger.atFine().log("visit code owner config for %s", ownerConfigFolder);
        boolean visitFurtherCodeOwnerConfigs =
            codeOwnerConfigVisitor.visit(pathCodeOwners.get().getCodeOwnerConfig());
        boolean ignoreParentCodeOwners = pathCodeOwners.get().ignoreParentCodeOwners();
        logger.atFine().log(
            "visitFurtherCodeOwnerConfigs = %s, ignoreParentCodeOwners = %s",
            visitFurtherCodeOwnerConfigs, ignoreParentCodeOwners);
        if (!visitFurtherCodeOwnerConfigs || ignoreParentCodeOwners) {
          return;
        }
      } else {
        logger.atFine().log("no code owner config found in %s", ownerConfigFolder);
      }

      // Continue the loop with the next parent folder.
      ownerConfigFolder = ownerConfigFolder.getParent();
    }
  }
}
