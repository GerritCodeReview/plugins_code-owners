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
import com.google.gerrit.entities.Project;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Code owner configuration for a folder in a branch.
 *
 * <p>This representation of a code owner configuration is independent of any code owner backend and
 * hence independent of any specific syntax of the code owner configuration files.
 */
@AutoValue
public abstract class CodeOwnerConfig {
  /** Gets the key of this code owner config. */
  public abstract Key key();

  // TODO(ekempin): add field for included code owner configs

  /** Gets the code owners of this code owner config. */
  public abstract ImmutableSet<CodeOwnerReference> codeOwners();

  // TODO(ekempin): add field for per-file code owners

  /**
   * Computes the local code owners for the given path.
   *
   * <p>This method computes the <strong>local</strong> code owners which means that only code
   * owners that are directly mentioned in this code owner config are considered. Code owners in
   * inherited and included code owner configs are not considered.
   *
   * @param relativePath path for which the local code owners should be returned; the path must be
   *     relative to the {@link Key#folderPath()} of this code owner config; can be the path of a
   *     file or folder; the path may or may not exist
   * @return the local code owners for the given path
   */
  public ImmutableSet<CodeOwnerReference> localCodeOwners(Path relativePath) {
    requireNonNull(relativePath, "relativePath");
    // TODO(ekempin): Check for matching per-file code owner definitions once we support per-file
    // code owner definitions
    return codeOwners();
  }

  /**
   * Creates a builder form this code owner config.
   *
   * @return builder that was created from this code owner config
   */
  public abstract Builder toBuilder();

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

    /** Gets a builder to add code owner references. */
    abstract ImmutableSet.Builder<CodeOwnerReference> codeOwnersBuilder();

    /**
     * Adds a code owner for the given email.
     *
     * @param email email of the code owner
     * @return the Builder instance for chaining calls
     */
    public Builder addCodeOwnerEmail(String email) {
      return addCodeOwner(CodeOwnerReference.create(requireNonNull(email, "codeOwnerEmail")));
    }

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
    /** Gets the branch to which the code owner config belongs. */
    public abstract BranchNameKey branch();

    /** Gets the path of the folder to which the code owner config belongs. */
    public abstract Path folderPath();

    /**
     * Gets the project to which the code owner config belongs.
     *
     * @return the project to which the code owner config belongs
     */
    public Project.NameKey project() {
      return branch().project();
    }

    /**
     * Gets the short name of the branch of this owner config key.
     *
     * @return the short name of the branch of this owner config key
     */
    public String shortBranchName() {
      return branch().shortName();
    }

    /**
     * Gets the ref name of the branch to which the code owner config belongs.
     *
     * @return the ref name of the branch to which the code owner config belongs
     */
    public String ref() {
      return branch().branch();
    }

    /**
     * Gets the path for the given code owner config file name.
     *
     * @param codeOwnerConfigFileName the name of the code owner config file for which the path
     *     should be returned
     * @return the path for the given file code owner config file name
     */
    public Path filePath(String codeOwnerConfigFileName) {
      return folderPath()
          .resolve(requireNonNull(codeOwnerConfigFileName, "codeOwnerConfigFileName"));
    }

    /**
     * Gets the path for the given code owner config file name as string that can be used for the
     * jgit API.
     *
     * <p>The jgit API doesn't accept leading '/' for absolute paths, so this method makes sure to
     * remove it.
     *
     * @param codeOwnerConfigFileName the name of the code owner config file for which the path
     *     should be returned
     * @return the path for the given file code owner config file name as string that can be used
     *     for the jgit API
     */
    public String filePathForJgit(String codeOwnerConfigFileName) {
      String filePath = filePath(codeOwnerConfigFileName).toString();
      if (filePath.startsWith("/")) {
        filePath = filePath.substring(1);
      }
      return filePath;
    }

    /**
     * Creates a code owner config key.
     *
     * @param project the project to which the code owner config belongs
     * @param branch the branch to which the code owner config belongs
     * @param folderPath the path of the folder to which the code owner config belongs
     * @return the code owner config key
     */
    public static Key create(Project.NameKey project, String branch, String folderPath) {
      return create(BranchNameKey.create(project, branch), Paths.get(folderPath));
    }

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
