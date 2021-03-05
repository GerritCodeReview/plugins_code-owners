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

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.common.ChangedFile;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.truth.ListSubject;
import com.google.gerrit.truth.NullAwareCorrespondence;
import com.google.gerrit.truth.OptionalSubject;
import java.util.Collection;

/** {@link Subject} for doing assertions on {@link ChangedFile}s. */
public class ChangedFileSubject extends Subject {
  /**
   * Constructs a {@link Correspondence} that maps {@link ChangedFile}s to their paths (new path if
   * set, otherwise old path).
   */
  public static Correspondence<ChangedFile, String> hasPath() {
    return NullAwareCorrespondence.transforming(
        changedFile ->
            JgitPath.of(
                    changedFile.newPath().isPresent()
                        ? changedFile.newPath().get()
                        : changedFile.oldPath().get())
                .get(),
        "has path");
  }

  /**
   * Starts fluent chain to do assertions on a {@link ChangedFile}.
   *
   * @param changedFile the changed file on which assertions should be done
   * @return the created {@link ChangedFileSubject}
   */
  public static ChangedFileSubject assertThat(ChangedFile changedFile) {
    return assertAbout(changedFiles()).that(changedFile);
  }

  /** Starts fluent chain to do assertions on a collection of {@link ChangedFile}s. */
  public static ListSubject<ChangedFileSubject, ChangedFile> assertThatCollection(
      Collection<ChangedFile> changedFiles) {
    return ListSubject.assertThat(ImmutableList.copyOf(changedFiles), changedFiles());
  }

  /** Creates subject factory for mapping {@link ChangedFile}s to {@link ChangedFileSubject}s. */
  public static Subject.Factory<ChangedFileSubject, ChangedFile> changedFiles() {
    return ChangedFileSubject::new;
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

  /** Checks whether it's a deletion. */
  public void isDeletion() {
    check("isDeletion()").that(changedFile().isDeletion()).isTrue();
  }

  /** Checks whether it's not a deletion. */
  public void isNoDeletion() {
    check("isNoDeletion()").that(changedFile().isDeletion()).isFalse();
  }

  private ChangedFile changedFile() {
    isNotNull();
    return changedFile;
  }
}
