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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import java.util.Optional;

/**
 * Information about an import of a {@link CodeOwnerConfig}.
 *
 * <p>Contains the keys of the importing and the imported code owner config, as well as the
 * reference that the importing code owner config uses to reference the imported code owner config
 * (contains the import mode).
 *
 * <p>It's possible that this class represents non-resolveable imports (e.g. an import of a
 * non-existing code owner config). In this case an error message is contained that explains why the
 * import couldn't be resolved.
 */
@AutoValue
public abstract class CodeOwnerConfigImport {
  /** Key of the importing code owner config. */
  public abstract CodeOwnerConfig importingCodeOwnerConfig();

  /** Key of the imported code owner config. */
  public abstract CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig();

  /**
   * Imported code owner config.
   *
   * <p>Not set for unresolved imports.
   */
  public abstract Optional<CodeOwnerConfig> importedCodeOwnerConfig();

  /** The code owner config reference that references the imported code owner config. */
  public abstract CodeOwnerConfigReference codeOwnerConfigReference();

  /**
   * If the import couldn't be resolved, a message explaining why the code owner config reference
   * couldn't be resolved.
   */
  public abstract Optional<String> errorMessage();

  @Override
  public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("importingCodeOwnerConfig", importingCodeOwnerConfig())
        .add("keyOfImportedCodeOwnerConfig", keyOfImportedCodeOwnerConfig())
        .add("importedCodeOwnerConfig", importedCodeOwnerConfig())
        .add("codeOwnerConfigReference", codeOwnerConfigReference())
        .add("errorMessage", errorMessage())
        .toString();
  }

  /** Creates a {@link CodeOwnerConfigImport} instance for an unresolved import. */
  @VisibleForTesting
  public static CodeOwnerConfigImport createUnresolvedImport(
      CodeOwnerConfig importingCodeOwnerConfig,
      CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig,
      CodeOwnerConfigReference codeOwnerConfigReference,
      String errorMessage) {
    return new AutoValue_CodeOwnerConfigImport(
        importingCodeOwnerConfig,
        keyOfImportedCodeOwnerConfig,
        Optional.empty(),
        codeOwnerConfigReference,
        Optional.of(errorMessage));
  }

  /** Creates a {@link CodeOwnerConfigImport} instance for a resolved import. */
  @VisibleForTesting
  public static CodeOwnerConfigImport createResolvedImport(
      CodeOwnerConfig importingCodeOwnerConfig,
      CodeOwnerConfig importedCodeOwnerConfig,
      CodeOwnerConfigReference codeOwnerConfigReference) {
    return new AutoValue_CodeOwnerConfigImport(
        importingCodeOwnerConfig,
        importedCodeOwnerConfig.key(),
        Optional.of(importedCodeOwnerConfig),
        codeOwnerConfigReference,
        Optional.empty());
  }
}
