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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Class to resolve {@link CodeOwnerReference}s to {@link CodeOwner}s.
 *
 * <p>Code owners are defined by {@link CodeOwnerReference}s (e.g. emails) that need to be resolved
 * to accounts. The accounts are wrapped in {@link CodeOwner}s so that we can support different kind
 * of code owners later (e.g. groups).
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
 *       was meant to have the ownership. This behaviour is consistent with the behaviour in Gerrit
 *       core that also treats ambiguous identifiers as non-resolveable.
 * </ul>
 *
 * <p>Unless {@link CodeOwnerResolver#enforceVisibility} is {@code false} it is checked whether the
 * {@link #user} or the calling user (if {@link #user} is unset) can see the accounts of the code
 * owners and code owners whose accounts are not visible are filtered out.
 *
 * <p>In addition code owners that are referenced by a secondary email are filtered out if the
 * {@link #user} or the calling user (if {@link #user} is unset) cannot see the secondary email:
 *
 * <ul>
 *   <li>every user can see their own secondary emails
 *   <li>users with the {@code Modify Account} global capability can see the secondary emails of all
 *       accounts
 * </ul>
 *
 * <p>Resolved code owners are cached within this class so that each email needs to be resolved only
 * once. To take advantage of this caching callers should reuse {@link CodeOwnerResolver} instances
 * where possible.
 */
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
  private final TransientCodeOwnerCache transientCodeOwnerCache;

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
      UnresolvedImportFormatter unresolvedImportFormatter,
      TransientCodeOwnerCache transientCodeOwnerCache) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.permissionBackend = permissionBackend;
    this.currentUser = currentUser;
    this.externalIds = externalIds;
    this.accountCache = accountCache;
    this.accountControlFactory = accountControlFactory;
    this.pathCodeOwnersFactory = pathCodeOwnersFactory;
    this.codeOwnerMetrics = codeOwnerMetrics;
    this.unresolvedImportFormatter = unresolvedImportFormatter;
    this.transientCodeOwnerCache = transientCodeOwnerCache;
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
    transientCodeOwnerCache.clear();
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
    transientCodeOwnerCache.clear();
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
          pathCodeOwnersResult.get().getAnnotations(),
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
   */
  public CodeOwnerResolverResult resolve(Set<CodeOwnerReference> codeOwnerReferences) {
    return resolve(
        codeOwnerReferences,
        /* annotations= */ ImmutableMultimap.of(),
        /* unresolvedImports= */ ImmutableList.of(),
        /* pathCodeOwnersMessages= */ ImmutableList.of());
  }

  /**
   * Resolves the given {@link CodeOwnerReference}s to {@link CodeOwner}s.
   *
   * <p>The accounts for the given {@link CodeOwnerReference}s are loaded from the account cache in
   * parallel (via {@link AccountCache#get(Set)}.
   *
   * @param codeOwnerReferences the code owner references that should be resolved
   * @param annotationsByCodeOwnerReference annotations by code owner reference
   * @param unresolvedImports list of unresolved imports
   * @param pathCodeOwnersMessages messages that were collected when resolving path code owners
   * @return the resolved code owner references as a {@link CodeOwnerResolverResult}
   */
  private CodeOwnerResolverResult resolve(
      Set<CodeOwnerReference> codeOwnerReferences,
      ImmutableMultimap<CodeOwnerReference, CodeOwnerAnnotation> annotationsByCodeOwnerReference,
      List<UnresolvedImport> unresolvedImports,
      ImmutableList<String> pathCodeOwnersMessages) {
    requireNonNull(codeOwnerReferences, "codeOwnerReferences");
    requireNonNull(unresolvedImports, "unresolvedImports");
    requireNonNull(pathCodeOwnersMessages, "pathCodeOwnersMessages");

    try (Timer0.Context ctx = codeOwnerMetrics.resolveCodeOwnerReferences.start()) {
      ImmutableList.Builder<String> messageBuilder = ImmutableList.builder();
      messageBuilder.addAll(pathCodeOwnersMessages);
      unresolvedImports.forEach(
          unresolvedImport ->
              messageBuilder.add(unresolvedImportFormatter.format(unresolvedImport)));

      AtomicBoolean ownedByAllUsers = new AtomicBoolean(false);
      AtomicBoolean hasUnresolvedCodeOwners = new AtomicBoolean(false);
      ImmutableMap<CodeOwner, ImmutableSet<CodeOwnerAnnotation>> codeOwnersWithAnnotations =
          resolve(
              messageBuilder,
              ownedByAllUsers,
              hasUnresolvedCodeOwners,
              codeOwnerReferences,
              annotationsByCodeOwnerReference);

      ImmutableMultimap.Builder<CodeOwner, CodeOwnerAnnotation> annotationsByCodeOwner =
          ImmutableMultimap.builder();
      codeOwnersWithAnnotations.forEach(
          (codeOwner, annotations) -> annotationsByCodeOwner.putAll(codeOwner, annotations));

      CodeOwnerResolverResult codeOwnerResolverResult =
          CodeOwnerResolverResult.create(
              codeOwnersWithAnnotations.keySet(),
              annotationsByCodeOwner.build(),
              ownedByAllUsers.get(),
              hasUnresolvedCodeOwners.get(),
              !unresolvedImports.isEmpty(),
              messageBuilder.build());
      logger.atFine().log("resolve result = %s", codeOwnerResolverResult);
      return codeOwnerResolverResult;
    }
  }

  /**
   * Resolves a {@link CodeOwnerReference} to a {@link CodeOwner}.
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

  /**
   * Resolves a {@link CodeOwnerReference} to a {@link CodeOwner}.
   *
   * <p>This method does not resolve {@link CodeOwnerReference}s that assign the code ownership to
   * all user by using {@link #ALL_USERS_WILDCARD} as email.
   *
   * <p>Debug messages are returned with the result.
   *
   * @param codeOwnerReference the code owner reference that should be resolved
   * @return the result of resolving the given code owner reference with debug messages
   */
  public OptionalResultWithMessages<CodeOwner> resolveWithMessages(
      CodeOwnerReference codeOwnerReference) {
    requireNonNull(codeOwnerReference, "codeOwnerReference");

    if (CodeOwnerResolver.ALL_USERS_WILDCARD.equals(codeOwnerReference.email())) {
      return OptionalResultWithMessages.createEmpty(
          String.format(
              "cannot resolve code owner email %s: no account with this email exists",
              CodeOwnerResolver.ALL_USERS_WILDCARD));
    }

    ImmutableList.Builder<String> messageBuilder = ImmutableList.builder();
    AtomicBoolean ownedByAllUsers = new AtomicBoolean(false);
    AtomicBoolean hasUnresolvedCodeOwners = new AtomicBoolean(false);
    ImmutableMap<CodeOwner, ImmutableSet<CodeOwnerAnnotation>> codeOwnersWithAnnotations =
        resolve(
            messageBuilder,
            ownedByAllUsers,
            hasUnresolvedCodeOwners,
            ImmutableSet.of(codeOwnerReference),
            /* annotations= */ ImmutableMultimap.of());
    ImmutableList<String> messages = messageBuilder.build();
    if (codeOwnersWithAnnotations.isEmpty()) {
      return OptionalResultWithMessages.createEmpty(messages);
    }
    return OptionalResultWithMessages.create(
        Iterables.getOnlyElement(codeOwnersWithAnnotations.keySet()), messages);
  }

  /**
   * Resolves the given {@link CodeOwnerReference}s to {@link CodeOwner}s.
   *
   * <p>The accounts for the given {@link CodeOwnerReference}s are loaded from the account cache in
   * parallel (via {@link AccountCache#get(Set)}.
   *
   * @param messages a builder to which debug messages are added
   * @param ownedByAllUsers a flag that is set if any of the given {@link CodeOwnerReference}s
   *     assigns code ownership to all users
   * @param hasUnresolvedCodeOwners a flag that is set any of the given {@link CodeOwnerReference}s
   *     cannot be resolved
   * @param codeOwnerReferences the code owner references that should be resolved
   * @param annotations annotations by code owner reference
   * @return map that maps the resolved {@link CodeOwner}s to their annotations (note: we cannot
   *     return a {@code Multimap<CodeOwner, CodeOwnerAnnotation>} here since there may be code
   *     owners without annotations and Multimap doesn't store keys for which no values are stored)
   */
  private ImmutableMap<CodeOwner, ImmutableSet<CodeOwnerAnnotation>> resolve(
      ImmutableList.Builder<String> messages,
      AtomicBoolean ownedByAllUsers,
      AtomicBoolean hasUnresolvedCodeOwners,
      Set<CodeOwnerReference> codeOwnerReferences,
      ImmutableMultimap<CodeOwnerReference, CodeOwnerAnnotation> annotations) {
    requireNonNull(codeOwnerReferences, "codeOwnerReferences");

    ImmutableSet<String> emailsToResolve =
        codeOwnerReferences.stream()
            .map(CodeOwnerReference::email)
            .filter(filterOutAllUsersWildCard(ownedByAllUsers))
            .collect(toImmutableSet());

    ImmutableMap<String, Optional<CodeOwner>> cachedCodeOwnersByEmail =
        transientCodeOwnerCache.get(emailsToResolve);

    ImmutableSet<String> emailsToLookup =
        emailsToResolve.stream()
            .filter(email -> !cachedCodeOwnersByEmail.containsKey(email))
            .filter(filterOutEmailsWithNonAllowedDomains(messages))
            .collect(toImmutableSet());

    ImmutableMap<String, Collection<ExternalId>> externalIdsByEmail =
        lookupExternalIds(messages, emailsToLookup);

    Stream<Pair<String, AccountState>> accountsByEmail =
        lookupAccounts(messages, externalIdsByEmail)
            .map(removeInactiveAccounts(messages))
            .filter(filterOutEmailsWithoutAccounts(messages))
            .filter(filterOutAmbiguousEmails(messages))
            .map(mapToOnlyAccount(messages));

    if (enforceVisibility) {
      accountsByEmail =
          accountsByEmail
              .filter(filterOutEmailsOfNonVisibleAccounts(messages))
              .filter(filterOutNonVisibleSecondaryEmails(messages));
    } else {
      messages.add("code owner visibility is not checked");
    }

    ImmutableMap<String, CodeOwner> codeOwnersByEmail =
        accountsByEmail.map(mapToCodeOwner()).collect(toImmutableMap(Pair::key, Pair::value));

    if (codeOwnersByEmail.keySet().size() < emailsToResolve.size()) {
      hasUnresolvedCodeOwners.set(true);
    }

    Map<CodeOwner, Set<CodeOwnerAnnotation>> codeOwnersWithAnnotations = new HashMap<>();

    // Merge code owners that have been newly resolved with code owners which have been looked up
    // from cache and return them with their annotations.
    Stream<Pair<String, CodeOwner>> newlyResolvedCodeOwnersStream =
        codeOwnersByEmail.entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue()));
    Stream<Pair<String, CodeOwner>> cachedCodeOwnersStream =
        cachedCodeOwnersByEmail.entrySet().stream()
            .filter(e -> e.getValue().isPresent())
            .map(e -> Pair.of(e.getKey(), e.getValue().get()));
    Streams.concat(newlyResolvedCodeOwnersStream, cachedCodeOwnersStream)
        .forEach(
            p -> {
              ImmutableSet.Builder<CodeOwnerAnnotation> annotationBuilder = ImmutableSet.builder();

              annotationBuilder.addAll(annotations.get(CodeOwnerReference.create(p.key())));

              // annotations for the all users wildcard (aka '*') apply to all code owners
              annotationBuilder.addAll(
                  annotations.get(CodeOwnerReference.create(ALL_USERS_WILDCARD)));

              if (!codeOwnersWithAnnotations.containsKey(p.value())) {
                codeOwnersWithAnnotations.put(p.value(), new HashSet<>());
              }
              codeOwnersWithAnnotations.get(p.value()).addAll(annotationBuilder.build());
            });

    return codeOwnersWithAnnotations.entrySet().stream()
        .collect(toImmutableMap(Map.Entry::getKey, e -> ImmutableSet.copyOf(e.getValue())));
  }

  /**
   * Creates a predicate to filter out emails that are all users wild card (aka {@code *}).
   *
   * @param ownedByAllUsers flag that is set if any of the emails is the all users wild card (aka
   *     {@code *})
   */
  private Predicate<String> filterOutAllUsersWildCard(AtomicBoolean ownedByAllUsers) {
    return email -> {
      if (ALL_USERS_WILDCARD.equals(email)) {
        ownedByAllUsers.set(true);
        return false;
      }
      return true;
    };
  }

  /**
   * Creates a predicate to filter out emails that have a non-allowed email domain.
   *
   * <p>Which emails domains are allowed is controlled via the plugin configuration (see {@link
   * com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginGlobalConfigSnapshot#getAllowedEmailDomains()}
   *
   * @param messages builder to which debug messages are added
   */
  private Predicate<String> filterOutEmailsWithNonAllowedDomains(
      ImmutableList.Builder<String> messages) {
    return email -> {
      boolean isEmailDomainAllowed = isEmailDomainAllowed(messages, email);
      if (!isEmailDomainAllowed) {
        transientCodeOwnerCache.cacheNonResolvable(email);
      }
      return isEmailDomainAllowed;
    };
  }

  /**
   * Whether the domain of the given email is allowed for code owners.
   *
   * <p>Which emails domains are allowed is controlled via the plugin configuration (see {@link
   * com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginGlobalConfigSnapshot#getAllowedEmailDomains()}
   *
   * <p>Debug messages are returned with the result.
   *
   * @param email the email for which the domain should be checked
   * @return a {@link OptionalResultWithMessages} that contains {@code true} if the domain of the
   *     given email is allowed for code owners, otherwise {@link OptionalResultWithMessages} that
   *     contains {@code false}
   */
  public OptionalResultWithMessages<Boolean> isEmailDomainAllowed(String email) {
    ImmutableList.Builder<String> messages = ImmutableList.builder();
    boolean isEmailDomainAllowed = isEmailDomainAllowed(messages, email);
    return OptionalResultWithMessages.create(isEmailDomainAllowed, messages.build());
  }

  /**
   * Whether the domain of the given email is allowed for code owners.
   *
   * <p>Which emails domains are allowed is controlled via the plugin configuration (see {@link
   * com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginGlobalConfigSnapshot#getAllowedEmailDomains()}
   *
   * @param messages builder to which debug messages are added
   * @param email the email for which the domain should be checked
   * @return {@code true} if the domain of the given email is allowed for code owners, otherwise
   *     {@code false}
   */
  private boolean isEmailDomainAllowed(ImmutableList.Builder<String> messages, String email) {
    requireNonNull(messages, "messages");
    requireNonNull(email, "email");

    ImmutableSet<String> allowedEmailDomains =
        codeOwnersPluginConfiguration.getGlobalConfig().getAllowedEmailDomains();
    if (allowedEmailDomains.isEmpty()) {
      messages.add("all domains are allowed");
      return true;
    }

    if (email.equals(ALL_USERS_WILDCARD)) {
      messages.add("all users wildcard is allowed");
      return true;
    }

    int emailAtIndex = email.lastIndexOf('@');
    if (emailAtIndex >= 0 && emailAtIndex < email.length() - 1) {
      String emailDomain = email.substring(emailAtIndex + 1);
      boolean isEmailDomainAllowed = allowedEmailDomains.contains(emailDomain);
      messages.add(
          String.format(
              "domain %s of email %s is %s",
              emailDomain, email, isEmailDomainAllowed ? "allowed" : "not allowed"));
      return isEmailDomainAllowed;
    }

    messages.add(String.format("email %s has no domain", email));
    return false;
  }

  /**
   * Looks up the external IDs for the given emails.
   *
   * <p>Looks up all emails from the external ID cache at once, which is more efficient than looking
   * up external IDs for emails one by one (see {@link ExternalIds#byEmails(String...)}).
   *
   * @param messages builder to which debug messages are added
   * @param emails the emails for which the external IDs should be looked up
   * @return external IDs per email
   */
  private ImmutableMap<String, Collection<ExternalId>> lookupExternalIds(
      ImmutableList.Builder<String> messages, ImmutableSet<String> emails) {
    try {
      ImmutableMap<String, Collection<ExternalId>> extIdsByEmail =
          externalIds.byEmails(emails.toArray(new String[0])).asMap();
      emails.stream()
          .filter(email -> !extIdsByEmail.containsKey(email))
          .forEach(
              email -> {
                transientCodeOwnerCache.cacheNonResolvable(email);
                messages.add(
                    String.format(
                        "cannot resolve code owner email %s: no account with this email exists",
                        email));
              });
      return extIdsByEmail;
    } catch (IOException e) {
      throw new CodeOwnersInternalServerErrorException(
          String.format("cannot resolve code owner emails: %s", emails), e);
    }
  }

  /**
   * Looks up the accounts for the given external IDs.
   *
   * <p>Looks up all accounts from the account cache at once, which is more efficient than looking
   * up accounts one by one (see {@link AccountCache#get(Set)}).
   *
   * @param messages builder to which debug messages are added
   * @param externalIdsByEmail external IDs for which the accounts should be looked up
   * @return account states per email
   */
  private Stream<Pair<String, Collection<AccountState>>> lookupAccounts(
      ImmutableList.Builder<String> messages,
      ImmutableMap<String, Collection<ExternalId>> externalIdsByEmail) {
    ImmutableSet<Account.Id> accountIds =
        externalIdsByEmail.values().stream()
            .flatMap(Collection::stream)
            .map(ExternalId::accountId)
            .collect(toImmutableSet());
    Map<Account.Id, AccountState> accounts = accountCache.get(accountIds);
    return externalIdsByEmail.entrySet().stream()
        .map(
            e ->
                Pair.of(
                    e.getKey(),
                    e.getValue().stream()
                        .map(
                            extId -> {
                              Account.Id accountId = extId.accountId();
                              AccountState accountState = accounts.get(accountId);
                              if (accountState == null) {
                                messages.add(
                                    String.format(
                                        "cannot resolve account %s for email %s: account does not"
                                            + " exists",
                                        accountId, e.getKey()));
                              }
                              return accountState;
                            })
                        .filter(Objects::nonNull)
                        .collect(toImmutableSet())));
  }

  /**
   * Creates a map function that removes inactive accounts from a {@code Pair<String,
   * Collection<AccountState>>}.
   *
   * <p>The pair which is provided as input to the function maps an email to a collection of account
   * states.
   *
   * @param messages builder to which debug messages are added
   */
  private Function<Pair<String, Collection<AccountState>>, Pair<String, Collection<AccountState>>>
      removeInactiveAccounts(ImmutableList.Builder<String> messages) {
    return e -> Pair.of(e.key(), removeInactiveAccounts(messages, e.key(), e.value()));
  }

  /**
   * Removes inactive accounts from the given collection of account states.
   *
   * @param messages builder to which debug messages are added
   * @param email email to which the accounts belong
   * @param accountStates the set of account states from which inactive accounts should be removed
   * @return the account states that belong to active accounts
   */
  private ImmutableSet<AccountState> removeInactiveAccounts(
      ImmutableList.Builder<String> messages,
      String email,
      Collection<AccountState> accountStates) {
    return accountStates.stream()
        .filter(
            accountState -> {
              if (!accountState.account().isActive()) {
                messages.add(
                    String.format(
                        "ignoring inactive account %s for email %s",
                        accountState.account().id(), email));
                return false;
              }
              return true;
            })
        .collect(toImmutableSet());
  }

  /**
   * Creates a predicate to filter out emails without accounts.
   *
   * <p>The pair which is provided as input to the predicate maps an email to a collection of
   * account states. If the collection of account states is empty, the email is filtered out.
   *
   * @param messages builder to which debug messages are added
   */
  private Predicate<Pair<String, Collection<AccountState>>> filterOutEmailsWithoutAccounts(
      ImmutableList.Builder<String> messages) {
    return e -> {
      if (e.value().isEmpty()) {
        String email = e.key();
        transientCodeOwnerCache.cacheNonResolvable(email);
        messages.add(
            String.format(
                "cannot resolve code owner email %s: no active account with this email found",
                email));
        return false;
      }
      return true;
    };
  }

  /**
   * Creates a predicate to filter out ambiguous emails (emails that belong to multiple accounts).
   *
   * <p>The pair which is provided as input to the predicate maps an email to a collection of
   * account states. If the collection of account states contains more than 1 entry, the email is
   * filtered out.
   *
   * @param messages builder to which debug messages are added
   */
  private Predicate<Pair<String, Collection<AccountState>>> filterOutAmbiguousEmails(
      ImmutableList.Builder<String> messages) {
    return e -> {
      if (e.value().size() > 1) {
        String email = e.key();
        transientCodeOwnerCache.cacheNonResolvable(email);
        messages.add(
            String.format("cannot resolve code owner email %s: email is ambiguous", email));
        return false;
      }
      return true;
    };
  }

  /**
   * Creates a map function that maps a {@code Pair<String, Collection<AccountState>>} to a {@code
   * Pair<String, AccountState>}.
   *
   * <p>The pair which is provided as input to the function maps an email to a collection of account
   * states, which must contain exactly one entry. As output the function returns a pair that maps
   * the email to the only account state.
   *
   * @param messages builder to which debug messages are added
   */
  private Function<Pair<String, Collection<AccountState>>, Pair<String, AccountState>>
      mapToOnlyAccount(ImmutableList.Builder<String> messages) {
    return e -> {
      String email = e.key();
      AccountState accountState = Iterables.getOnlyElement(e.value());
      messages.add(
          String.format("resolved email %s to account %s", email, accountState.account().id()));
      return Pair.of(email, accountState);
    };
  }

  /**
   * Creates a predicate to filter out emails that belong to non-visible accounts.
   *
   * @param messages builder to which debug messages are added
   */
  private Predicate<Pair<String, AccountState>> filterOutEmailsOfNonVisibleAccounts(
      ImmutableList.Builder<String> messages) {
    return e -> {
      String email = e.key();
      AccountState accountState = e.value();
      if (!canSee(accountState)) {
        transientCodeOwnerCache.cacheNonResolvable(email);
        messages.add(
            String.format(
                "cannot resolve code owner email %s: account %s is not visible to user %s",
                email,
                accountState.account().id(),
                user != null ? user.getLoggableName() : currentUser.get().getLoggableName()));
        return false;
      }

      return true;
    };
  }

  /** Whether the given account can be seen. */
  private boolean canSee(AccountState accountState) {
    AccountControl accountControl =
        user != null ? accountControlFactory.get(user) : accountControlFactory.get();
    return accountControl.canSee(accountState);
  }

  /**
   * Creates a predicate to filter out non-visible secondary emails.
   *
   * <p>A secondary email is only visible if
   *
   * <ul>
   *   <li>it is owned by the {@link #user} or the calling user (if {@link #user} is unset)
   *   <li>if the {@link #user} or the calling user (if {@link #user} is unset) has the {@code
   *       Modify Account} global capability
   * </ul>
   *
   * @param messages builder to which debug messages are added
   */
  private Predicate<Pair<String, AccountState>> filterOutNonVisibleSecondaryEmails(
      ImmutableList.Builder<String> messages) {
    return e -> {
      String email = e.key();
      AccountState accountState = e.value();
      if (email.equals(accountState.account().preferredEmail())) {
        // the email is a primary email of the account
        messages.add(
            String.format(
                "account %s is visible to user %s",
                accountState.account().id(),
                user != null ? user.getLoggableName() : currentUser.get().getLoggableName()));
        return true;
      }

      if (user != null) {
        if (user.hasEmailAddress(email)) {
          messages.add(
              String.format(
                  "email %s is visible to user %s: email is a secondary email that is owned by this"
                      + " user",
                  email, user.getLoggableName()));
          return true;
        }
      } else if (currentUser.get().isIdentifiedUser()
          && currentUser.get().asIdentifiedUser().hasEmailAddress(email)) {
        // it's a secondary email of the calling user, users can always see their own secondary
        // emails
        messages.add(
            String.format(
                "email %s is visible to the calling user %s: email is a secondary email that is"
                    + " owned by this user",
                email, currentUser.get().getLoggableName()));
        return true;
      }

      // the email is a secondary email of another account, check if the user can see secondary
      // emails
      try {
        if (user != null) {
          if (!permissionBackend.user(user).test(GlobalPermission.MODIFY_ACCOUNT)) {
            transientCodeOwnerCache.cacheNonResolvable(email);
            messages.add(
                String.format(
                    "cannot resolve code owner email %s: account %s is referenced by secondary email"
                        + " but user %s cannot see secondary emails",
                    email, accountState.account().id(), user.getLoggableName()));
            return false;
          }
          messages.add(
              String.format(
                  "resolved code owner email %s: account %s is referenced by secondary email"
                      + " and user %s can see secondary emails",
                  email, accountState.account().id(), user.getLoggableName()));
          return true;
        } else if (!permissionBackend.currentUser().test(GlobalPermission.MODIFY_ACCOUNT)) {
          transientCodeOwnerCache.cacheNonResolvable(email);
          messages.add(
              String.format(
                  "cannot resolve code owner email %s: account %s is referenced by secondary email"
                      + " but the calling user %s cannot see secondary emails",
                  email, accountState.account().id(), currentUser.get().getLoggableName()));
          return false;
        } else {
          messages.add(
              String.format(
                  "resolved code owner email %s: account %s is referenced by secondary email"
                      + " and the calling user %s can see secondary emails",
                  email, accountState.account().id(), currentUser.get().getLoggableName()));
          return true;
        }
      } catch (PermissionBackendException ex) {
        throw new CodeOwnersInternalServerErrorException(
            String.format(
                "failed to test the %s global capability", GlobalPermission.MODIFY_ACCOUNT),
            ex);
      }
    };
  }

  /**
   * Creates a map function that maps a {@code Pair<String, AccountState>} to a code owner.
   *
   * <p>The pair which is provided as input to the function maps an email to an account states.
   */
  private Function<Pair<String, AccountState>, Pair<String, CodeOwner>> mapToCodeOwner() {
    return e -> {
      String email = e.key();
      CodeOwner codeOwner = CodeOwner.create(e.value().account().id());
      transientCodeOwnerCache.cache(email, codeOwner);
      return Pair.of(email, codeOwner);
    };
  }

  /** Returns the counters for resolutions and cache reads of code owners. */
  public TransientCodeOwnerCache.Counters getCodeOwnerCounters() {
    return transientCodeOwnerCache.getCounters();
  }
}
