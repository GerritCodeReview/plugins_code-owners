// Copyright (C) 2021 The Android Open Source Project
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
import static com.google.gerrit.plugins.codeowners.testing.OwnedPathInfoSubject.ownedPathInfos;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.api.OwnedChangedFileInfo;

/** {@link Subject} for doing assertions on {@link OwnedChangedFileInfo}s. */
public class OwnedChangedFileInfoSubject extends Subject {
  /**
   * Starts fluent chain to do assertions on a {@link OwnedChangedFileInfo}.
   *
   * @param ownedChangedFileInfo the owned changed file info on which assertions should be done
   * @return the created {@link OwnedChangedFileInfoSubject}
   */
  public static OwnedChangedFileInfoSubject assertThat(OwnedChangedFileInfo ownedChangedFileInfo) {
    return assertAbout(ownedChangedFileInfos()).that(ownedChangedFileInfo);
  }

  private static Factory<OwnedChangedFileInfoSubject, OwnedChangedFileInfo>
      ownedChangedFileInfos() {
    return OwnedChangedFileInfoSubject::new;
  }

  private final OwnedChangedFileInfo ownedChangedFileInfo;

  private OwnedChangedFileInfoSubject(
      FailureMetadata metadata, OwnedChangedFileInfo ownedChangedFileInfo) {
    super(metadata, ownedChangedFileInfo);
    this.ownedChangedFileInfo = ownedChangedFileInfo;
  }


  public OwnedPathInfoSubject hasNewPathThat(String expectedNewPath) {
    check("ownedNewPath").that(ownedChangedFileInfo().newPath).isNotNull();
    check("ownedNewPath").that(ownedChangedFileInfo().newPath.path).isEqualTo(expectedNewPath);
    return check("ownedNewPath").about(ownedPathInfos()).that(ownedChangedFileInfo().newPath);
  }

  public void hasEmptyNewPath() {
    check("ownedNewPath").that(ownedChangedFileInfo().newPath).isNull();
  }

  public OwnedPathInfoSubject hasOldPathThat(String expectedOldPath) {
    check("ownedOldPath").that(ownedChangedFileInfo().oldPath).isNotNull();
    check("ownedOldPath").that(ownedChangedFileInfo().oldPath.path).isEqualTo(expectedOldPath);
    return check("ownedNewPath").about(ownedPathInfos()).that(ownedChangedFileInfo().newPath);
  }

  public void hasOwnedNewPath(String expectedOwnedNewPath) {
    hasNewPathThat(expectedOwnedNewPath);
    check("ownedNewPath").that(ownedChangedFileInfo().newPath.owned).isTrue();
  }

  public void hasNonOwnedNewPath(String expectedNonOwnedNewPath) {
    hasNewPathThat(expectedNonOwnedNewPath);
    check("ownedNewPath").that(ownedChangedFileInfo().newPath.owned).isNull();
  }

  public void hasEmptyOldPath() {
    check("ownedOldPath").that(ownedChangedFileInfo().oldPath).isNull();
  }

  public void hasOwnedOldPath(String expectedOwnedOldPath) {
    hasOldPathThat(expectedOwnedOldPath);
    check("ownedOldPath").that(ownedChangedFileInfo().oldPath.owned).isTrue();
  }

  public void hasNonOwnedOldPath(String expectedNonOwnedOldPath) {
    hasOldPathThat(expectedNonOwnedOldPath);
    check("ownedOldPath").that(ownedChangedFileInfo().oldPath.owned).isNull();
  }

  private OwnedChangedFileInfo ownedChangedFileInfo() {
    isNotNull();
    return ownedChangedFileInfo;
  }
}
