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
import java.util.Optional;

/**
 * Definition of an update to a {@link CodeOwnerConfig}.
 *
 * <p>A {@code CodeOwnerConfigUpdate} only specifies the modifications which should be applied to a
 * code owner config. Each of the modifications and hence each call on {@link
 * CodeOwnerConfigUpdate.Builder} is optional.
 */
@AutoValue
public abstract class CodeOwnerConfigUpdate {
  /**
   * Gets the new value for the ignore parent code owners setting. {@link Optional#empty()} if the
   * ignore parent code owners setting should not be modified.
   */
  public abstract Optional<Boolean> ignoreParentCodeOwners();

  /**
   * Defines how the code owners of the code owner config should be modified. By default (that is if
   * nothing is specified), the code owners remain unchanged.
   *
   * @return a {@link CodeOwnerSetModification} which gets the current code owner sets of the code
   *     owner config as input and outputs the desired resulting code owner sets
   */
  public abstract CodeOwnerSetModification codeOwnerSetsModification();

  /**
   * Creates a builder for a {@link CodeOwnerConfigUpdate}.
   *
   * @return builder for a {@link CodeOwnerConfigUpdate}
   */
  public static Builder builder() {
    return new AutoValue_CodeOwnerConfigUpdate.Builder()
        .setCodeOwnerSetsModification(codeOwnerSets -> codeOwnerSets);
  }

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
    public abstract Builder setIgnoreParentCodeOwners(Optional<Boolean> ignoreParentCodeOwners);

    /**
     * Sets whether code owners from parent code owner configs (code owner configs in parent
     * folders) should be ignored.
     *
     * @param ignoreParentCodeOwners whether code owners from parent code owner configs should be
     *     ignored
     * @return the Builder instance for chaining calls
     */
    public abstract Builder setIgnoreParentCodeOwners(boolean ignoreParentCodeOwners);

    /**
     * Sets the code owner modification.
     *
     * @see #codeOwnerSetsModification()
     */
    public abstract Builder setCodeOwnerSetsModification(
        CodeOwnerSetModification codeOwnerSetsModification);

    /**
     * Builds the {@link CodeOwnerConfigUpdate} instance.
     *
     * @return the {@link CodeOwnerConfigUpdate} instance
     */
    public abstract CodeOwnerConfigUpdate build();
  }
}
