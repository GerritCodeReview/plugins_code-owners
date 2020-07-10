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
import com.google.gerrit.plugins.codeowners.JgitPath;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;

/**
 * A file that was touched in a change.
 *
 * <p>Wrapper around {@link DiffEntry} that provides convenience methods to access data from {@link
 * DiffEntry} and hides data that is not relevant for the code owners plugin.
 */
@AutoValue
public abstract class ChangedFile {
  /** The diff entry. */
  abstract DiffEntry diffEntry();

  /**
   * The new path of the file.
   *
   * <p>Not set if the file was deleted.
   *
   * <p>If set, the new path is returned as absolute path.
   */
  public Optional<Path> newPath() {
    return convertPath(diffEntry().getNewPath());
  }

  /**
   * Whether the file has the given path as new path.
   *
   * @param absolutePath an absolute path for which it should be checked if it matches the new path
   *     of this file
   */
  public boolean hasNewPath(Path absolutePath) {
    requireNonNull(absolutePath, "absolutePath");
    checkState(absolutePath.isAbsolute(), "path %s must be absolute", absolutePath);
    return newPath().isPresent() && newPath().get().equals(absolutePath);
  }

  /**
   * The old path of the file.
   *
   * <p>Not set if the file was newly added
   *
   * <p>If set, the old path is returned as absolute path.
   */
  public Optional<Path> oldPath() {
    return convertPath(diffEntry().getOldPath());
  }

  /**
   * Whether the file has the given path as old path.
   *
   * @param absolutePath an absolute path for which it should be checked if it matches the old path
   *     of this file
   */
  public boolean hasOldPath(Path absolutePath) {
    requireNonNull(absolutePath, "absolutePath");
    checkState(absolutePath.isAbsolute(), "path %s must be absolute", absolutePath);
    return oldPath().isPresent() && oldPath().get().equals(absolutePath);
  }

  /** Whether the file was renamed. */
  public boolean isRename() {
    return diffEntry().getChangeType() == ChangeType.RENAME;
  }

  /** Whether the file was deleted. */
  public boolean isDeletion() {
    return diffEntry().getChangeType() == ChangeType.DELETE;
  }

  /**
   * Converts the given string path to an absolute path.
   *
   * <p>{@link DiffEntry} is using {@code /dev/null} if a path doesn't exist. If the given path is
   * {@code /dev/null} {@link Optional#empty} is returned.
   */
  private static Optional<Path> convertPath(String path) {
    if (DiffEntry.DEV_NULL.equals(path)) {
      return Optional.empty();
    }
    return Optional.of(JgitPath.of(path).getAsAbsolutePath());
  }

  /**
   * Creates a {@link ChangedFile} instance.
   *
   * @param diffEntry the diff entry
   */
  public static ChangedFile create(DiffEntry diffEntry) {
    requireNonNull(diffEntry, "diffEntry");
    return new AutoValue_ChangedFile(diffEntry);
  }
}
