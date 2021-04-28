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

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerCheckInfo;

/** {@link Subject} for doing assertions on {@link CodeOwnerCheckInfo}s. */
public class CodeOwnerCheckInfoSubject extends Subject {
  /**
   * Starts fluent chain to do assertions on a {@link CodeOwnerCheckInfo}.
   *
   * @param codeOwnerCheckInfo the code owner check info on which assertions should be done
   * @return the created {@link CodeOwnerCheckInfoSubject}
   */
  public static CodeOwnerCheckInfoSubject assertThat(CodeOwnerCheckInfo codeOwnerCheckInfo) {
    return assertAbout(codeOwnerCheckInfos()).that(codeOwnerCheckInfo);
  }

  private static Factory<CodeOwnerCheckInfoSubject, CodeOwnerCheckInfo> codeOwnerCheckInfos() {
    return CodeOwnerCheckInfoSubject::new;
  }

  private final CodeOwnerCheckInfo codeOwnerCheckInfo;

  private CodeOwnerCheckInfoSubject(
      FailureMetadata metadata, CodeOwnerCheckInfo codeOwnerCheckInfo) {
    super(metadata, codeOwnerCheckInfo);
    this.codeOwnerCheckInfo = codeOwnerCheckInfo;
  }

  public void isCodeOwner() {
    check("isCodeOwner").that(codeOwnerCheckInfo().isCodeOwner).isTrue();
  }

  public void isNotCodeOwner() {
    check("isCodeOwner").that(codeOwnerCheckInfo().isCodeOwner).isFalse();
  }

  public void isFallbackCodeOwner() {
    check("isFallbackCodeOwner").that(codeOwnerCheckInfo().isFallbackCodeOwner).isTrue();
  }

  public void isNotFallbackCodeOwner() {
    check("isFallbackCodeOwner").that(codeOwnerCheckInfo().isFallbackCodeOwner).isFalse();
  }

  public void isResolvable() {
    check("isResolvable").that(codeOwnerCheckInfo().isResolvable).isTrue();
  }

  public void isNotResolvable() {
    check("isResolvable").that(codeOwnerCheckInfo().isResolvable).isFalse();
  }

  public void canReadRef() {
    check("canReadRef").that(codeOwnerCheckInfo().canReadRef).isTrue();
  }

  public void cannotReadRef() {
    check("canReadRef").that(codeOwnerCheckInfo().canReadRef).isFalse();
  }

  public void canReadRefNotSet() {
    check("canReadRef").that(codeOwnerCheckInfo().canReadRef).isNull();
  }

  public void canSeeChange() {
    check("canSeeChange").that(codeOwnerCheckInfo().canSeeChange).isTrue();
  }

  public void cannotSeeChange() {
    check("canSeeChange").that(codeOwnerCheckInfo().canSeeChange).isFalse();
  }

  public void canSeeChangeNotSet() {
    check("canSeeChange").that(codeOwnerCheckInfo().canSeeChange).isNull();
  }

  public void canApproveChange() {
    check("canApproveChange").that(codeOwnerCheckInfo().canApproveChange).isTrue();
  }

  public void cannotApproveChange() {
    check("canApproveChange").that(codeOwnerCheckInfo().canApproveChange).isFalse();
  }

  public void canApproveChangeNotSet() {
    check("canApproveChange").that(codeOwnerCheckInfo().canApproveChange).isNull();
  }

  public IterableSubject hasCodeOwnerConfigFilePathsThat() {
    return check("codeOwnerConfigFilePaths").that(codeOwnerCheckInfo().codeOwnerConfigFilePaths);
  }

  public void isDefaultCodeOwner() {
    check("isDefaultCodeOwner").that(codeOwnerCheckInfo().isDefaultCodeOwner).isTrue();
  }

  public void isNotDefaultCodeOwner() {
    check("isDefaultCodeOwner").that(codeOwnerCheckInfo().isDefaultCodeOwner).isFalse();
  }

  public void isGlobalCodeOwner() {
    check("isGlobalCodeOwner").that(codeOwnerCheckInfo().isGlobalCodeOwner).isTrue();
  }

  public void isNotGlobalCodeOwner() {
    check("isGlobalCodeOwner").that(codeOwnerCheckInfo().isGlobalCodeOwner).isFalse();
  }

  public void isOwnedByAllUsers() {
    check("isOwnedByAllUsers").that(codeOwnerCheckInfo().isOwnedByAllUsers).isTrue();
  }

  public void isNotOwnedByAllUsers() {
    check("isOwnedByAllUsers").that(codeOwnerCheckInfo().isOwnedByAllUsers).isFalse();
  }

  public void hasDebugLogsThatContainAllOf(String... expectedMessages) {
    for (String expectedMessage : expectedMessages) {
      check("debugLogs").that(codeOwnerCheckInfo().debugLogs).contains(expectedMessage);
    }
  }

  public void hasDebugLogsThatDoNotContainAnyOf(String... expectedMessages) {
    for (String expectedMessage : expectedMessages) {
      check("debugLogs").that(codeOwnerCheckInfo().debugLogs).doesNotContain(expectedMessage);
    }
  }

  private CodeOwnerCheckInfo codeOwnerCheckInfo() {
    isNotNull();
    return codeOwnerCheckInfo;
  }
}
