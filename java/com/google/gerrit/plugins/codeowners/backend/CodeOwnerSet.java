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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * A code owner set defines a set of code owners for a set of path expressions.
 *
 * <p>The code owners own the files that match any of the path expressions.
 *
 * <p>Code owner sets are stored in {@link CodeOwnerConfig}s which define code owners for the folder
 * in which they are stored.
 *
 * <p>The path expressions are relative to the folder of the {@link CodeOwnerConfig} that contains
 * the owner set.
 *
 * <p>If the set of path expressions is empty the code owners apply for all files in the folder of
 * the {@link CodeOwnerConfig} (including files in sub folders).
 */
@AutoValue
public abstract class CodeOwnerSet {
  // TODO(ekempin): add field for a set of path expressions (relative to the folder of the code
  // owner config)

  /** Gets the code owners of this code owner set. */
  public abstract ImmutableSet<CodeOwnerReference> codeOwners();

  /**
   * Whether this owner set matches the given path.
   *
   * <p>A path matches the owner set, if any of its path expressions matches the path.
   *
   * @param path path for which it should be checked whether it matches this owner set; the path
   *     must be relative to the path in which the {@link CodeOwnerConfig} that contains this code
   *     owner set is stored; can be the path of a file or folder; the path may or may not exist
   * @return whether this owner set matches the given path
   */
  boolean matches(Path path) {
    checkState(!requireNonNull(path, "path").isAbsolute(), "path %s must be relative", path);

    // TODO(ekempin): Check if any of the path expressions match, once we have path expressions.
    return true;
  }

  /**
   * Creates a builder form this code owner set.
   *
   * @return builder that was created from this code owner set
   */
  public abstract Builder toBuilder();

  /** Creates a builder for a {@link CodeOwnerSet}. */
  public static CodeOwnerSet.Builder builder() {
    return new AutoValue_CodeOwnerSet.Builder();
  }

  /** Creates a {@link CodeOwnerSet} instance for the given set of code owners. */
  public static CodeOwnerSet create(ImmutableSet<CodeOwnerReference> codeOwners) {
    return builder().setCodeOwners(codeOwners).build();
  }

  /** Creates a {@link CodeOwnerSet} instance for the given emails. */
  public static CodeOwnerSet createForEmails(String... emails) {
    return create(Stream.of(emails).map(CodeOwnerReference::create).collect(toImmutableSet()));
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Sets the code owners of this code owner set.
     *
     * @param codeOwners the code owners of this code owner set
     * @return the Builder instance for chaining calls
     */
    public abstract Builder setCodeOwners(ImmutableSet<CodeOwnerReference> codeOwners);

    /** Gets a builder to add code owner references. */
    abstract ImmutableSet.Builder<CodeOwnerReference> codeOwnersBuilder();

    /**
     * Adds a code owner.
     *
     * @param codeOwnerReference reference to the code owner
     * @return the Builder instance for chaining calls
     */
    public Builder addCodeOwner(CodeOwnerReference codeOwnerReference) {
      codeOwnersBuilder().add(requireNonNull(codeOwnerReference, "codeOwnerReference"));
      return this;
    }

    /**
     * Adds a code owner for the given email.
     *
     * @param email email of the code owner
     * @return the Builder instance for chaining calls
     */
    public Builder addCodeOwnerEmail(String email) {
      return addCodeOwner(CodeOwnerReference.create(requireNonNull(email, "codeOwnerEmail")));
    }

    /** Builds the {@link CodeOwnerSet} instance. */
    public abstract CodeOwnerSet build();
  }
}
