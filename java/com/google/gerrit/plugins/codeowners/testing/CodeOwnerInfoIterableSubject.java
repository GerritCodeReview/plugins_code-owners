// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
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

import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.gerrit.entities.Account;
import com.google.gerrit.plugins.codeowners.restapi.CodeOwnerInfo;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** {@link IterableSubject} for doing assertions on a list of {@link CodeOwnerInfo}s. */
public class CodeOwnerInfoIterableSubject extends IterableSubject {
  /**
   * {@link Correspondence} that maps {@link CodeOwnerInfo}s to {@link
   * com.google.gerrit.entities.Account.Id}s.
   */
  private static final Correspondence<CodeOwnerInfo, Account.Id> CODE_OWNER_INFO_TO_ACCOUNT_ID =
      Correspondence.from(
          (actualCodeOwnerInfo, expectedAccountId) -> {
            Account.Id accountId =
                Optional.ofNullable(actualCodeOwnerInfo)
                    .map(codeOwnerInfo -> Account.id(codeOwnerInfo.account._accountId))
                    .orElse(null);
            return Objects.equals(accountId, expectedAccountId);
          },
          "has account ID");

  /** {@link Correspondence} that maps {@link CodeOwnerInfo}s to account names. */
  private static final Correspondence<CodeOwnerInfo, String> CODE_OWNER_INFO_TO_ACCOUNT_NAME =
      Correspondence.from(
          (actualCodeOwnerInfo, expectedAccountName) -> {
            String accountName =
                Optional.ofNullable(actualCodeOwnerInfo)
                    .map(codeOwnerInfo -> codeOwnerInfo.account.name)
                    .orElse(null);
            return Objects.equals(accountName, expectedAccountName);
          },
          "has account name");

  /**
   * Starts fluent chain to do assertions on a list of {@link CodeOwnerInfo}s.
   *
   * @param codeOwnerInfos the list of code owners on which assertions should be done
   * @return the created {@link CodeOwnerInfoIterableSubject}
   */
  public static CodeOwnerInfoIterableSubject assertThat(List<CodeOwnerInfo> codeOwnerInfos) {
    return assertAbout(CodeOwnerInfoIterableSubject::new).that(codeOwnerInfos);
  }

  private final List<CodeOwnerInfo> codeOwnerInfos;

  private CodeOwnerInfoIterableSubject(
      FailureMetadata metadata, List<CodeOwnerInfo> codeOwnerInfos) {
    super(metadata, codeOwnerInfos);
    this.codeOwnerInfos = codeOwnerInfos;
  }

  /**
   * Returns an {@link IterableSubject} for the account IDs of the code owner infos.
   *
   * @return {@link IterableSubject} for the account IDs of the code owner infos
   */
  public IterableSubject.UsingCorrespondence<CodeOwnerInfo, Account.Id> hasAccountIdsThat() {
    return check("accountIds()")
        .that(codeOwnerInfos())
        .comparingElementsUsing(CODE_OWNER_INFO_TO_ACCOUNT_ID);
  }

  /**
   * Returns an {@link IterableSubject} for the account names of the code owner infos.
   *
   * @return {@link IterableSubject} for the account names of the code owner infos
   */
  public IterableSubject.UsingCorrespondence<CodeOwnerInfo, String> hasAccountNamesThat() {
    return check("accountNames()")
        .that(codeOwnerInfos())
        .comparingElementsUsing(CODE_OWNER_INFO_TO_ACCOUNT_NAME);
  }

  private List<CodeOwnerInfo> codeOwnerInfos() {
    isNotNull();
    return codeOwnerInfos;
  }
}
