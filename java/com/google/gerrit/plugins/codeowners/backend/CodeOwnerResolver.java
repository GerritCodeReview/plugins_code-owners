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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
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
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
  private final CodeOwnerMetrics codeOwnerMetrics;
  private final UnresolvedImportFormatter unresolvedImportFormatter;

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
      PathCodeOwners.Factory pathCodeOwnersFactory,
      CodeOwnerMetrics codeOwnerMetrics,
      UnresolvedImportFormatter unresolvedImportFormatter) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.permissionBackend = permissionBackend;
    this.currentUser = currentUser;
    this.externalIds = externalIds;
    this.accountCache = accountCache;
    this.accountControlFactory = accountControlFactory;
    this.pathCodeOwnersFactory = pathCodeOwnersFactory;
    this.codeOwnerMetrics = codeOwnerMetrics;
    this.unresolvedImportFormatter = unresolvedImportFormatter;
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
   * <p>If the code owner config has already been resolved to {@link PathCodeOwners}, prefer calling
   * {@link #resolvePathCodeOwners(PathCodeOwners)} instead, so that {@link PathCodeOwners} do not
   * need to be created again.
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
    return resolvePathCodeOwners(
        pathCodeOwnersFactory.createWithoutCache(codeOwnerConfig, absolutePath));
  }

  /**
   * Resolves the the given path code owners from {@link CodeOwnerReference}s to a {@link
   * CodeOwner}s.
   *
   * <p>Non-resolvable code owners are filtered out.
   *
   * @param pathCodeOwners the path code owners that should be resolved
   * @return the resolved code owners
   */
  public CodeOwnerResolverResult resolvePathCodeOwners(PathCodeOwners pathCodeOwners) {
    requireNonNull(pathCodeOwners, "pathCodeOwners");

    try (Timer0.Context ctx = codeOwnerMetrics.resolvePathCodeOwners.start()) {
      logger.atFine().log(
          "resolve path code owners (code owner config = %s, path = %s)",
          pathCodeOwners.getCodeOwnerConfig().key(), pathCodeOwners.getPath());
      OptionalResultWithMessages<PathCodeOwnersResult> pathCodeOwnersResult =
          pathCodeOwners.resolveCodeOwnerConfig();
      return resolve(
          pathCodeOwnersResult.get().getPathCodeOwners(),
          pathCodeOwnersResult.get().unresolvedImports(),
          pathCodeOwnersResult.messages());
    }
  }

  /**
   * Resolves the global code owners for the given project.
   *
   * @param projectName the name of the project for which the global code owners should be resolved
   * @return the resolved global code owners of the given project
   */
  public CodeOwnerResolverResult resolveGlobalCodeOwners(Project.NameKey projectName) {
    return resolve(
        codeOwnersPluginConfiguration.getProjectConfig(projectName).getGlobalCodeOwners());
  }

  /**
   * Resolves the given {@link CodeOwnerReference}s to {@link CodeOwner}s.
   *
   * @param codeOwnerReferences the code owner references that should be resolved
   * @return the {@link CodeOwner} for the given code owner references
   * @see #resolve(CodeOwnerReference)
   */
  public CodeOwnerResolverResult resolve(Set<CodeOwnerReference> codeOwnerReferences) {
    return resolve(
        codeOwnerReferences,
        /* unresolvedImports= */ ImmutableList.of(),
        /* pathCodeOwnersMessages= */ ImmutableList.of());
  }

  /**
   * Resolves the given {@link CodeOwnerReference}s to {@link CodeOwner}s.
   *
   * @param codeOwnerReferences the code owner references that should be resolved
   * @param unresolvedImports list of unresolved imports
   * @param pathCodeOwnersMessages messages that were collected when resolving path code owners
   * @return the {@link CodeOwner} for the given code owner references
   * @see #resolve(CodeOwnerReference)
   */
  private CodeOwnerResolverResult resolve(
      Set<CodeOwnerReference> codeOwnerReferences,
      List<UnresolvedImport> unresolvedImports,
      ImmutableList<String> pathCodeOwnersMessages) {
    requireNonNull(codeOwnerReferences, "codeOwnerReferences");
    requireNonNull(unresolvedImports, "unresolvedImports");
    requireNonNull(pathCodeOwnersMessages, "pathCodeOwnersMessages");

    try (Timer0.Context ctx = codeOwnerMetrics.resolveCodeOwnerReferences.start()) {
      AtomicBoolean ownedByAllUsers = new AtomicBoolean(false);
      AtomicBoolean hasUnresolvedCodeOwners = new AtomicBoolean(false);
      List<String> messages = new ArrayList<>(pathCodeOwnersMessages);
      unresolvedImports.forEach(
          unresolvedImport -> messages.add(unresolvedImportFormatter.format(unresolvedImport)));
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
              .map(this::resolveWithMessages)
              .filter(
                  resolveResult -> {
                    messages.addAll(resolveResult.messages());
                    if (!resolveResult.isPresent()) {
                      hasUnresolvedCodeOwners.set(true);
                      return false;
                    }
                    return true;
                  })
              .map(OptionalResultWithMessages::get)
              .collect(toImmutableSet());
      CodeOwnerResolverResult codeOwnerResolverResult =
          CodeOwnerResolverResult.create(
              codeOwners,
              ownedByAllUsers.get(),
              hasUnresolvedCodeOwners.get(),
              !unresolvedImports.isEmpty(),
              messages);
      logger.atFine().log("resolve result = %s", codeOwnerResolverResult);
      return codeOwnerResolverResult;
    }
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
   * <p>This methods checks whether the {@link #user} or the calling user (if {@link #user} is
   * unset) can see the accounts of the code owners and returns code owners whose accounts are
   * visible.
   *
   * <p>In addition code owners that are referenced by a secondary email are only returned if the
   * {@link #user} or the calling user (if {@link #user} is unset) can see the secondary email:
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
    OptionalResultWithMessages<CodeOwner> resolveResult = resolveWithMessages(codeOwnerReference);
    logger.atFine().log("resolve result = %s", resolveResult);
    return resolveResult.result();
  }

  public OptionalResultWithMessages<CodeOwner> resolveWithMessages(
      CodeOwnerReference codeOwnerReference) {
    String email = requireNonNull(codeOwnerReference, "codeOwnerReference").email();

    List<String> messages = new ArrayList<>();
    messages.add(String.format("resolving code owner reference %s", codeOwnerReference));

    OptionalResultWithMessages<Boolean> emailDomainAllowedResult = isEmailDomainAllowed(email);
    messages.addAll(emailDomainAllowedResult.messages());
    if (!emailDomainAllowedResult.get()) {
      return OptionalResultWithMessages.createEmpty(messages);
    }

    OptionalResultWithMessages<AccountState> activeAccountResult =
        lookupActiveAccountForEmail(email);
    messages.addAll(activeAccountResult.messages());
    if (activeAccountResult.isEmpty()) {
      return OptionalResultWithMessages.createEmpty(messages);
    }

    AccountState accountState = activeAccountResult.get();
    if (enforceVisibility) {
      OptionalResultWithMessages<Boolean> isVisibleResult = isVisible(accountState, email);
      messages.addAll(isVisibleResult.messages());
      if (!isVisibleResult.get()) {
        return OptionalResultWithMessages.createEmpty(messages);
      }
    } else {
      messages.add("code owner visibility is not checked");
    }

    CodeOwner codeOwner = CodeOwner.create(accountState.account().id());
    messages.add(String.format("resolved to account %s", codeOwner.accountId()));
    return OptionalResultWithMessages.create(codeOwner, messages);
  }

  /** Whether the given account can be seen. */
  private boolean canSee(AccountState accountState) {
    AccountControl accountControl =
        user != null ? accountControlFactory.get(user) : accountControlFactory.get();
    return accountControl.canSee(accountState);
  }

  /**
   * Looks up an email and returns the ID of the active account to which it belongs.
   *
   * <p>If the email is ambiguous (it belongs to multiple active accounts) it is considered as
   * non-resolvable and empty result is returned.
   *
   * @param email the email that should be looked up
   * @return the ID of the account to which the email belongs if was found
   */
  private OptionalResultWithMessages<AccountState> lookupActiveAccountForEmail(String email) {
    ImmutableSet<ExternalId> extIds;
    try {
      extIds = externalIds.byEmail(email);
    } catch (IOException e) {
      throw new CodeOwnersInternalServerErrorException(
          String.format("cannot resolve code owner email %s", email), e);
    }

    if (extIds.isEmpty()) {
      return OptionalResultWithMessages.createEmpty(
          String.format(
              "cannot resolve code owner email %s: no account with this email exists", email));
    }

    List<String> messages = new ArrayList<>();
    OptionalResultWithMessages<ImmutableSet<AccountState>> activeAccountsResult =
        lookupActiveAccounts(extIds, email);
    ImmutableSet<AccountState> activeAccounts = activeAccountsResult.get();
    messages.addAll(activeAccountsResult.messages());

    if (activeAccounts.isEmpty()) {
      messages.add(
          String.format(
              "cannot resolve code owner email %s: no active account with this email found",
              email));
      return OptionalResultWithMessages.createEmpty(messages);
    }

    if (activeAccounts.size() > 1) {
      messages.add(String.format("cannot resolve code owner email %s: email is ambiguous", email));
      return OptionalResultWithMessages.createEmpty(messages);
    }

    return OptionalResultWithMessages.create(Iterables.getOnlyElement(activeAccounts));
  }

  private OptionalResultWithMessages<ImmutableSet<AccountState>> lookupActiveAccounts(
      ImmutableSet<ExternalId> extIds, String email) {
    ImmutableSet<OptionalResultWithMessages<AccountState>> accountStateResults =
        extIds.stream()
            .map(externalId -> lookupAccount(externalId.accountId(), externalId.email()))
            .collect(toImmutableSet());

    ImmutableSet.Builder<AccountState> activeAccounts = ImmutableSet.builder();
    List<String> messages = new ArrayList<>();
    for (OptionalResultWithMessages<AccountState> accountStateResult : accountStateResults) {
      messages.addAll(accountStateResult.messages());
      if (accountStateResult.isPresent()) {
        AccountState accountState = accountStateResult.get();
        if (accountState.account().isActive()) {
          activeAccounts.add(accountState);
        } else {
          messages.add(
              String.format(
                  "account %s for email %s is inactive", accountState.account().id(), email));
        }
      }
    }
    return OptionalResultWithMessages.create(activeAccounts.build(), messages);
  }

  /**
   * Looks up an account by account ID and returns the corresponding {@link AccountState} if it is
   * found.
   *
   * @param accountId the ID of the account that should be looked up
   * @param email the email that was resolved to the account ID
   * @return the {@link AccountState} of the account with the given account ID, if it exists
   */
  private OptionalResultWithMessages<AccountState> lookupAccount(
      Account.Id accountId, String email) {
    Optional<AccountState> accountState = accountCache.get(accountId);
    if (!accountState.isPresent()) {
      return OptionalResultWithMessages.createEmpty(
          String.format(
              "cannot resolve account %s for email %s: account does not exists", accountId, email));
    }
    return OptionalResultWithMessages.create(accountState.get());
  }

  /**
   * Checks whether the given account and email are visible to the {@link #user} or the calling user
   * (if {@link #user} is unset).
   *
   * <p>If the email is a secondary email it is only visible if
   *
   * <ul>
   *   <li>it is owned by the {@link #user} or the calling user (if {@link #user} is unset)
   *   <li>if the {@link #user} or the calling user (if {@link #user} is unset) has the {@code
   *       Modify Account} global capability
   * </ul>
   *
   * @param accountState the account for which it should be checked whether it's visible to the user
   * @param email email that was used to reference the account
   * @return {@code true} if the given account and email are visible to the user, otherwise {@code
   *     false}
   */
  private OptionalResultWithMessages<Boolean> isVisible(AccountState accountState, String email) {
    if (!canSee(accountState)) {
      return OptionalResultWithMessages.create(
          false,
          String.format(
              "cannot resolve code owner email %s: account %s is not visible to user %s",
              email,
              accountState.account().id(),
              user != null ? user.getLoggableName() : currentUser.get().getLoggableName()));
    }

    if (!email.equals(accountState.account().preferredEmail())) {
      // the email is a secondary email of the account

      if (user != null) {
        if (user.hasEmailAddress(email)) {
          return OptionalResultWithMessages.create(
              true,
              String.format(
                  "email %s is visible to user %s: email is a secondary email that is owned by this"
                      + " user",
                  email, user.getLoggableName()));
        }
      } else if (currentUser.get().isIdentifiedUser()
          && currentUser.get().asIdentifiedUser().hasEmailAddress(email)) {
        // it's a secondary email of the calling user, users can always see their own secondary
        // emails
        return OptionalResultWithMessages.create(
            true,
            String.format(
                "email %s is visible to the calling user %s: email is a secondary email that is"
                    + " owned by this user",
                email, currentUser.get().getLoggableName()));
      }

      // the email is a secondary email of another account, check if the user can see secondary
      // emails
      try {
        if (user != null) {
          if (!permissionBackend.user(user).test(GlobalPermission.MODIFY_ACCOUNT)) {
            return OptionalResultWithMessages.create(
                false,
                String.format(
                    "cannot resolve code owner email %s: account %s is referenced by secondary email"
                        + " but user %s cannot see secondary emails",
                    email, accountState.account().id(), user.getLoggableName()));
          }
          return OptionalResultWithMessages.create(
              true,
              String.format(
                  "resolved code owner email %s: account %s is referenced by secondary email"
                      + " and user %s can see secondary emails",
                  email, accountState.account().id(), user.getLoggableName()));
        } else if (!permissionBackend.currentUser().test(GlobalPermission.MODIFY_ACCOUNT)) {
          return OptionalResultWithMessages.create(
              false,
              String.format(
                  "cannot resolve code owner email %s: account %s is referenced by secondary email"
                      + " but the calling user %s cannot see secondary emails",
                  email, accountState.account().id(), currentUser.get().getLoggableName()));
        } else {
          return OptionalResultWithMessages.create(
              true,
              String.format(
                  "resolved code owner email %s: account %s is referenced by secondary email"
                      + " and the calling user %s can see secondary emails",
                  email, accountState.account().id(), currentUser.get().getLoggableName()));
        }
      } catch (PermissionBackendException e) {
        throw new CodeOwnersInternalServerErrorException(
            String.format(
                "failed to test the %s global capability", GlobalPermission.MODIFY_ACCOUNT),
            e);
      }
    }
    return OptionalResultWithMessages.create(
        true,
        String.format(
            "account %s is visible to user %s",
            accountState.account().id(),
            user != null ? user.getLoggableName() : currentUser.get().getLoggableName()));
  }

  /**
   * Whether the domain of the given email is allowed for code owners.
   *
   * @param email the email for which the domain should be checked
   * @return {@code true} if the domain of the given email is allowed for code owners, otherwise
   *     {@code false}
   */
  public OptionalResultWithMessages<Boolean> isEmailDomainAllowed(String email) {
    requireNonNull(email, "email");

    ImmutableSet<String> allowedEmailDomains =
        codeOwnersPluginConfiguration.getAllowedEmailDomains();
    if (allowedEmailDomains.isEmpty()) {
      return OptionalResultWithMessages.create(true, "all domains are allowed");
    }

    if (email.equals(ALL_USERS_WILDCARD)) {
      return OptionalResultWithMessages.create(true, "all users wildcard is allowed");
    }

    int emailAtIndex = email.lastIndexOf('@');
    if (emailAtIndex >= 0 && emailAtIndex < email.length() - 1) {
      String emailDomain = email.substring(emailAtIndex + 1);
      boolean isEmailDomainAllowed = allowedEmailDomains.contains(emailDomain);
      return OptionalResultWithMessages.create(
          isEmailDomainAllowed,
          String.format(
              "domain %s of email %s is %s",
              emailDomain, email, isEmailDomainAllowed ? "allowed" : "not allowed"));
    }

    return OptionalResultWithMessages.create(false, String.format("email %s has no domain", email));
  }
}
