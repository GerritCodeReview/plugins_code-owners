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

import com.google.common.collect.ImmutableSet;
import com.google.common.truth.ComparableSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.PathSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.entities.Account;
import com.google.gerrit.plugins.codeowners.backend.PathCodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;

/** {@link Subject} for doing assertions on {@link PathCodeOwnerStatus}s. */
public class PathCodeOwnerStatusSubject extends Subject {
  /**
   * Creates subject factory for mapping {@link PathCodeOwnerStatus}es to {@link
   * PathCodeOwnerStatusSubject}s.
   */
  public static Subject.Factory<PathCodeOwnerStatusSubject, PathCodeOwnerStatus>
      pathCodeOwnerStatuses() {
    return PathCodeOwnerStatusSubject::new;
  }

  private final PathCodeOwnerStatus pathCodeOwnerStatus;

  private PathCodeOwnerStatusSubject(
      FailureMetadata metadata, PathCodeOwnerStatus pathCodeOwnerStatus) {
    super(metadata, pathCodeOwnerStatus);
    this.pathCodeOwnerStatus = pathCodeOwnerStatus;
  }

  /** Returns a {@link ComparableSubject} for the path. */
  public PathSubject hasPathThat() {
    return check("path").that(pathCodeOwnerStatus().path());
  }

  /** Returns a {@link ComparableSubject} for the code owner status. */
  public ComparableSubject<CodeOwnerStatus> hasStatusThat() {
    return check("status").that(pathCodeOwnerStatus().status());
  }

  /** Returns a {@link ComparableSubject} for the code owner owners. */
  public IterableSubject hasOwnersThat() {
    return check("owners.get").that(pathCodeOwnerStatus.owners().get());
  }

  public void hasOwners(TestAccount... testAccounts) {
    ImmutableSet.Builder<Account.Id> accounts = ImmutableSet.builder();
    for (TestAccount testAccount : testAccounts) {
      accounts.add(testAccount.id());
    }
    hasOwnersThat().containsExactlyElementsIn(accounts.build());
  }

  private PathCodeOwnerStatus pathCodeOwnerStatus() {
    isNotNull();
    return pathCodeOwnerStatus;
  }
}
