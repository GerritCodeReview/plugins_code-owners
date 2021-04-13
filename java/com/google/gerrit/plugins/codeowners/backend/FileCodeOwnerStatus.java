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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.common.ChangeType;
import com.google.gerrit.plugins.codeowners.api.FileCodeOwnerStatusInfo;
import com.google.gerrit.plugins.codeowners.common.ChangedFile;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.diff.DiffEntry;

/** Code owner status for a particular file that was changed in a change. */
@AutoValue
public abstract class FileCodeOwnerStatus {
  private static final ImmutableMap<ChangeType, DiffEntry.ChangeType> CHANGE_TYPE =
      Maps.immutableEnumMap(
          new ImmutableMap.Builder<ChangeType, DiffEntry.ChangeType>()
              .put(ChangeType.ADDED, DiffEntry.ChangeType.ADD)
              .put(ChangeType.MODIFIED, DiffEntry.ChangeType.MODIFY)
              .put(ChangeType.DELETED, DiffEntry.ChangeType.DELETE)
              .put(ChangeType.RENAMED, DiffEntry.ChangeType.RENAME)
              .put(ChangeType.COPIED, DiffEntry.ChangeType.COPY)
              .build());

  /** The changed file to which the code owner statuses belong. */
  public abstract ChangedFile changedFile();

  /**
   * The code owner status for the new path.
   *
   * <p>Not set if the file was deleted.
   */
  public abstract Optional<PathCodeOwnerStatus> newPathStatus();

  /**
   * The code owner status for the old path.
   *
   * <p>Only set if the file was deleted or renamed.
   *
   * <p>{@link #changedFile()} also has an old path if the file was copied, but in case of copy the
   * old path didn't change and hence we do not need any code owner approval for it.
   */
  public abstract Optional<PathCodeOwnerStatus> oldPathStatus();

  /**
   * Creates a {@link FileCodeOwnerStatus} instance.
   *
   * @param changedFile the changed file to which the code owner statuses belong
   * @param newPathCodeOwnerStatus the code owner status of the new path
   * @param oldPathCodeOwnerStatus the code owner status of the old path
   * @return the created {@link FileCodeOwnerStatus} instance
   */
  public static FileCodeOwnerStatus create(
      ChangedFile changedFile,
      Optional<PathCodeOwnerStatus> newPathCodeOwnerStatus,
      Optional<PathCodeOwnerStatus> oldPathCodeOwnerStatus) {
    return new AutoValue_FileCodeOwnerStatus(
        changedFile, newPathCodeOwnerStatus, oldPathCodeOwnerStatus);
  }

  public static FileCodeOwnerStatus addition(String path, CodeOwnerStatus codeOwnerStatus) {
    requireNonNull(path, "path");

    return addition(JgitPath.of(path).getAsAbsolutePath(), codeOwnerStatus);
  }

  public static FileCodeOwnerStatus addition(Path path, CodeOwnerStatus codeOwnerStatus) {
    requireNonNull(path, "path");
    requireNonNull(codeOwnerStatus, "codeOwnerStatus");

    return create(
        ChangedFile.addition(path),
        Optional.of(PathCodeOwnerStatus.create(path, codeOwnerStatus)),
        Optional.empty());
  }

  public static FileCodeOwnerStatus modification(Path path, CodeOwnerStatus codeOwnerStatus) {
    requireNonNull(path, "path");
    requireNonNull(codeOwnerStatus, "codeOwnerStatus");

    return create(
        ChangedFile.modification(path),
        Optional.of(PathCodeOwnerStatus.create(path, codeOwnerStatus)),
        Optional.empty());
  }

  public static FileCodeOwnerStatus deletion(Path path, CodeOwnerStatus codeOwnerStatus) {
    requireNonNull(path, "path");
    requireNonNull(codeOwnerStatus, "codeOwnerStatus");

    return create(
        ChangedFile.deletion(path),
        Optional.empty(),
        Optional.of(PathCodeOwnerStatus.create(path, codeOwnerStatus)));
  }

  public static FileCodeOwnerStatus rename(
      String oldPath,
      CodeOwnerStatus oldPathCodeOwnerStatus,
      String newPath,
      CodeOwnerStatus newPathCodeOwnerStatus) {
    requireNonNull(oldPath, "oldPath");
    requireNonNull(newPath, "newPath");

    return rename(
        JgitPath.of(oldPath).getAsAbsolutePath(),
        oldPathCodeOwnerStatus,
        JgitPath.of(newPath).getAsAbsolutePath(),
        newPathCodeOwnerStatus);
  }

  public static FileCodeOwnerStatus rename(
      Path oldPath,
      CodeOwnerStatus oldPathCodeOwnerStatus,
      Path newPath,
      CodeOwnerStatus newPathCodeOwnerStatus) {
    requireNonNull(oldPath, "oldPath");
    requireNonNull(oldPathCodeOwnerStatus, "oldPathCodeOwnerStatus");
    requireNonNull(newPath, "newPath");
    requireNonNull(newPathCodeOwnerStatus, "newPathCodeOwnerStatus");

    return create(
        ChangedFile.rename(newPath, oldPath),
        Optional.of(PathCodeOwnerStatus.create(newPath, newPathCodeOwnerStatus)),
        Optional.of(PathCodeOwnerStatus.create(oldPath, oldPathCodeOwnerStatus)));
  }

  public static FileCodeOwnerStatus from(FileCodeOwnerStatusInfo fileCodeOwnerStatusInfo) {
    requireNonNull(fileCodeOwnerStatusInfo, "fileCodeOwnerStatusInfo");

    ChangedFile changedFile =
        ChangedFile.create(
            Optional.ofNullable(fileCodeOwnerStatusInfo.newPathStatus)
                .map(pathCodeOwnerStatusInfo -> pathCodeOwnerStatusInfo.path),
            Optional.ofNullable(fileCodeOwnerStatusInfo.oldPathStatus)
                .map(pathCodeOwnerStatusInfo -> pathCodeOwnerStatusInfo.path),
            CHANGE_TYPE.get(fileCodeOwnerStatusInfo.changeType));
    return create(
        changedFile,
        Optional.ofNullable(fileCodeOwnerStatusInfo.newPathStatus).map(PathCodeOwnerStatus::from),
        Optional.ofNullable(fileCodeOwnerStatusInfo.oldPathStatus).map(PathCodeOwnerStatus::from));
  }
}
