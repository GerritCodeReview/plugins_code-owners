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
import com.google.common.truth.Subject;
import com.google.gerrit.entities.Account;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerInfo;
import com.google.gerrit.truth.ListSubject;
import java.util.List;

/** {@link Subject} for doing assertions on {@link CodeOwnerInfo}s. */
public class CodeOwnerInfoSubject extends Subject {
  /**
   * Starts fluent chain to do assertions on a {@link CodeOwnerInfo}.
   *
   * @param codeOwnerInfo the code owner info on which assertions should be done
   * @return the created {@link CodeOwnerInfoSubject}
   */
  public static CodeOwnerInfoSubject assertThat(CodeOwnerInfo codeOwnerInfo) {
    return assertAbout(CodeOwnerInfoSubject::new).that(codeOwnerInfo);
  }

  public static ListSubject<CodeOwnerInfoSubject, CodeOwnerInfo> assertThatList(
      List<CodeOwnerInfo> codeOwnerInfos) {
    return ListSubject.assertThat(codeOwnerInfos, codeOwners());
  }

  private static Factory<CodeOwnerInfoSubject, CodeOwnerInfo> codeOwners() {
    return CodeOwnerInfoSubject::new;
  }

  private final CodeOwnerInfo codeOwnerInfo;

  private CodeOwnerInfoSubject(FailureMetadata metadata, CodeOwnerInfo codeOwnerInfo) {
    super(metadata, codeOwnerInfo);
    this.codeOwnerInfo = codeOwnerInfo;
  }

  /**
   * Returns a {@link ComparableSubject} for the account ID of the code owner info.
   *
   * @return {@link ComparableSubject} for the account ID of the code owner info
   */
  public ComparableSubject<Account.Id> hasAccountIdThat() {
    return check("accountId()").that(Account.id(codeOwnerInfo().account._accountId));
  }

  private CodeOwnerInfo codeOwnerInfo() {
    isNotNull();
    return codeOwnerInfo;
  }
}
