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
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

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

  /** The revision from which this code owner config was loaded. */
  public abstract ObjectId revision();

  /**
   * Gets whether code owners from parent code owner configs (code owner configs in parent folders)
   * should be ignored.
   */
  public abstract boolean ignoreParentCodeOwners();

  /** Gets references to the code owner configs that should be imported. */
  public abstract ImmutableSet<CodeOwnerConfigReference> imports();

  /**
   * Gets the code owner sets of this code owner config.
   *
   * <p>A code owner set defines a set of code owners for a set of path expressions.
   *
   * <p>The code owner sets are stored in an {@link ImmutableSet} so that duplicate code owner sets
   * are filtered out.
   *
   * <p>The order of the code owner sets is the same in which they have been added ({@link
   * ImmutableSet} preserves the iteration order).
   */
  public abstract ImmutableSet<CodeOwnerSet> codeOwnerSets();

  /**
   * Gets the code owner sets of this code owner config as list.
   *
   * <p>For some callers it's better to retrieve the code owner sets as list since lists have a
   * defined order, e.g. for:
   *
   * <ul>
   *   <li>updating code owner sets where the updater must be aware that the order matters
   *   <li>doing test assertions where the order should be verified
   * </ul>
   *
   * @return the code owner sets of this code owner config as list
   */
  public ImmutableList<CodeOwnerSet> codeOwnerSetsAsList() {
    return codeOwnerSets().asList();
  }

  /**
   * Gets the imports of this code owner config as list.
   *
   * <p>For some callers it's better to retrieve the imports as list since lists have a defined
   * order, e.g. for:
   *
   * <ul>
   *   <li>updating imports where the updater must be aware that the order matters
   *   <li>doing test assertions where the order should be verified
   * </ul>
   *
   * @return the imports of this code owner config as list
   */
  public ImmutableList<CodeOwnerConfigReference> importsAsList() {
    return imports().asList();
  }

  /**
   * Relativizes the given path in relation to the folder path of this code owner config.
   *
   * @param path the path that should be relativized
   * @return the relativized path of the given path in relation to the folder path of this code
   *     owner config
   */
  Path relativize(Path path) {
    return key().folderPath().relativize(requireNonNull(path, "path"));
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
   * @param revision the revision from which the code owner config was loaded
   * @return builder for a code owner config
   */
  public static Builder builder(Key key, ObjectId revision) {
    return new AutoValue_CodeOwnerConfig.Builder()
        .setKey(key)
        .setRevision(revision)
        .setIgnoreParentCodeOwners(false);
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
     * Sets the revision from which this code owner config was loaded.
     *
     * @param revision the revision from which this code owner config was loaded
     * @return the Builder instance for chaining calls
     */
    public abstract Builder setRevision(ObjectId revision);

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
     * Sets the imports of this code owner config.
     *
     * @param imports the imports of this code owner config
     * @return the Builder instance for chaining calls
     */
    public Builder setImports(ImmutableList<CodeOwnerConfigReference> imports) {
      return setImports(ImmutableSet.copyOf(imports));
    }

    /**
     * Sets the imports of this code owner config.
     *
     * @param imports the imports of this code owner config
     * @return the Builder instance for chaining calls
     */
    abstract Builder setImports(ImmutableSet<CodeOwnerConfigReference> imports);

    /** Gets a builder to add references to code owner configs that should be imported. */
    abstract ImmutableSet.Builder<CodeOwnerConfigReference> importsBuilder();

    /**
     * Adds a reference to code owner configs that should be imported.
     *
     * @param codeOwnerConfigReference reference to the code owner config that should be imported
     * @return the Builder instance for chaining calls
     */
    public Builder addImport(CodeOwnerConfigReference codeOwnerConfigReference) {
      importsBuilder().add(codeOwnerConfigReference);
      return this;
    }

    /**
     * Sets that code owners from parent code owner configs (code owner configs in parent folders)
     * should be ignored.
     *
     * @return the Builder instance for chaining calls
     */
    public Builder setIgnoreParentCodeOwners() {
      return setIgnoreParentCodeOwners(true);
    }

    /** Gets the code owner sets from this builder. */
    public abstract ImmutableSet<CodeOwnerSet> codeOwnerSets();

    /**
     * Sets the code owner sets of this code owner config.
     *
     * @param codeOwnerSets the code owner sets of this code owner config
     * @return the Builder instance for chaining calls
     */
    public Builder setCodeOwnerSets(ImmutableList<CodeOwnerSet> codeOwnerSets) {
      return setCodeOwnerSets(ImmutableSet.copyOf(codeOwnerSets));
    }

    /**
     * Sets the code owner sets of this code owner config.
     *
     * @param codeOwnerSets the code owner sets of this code owner config
     * @return the Builder instance for chaining calls
     */
    abstract Builder setCodeOwnerSets(ImmutableSet<CodeOwnerSet> codeOwnerSets);

    /** Gets a builder to add code owner sets. */
    abstract ImmutableSet.Builder<CodeOwnerSet> codeOwnerSetsBuilder();

    /**
     * Adds a code owner set.
     *
     * @param codeOwnerSet the code owner set
     * @return the Builder instance for chaining calls
     */
    public Builder addCodeOwnerSet(CodeOwnerSet codeOwnerSet) {
      codeOwnerSetsBuilder().add(requireNonNull(codeOwnerSet, "codeOwnerSet"));
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
    /** Gets the project and branch to which the code owner config belongs. */
    public abstract BranchNameKey branchNameKey();

    /**
     * Gets the path of the folder to which the code owner config belongs.
     *
     * <p>The returned path is absolute.
     */
    public abstract Path folderPath();

    /**
     * Gets the name of the code owner config file.
     *
     * <p>If not set the default file of the code owner backend should be used.
     */
    public abstract Optional<String> fileName();

    /**
     * Gets the project to which the code owner config belongs.
     *
     * @return the project to which the code owner config belongs
     */
    public Project.NameKey project() {
      return branchNameKey().project();
    }

    /**
     * Gets the short name of the branch of this owner config key.
     *
     * @return the short name of the branch of this owner config key
     */
    public String shortBranchName() {
      return branchNameKey().shortName();
    }

    /**
     * Gets the ref name of the branch to which the code owner config belongs.
     *
     * @return the ref name of the branch to which the code owner config belongs
     */
    public String ref() {
      return branchNameKey().branch();
    }

    /**
     * Gets the path of the code owner config file.
     *
     * @param defaultCodeOwnerConfigFileName the name of the code owner config file that should be
     *     used if no {@link #fileName()} is set.
     * @return the path of the code owner config file
     */
    public Path filePath(String defaultCodeOwnerConfigFileName) {
      requireNonNull(defaultCodeOwnerConfigFileName, "codeOwnerConfigFileName");
      return folderPath().resolve(fileName().orElse(defaultCodeOwnerConfigFileName));
    }

    /** User-readable string representing of this code owner config key. */
    public String format(CodeOwners codeOwners) {
      return String.format("%s:%s:%s", project(), shortBranchName(), codeOwners.getFilePath(this));
    }

    /**
     * Creates a builder from this code owner config key.
     *
     * @return builder that was created from this code owner config key
     */
    public abstract Key.Builder toBuilder();

    /**
     * Creates a code owner config key.
     *
     * @param project the project to which the code owner config belongs
     * @param branch the branch to which the code owner config belongs
     * @param folderPath the path of the folder to which the code owner config belongs, must be
     *     absolute
     * @return the code owner config key
     */
    public static Key create(Project.NameKey project, String branch, String folderPath) {
      return create(BranchNameKey.create(project, branch), Paths.get(folderPath));
    }

    /**
     * Creates a code owner config key.
     *
     * @param project the project to which the code owner config belongs
     * @param branch the branch to which the code owner config belongs
     * @param folderPath the path of the folder to which the code owner config belongs, must be
     *     absolute
     * @param fileName the name of the code owner config file
     * @return the code owner config key
     */
    public static Key create(
        Project.NameKey project, String branch, String folderPath, @Nullable String fileName) {
      return create(BranchNameKey.create(project, branch), Paths.get(folderPath), fileName);
    }

    /**
     * Creates a code owner config key.
     *
     * @param branchNameKey the project and branch to which the code owner config belongs
     * @param folderPath the path of the folder to which the code owner config belongs, must be
     *     absolute
     * @return the code owner config key
     */
    public static Key create(BranchNameKey branchNameKey, Path folderPath) {
      return create(branchNameKey, folderPath, /* fileName= */ null);
    }

    /**
     * Creates a code owner config key.
     *
     * @param branchNameKey the project and branch to which the code owner config belongs
     * @param folderPath the path of the folder to which the code owner config belongs, must be
     *     absolute
     * @param fileName the name of the code owner config file
     * @return the code owner config key
     */
    public static Key create(
        BranchNameKey branchNameKey, Path folderPath, @Nullable String fileName) {
      Builder builder =
          new AutoValue_CodeOwnerConfig_Key.Builder()
              .setBranchNameKey(branchNameKey)
              .setFolderPath(folderPath);

      if (fileName != null) {
        builder.setFileName(fileName);
      }

      return builder.build();
    }

    /**
     * Create a key with the file name being set.
     *
     * @param codeOwnerBackend code owner backend that should be used to lookup the file name if it
     *     is not set yet
     * @param project the project to which the code owner config belongs
     * @param branch the branch to which the code owner config belongs
     * @param folderPath the path of the folder to which the code owner config belongs, must be
     *     absolute
     * @return the code owner config key with the file name being set
     */
    public static Key createWithFileName(
        CodeOwnerBackend codeOwnerBackend,
        Project.NameKey project,
        String branch,
        String folderPath) {
      Key key = create(project, branch, folderPath);
      return key.toBuilder()
          .setFileName(codeOwnerBackend.getFilePath(key).getFileName().toString())
          .build();
    }

    @AutoValue.Builder
    public abstract static class Builder {
      /**
       * Sets the project and branch for this owner config key.
       *
       * @param branchNameKey the project and branch for this owner config key
       * @return the Builder instance for chaining calls
       */
      public abstract Builder setBranchNameKey(BranchNameKey branchNameKey);

      /**
       * Sets the folder path for this owner config key.
       *
       * @param folderPath the folder path for this owner config key, must be absolute
       * @return the Builder instance for chaining calls
       */
      public abstract Builder setFolderPath(Path folderPath);

      /**
       * Sets the name of the code owner config file for this owner config key.
       *
       * @param fileName the name of the code owner config file
       * @return the Builder instance for chaining calls
       */
      public abstract Builder setFileName(String fileName);

      /**
       * Builds the {@link Key} instance.
       *
       * @return the {@link Key} instance
       */
      abstract Key autoBuild();

      /**
       * Builds the {@link Key} instance with validation.
       *
       * @return the {@link Key} instance
       */
      public Key build() {
        Key key = autoBuild();
        checkState(
            key.folderPath().isAbsolute(), "folder path %s must be absolute", key.folderPath());
        return key;
      }
    }
  }
}
