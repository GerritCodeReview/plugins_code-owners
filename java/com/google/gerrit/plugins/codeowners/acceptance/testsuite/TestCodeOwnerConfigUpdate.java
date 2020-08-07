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

package com.google.gerrit.plugins.codeowners.acceptance.testsuite;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.testsuite.ThrowingConsumer;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportModification;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSetModification;
import java.util.Optional;

/**
 * Test API to update a code owner config.
 *
 * <p>To execute the operations, no Gerrit permissions are necessary.
 *
 * <p><strong>Note:</strong> This interface is not implemented using the REST or extension API.
 * Hence, it cannot be used for testing those APIs.
 */
@AutoValue
public abstract class TestCodeOwnerConfigUpdate {
  /**
   * Gets the new value for the ignore parent code owners setting. {@link Optional#empty()} if the
   * ignore parent code owners setting should not be modified.
   */
  public abstract Optional<Boolean> ignoreParentCodeOwners();

  /**
   * Defines how the code owner sets of the code owner config should be modified. By default (that
   * is if nothing is specified), the code owner sets remain unchanged.
   *
   * @return a function which gets the current code owner sets of the code owner config as input and
   *     outputs the desired resulting code owner sets
   */
  public abstract CodeOwnerSetModification codeOwnerSetsModification();

  /**
   * Defines how the imports of the code owner config should be modified. By default (that is if
   * nothing is specified), the imports remain unchanged.
   *
   * @return a function which gets the current imports of the code owner config as input and outputs
   *     the desired resulting imports
   */
  public abstract CodeOwnerConfigImportModification importsModification();

  /**
   * Gets the function that updates the code owner config.
   *
   * @return the function that updates the code owner config
   */
  abstract ThrowingConsumer<TestCodeOwnerConfigUpdate> codeOwnerConfigUpdater();

  /**
   * Creates a builder for a {@link TestCodeOwnerConfigUpdate}.
   *
   * @param codeOwnerConfigUpdater function that updates the code owner config
   * @return builder for a {@link TestCodeOwnerConfigUpdate}
   */
  public static Builder builder(
      ThrowingConsumer<TestCodeOwnerConfigUpdate> codeOwnerConfigUpdater) {
    return new AutoValue_TestCodeOwnerConfigUpdate.Builder()
        .codeOwnerConfigUpdater(codeOwnerConfigUpdater)
        .codeOwnerSetsModification(CodeOwnerSetModification.keep())
        .importsModification(CodeOwnerConfigImportModification.keep());
  }

  /** Builder for a {@link TestCodeOwnerConfigUpdate}. */
  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Sets whether code owners from parent code owner configs (code owner configs in parent
     * folders) should be ignored.
     *
     * @param ignoreParentCodeOwners whether code owners from parent code owner configs should be
     *     ignored
     * @return the Builder instance for chaining calls
     */
    public abstract Builder ignoreParentCodeOwners(boolean ignoreParentCodeOwners);

    /**
     * Sets that code owners from parent code owner configs (code owner configs in parent folders)
     * should be ignored.
     *
     * @return the Builder instance for chaining calls
     */
    public Builder ignoreParentCodeOwners() {
      return ignoreParentCodeOwners(true);
    }

    /**
     * Sets the code owner set modification.
     *
     * @return the Builder instance for chaining calls
     * @see TestCodeOwnerConfigUpdate#codeOwnerSetsModification()
     */
    public abstract Builder codeOwnerSetsModification(
        CodeOwnerSetModification codeOwnerSetsModification);

    /**
     * Gets the code owner set modification.
     *
     * @see TestCodeOwnerConfigUpdate#codeOwnerSetsModification()
     */
    abstract CodeOwnerSetModification codeOwnerSetsModification();

    /**
     * Removes all code owner sets.
     *
     * @return the Builder instance for chaining calls
     */
    public Builder clearCodeOwnerSets() {
      return codeOwnerSetsModification(CodeOwnerSetModification.clear());
    }

    /**
     * Adds an import.
     *
     * @param codeOwnerConfigReference reference to the code owner config that should be imported
     * @return the Builder instance for chaining calls
     */
    public Builder addImport(CodeOwnerConfigReference codeOwnerConfigReference) {
      CodeOwnerConfigImportModification previousModification = importsModification();
      importsModification(
          originalImports ->
              new ImmutableList.Builder<CodeOwnerConfigReference>()
                  .addAll(previousModification.apply(originalImports))
                  .add(codeOwnerConfigReference)
                  .build());
      return this;
    }

    /**
     * Sets the import modification.
     *
     * @return the Builder instance for chaining calls
     * @see TestCodeOwnerConfigUpdate#importsModification()
     */
    public abstract Builder importsModification(
        CodeOwnerConfigImportModification importsModification);

    /**
     * Gets the import modification.
     *
     * @see TestCodeOwnerConfigUpdate#importsModification()
     */
    abstract CodeOwnerConfigImportModification importsModification();

    /**
     * Removes all imports.
     *
     * @return the Builder instance for chaining calls
     */
    public Builder clearImports() {
      return importsModification(CodeOwnerConfigImportModification.clear());
    }

    /**
     * Adds a code owner set.
     *
     * @param codeOwnerSet code owner set that should be added
     * @return the Builder instance for chaining calls
     */
    public Builder addCodeOwnerSet(CodeOwnerSet codeOwnerSet) {
      CodeOwnerSetModification previousModification = codeOwnerSetsModification();
      codeOwnerSetsModification(
          originalCodeOwnerSets ->
              new ImmutableList.Builder<CodeOwnerSet>()
                  .addAll(previousModification.apply(originalCodeOwnerSets))
                  .add(codeOwnerSet)
                  .build());
      return this;
    }

    /**
     * Sets the function that updates the code owner config.
     *
     * @param codeOwnerConfigUpdater the function that updates the code owner config
     * @return the Builder instance for chaining calls
     */
    abstract Builder codeOwnerConfigUpdater(
        ThrowingConsumer<TestCodeOwnerConfigUpdate> codeOwnerConfigUpdater);

    /**
     * Builds the {@link TestCodeOwnerConfigUpdate} instance.
     *
     * @return the {@link TestCodeOwnerConfigUpdate} instance
     */
    abstract TestCodeOwnerConfigUpdate autoBuild();

    /** Executes the code owner config update as specified. */
    public void update() {
      TestCodeOwnerConfigUpdate codeOwnerConfigUpdater = autoBuild();
      codeOwnerConfigUpdater
          .codeOwnerConfigUpdater()
          .acceptAndThrowSilently(codeOwnerConfigUpdater);
    }
  }
}
