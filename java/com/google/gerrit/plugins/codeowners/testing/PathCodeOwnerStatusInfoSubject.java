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

import com.google.common.truth.ComparableSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.api.PathCodeOwnerStatusInfo;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;

/** {@link Subject} for doing assertions on {@link PathCodeOwnerStatusInfo}s. */
public class PathCodeOwnerStatusInfoSubject extends Subject {
  /**
   * Starts fluent chain to do assertions on a {@link PathCodeOwnerStatusInfo}.
   *
   * @param pathCodeOwnerStatusInfo the {@link PathCodeOwnerStatusInfo} on which assertions should
   *     be done
   * @return the created {@link PathCodeOwnerStatusInfoSubject}
   */
  public static PathCodeOwnerStatusInfoSubject assertThat(
      PathCodeOwnerStatusInfo pathCodeOwnerStatusInfo) {
    return assertAbout(pathCodeOwnerStatusInfos()).that(pathCodeOwnerStatusInfo);
  }

  /**
   * Creates subject factory for mapping {@link PathCodeOwnerStatusInfo}s to {@link
   * PathCodeOwnerStatusInfoSubject}s.
   */
  public static Subject.Factory<PathCodeOwnerStatusInfoSubject, PathCodeOwnerStatusInfo>
      pathCodeOwnerStatusInfos() {
    return PathCodeOwnerStatusInfoSubject::new;
  }

  private final PathCodeOwnerStatusInfo pathCodeOwnerStatusInfo;

  private PathCodeOwnerStatusInfoSubject(
      FailureMetadata metadata, PathCodeOwnerStatusInfo pathCodeOwnerStatusInfo) {
    super(metadata, pathCodeOwnerStatusInfo);
    this.pathCodeOwnerStatusInfo = pathCodeOwnerStatusInfo;
  }

  /** Returns a {@link ComparableSubject} for the path. */
  public ComparableSubject<String> hasPathThat() {
    return check("path()").that(pathCodeOwnerStatusInfo().path);
  }

  /** Returns a {@link ComparableSubject} for the code owner status. */
  public ComparableSubject<CodeOwnerStatus> hasStatusThat() {
    return check("status()").that(pathCodeOwnerStatusInfo().status);
  }

  /** Returns an {@link IterableSubject} for the reasons. */
  public IterableSubject hasReasonsThat() {
    return check("reasons()").that(pathCodeOwnerStatusInfo().reasons);
  }

  private PathCodeOwnerStatusInfo pathCodeOwnerStatusInfo() {
    isNotNull();
    return pathCodeOwnerStatusInfo;
  }
}
