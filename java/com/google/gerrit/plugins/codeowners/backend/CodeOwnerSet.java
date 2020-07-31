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
import java.util.Arrays;

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
  /**
   * Gets whether global code owners (code owners from code owner sets without path expression in
   * the same code owner config) and code owners from parent code owner configs (code owner configs
   * in parent folders) should be ignored.
   *
   * <p>Code owner configs are organized hierarchically, e.g. the code owner config at "/" is the
   * parent config of the code owner config at "/foo" which in turn is the parent config of the code
   * owner config at "/foo/bar". Code owners from the parent config can be ignored by setting {@link
   * CodeOwnerConfig#ignoreParentCodeOwners()} on code owner config level.
   *
   * <p>In addition there are 2 hierarchy levels within each code owner config. 1. global code
   * owners applying to all files in the folder (represented by code owner sets without path
   * expressions), 2. per file code owners (represented by code owner sets with path expressions).
   * On per file level it is possible to ignore the global code owners (code owner sets without path
   * expressions) by setting {@link #ignoreGlobalAndParentCodeOwners()} on the code owner set. If
   * {@link #ignoreGlobalAndParentCodeOwners()} is set, implicitly for matching files also all code
   * owners inherited from parent code owner configs are ignored.
   *
   * <p>If a matching code owner set ignores glabel and parent code owners, matching sibling code
   * owner sets (other code owner sets with matching path expressions in the same code owner config)
   * are still honored.
   */
  public abstract boolean ignoreGlobalAndParentCodeOwners();

  /** Path expressions that match the files that are owned by the {@link #codeOwners()}. */
  public abstract ImmutableSet<String> pathExpressions();

  /** Gets the code owners of this code owner set. */
  public abstract ImmutableSet<CodeOwnerReference> codeOwners();

  /**
   * Creates a builder from this code owner set.
   *
   * @return builder that was created from this code owner set
   */
  public abstract Builder toBuilder();

  /** Creates a builder for a {@link CodeOwnerSet}. */
  public static CodeOwnerSet.Builder builder() {
    return new AutoValue_CodeOwnerSet.Builder()
        .setIgnoreGlobalAndParentCodeOwners(false)
        .setPathExpressions(ImmutableSet.of());
  }

  /**
   * Creates a {@link CodeOwnerSet} instance without path expressions.
   *
   * @param codeOwners the code owners of the code owner set
   */
  public static CodeOwnerSet createWithoutPathExpressions(
      ImmutableSet<CodeOwnerReference> codeOwners) {
    return builder().setCodeOwners(codeOwners).build();
  }

  /**
   * Creates a {@link CodeOwnerSet} instance without path expressions.
   *
   * @param emails the emails of the code owners
   */
  public static CodeOwnerSet createWithoutPathExpressions(String... emails) {
    return createWithoutPathExpressions(
        Arrays.stream(emails).map(CodeOwnerReference::create).collect(toImmutableSet()));
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Sets whether global code owners (code owners from code owner sets without path expression in
     * the same code owner config) and code owners from parent code owner configs (code owner
     * configs in parent folders) should be ignored.
     *
     * @param ignoreGlobalAndParentCodeOwners whether global code owners and code owners from parent
     *     code owner configs should be ignored
     * @return the Builder instance for chaining calls
     * @see CodeOwnerSet#ignoreGlobalAndParentCodeOwners()
     */
    public abstract Builder setIgnoreGlobalAndParentCodeOwners(
        boolean ignoreGlobalAndParentCodeOwners);

    /**
     * Sets that global code owners (code owners from code owner sets without path expression in the
     * same code owner config) and code owners from parent code owner configs (code owner configs in
     * parent folders) should be ignored.
     *
     * @return the Builder instance for chaining calls
     * @see CodeOwnerSet#ignoreGlobalAndParentCodeOwners()
     */
    public Builder setIgnoreGlobalAndParentCodeOwners() {
      return setIgnoreGlobalAndParentCodeOwners(true);
    }

    /**
     * Sets the path expressions that match the files that are owned by the code owners.
     *
     * @param pathExpressions the path expressions
     * @return the Builder instance for chaining calls
     */
    public abstract Builder setPathExpressions(ImmutableSet<String> pathExpressions);

    /** Gets a builder to add path expressions. */
    abstract ImmutableSet.Builder<String> pathExpressionsBuilder();

    /**
     * Adds a path expression.
     *
     * @param pathExpression path expression that should be added
     * @return the Builder instance for chaining calls
     */
    public Builder addPathExpression(String pathExpression) {
      pathExpressionsBuilder().add(requireNonNull(pathExpression, "pathExpression"));
      return this;
    }

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
    public CodeOwnerSet build() {
      CodeOwnerSet codeOwnerSet = autoBuild();
      checkState(
          !(codeOwnerSet.ignoreGlobalAndParentCodeOwners()
              && codeOwnerSet.pathExpressions().isEmpty()),
          "ignoreParentCodeOwners = true is not allowed for code owner set without path expressions");
      return codeOwnerSet;
    }

    abstract CodeOwnerSet autoBuild();
  }
}
