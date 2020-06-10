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

package com.google.gerrit.plugins.codeowners.acceptance.api;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoIterableSubject.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.client.ListAccountsOption;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerInfo;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.List;
import org.junit.Test;

/**
 * Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnersForPathInBranch} REST endpoint.
 *
 * <p>Further tests for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnersForPathInBranch} REST endpoint that
 * require using the REST API are implemented in {@link
 * com.google.gerrit.plugins.codeowners.acceptance.restapi.GetCodeOwnersForPathInBranchRestIT}.
 */
public class GetCodeOwnersForPathInBranchIT extends AbstractCodeOwnersIT {
  @Inject @ServerInitiated private Provider<AccountsUpdate> accountsUpdate;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private GroupOperations groupOperations;

  @Test
  public void getCodeOwnersWhenNoCodeOwnerConfigsExist() throws Exception {
    assertThat(codeOwnersApiFactory.branch(project, "master").query().get("/foo/bar/baz.md"))
        .isEmpty();
  }

  @Test
  public void getCodeOwnersUnrelatedCodeOwnerConfigHasNoEffect() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/abc/def/")
        .addCodeOwnerEmail(admin.email())
        .addCodeOwnerEmail(user.email())
        .create();
    assertThat(codeOwnersApiFactory.branch(project, "master").query().get("/foo/bar/baz.md"))
        .isEmpty();
  }

  @Test
  public void getCodeOwnersForAbsolutePath() throws Exception {
    testGetCodeOwners(true);
  }

  @Test
  public void getCodeOwnersForNonAbsolutePath() throws Exception {
    testGetCodeOwners(false);
  }

  private void testGetCodeOwners(boolean useAbsolutePath) throws Exception {
    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(user2.email())
        .create();

    List<CodeOwnerInfo> codeOwnerInfos =
        codeOwnersApiFactory
            .branch(project, "master")
            .query()
            .get(useAbsolutePath ? "/foo/bar/baz.md" : "foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .hasAccountIdsThat()
        .containsExactly(user2.id(), user.id(), admin.id())
        .inOrder();
    assertThat(codeOwnerInfos).hasAccountNamesThat().containsExactly(null, null, null);
  }

  @Test
  public void getCodeOwnersWithAccountDetails() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(admin.email())
        .create();

    List<CodeOwnerInfo> codeOwnerInfos =
        codeOwnersApiFactory
            .branch(project, "master")
            .query()
            .withOptions(ListAccountsOption.DETAILS)
            .get("/foo/bar/baz.md");
    assertThat(codeOwnerInfos).hasAccountIdsThat().containsExactly(admin.id());
    assertThat(codeOwnerInfos).hasAccountNamesThat().containsExactly(admin.fullName());
  }

  @Test
  public void getCodeOwnersWithSecondaryEmails() throws Exception {
    // create a code owner config
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(admin.email())
        .create();

    // add secondary email to admin account
    String secondaryEmail = "admin@foo.bar";
    accountsUpdate
        .get()
        .update(
            "Add secondary email to admin test account",
            admin.id(),
            (a, u) -> u.addExternalId(ExternalId.createEmail(admin.id(), secondaryEmail)));

    // Make the request with the admin user that has the 'Modify Account' global capability.
    List<CodeOwnerInfo> codeOwnerInfos =
        codeOwnersApiFactory
            .branch(project, "master")
            .query()
            .withOptions(ListAccountsOption.ALL_EMAILS)
            .get("/foo/bar/baz.md");
    assertThat(codeOwnerInfos).hasAccountIdsThat().containsExactly(admin.id());
    assertThat(Iterables.getOnlyElement(codeOwnerInfos).account.secondaryEmails)
        .containsExactly(secondaryEmail);
  }

  @Test
  public void cannotGetCodeOwnersWithSecondaryEmailsWithoutModifyAccountCapability()
      throws Exception {
    // create a code owner config
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(admin.email())
        .create();

    // add secondary email to admin account
    String secondaryEmail = "admin@foo.bar";
    accountsUpdate
        .get()
        .update(
            "Add secondary email to admin test account",
            admin.id(),
            (a, u) -> u.addExternalId(ExternalId.createEmail(admin.id(), secondaryEmail)));

    // Make the request with a user that doesn't have the 'Modify Account' global capability.
    requestScopeOperations.setApiUser(user.id());

    AuthException exception =
        assertThrows(
            AuthException.class,
            () ->
                codeOwnersApiFactory
                    .branch(project, "master")
                    .query()
                    .withOptions(ListAccountsOption.ALL_EMAILS)
                    .get("/foo/bar/baz.md"));
    assertThat(exception).hasMessageThat().isEqualTo("modify account not permitted");
  }

  @Test
  public void getCodeOwnersWithMultipleAccountOptions() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(admin.email())
        .create();

    // add secondary email to admin account
    String secondaryEmail = "admin@foo.bar";
    accountsUpdate
        .get()
        .update(
            "Add secondary email to admin test account",
            admin.id(),
            (a, u) -> u.addExternalId(ExternalId.createEmail(admin.id(), secondaryEmail)));

    // Make the request with the admin user that has the 'Modify Account' global capability.
    List<CodeOwnerInfo> codeOwnerInfos =
        codeOwnersApiFactory
            .branch(project, "master")
            .query()
            .withOptions(ListAccountsOption.DETAILS, ListAccountsOption.ALL_EMAILS)
            .get("/foo/bar/baz.md");
    assertThat(codeOwnerInfos).hasAccountIdsThat().containsExactly(admin.id());
    assertThat(codeOwnerInfos).hasAccountNamesThat().containsExactly(admin.fullName());
    assertThat(Iterables.getOnlyElement(codeOwnerInfos).account.secondaryEmails)
        .containsExactly(secondaryEmail);
  }

  @Test
  public void accountOptionCannotBeNull() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> codeOwnersApiFactory.branch(project, "master").query().withOptions(null));
    assertThat(npe).hasMessageThat().isEqualTo("option");
  }

  @Test
  public void furtherAccountOptionsCannotBeNull() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwnersApiFactory
                    .branch(project, "master")
                    .query()
                    .withOptions(ListAccountsOption.DETAILS, (ListAccountsOption[]) null));
    assertThat(npe).hasMessageThat().isEqualTo("furtherOptions");
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void nonVisibleCodeOwnersAreFilteredOut() throws Exception {
    // Create 2 accounts that share a group.
    TestAccount user2 = accountCreator.user2();
    TestAccount user3 = accountCreator.create("user3", "user3@example.com", "User3", null);
    groupOperations.newGroup().addMember(user2.id()).addMember(user3.id()).create();

    // Create some code owner configs.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .addCodeOwnerEmail(user3.email())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(user2.email())
        .create();

    // Make the request as admin who can see all accounts.
    assertThat(codeOwnersApiFactory.branch(project, "master").query().get("/foo/bar/baz.md"))
        .hasAccountIdsThat()
        .containsExactly(admin.id(), user.id(), user2.id(), user3.id());

    // Make the request as user2. This user only shares a group with user3. Since
    // "accounts.visibility" is set to "SAME_GROUP" user2 can only see user3's account (besides
    // the own account).
    requestScopeOperations.setApiUser(user2.id());

    // We expect only user2 and user3 as code owner (user and admin should be filtered
    // out because user2 cannot see their accounts).
    assertThat(codeOwnersApiFactory.branch(project, "master").query().get("/foo/bar/baz.md"))
        .hasAccountIdsThat()
        .containsExactly(user2.id(), user3.id());
  }

  @Test
  public void getCodeOwnersReferencedBySecondaryEmails() throws Exception {
    // add secondary email to user account
    String secondaryEmail = "user@foo.bar";
    accountsUpdate
        .get()
        .update(
            "Add secondary email to user test account",
            user.id(),
            (a, u) -> u.addExternalId(ExternalId.createEmail(user.id(), secondaryEmail)));

    // create a code owner config
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(secondaryEmail)
        .create();

    // admin has the "Modify Account" global capability and hence can see secondary emails
    assertThat(codeOwnersApiFactory.branch(project, "master").query().get("/foo/bar/baz.md"))
        .hasAccountIdsThat()
        .containsExactly(user.id());

    // user can see the own secondary email
    requestScopeOperations.setApiUser(user.id());
    assertThat(codeOwnersApiFactory.branch(project, "master").query().get("/foo/bar/baz.md"))
        .hasAccountIdsThat()
        .containsExactly(user.id());

    // user2 doesn't have the 'Modify Account' global capability and hence cannot see the secondary
    // email
    TestAccount user2 = accountCreator.user2();
    requestScopeOperations.setApiUser(user2.id());
    assertThat(codeOwnersApiFactory.branch(project, "master").query().get("/foo/bar/baz.md"))
        .isEmpty();
  }

  @Test
  public void getCodeOwnersOrderNotDefinedIfCodeOwnersHaveTheSameScoring() throws Exception {
    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .addCodeOwnerEmail(user2.email())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    List<CodeOwnerInfo> codeOwnerInfos =
        codeOwnersApiFactory.branch(project, "master").query().get("/foo/bar.md");
    assertThat(codeOwnerInfos)
        .hasAccountIdsThat()
        .containsExactly(admin.id(), user.id(), user2.id());

    // The first code owner in the result should be user as user has the best distance score.
    // The other 2 code owners come in a random order, but verifying this is a test is hard, hence
    // there is no assertion for this.
    assertThat(Iterables.getFirst(codeOwnerInfos, null)).hasAccountIdThat().isEqualTo(user.id());
  }
}
