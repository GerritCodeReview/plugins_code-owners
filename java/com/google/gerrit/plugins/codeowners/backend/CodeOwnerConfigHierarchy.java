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

import com.google.gerrit.entities.BranchNameKey;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Class to visit the code owner configs in a given branch that apply for a given path by following
 * the path hierarchy from the given path up to the root folder.
 */
@Singleton
public class CodeOwnerConfigHierarchy {
  /** Callback interface to visit a code owner config. */
  @FunctionalInterface
  public interface CodeOwnerConfigVisitor {
    /**
     * Callback for a code owner config.
     *
     * @param codeOwnerConfig the code owner config that was found
     * @return whether further code owner configs should be visited
     */
    boolean visit(CodeOwnerConfig codeOwnerConfig);
  }

  private final CodeOwners codeOwners;

  @Inject
  CodeOwnerConfigHierarchy(CodeOwners codeOwners) {
    this.codeOwners = codeOwners;
  }

  /**
   * Visits the code owner configs in the given branch that apply for the given path by following
   * the path hierarchy from the given path up to the root folder.
   *
   * @param branch project and branch from which the code owner configs should be visited
   * @param path the path for which the code owner configs should be visited
   * @param codeOwnerConfigVisitor visitor that should be invoked for the applying code owner
   *     configs
   */
  public void visit(
      BranchNameKey branch, Path path, CodeOwnerConfigVisitor codeOwnerConfigVisitor) {
    // Next path in which we look for a code owner configuration. We start at the given path and
    // then go up the parent hierarchy.
    Path ownerConfigFolder = path;

    // Iterate over the parent code owner configurations.
    while (ownerConfigFolder != null) {
      // Read code owner config and invoke the codeOwnerConfigVisitor if the code owner config
      // exists.
      Optional<CodeOwnerConfig> codeOwnerConfig =
          codeOwners.get(CodeOwnerConfig.Key.create(branch, ownerConfigFolder));
      if (codeOwnerConfig.isPresent()) {
        boolean visitFurtherCodeOwnerConfigs = codeOwnerConfigVisitor.visit(codeOwnerConfig.get());
        if (!visitFurtherCodeOwnerConfigs) {
          return;
        }
      }

      // Continue the loop with the next parent folder.
      ownerConfigFolder = ownerConfigFolder.getParent();
    }
  }
}
