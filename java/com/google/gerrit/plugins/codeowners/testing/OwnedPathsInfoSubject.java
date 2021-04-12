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

import com.google.common.truth.BooleanSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.api.OwnedPathsInfo;

/** {@link Subject} for doing assertions on {@link OwnedPathsInfo}s. */
public class OwnedPathsInfoSubject extends Subject {
  /**
   * Starts fluent chain to do assertions on a {@link OwnedPathsInfo}.
   *
   * @param ownedPathsInfo the owned paths info on which assertions should be done
   * @return the created {@link OwnedPathsInfoSubject}
   */
  public static OwnedPathsInfoSubject assertThat(OwnedPathsInfo ownedPathsInfo) {
    return assertAbout(ownedPathsInfos()).that(ownedPathsInfo);
  }

  private static Factory<OwnedPathsInfoSubject, OwnedPathsInfo> ownedPathsInfos() {
    return OwnedPathsInfoSubject::new;
  }

  private final OwnedPathsInfo ownedPathsInfo;

  private OwnedPathsInfoSubject(FailureMetadata metadata, OwnedPathsInfo ownedPathsInfo) {
    super(metadata, ownedPathsInfo);
    this.ownedPathsInfo = ownedPathsInfo;
  }

  public IterableSubject hasOwnedPathsThat() {
    return check("ownedPaths()").that(ownedPathsInfo().ownedPaths);
  }

  public BooleanSubject hasMoreThat() {
    return check("more()").that(ownedPathsInfo().more);
  }

  private OwnedPathsInfo ownedPathsInfo() {
    isNotNull();
    return ownedPathsInfo;
  }
}
