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

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

/** Class to resolve {@link CodeOwnerReference}s to {@link CodeOwner}s. */
@Singleton
public class CodeOwnerResolver {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ExternalIds externalIds;
  private final AccountCache accountCache;

  @Inject
  CodeOwnerResolver(ExternalIds externalIds, AccountCache accountCache) {
    this.externalIds = externalIds;
    this.accountCache = accountCache;
  }

  /**
   * Resolves a {@link CodeOwnerReference} to a {@link CodeOwner}.
   *
   * <p>Code owners are defined by {@link CodeOwnerReference}s (e.g. emails) that need to be
   * resolved to accounts. The accounts are wrapped in {@link CodeOwner}s so that we can support
   * different kind of code owners later (e.g. groups).
   *
   * <p>Code owners that cannot be resolved (e.g. an email for which no account exists or email for
   * which multiple accounts exist) are filtered out.
   *
   * @param codeOwnerReference the code owner reference that should be resolved
   * @return the {@link CodeOwner} for the code owner reference if it was resolved, otherwise {@link
   *     Optional#empty()}
   */
  public Optional<CodeOwner> resolve(CodeOwnerReference codeOwnerReference) {
    String email = requireNonNull(codeOwnerReference, "codeOwnerReference").email();

    Optional<AccountState> accountState =
        lookupEmail(email).flatMap(accountId -> lookupAccount(accountId, email));
    if (!accountState.isPresent()) {
      return Optional.empty();
    }

    return Optional.of(CodeOwner.create(accountState.get().account().id()));
  }

  /**
   * Looks up an email and returns the ID of the account to which it belongs.
   *
   * <p>If the email is ambiguous (it belongs to multiple accounts) it is considered as
   * non-resolvable and {@link Optional#empty()} is returned.
   *
   * @param email the email that should be looked up
   * @return the ID of the account to which the email belongs if was found
   */
  private Optional<Account.Id> lookupEmail(String email) {
    ImmutableSet<ExternalId> extIds;
    try {
      extIds = externalIds.byEmail(email);
    } catch (IOException e) {
      throw new StorageException(String.format("cannot resolve code owner email %s", email), e);
    }

    if (extIds.isEmpty()) {
      logger.atFine().log(
          "cannot resolve code owner email %s: no account with this email exists", email);
      return Optional.empty();
    }

    if (extIds.stream().map(extId -> extId.accountId()).distinct().count() > 1) {
      logger.atFine().log("cannot resolve code owner email %s: email is ambiguous", email);
      return Optional.empty();
    }

    return Optional.of(extIds.stream().findFirst().get().accountId());
  }

  /**
   * Looks up an account by account ID and returns the corresponding {@link AccountState} if it is
   * found.
   *
   * @param accountId the ID of the account that should be looked up
   * @param email the email that was resolved to the account ID
   * @return the {@link AccountState} of the account with the given account ID, if it exists
   */
  private Optional<AccountState> lookupAccount(Account.Id accountId, String email) {
    Optional<AccountState> accountState = accountCache.get(accountId);
    if (!accountState.isPresent()) {
      logger.atFine().log(
          "cannot resolve code owner email %s: email belongs to account %s,"
              + " but no account with this ID exists",
          email, accountId);
      return Optional.empty();
    }
    return accountState;
  }
}
