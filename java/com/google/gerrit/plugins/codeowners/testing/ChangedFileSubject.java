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
import static com.google.gerrit.truth.OptionalSubject.optionals;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.backend.ChangedFile;
import com.google.gerrit.truth.OptionalSubject;

/** {@link Subject} for doing assertions on {@link ChangedFile}s. */
public class ChangedFileSubject extends Subject {
  /**
   * Starts fluent chain to do assertions on a {@link ChangedFile}.
   *
   * @param changedFile the changed file on which assertions should be done
   * @return the created {@link ChangedFileSubject}
   */
  public static ChangedFileSubject assertThat(ChangedFile changedFile) {
    return assertAbout(ChangedFileSubject::new).that(changedFile);
  }

  private final ChangedFile changedFile;

  private ChangedFileSubject(FailureMetadata metadata, ChangedFile changedFile) {
    super(metadata, changedFile);
    this.changedFile = changedFile;
  }

  /** Returns an {@link OptionalSubject} for the new path. */
  public OptionalSubject<Subject, ?> hasNewPath() {
    return check("newPath()").about(optionals()).that(changedFile().newPath());
  }

  /** Returns an {@link OptionalSubject} for the old path. */
  public OptionalSubject<Subject, ?> hasOldPath() {
    return check("oldPath()").about(optionals()).that(changedFile().oldPath());
  }

  /** Checks whether it's a rename. */
  public void isRename() {
    check("isRename()").that(changedFile().isRename()).isTrue();
  }

  /** Checks whether it's not a rename. */
  public void isNoRename() {
    check("isNoRename()").that(changedFile().isRename()).isFalse();
  }

  /** Checks whether it's a rename. */
  public void isDeletion() {
    check("isDeletion()").that(changedFile().isDeletion()).isTrue();
  }

  /** Checks whether it's not a rename. */
  public void isNoDeletion() {
    check("isNoDeletion()").that(changedFile().isDeletion()).isFalse();
  }

  private ChangedFile changedFile() {
    isNotNull();
    return changedFile;
  }
}
