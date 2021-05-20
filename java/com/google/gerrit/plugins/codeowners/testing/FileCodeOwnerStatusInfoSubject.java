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

package com.google.gerrit.plugins.codeowners.testing;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.gerrit.plugins.codeowners.testing.PathCodeOwnerStatusInfoSubject.pathCodeOwnerStatusInfos;
import static com.google.gerrit.truth.OptionalSubject.optionals;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.truth.ComparableSubject;
import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.common.ChangeType;
import com.google.gerrit.plugins.codeowners.api.FileCodeOwnerStatusInfo;
import com.google.gerrit.plugins.codeowners.api.PathCodeOwnerStatusInfo;
import com.google.gerrit.plugins.codeowners.backend.FileCodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.backend.PathCodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.common.ChangedFile;
import com.google.gerrit.truth.NullAwareCorrespondence;
import com.google.gerrit.truth.OptionalSubject;
import java.util.Optional;
import org.eclipse.jgit.diff.DiffEntry;

/** {@link Subject} for doing assertions on {@link FileCodeOwnerStatusInfo}s. */
public class FileCodeOwnerStatusInfoSubject extends Subject {
  private static final ImmutableMap<ChangeType, DiffEntry.ChangeType> CHANGE_TYPE =
      Maps.immutableEnumMap(
          new ImmutableMap.Builder<ChangeType, DiffEntry.ChangeType>()
              .put(ChangeType.ADDED, DiffEntry.ChangeType.ADD)
              .put(ChangeType.MODIFIED, DiffEntry.ChangeType.MODIFY)
              .put(ChangeType.DELETED, DiffEntry.ChangeType.DELETE)
              .put(ChangeType.RENAMED, DiffEntry.ChangeType.RENAME)
              .put(ChangeType.COPIED, DiffEntry.ChangeType.COPY)
              .build());

  /**
   * {@link Correspondence} that maps {@link FileCodeOwnerStatusInfo}s to {@link
   * FileCodeOwnerStatus}s.
   */
  public static final Correspondence<FileCodeOwnerStatusInfo, FileCodeOwnerStatus>
      isFileCodeOwnerStatus() {
    return NullAwareCorrespondence.transforming(
        FileCodeOwnerStatusInfoSubject::toFileCodeOwnerStatus, "is file code owner status");
  }

  private static FileCodeOwnerStatus toFileCodeOwnerStatus(
      FileCodeOwnerStatusInfo fileCodeOwnerStatusInfo) {
    requireNonNull(fileCodeOwnerStatusInfo, "fileCodeOwnerStatusInfo");

    ChangedFile changedFile =
        ChangedFile.create(
            Optional.ofNullable(fileCodeOwnerStatusInfo.newPathStatus)
                .map(pathCodeOwnerStatusInfo -> pathCodeOwnerStatusInfo.path),
            Optional.ofNullable(fileCodeOwnerStatusInfo.oldPathStatus)
                .map(pathCodeOwnerStatusInfo -> pathCodeOwnerStatusInfo.path),
            CHANGE_TYPE.get(fileCodeOwnerStatusInfo.changeType));
    return FileCodeOwnerStatus.create(
        changedFile,
        Optional.ofNullable(fileCodeOwnerStatusInfo.newPathStatus)
            .map(FileCodeOwnerStatusInfoSubject::toPathCodeOwnerStatus),
        Optional.ofNullable(fileCodeOwnerStatusInfo.oldPathStatus)
            .map(FileCodeOwnerStatusInfoSubject::toPathCodeOwnerStatus));
  }

  private static PathCodeOwnerStatus toPathCodeOwnerStatus(
      PathCodeOwnerStatusInfo pathCodeOwnerStatusInfo) {
    requireNonNull(pathCodeOwnerStatusInfo, "pathCodeOwnerStatusInfo");
    PathCodeOwnerStatus.Builder pathCodeOwnerStatus =
        PathCodeOwnerStatus.builder(pathCodeOwnerStatusInfo.path, pathCodeOwnerStatusInfo.status);
    if (pathCodeOwnerStatusInfo.reasons != null) {
      pathCodeOwnerStatusInfo.reasons.forEach(reason -> pathCodeOwnerStatus.addReason(reason));
    }
    return pathCodeOwnerStatus.build();
  }

  /**
   * Starts fluent chain to do assertions on a {@link FileCodeOwnerStatusInfo}.
   *
   * @param fileCodeOwnerStatusInfo the {@link FileCodeOwnerStatusInfo} on which assertions should
   *     be done
   * @return the created {@link FileCodeOwnerStatusInfoSubject}
   */
  public static FileCodeOwnerStatusInfoSubject assertThat(
      FileCodeOwnerStatusInfo fileCodeOwnerStatusInfo) {
    return assertAbout(fileCodeOwnerStatusInfos()).that(fileCodeOwnerStatusInfo);
  }

  public static Factory<FileCodeOwnerStatusInfoSubject, FileCodeOwnerStatusInfo>
      fileCodeOwnerStatusInfos() {
    return FileCodeOwnerStatusInfoSubject::new;
  }

  private final FileCodeOwnerStatusInfo fileCodeOwnerStatusInfo;

  private FileCodeOwnerStatusInfoSubject(
      FailureMetadata metadata, FileCodeOwnerStatusInfo fileCodeOwnerStatusInfo) {
    super(metadata, fileCodeOwnerStatusInfo);
    this.fileCodeOwnerStatusInfo = fileCodeOwnerStatusInfo;
  }

  /** Returns a subject for the change type. */
  public ComparableSubject<ChangeType> hasChangeTypeThat() {
    return check("changeType()").that(fileCodeOwnerStatusInfo().changeType);
  }

  /** Returns an {@link OptionalSubject} for the code owners status of the new path. */
  public OptionalSubject<PathCodeOwnerStatusInfoSubject, ?> hasNewPathStatusThat() {
    return check("newPathStatus()")
        .about(optionals())
        .thatCustom(
            Optional.ofNullable(fileCodeOwnerStatusInfo().newPathStatus),
            pathCodeOwnerStatusInfos());
  }

  /** Returns an {@link OptionalSubject} for the code owners status of the old path. */
  public OptionalSubject<PathCodeOwnerStatusInfoSubject, ?> hasOldPathStatusThat() {
    return check("oldPathStatus()")
        .about(optionals())
        .thatCustom(
            Optional.ofNullable(fileCodeOwnerStatusInfo().oldPathStatus),
            pathCodeOwnerStatusInfos());
  }

  private FileCodeOwnerStatusInfo fileCodeOwnerStatusInfo() {
    isNotNull();
    return fileCodeOwnerStatusInfo;
  }
}
