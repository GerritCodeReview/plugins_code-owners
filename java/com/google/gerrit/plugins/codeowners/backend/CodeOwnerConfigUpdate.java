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
import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 * Definition of an update to a {@link CodeOwnerConfig}.
 *
 * <p>A {@code CodeOwnerConfigUpdate} only specifies the modifications which should be applied to a
 * code owner config. Each of the modifications and hence each call on {@link
 * CodeOwnerConfigUpdate.Builder} is optional.
 */
@AutoValue
public abstract class CodeOwnerConfigUpdate {
  /** Representation of a code owner modification as defined by {@link #apply(ImmutableSet)}. */
  @FunctionalInterface
  public interface CodeOwnerModification {

    /**
     * Applies the modification to the given code owners.
     *
     * @param originalCodeOwners current code owners of the code owner config. If used for a code
     *     owner config creation, this set is empty.
     * @return the desired resulting code owners (not the diff of the code owners!)
     */
    Set<CodeOwnerReference> apply(ImmutableSet<CodeOwnerReference> originalCodeOwners);
  }

  /**
   * Defines how the code owners of the code owner config should be modified. By default (that is if
   * nothing is specified), the code owners remain unchanged.
   *
   * @return a {@link CodeOwnerModification} which gets the current code owners of the code owner
   *     config as input and outputs the desired resulting code owners
   */
  public abstract CodeOwnerModification codeOwnerModification();

  /**
   * Creates a builder for a {@link CodeOwnerConfigUpdate}.
   *
   * @return builder for a {@link CodeOwnerConfigUpdate}
   */
  public static Builder builder() {
    return new AutoValue_CodeOwnerConfigUpdate.Builder()
        .setCodeOwnerModification(codeOwners -> codeOwners);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Sets the code owner modification.
     *
     * @see #codeOwnerModification()
     */
    public abstract Builder setCodeOwnerModification(CodeOwnerModification codeOwnerModification);

    /**
     * Builds the {@link CodeOwnerConfigUpdate} instance.
     *
     * @return the {@link CodeOwnerConfigUpdate} instance
     */
    public abstract CodeOwnerConfigUpdate build();
  }
}
