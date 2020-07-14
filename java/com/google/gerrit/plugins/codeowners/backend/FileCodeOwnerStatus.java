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

import com.google.auto.value.AutoValue;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerStatus;
import java.nio.file.Path;
import java.util.Optional;

/** Code owner status for a particular file that was changed in a change. */
@AutoValue
public abstract class FileCodeOwnerStatus {
  /** The changed file to which the code owner statuses belong. */
  public abstract ChangedFile changedFile();

  /**
   * The code owner status for the new path.
   *
   * <p>Not set if the file was deleted.
   */
  public abstract Optional<PathCodeOwnerStatus> newPathCodeOwnerStatus();

  /**
   * The code owner status for the old path.
   *
   * <p>Only set if the file was deleted or renamed.
   *
   * <p>{@link #changedFile()} also has an old path if the file was copied, but in case of copy the
   * old path didn't change and hence we do not need any code owner approval for it.
   */
  public abstract Optional<PathCodeOwnerStatus> oldPathCodeOwnerStatus();

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

  /** Code owner status for a particular path that has been modified in a change. */
  @AutoValue
  public abstract static class PathCodeOwnerStatus {
    /**
     * Path to which the {@link #codeOwnerStatus()} belongs.
     *
     * <p>Always an absolute path.
     */
    public abstract Path path();

    /** The code owner status of the {@link #path()}. */
    public abstract CodeOwnerStatus codeOwnerStatus();

    /**
     * Creates a {@link PathCodeOwnerStatus} instance.
     *
     * @param path the path to which the code owner status belongs
     * @param codeOwnerStatus the code owner status
     * @return the created {@link PathCodeOwnerStatus} instance
     */
    public static PathCodeOwnerStatus create(Path path, CodeOwnerStatus codeOwnerStatus) {
      return new AutoValue_FileCodeOwnerStatus_PathCodeOwnerStatus(path, codeOwnerStatus);
    }
  }
}
