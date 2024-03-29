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

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.testsuite.ThrowingFunction;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Test API to create a code owner config.
 *
 * <p>To execute the operations, no Gerrit permissions are necessary.
 *
 * <p><strong>Note:</strong> This interface is not implemented using the REST or extension API.
 * Hence, it cannot be used for testing those APIs.
 */
@AutoValue
public abstract class TestCodeOwnerConfigCreation {
  /**
   * Gets the project in which the code owner config should be created.
   *
   * @return the project in which the code owner config should be created, {@link Optional#empty()}
   *     if the caller didn't specify a project for the code owner config creation
   */
  public abstract Optional<Project.NameKey> project();

  /**
   * Gets the branch in which the code owner config should be created.
   *
   * @return the branch in which the code owner config should be created, {@link Optional#empty()}
   *     if the caller didn't specify a branch for the code owner config creation
   */
  public abstract Optional<String> branch();

  /**
   * Gets the folder path in which the code owner config should be created.
   *
   * @return the folder path in which the code owner config should be created, {@link
   *     Optional#empty()} if the caller didn't specify a folder path for the code owner config
   *     creation
   */
  public abstract Optional<Path> folderPath();

  /**
   * Gets the file name for the code owner config.
   *
   * @return the file name for the code owner config, {@link Optional#empty()} if the caller didn't
   *     specify a file name for the code owner config creation
   */
  public abstract Optional<String> fileName();

  /**
   * Gets whether code owners from parent code owner configs (code owner configs in parent folders)
   * should be ignored.
   */
  public abstract boolean ignoreParentCodeOwners();

  /**
   * Gets the global code owners (without path expression) that should be set in the newly created
   * code owner config.
   *
   * @return the global code owners that should be set in the newly created code owner config
   */
  public abstract ImmutableSet<CodeOwnerReference> globalCodeOwners();

  /**
   * Gets the code owner sets that have been set for the new code owner config.
   *
   * <p>Doesn't include the code owner set for the global code owners that are defined by {@link
   * #globalCodeOwners()}. Use {@link #computeCodeOwnerSets()} to get the code owner set for the
   * global code owners included.
   *
   * @return the code owner sets that have been set for the new code owner config
   */
  abstract ImmutableList<CodeOwnerSet> codeOwnerSets();

  /**
   * Gets the imports that have been set for the new code owner config.
   *
   * @return the imports that have been set for the new code owner config
   */
  abstract ImmutableList<CodeOwnerConfigReference> imports();

  /**
   * Gets the code owner sets that should be set in the newly created code owner config.
   *
   * <p>Includes the global code owners that are defined by {@link #globalCodeOwners()}.
   *
   * @return the code owner sets that should be set in the newly created code owner config
   */
  public ImmutableList<CodeOwnerSet> computeCodeOwnerSets() {
    if (globalCodeOwners().isEmpty()) {
      return codeOwnerSets();
    }

    return ImmutableList.<CodeOwnerSet>builder()
        .add(CodeOwnerSet.createWithoutPathExpressions(globalCodeOwners()))
        .addAll(codeOwnerSets())
        .build();
  }

  /**
   * Returns the key for the code owner config that should be created.
   *
   * <p>If no project was specified, {@link IllegalStateException} is thrown.
   *
   * <p>If no branch was specified, {@code master} is used.
   *
   * <p>If no folder path was sepcified, {@code /} is used.
   *
   * @return the key for the code owner config that should be created
   */
  public CodeOwnerConfig.Key key() {
    Project.NameKey projectName =
        project()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "project not specified, specifying a project is required for code owner config creation"));
    String branchName = branch().orElse("master");
    Path folderPath = folderPath().orElse(Path.of("/"));
    return CodeOwnerConfig.Key.create(
        BranchNameKey.create(projectName, branchName), folderPath, fileName().orElse(null));
  }

  /** Returns whether the code owner config would be empty. */
  public boolean isEmpty() {
    return !ignoreParentCodeOwners() && computeCodeOwnerSets().isEmpty() && imports().isEmpty();
  }

  /**
   * Gets the function that creates the code owner config.
   *
   * @return the function that creates the code owner config
   */
  abstract ThrowingFunction<TestCodeOwnerConfigCreation, CodeOwnerConfig.Key>
      codeOwnerConfigCreator();

  /**
   * Creates a builder for a {@link TestCodeOwnerConfigCreation}.
   *
   * @param codeOwnerConfigCreator function that creates the code owner config
   * @return builder for a {@link TestCodeOwnerConfigCreation}
   */
  public static Builder builder(
      ThrowingFunction<TestCodeOwnerConfigCreation, CodeOwnerConfig.Key> codeOwnerConfigCreator) {
    return new AutoValue_TestCodeOwnerConfigCreation.Builder()
        .ignoreParentCodeOwners(false)
        .codeOwnerConfigCreator(codeOwnerConfigCreator);
  }

  /** Builder for a {@link TestCodeOwnerConfigCreation}. */
  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Sets the project in which the code owner config should be created.
     *
     * <p>Setting a project is mandatory. If a project is not set the creation of the code owner
     * config will fail.
     *
     * @param project the project in which the code owner config should be created
     * @return the Builder instance for chaining calls
     */
    public abstract Builder project(Project.NameKey project);

    /**
     * Sets the branch in which the code owner config should be created.
     *
     * <p>Setting a branch is optional. If a branch is not set {@code master} is used as default.
     *
     * @param branch the branch in which the code owner config should be created
     * @return the Builder instance for chaining calls
     */
    public abstract Builder branch(String branch);

    /**
     * Sets the folder path in which the code owner config should be created.
     *
     * <p>Setting a folder path is optional. If a folder path is not set {@code /} is used as
     * default.
     *
     * @param folderPath the folder path in which the code owner config should be created
     * @return the Builder instance for chaining calls
     */
    public abstract Builder folderPath(Path folderPath);

    /**
     * Sets the folder path in which the code owner config should be created.
     *
     * @param folderPath the folder path in which the code owner config should be created
     * @return the Builder instance for chaining calls
     */
    public Builder folderPath(String folderPath) {
      return folderPath(Path.of(folderPath));
    }

    /**
     * Sets the file name for the code owner config.
     *
     * @param fileName the file name for the code owner config
     * @return the Builder instance for chaining calls
     */
    public abstract Builder fileName(String fileName);

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
     * Gets a builder to add global code owners
     *
     * @return builder to add global code owners
     */
    abstract ImmutableSet.Builder<CodeOwnerReference> globalCodeOwnersBuilder();

    /**
     * Adds a global code owner for the given email.
     *
     * @param codeOwnerEmail email of the code owner
     * @return the Builder instance for chaining calls
     */
    public Builder addCodeOwnerEmail(String codeOwnerEmail) {
      return addCodeOwner(
          CodeOwnerReference.create(requireNonNull(codeOwnerEmail, "codeOwnerEmail")));
    }

    /**
     * Adds a global code owner.
     *
     * @param codeOwnerReference reference to the code owner
     * @return the Builder instance for chaining calls
     */
    Builder addCodeOwner(CodeOwnerReference codeOwnerReference) {
      globalCodeOwnersBuilder().add(requireNonNull(codeOwnerReference, "codeOwnerReference"));
      return this;
    }

    /**
     * Gets a builder to add code owner sets.
     *
     * @return builder to add code owner sets
     */
    abstract ImmutableList.Builder<CodeOwnerSet> codeOwnerSetsBuilder();

    /**
     * Adds a code owner set.
     *
     * @param codeOwnerSet code owner set that should be added
     * @return the Builder instance for chaining calls
     */
    public Builder addCodeOwnerSet(CodeOwnerSet codeOwnerSet) {
      codeOwnerSetsBuilder().add(requireNonNull(codeOwnerSet, "codeOwnerSet"));
      return this;
    }

    /**
     * Gets a builder to add imports.
     *
     * @return builder to add imports
     */
    abstract ImmutableList.Builder<CodeOwnerConfigReference> importsBuilder();

    /**
     * Adds an import.
     *
     * @param codeOwnerConfigReference reference to the code owner config that should be imported
     * @return the Builder instance for chaining calls
     */
    public Builder addImport(CodeOwnerConfigReference codeOwnerConfigReference) {
      importsBuilder().add(requireNonNull(codeOwnerConfigReference, "codeOwnerConfigReference"));
      return this;
    }

    /**
     * Sets the function that creates the code owner config.
     *
     * @param codeOwnerConfigCreator the function that creates the code owner config
     * @return the Builder instance for chaining calls
     */
    abstract Builder codeOwnerConfigCreator(
        ThrowingFunction<TestCodeOwnerConfigCreation, CodeOwnerConfig.Key> codeOwnerConfigCreator);

    /**
     * Builds the {@link TestCodeOwnerConfigCreation} instance.
     *
     * @return the {@link TestCodeOwnerConfigCreation} instance
     */
    abstract TestCodeOwnerConfigCreation autoBuild();

    /**
     * Executes the code owner config creation as specified.
     *
     * @return the key of the code owner config
     */
    public CodeOwnerConfig.Key create() {
      TestCodeOwnerConfigCreation creation = autoBuild();
      return creation.codeOwnerConfigCreator().applyAndThrowSilently(creation);
    }
  }
}
