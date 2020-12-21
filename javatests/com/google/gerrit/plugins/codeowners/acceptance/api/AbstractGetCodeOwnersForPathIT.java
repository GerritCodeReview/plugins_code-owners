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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoSubject.assertThatList;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoSubject.hasAccountId;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoSubject.hasAccountName;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.client.ListAccountsOption;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.TestCodeOwnerConfigCreation;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.TestPathExpressions;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwners;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnersForPathInBranch;
import com.google.inject.Inject;
import java.util.List;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;

/**
 * Base class of acceptance tests for REST endpoints that extend {@link
 * com.google.gerrit.plugins.codeowners.restapi.AbstractGetCodeOwnersForPath}.
 */
public abstract class AbstractGetCodeOwnersForPathIT extends AbstractCodeOwnersIT {
  /**
   * List of all file paths that are used by the tests. Subclasses can use this list to create these
   * files in the test setup in case they test functionality that requires the files to exist.
   */
  protected static final ImmutableList<String> TEST_PATHS =
      ImmutableList.of(
          "/foo/bar.md", "/foo/bar/baz.md", "/foo/bar/config.txt", "/foo/bar/main.config");

  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private AccountOperations accountOperations;
  @Inject private GroupOperations groupOperations;
  @Inject private ProjectOperations projectOperations;

  private TestPathExpressions testPathExpressions;

  @Before
  public void setup() throws Exception {
    testPathExpressions = plugin.getSysInjector().getInstance(TestPathExpressions.class);
  }

  /** Must return the {@link CodeOwners} API against which the tests should be run. */
  protected abstract CodeOwners getCodeOwnersApi() throws RestApiException;

  protected List<CodeOwnerInfo> queryCodeOwners(String path) throws RestApiException {
    return queryCodeOwners(getCodeOwnersApi().query(), path);
  }

  protected List<CodeOwnerInfo> queryCodeOwners(CodeOwners.QueryRequest queryRequest, String path)
      throws RestApiException {
    return queryRequest.get(path);
  }

  @Test
  public void getCodeOwnersWhenNoCodeOwnerConfigsExist() throws Exception {
    assertThat(queryCodeOwners("/foo/bar/baz.md")).isEmpty();
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
    assertThat(queryCodeOwners("/foo/bar/baz.md")).isEmpty();
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
        queryCodeOwners(useAbsolutePath ? "/foo/bar/baz.md" : "foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user2.id(), user.id(), admin.id())
        .inOrder();
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountName())
        .containsExactly(null, null, null);
  }

  @Test
  public void getCodeOwnersWithIgnoreParentCodeOwners() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // Create some code owner configs.
    // The order below reflects the order in which the code owner configs are evaluated.

    // 1. code owner config that makes "user2" a code owner, inheriting code owners from parent code
    // owner configs is enabled by default
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(user2.email())
        .create();

    // 2. code owner config that makes "user" a code owner, code owners from parent code owner
    // configs are ignored
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .ignoreParentCodeOwners()
        .addCodeOwnerEmail(user.email())
        .create();

    // 3. code owner config that makes "admin" a code owner, but for this test this code owner
    // config is ignored, since the 2. code owner config ignores code owners from parent code owner
    // configs
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    // Assert the code owners for "/foo/bar/baz.md". This evaluates the code owner configs in the
    // order: 1., 2., 3.
    // The 3. code owner config is ignored since the 2. code owner config has set
    // 'ignoreParentCodeOwners=true'. Hence the expected code owners are only the users that are
    // code owner according to the 1. and 2. code owner config: user2 + user
    List<CodeOwnerInfo> codeOwnerInfos = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user2.id(), user.id())
        .inOrder();
  }

  @Test
  public void getPerFileCodeOwners() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression(testPathExpressions.matchFileTypeInCurrentFolder("txt"))
                .addCodeOwnerEmail(admin.email())
                .build())
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression(testPathExpressions.matchFileTypeInCurrentFolder("md"))
                .addCodeOwnerEmail(user.email())
                .build())
        .create();

    assertThat(queryCodeOwners("/foo/bar/config.txt"))
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id());
    assertThat(queryCodeOwners("/foo/bar/baz.md"))
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id());
    assertThat(queryCodeOwners("/foo/bar/main.config")).isEmpty();
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
        queryCodeOwners(
            getCodeOwnersApi().query().withOptions(ListAccountsOption.DETAILS), "/foo/bar/baz.md");
    assertThat(codeOwnerInfos).comparingElementsUsing(hasAccountId()).containsExactly(admin.id());
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountName())
        .containsExactly(admin.fullName());
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
    accountOperations.account(admin.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    // Make the request with the admin user that has the 'Modify Account' global capability.
    List<CodeOwnerInfo> codeOwnerInfos =
        queryCodeOwners(
            getCodeOwnersApi().query().withOptions(ListAccountsOption.ALL_EMAILS),
            "/foo/bar/baz.md");
    assertThat(codeOwnerInfos).comparingElementsUsing(hasAccountId()).containsExactly(admin.id());
    assertThatList(codeOwnerInfos)
        .onlyElement()
        .hasSecondaryEmailsThat()
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
    accountOperations.account(admin.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    // Make the request with a user that doesn't have the 'Modify Account' global capability.
    requestScopeOperations.setApiUser(user.id());

    AuthException exception =
        assertThrows(
            AuthException.class,
            () ->
                queryCodeOwners(
                    getCodeOwnersApi().query().withOptions(ListAccountsOption.ALL_EMAILS),
                    "/foo/bar/baz.md"));
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
    accountOperations.account(admin.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    // Make the request with the admin user that has the 'Modify Account' global capability.
    List<CodeOwnerInfo> codeOwnerInfos =
        queryCodeOwners(
            getCodeOwnersApi()
                .query()
                .withOptions(ListAccountsOption.DETAILS, ListAccountsOption.ALL_EMAILS),
            "/foo/bar/baz.md");
    assertThat(codeOwnerInfos).comparingElementsUsing(hasAccountId()).containsExactly(admin.id());
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountName())
        .containsExactly(admin.fullName());
    assertThatList(codeOwnerInfos)
        .onlyElement()
        .hasSecondaryEmailsThat()
        .containsExactly(secondaryEmail);
  }

  @Test
  public void accountOptionCannotBeNull() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> getCodeOwnersApi().query().withOptions(null));
    assertThat(npe).hasMessageThat().isEqualTo("option");
  }

  @Test
  public void furtherAccountOptionsCannotBeNull() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                getCodeOwnersApi()
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
    assertThat(queryCodeOwners("/foo/bar/baz.md"))
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id(), user3.id());

    // Make the request as user2. This user only shares a group with user3. Since
    // "accounts.visibility" is set to "SAME_GROUP" user2 can only see user3's account (besides
    // the own account).
    requestScopeOperations.setApiUser(user2.id());

    // We expect only user2 and user3 as code owner (user and admin should be filtered
    // out because user2 cannot see their accounts).
    assertThat(queryCodeOwners("/foo/bar/baz.md"))
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user2.id(), user3.id());
  }

  @Test
  public void codeOwnersThatCannotSeeTheBranchAreFilteredOut() throws Exception {
    // Create a code owner config with 2 code owners.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .addCodeOwnerEmail(user.email())
        .create();

    // Check that both code owners are suggested.
    assertThat(queryCodeOwners("/foo/bar/baz.md"))
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id());

    // Block read access for 'user'.
    AccountGroup.UUID blockedGroupUuid = groupOperations.newGroup().addMember(user.id()).create();
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/heads/master").group(blockedGroupUuid))
        .update();

    // Expect that 'user' is filtered out now.
    assertThat(queryCodeOwners("/foo/bar/baz.md"))
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id());
  }

  @Test
  public void getCodeOwnersReferencedBySecondaryEmails() throws Exception {
    // add secondary email to user account
    String secondaryEmail = "user@foo.bar";
    accountOperations.account(user.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    // create a code owner config
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(secondaryEmail)
        .create();

    // admin has the "Modify Account" global capability and hence can see secondary emails
    assertThat(queryCodeOwners("/foo/bar/baz.md"))
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id());

    // user can see the own secondary email
    requestScopeOperations.setApiUser(user.id());
    assertThat(queryCodeOwners("/foo/bar/baz.md"))
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id());

    // user2 doesn't have the 'Modify Account' global capability and hence cannot see the secondary
    // email
    TestAccount user2 = accountCreator.user2();
    requestScopeOperations.setApiUser(user2.id());
    assertThat(queryCodeOwners("/foo/bar/baz.md")).isEmpty();
  }

  @Test
  public void getCodeOwnersOrderNotDefinedIfCodeOwnersHaveTheSameScoring() throws Exception {
    TestAccount user2 = accountCreator.user2();
    TestAccount user3 = accountCreator.create("user3", "user3@example.com", "User3", null);
    TestAccount user4 = accountCreator.create("user4", "user4@example.com", "User4", null);

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .addCodeOwnerEmail(user2.email())
        .addCodeOwnerEmail(user3.email())
        .addCodeOwnerEmail(user4.email())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    List<CodeOwnerInfo> codeOwnerInfos = queryCodeOwners("/foo/bar.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id(), user3.id(), user4.id());

    // The first code owner in the result should be user as user has the best distance score.
    assertThatList(codeOwnerInfos).element(0).hasAccountIdThat().isEqualTo(user.id());

    // The order of the other code owners is random since they have the same score.
    // Check that the order of the code owners with the same score is different for further requests
    // at least once.
    List<Account.Id> accountIdsInRetrievedOrder1 =
        codeOwnerInfos.stream().map(info -> Account.id(info.account._accountId)).collect(toList());
    boolean foundOtherOrder = false;
    for (int i = 0; i < 10; i++) {
      codeOwnerInfos = queryCodeOwners("/foo/bar.md");
      List<Account.Id> accountIdsInRetrievedOrder2 =
          codeOwnerInfos.stream()
              .map(info -> Account.id(info.account._accountId))
              .collect(toList());
      if (!accountIdsInRetrievedOrder1.equals(accountIdsInRetrievedOrder2)) {
        foundOtherOrder = true;
        break;
      }
    }
    if (!foundOtherOrder) {
      fail(
          String.format(
              "expected different order, but order was always %s", accountIdsInRetrievedOrder1));
    }
  }

  @Test
  public void getCodeOwnersIsLimitedByDefault() throws Exception {
    // Create a code owner config that has more code owners than the number of code owners which are
    // returned by default.
    TestCodeOwnerConfigCreation.Builder codeOwnerConfigCreation =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/");
    for (int i = 1; i <= GetCodeOwnersForPathInBranch.DEFAULT_LIMIT + 1; i++) {
      TestAccount user =
          accountCreator.create("foo" + i, "foo" + i + "@example.com", "Foo " + i, null);
      codeOwnerConfigCreation.addCodeOwnerEmail(user.email());
    }
    codeOwnerConfigCreation.create();

    // Assert that the result is limited by the default limit.
    List<CodeOwnerInfo> codeOwnerInfos = queryCodeOwners("/foo/bar.md");
    assertThat(codeOwnerInfos).hasSize(GetCodeOwnersForPathInBranch.DEFAULT_LIMIT);
  }

  @Test
  public void getCodeOwnersWithLimit() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create some code owner configs
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
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(user.email())
        .addCodeOwnerEmail(user2.email())
        .create();

    // get code owners with different limits
    List<CodeOwnerInfo> codeOwnerInfos =
        queryCodeOwners(getCodeOwnersApi().query().withLimit(1), "/foo/bar/baz.md");
    assertThat(codeOwnerInfos).hasSize(1);
    // the first 2 code owners have the same scoring, so their order is random and we don't know
    // which of them we get when the limit is 1
    assertThatList(codeOwnerInfos).element(0).hasAccountIdThat().isAnyOf(user.id(), user2.id());

    codeOwnerInfos = queryCodeOwners(getCodeOwnersApi().query().withLimit(2), "/foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), user2.id());

    codeOwnerInfos = getCodeOwnersApi().query().withLimit(3).get("/foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id());
  }

  @Test
  public void cannotGetCodeOwnersWithoutLimit() throws Exception {
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> queryCodeOwners(getCodeOwnersApi().query().withLimit(0), "/foo/bar/baz.md"));
    assertThat(exception).hasMessageThat().isEqualTo("limit must be positive");
  }

  @Test
  public void limitCannotBeNegative() throws Exception {
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> queryCodeOwners(getCodeOwnersApi().query().withLimit(-1), "/foo/bar/baz.md"));
    assertThat(exception).hasMessageThat().isEqualTo("limit must be positive");
  }

  @Test
  public void getCodeOwnersIfCodeOwnersFunctionalityIsDisabled() throws Exception {
    disableCodeOwnersForProject(project);

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    List<CodeOwnerInfo> codeOwnerInfos = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnerInfos).comparingElementsUsing(hasAccountId()).containsExactly(admin.id());
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "global.owner@example.com")
  public void getGlobalCodeOwners() throws Exception {
    TestAccount globalOwner =
        accountCreator.create("global_owner", "global.owner@example.com", "Global Owner", null);
    assertThat(queryCodeOwners("/foo/bar/baz.md"))
        .comparingElementsUsing(hasAccountId())
        .containsExactly(globalOwner.id());
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.globalCodeOwner",
      values = {"global.owner1@example.com", "global.owner2@example.com"})
  public void getWithGlobalCodeOwnersAndLimit() throws Exception {
    TestAccount globalOwner1 =
        accountCreator.create(
            "global_owner_1", "global.owner1@example.com", "Global Owner 1", null);
    TestAccount globalOwner2 =
        accountCreator.create(
            "global_owner_2", "global.owner2@example.com", "Global Owner 2", null);

    TestAccount user2 = accountCreator.user2();

    // create some code owner configs
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
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(user.email())
        .addCodeOwnerEmail(user2.email())
        .create();

    // get code owners with different limits
    List<CodeOwnerInfo> codeOwnerInfos =
        queryCodeOwners(getCodeOwnersApi().query().withLimit(1), "/foo/bar/baz.md");
    assertThat(codeOwnerInfos).hasSize(1);
    // the first 2 code owners have the same scoring, so their order is random and we don't know
    // which of them we get when the limit is 1
    assertThatList(codeOwnerInfos).element(0).hasAccountIdThat().isAnyOf(user.id(), user2.id());

    codeOwnerInfos = queryCodeOwners(getCodeOwnersApi().query().withLimit(2), "/foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), user2.id());

    codeOwnerInfos = getCodeOwnersApi().query().withLimit(3).get("/foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id());

    codeOwnerInfos = getCodeOwnersApi().query().withLimit(4).get("/foo/bar/baz.md");
    assertThat(codeOwnerInfos).hasSize(4);
    // the order of the first 2 code owners is random
    assertThatList(codeOwnerInfos).element(0).hasAccountIdThat().isAnyOf(user.id(), user2.id());
    assertThatList(codeOwnerInfos).element(1).hasAccountIdThat().isAnyOf(user.id(), user2.id());
    assertThatList(codeOwnerInfos).element(2).hasAccountIdThat().isEqualTo(admin.id());
    // the order of the global code owners is random
    assertThatList(codeOwnerInfos)
        .element(3)
        .hasAccountIdThat()
        .isAnyOf(globalOwner1.id(), globalOwner2.id());

    codeOwnerInfos = getCodeOwnersApi().query().withLimit(5).get("/foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id(), globalOwner1.id(), globalOwner2.id());
  }

  @Test
  public void getDefaultCodeOwners() throws Exception {
    // Create default code owner config file in refs/meta/config.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(RefNames.REFS_CONFIG)
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();

    assertThat(queryCodeOwners("/foo/bar/baz.md"))
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id());
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.globalCodeOwner",
      values = {"global.owner1@example.com", "global.owner2@example.com"})
  public void getWithDefaultAndGlobalCodeOwnersAndLimit() throws Exception {
    TestAccount globalOwner1 =
        accountCreator.create(
            "global_owner_1", "global.owner1@example.com", "Global Owner 1", null);
    TestAccount globalOwner2 =
        accountCreator.create(
            "global_owner_2", "global.owner2@example.com", "Global Owner 2", null);

    TestAccount user2 = accountCreator.user2();
    TestAccount defaultCodeOwner1 =
        accountCreator.create("user3", "user3@example.com", "User3", null);
    TestAccount defaultCodeOwner2 =
        accountCreator.create("user4", "user4@example.com", "User4", null);

    // Create default code owner config file in refs/meta/config.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(RefNames.REFS_CONFIG)
        .folderPath("/")
        .addCodeOwnerEmail(defaultCodeOwner1.email())
        .addCodeOwnerEmail(defaultCodeOwner2.email())
        .create();

    // create some code owner configs
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
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(user.email())
        .addCodeOwnerEmail(user2.email())
        .create();

    // get code owners with different limits
    List<CodeOwnerInfo> codeOwnerInfos =
        queryCodeOwners(getCodeOwnersApi().query().withLimit(1), "/foo/bar/baz.md");
    assertThat(codeOwnerInfos).hasSize(1);
    // the first 2 code owners have the same scoring, so their order is random and we don't know
    // which of them we get when the limit is 1
    assertThatList(codeOwnerInfos).element(0).hasAccountIdThat().isAnyOf(user.id(), user2.id());

    codeOwnerInfos = queryCodeOwners(getCodeOwnersApi().query().withLimit(2), "/foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), user2.id());

    codeOwnerInfos = getCodeOwnersApi().query().withLimit(3).get("/foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id());

    codeOwnerInfos = getCodeOwnersApi().query().withLimit(4).get("/foo/bar/baz.md");
    assertThat(codeOwnerInfos).hasSize(4);
    // the order of the first 2 code owners is random
    assertThatList(codeOwnerInfos).element(0).hasAccountIdThat().isAnyOf(user.id(), user2.id());
    assertThatList(codeOwnerInfos).element(1).hasAccountIdThat().isAnyOf(user.id(), user2.id());
    assertThatList(codeOwnerInfos).element(2).hasAccountIdThat().isEqualTo(admin.id());
    // the order of the default code owners is random
    assertThatList(codeOwnerInfos)
        .element(3)
        .hasAccountIdThat()
        .isAnyOf(defaultCodeOwner1.id(), defaultCodeOwner2.id());

    codeOwnerInfos = getCodeOwnersApi().query().withLimit(5).get("/foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(
            admin.id(), user.id(), user2.id(), defaultCodeOwner1.id(), defaultCodeOwner2.id());

    codeOwnerInfos = getCodeOwnersApi().query().withLimit(6).get("/foo/bar/baz.md");
    assertThat(codeOwnerInfos).hasSize(6);
    // the order of the first 2 code owners is random
    assertThatList(codeOwnerInfos).element(0).hasAccountIdThat().isAnyOf(user.id(), user2.id());
    assertThatList(codeOwnerInfos).element(1).hasAccountIdThat().isAnyOf(user.id(), user2.id());
    assertThatList(codeOwnerInfos).element(2).hasAccountIdThat().isEqualTo(admin.id());
    // the order of the default code owners is random
    assertThatList(codeOwnerInfos)
        .element(3)
        .hasAccountIdThat()
        .isAnyOf(defaultCodeOwner1.id(), defaultCodeOwner2.id());
    assertThatList(codeOwnerInfos)
        .element(4)
        .hasAccountIdThat()
        .isAnyOf(defaultCodeOwner1.id(), defaultCodeOwner2.id());
    // the order of the global code owners is random
    assertThatList(codeOwnerInfos)
        .element(5)
        .hasAccountIdThat()
        .isAnyOf(globalOwner1.id(), globalOwner2.id());

    codeOwnerInfos = getCodeOwnersApi().query().withLimit(7).get("/foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(
            admin.id(),
            user.id(),
            user2.id(),
            defaultCodeOwner1.id(),
            defaultCodeOwner2.id(),
            globalOwner1.id(),
            globalOwner2.id());
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "ALL")
  public void getAllUsersAsCodeOwners_allVisible() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // Add a code owner config that makes all users code owners.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail("*")
        .create();

    List<CodeOwnerInfo> codeOwnerInfos = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), user2.id(), admin.id());

    // Query code owners with a limit.
    codeOwnerInfos = queryCodeOwners(getCodeOwnersApi().query().withLimit(2), "/foo/bar/baz.md");
    assertThat(codeOwnerInfos).hasSize(2);
    assertThatList(codeOwnerInfos)
        .element(0)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id(), admin.id());
    assertThatList(codeOwnerInfos)
        .element(1)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id(), admin.id());
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void getAllUsersAsCodeOwners_sameGroupVisibility() throws Exception {
    // Create 2 accounts that share a group.
    TestAccount user2 = accountCreator.user2();
    TestAccount user3 = accountCreator.create("user3", "user3@example.com", "User3", null);
    groupOperations.newGroup().addMember(user2.id()).addMember(user3.id()).create();

    // Add a code owner config that makes all users code owners.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail("*")
        .create();

    // user can only see itself
    requestScopeOperations.setApiUser(user.id());
    List<CodeOwnerInfo> codeOwnerInfos = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnerInfos).comparingElementsUsing(hasAccountId()).containsExactly(user.id());

    // user2 can see user3 and itself
    requestScopeOperations.setApiUser(user2.id());
    codeOwnerInfos = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user2.id(), user3.id());

    // admin can see all users
    requestScopeOperations.setApiUser(admin.id());
    codeOwnerInfos = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id(), user3.id());

    // Query code owners with a limit, user2 can see user3 and itself
    requestScopeOperations.setApiUser(user2.id());
    codeOwnerInfos = queryCodeOwners(getCodeOwnersApi().query().withLimit(1), "/foo/bar/baz.md");
    assertThat(codeOwnerInfos).hasSize(1);
    assertThatList(codeOwnerInfos).element(0).hasAccountIdThat().isAnyOf(user2.id(), user3.id());
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "VISIBLE_GROUP")
  public void getAllUsersAsCodeOwners_visibleGroupVisibility() throws Exception {
    // create a group that until contains user
    AccountGroup.UUID userGroup = groupOperations.newGroup().addMember(user.id()).create();

    // create user2 account and a group that only contains user2, but which is visible to user
    // (since user owns the group)
    TestAccount user2 = accountCreator.user2();
    groupOperations.newGroup().addMember(user2.id()).ownerGroupUuid(userGroup).create();

    // create user3 account and a group that only contains user3, but which is visible to all users
    TestAccount user3 = accountCreator.create("user3", "user3@example.com", "User3", null);
    groupOperations.newGroup().addMember(user3.id()).visibleToAll(true).create();

    // Add a code owner config that makes all users code owners.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail("*")
        .create();

    // user can only see itself, user2 (because user is owner of a group that contains user2) and
    // user3 (because user3 is member of a group that is visible to all users)
    requestScopeOperations.setApiUser(user.id());
    List<CodeOwnerInfo> codeOwnerInfos = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), user2.id(), user3.id());

    // user2 can see user3 and itself
    requestScopeOperations.setApiUser(user2.id());
    codeOwnerInfos = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user2.id(), user3.id());

    // admin can see all users
    requestScopeOperations.setApiUser(admin.id());
    codeOwnerInfos = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id(), user3.id());

    // Query code owners with a limit, user2 can see user3 and itself
    requestScopeOperations.setApiUser(user2.id());
    codeOwnerInfos = queryCodeOwners(getCodeOwnersApi().query().withLimit(1), "/foo/bar/baz.md");
    assertThat(codeOwnerInfos).hasSize(1);
    assertThatList(codeOwnerInfos).element(0).hasAccountIdThat().isAnyOf(user2.id(), user3.id());
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "NONE")
  public void getAllUsersAsCodeOwners_noneVisible() throws Exception {
    accountCreator.user2();

    // Add a code owner config that makes all users code owners.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail("*")
        .create();

    // Use user, since admin is allowed to view all accounts.
    requestScopeOperations.setApiUser(user.id());
    List<CodeOwnerInfo> codeOwnerInfos = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnerInfos).isEmpty();
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "NONE")
  public void getAllUsersAsCodeOwners_withViewAllAccounts() throws Exception {
    // Allow all users to view all accounts.
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.VIEW_ALL_ACCOUNTS).group(REGISTERED_USERS))
        .update();

    TestAccount user2 = accountCreator.user2();

    // Add a code owner config that makes all users code owners.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail("*")
        .create();

    List<CodeOwnerInfo> codeOwnerInfos = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id());
  }

  @Test
  public void getCodeOwnersProvidingASeedMakesSortOrderStableAcrocssRequests() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create some code owner configs
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .addCodeOwnerEmail(user.email())
        .addCodeOwnerEmail(user2.email())
        .create();

    long seed = (new Random()).nextLong();

    List<CodeOwnerInfo> codeOwnerInfos =
        queryCodeOwners(getCodeOwnersApi().query().withSeed(seed), "/foo/bar/baz.md");
    // all code owners have the same score, hence their order is random
    assertThatList(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id());

    // Check that the order for further requests that use the same seed is the same.
    List<Account.Id> expectedAccountIds =
        codeOwnerInfos.stream().map(info -> Account.id(info.account._accountId)).collect(toList());
    for (int i = 0; i < 10; i++) {
      assertThatList(queryCodeOwners(getCodeOwnersApi().query().withSeed(seed), "/foo/bar/baz.md"))
          .comparingElementsUsing(hasAccountId())
          .containsExactlyElementsIn(expectedAccountIds)
          .inOrder();
    }
  }

  @Test
  public void getCodeOwnersProvidingASeedMakesSortOrderStableAcrocssRequests_allUsersAreCodeOwners()
      throws Exception {
    TestAccount user2 = accountCreator.user2();

    // create some code owner configs
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail("*")
        .create();

    long seed = (new Random()).nextLong();

    List<CodeOwnerInfo> codeOwnerInfos =
        queryCodeOwners(getCodeOwnersApi().query().withSeed(seed), "/foo/bar/baz.md");
    // all code owners have the same score, hence their order is random
    assertThatList(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id());

    // Check that the order for further requests that use the same seed is the same.
    List<Account.Id> expectedAccountIds =
        codeOwnerInfos.stream().map(info -> Account.id(info.account._accountId)).collect(toList());
    for (int i = 0; i < 10; i++) {
      assertThatList(queryCodeOwners(getCodeOwnersApi().query().withSeed(seed), "/foo/bar/baz.md"))
          .comparingElementsUsing(hasAccountId())
          .containsExactlyElementsIn(expectedAccountIds)
          .inOrder();
    }
  }
}
