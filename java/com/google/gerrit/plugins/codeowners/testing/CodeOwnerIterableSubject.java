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
import com.google.gerrit.plugins.codeowners.backend.CodeOwner;
import java.util.Objects;
import java.util.Optional;

/** {@link IterableSubject} for doing assertions on an {@link Iterable} of {@link CodeOwner}s. */
public class CodeOwnerIterableSubject extends IterableSubject {
  /**
   * {@link Correspondence} that maps {@link CodeOwner}s to {@link
   * com.google.gerrit.entities.Account.Id}s.
   */
  private static final Correspondence<CodeOwner, Account.Id> CODE_OWNER_TO_ACCOUNT_ID =
      Correspondence.from(
          (actualCodeOwner, expectedAccountId) -> {
            Account.Id accountId =
                Optional.ofNullable(actualCodeOwner).map(CodeOwner::accountId).orElse(null);
            return Objects.equals(accountId, expectedAccountId);
          },
          "has account ID");

  /**
   * Starts fluent chain to do assertions on an {@link Iterable} of {@link CodeOwner}s.
   *
   * @param codeOwners the code owners on which assertions should be done
   * @return the created {@link CodeOwnerIterableSubject}
   */
  public static CodeOwnerIterableSubject assertThat(Iterable<CodeOwner> codeOwners) {
    return assertAbout(CodeOwnerIterableSubject::new).that(codeOwners);
  }

  private final Iterable<CodeOwner> codeOwners;

  private CodeOwnerIterableSubject(FailureMetadata metadata, Iterable<CodeOwner> codeOwners) {
    super(metadata, codeOwners);
    this.codeOwners = codeOwners;
  }

  /**
   * Returns an {@link IterableSubject} for the account IDs of the code owners.
   *
   * @return {@link IterableSubject} for the account IDs of the code owners
   */
  public IterableSubject.UsingCorrespondence<CodeOwner, Account.Id> hasAccountIdsThat() {
    return check("accountIds()")
        .that(codeOwners())
        .comparingElementsUsing(CODE_OWNER_TO_ACCOUNT_ID);
  }

  private Iterable<CodeOwner> codeOwners() {
    isNotNull();
    return codeOwners;
  }
}
