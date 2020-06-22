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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/** Class to resolve {@link CodeOwnerReference}s to {@link CodeOwner}s. */
@Singleton
public class CodeOwnerResolver {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> currentUser;
  private final ExternalIds externalIds;
  private final AccountCache accountCache;
  private final AccountControl.Factory accountControlFactory;
  private final LocalCodeOwners localCodeOwners;

  @Inject
  CodeOwnerResolver(
      PermissionBackend permissionBackend,
      Provider<CurrentUser> currentUser,
      ExternalIds externalIds,
      AccountCache accountCache,
      AccountControl.Factory accountControlFactory,
      LocalCodeOwners localCodeOwners) {
    this.permissionBackend = permissionBackend;
    this.currentUser = currentUser;
    this.externalIds = externalIds;
    this.accountCache = accountCache;
    this.accountControlFactory = accountControlFactory;
    this.localCodeOwners = localCodeOwners;
  }

  /**
   * Resolves the local code owners from the given code owner config for the given path from {@link
   * CodeOwnerReference}s to a {@link CodeOwner}s.
   *
   * <p>Non-resolvable code owners are filtered out.
   *
   * @param codeOwnerConfig the code owner config for which the local owners for the given path
   *     should be resolved
   * @param absolutePath path for which the local code owners should be returned; the path must be
   *     absolute; can be the path of a file or folder; the path may or may not exist
   * @return the resolved code owners
   */
  public ImmutableSet<CodeOwner> resolveLocalCodeOwners(
      CodeOwnerConfig codeOwnerConfig, Path absolutePath) {
    requireNonNull(codeOwnerConfig, "codeOwnerConfig");
    requireNonNull(absolutePath, "absolutePath");
    checkState(absolutePath.isAbsolute(), "path %s must be absolute", absolutePath);

    return localCodeOwners.get(codeOwnerConfig, absolutePath).stream()
        .map(this::resolve)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toImmutableSet());
  }

  /**
   * Resolves a {@link CodeOwnerReference} to a {@link CodeOwner}.
   *
   * <p>Code owners are defined by {@link CodeOwnerReference}s (e.g. emails) that need to be
   * resolved to accounts. The accounts are wrapped in {@link CodeOwner}s so that we can support
   * different kind of code owners later (e.g. groups).
   *
   * <p>Code owners that cannot be resolved are filtered out:
   *
   * <ul>
   *   <li>Emails for which no account exists: If no account exists, we cannot return any account.
   *       It's fine to filter them out as it just means nobody can claim the ownership that was
   *       assigned for this email.
   *   <li>Emails for which multiple accounts exist: If an email is ambiguous it is treated the same
   *       way as if there was no account for the email. That's because we can't tell which account
   *       was meant to have the ownership. This behaviour is consistent with the behaviour in
   *       Gerrit core that also treats ambiguous identifiers as non-resolveable.
   * </ul>
   *
   * <p>This methods checks whether the calling user can see the accounts of the code owners and
   * returns code owners whose accounts are visible.
   *
   * <p>In addition code owners that are referenced by a secondary email are only returned if the
   * calling user can see the secondary email:
   *
   * <ul>
   *   <li>every user can see the own secondary emails
   *   <li>users with the {@code Modify Account} global capability can see the secondary emails of
   *       all accounts
   * </ul>
   *
   * @param codeOwnerReference the code owner reference that should be resolved
   * @return the {@link CodeOwner} for the code owner reference if it was resolved, otherwise {@link
   *     Optional#empty()}
   */
  public Optional<CodeOwner> resolve(CodeOwnerReference codeOwnerReference) {
    String email = requireNonNull(codeOwnerReference, "codeOwnerReference").email();

    Optional<AccountState> accountState =
        lookupEmail(email).flatMap(accountId -> lookupAccount(accountId, email));
    if (!accountState.isPresent() || !isVisible(accountState.get(), email)) {
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

  /**
   * Checks whether the given account and email are visible to the calling user.
   *
   * <p>If the email is a secondary email it is only visible if it is owned by the calling user or
   * if the calling user has the {@code Modify Account} global capability.
   *
   * @param accountState the account for which it should be checked whether it's visible to the
   *     calling user
   * @param email email that was used to reference the account
   * @return {@code true} if the given account and email are visible to the calling user, otherwise
   *     {@code false}
   */
  private boolean isVisible(AccountState accountState, String email) {
    if (!accountControlFactory.get().canSee(accountState)) {
      logger.atFine().log(
          "cannot resolve code owner email %s: account %s is not visible to calling user",
          email, accountState.account().id());
      return false;
    }

    if (!email.equals(accountState.account().preferredEmail())) {
      // the email is a secondary email of the account

      if (currentUser.get().isIdentifiedUser()
          && currentUser.get().asIdentifiedUser().hasEmailAddress(email)) {
        // it's a secondary email of the calling user, users can always see their own secondary
        // emails
        return true;
      }

      // the email is a secondary email of another account, check if the calling user can see
      // secondary emails
      try {
        if (!permissionBackend.currentUser().test(GlobalPermission.MODIFY_ACCOUNT)) {
          logger.atFine().log(
              "cannot resolve code owner email %s: account %s is referenced by secondary email,"
                  + " but the calling user cannot see secondary emails",
              email, accountState.account().id());
          return false;
        }
      } catch (PermissionBackendException e) {
        throw new StorageException(
            String.format(
                "failed to test the %s global capability", GlobalPermission.MODIFY_ACCOUNT),
            e);
      }
    }

    return true;
  }
}
