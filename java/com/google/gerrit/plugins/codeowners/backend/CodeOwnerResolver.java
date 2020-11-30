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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Class to resolve {@link CodeOwnerReference}s to {@link CodeOwner}s. */
public class CodeOwnerResolver {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting public static final String ALL_USERS_WILDCARD = "*";

  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> currentUser;
  private final ExternalIds externalIds;
  private final AccountCache accountCache;
  private final AccountControl.Factory accountControlFactory;
  private final PathCodeOwners.Factory pathCodeOwnersFactory;

  // Enforce visibility by default.
  private boolean enforceVisibility = true;

  // The the user that should be used to check the account visibility (whether this user can see the
  // accounts of the code owners).
  // If unset, the current user is used.
  private IdentifiedUser user;

  @Inject
  CodeOwnerResolver(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      PermissionBackend permissionBackend,
      Provider<CurrentUser> currentUser,
      ExternalIds externalIds,
      AccountCache accountCache,
      AccountControl.Factory accountControlFactory,
      PathCodeOwners.Factory pathCodeOwnersFactory) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.permissionBackend = permissionBackend;
    this.currentUser = currentUser;
    this.externalIds = externalIds;
    this.accountCache = accountCache;
    this.accountControlFactory = accountControlFactory;
    this.pathCodeOwnersFactory = pathCodeOwnersFactory;
  }

  /**
   * Whether it should be enforced that the returned code owners are visible to the current user.
   *
   * @param enforceVisibility whether it should be enforced that the returned code owners are
   *     visible to the current user
   * @return the {@link CodeOwnerResolver} instance for chaining calls
   */
  public CodeOwnerResolver enforceVisibility(boolean enforceVisibility) {
    logger.atFine().log("enforceVisibility = %s", enforceVisibility);
    this.enforceVisibility = enforceVisibility;
    return this;
  }

  /**
   * Sets the user that should be used to check the account visibility (whether this user can see
   * the accounts of the code owners).
   *
   * <p>This overrides the current user that would be used to check the account visibility if this
   * method was not called.
   *
   * <p>Using this method it's possible to resolve code owner references for accounts that are
   * visible to the given user, but not visible to the current user. Callers must use this method
   * with care to not expose the existence of non-visible accounts to the current user.
   *
   * @param user the user that should be used to check the account visibility (whether this user can
   *     see the accounts of the code owners)
   * @return the {@link CodeOwnerResolver} instance for chaining calls
   */
  public CodeOwnerResolver forUser(IdentifiedUser user) {
    logger.atFine().log("user = %s", user.getLoggableName());
    this.user = user;
    return this;
  }

  /** Whether the given code owner reference can be resolved. */
  public boolean isResolvable(CodeOwnerReference codeOwnerReference) {
    CodeOwnerResolverResult result = resolve(ImmutableSet.of(codeOwnerReference));
    return !result.codeOwners().isEmpty() || result.ownedByAllUsers();
  }

  /**
   * Resolves the code owners from the given code owner config for the given path from {@link
   * CodeOwnerReference}s to a {@link CodeOwner}s.
   *
   * <p>Non-resolvable code owners are filtered out.
   *
   * @param codeOwnerConfig the code owner config for which the local owners for the given path
   *     should be resolved
   * @param absolutePath path for which the code owners should be returned; the path must be
   *     absolute; can be the path of a file or folder; the path may or may not exist
   * @return the resolved code owners
   */
  public CodeOwnerResolverResult resolvePathCodeOwners(
      CodeOwnerConfig codeOwnerConfig, Path absolutePath) {
    requireNonNull(codeOwnerConfig, "codeOwnerConfig");
    requireNonNull(absolutePath, "absolutePath");
    checkState(absolutePath.isAbsolute(), "path %s must be absolute", absolutePath);

    try (TraceTimer traceTimer =
        TraceContext.newTimer(
            "Resolve path code owners",
            Metadata.builder()
                .projectName(codeOwnerConfig.key().project().get())
                .branchName(codeOwnerConfig.key().ref())
                .filePath(codeOwnerConfig.key().fileName().orElse("<default>"))
                .build())) {
      logger.atFine().log("resolving path code owners for path %s", absolutePath);
      PathCodeOwnersResult pathCodeOwnersResult =
          pathCodeOwnersFactory.create(codeOwnerConfig, absolutePath).resolveCodeOwnerConfig();
      return resolve(
          pathCodeOwnersResult.getPathCodeOwners(), pathCodeOwnersResult.hasUnresolvedImports());
    }
  }

  /**
   * Resolves the global code owners for the given project.
   *
   * @param projectName the name of the project for which the global code owners should be resolved
   * @return the resolved global code owners of the given project
   */
  public CodeOwnerResolverResult resolveGlobalCodeOwners(Project.NameKey projectName) {
    return resolve(codeOwnersPluginConfiguration.getGlobalCodeOwners(projectName));
  }

  /**
   * Resolves the given {@link CodeOwnerReference}s to {@link CodeOwner}s.
   *
   * @param codeOwnerReferences the code owner references that should be resolved
   * @return the {@link CodeOwner} for the given code owner references
   * @see #resolve(CodeOwnerReference)
   */
  public CodeOwnerResolverResult resolve(Set<CodeOwnerReference> codeOwnerReferences) {
    return resolve(codeOwnerReferences, /* hasUnresolvedImports= */ false);
  }

  /**
   * Resolves the given {@link CodeOwnerReference}s to {@link CodeOwner}s.
   *
   * @param codeOwnerReferences the code owner references that should be resolved
   * @param hasUnresolvedImports whether there are unresolved imports
   * @return the {@link CodeOwner} for the given code owner references
   * @see #resolve(CodeOwnerReference)
   */
  private CodeOwnerResolverResult resolve(
      Set<CodeOwnerReference> codeOwnerReferences, boolean hasUnresolvedImports) {
    requireNonNull(codeOwnerReferences, "codeOwnerReferences");
    AtomicBoolean ownedByAllUsers = new AtomicBoolean(false);
    AtomicBoolean hasUnresolvedCodeOwners = new AtomicBoolean(false);
    ImmutableSet<CodeOwner> codeOwners =
        codeOwnerReferences.stream()
            .filter(
                codeOwnerReference -> {
                  if (ALL_USERS_WILDCARD.equals(codeOwnerReference.email())) {
                    ownedByAllUsers.set(true);
                    return false;
                  }
                  return true;
                })
            .map(this::resolve)
            .filter(
                codeOwner -> {
                  if (!codeOwner.isPresent()) {
                    hasUnresolvedCodeOwners.set(true);
                    return false;
                  }
                  return true;
                })
            .map(Optional::get)
            .collect(toImmutableSet());
    return CodeOwnerResolverResult.create(
        codeOwners, ownedByAllUsers.get(), hasUnresolvedCodeOwners.get(), hasUnresolvedImports);
  }

  /**
   * Resolves a {@link CodeOwnerReference} to {@link CodeOwner}s.
   *
   * <p>Code owners are defined by {@link CodeOwnerReference}s (e.g. emails) that need to be
   * resolved to accounts. The accounts are wrapped in {@link CodeOwner}s so that we can support
   * different kind of code owners later (e.g. groups).
   *
   * <p>Code owners that cannot be resolved are filtered out:
   *
   * <ul>
   *   <li>Emails that have a non-allowed email domain (see config parameter {@code
   *       plugin.code-owners.allowedEmailDomain}).
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
   * <p>This method does not resolve {@link CodeOwnerReference}s that assign the code ownership to
   * all user by using {@link #ALL_USERS_WILDCARD} as email.
   *
   * @param codeOwnerReference the code owner reference that should be resolved
   * @return the {@link CodeOwner} for the code owner reference if it was resolved, otherwise {@link
   *     Optional#empty()}
   */
  public Optional<CodeOwner> resolve(CodeOwnerReference codeOwnerReference) {
    String email = requireNonNull(codeOwnerReference, "codeOwnerReference").email();
    logger.atFine().log("resolving code owner reference %s", codeOwnerReference);

    if (!isEmailDomainAllowed(email)) {
      logger.atFine().log("domain of email %s is not allowed", email);
      return Optional.empty();
    }

    Optional<AccountState> accountState =
        lookupEmail(email).flatMap(accountId -> lookupAccount(accountId, email));
    if (!accountState.isPresent()) {
      logger.atFine().log("no account for email %s", email);
      return Optional.empty();
    }
    if (!accountState.get().account().isActive()) {
      logger.atFine().log("account for email %s is inactive", email);
      return Optional.empty();
    }
    if (enforceVisibility && !isVisible(accountState.get(), email)) {
      logger.atFine().log(
          "account %d or email %s not visible", accountState.get().account().id().get(), email);
      return Optional.empty();
    }

    CodeOwner codeOwner = CodeOwner.create(accountState.get().account().id());
    logger.atFine().log("resolved to code owner %s", codeOwner);
    return Optional.of(codeOwner);
  }

  /** Whether the given account can be seen. */
  private boolean canSee(AccountState accountState) {
    AccountControl accountControl =
        user != null ? accountControlFactory.get(user) : accountControlFactory.get();
    return accountControl.canSee(accountState);
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

    if (extIds.stream().map(ExternalId::accountId).distinct().count() > 1) {
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
    if (!canSee(accountState)) {
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

  /**
   * Whether the domain of the given email is allowed for code owners.
   *
   * @param email the email for which the domain should be checked
   * @return {@code true} if the domain of the given email is allowed for code owners, otherwise
   *     {@code false}
   */
  public boolean isEmailDomainAllowed(String email) {
    requireNonNull(email, "email");

    ImmutableSet<String> allowedEmailDomains =
        codeOwnersPluginConfiguration.getAllowedEmailDomains();
    if (allowedEmailDomains.isEmpty()) {
      // all domains are allowed
      return true;
    }

    if (email.equals(ALL_USERS_WILDCARD)) {
      return true;
    }

    int emailAtIndex = email.lastIndexOf('@');
    if (emailAtIndex >= 0 && emailAtIndex < email.length() - 1) {
      String emailDomain = email.substring(emailAtIndex + 1);
      logger.atFine().log("email domain = %s", emailDomain);
      return allowedEmailDomains.contains(emailDomain);
    }

    // email has no domain
    logger.atFine().log("email %s has no domain", email);
    return false;
  }
}
