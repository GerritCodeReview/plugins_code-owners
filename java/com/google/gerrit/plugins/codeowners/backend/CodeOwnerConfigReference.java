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

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Project;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * A reference to a {@link CodeOwnerConfig}.
 *
 * <p>Allows a {@link CodeOwnerConfig} to include other {@link CodeOwnerConfig}s.
 */
@AutoValue
public abstract class CodeOwnerConfigReference {
  /**
   * Gets the import mode that controls which parts of the referenced code owner config should be
   * imported.
   */
  public abstract CodeOwnerConfigImportMode importMode();

  /**
   * The project in which the code owner config is stored.
   *
   * <p>If not set, the project is the same as the project of the code owner config that contains
   * this code owner config reference.
   */
  public abstract Optional<Project.NameKey> project();

  /**
   * The path of the code owner config file.
   *
   * <p>May be absolute or relative to the path of the importing code owner config.
   */
  public abstract Path filePath();

  /**
   * The path of the folder that contains the code owner config.
   *
   * <p>May be absolute or relative to the path of the importing code owner config.
   */
  public Path path() {
    return firstNonNull(filePath().getParent(), Paths.get(""));
  }

  /** The name of the code owner config file. */
  public String fileName() {
    return filePath().getFileName().toString();
  }

  /**
   * Creates a code owner config reference.
   *
   * @param importMode the import mode
   * @param filePath the path of the code owner config, may be absolute or relative to the path of
   *     the importing code owner config
   * @return the created code owner reference
   */
  public static CodeOwnerConfigReference create(
      CodeOwnerConfigImportMode importMode, String filePath) {
    return builder(importMode, filePath).build();
  }

  /**
   * Creates a builder for a code owner config reference.
   *
   * @param importMode the import mode
   * @param filePath the path of the code owner config, may be absolute or relative to the path of
   *     the importing code owner config
   * @return builder for a code owner config reference
   */
  public static Builder builder(CodeOwnerConfigImportMode importMode, String filePath) {
    requireNonNull(filePath, "filePath");
    return builder(importMode, Paths.get(filePath));
  }

  /**
   * Creates a builder for a code owner config reference.
   *
   * @param importMode the import mode
   * @param filePath the path of the code owner config, may be absolute or relative to the path of
   *     the importing code owner config
   * @return builder for a code owner config reference
   */
  public static Builder builder(CodeOwnerConfigImportMode importMode, Path filePath) {
    return new AutoValue_CodeOwnerConfigReference.Builder()
        .setImportMode(importMode)
        .setFilePath(filePath);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Sets the import mode that controls which parts of the referenced code owner config should be
     * imported.
     *
     * @param codeOwnerConfigImportMode the import mode that controls which parts of the referenced
     *     code owner config should be imported
     * @return the Builder instance for chaining calls
     */
    public abstract Builder setImportMode(CodeOwnerConfigImportMode codeOwnerConfigImportMode);

    /**
     * Sets the project in which the code owner config is stored.
     *
     * @param project the project in which the code owner config is stored
     * @return the Builder instance for chaining calls
     */
    public abstract Builder setProject(Project.NameKey project);

    /**
     * Sets the path of the code owner config file.
     *
     * @param filePath path of the code owner config file, may be absolute or relative to the path
     *     of the importing code owner config
     * @return the Builder instance for chaining calls
     */
    abstract Builder setFilePath(Path filePath);

    /**
     * Builds the {@link CodeOwnerConfigReference} instance.
     *
     * @return the {@link CodeOwnerConfigReference} instance
     */
    public abstract CodeOwnerConfigReference build();
  }
}
