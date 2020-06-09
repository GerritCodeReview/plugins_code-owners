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
import com.google.gerrit.plugins.codeowners.backend.CodeOwner;
import com.google.gerrit.truth.OptionalSubject;
import java.util.Optional;

/** {@link Subject} for doing assertions on {@link CodeOwner}s. */
public class CodeOwnerSubject extends Subject {
  /**
   * Starts fluent chain to do assertions on a {@link CodeOwner}.
   *
   * @param codeOwner the code owner on which assertions should be done
   * @return the created {@link CodeOwnerSubject}
   */
  public static CodeOwnerSubject assertThat(CodeOwner codeOwner) {
    return assertAbout(CodeOwnerSubject::new).that(codeOwner);
  }

  /**
   * Starts fluent chain to do assertions on an {@link Optional} {@link CodeOwner}.
   *
   * @param codeOwner optional code owner on which assertions should be done
   * @return the created {@link OptionalSubject}
   */
  public static OptionalSubject<CodeOwnerSubject, CodeOwner> assertThat(
      Optional<CodeOwner> codeOwner) {
    return OptionalSubject.assertThat(codeOwner, codeOwners());
  }

  /** Creates subject factory for mapping {@link CodeOwner}s to {@link CodeOwnerSubject}s. */
  private static Subject.Factory<CodeOwnerSubject, CodeOwner> codeOwners() {
    return CodeOwnerSubject::new;
  }

  private final CodeOwner codeOwner;

  private CodeOwnerSubject(FailureMetadata metadata, CodeOwner codeOwner) {
    super(metadata, codeOwner);
    this.codeOwner = codeOwner;
  }

  /**
   * Returns a {@link ComparableSubject} for the account ID of the code owner.
   *
   * @return {@link ComparableSubject} for the account ID of the code owner
   */
  public ComparableSubject<Account.Id> hasAccountIdThat() {
    return check("accountId()").that(codeOwner().accountId());
  }

  private CodeOwner codeOwner() {
    isNotNull();
    return codeOwner;
  }
}
