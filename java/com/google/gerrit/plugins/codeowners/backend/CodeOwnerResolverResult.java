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

package com.google.gerrit.plugins.codeowners.backend;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;

/** The result of resolving code owner references via {@link CodeOwnerResolver}. */
@AutoValue
public abstract class CodeOwnerResolverResult {
  /**
   * Returns the resolved code owners as stream.
   *
   * <p>Doesn't include code owners to which the code ownership was assigned by using the {@link
   * CodeOwnerResolver#ALL_USERS_WILDCARD}.
   */
  public abstract ImmutableSet<CodeOwner> codeOwners();

  /** Returns the account IDs of the resolved code owners as set. */
  public ImmutableSet<Account.Id> codeOwnersAccountIds() {
    return codeOwners().stream().map(CodeOwner::accountId).collect(toImmutableSet());
  }

  /**
   * Whether the code ownership was assigned to all users by using the {@link
   * CodeOwnerResolver#ALL_USERS_WILDCARD}.
   */
  public abstract boolean ownedByAllUsers();

  /** Whether there are code owner references which couldn't be resolved. */
  public abstract boolean hasUnresolvedCodeOwners();

  /**
   * Whether there are any code owners defined for the path, regardless of whether they can be
   * resolved or not.
   */
  public boolean hasRevelantCodeOwnerDefinitions() {
    return !codeOwners().isEmpty() || ownedByAllUsers() || hasUnresolvedCodeOwners();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("codeOwners", codeOwners())
        .add("ownedByAllUsers", ownedByAllUsers())
        .add("hasUnresolvedCodeOwners", hasUnresolvedCodeOwners())
        .toString();
  }

  /** Creates a {@link CodeOwnerResolverResult} instance. */
  public static CodeOwnerResolverResult create(
      ImmutableSet<CodeOwner> codeOwners,
      boolean ownedByAllUsers,
      boolean hasUnresolvedCodeOwners) {
    return new AutoValue_CodeOwnerResolverResult(
        codeOwners, ownedByAllUsers, hasUnresolvedCodeOwners);
  }
}
