// Copyright (C) 2022 The Android Open Source Project
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

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.api.OwnedPathInfo;


/** {@link Subject} for doing assertions on {@link OwnedPathInfo}s. */
public class OwnedPathInfoSubject extends Subject {
  /**
   * Starts fluent chain to do assertions on a {@link OwnedPathInfo}.
   *
   * @param ownedPathInfo the owned path info on which assertions should be done
   * @return the created {@link OwnedPathInfoSubject}
   */
  public static OwnedPathInfoSubject assertThat(OwnedPathInfo ownedPathInfo) {
    return assertAbout(ownedPathInfos()).that(ownedPathInfo);
  }

  public static Factory<OwnedPathInfoSubject, OwnedPathInfo> ownedPathInfos() {
    return OwnedPathInfoSubject::new;
  }

  private final OwnedPathInfo ownedPathInfo;

  private OwnedPathInfoSubject(
      FailureMetadata metadata, OwnedPathInfo ownedPathInfo) {
    super(metadata, ownedPathInfo);
    this.ownedPathInfo = ownedPathInfo;
  }

  public IterableSubject hasOwnersThat() {
    return check("ownedPathInfo()").that(ownedPathInfo().owners);
  }

  private OwnedPathInfo ownedPathInfo() {
    isNotNull();
    return ownedPathInfo;
  }
}

