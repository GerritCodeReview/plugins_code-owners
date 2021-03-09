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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.gerrit.plugins.codeowners.testing.ChangedFileSubject.changedFiles;
import static com.google.gerrit.plugins.codeowners.testing.PathCodeOwnerStatusSubject.pathCodeOwnerStatuses;
import static com.google.gerrit.truth.OptionalSubject.optionals;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.backend.FileCodeOwnerStatus;
import com.google.gerrit.truth.ListSubject;
import com.google.gerrit.truth.OptionalSubject;
import java.util.Collection;
import java.util.stream.Stream;

/** {@link Subject} for doing assertions on {@link FileCodeOwnerStatus}es. */
public class FileCodeOwnerStatusSubject extends Subject {
  public static FileCodeOwnerStatusSubject assertThat(FileCodeOwnerStatus fileCodeOwnerStatus) {
    return assertAbout(fileCodeOwnerStatuses()).that(fileCodeOwnerStatus);
  }

  public static ListSubject<FileCodeOwnerStatusSubject, FileCodeOwnerStatus> assertThatStream(
      Stream<FileCodeOwnerStatus> fileCodeOwnerStatuses) {
    return ListSubject.assertThat(
        fileCodeOwnerStatuses.collect(toImmutableList()), fileCodeOwnerStatuses());
  }

  /** Starts fluent chain to do assertions on a collection of {@link FileCodeOwnerStatus}es. */
  public static ListSubject<FileCodeOwnerStatusSubject, FileCodeOwnerStatus> assertThatCollection(
      Collection<FileCodeOwnerStatus> fileCodeOwnerStatuses) {
    return ListSubject.assertThat(
        ImmutableList.copyOf(fileCodeOwnerStatuses), fileCodeOwnerStatuses());
  }

  private static Factory<FileCodeOwnerStatusSubject, FileCodeOwnerStatus> fileCodeOwnerStatuses() {
    return FileCodeOwnerStatusSubject::new;
  }

  private final FileCodeOwnerStatus fileCodeOwnerStatus;

  private FileCodeOwnerStatusSubject(
      FailureMetadata metadata, FileCodeOwnerStatus fileCodeOwnerStatus) {
    super(metadata, fileCodeOwnerStatus);
    this.fileCodeOwnerStatus = fileCodeOwnerStatus;
  }

  /** Returns a subject for the changed file. */
  public ChangedFileSubject hasChangedFile() {
    return check("changedFile()").about(changedFiles()).that(fileCodeOwnerStatus().changedFile());
  }

  /** Returns an {@link OptionalSubject} for the code owners status of the new path. */
  public OptionalSubject<PathCodeOwnerStatusSubject, ?> hasNewPathStatus() {
    return check("newPathStatus()")
        .about(optionals())
        .thatCustom(fileCodeOwnerStatus().newPathStatus(), pathCodeOwnerStatuses());
  }

  /** Returns an {@link OptionalSubject} for the code owners status of the old path. */
  public OptionalSubject<PathCodeOwnerStatusSubject, ?> hasOldPathStatus() {
    return check("oldPathStatus()")
        .about(optionals())
        .thatCustom(fileCodeOwnerStatus().oldPathStatus(), pathCodeOwnerStatuses());
  }

  private FileCodeOwnerStatus fileCodeOwnerStatus() {
    isNotNull();
    return fileCodeOwnerStatus;
  }
}
