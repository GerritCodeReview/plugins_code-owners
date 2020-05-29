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
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.testsuite.ThrowingFunction;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import java.nio.file.Path;
import java.nio.file.Paths;
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
   * @return the project in which the code owner config should be created
   */
  public abstract Optional<Project.NameKey> project();

  /**
   * Gets the branch in which the code owner config should be created.
   *
   * @return the branch in which the code owner config should be created
   */
  public abstract Optional<String> branch();

  /**
   * Gets the folder path in which the code owner config should be created.
   *
   * @return the folder path in which the code owner config should be created
   */
  public abstract Optional<Path> folderPath();

  /**
   * Gets the code owners that should be set in the newly created code owner config.
   *
   * @return the code owners that should be set in the newly created code owner config
   */
  public abstract ImmutableSet<CodeOwnerReference> codeOwners();

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
    Path folderPath = folderPath().orElse(Paths.get("/"));
    return CodeOwnerConfig.Key.create(BranchNameKey.create(projectName, branchName), folderPath);
  }

  /**
   * Whether the code owner config would empty.
   *
   * @return whether the code owner config would empty
   */
  public boolean isEmpty() {
    return codeOwners().isEmpty();
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
        .codeOwnerConfigCreator(codeOwnerConfigCreator);
  }

  /** Builder for a {@link TestCodeOwnerConfigCreation}. */
  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Sets the project in which the code owner config should be created.
     *
     * @param project the project in which the code owner config should be created
     * @return the Builder instance for chaining calls
     */
    public abstract Builder project(Project.NameKey project);

    /**
     * Sets the branch in which the code owner config should be created.
     *
     * @param branch the branch in which the code owner config should be created
     * @return the Builder instance for chaining calls
     */
    public abstract Builder branch(String branch);

    /**
     * Sets the folder path in which the code owner config should be created.
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
      return folderPath(Paths.get(folderPath));
    }

    /**
     * Gets a builder to add code owner references.
     *
     * @return builder to add code owner references
     */
    abstract ImmutableSet.Builder<CodeOwnerReference> codeOwnersBuilder();

    /**
     * Adds a code owner for the given email.
     *
     * @param codeOwnerEmail email of the code owner
     * @return the Builder instance for chaining calls
     */
    public Builder addCodeOwnerEmail(String codeOwnerEmail) {
      return addCodeOwner(
          CodeOwnerReference.create(requireNonNull(codeOwnerEmail, "codeOwnerEmail")));
    }

    /**
     * Adds a code owner.
     *
     * @param codeOwnerReference reference to the code owner
     * @return the Builder instance for chaining calls
     */
    Builder addCodeOwner(CodeOwnerReference codeOwnerReference) {
      codeOwnersBuilder().add(requireNonNull(codeOwnerReference, "codeOwnerReference"));
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
