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

/** Enum controlling which parts of a referenced code owner config should be imported. */
public enum CodeOwnerConfigImportMode {
  /**
   * All code owners and properties should be imported from the referenced code owner config.
   *
   * <p>The following things are imported:
   *
   * <ul>
   *   <li>the {@link CodeOwnerConfig#ignoreParentCodeOwners()} flag
   *   <li>the global code owner sets (code owner sets without path expressions)
   *   <li>the per-file code owner sets (code owner sets with path expressions)
   * </ul>
   *
   * <p>Imports of the referenced code owner config are resolved.
   */
  ALL(
      /* importIgnoreParentCodeOwners= */ true,
      /* importGlobalCodeOwnerSets= */ true,
      /* importPerFileCodeOwnerSets= */ true,
      /* resolveImportsOfImport= */ true),

  /**
   * Only global code owner sets (code owner sets without path expressions) should be imported from
   * the referenced code owner config.
   *
   * <p>The following things are not imported:
   *
   * <ul>
   *   <li>the {@link CodeOwnerConfig#ignoreParentCodeOwners()} flag
   *   <li>the per-file code owner sets (code owner sets with path expressions)
   * </ul>
   *
   * <p>Imports of the referenced code owner config are resolved.
   */
  GLOBAL_CODE_OWNER_SETS_ONLY(
      /* importIgnoreParentCodeOwners= */ false,
      /* importGlobalCodeOwnerSets= */ true,
      /* importPerFileCodeOwnerSets= */ false,
      /* resolveImportsOfImport= */ true);

  private final boolean importIgnoreParentCodeOwners;
  private final boolean importGlobalCodeOwnerSets;
  private final boolean importPerFileCodeOwnerSets;
  private final boolean resolveImportsOfImport;

  private CodeOwnerConfigImportMode(
      boolean importIgnoreParentCodeOwners,
      boolean importGlobalCodeOwnerSets,
      boolean importPerFileCodeOwnerSets,
      boolean resolveImportsOfImport) {
    this.importIgnoreParentCodeOwners = importIgnoreParentCodeOwners;
    this.importGlobalCodeOwnerSets = importGlobalCodeOwnerSets;
    this.importPerFileCodeOwnerSets = importPerFileCodeOwnerSets;
    this.resolveImportsOfImport = resolveImportsOfImport;
  }

  /**
   * Whether the {@link CodeOwnerConfig#ignoreParentCodeOwners()} flag should be imported from the
   * referenced code owner config.
   */
  public boolean importIgnoreParentCodeOwners() {
    return importIgnoreParentCodeOwners;
  }

  /**
   * Whether the global code owner sets (code owner sets without path expressions) should be
   * imported from the referenced code owner config.
   */
  public boolean importGlobalCodeOwnerSets() {
    return importGlobalCodeOwnerSets;
  }

  /**
   * Whether the per-file code owner sets (code owner sets with path expressions) should be imported
   * from the referenced code owner config.
   */
  public boolean importPerFileCodeOwnerSets() {
    return importPerFileCodeOwnerSets;
  }

  /** Whether the imports from the referenced code owner config should be resolved. */
  public boolean resolveImportsOfImport() {
    return resolveImportsOfImport;
  }
}
