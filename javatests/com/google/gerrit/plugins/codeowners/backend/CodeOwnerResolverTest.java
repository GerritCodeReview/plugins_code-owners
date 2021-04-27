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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerSubject.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.OptionalResultWithMessagesSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestMetricMaker;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provider;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerResolver}. */
public class CodeOwnerResolverTest extends AbstractCodeOwnersTest {
  private static final ObjectId TEST_REVISION =
      ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject @ServerInitiated private Provider<AccountsUpdate> accountsUpdate;
  @Inject private AccountOperations accountOperations;
  @Inject private ExternalIdNotes.Factory externalIdNotesFactory;
  @Inject private TestMetricMaker testMetricMaker;

  private Provider<CodeOwnerResolver> codeOwnerResolverProvider;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerResolverProvider =
        plugin.getSysInjector().getInstance(new Key<Provider<CodeOwnerResolver>>() {});
  }

  @Test
  public void cannotResolveNullToCodeOwner() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwnerResolverProvider
                    .get()
                    .resolve(/* codeOwnerReference= */ (CodeOwnerReference) null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerReference");

    npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwnerResolverProvider
                    .get()
                    .resolve(/* codeOwnerReferences= */ (Set<CodeOwnerReference>) null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerReferences");
  }

  @Test
  public void resolveCodeOwnerReferenceForNonExistingEmail() throws Exception {
    String nonExistingEmail = "non-existing@example.com";
    OptionalResultWithMessages<CodeOwner> result =
        codeOwnerResolverProvider
            .get()
            .resolveWithMessages(CodeOwnerReference.create(nonExistingEmail));
    assertThat(result).isEmpty();
    assertThat(result)
        .hasMessagesThat()
        .contains(
            String.format(
                "cannot resolve code owner email %s: no account with this email exists",
                nonExistingEmail));
  }

  @Test
  public void resolveCodeOwnerReferenceForEmail() throws Exception {
    OptionalResultWithMessages<CodeOwner> result =
        codeOwnerResolverProvider
            .get()
            .resolveWithMessages(CodeOwnerReference.create(admin.email()));
    assertThat(result.get()).hasAccountIdThat().isEqualTo(admin.id());
    assertThat(result)
        .hasMessagesThat()
        .contains(String.format("account %s is visible to user %s", admin.id(), admin.username()));
  }

  @Test
  public void cannotResolveCodeOwnerReferenceForStarAsEmail() throws Exception {
    OptionalResultWithMessages<CodeOwner> result =
        codeOwnerResolverProvider
            .get()
            .resolveWithMessages(CodeOwnerReference.create(CodeOwnerResolver.ALL_USERS_WILDCARD));
    assertThat(result).isEmpty();
    assertThat(result)
        .hasMessagesThat()
        .contains(
            String.format(
                "cannot resolve code owner email %s: no account with this email exists",
                CodeOwnerResolver.ALL_USERS_WILDCARD));
  }

  @Test
  public void resolveCodeOwnerReferenceForAmbiguousEmailIfOtherAccountIsInactive()
      throws Exception {
    // Create an external ID for 'user' account that has the same email as the 'admin' account.
    accountsUpdate
        .get()
        .update(
            "Test update",
            user.id(),
            (a, u) ->
                u.addExternalId(
                    ExternalId.create(
                        "foo", "bar", user.id(), admin.email(), /* hashedPassword= */ null)));

    // Deactivate the 'user' account.
    accountOperations.account(user.id()).forUpdate().inactive().update();

    OptionalResultWithMessages<CodeOwner> result =
        codeOwnerResolverProvider
            .get()
            .resolveWithMessages(CodeOwnerReference.create(admin.email()));
    assertThat(result.get()).hasAccountIdThat().isEqualTo(admin.id());
  }

  @Test
  public void resolveCodeOwnerReferenceForAmbiguousEmail() throws Exception {
    // Create an external ID for 'user' account that has the same email as the 'admin' account.
    accountsUpdate
        .get()
        .update(
            "Test update",
            user.id(),
            (a, u) ->
                u.addExternalId(
                    ExternalId.create(
                        "foo", "bar", user.id(), admin.email(), /* hashedPassword= */ null)));

    OptionalResultWithMessages<CodeOwner> result =
        codeOwnerResolverProvider
            .get()
            .resolveWithMessages(CodeOwnerReference.create(admin.email()));
    assertThat(result).isEmpty();
    assertThat(result)
        .hasMessagesThat()
        .contains(
            String.format("cannot resolve code owner email %s: email is ambiguous", admin.email()));
  }

  @Test
  public void resolveCodeOwnerReferenceForOrphanedEmail() throws Exception {
    // Create an external ID with an email for a non-existing account.
    String email = "foo.bar@example.com";
    Account.Id accountId = Account.id(999999);
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.upsert(ExternalId.createEmail(accountId, email));
      extIdNotes.commit(md);
    }

    OptionalResultWithMessages<CodeOwner> result =
        codeOwnerResolverProvider.get().resolveWithMessages(CodeOwnerReference.create(email));
    assertThat(result).isEmpty();
    assertThat(result)
        .hasMessagesThat()
        .containsAnyOf(
            String.format(
                "cannot resolve account %s for email %s: account does not exists",
                accountId, email),
            String.format(
                "cannost resolve code owner email %s: no active account with this email found",
                email));
  }

  @Test
  public void resolveCodeOwnerReferenceForInactiveUser() throws Exception {
    accountOperations.account(user.id()).forUpdate().inactive().update();
    OptionalResultWithMessages<CodeOwner> result =
        codeOwnerResolverProvider
            .get()
            .resolveWithMessages(CodeOwnerReference.create(user.email()));
    assertThat(result).isEmpty();
    assertThat(result)
        .hasMessagesThat()
        .contains(
            String.format("ignoring inactive account %s for email %s", user.id(), user.email()));
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void resolveCodeOwnerReferenceForNonVisibleAccount() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // Set user2 as current user.
    requestScopeOperations.setApiUser(user2.id());

    // user2 cannot see the admin account since they do not share any group and
    // "accounts.visibility" is set to "SAME_GROUP".
    OptionalResultWithMessages<CodeOwner> result =
        codeOwnerResolverProvider
            .get()
            .resolveWithMessages(CodeOwnerReference.create(admin.email()));
    assertThat(result).isEmpty();
    assertThat(result)
        .hasMessagesThat()
        .contains(
            String.format(
                "cannot resolve code owner email %s: account %s is not visible to user %s",
                admin.email(), admin.id(), user2.username()));
  }

  @Test
  public void resolveCodeOwnerReferenceForSecondaryEmail() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // add secondary email to user account
    String secondaryEmail = "user@foo.bar";
    accountOperations.account(user.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    // admin has the "Modify Account" global capability and hence can see the secondary email of the
    // user account.
    OptionalResultWithMessages<CodeOwner> result =
        codeOwnerResolverProvider
            .get()
            .resolveWithMessages(CodeOwnerReference.create(secondaryEmail));
    assertThat(result.get()).hasAccountIdThat().isEqualTo(user.id());
    assertThat(result)
        .hasMessagesThat()
        .contains(
            String.format(
                "resolved code owner email %s: account %s is referenced by secondary email and the calling user %s can see secondary emails",
                secondaryEmail, user.id(), admin.username()));

    // admin has the "Modify Account" global capability and hence can see the secondary email of the
    // user account if another user is the calling user
    requestScopeOperations.setApiUser(user2.id());
    result =
        codeOwnerResolverProvider
            .get()
            .forUser(identifiedUserFactory.create(admin.id()))
            .resolveWithMessages(CodeOwnerReference.create(secondaryEmail));
    assertThat(result.get()).hasAccountIdThat().isEqualTo(user.id());
    assertThat(result)
        .hasMessagesThat()
        .contains(
            String.format(
                "resolved code owner email %s: account %s is referenced by secondary email and user %s can see secondary emails",
                secondaryEmail, user.id(), admin.username()));

    // user can see its own secondary email.
    requestScopeOperations.setApiUser(user.id());
    result =
        codeOwnerResolverProvider
            .get()
            .resolveWithMessages(CodeOwnerReference.create(secondaryEmail));
    assertThat(result.get()).hasAccountIdThat().isEqualTo(user.id());
    assertThat(result)
        .hasMessagesThat()
        .contains(
            String.format(
                "email %s is visible to the calling user %s: email is a secondary email that is owned by this user",
                secondaryEmail, user.username()));

    // user can see its own secondary email if another user is the calling user.
    requestScopeOperations.setApiUser(user2.id());
    result =
        codeOwnerResolverProvider
            .get()
            .forUser(identifiedUserFactory.create(user.id()))
            .resolveWithMessages(CodeOwnerReference.create(secondaryEmail));
    assertThat(result.get()).hasAccountIdThat().isEqualTo(user.id());
    assertThat(result)
        .hasMessagesThat()
        .contains(
            String.format(
                "email %s is visible to user %s: email is a secondary email that is owned by this user",
                secondaryEmail, user.username()));
  }

  @Test
  public void resolveCodeOwnerReferenceForNonVisibleSecondaryEmail() throws Exception {
    // add secondary email to admin account
    String secondaryEmail = "admin@foo.bar";
    accountOperations.account(admin.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    // user doesn't have the "Modify Account" global capability and hence cannot see the secondary
    // email of the admin account.
    requestScopeOperations.setApiUser(user.id());
    OptionalResultWithMessages<CodeOwner> result =
        codeOwnerResolverProvider
            .get()
            .resolveWithMessages(CodeOwnerReference.create(secondaryEmail));
    assertThat(result).isEmpty();
    assertThat(result)
        .hasMessagesThat()
        .contains(
            String.format(
                "cannot resolve code owner email %s: account %s is referenced by secondary email but the calling user %s cannot see secondary emails",
                secondaryEmail, admin.id(), user.username()));

    // user doesn't have the "Modify Account" global capability and hence cannot see the secondary
    // email of the admin account if another user is the calling user
    requestScopeOperations.setApiUser(admin.id());
    result =
        codeOwnerResolverProvider
            .get()
            .forUser(identifiedUserFactory.create(user.id()))
            .resolveWithMessages(CodeOwnerReference.create(secondaryEmail));
    assertThat(result).isEmpty();
    assertThat(result)
        .hasMessagesThat()
        .contains(
            String.format(
                "cannot resolve code owner email %s: account %s is referenced by secondary email but user %s cannot see secondary emails",
                secondaryEmail, admin.id(), user.username()));
  }

  @Test
  public void resolvePathCodeOwnersForEmptyCodeOwnerConfig() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"), TEST_REVISION)
            .build();
    CodeOwnerResolverResult result =
        codeOwnerResolverProvider
            .get()
            .resolvePathCodeOwners(codeOwnerConfig, Paths.get("/README.md"));
    assertThat(result.codeOwners()).isEmpty();
    assertThat(result.ownedByAllUsers()).isFalse();
    assertThat(result.hasUnresolvedCodeOwners()).isFalse();
  }

  @Test
  public void resolvePathCodeOwners() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"), TEST_REVISION)
            .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email(), user.email()))
            .build();

    CodeOwnerResolverResult result =
        codeOwnerResolverProvider
            .get()
            .resolvePathCodeOwners(codeOwnerConfig, Paths.get("/README.md"));
    assertThat(result.codeOwnersAccountIds()).containsExactly(admin.id(), user.id());
    assertThat(result.ownedByAllUsers()).isFalse();
    assertThat(result.hasUnresolvedCodeOwners()).isFalse();
  }

  @Test
  public void resolvePathCodeOwnersWhenStarIsUsedAsEmail() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"), TEST_REVISION)
            .addCodeOwnerSet(
                CodeOwnerSet.createWithoutPathExpressions(CodeOwnerResolver.ALL_USERS_WILDCARD))
            .build();

    CodeOwnerResolverResult result =
        codeOwnerResolverProvider
            .get()
            .resolvePathCodeOwners(codeOwnerConfig, Paths.get("/README.md"));
    assertThat(result.codeOwnersAccountIds()).isEmpty();
    assertThat(result.ownedByAllUsers()).isTrue();
    assertThat(result.hasUnresolvedCodeOwners()).isFalse();
  }

  @Test
  public void resolvePathCodeOwnersNonResolvableCodeOwnersAreFilteredOut() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"), TEST_REVISION)
            .addCodeOwnerSet(
                CodeOwnerSet.createWithoutPathExpressions(
                    admin.email(), "non-existing@example.com"))
            .build();
    CodeOwnerResolverResult result =
        codeOwnerResolverProvider
            .get()
            .resolvePathCodeOwners(codeOwnerConfig, Paths.get("/README.md"));
    assertThat(result.codeOwnersAccountIds()).containsExactly(admin.id());
    assertThat(result.ownedByAllUsers()).isFalse();
    assertThat(result.hasUnresolvedCodeOwners()).isTrue();
  }

  @Test
  public void resolvePathCodeOwnersNonResolvableCodeOwnersAreFilteredOutIfOwnedByAllUsers()
      throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"), TEST_REVISION)
            .addCodeOwnerSet(
                CodeOwnerSet.createWithoutPathExpressions(
                    "*", admin.email(), "non-existing@example.com"))
            .build();
    CodeOwnerResolverResult result =
        codeOwnerResolverProvider
            .get()
            .resolvePathCodeOwners(codeOwnerConfig, Paths.get("/README.md"));
    assertThat(result.codeOwnersAccountIds()).containsExactly(admin.id());
    assertThat(result.ownedByAllUsers()).isTrue();
    assertThat(result.hasUnresolvedCodeOwners()).isTrue();
  }

  @Test
  public void cannotResolvePathCodeOwnersOfNullCodeOwnerConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwnerResolverProvider
                    .get()
                    .resolvePathCodeOwners(/* codeOwnerConfig= */ null, Paths.get("/README.md")));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfig");
  }

  @Test
  public void cannotResolvePathCodeOwnersForNullPath() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"), TEST_REVISION)
            .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
            .build();
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwnerResolverProvider
                    .get()
                    .resolvePathCodeOwners(codeOwnerConfig, /* absolutePath= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("absolutePath");
  }

  @Test
  public void cannotResolvePathCodeOwnersOfNullPathCodeOwners() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwnerResolverProvider.get().resolvePathCodeOwners(/* pathCodeOwners= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pathCodeOwners");
  }

  @Test
  public void cannotResolvePathCodeOwnersForRelativePath() throws Exception {
    String relativePath = "foo/bar.md";
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"), TEST_REVISION)
            .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
            .build();
    IllegalStateException npe =
        assertThrows(
            IllegalStateException.class,
            () ->
                codeOwnerResolverProvider
                    .get()
                    .resolvePathCodeOwners(codeOwnerConfig, Paths.get(relativePath)));
    assertThat(npe)
        .hasMessageThat()
        .isEqualTo(String.format("path %s must be absolute", relativePath));
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void nonVisibleCodeOwnerCanBeResolvedIfVisibilityIsNotEnforced() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // Set user2 as current user.
    requestScopeOperations.setApiUser(user2.id());

    CodeOwnerReference adminCodeOwnerReference = CodeOwnerReference.create(admin.email());

    // user2 cannot see the admin account since they do not share any group and
    // "accounts.visibility" is set to "SAME_GROUP".
    assertThat(codeOwnerResolverProvider.get().resolve(adminCodeOwnerReference)).isEmpty();

    // if visibility is not enforced the code owner reference can be resolved regardless
    Optional<CodeOwner> codeOwner =
        codeOwnerResolverProvider.get().enforceVisibility(false).resolve(adminCodeOwnerReference);
    assertThat(codeOwner).value().hasAccountIdThat().isEqualTo(admin.id());
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void codeOwnerVisibilityIsCheckedForGivenAccount() throws Exception {
    // Create a new user that is not a member of any group. This means 'user' and 'admin' are not
    // visible to this user since they do not share any group.
    TestAccount user2 = accountCreator.user2();

    // admin is the current user and can see the account
    assertThat(codeOwnerResolverProvider.get().resolve(CodeOwnerReference.create(user.email())))
        .isPresent();
    assertThat(
            codeOwnerResolverProvider
                .get()
                .forUser(identifiedUserFactory.create(admin.id()))
                .resolve(CodeOwnerReference.create(user.email())))
        .isPresent();

    // user2 cannot see the account
    assertThat(
            codeOwnerResolverProvider
                .get()
                .forUser(identifiedUserFactory.create(user2.id()))
                .resolve(CodeOwnerReference.create(user.email())))
        .isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.allowedEmailDomain", value = "example.net")
  public void resolveCodeOwnerReferenceForEmailWithNonAllowedEmailDomain() throws Exception {
    assertThat(
            codeOwnerResolverProvider.get().resolve(CodeOwnerReference.create("foo@example.com")))
        .isEmpty();
  }

  @Test
  public void isEmailDomainAllowedRequiresEmailToBeNonNull() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> codeOwnerResolverProvider.get().isEmailDomainAllowed(/* email= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("email");
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.allowedEmailDomain",
      values = {"example.com", "example.net"})
  public void configuredEmailDomainsAreAllowed() throws Exception {
    assertIsEmailDomainAllowed(
        "foo@example.com", true, "domain example.com of email foo@example.com is allowed");
    assertIsEmailDomainAllowed(
        "foo@example.net", true, "domain example.net of email foo@example.net is allowed");
    assertIsEmailDomainAllowed(
        "foo@example.org@example.com",
        true,
        "domain example.com of email foo@example.org@example.com is allowed");
    assertIsEmailDomainAllowed(
        "foo@example.org", false, "domain example.org of email foo@example.org is not allowed");
    assertIsEmailDomainAllowed("foo", false, "email foo has no domain");
    assertIsEmailDomainAllowed(
        "foo@example.com@example.org",
        false,
        "domain example.org of email foo@example.com@example.org is not allowed");
    assertIsEmailDomainAllowed(
        CodeOwnerResolver.ALL_USERS_WILDCARD, true, "all users wildcard is allowed");
  }

  @Test
  public void allEmailDomainsAreAllowed() throws Exception {
    String expectedMessage = "all domains are allowed";
    assertIsEmailDomainAllowed("foo@example.com", true, expectedMessage);
    assertIsEmailDomainAllowed("foo@example.net", true, expectedMessage);
    assertIsEmailDomainAllowed("foo@example.org@example.com", true, expectedMessage);
    assertIsEmailDomainAllowed("foo@example.org", true, expectedMessage);
    assertIsEmailDomainAllowed("foo", true, expectedMessage);
    assertIsEmailDomainAllowed("foo@example.com@example.org", true, expectedMessage);
    assertIsEmailDomainAllowed(CodeOwnerResolver.ALL_USERS_WILDCARD, true, expectedMessage);
  }

  private void assertIsEmailDomainAllowed(
      String email, boolean expectedResult, String expectedMessage) {
    OptionalResultWithMessages<Boolean> isEmailDomainAllowedResult =
        codeOwnerResolverProvider.get().isEmailDomainAllowed(email);
    assertThat(isEmailDomainAllowedResult.get()).isEqualTo(expectedResult);
    assertThat(isEmailDomainAllowedResult.messages()).containsExactly(expectedMessage);
  }

  @Test
  public void resolveCodeOwnerReferences() throws Exception {
    CodeOwnerResolverResult result =
        codeOwnerResolverProvider
            .get()
            .resolve(
                ImmutableSet.of(
                    CodeOwnerReference.create(admin.email()),
                    CodeOwnerReference.create(user.email())));
    assertThat(result.codeOwnersAccountIds()).containsExactly(admin.id(), user.id());
    assertThat(result.ownedByAllUsers()).isFalse();
    assertThat(result.hasUnresolvedCodeOwners()).isFalse();
  }

  @Test
  public void resolveCodeOwnerReferencesNonResolveableCodeOwnersAreFilteredOut() throws Exception {
    CodeOwnerResolverResult result =
        codeOwnerResolverProvider
            .get()
            .resolve(
                ImmutableSet.of(
                    CodeOwnerReference.create(admin.email()),
                    CodeOwnerReference.create("non-existing@example.com")));
    assertThat(result.codeOwnersAccountIds()).containsExactly(admin.id());
    assertThat(result.ownedByAllUsers()).isFalse();
    assertThat(result.hasUnresolvedCodeOwners()).isTrue();
  }

  @Test
  public void isResolvable() throws Exception {
    assertThat(
            codeOwnerResolverProvider.get().isResolvable(CodeOwnerReference.create(admin.email())))
        .isTrue();
  }

  @Test
  public void isNotResolvable() throws Exception {
    assertThat(
            codeOwnerResolverProvider
                .get()
                .isResolvable(CodeOwnerReference.create("unknown@example.com")))
        .isFalse();
  }

  @Test
  public void emailIsResolvedOnlyOnce() throws Exception {
    testMetricMaker.reset();
    CodeOwnerResolver codeOwnerResolver = codeOwnerResolverProvider.get();
    OptionalResultWithMessages<CodeOwner> result =
        codeOwnerResolver.resolveWithMessages(CodeOwnerReference.create(admin.email()));
    assertThat(result.get()).hasAccountIdThat().isEqualTo(admin.id());
    assertThat(testMetricMaker.getCount("plugins/code-owners/count_code_owner_resolutions"))
        .isEqualTo(1);
    assertThat(testMetricMaker.getCount("plugins/code-owners/count_code_owner_config_reads"))
        .isEqualTo(0);

    // Doing the same lookup again doesn't resolve the code owner again.
    testMetricMaker.reset();
    result = codeOwnerResolver.resolveWithMessages(CodeOwnerReference.create(admin.email()));
    assertThat(result.get()).hasAccountIdThat().isEqualTo(admin.id());
    assertThat(testMetricMaker.getCount("plugins/code-owners/count_code_owner_resolutions"))
        .isEqualTo(0);
    assertThat(testMetricMaker.getCount("plugins/code-owners/count_code_owner_config_reads"))
        .isEqualTo(1);
  }

  @Test
  public void nonExistingEmailIsResolvedOnlyOnce() throws Exception {
    testMetricMaker.reset();
    CodeOwnerResolver codeOwnerResolver = codeOwnerResolverProvider.get();
    OptionalResultWithMessages<CodeOwner> result =
        codeOwnerResolver.resolveWithMessages(
            CodeOwnerReference.create("non-existing@example.com"));
    assertThat(result).isEmpty();
    assertThat(testMetricMaker.getCount("plugins/code-owners/count_code_owner_resolutions"))
        .isEqualTo(1);
    assertThat(testMetricMaker.getCount("plugins/code-owners/count_code_owner_config_reads"))
        .isEqualTo(0);

    // Doing the same lookup again doesn't resolve the code owner again.
    testMetricMaker.reset();
    result =
        codeOwnerResolver.resolveWithMessages(
            CodeOwnerReference.create("non-existing@example.com"));
    assertThat(result).isEmpty();
    assertThat(testMetricMaker.getCount("plugins/code-owners/count_code_owner_resolutions"))
        .isEqualTo(0);
    assertThat(testMetricMaker.getCount("plugins/code-owners/count_code_owner_config_reads"))
        .isEqualTo(1);
  }
}
