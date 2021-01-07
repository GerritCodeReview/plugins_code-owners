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

package com.google.gerrit.plugins.codeowners.restapi;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.common.ChangeType;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerStatusInfo;
import com.google.gerrit.plugins.codeowners.api.FileCodeOwnerStatusInfo;
import com.google.gerrit.plugins.codeowners.api.PathCodeOwnerStatusInfo;
import com.google.gerrit.plugins.codeowners.backend.FileCodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.backend.PathCodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import java.util.Comparator;
import org.eclipse.jgit.diff.DiffEntry;

/** Collection of routines to populate {@link CodeOwnerStatusInfo}. */
public class CodeOwnerStatusInfoJson {
  /** Comparator that sorts {@link FileCodeOwnerStatus} by new path and then old path. */
  private static final Comparator<FileCodeOwnerStatus> FILE_CODE_OWNER_STATUS_COMPARATOR =
      comparing(
          (FileCodeOwnerStatus fileStatus) -> {
            if (fileStatus.newPathStatus().isPresent()) {
              return fileStatus.newPathStatus().get().path().toString();
            }
            if (fileStatus.oldPathStatus().isPresent()) {
              return fileStatus.oldPathStatus().get().path().toString();
            }
            throw new IllegalStateException(
                String.format(
                    "file code owner status %s has neither oldPath nor newPath", fileStatus));
          });

  private static final ImmutableMap<DiffEntry.ChangeType, ChangeType> CHANGE_TYPE =
      Maps.immutableEnumMap(
          new ImmutableMap.Builder<DiffEntry.ChangeType, ChangeType>()
              .put(DiffEntry.ChangeType.ADD, ChangeType.ADDED)
              .put(DiffEntry.ChangeType.MODIFY, ChangeType.MODIFIED)
              .put(DiffEntry.ChangeType.DELETE, ChangeType.DELETED)
              .put(DiffEntry.ChangeType.RENAME, ChangeType.RENAMED)
              .put(DiffEntry.ChangeType.COPY, ChangeType.COPIED)
              .build());

  /**
   * Formats a {@link CodeOwnerStatusInfo} from the provided file code owner statuses.
   *
   * @param patchSetId the ID of the patch set for which the file code owner statuses were computed
   * @param fileCodeOwnerStatuses the {@link FileCodeOwnerStatus}es that should be set in the {@link
   *     CodeOwnerStatusInfo}
   * @return the created {@link CodeOwnerStatusInfo}
   */
  public static CodeOwnerStatusInfo format(
      PatchSet.Id patchSetId, ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses) {
    requireNonNull(patchSetId, "patchSetId");
    requireNonNull(fileCodeOwnerStatuses, "fileCodeOwnerStatuses");
    CodeOwnerStatusInfo info = new CodeOwnerStatusInfo();
    info.patchSetNumber = patchSetId.get();
    info.fileCodeOwnerStatuses =
        fileCodeOwnerStatuses.stream()
            .sorted(FILE_CODE_OWNER_STATUS_COMPARATOR)
            .map(CodeOwnerStatusInfoJson::format)
            .collect(toImmutableList());
    return info;
  }

  /**
   * Formats the provided {@link FileCodeOwnerStatus} as {@link FileCodeOwnerStatusInfo}.
   *
   * @param fileCodeOwnerStatus the {@link FileCodeOwnerStatus} that should be formatted as {@link
   *     FileCodeOwnerStatusInfo}
   * @return the provided {@link FileCodeOwnerStatus} as {@link FileCodeOwnerStatusInfo}
   */
  @VisibleForTesting
  static FileCodeOwnerStatusInfo format(FileCodeOwnerStatus fileCodeOwnerStatus) {
    requireNonNull(fileCodeOwnerStatus, "fileCodeOwnerStatus");
    FileCodeOwnerStatusInfo info = new FileCodeOwnerStatusInfo();
    info.changeType =
        fileCodeOwnerStatus.changedFile().changeType() != DiffEntry.ChangeType.MODIFY
            ? CHANGE_TYPE.get(fileCodeOwnerStatus.changedFile().changeType())
            : null;
    fileCodeOwnerStatus
        .oldPathStatus()
        .ifPresent(oldPathStatus -> info.oldPathStatus = format(oldPathStatus));
    fileCodeOwnerStatus
        .newPathStatus()
        .ifPresent(newPathStatus -> info.newPathStatus = format(newPathStatus));
    return info;
  }

  /**
   * Formats the provided {@link PathCodeOwnerStatus} as {@link PathCodeOwnerStatusInfo}.
   *
   * @param pathCodeOwnerStatus the {@link PathCodeOwnerStatus} that should be formatted as {@link
   *     PathCodeOwnerStatusInfo}
   * @return the provided {@link PathCodeOwnerStatus} as {@link PathCodeOwnerStatusInfo}
   */
  @VisibleForTesting
  static PathCodeOwnerStatusInfo format(PathCodeOwnerStatus pathCodeOwnerStatus) {
    requireNonNull(pathCodeOwnerStatus, "pathCodeOwnerStatus");
    PathCodeOwnerStatusInfo info = new PathCodeOwnerStatusInfo();
    info.path = JgitPath.of(pathCodeOwnerStatus.path()).get();
    info.status = pathCodeOwnerStatus.status();
    return info;
  }

  /**
   * Private constructor to prevent instantiation of this class.
   *
   * <p>The class only contains static methods, hence the class never needs to be instantiated.
   */
  private CodeOwnerStatusInfoJson() {}
}
