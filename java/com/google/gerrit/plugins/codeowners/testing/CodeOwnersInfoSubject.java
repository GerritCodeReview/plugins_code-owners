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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoSubject.codeOwnerInfos;
import static com.google.gerrit.truth.ListSubject.elements;

import com.google.common.truth.BooleanSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnersInfo;
import com.google.gerrit.truth.ListSubject;

/** {@link Subject} for doing assertions on {@link CodeOwnersInfo}s. */
public class CodeOwnersInfoSubject extends Subject {
  /**
   * Starts fluent chain to do assertions on a {@link CodeOwnersInfo}.
   *
   * @param codeOwnersInfo the code owners info on which assertions should be done
   * @return the created {@link CodeOwnersInfoSubject}
   */
  public static CodeOwnersInfoSubject assertThat(CodeOwnersInfo codeOwnersInfo) {
    return assertAbout(codeOwnersInfos()).that(codeOwnersInfo);
  }

  private static Factory<CodeOwnersInfoSubject, CodeOwnersInfo> codeOwnersInfos() {
    return CodeOwnersInfoSubject::new;
  }

  private final CodeOwnersInfo codeOwnersInfo;

  private CodeOwnersInfoSubject(FailureMetadata metadata, CodeOwnersInfo codeOwnersInfo) {
    super(metadata, codeOwnersInfo);
    this.codeOwnersInfo = codeOwnersInfo;
  }

  public ListSubject<CodeOwnerInfoSubject, CodeOwnerInfo> hasCodeOwnersThat() {
    return check("codeOwners()")
        .about(elements())
        .thatCustom(codeOwnersInfo().codeOwners, codeOwnerInfos());
  }

  public BooleanSubject hasOwnedByAllUsersThat() {
    return check("ownedByAllUsers").that(codeOwnersInfo().ownedByAllUsers);
  }

  public void hasDebugLogsThatContainAllOf(String... expectedMessages) {
    for (String expectedMessage : expectedMessages) {
      check("debugLogs").that(codeOwnersInfo().debugLogs).contains(expectedMessage);
    }
  }

  public IterableSubject hasDebugLogsThat() {
    return check("debugLogs").that(codeOwnersInfo().debugLogs);
  }

  private CodeOwnersInfo codeOwnersInfo() {
    isNotNull();
    return codeOwnersInfo;
  }
}
