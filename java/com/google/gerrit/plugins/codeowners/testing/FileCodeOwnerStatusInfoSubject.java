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

import com.google.common.truth.ComparableSubject;
import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.common.ChangeType;
import com.google.gerrit.plugins.codeowners.api.FileCodeOwnerStatusInfo;
import com.google.gerrit.plugins.codeowners.backend.FileCodeOwnerStatus;
import com.google.gerrit.truth.NullAwareCorrespondence;
import com.google.gerrit.truth.OptionalSubject;
import java.util.Optional;

/** {@link Subject} for doing assertions on {@link FileCodeOwnerStatusInfo}s. */
public class FileCodeOwnerStatusInfoSubject extends Subject {
  /**
   * {@link Correspondence} that maps {@link FileCodeOwnerStatusInfo}s to {@link
   * FileCodeOwnerStatus}s.
   */
  public static final Correspondence<FileCodeOwnerStatusInfo, FileCodeOwnerStatus>
      isFileCodeOwnerStatus() {
    return NullAwareCorrespondence.transforming(
        FileCodeOwnerStatus::from, "is file code owner status");
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
