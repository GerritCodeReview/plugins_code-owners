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

import com.google.common.truth.ComparableSubject;
import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.entities.Account;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerScore;
import com.google.gerrit.truth.NullAwareCorrespondence;

/** {@link Subject} for doing assertions on {@link CodeOwnerInfo}s. */
public class CodeOwnerInfoSubject extends Subject {
  /**
   * Constructs a {@link Correspondence} that maps {@link CodeOwnerInfo}s to {@link
   * com.google.gerrit.entities.Account.Id}s.
   */
  public static Correspondence<CodeOwnerInfo, Account.Id> hasAccountId() {
    return NullAwareCorrespondence.transforming(
        codeOwnerInfo -> Account.id(codeOwnerInfo.account._accountId), "has account ID");
  }

  /** Constructs a {@link Correspondence} that maps {@link CodeOwnerInfo}s to account names. */
  public static final Correspondence<CodeOwnerInfo, String> hasAccountName() {
    return NullAwareCorrespondence.transforming(
        codeOwnerInfo -> codeOwnerInfo.account.name, "has account name");
  }

  /** Constructs a {@link Correspondence} that maps {@link CodeOwnerInfo}s to scoring. */
  public static final Correspondence<CodeOwnerInfo, Integer> hasScoring(CodeOwnerScore score) {
    return NullAwareCorrespondence.transforming(
        codeOwnerInfo -> codeOwnerInfo.scorings.entrySet().stream()
            .filter(factor -> factor.getKey().equals(score.name()))
            .findFirst()
            .orElseThrow()
            .getValue(),
        "has scoring");
  }

  public static Factory<CodeOwnerInfoSubject, CodeOwnerInfo> codeOwnerInfos() {
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

  /**
   * Returns an {@link IterableSubject} for the secondary emails of the code owner info.
   *
   * @return {@link IterableSubject} for the secondary emails of the code owner info
   */
  public IterableSubject hasSecondaryEmailsThat() {
    return check("account().secondaryEmails()").that(codeOwnerInfo().account.secondaryEmails);
  }

  private CodeOwnerInfo codeOwnerInfo() {
    isNotNull();
    return codeOwnerInfo;
  }
}
