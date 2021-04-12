// Copyright (C) 2021 The Android Open Source Project
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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerCheckInfoSubject.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.TestCodeOwnerConfigCreation;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.TestPathExpressions;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerCheckInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportMode;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.restapi.CheckCodeOwnerCapability;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Arrays;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

/**
 * Acceptance test for the {@link com.google.gerrit.plugins.codeowners.restapi.CheckCodeOwner} REST
 * endpoint.
 */
public class CheckCodeOwnerIT extends AbstractCodeOwnersIT {
  private static final String ROOT_PATH = "/";

  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private AccountOperations accountOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject @ServerInitiated private Provider<AccountsUpdate> accountsUpdate;
  @Inject private ExternalIdNotes.Factory externalIdNotesFactory;

  private TestPathExpressions testPathExpressions;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    testPathExpressions = plugin.getSysInjector().getInstance(TestPathExpressions.class);
  }

  @Test
  public void requiresEmail() throws Exception {
    BadRequestException exception =
        assertThrows(BadRequestException.class, () -> checkCodeOwner("/", /* email= */ null));
    assertThat(exception).hasMessageThat().isEqualTo("email required");
  }

  @Test
  public void requiresPath() throws Exception {
    BadRequestException exception =
        assertThrows(
            BadRequestException.class, () -> checkCodeOwner(/* path= */ null, user.email()));
    assertThat(exception).hasMessageThat().isEqualTo("path required");
  }

  @Test
  public void requiresCallerToBeAdminOrHaveTheCheckCodeOwnerCapability() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    AuthException authException =
        assertThrows(AuthException.class, () -> checkCodeOwner(ROOT_PATH, user.email()));
    assertThat(authException)
        .hasMessageThat()
        .isEqualTo(
            String.format("%s for plugin code-owners not permitted", CheckCodeOwnerCapability.ID));
  }

  @Test
  public void checkCodeOwner_byAdmin() throws Exception {
    testCheckCodeOwner();
  }

  @Test
  public void checkCodeOwner_byUserThatHasTheCheckCodeOwnerCapability() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability("code-owners-" + CheckCodeOwnerCapability.ID).group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    testCheckCodeOwner();
  }

  private void testCheckCodeOwner() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);
    setAsCodeOwners("/foo/", codeOwner);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, codeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath("/foo/"));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath("/foo/")),
            String.format("resolved to account %s", codeOwner.id()));
  }

  @Test
  public void checkCodeOwnerThatHasCodeOwnershipThroughMultipleFiles() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);
    setAsRootCodeOwners(codeOwner);
    setAsCodeOwners("/foo/", codeOwner);
    setAsCodeOwners("/foo/bar/", codeOwner);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, codeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(
            getCodeOwnerConfigFilePath("/foo/bar/"),
            getCodeOwnerConfigFilePath("/foo/"),
            getCodeOwnerConfigFilePath(ROOT_PATH))
        .inOrder();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath("/foo/bar/")),
            String.format(
                "found email %s as code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath("/foo/")),
            String.format(
                "found email %s as code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format("resolved to account %s", codeOwner.id()));
  }

  @Test
  public void checkCodeOwnerWithParentCodeOwnersIgnored() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);
    setAsRootCodeOwners(codeOwner);

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .ignoreParentCodeOwners()
        .addCodeOwnerEmail(codeOwner.email())
        .create();

    setAsCodeOwners("/foo/bar/", codeOwner);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, codeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(
            getCodeOwnerConfigFilePath("/foo/bar/"), getCodeOwnerConfigFilePath("/foo/"))
        .inOrder();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath("/foo/bar/")),
            String.format(
                "found email %s as code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath("/foo/")),
            "parent code owners are ignored",
            String.format("resolved to account %s", codeOwner.id()));
  }

  @Test
  public void checkCodeOwner_secondaryEmail() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);
    String secondaryEmail = "codeOwnerSecondary@example.com";
    accountOperations
        .account(codeOwner.id())
        .forUpdate()
        .addSecondaryEmail(secondaryEmail)
        .update();

    setAsRootCodeOwners(secondaryEmail);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, secondaryEmail);
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath(ROOT_PATH));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in %s",
                secondaryEmail, getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format("resolved to account %s", codeOwner.id()));
  }

  @Test
  public void checkCodeOwner_ownedByAllUsers() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);

    setAsRootCodeOwners(CodeOwnerResolver.ALL_USERS_WILDCARD);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, codeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath(ROOT_PATH));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in %s",
                CodeOwnerResolver.ALL_USERS_WILDCARD, getCodeOwnerConfigFilePath(ROOT_PATH)));
  }

  @Test
  public void checkCodeOwner_ownedByEmailAndOwnedByAllUsers() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);

    setAsRootCodeOwners(codeOwner.email(), CodeOwnerResolver.ALL_USERS_WILDCARD);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, codeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath(ROOT_PATH));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format(
                "found email %s as code owner in %s",
                CodeOwnerResolver.ALL_USERS_WILDCARD, getCodeOwnerConfigFilePath(ROOT_PATH)));
  }

  @Test
  public void checkNonCodeOwner() throws Exception {
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, user.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo).hasCodeOwnerConfigFilePathsThat().isEmpty();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(String.format("resolved to account %s", user.id()));
  }

  @Test
  public void checkNonExistingEmail() throws Exception {
    String nonExistingEmail = "non-exiting@example.com";

    setAsRootCodeOwners(nonExistingEmail);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, nonExistingEmail);
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath(ROOT_PATH));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in %s",
                nonExistingEmail, getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format(
                "cannot resolve code owner email %s: no account with this email exists",
                nonExistingEmail));
  }

  @Test
  public void checkAmbiguousExistingEmail() throws Exception {
    String ambiguousEmail = "ambiguous@example.com";

    setAsRootCodeOwners(ambiguousEmail);

    // Add the email to 2 accounts to make it ambiguous.
    addEmail(user.id(), ambiguousEmail);
    addEmail(admin.id(), ambiguousEmail);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, ambiguousEmail);
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath(ROOT_PATH));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in %s",
                ambiguousEmail, getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format(
                "cannot resolve code owner email %s: email is ambiguous", ambiguousEmail));
  }

  @Test
  public void checkOrphanedEmail() throws Exception {
    // Create an external ID with an email for a non-existing account.
    String orphanedEmail = "orphaned@example.com";
    Account.Id accountId = Account.id(999999);
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.upsert(ExternalId.createEmail(accountId, orphanedEmail));
      extIdNotes.commit(md);
    }

    setAsRootCodeOwners(orphanedEmail);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, orphanedEmail);
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath(ROOT_PATH));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in %s",
                orphanedEmail, getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format(
                "cannot resolve account %s for email %s: account does not exists",
                accountId, orphanedEmail),
            String.format(
                "cannot resolve code owner email %s: no active account with this email found",
                orphanedEmail));
  }

  @Test
  public void checkInactiveAccount() throws Exception {
    TestAccount inactiveUser =
        accountCreator.create(
            "inactiveUser", "inactiveUser@example.com", "Inactive User", /* displayName= */ null);
    accountOperations.account(inactiveUser.id()).forUpdate().inactive().update();

    setAsRootCodeOwners(inactiveUser);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, inactiveUser.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath(ROOT_PATH));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in %s",
                inactiveUser.email(), getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format(
                "account %s for email %s is inactive", inactiveUser.id(), inactiveUser.email()));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.allowedEmailDomain", value = "example.net")
  public void checkEmailWithAllowedDomain() throws Exception {
    String emailWithAllowedEmailDomain = "foo@example.net";
    TestAccount userWithAllowedEmail =
        accountCreator.create(
            "userWithAllowedEmail",
            emailWithAllowedEmailDomain,
            "User with allowed emil",
            /* displayName= */ null);

    setAsRootCodeOwners(userWithAllowedEmail);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, emailWithAllowedEmailDomain);
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath(ROOT_PATH));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in %s",
                emailWithAllowedEmailDomain, getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format(
                "domain %s of email %s is allowed",
                emailWithAllowedEmailDomain.substring(emailWithAllowedEmailDomain.indexOf('@') + 1),
                emailWithAllowedEmailDomain));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.allowedEmailDomain", value = "example.net")
  public void checkEmailWithNonAllowedDomain() throws Exception {
    String emailWithNonAllowedEmailDomain = "foo@example.com";
    TestAccount userWithAllowedEmail =
        accountCreator.create(
            "userWithNonAllowedEmail",
            emailWithNonAllowedEmailDomain,
            "User with non-allowed emil",
            /* displayName= */ null);

    setAsRootCodeOwners(userWithAllowedEmail);

    CodeOwnerCheckInfo checkCodeOwnerInfo =
        checkCodeOwner(ROOT_PATH, emailWithNonAllowedEmailDomain);
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath(ROOT_PATH));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in %s",
                emailWithNonAllowedEmailDomain, getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format(
                "domain %s of email %s is not allowed",
                emailWithNonAllowedEmailDomain.substring(
                    emailWithNonAllowedEmailDomain.indexOf('@') + 1),
                emailWithNonAllowedEmailDomain));
  }

  @Test
  public void checkAllUsersWildcard() throws Exception {
    CodeOwnerCheckInfo checkCodeOwnerInfo =
        checkCodeOwner(ROOT_PATH, CodeOwnerResolver.ALL_USERS_WILDCARD);
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo).hasCodeOwnerConfigFilePathsThat().isEmpty();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
  }

  @Test
  public void checkAllUsersWildcard_ownedByAllUsers() throws Exception {
    setAsRootCodeOwners(CodeOwnerResolver.ALL_USERS_WILDCARD);

    CodeOwnerCheckInfo checkCodeOwnerInfo =
        checkCodeOwner(ROOT_PATH, CodeOwnerResolver.ALL_USERS_WILDCARD);
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath(ROOT_PATH));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in %s",
                CodeOwnerResolver.ALL_USERS_WILDCARD, getCodeOwnerConfigFilePath(ROOT_PATH)));
  }

  @Test
  public void checkDefaultCodeOwner() throws Exception {
    TestAccount defaultCodeOwner =
        accountCreator.create(
            "defaultCodeOwner",
            "defaultCodeOwner@example.com",
            "Default Code Owner",
            /* displayName= */ null);
    setAsDefaultCodeOwners(defaultCodeOwner);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, defaultCodeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo).hasCodeOwnerConfigFilePathsThat().isEmpty();
    assertThat(checkCodeOwnerInfo).isDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in default code owner config",
                defaultCodeOwner.email()),
            String.format("resolved to account %s", defaultCodeOwner.id()));
  }

  @Test
  public void checkDefaultCodeOwner_ownedByAllUsers() throws Exception {
    TestAccount defaultCodeOwner =
        accountCreator.create(
            "defaultCodeOwner",
            "defaultCodeOwner@example.com",
            "Default Code Owner",
            /* displayName= */ null);
    setAsDefaultCodeOwner(CodeOwnerResolver.ALL_USERS_WILDCARD);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, defaultCodeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo).hasCodeOwnerConfigFilePathsThat().isEmpty();
    assertThat(checkCodeOwnerInfo).isDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in default code owner config",
                CodeOwnerResolver.ALL_USERS_WILDCARD),
            String.format("resolved to account %s", defaultCodeOwner.id()));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "globalCodeOwner@example.com")
  public void checkGlobalCodeOwner() throws Exception {
    TestAccount globalCodeOwner =
        accountCreator.create(
            "globalCodeOwner",
            "globalCodeOwner@example.com",
            "Global Code Owner",
            /* displayName= */ null);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, globalCodeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo).hasCodeOwnerConfigFilePathsThat().isEmpty();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format("found email %s as global code owner", globalCodeOwner.email()),
            String.format("resolved to account %s", globalCodeOwner.id()));
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.globalCodeOwner",
      value = CodeOwnerResolver.ALL_USERS_WILDCARD)
  public void checkGlobalCodeOwner_ownedByAllUsers() throws Exception {
    TestAccount globalCodeOwner =
        accountCreator.create(
            "globalCodeOwner",
            "globalCodeOwner@example.com",
            "Global Code Owner",
            /* displayName= */ null);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, globalCodeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo).hasCodeOwnerConfigFilePathsThat().isEmpty();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as global code owner", CodeOwnerResolver.ALL_USERS_WILDCARD),
            String.format("resolved to account %s", globalCodeOwner.id()));
  }

  @Test
  public void checkCodeOwnerForOtherUser() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);
    setAsCodeOwners("/foo/", codeOwner);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, codeOwner.email(), user.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath("/foo/"));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath("/foo/")),
            String.format("account %s is visible to user %s", codeOwner.id(), user.username()),
            String.format("resolved to account %s", codeOwner.id()));
  }

  @Test
  public void cannotCheckForNonExistingUser() throws Exception {
    String nonExistingEmail = "non-existing@example.com";
    BadRequestException exception =
        assertThrows(
            BadRequestException.class, () -> checkCodeOwner("/", user.email(), nonExistingEmail));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("user %s not found", nonExistingEmail));
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void checkNonVisibleCodeOwnerForOtherUser() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);

    setAsRootCodeOwners(codeOwner);

    CodeOwnerCheckInfo checkCodeOwnerInfo =
        checkCodeOwner(ROOT_PATH, codeOwner.email(), user.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath(ROOT_PATH));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format(
                "cannot resolve code owner email %s: account %s is not visible to user %s",
                codeOwner.email(), codeOwner.id(), user.username()));
  }

  @Test
  public void checkNonVisibleCodeOwnerForOtherUser_secondaryEmail() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);
    String secondaryEmail = "codeOwnerSecondary@example.com";
    accountOperations
        .account(codeOwner.id())
        .forUpdate()
        .addSecondaryEmail(secondaryEmail)
        .update();

    setAsRootCodeOwners(secondaryEmail);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, secondaryEmail, user.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath(ROOT_PATH));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in %s",
                secondaryEmail, getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format(
                "cannot resolve code owner email %s: account %s is referenced by secondary email"
                    + " but user %s cannot see secondary emails",
                secondaryEmail, codeOwner.id(), user.username()));
  }

  @Test
  public void debugLogsContainUnresolvedImports() throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    CodeOwnerConfigReference unresolvableCodeOwnerConfigReferenceCodeOwnerConfigNotFound =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.ALL, "non-existing/" + getCodeOwnerConfigFileName());

    CodeOwnerConfigReference unresolvableCodeOwnerConfigReferenceProjectNotFound =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.ALL, getCodeOwnerConfigFileName())
            .setProject(Project.nameKey("non-existing"))
            .build();

    Project.NameKey nonReadableProject =
        projectOperations.newProject().name("non-readable").create();
    ConfigInput configInput = new ConfigInput();
    configInput.state = ProjectState.HIDDEN;
    gApi.projects().name(nonReadableProject.get()).config(configInput);
    CodeOwnerConfigReference unresolvableCodeOwnerConfigReferenceProjectNotReadable =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.ALL, getCodeOwnerConfigFileName())
            .setProject(nonReadableProject)
            .build();

    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath(ROOT_PATH)
            .addImport(unresolvableCodeOwnerConfigReferenceCodeOwnerConfigNotFound)
            .addImport(unresolvableCodeOwnerConfigReferenceProjectNotFound)
            .addImport(unresolvableCodeOwnerConfigReferenceProjectNotReadable)
            .create();

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, user.email());
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "Code owner config %s:%s:%s imports:\n"
                    + "* %s (global import, import mode = ALL)\n"
                    + "  * failed to resolve (code owner config not found)\n"
                    + "* %s:%s (global import, import mode = ALL)\n"
                    + "  * failed to resolve (project not found)\n"
                    + "* %s:%s (global import, import mode = ALL)\n"
                    + "  * failed to resolve (project state doesn't allow read)",
                project,
                "master",
                getCodeOwnerConfigFilePath(codeOwnerConfigKey.folderPath().toString()),
                unresolvableCodeOwnerConfigReferenceCodeOwnerConfigNotFound.filePath(),
                unresolvableCodeOwnerConfigReferenceProjectNotFound.project().get(),
                unresolvableCodeOwnerConfigReferenceProjectNotFound.filePath(),
                unresolvableCodeOwnerConfigReferenceProjectNotReadable.project().get(),
                unresolvableCodeOwnerConfigReferenceProjectNotReadable.filePath()),
            String.format(
                "The import of %s:%s:%s in %s:%s:%s cannot be resolved:"
                    + " code owner config does not exist (revision = %s)",
                project,
                "master",
                JgitPath.of(unresolvableCodeOwnerConfigReferenceCodeOwnerConfigNotFound.filePath())
                    .getAsAbsolutePath(),
                project,
                "master",
                getCodeOwnerConfigFilePath(codeOwnerConfigKey.folderPath().toString()),
                projectOperations.project(project).getHead("master").name()),
            String.format(
                "The import of %s:%s:%s in %s:%s:%s cannot be resolved: project %s not found",
                unresolvableCodeOwnerConfigReferenceProjectNotFound.project().get(),
                "master",
                JgitPath.of(unresolvableCodeOwnerConfigReferenceProjectNotFound.filePath())
                    .getAsAbsolutePath(),
                project,
                "master",
                getCodeOwnerConfigFilePath(codeOwnerConfigKey.folderPath().toString()),
                unresolvableCodeOwnerConfigReferenceProjectNotFound.project().get()),
            String.format(
                "The import of %s:%s:%s in %s:%s:%s cannot be resolved:"
                    + " state of project %s doesn't permit read",
                unresolvableCodeOwnerConfigReferenceProjectNotReadable.project().get(),
                "master",
                JgitPath.of(unresolvableCodeOwnerConfigReferenceProjectNotReadable.filePath())
                    .getAsAbsolutePath(),
                project,
                "master",
                getCodeOwnerConfigFilePath(codeOwnerConfigKey.folderPath().toString()),
                unresolvableCodeOwnerConfigReferenceProjectNotReadable.project().get()));
  }

  @Test
  public void debugLogsContainUnresolvedTransitiveImports() throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath(ROOT_PATH)
        .addImport(
            CodeOwnerConfigReference.create(
                CodeOwnerConfigImportMode.ALL, "/foo/" + getCodeOwnerConfigFileName()))
        .create();

    CodeOwnerConfigReference unresolvableCodeOwnerConfigReference =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.ALL, "non-existing/" + getCodeOwnerConfigFileName());
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addImport(unresolvableCodeOwnerConfigReference)
        .create();

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, user.email());
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "Code owner config %s:%s:%s imports:\n"
                    + "* %s (global import, import mode = ALL)\n"
                    + "  * %s (global import, import mode = ALL)\n"
                    + "    * failed to resolve (code owner config not found)",
                project,
                "master",
                getCodeOwnerConfigFilePath("/"),
                getCodeOwnerConfigFilePath("/foo/"),
                unresolvableCodeOwnerConfigReference.filePath()),
            String.format(
                "The import of %s:%s:%s in %s:%s:%s cannot be resolved:"
                    + " code owner config does not exist (revision = %s)",
                project,
                "master",
                JgitPath.of(unresolvableCodeOwnerConfigReference.filePath()).getAsAbsolutePath(),
                project,
                "master",
                getCodeOwnerConfigFilePath("/foo/"),
                projectOperations.project(project).getHead("master").name()));
  }

  @Test
  public void checkPerFileCodeOwner() throws Exception {
    TestAccount txtOwner =
        accountCreator.create(
            "txtCodeOwner", "txtCodeOwner@example.com", "Txt Code Owner", /* displayName= */ null);
    TestAccount mdOwner =
        accountCreator.create(
            "mdCodeOwner", "mdCodeOwner@example.com", "Md Code Owner", /* displayName= */ null);

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression(testPathExpressions.matchFileType("txt"))
                .addCodeOwnerEmail(txtOwner.email())
                .build())
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression(testPathExpressions.matchFileType("md"))
                .addCodeOwnerEmail(mdOwner.email())
                .build())
        .create();

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, mdOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath("/foo/"));
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "per-file code owner set with path expressions [%s] matches",
                testPathExpressions.matchFileType("md")),
            String.format(
                "found email %s as code owner in %s",
                mdOwner.email(), getCodeOwnerConfigFilePath("/foo/")),
            String.format("resolved to account %s", mdOwner.id()));
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatDoNotContainAnyOf(
            String.format(
                "path expressions [%s] matches", testPathExpressions.matchFileType("txt")));
  }

  @Test
  public void checkPerFileCodeOwnerWhenParentCodeOwnersAreIgnored() throws Exception {
    skipTestIfIgnoreParentCodeOwnersNotSupportedByCodeOwnersBackend();

    TestAccount fileCodeOwner =
        accountCreator.create(
            "fileCodeOwner",
            "fileCodeOwner@example.com",
            "File Code Owner",
            /* displayName= */ null);
    TestAccount folderCodeOwner =
        accountCreator.create(
            "folderCodeOwner",
            "folderCodeOwner@example.com",
            "Folder Code Owner",
            /* displayName= */ null);

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(folderCodeOwner.email())
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression(testPathExpressions.matchFileType("md"))
                .setIgnoreGlobalAndParentCodeOwners()
                .addCodeOwnerEmail(fileCodeOwner.email())
                .build())
        .create();

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, folderCodeOwner.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo).hasCodeOwnerConfigFilePathsThat().isEmpty();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "per-file code owner set with path expressions [%s] matches",
                testPathExpressions.matchFileType("md")),
            String.format(
                "found matching per-file code owner set (with path expressions = [%s]) that ignores"
                    + " parent code owners, hence ignoring the folder code owners",
                testPathExpressions.matchFileType("md")));

    checkCodeOwnerInfo = checkCodeOwner(path, fileCodeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath("/foo/"));
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "per-file code owner set with path expressions [%s] matches",
                testPathExpressions.matchFileType("md")),
            String.format(
                "found matching per-file code owner set (with path expressions = [%s]) that ignores"
                    + " parent code owners, hence ignoring the folder code owners",
                testPathExpressions.matchFileType("md")),
            String.format(
                "found email %s as code owner in %s",
                fileCodeOwner.email(), getCodeOwnerConfigFilePath("/foo/")),
            String.format("resolved to account %s", fileCodeOwner.id()));
  }

  @Test
  public void checkCodeOwnerFromImportedConfig() throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addImport(
            CodeOwnerConfigReference.create(
                CodeOwnerConfigImportMode.ALL, "/bar/" + getCodeOwnerConfigFileName()))
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .addImport(
            CodeOwnerConfigReference.create(
                CodeOwnerConfigImportMode.ALL, "/baz/" + getCodeOwnerConfigFileName()))
        .create();

    setAsCodeOwners("/baz/", codeOwner);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, codeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath("/foo/"));
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "Code owner config %s:%s:/foo/%s imports:\n"
                    + "* /bar/%s (global import, import mode = ALL)\n"
                    + "  * /baz/%s (global import, import mode = ALL)",
                project,
                "master",
                getCodeOwnerConfigFileName(),
                getCodeOwnerConfigFileName(),
                getCodeOwnerConfigFileName()),
            String.format(
                "found email %s as code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath("/foo/")),
            String.format("resolved to account %s", codeOwner.id()));
  }

  @Test
  public void checkCodeOwnerFromImportedPerFileConfig() throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    TestAccount mdCodeOwner =
        accountCreator.create(
            "mdCodeOwner", "mdCodeOwner@example.com", "Md Code Owner", /* displayName= */ null);

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addImport(
            CodeOwnerConfigReference.create(
                CodeOwnerConfigImportMode.ALL, "/bar/" + getCodeOwnerConfigFileName()))
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/bar/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression(testPathExpressions.matchFileType("md"))
                .addImport(
                    CodeOwnerConfigReference.create(
                        CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                        "/baz/" + getCodeOwnerConfigFileName()))
                .build())
        .create();

    setAsCodeOwners("/baz/", mdCodeOwner);

    // 1. check for mdCodeOwner and path of an md file
    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, mdCodeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath("/foo/"));
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "Code owner config %s:%s:/foo/%s imports:\n"
                    + "* /bar/%s (global import, import mode = ALL)\n"
                    + "  * per-file code owner set with path expressions [%s] matches\n"
                    + "  * /baz/%s (per-file import, import mode = GLOBAL_CODE_OWNER_SETS_ONLY,"
                    + " path expressions = [%s])",
                project,
                "master",
                getCodeOwnerConfigFileName(),
                getCodeOwnerConfigFileName(),
                testPathExpressions.matchFileType("md"),
                getCodeOwnerConfigFileName(),
                testPathExpressions.matchFileType("md")),
            String.format(
                "found email %s as code owner in %s",
                mdCodeOwner.email(), getCodeOwnerConfigFilePath("/foo/")),
            String.format("resolved to account %s", mdCodeOwner.id()));

    // 2. check for user and path of an md file
    checkCodeOwnerInfo = checkCodeOwner(path, user.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo).hasCodeOwnerConfigFilePathsThat().isEmpty();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "Code owner config %s:%s:/foo/%s imports:\n"
                    + "* /bar/%s (global import, import mode = ALL)\n"
                    + "  * per-file code owner set with path expressions [%s] matches\n"
                    + "  * /baz/%s (per-file import, import mode = GLOBAL_CODE_OWNER_SETS_ONLY,"
                    + " path expressions = [%s])",
                project,
                "master",
                getCodeOwnerConfigFileName(),
                getCodeOwnerConfigFileName(),
                testPathExpressions.matchFileType("md"),
                getCodeOwnerConfigFileName(),
                testPathExpressions.matchFileType("md")),
            String.format("resolved to account %s", user.id()));
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatDoNotContainAnyOf(String.format("email %s", user.email()));

    // 3. check for mdCodeOwner and path of an txt file
    path = "/foo/bar/baz.txt";
    checkCodeOwnerInfo = checkCodeOwner(path, mdCodeOwner.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo).hasCodeOwnerConfigFilePathsThat().isEmpty();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "Code owner config %s:%s:/foo/%s imports:\n"
                    + "* /bar/%s (global import, import mode = ALL)",
                project, "master", getCodeOwnerConfigFileName(), getCodeOwnerConfigFileName()),
            String.format("resolved to account %s", mdCodeOwner.id()));
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatDoNotContainAnyOf(String.format("email %s", mdCodeOwner.email()));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "ALL_USERS")
  public void checkFallbackCodeOwner_AllUsers() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);
    setAsCodeOwners("/foo/", codeOwner);

    // 1. Check for a file to which fallback code owners do not apply because code owners are
    // defined
    String path = "/foo/bar/baz.md";

    // 1a. by a code owner
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, codeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotFallbackCodeOwner();

    // 1b. by a non code owner
    checkCodeOwnerInfo = checkCodeOwner(path, user.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotFallbackCodeOwner();

    // 2. Check for a file to which fallback code owners apply because no code owners are defined
    path = "/other/bar/baz.md";
    checkCodeOwnerInfo = checkCodeOwner(path, user.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isFallbackCodeOwner();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "PROJECT_OWNERS")
  public void checkFallbackCodeOwner_ProjectOwners() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);
    setAsCodeOwners("/foo/", codeOwner);

    // 1. Check for a file to which fallback code owners do not apply because code owners are
    // defined
    String path = "/foo/bar/baz.md";

    // 1a. by a code owner
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, codeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotFallbackCodeOwner();

    // 1b. by a project owner
    checkCodeOwnerInfo = checkCodeOwner(path, admin.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotFallbackCodeOwner();

    // 1c. by a non code owner
    checkCodeOwnerInfo = checkCodeOwner(path, user.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotFallbackCodeOwner();

    // 2. Check for a file to which fallback code owners apply because no code owners are defined
    path = "/other/bar/baz.md";

    // 2b. by a project owner
    checkCodeOwnerInfo = checkCodeOwner(path, admin.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isFallbackCodeOwner();

    // 2b. by a non project owner
    checkCodeOwnerInfo = checkCodeOwner(path, user.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotFallbackCodeOwner();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "ALL_USERS")
  public void noFallbackCodeOwnerIfParentCodeOwnersIgnored() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .ignoreParentCodeOwners()
        .create();

    // 1. Check for a file to which parent code owners are ignored
    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, user.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotFallbackCodeOwner();

    // 2. Check for a file to which parent code owners are not ignored
    path = "/other/bar/baz.md";
    checkCodeOwnerInfo = checkCodeOwner(path, user.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isFallbackCodeOwner();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "ALL_USERS")
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void noFallbackCodeOwnerIfNonVisibleRelevantCodeOwnerExists() throws Exception {
    TestAccount nonVisibleCodeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(nonVisibleCodeOwner.email())
        .create();

    requestScopeOperations.setApiUser(user.id());

    // verify that the account is not visible
    assertThrows(
        ResourceNotFoundException.class, () -> gApi.accounts().id(nonVisibleCodeOwner.id().get()));

    // allow user to call the check code owner REST endpoint
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability("code-owners-" + CheckCodeOwnerCapability.ID).group(REGISTERED_USERS))
        .update();

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, admin.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotFallbackCodeOwner();
  }

  @Test
  public void noEmptyDebugLogs() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);
    setAsCodeOwners("/foo/", codeOwner);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, codeOwner.email());
    assertThat(checkCodeOwnerInfo).hasDebugLogsThatDoNotContainAnyOf("");
  }

  private CodeOwnerCheckInfo checkCodeOwner(String path, String email) throws RestApiException {
    return checkCodeOwner(path, email, null);
  }

  private CodeOwnerCheckInfo checkCodeOwner(String path, String email, @Nullable String user)
      throws RestApiException {
    return projectCodeOwnersApiFactory
        .project(project)
        .branch("master")
        .checkCodeOwner()
        .path(path)
        .email(email)
        .user(user)
        .check();
  }

  private String getCodeOwnerConfigFilePath(String folderPath) {
    assertThat(folderPath).startsWith("/");
    assertThat(folderPath).endsWith("/");
    return folderPath + getCodeOwnerConfigFileName();
  }

  private void setAsRootCodeOwners(String... emails) {
    TestCodeOwnerConfigCreation.Builder builder =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath(ROOT_PATH);
    Arrays.stream(emails).forEach(builder::addCodeOwnerEmail);
    builder.create();
  }

  private void setAsDefaultCodeOwner(String email) {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(RefNames.REFS_CONFIG)
        .folderPath(ROOT_PATH)
        .addCodeOwnerEmail(email)
        .create();
  }

  private void addEmail(Account.Id accountId, String email) throws Exception {
    accountsUpdate
        .get()
        .update(
            "Test update",
            accountId,
            (a, u) ->
                u.addExternalId(
                    ExternalId.create(
                        "foo",
                        "bar" + accountId.get(),
                        accountId,
                        email,
                        /* hashedPassword= */ null)));
  }
}
