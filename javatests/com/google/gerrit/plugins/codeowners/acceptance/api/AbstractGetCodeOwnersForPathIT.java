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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoSubject.hasAccountId;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoSubject.hasAccountName;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnersInfoSubject.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toList;

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
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.client.ListAccountsOption;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.TestCodeOwnerConfigCreation;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.TestPathExpressions;
import com.google.gerrit.plugins.codeowners.api.CodeOwners;
import com.google.gerrit.plugins.codeowners.api.CodeOwnersInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportMode;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.restapi.CheckCodeOwnerCapability;
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

  protected CodeOwnersInfo queryCodeOwners(String path) throws RestApiException {
    return queryCodeOwners(getCodeOwnersApi().query(), path);
  }

  protected CodeOwnersInfo queryCodeOwners(CodeOwners.QueryRequest queryRequest, String path)
      throws RestApiException {
    return queryRequest.get(path);
  }

  @Test
  public void getCodeOwnersWhenNoCodeOwnerConfigsExist() throws Exception {
    assertThat(queryCodeOwners("/foo/bar/baz.md")).hasCodeOwnersThat().isEmpty();
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
        .addCodeOwnerEmail(CodeOwnerResolver.ALL_USERS_WILDCARD)
        .create();
    CodeOwnersInfo codeOwnersInfo = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnersInfo).hasCodeOwnersThat().isEmpty();
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isNull();
  }

  @Test
  public void getCodeOwnersForAbsolutePath() throws Exception {
    testGetCodeOwners(/* useAbsolutePath= */ true);
  }

  @Test
  public void getCodeOwnersForNonAbsolutePath() throws Exception {
    testGetCodeOwners(/* useAbsolutePath= */ false);
  }

  @Test
  public void getCodeOwnersForAnonymousUser() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    testGetCodeOwners(/* useAbsolutePath= */ true);
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

    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(useAbsolutePath ? "/foo/bar/baz.md" : "foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user2.id(), user.id(), admin.id())
        .inOrder();
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountName())
        .containsExactly(null, null, null);
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isNull();
    assertThat(codeOwnersInfo).hasDebugLogsThat().isNull();
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

    // 3. code owner config that makes "admin" a code owner and assigns code ownership to all users,
    // but for this test this code owner config is ignored, since the 2. code owner config ignores
    // code owners from parent code owner configs
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .addCodeOwnerEmail(CodeOwnerResolver.ALL_USERS_WILDCARD)
        .create();

    // Assert the code owners for "/foo/bar/baz.md". This evaluates the code owner configs in the
    // order: 1., 2., 3.
    // The 3. code owner config is ignored since the 2. code owner config has set
    // 'ignoreParentCodeOwners=true'. Hence the expected code owners are only the users that are
    // code owner according to the 1. and 2. code owner config: user2 + user
    CodeOwnersInfo codeOwnersInfo = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user2.id(), user.id())
        .inOrder();
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isNull();
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
                .addPathExpression(testPathExpressions.matchFileType("txt"))
                .addCodeOwnerEmail(admin.email())
                .build())
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression(testPathExpressions.matchFileType("md"))
                .addCodeOwnerEmail(user.email())
                .build())
        .create();

    assertThat(queryCodeOwners("/foo/bar/config.txt"))
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id());
    assertThat(queryCodeOwners("/foo/bar/baz.md"))
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id());
    assertThat(queryCodeOwners("/foo/bar/main.config")).hasCodeOwnersThat().isEmpty();
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

    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(
            getCodeOwnersApi().query().withOptions(ListAccountsOption.DETAILS), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
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
    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(
            getCodeOwnersApi().query().withOptions(ListAccountsOption.ALL_EMAILS),
            "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
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
    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(
            getCodeOwnersApi()
                .query()
                .withOptions(ListAccountsOption.DETAILS, ListAccountsOption.ALL_EMAILS),
            "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountName())
        .containsExactly(admin.fullName());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
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
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id(), user3.id());

    // Make the request as user2. This user only shares a group with user3. Since
    // "accounts.visibility" is set to "SAME_GROUP" user2 can only see user3's account (besides
    // the own account).
    requestScopeOperations.setApiUser(user2.id());

    // We expect only user2 and user3 as code owner (user and admin should be filtered
    // out because user2 cannot see their accounts).
    assertThat(queryCodeOwners("/foo/bar/baz.md"))
        .hasCodeOwnersThat()
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
        .hasCodeOwnersThat()
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
        .hasCodeOwnersThat()
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
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id());

    // user can see the own secondary email
    requestScopeOperations.setApiUser(user.id());
    assertThat(queryCodeOwners("/foo/bar/baz.md"))
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id());

    // user2 doesn't have the 'Modify Account' global capability and hence cannot see the secondary
    // email
    TestAccount user2 = accountCreator.user2();
    requestScopeOperations.setApiUser(user2.id());
    assertThat(queryCodeOwners("/foo/bar/baz.md")).hasCodeOwnersThat().isEmpty();
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
    CodeOwnersInfo codeOwnersInfo = queryCodeOwners("/foo/bar.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .hasSize(GetCodeOwnersForPathInBranch.DEFAULT_LIMIT);
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
    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().withLimit(1), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo).hasCodeOwnersThat().hasSize(1);
    // the first 2 code owners have the same scoring, so their order is random and we don't know
    // which of them we get when the limit is 1
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(0)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id());

    codeOwnersInfo = queryCodeOwners(getCodeOwnersApi().query().withLimit(2), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), user2.id());

    codeOwnersInfo = getCodeOwnersApi().query().withLimit(3).get("/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
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

    CodeOwnersInfo codeOwnersInfo = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id());
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "global.owner@example.com")
  public void getGlobalCodeOwners() throws Exception {
    TestAccount globalOwner =
        accountCreator.create("global_owner", "global.owner@example.com", "Global Owner", null);
    assertThat(queryCodeOwners("/foo/bar/baz.md"))
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(globalOwner.id());
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  public void getAllUsersAsGlobalCodeOwners() throws Exception {
    TestAccount user2 = accountCreator.user2();

    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().setResolveAllUsers(true), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), user2.id(), admin.id());
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();

    // Query code owners with a limit.
    requestScopeOperations.setApiUser(user.id());
    codeOwnersInfo =
        queryCodeOwners(
            getCodeOwnersApi().query().setResolveAllUsers(true).withLimit(2), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo).hasCodeOwnersThat().hasSize(2);
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(0)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id(), admin.id());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(1)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id(), admin.id());
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();

    // Query code owners without resolving all users.
    codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().setResolveAllUsers(false), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo).hasCodeOwnersThat().isEmpty();
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();
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
    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().withLimit(1), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo).hasCodeOwnersThat().hasSize(1);
    // the first 2 code owners have the same scoring, so their order is random and we don't know
    // which of them we get when the limit is 1
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(0)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id());

    codeOwnersInfo = queryCodeOwners(getCodeOwnersApi().query().withLimit(2), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), user2.id());

    codeOwnersInfo = getCodeOwnersApi().query().withLimit(3).get("/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id());

    codeOwnersInfo = getCodeOwnersApi().query().withLimit(4).get("/foo/bar/baz.md");
    assertThat(codeOwnersInfo).hasCodeOwnersThat().hasSize(4);
    // the order of the first 2 code owners is random
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(0)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(1)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(2)
        .hasAccountIdThat()
        .isEqualTo(admin.id());
    // the order of the global code owners is random
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(3)
        .hasAccountIdThat()
        .isAnyOf(globalOwner1.id(), globalOwner2.id());

    codeOwnersInfo = getCodeOwnersApi().query().withLimit(5).get("/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
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
        .hasCodeOwnersThat()
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
    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().withLimit(1), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo).hasCodeOwnersThat().hasSize(1);
    // the first 2 code owners have the same scoring, so their order is random and we don't know
    // which of them we get when the limit is 1
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(0)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id());

    codeOwnersInfo = queryCodeOwners(getCodeOwnersApi().query().withLimit(2), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), user2.id());

    codeOwnersInfo = getCodeOwnersApi().query().withLimit(3).get("/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id());

    codeOwnersInfo = getCodeOwnersApi().query().withLimit(4).get("/foo/bar/baz.md");
    assertThat(codeOwnersInfo).hasCodeOwnersThat().hasSize(4);
    // the order of the first 2 code owners is random
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(0)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(1)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(2)
        .hasAccountIdThat()
        .isEqualTo(admin.id());
    // the order of the default code owners is random
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(3)
        .hasAccountIdThat()
        .isAnyOf(defaultCodeOwner1.id(), defaultCodeOwner2.id());

    codeOwnersInfo = getCodeOwnersApi().query().withLimit(5).get("/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(
            admin.id(), user.id(), user2.id(), defaultCodeOwner1.id(), defaultCodeOwner2.id());

    codeOwnersInfo = getCodeOwnersApi().query().withLimit(6).get("/foo/bar/baz.md");
    assertThat(codeOwnersInfo).hasCodeOwnersThat().hasSize(6);
    // the order of the first 2 code owners is random
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(0)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(1)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(2)
        .hasAccountIdThat()
        .isEqualTo(admin.id());
    // the order of the default code owners is random
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(3)
        .hasAccountIdThat()
        .isAnyOf(defaultCodeOwner1.id(), defaultCodeOwner2.id());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(4)
        .hasAccountIdThat()
        .isAnyOf(defaultCodeOwner1.id(), defaultCodeOwner2.id());
    // the order of the global code owners is random
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(5)
        .hasAccountIdThat()
        .isAnyOf(globalOwner1.id(), globalOwner2.id());

    codeOwnersInfo = getCodeOwnersApi().query().withLimit(7).get("/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
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

    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().setResolveAllUsers(true), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), user2.id(), admin.id());
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();

    // Query code owners with a limit.
    requestScopeOperations.setApiUser(user.id());
    codeOwnersInfo =
        queryCodeOwners(
            getCodeOwnersApi().query().setResolveAllUsers(true).withLimit(2), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo).hasCodeOwnersThat().hasSize(2);
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(0)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id(), admin.id());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(1)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id(), admin.id());
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();
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
    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().setResolveAllUsers(true), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id());
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();

    // user2 can see user3 and itself
    requestScopeOperations.setApiUser(user2.id());
    codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().setResolveAllUsers(true), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user2.id(), user3.id());
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();

    // admin can see all users
    requestScopeOperations.setApiUser(admin.id());
    codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().setResolveAllUsers(true), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id(), user3.id());
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();

    // Query code owners with a limit, user2 can see user3 and itself
    requestScopeOperations.setApiUser(user2.id());
    codeOwnersInfo =
        queryCodeOwners(
            getCodeOwnersApi().query().setResolveAllUsers(true).withLimit(1), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo).hasCodeOwnersThat().hasSize(1);
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(0)
        .hasAccountIdThat()
        .isAnyOf(user2.id(), user3.id());
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();
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
    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().setResolveAllUsers(true), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), user2.id(), user3.id());
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();

    // user2 can see user3 and itself
    requestScopeOperations.setApiUser(user2.id());
    codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().setResolveAllUsers(true), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user2.id(), user3.id());
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();

    // admin can see all users
    requestScopeOperations.setApiUser(admin.id());
    codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().setResolveAllUsers(true), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id(), user3.id());
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();

    // Query code owners with a limit, user2 can see user3 and itself
    requestScopeOperations.setApiUser(user2.id());
    codeOwnersInfo =
        queryCodeOwners(
            getCodeOwnersApi().query().setResolveAllUsers(true).withLimit(1), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo).hasCodeOwnersThat().hasSize(1);
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(0)
        .hasAccountIdThat()
        .isAnyOf(user2.id(), user3.id());
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();
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
    CodeOwnersInfo codeOwnersInfo = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnersInfo).hasCodeOwnersThat().isEmpty();
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "NONE")
  public void getAllUsersAsCodeOwners_withViewAllAccounts() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // Add a code owner config that makes all users code owners.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail("*")
        .create();

    requestScopeOperations.setApiUser(user.id());

    // Since accounts.visibility = NONE, no account is visible and hence the list of code owners is
    // empty.
    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().setResolveAllUsers(true), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo).hasCodeOwnersThat().isEmpty();
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();

    // Allow all users to view all accounts.
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.VIEW_ALL_ACCOUNTS).group(REGISTERED_USERS))
        .update();

    // If VIEW_ALL_ACCOUNTS is assigned, all accounts are visible now.
    codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().setResolveAllUsers(true), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id());
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();
  }

  @Test
  public void getAllUsersAsCodeOwners_withoutResolvingAllUsers() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .addCodeOwnerEmail("*")
        .create();

    // If resolveAllUsers = false, only 'user' should be returned as code owner.
    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().setResolveAllUsers(false), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id());
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();

    // If resolveAllUsers = true, the result includes 'admin' in addition to 'user' which has code
    // ownership explicitly assigned.
    codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().setResolveAllUsers(true), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), admin.id());
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();
  }

  @Test
  public void getAllUsersAsCodeOwners_explicitlyMentionedCodeOwnersArePreferred() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // Add a code owner config that assigns code ownership to user2 and all users.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user2.email())
        .addCodeOwnerEmail("*")
        .create();

    // Query code owners with limits, "*" is resolved to random users, but user2 should always be
    // included since this user is set explicitly as code owner
    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(
            getCodeOwnersApi().query().setResolveAllUsers(true).withLimit(2), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo).hasCodeOwnersThat().hasSize(2);
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .contains(user2.id());

    codeOwnersInfo =
        queryCodeOwners(
            getCodeOwnersApi().query().setResolveAllUsers(true).withLimit(1), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user2.id());
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

    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().withSeed(seed), "/foo/bar/baz.md");
    // all code owners have the same score, hence their order is random
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id());

    // Check that the order for further requests that use the same seed is the same.
    List<Account.Id> expectedAccountIds =
        codeOwnersInfo.codeOwners.stream()
            .map(info -> Account.id(info.account._accountId))
            .collect(toList());
    for (int i = 0; i < 10; i++) {
      assertThat(queryCodeOwners(getCodeOwnersApi().query().withSeed(seed), "/foo/bar/baz.md"))
          .hasCodeOwnersThat()
          .comparingElementsUsing(hasAccountId())
          .containsExactlyElementsIn(expectedAccountIds)
          .inOrder();
    }
  }

  @Test
  public void getCodeOwnersProvidingASeedMakesSortOrderStableAcrossRequests_allUsersAreCodeOwners()
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

    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(
            getCodeOwnersApi().query().setResolveAllUsers(true).withSeed(seed), "/foo/bar/baz.md");
    // all code owners have the same score, hence their order is random
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id());

    // Check that the order for further requests that use the same seed is the same.
    List<Account.Id> expectedAccountIds =
        codeOwnersInfo.codeOwners.stream()
            .map(info -> Account.id(info.account._accountId))
            .collect(toList());
    for (int i = 0; i < 10; i++) {
      assertThat(
              queryCodeOwners(
                  getCodeOwnersApi().query().setResolveAllUsers(true).withSeed(seed),
                  "/foo/bar/baz.md"))
          .hasCodeOwnersThat()
          .comparingElementsUsing(hasAccountId())
          .containsExactlyElementsIn(expectedAccountIds)
          .inOrder();
    }
  }

  @Test
  public void allUsersOwnershipThatIsAssignedInParentIsConsideredEvenIfLimitWasAlreadyReached()
      throws Exception {
    // Create some code owner configs.
    // The order below reflects the order in which the code owner configs are evaluated.

    // 1. code owner config that makes "user" a code owner, inheriting code owners from parent code
    // owner configs is enabled by default
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(user.email())
        .create();

    // 2. code owner config that makes "admin" a code owner, inheriting code owners from parent code
    // owner configs is enabled by default
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    // 3. code owner config that makes all users code owners
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(CodeOwnerResolver.ALL_USERS_WILDCARD)
        .create();

    // Query code owners for "/foo/bar/baz.md" with limit 2. After inspecting code owner config 1.
    // and 2. enough code owners are found to satisfy the limit, but code owner config 3. is still
    // inspected so that the ownedByAllUsers field in the response gets set.
    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().withLimit(2), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), admin.id())
        .inOrder();
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  public void allUsersOwnershipThatIsAssignedAsGlobalOwnerIsConsideredEvenIfLimitWasAlreadyReached()
      throws Exception {
    // Create some code owner configs.
    // The order below reflects the order in which the code owner configs are evaluated.

    // 1. code owner config that makes "user" a code owner, inheriting code owners from parent code
    // owner configs is enabled by default
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(user.email())
        .create();

    // 2. code owner config that makes "admin" a code owner, inheriting code owners from parent code
    // owner configs is enabled by default
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(admin.email())
        .create();

    // Query code owners for "/foo/bar/baz.md" with limit 2. After inspecting code owner config 1.
    // and 2. enough code owners are found to satisfy the limit, but global code owners are still
    // inspected so that the ownedByAllUsers field in the response gets set.
    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().withLimit(2), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), admin.id())
        .inOrder();
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();
  }

  @Test
  public void getCodeOwnersWithHighestScoreOnly() throws Exception {
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

    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(
            getCodeOwnersApi().query().withHighestScoreOnly(/* highestScoreOnly= */ false),
            "/foo/bar/baz.md");
    assertThat(codeOwnersInfo).hasCodeOwnersThat().hasSize(3);
    // the first 2 code owners have the same scoring, so their order is random and we don't know
    // which of them we get get first and second
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(0)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(1)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(2)
        .hasAccountIdThat()
        .isEqualTo(admin.id());

    codeOwnersInfo =
        queryCodeOwners(
            getCodeOwnersApi().query().withHighestScoreOnly(/* highestScoreOnly= */ true),
            "/foo/bar/baz.md");
    assertThat(codeOwnersInfo).hasCodeOwnersThat().hasSize(2);
    // the first 2 code owners have the same scoring, so their order is random and we don't know
    // which of them we get get first and second
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(0)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(1)
        .hasAccountIdThat()
        .isAnyOf(user.id(), user2.id());
  }

  @Test
  public void getCodeOwnersWithHighestScoreOnly_noCodeOwners() throws Exception {
    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(
            getCodeOwnersApi().query().withHighestScoreOnly(/* highestScoreOnly= */ true),
            "/foo/bar/baz.md");
    assertThat(codeOwnersInfo).hasCodeOwnersThat().isEmpty();
  }

  @Test
  public void debugRequireCallerToBeAdminOrHaveTheCheckCodeOwnerCapability() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    AuthException authException =
        assertThrows(
            AuthException.class,
            () ->
                queryCodeOwners(
                    getCodeOwnersApi().query().withDebug(/* debug= */ true), "/foo/bar/baz.md"));
    assertThat(authException)
        .hasMessageThat()
        .isEqualTo(
            String.format("%s for plugin code-owners not permitted", CheckCodeOwnerCapability.ID));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "global.owner@example.com")
  public void getCodeOwnersWithDebug_byAdmin() throws Exception {
    testGetCodeOwnersWithDebug();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "global.owner@example.com")
  public void getCodeOwnersWithDebug_byUserThatHasTheCheckCodeOwnerCapability() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability("code-owners-" + CheckCodeOwnerCapability.ID).group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    testGetCodeOwnersWithDebug();
  }

  private void testGetCodeOwnersWithDebug() throws Exception {
    TestAccount globalOwner =
        accountCreator.create("global_owner", "global.owner@example.com", "Global Owner", null);

    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    TestAccount user2 = accountCreator.user2();

    CodeOwnerConfig.Key rootKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .create();

    String nonExistingEmail = "non-existing@example.com";
    CodeOwnerConfig.Key fooKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addCodeOwnerEmail(nonExistingEmail)
            .create();

    Project.NameKey nonExistingProject = Project.nameKey("non-existing");
    CodeOwnerConfigReference nonResolvableCodeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                codeOwnerConfigOperations
                    .codeOwnerConfig(CodeOwnerConfig.Key.create(nonExistingProject, "master", "/"))
                    .getFilePath())
            .setProject(nonExistingProject)
            .build();

    CodeOwnerConfig.Key fooBarKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/")
            .addImport(nonResolvableCodeOwnerConfigReference)
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression(testPathExpressions.matchFileType("md"))
                    .addCodeOwnerEmail(user.email())
                    .build())
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression(testPathExpressions.matchFileType("txt"))
                    .addCodeOwnerEmail(user2.email())
                    .build())
            .create();

    String path = "/foo/bar/baz.md";
    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().withDebug(/* debug= */ true), path);
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), admin.id(), globalOwner.id())
        .inOrder();
    assertThat(codeOwnersInfo)
        .hasDebugLogsThatContainAllOf(
            String.format("resolve code owners for %s from code owner config %s", path, fooBarKey),
            "per-file code owner set with path expressions [*.md] matches",
            String.format(
                "The import of %s:master:/%s in %s:master:/foo/bar/%s cannot be resolved:"
                    + " project %s not found",
                nonExistingProject.get(),
                getCodeOwnerConfigFileName(),
                project.get(),
                getCodeOwnerConfigFileName(),
                nonExistingProject.get()),
            String.format(
                "resolving code owner reference %s", CodeOwnerReference.create(user.email())),
            String.format("resolved to account %d", user.id().get()),
            String.format("resolve code owners for %s from code owner config %s", path, fooKey),
            String.format(
                "resolving code owner reference %s", CodeOwnerReference.create(nonExistingEmail)),
            String.format(
                "cannot resolve code owner email %s: no account with this email exists",
                nonExistingEmail),
            String.format("resolve code owners for %s from code owner config %s", path, rootKey),
            String.format(
                "resolving code owner reference %s", CodeOwnerReference.create(admin.email())),
            String.format("resolved to account %d", admin.id().get()),
            "resolve global code owners",
            String.format(
                "resolving code owner reference %s",
                CodeOwnerReference.create(globalOwner.email())),
            String.format("resolved to account %d", globalOwner.id().get()));
  }
}
