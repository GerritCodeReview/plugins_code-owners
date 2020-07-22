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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoSubject.assertThatList;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoSubject.hasAccountId;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoSubject.hasAccountName;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
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

    List<CodeOwnerInfo> codeOwnerInfos = queryCodeOwners("/foo/bar.md");
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id());

    // The first code owner in the result should be user as user has the best distance score.
    // The other 2 code owners come in a random order, but verifying this in a test is hard, hence
    // there is no assertion for this.
    assertThatList(codeOwnerInfos).element(0).hasAccountIdThat().isEqualTo(user.id());
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
}
