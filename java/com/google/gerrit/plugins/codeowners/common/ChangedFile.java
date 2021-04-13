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

package com.google.gerrit.plugins.codeowners.common;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
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
  private static final ImmutableMap<Patch.ChangeType, ChangeType> CHANGE_TYPE =
      Maps.immutableEnumMap(
          new ImmutableMap.Builder<Patch.ChangeType, ChangeType>()
              .put(Patch.ChangeType.ADDED, ChangeType.ADD)
              .put(Patch.ChangeType.MODIFIED, ChangeType.MODIFY)
              .put(Patch.ChangeType.DELETED, ChangeType.DELETE)
              .put(Patch.ChangeType.RENAMED, ChangeType.RENAME)
              .put(Patch.ChangeType.COPIED, ChangeType.COPY)
              .put(Patch.ChangeType.REWRITE, ChangeType.MODIFY)
              .build());

  /**
   * The new path of the file.
   *
   * <p>Not set if the file was deleted.
   *
   * <p>If set, the new path is returned as absolute path.
   */
  public abstract Optional<Path> newPath();

  /**
   * The old path of the file.
   *
   * <p>Not set if the file was newly added
   *
   * <p>If set, the old path is returned as absolute path.
   */
  public abstract Optional<Path> oldPath();

  /** Gets the change type. */
  public abstract ChangeType changeType();

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
    return changeType() == ChangeType.RENAME;
  }

  /** Whether the file was deleted. */
  public boolean isDeletion() {
    return changeType() == ChangeType.DELETE;
  }

  /**
   * Creates a {@link ChangedFile} instance from a {@link DiffEntry}.
   *
   * @param diffEntry the diff entry
   */
  public static ChangedFile create(DiffEntry diffEntry) {
    requireNonNull(diffEntry, "diffEntry");
    return new AutoValue_ChangedFile(
        convertPathFromDiffEntryPath(diffEntry.getNewPath()),
        convertPathFromDiffEntryPath(diffEntry.getOldPath()),
        diffEntry.getChangeType());
  }

  /**
   * Converts the given string path to an absolute path.
   *
   * <p>{@link DiffEntry} is using {@code /dev/null} if a path doesn't exist. If the given path is
   * {@code /dev/null} {@link Optional#empty} is returned.
   */
  private static Optional<Path> convertPathFromDiffEntryPath(String path) {
    if (DiffEntry.DEV_NULL.equals(path)) {
      return Optional.empty();
    }
    return Optional.of(JgitPath.of(path).getAsAbsolutePath());
  }

  /**
   * Creates a {@link ChangedFile} instance from a {@link PatchListEntry}.
   *
   * @param patchListEntry the patch list entry
   */
  public static ChangedFile create(PatchListEntry patchListEntry) {
    requireNonNull(patchListEntry, "patchListEntry");

    if (patchListEntry.getChangeType() == Patch.ChangeType.DELETED) {
      // For deletions PatchListEntry sets the old path as new name and the old name is unset (see
      // PatchListEntry constructor). This means to get the old path we need to read the new name.
      return new AutoValue_ChangedFile(
          Optional.empty(),
          convertPathFromPatchListEntry(patchListEntry.getNewName()),
          CHANGE_TYPE.get(patchListEntry.getChangeType()));
    }

    return new AutoValue_ChangedFile(
        convertPathFromPatchListEntry(patchListEntry.getNewName()),
        convertPathFromPatchListEntry(patchListEntry.getOldName()),
        CHANGE_TYPE.get(patchListEntry.getChangeType()));
  }

  /**
   * Converts the given string path to an absolute path.
   *
   * <p>{@link PatchListEntry} is using {@code null} if a path doesn't exist. If the given path is
   * {@code null} {@link Optional#empty} is returned.
   */
  private static Optional<Path> convertPathFromPatchListEntry(@Nullable String path) {
    return Optional.ofNullable(path).map(newName -> JgitPath.of(newName).getAsAbsolutePath());
  }

  /**
   * Creates a {@link ChangedFile} instance from a {@link FileDiffOutput}.
   *
   * @param fileDiffOutput the file diff output
   */
  public static ChangedFile create(FileDiffOutput fileDiffOutput) {
    requireNonNull(fileDiffOutput, "fileDiffOutput");

    return new AutoValue_ChangedFile(
        convertPathFromFileDiffOutput(fileDiffOutput.newPath()),
        convertPathFromFileDiffOutput(fileDiffOutput.oldPath()),
        CHANGE_TYPE.get(fileDiffOutput.changeType()));
  }

  /** Converts the given string path to an absolute path. */
  private static Optional<Path> convertPathFromFileDiffOutput(Optional<String> path) {
    requireNonNull(path, "path");
    return path.map(p -> JgitPath.of(p).getAsAbsolutePath());
  }

  public static ChangedFile create(
      Optional<String> newPath, Optional<String> oldPath, ChangeType changeType) {
    requireNonNull(changeType, "changeType");

    return new AutoValue_ChangedFile(
        newPath.map(JgitPath::of).map(JgitPath::getAsAbsolutePath),
        oldPath.map(JgitPath::of).map(JgitPath::getAsAbsolutePath),
        changeType);
  }

  public static ChangedFile addition(Path newPath) {
    requireNonNull(newPath, "newPath");

    return new AutoValue_ChangedFile(Optional.of(newPath), Optional.empty(), ChangeType.ADD);
  }

  public static ChangedFile modification(Path path) {
    requireNonNull(path, "path");

    return new AutoValue_ChangedFile(Optional.of(path), Optional.of(path), ChangeType.MODIFY);
  }

  public static ChangedFile deletion(Path path) {
    requireNonNull(path, "path");

    return new AutoValue_ChangedFile(Optional.empty(), Optional.of(path), ChangeType.DELETE);
  }

  public static ChangedFile rename(Path newPath, Path oldPath) {
    requireNonNull(newPath, "newPath");

    return new AutoValue_ChangedFile(Optional.of(newPath), Optional.of(oldPath), ChangeType.RENAME);
  }
}
