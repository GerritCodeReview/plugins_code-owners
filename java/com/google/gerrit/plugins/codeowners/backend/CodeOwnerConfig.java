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

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.BranchNameKey;
import java.nio.file.Path;

/** Code owner configuration for a folder in a branch. */
@AutoValue
public abstract class CodeOwnerConfig {
  /**
   * Gets the key of this code owner config.
   *
   * @return the key of this code owner config
   */
  public abstract Key key();

  /**
   * Gets the code owners of this code owner config.
   *
   * @return the code owners of this code owner config
   */
  public abstract ImmutableSet<CodeOwnerReference> codeOwners();

  /**
   * Creates a builder for a code owner config.
   *
   * @param key the key of the code owner config
   * @return builder for a code owner config
   */
  public static Builder builder(Key key) {
    return new AutoValue_CodeOwnerConfig.Builder().setKey(key);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Sets the key of this code owner config.
     *
     * @param key the key of this code owner config
     * @return the Builder instance for chaining calls
     */
    public abstract Builder setKey(Key key);

    /**
     * Sets the code owners of this code owner config.
     *
     * @param codeOwners the code owners of this code owner config
     * @return the Builder instance for chaining calls
     */
    public abstract Builder setCodeOwners(ImmutableSet<CodeOwnerReference> codeOwners);

    /**
     * Gets a builder to add code owner references.
     *
     * @return builder to add code owner references
     */
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
     * Builds the {@link CodeOwnerConfig} instance.
     *
     * @return the {@link CodeOwnerConfig} instance
     */
    public abstract CodeOwnerConfig build();
  }

  /**
   * Key of a {@link CodeOwnerConfig}.
   *
   * <p>The folder in a branch for which the code owner config defines code owners.
   */
  @AutoValue
  public abstract static class Key {
    /**
     * Gets the branch to which the code owner config belongs.
     *
     * @return the branch to which the code owner config belongs
     */
    public abstract BranchNameKey branch();

    /**
     * Gets the path of the folder to which the code owner config belongs.
     *
     * @return the path of the folder to which the code owner config belongs.
     */
    public abstract Path folderPath();

    /**
     * Creates a code owner config key.
     *
     * @param branch the branch to which the code owner config belongs
     * @param folderPath the path of the folder to which the code owner config belongs
     * @return the code owner config key
     */
    public static Key create(BranchNameKey branch, Path folderPath) {
      return new AutoValue_CodeOwnerConfig_Key.Builder()
          .setBranch(branch)
          .setFolderPath(folderPath)
          .build();
    }

    @AutoValue.Builder
    abstract static class Builder {
      /**
       * Sets the branch for this owner config key.
       *
       * @param branch the branch for this owner config key
       * @return the Builder instance for chaining calls
       */
      public abstract Builder setBranch(BranchNameKey branch);

      /**
       * Sets the folder path for this owner config key.
       *
       * @param folderPath the folder path for this owner config key
       * @return the Builder instance for chaining calls
       */
      public abstract Builder setFolderPath(Path folderPath);

      /**
       * Builds the {@link Key} instance.
       *
       * @return the {@link Key} instance
       */
      abstract Key build();
    }
  }
}
