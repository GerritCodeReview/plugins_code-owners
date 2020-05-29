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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.testsuite.ThrowingConsumer;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import java.util.Set;
import java.util.function.Function;

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
   * Defines how the code owners of the code owner config should be modified. By default (that is if
   * nothing is specified), the code owners remain unchanged.
   *
   * @return a function which gets the current code owners of the code owner config as input and
   *     outputs the desired resulting code owners
   */
  public abstract Function<ImmutableSet<CodeOwnerReference>, Set<CodeOwnerReference>>
      codeOwnerModification();

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
        .codeOwnerModification(in -> in);
  }

  /** Builder for a {@link TestCodeOwnerConfigUpdate}. */
  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Sets the code owner modification.
     *
     * @return the Builder instance for chaining calls
     * @see TestCodeOwnerConfigUpdate#codeOwnerModification()
     */
    abstract Builder codeOwnerModification(
        Function<ImmutableSet<CodeOwnerReference>, Set<CodeOwnerReference>> codeOwnerModification);

    /**
     * Gets the code owner modification.
     *
     * @see TestCodeOwnerConfigUpdate#codeOwnerModification()
     */
    abstract Function<ImmutableSet<CodeOwnerReference>, Set<CodeOwnerReference>>
        codeOwnerModification();

    /**
     * Removes all code owners.
     *
     * @return the Builder instance for chaining calls
     */
    public Builder clearCodeOwners() {
      return codeOwnerModification(originalCodeOwners -> ImmutableSet.of());
    }

    /**
     * Adds a code owner.
     *
     * @param codeOwner code owner that should be added
     * @return the Builder instance for chaining calls
     */
    public Builder addCodeOwner(CodeOwnerReference codeOwner) {
      Function<ImmutableSet<CodeOwnerReference>, Set<CodeOwnerReference>> previousModification =
          codeOwnerModification();
      codeOwnerModification(
          originalCodeOwners ->
              Sets.union(
                  previousModification.apply(originalCodeOwners), ImmutableSet.of(codeOwner)));
      return this;
    }

    /**
     * Adds a code owner by email.
     *
     * @param codeOwnerEmail email of the code owner that should be added
     * @return the Builder instance for chaining calls
     */
    public Builder addCodeOwnerEmail(String codeOwnerEmail) {
      return addCodeOwner(CodeOwnerReference.create(codeOwnerEmail));
    }

    /**
     * Removes a code owner.
     *
     * <p>Removing a non-existing code owner is a no-op.
     *
     * @param codeOwner code owner that should be removed
     * @return the Builder instance for chaining calls
     */
    public Builder removeCodeOwner(CodeOwnerReference codeOwner) {
      Function<ImmutableSet<CodeOwnerReference>, Set<CodeOwnerReference>> previousModification =
          codeOwnerModification();
      codeOwnerModification(
          originalCodeOwners ->
              Sets.difference(
                  previousModification.apply(originalCodeOwners), ImmutableSet.of(codeOwner)));
      return this;
    }

    /**
     * Removes a code owner by email.
     *
     * <p>Removing a non-existing code owner is a no-op.
     *
     * @param codeOwnerEmail email of code owner that should be removed
     * @return the Builder instance for chaining calls
     */
    public Builder removeCodeOwnerEmail(String codeOwnerEmail) {
      return removeCodeOwner(CodeOwnerReference.create(codeOwnerEmail));
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
