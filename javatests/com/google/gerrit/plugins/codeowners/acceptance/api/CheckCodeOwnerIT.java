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
import static com.google.common.truth.TruthJUnit.assume;
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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerCheckInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportMode;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import com.google.gerrit.plugins.codeowners.config.BackendConfig;
import com.google.gerrit.plugins.codeowners.restapi.CheckCodeOwnerCapability;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;
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

  private BackendConfig backendConfig;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    backendConfig = plugin.getSysInjector().getInstance(BackendConfig.class);
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

    setAsRootCodeOwner(secondaryEmail);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, secondaryEmail);
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath(ROOT_PATH));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in %s",
                secondaryEmail, getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format("resolved to account %s", codeOwner.id()));
  }

  @Test
  public void checkNonCodeOwner() throws Exception {
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, user.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo).hasCodeOwnerConfigFilePathsThat().isEmpty();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(String.format("resolved to account %s", user.id()));
  }

  @Test
  public void checkNonExistingEmail() throws Exception {
    String nonExistingEmail = "non-exiting@example.com";

    setAsRootCodeOwner(nonExistingEmail);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, nonExistingEmail);
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath(ROOT_PATH));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
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

    setAsRootCodeOwner(ambiguousEmail);

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

    setAsRootCodeOwner(orphanedEmail);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, orphanedEmail);
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath(ROOT_PATH));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in %s",
                orphanedEmail, getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format(
                "cannot resolve code owner email %s: email belongs to account %s,"
                    + " but no account with this ID exists",
                orphanedEmail, accountId));
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
  }

  @Test
  public void checkAllUsersWildcard_ownedByAllUsers() throws Exception {
    setAsRootCodeOwner(CodeOwnerResolver.ALL_USERS_WILDCARD);

    CodeOwnerCheckInfo checkCodeOwnerInfo =
        checkCodeOwner(ROOT_PATH, CodeOwnerResolver.ALL_USERS_WILDCARD);
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath(ROOT_PATH));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
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
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as code owner in default code owner config",
                defaultCodeOwner.email()),
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
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format("found email %s as global code owner", globalCodeOwner.email()),
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

    setAsRootCodeOwner(secondaryEmail);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, secondaryEmail, user.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCodeOwnerConfigFilePathsThat()
        .containsExactly(getCodeOwnerConfigFilePath(ROOT_PATH));
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
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
    // imports are not supported for the proto backend
    assume().that(backendConfig.getDefaultBackend()).isNotInstanceOf(ProtoBackend.class);

    CodeOwnerConfigReference unresolvableCodeOwnerConfigReference =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.ALL, "non-existing/" + getCodeOwnerConfigFileName());
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath(ROOT_PATH)
            .addImport(unresolvableCodeOwnerConfigReference)
            .create();

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, user.email());
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThat()
        .contains(
            String.format(
                "The import of %s:%s:%s in %s:%s:%s cannot be resolved:"
                    + " code owner config does not exist (revision = %s)",
                project,
                "master",
                JgitPath.of(unresolvableCodeOwnerConfigReference.filePath()).getAsAbsolutePath(),
                project,
                "master",
                getCodeOwnerConfigFilePath(codeOwnerConfigKey.folderPath().toString()),
                projectOperations.project(project).getHead("master").name()));
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

  private void setAsRootCodeOwner(String email) {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath(ROOT_PATH)
        .addCodeOwnerEmail(email)
        .create();
  }

  private String getCodeOwnerConfigFileName() {
    CodeOwnerBackend backend = backendConfig.getDefaultBackend();
    if (backend instanceof FindOwnersBackend) {
      return FindOwnersBackend.CODE_OWNER_CONFIG_FILE_NAME;
    } else if (backend instanceof ProtoBackend) {
      return ProtoBackend.CODE_OWNER_CONFIG_FILE_NAME;
    }
    throw new IllegalStateException("unknown code owner backend: " + backend.getClass().getName());
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
