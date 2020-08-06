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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;

/**
 * Representation of a code owner import modification as defined by {@link #apply(ImmutableList)}.
 *
 * <p>Used by {@link CodeOwnerConfigUpdate} to describe how the imports (aka {@link
 * CodeOwnerConfigReference}s) in a {@link CodeOwnerConfig} should be changed on updated or be
 * populated on creation.
 *
 * <p>This class provides a couple of static helper methods to modify imports that make changes to
 * imports easier for callers.
 */
@FunctionalInterface
public interface CodeOwnerConfigImportModification {
  /**
   * Create a {@link CodeOwnerConfigImportModification} instance that keeps the imports as they are.
   *
   * @return the created {@link CodeOwnerConfigImportModification} instance
   */
  public static CodeOwnerConfigImportModification keep() {
    return originalImports -> originalImports;
  }

  /**
   * Create a {@link CodeOwnerConfigImportModification} instance that clears the imports.
   *
   * <p>All imports are removed.
   *
   * @return the created {@link CodeOwnerConfigImportModification} instance
   */
  public static CodeOwnerConfigImportModification clear() {
    return originalImports -> ImmutableList.of();
  }

  /**
   * Create a {@link CodeOwnerConfigImportModification} instance that sets the given import.
   *
   * <p>This overrides all imports which have been set before.
   *
   * @param newImport the import that should be set
   * @return the created {@link CodeOwnerConfigImportModification} instance
   */
  public static CodeOwnerConfigImportModification set(CodeOwnerConfigReference newImport) {
    return set(ImmutableList.of(newImport));
  }

  /**
   * Create a {@link CodeOwnerConfigImportModification} instance that sets the given imports.
   *
   * <p>This overrides imports which have been set before.
   *
   * @param newImports the imports that should be set
   * @return the created {@link CodeOwnerConfigImportModification} instance
   */
  public static CodeOwnerConfigImportModification set(
      ImmutableList<CodeOwnerConfigReference> newImports) {
    return originalImports -> newImports;
  }

  /**
   * Create a {@link CodeOwnerConfigImportModification} instance that removes the given import.
   *
   * <p>No-op if the given import doesn't exist.
   *
   * @param importToRemove the import that should be removed
   * @return the created {@link CodeOwnerConfigImportModification} instance
   */
  public static CodeOwnerConfigImportModification remove(CodeOwnerConfigReference importToRemove) {
    return originalImports ->
        originalImports.stream()
            .filter(codeOwnerConfigReference -> !codeOwnerConfigReference.equals(importToRemove))
            .collect(toImmutableList());
  }

  /**
   * Applies the modification to the given imports.
   *
   * @param originalImports the current imports of the code owner config that is being updated. If
   *     used for a code owner config creation, this set is empty.
   * @return the desired resulting imports (not the diff of the imports!)
   */
  ImmutableList<CodeOwnerConfigReference> apply(
      ImmutableList<CodeOwnerConfigReference> originalImports);
}
