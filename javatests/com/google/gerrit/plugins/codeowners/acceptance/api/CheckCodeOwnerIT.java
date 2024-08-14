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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.labelPermissionKey;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerCheckInfoSubject.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.testing.TestActionRefUpdateContext.testRefAction;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.BranchInput;
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
import com.google.gerrit.plugins.codeowners.api.CodeOwnerConfigFileInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerAnnotation;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerAnnotations;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportMode;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.backend.config.BackendConfig;
import com.google.gerrit.plugins.codeowners.restapi.CheckCodeOwnerCapability;
import com.google.gerrit.plugins.codeowners.testing.CheckedCodeOwnerConfigFileInfoSubject;
import com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigFileInfoSubject;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdNotes;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.truth.ListSubject;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Arrays;
import org.eclipse.jgit.lib.Constants;
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
  @Inject private ExternalIdFactory externalIdFactory;

  private TestPathExpressions testPathExpressions;
  private CodeOwnerBackend backend;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    testPathExpressions = plugin.getSysInjector().getInstance(TestPathExpressions.class);
    backend = plugin.getSysInjector().getInstance(BackendConfig.class).getDefaultBackend();
  }

  @Test
  public void checkCodeOwnerForNonExistingBranch() throws Exception {
    RestResponse response =
        adminRestSession.get(
            String.format("/projects/%s/branches/non-existing/code_owners.check", project.get()));
    response.assertNotFound();
  }

  @Test
  public void checkCodeOwnerForSymbolicRefPointingToAnUnbornBranch() throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      testRefAction(() -> repo.updateRef(Constants.HEAD, true).link("refs/heads/non-existing"));
    }
    RestResponse response =
        adminRestSession.get(
            String.format("/projects/%s/branches/HEAD/code_owners.check", project.get()));
    response.assertNotFound();
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
    CodeOwnerConfig.Key codeOwnerConfigKey = setAsCodeOwners("/foo/", codeOwner);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, codeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo).canReadRef();
    assertThat(checkCodeOwnerInfo).canSeeChangeNotSet();
    assertThat(checkCodeOwnerInfo).canApproveChangeNotSet();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo).hasAnnotationsThat().isEmpty();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "resolve code owners for %s from code owner config %s:master:%s",
                path, project, getCodeOwnerConfigFilePath("/foo/")),
            String.format(
                "found email %s as a code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath("/foo/")),
            String.format("resolved email %s to account %s", codeOwner.email(), codeOwner.id()));
  }

  @Test
  public void checkCodeOwnerThatHasCodeOwnershipThroughMultipleFiles() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);
    CodeOwnerConfig.Key rootCodeOwnerConfigKey = setAsRootCodeOwners(codeOwner);
    CodeOwnerConfig.Key fooCodeOwnerConfigKey = setAsCodeOwners("/foo/", codeOwner);
    CodeOwnerConfig.Key fooBarCodeOwnerConfigKey = setAsCodeOwners("/foo/bar/", codeOwner);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, codeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();

    assertThat(checkCodeOwnerInfo).hasCheckedCodeOwnerConfigsThat().hasSize(3);
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .element(0)
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, fooBarCodeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .element(1)
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, fooCodeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .element(2)
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, rootCodeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();

    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as a code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath("/foo/bar/")),
            String.format(
                "found email %s as a code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath("/foo/")),
            String.format(
                "found email %s as a code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format("resolved email %s to account %s", codeOwner.email(), codeOwner.id()));
  }

  @Test
  public void checkCodeOwnerWithParentCodeOwnersIgnored() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);
    setAsRootCodeOwners(codeOwner);

    CodeOwnerConfig.Key fooCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .ignoreParentCodeOwners()
            .addCodeOwnerEmail(codeOwner.email())
            .create();

    CodeOwnerConfig.Key fooBarCodeOwnerConfigKey = setAsCodeOwners("/foo/bar/", codeOwner);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, codeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();

    assertThat(checkCodeOwnerInfo).hasCheckedCodeOwnerConfigsThat().hasSize(2);
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .element(0)
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, fooBarCodeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .element(1)
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, fooCodeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();

    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as a code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath("/foo/bar/")),
            String.format(
                "found email %s as a code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath("/foo/")),
            "parent code owners are ignored",
            String.format("resolved email %s to account %s", codeOwner.email(), codeOwner.id()));
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

    CodeOwnerConfig.Key codeOwnerConfigKey = setAsRootCodeOwners(secondaryEmail);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, secondaryEmail);
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as a code owner in %s",
                secondaryEmail, getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format("resolved email %s to account %s", secondaryEmail, codeOwner.id()));
  }

  @Test
  public void checkCodeOwner_ownedByAllUsers() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);

    CodeOwnerConfig.Key codeOwnerConfigKey =
        setAsRootCodeOwners(CodeOwnerResolver.ALL_USERS_WILDCARD);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, codeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found the all users wildcard ('%s') as a code owner in %s which makes %s a code"
                    + " owner",
                CodeOwnerResolver.ALL_USERS_WILDCARD,
                getCodeOwnerConfigFilePath(ROOT_PATH),
                codeOwner.email()));
  }

  @Test
  public void checkCodeOwner_ownedByEmailAndOwnedByAllUsers() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);

    CodeOwnerConfig.Key codeOwnerConfigKey =
        setAsRootCodeOwners(codeOwner.email(), CodeOwnerResolver.ALL_USERS_WILDCARD);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, codeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as a code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format(
                "found the all users wildcard ('%s') as a code owner in %s which makes %s a code"
                    + " owner",
                CodeOwnerResolver.ALL_USERS_WILDCARD,
                getCodeOwnerConfigFilePath(ROOT_PATH),
                codeOwner.email()));
  }

  @Test
  public void checkNonCodeOwner() throws Exception {
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, user.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo).hasCheckedCodeOwnerConfigsThat().isEmpty();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format("resolved email %s to account %s", user.email(), user.id()));
  }

  @Test
  public void checkNonExistingEmail() throws Exception {
    String nonExistingEmail = "non-exiting@example.com";

    CodeOwnerConfig.Key codeOwnerConfigKey = setAsRootCodeOwners(nonExistingEmail);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, nonExistingEmail);
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as a code owner in %s",
                nonExistingEmail, getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format(
                "cannot resolve code owner email %s: no account with this email exists",
                nonExistingEmail));
  }

  @Test
  public void checkAmbiguousExistingEmail() throws Exception {
    String ambiguousEmail = "ambiguous@example.com";

    CodeOwnerConfig.Key codeOwnerConfigKey = setAsRootCodeOwners(ambiguousEmail);

    // Add the email to 2 accounts to make it ambiguous.
    addEmail(user.id(), ambiguousEmail);
    addEmail(admin.id(), ambiguousEmail);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, ambiguousEmail);
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as a code owner in %s",
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
      extIdNotes.upsert(externalIdFactory.createEmail(accountId, orphanedEmail));
      extIdNotes.commit(md);
    }

    CodeOwnerConfig.Key codeOwnerConfigKey = setAsRootCodeOwners(orphanedEmail);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, orphanedEmail);
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as a code owner in %s",
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

    CodeOwnerConfig.Key codeOwnerConfigKey = setAsRootCodeOwners(inactiveUser);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, inactiveUser.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as a code owner in %s",
                inactiveUser.email(), getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format(
                "ignoring inactive account %s for email %s",
                inactiveUser.id(), inactiveUser.email()));
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

    CodeOwnerConfig.Key codeOwnerConfigKey = setAsRootCodeOwners(userWithAllowedEmail);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, emailWithAllowedEmailDomain);
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as a code owner in %s",
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
    TestAccount userWithNonAllowedEmail =
        accountCreator.create(
            "userWithNonAllowedEmail",
            emailWithNonAllowedEmailDomain,
            "User with non-allowed emil",
            /* displayName= */ null);

    CodeOwnerConfig.Key codeOwnerConfigKey = setAsRootCodeOwners(userWithNonAllowedEmail);

    CodeOwnerCheckInfo checkCodeOwnerInfo =
        checkCodeOwner(ROOT_PATH, emailWithNonAllowedEmailDomain);
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as a code owner in %s",
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
    assertThat(checkCodeOwnerInfo).hasCheckedCodeOwnerConfigsThat().isEmpty();
    assertThat(checkCodeOwnerInfo).canReadRefNotSet();
    assertThat(checkCodeOwnerInfo).canSeeChangeNotSet();
    assertThat(checkCodeOwnerInfo).canApproveChangeNotSet();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
  }

  @Test
  public void checkAllUsersWildcard_ownedByAllUsers() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        setAsRootCodeOwners(CodeOwnerResolver.ALL_USERS_WILDCARD);

    CodeOwnerCheckInfo checkCodeOwnerInfo =
        checkCodeOwner(ROOT_PATH, CodeOwnerResolver.ALL_USERS_WILDCARD);
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found the all users wildcard ('%s') as a code owner in %s which makes %s a code"
                    + " owner",
                CodeOwnerResolver.ALL_USERS_WILDCARD,
                getCodeOwnerConfigFilePath(ROOT_PATH),
                CodeOwnerResolver.ALL_USERS_WILDCARD));
  }

  @Test
  public void checkDefaultCodeOwner() throws Exception {
    TestAccount defaultCodeOwner =
        accountCreator.create(
            "defaultCodeOwner",
            "defaultCodeOwner@example.com",
            "Default Code Owner",
            /* displayName= */ null);
    CodeOwnerConfig.Key codeOwnerConfigKey = setAsDefaultCodeOwners(defaultCodeOwner);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, defaultCodeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo).isDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as a code owner in the default code owner config",
                defaultCodeOwner.email()),
            String.format(
                "resolved email %s to account %s",
                defaultCodeOwner.email(), defaultCodeOwner.id()));
  }

  @Test
  public void checkDefaultCodeOwner_ownedByAllUsers() throws Exception {
    TestAccount defaultCodeOwner =
        accountCreator.create(
            "defaultCodeOwner",
            "defaultCodeOwner@example.com",
            "Default Code Owner",
            /* displayName= */ null);
    CodeOwnerConfig.Key codeOwnerConfigKey =
        setAsDefaultCodeOwner(CodeOwnerResolver.ALL_USERS_WILDCARD);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, defaultCodeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo).isDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found the all users wildcard ('%s') as a code owner in the default code owner"
                    + " config which makes %s a code owner",
                CodeOwnerResolver.ALL_USERS_WILDCARD, defaultCodeOwner.email()),
            String.format(
                "resolved email %s to account %s",
                defaultCodeOwner.email(), defaultCodeOwner.id()));
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
    assertThat(checkCodeOwnerInfo).hasCheckedCodeOwnerConfigsThat().isEmpty();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format("found email %s as global code owner", globalCodeOwner.email()),
            String.format(
                "resolved email %s to account %s", globalCodeOwner.email(), globalCodeOwner.id()));
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
    assertThat(checkCodeOwnerInfo).hasCheckedCodeOwnerConfigsThat().isEmpty();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as global code owner", CodeOwnerResolver.ALL_USERS_WILDCARD),
            String.format(
                "resolved email %s to account %s", globalCodeOwner.email(), globalCodeOwner.id()));
  }

  @Test
  public void checkCodeOwnerForOtherUser() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);
    CodeOwnerConfig.Key codeOwnerConfigKey = setAsCodeOwners("/foo/", codeOwner);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, codeOwner.email(), user.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as a code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath("/foo/")),
            String.format("account %s is visible to user %s", codeOwner.id(), user.username()),
            String.format("resolved email %s to account %s", codeOwner.email(), codeOwner.id()));
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

    CodeOwnerConfig.Key codeOwnerConfigKey = setAsRootCodeOwners(codeOwner);

    CodeOwnerCheckInfo checkCodeOwnerInfo =
        checkCodeOwner(ROOT_PATH, codeOwner.email(), user.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as a code owner in %s",
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

    CodeOwnerConfig.Key codeOwnerConfigKey = setAsRootCodeOwners(secondaryEmail);

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, secondaryEmail, user.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotResolvable();
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo).isNotDefaultCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotGlobalCodeOwner();
    assertThat(checkCodeOwnerInfo).isNotOwnedByAllUsers();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as a code owner in %s",
                secondaryEmail, getCodeOwnerConfigFilePath(ROOT_PATH)),
            String.format(
                "cannot resolve code owner email %s: account %s is referenced by secondary email"
                    + " but user %s cannot see secondary emails",
                secondaryEmail, codeOwner.id(), user.username()));
  }

  @Test
  public void checkWithUnresolvedImports() throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    CodeOwnerConfigReference unresolvableCodeOwnerConfigReferenceCodeOwnerConfigNotFound =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.ALL, "/non-existing/" + getCodeOwnerConfigFileName());

    CodeOwnerConfigReference unresolvableCodeOwnerConfigReferenceProjectNotFound =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.ALL, "/" + getCodeOwnerConfigFileName())
            .setProject(Project.nameKey("non-existing"))
            .build();

    Project.NameKey nonReadableProject =
        projectOperations.newProject().name("non-readable").create();
    ConfigInput configInput = new ConfigInput();
    configInput.state = ProjectState.HIDDEN;
    gApi.projects().name(nonReadableProject.get()).config(configInput);
    CodeOwnerConfigReference unresolvableCodeOwnerConfigReferenceProjectNotReadable =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.ALL, "/" + getCodeOwnerConfigFileName())
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

    CodeOwnerConfigFileInfoSubject codeOwnerConfigFileInfoSubject =
        assertThat(checkCodeOwnerInfo)
            .hasCheckedCodeOwnerConfigsThat()
            .onlyElement()
            .hasCodeOwnerConfigFileThat();
    codeOwnerConfigFileInfoSubject.assertKey(backend, codeOwnerConfigKey);
    codeOwnerConfigFileInfoSubject
        .assertNoWebLinks()
        .assertNoResolvedImports()
        .assertNoImportMode();

    ListSubject<CodeOwnerConfigFileInfoSubject, CodeOwnerConfigFileInfo>
        unresolvedImportsListSubject = codeOwnerConfigFileInfoSubject.hasUnresolvedImportsThat();
    unresolvedImportsListSubject.hasSize(3);

    CodeOwnerConfigFileInfoSubject unresolvedImportSubject1 =
        unresolvedImportsListSubject.element(0);
    unresolvedImportSubject1.hasProjectThat().isEqualTo(project.get());
    unresolvedImportSubject1.hasBranchThat().isEqualTo("refs/heads/master");
    unresolvedImportSubject1
        .hasPathThat()
        .isEqualTo(
            unresolvableCodeOwnerConfigReferenceCodeOwnerConfigNotFound.filePath().toString());
    unresolvedImportSubject1.assertImportMode(
        unresolvableCodeOwnerConfigReferenceCodeOwnerConfigNotFound.importMode());
    unresolvedImportSubject1
        .hasUnresolvedErrorMessageThat()
        .isEqualTo(
            String.format(
                "code owner config does not exist (revision = %s)",
                projectOperations.project(project).getHead("master").name()));
    unresolvedImportSubject1.assertNoWebLinks().assertNoImports();

    CodeOwnerConfigFileInfoSubject unresolvedImportSubject2 =
        unresolvedImportsListSubject.element(1);
    unresolvedImportSubject2
        .hasProjectThat()
        .isEqualTo(unresolvableCodeOwnerConfigReferenceProjectNotFound.project().get().get());
    unresolvedImportSubject2.hasBranchThat().isEqualTo("refs/heads/master");
    unresolvedImportSubject2
        .hasPathThat()
        .isEqualTo(unresolvableCodeOwnerConfigReferenceProjectNotFound.filePath().toString());
    unresolvedImportSubject2.assertImportMode(
        unresolvableCodeOwnerConfigReferenceProjectNotFound.importMode());
    unresolvedImportSubject2
        .hasUnresolvedErrorMessageThat()
        .isEqualTo(
            String.format(
                "project %s not found",
                unresolvableCodeOwnerConfigReferenceProjectNotFound.project().get()));
    unresolvedImportSubject2.assertNoWebLinks().assertNoImports();

    CodeOwnerConfigFileInfoSubject unresolvedImportSubject3 =
        unresolvedImportsListSubject.element(2);
    unresolvedImportSubject3
        .hasProjectThat()
        .isEqualTo(unresolvableCodeOwnerConfigReferenceProjectNotReadable.project().get().get());
    unresolvedImportSubject3.hasBranchThat().isEqualTo("refs/heads/master");
    unresolvedImportSubject3
        .hasPathThat()
        .isEqualTo(unresolvableCodeOwnerConfigReferenceProjectNotReadable.filePath().toString());
    unresolvedImportSubject3.assertImportMode(
        unresolvableCodeOwnerConfigReferenceProjectNotReadable.importMode());
    unresolvedImportSubject3
        .hasUnresolvedErrorMessageThat()
        .isEqualTo(
            String.format(
                "state of project %s doesn't permit read",
                unresolvableCodeOwnerConfigReferenceProjectNotReadable.project().get()));
    unresolvedImportSubject3.assertNoWebLinks().assertNoImports();

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
  public void checkWithUnresolvedTransitiveImports() throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.ALL, "/foo/" + getCodeOwnerConfigFileName());
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath(ROOT_PATH)
            .addImport(codeOwnerConfigReference)
            .create();

    CodeOwnerConfigReference unresolvableCodeOwnerConfigReference =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.ALL, "/non-existing/" + getCodeOwnerConfigFileName());
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addImport(unresolvableCodeOwnerConfigReference)
        .create();

    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(ROOT_PATH, user.email());

    CodeOwnerConfigFileInfoSubject codeOwnerConfigFileInfoSubject =
        assertThat(checkCodeOwnerInfo)
            .hasCheckedCodeOwnerConfigsThat()
            .onlyElement()
            .hasCodeOwnerConfigFileThat();
    codeOwnerConfigFileInfoSubject.assertKey(backend, codeOwnerConfigKey);
    codeOwnerConfigFileInfoSubject
        .assertNoWebLinks()
        .assertNoUnresolvedImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();

    CodeOwnerConfigFileInfoSubject importSubject =
        codeOwnerConfigFileInfoSubject.hasImportsThat().onlyElement();
    importSubject.hasProjectThat().isEqualTo(project.get());
    importSubject.hasBranchThat().isEqualTo("refs/heads/master");
    importSubject.hasPathThat().isEqualTo(codeOwnerConfigReference.filePath().toString());
    importSubject.assertImportMode(codeOwnerConfigReference.importMode());
    importSubject.assertNoWebLinks().assertNoResolvedImports();

    CodeOwnerConfigFileInfoSubject transitiveImportSubject =
        importSubject.hasUnresolvedImportsThat().onlyElement();
    transitiveImportSubject.hasProjectThat().isEqualTo(project.get());
    transitiveImportSubject.hasBranchThat().isEqualTo("refs/heads/master");
    transitiveImportSubject
        .hasPathThat()
        .isEqualTo(unresolvableCodeOwnerConfigReference.filePath().toString());
    transitiveImportSubject.assertImportMode(unresolvableCodeOwnerConfigReference.importMode());
    transitiveImportSubject
        .hasUnresolvedErrorMessageThat()
        .isEqualTo(
            String.format(
                "code owner config does not exist (revision = %s)",
                projectOperations.project(project).getHead("master").name()));
    transitiveImportSubject.assertNoWebLinks().assertNoImports();

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

    CodeOwnerConfig.Key codeOwnerConfigKey =
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
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "per-file code owner set with path expressions [%s] matches",
                testPathExpressions.matchFileType("md")),
            String.format(
                "found email %s as a code owner in %s",
                mdOwner.email(), getCodeOwnerConfigFilePath("/foo/")),
            String.format("resolved email %s to account %s", mdOwner.email(), mdOwner.id()));
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

    CodeOwnerConfig.Key codeOwnerConfigKey =
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
    assertThat(checkCodeOwnerInfo)
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .doesNotAssignCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
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
        .hasCheckedCodeOwnerConfigsThat()
        .onlyElement()
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, codeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();
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
                "found email %s as a code owner in %s",
                fileCodeOwner.email(), getCodeOwnerConfigFilePath("/foo/")),
            String.format(
                "resolved email %s to account %s", fileCodeOwner.email(), fileCodeOwner.id()));
  }

  @Test
  public void checkCodeOwnerFromImportedConfig() throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);

    CodeOwnerConfigReference barCodeOwnerConfigReference =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.ALL, "/bar/" + getCodeOwnerConfigFileName());
    CodeOwnerConfig.Key fooCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addImport(barCodeOwnerConfigReference)
            .create();

    CodeOwnerConfigReference bazCodeOwnerConfigReference =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.ALL, "/baz/" + getCodeOwnerConfigFileName());
    CodeOwnerConfig.Key barCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .addImport(bazCodeOwnerConfigReference)
            .create();

    CodeOwnerConfig.Key bazCodeOwnerConfigKey = setAsCodeOwners("/baz/", codeOwner);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, codeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();

    CheckedCodeOwnerConfigFileInfoSubject codeOwnerConfigFileInfoSubject =
        assertThat(checkCodeOwnerInfo).hasCheckedCodeOwnerConfigsThat().onlyElement();
    codeOwnerConfigFileInfoSubject
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, fooCodeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoUnresolvedImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();

    CodeOwnerConfigFileInfoSubject importSubject =
        codeOwnerConfigFileInfoSubject.hasCodeOwnerConfigFileThat().hasImportsThat().onlyElement();
    importSubject.assertKey(backend, barCodeOwnerConfigKey);
    importSubject.assertImportMode(barCodeOwnerConfigReference.importMode());
    importSubject.assertNoWebLinks().assertNoUnresolvedImports().assertNoUnresolvedErrorMessage();

    CodeOwnerConfigFileInfoSubject transitiveImportSubject =
        importSubject.hasImportsThat().onlyElement();
    transitiveImportSubject.assertKey(backend, bazCodeOwnerConfigKey);
    transitiveImportSubject.assertImportMode(bazCodeOwnerConfigReference.importMode());
    transitiveImportSubject.assertNoWebLinks().assertNoImports().assertNoUnresolvedErrorMessage();

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
                "found email %s as a code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath("/foo/")),
            String.format("resolved email %s to account %s", codeOwner.email(), codeOwner.id()));
  }

  @Test
  public void checkCodeOwnerFromImportedPerFileConfig() throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    TestAccount mdCodeOwner =
        accountCreator.create(
            "mdCodeOwner", "mdCodeOwner@example.com", "Md Code Owner", /* displayName= */ null);

    CodeOwnerConfigReference barCodeOwnerConfigReference =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.ALL, "/bar/" + getCodeOwnerConfigFileName());
    CodeOwnerConfig.Key fooCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addImport(barCodeOwnerConfigReference)
            .create();

    CodeOwnerConfigReference bazCodeOwnerConfigReference =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
            "/baz/" + getCodeOwnerConfigFileName());
    CodeOwnerConfig.Key barCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/bar/")
            .addCodeOwnerSet(
                CodeOwnerSet.builder()
                    .addPathExpression(testPathExpressions.matchFileType("md"))
                    .addImport(bazCodeOwnerConfigReference)
                    .build())
            .create();

    CodeOwnerConfig.Key bazCodeOwnerConfigKey = setAsCodeOwners("/baz/", mdCodeOwner);

    // 1. check for mdCodeOwner and path of an md file
    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, mdCodeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();

    CheckedCodeOwnerConfigFileInfoSubject codeOwnerConfigFileInfoSubject =
        assertThat(checkCodeOwnerInfo).hasCheckedCodeOwnerConfigsThat().onlyElement();
    codeOwnerConfigFileInfoSubject
        .assignsCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, fooCodeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoUnresolvedImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();

    CodeOwnerConfigFileInfoSubject importSubject =
        codeOwnerConfigFileInfoSubject.hasCodeOwnerConfigFileThat().hasImportsThat().onlyElement();
    importSubject.assertKey(backend, barCodeOwnerConfigKey);
    importSubject.assertImportMode(barCodeOwnerConfigReference.importMode());
    importSubject.assertNoWebLinks().assertNoUnresolvedImports().assertNoUnresolvedErrorMessage();

    CodeOwnerConfigFileInfoSubject transitiveImportSubject =
        importSubject.hasImportsThat().onlyElement();
    transitiveImportSubject.assertKey(backend, bazCodeOwnerConfigKey);
    transitiveImportSubject.assertImportMode(bazCodeOwnerConfigReference.importMode());
    transitiveImportSubject.assertNoWebLinks().assertNoImports().assertNoUnresolvedErrorMessage();

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
                "found email %s as a code owner in %s",
                mdCodeOwner.email(), getCodeOwnerConfigFilePath("/foo/")),
            String.format(
                "resolved email %s to account %s", mdCodeOwner.email(), mdCodeOwner.id()));

    // 2. check for user and path of an md file
    checkCodeOwnerInfo = checkCodeOwner(path, user.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();

    codeOwnerConfigFileInfoSubject =
        assertThat(checkCodeOwnerInfo).hasCheckedCodeOwnerConfigsThat().onlyElement();
    codeOwnerConfigFileInfoSubject
        .doesNotAssignCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, fooCodeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoUnresolvedImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();

    importSubject =
        codeOwnerConfigFileInfoSubject.hasCodeOwnerConfigFileThat().hasImportsThat().onlyElement();
    importSubject.assertKey(backend, barCodeOwnerConfigKey);
    importSubject.assertImportMode(barCodeOwnerConfigReference.importMode());
    importSubject.assertNoWebLinks().assertNoUnresolvedImports().assertNoUnresolvedErrorMessage();

    transitiveImportSubject = importSubject.hasImportsThat().onlyElement();
    transitiveImportSubject.assertKey(backend, bazCodeOwnerConfigKey);
    transitiveImportSubject.assertImportMode(bazCodeOwnerConfigReference.importMode());
    transitiveImportSubject.assertNoWebLinks().assertNoImports().assertNoUnresolvedErrorMessage();

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
            String.format("resolved email %s to account %s", user.email(), user.id()));
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatDoNotContainAnyOf(String.format("email %s", user.email()));

    // 3. check for mdCodeOwner and path of an txt file
    path = "/foo/bar/baz.txt";
    checkCodeOwnerInfo = checkCodeOwner(path, mdCodeOwner.email());
    assertThat(checkCodeOwnerInfo).isNotCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();

    codeOwnerConfigFileInfoSubject =
        assertThat(checkCodeOwnerInfo).hasCheckedCodeOwnerConfigsThat().onlyElement();
    codeOwnerConfigFileInfoSubject
        .doesNotAssignCodeOwnershipToUser()
        .hasCodeOwnerConfigFileThat()
        .assertKey(backend, fooCodeOwnerConfigKey)
        .assertNoWebLinks()
        .assertNoUnresolvedImports()
        .assertNoUnresolvedErrorMessage()
        .assertNoImportMode();

    importSubject =
        codeOwnerConfigFileInfoSubject.hasCodeOwnerConfigFileThat().hasImportsThat().onlyElement();
    importSubject.assertKey(backend, barCodeOwnerConfigKey);
    importSubject.assertImportMode(barCodeOwnerConfigReference.importMode());
    importSubject.assertNoWebLinks().assertNoImports().assertNoUnresolvedErrorMessage();

    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "Code owner config %s:%s:/foo/%s imports:\n"
                    + "* /bar/%s (global import, import mode = ALL)",
                project, "master", getCodeOwnerConfigFileName(), getCodeOwnerConfigFileName()),
            String.format(
                "resolved email %s to account %s", mdCodeOwner.email(), mdCodeOwner.id()));
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

  @Test
  public void checkCodeOwnerThatCannotReadRef() throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);
    setAsCodeOwners("/foo/", codeOwner);

    // Make read permission on master branch exclusive for admins, so that the code owner cannot
    // read master.
    projectOperations
        .allProjectsForUpdate()
        .add(allow(Permission.READ).ref("refs/heads/master").group(adminGroupUuid()))
        .setExclusiveGroup(permissionKey(Permission.READ).ref("refs/heads/master"), true)
        .update();

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, codeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo).cannotReadRef();
    assertThat(checkCodeOwnerInfo).canSeeChangeNotSet();
    assertThat(checkCodeOwnerInfo).canApproveChangeNotSet();
  }

  @Test
  public void cannotCheckForNonExistingChange() throws Exception {
    String nonExistingChange = "non-existing";
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> checkCodeOwnerForChange("/", user.email(), nonExistingChange));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("change %s not found", nonExistingChange));
  }

  @Test
  public void cannotCheckForNonVisibleChange() throws Exception {
    String changeId = createChange().getChangeId();
    gApi.changes().id(changeId).setPrivate(true);

    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability("code-owners-" + CheckCodeOwnerCapability.ID).group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    BadRequestException exception =
        assertThrows(
            BadRequestException.class, () -> checkCodeOwnerForChange("/", user.email(), changeId));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("change %s not found", changeId));
  }

  @Test
  public void cannotCheckForChangeOfOtherBranch() throws Exception {
    // Create another branch
    String branchName = "foo";
    BranchInput branchInput = new BranchInput();
    branchInput.ref = branchName;
    branchInput.revision = projectOperations.project(project).getHead("master").name();
    gApi.projects().name(project.get()).branch(branchInput.ref).create(branchInput);

    String changeId = createChange("refs/for/" + branchName).getChangeId();
    BadRequestException exception =
        assertThrows(
            BadRequestException.class, () -> checkCodeOwnerForChange("/", user.email(), changeId));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("target branch of specified change must match branch from the request URL");
  }

  @Test
  public void checkCodeOwnerThatCannotSeeChange_privateChange() throws Exception {
    String changeId = createChange().getChangeId();
    gApi.changes().id(changeId).setPrivate(true);

    testCheckCodeOwnerThatCannotSeeChange(changeId, /* canReadRef= */ true);
  }

  @Test
  public void checkCodeOwnerThatCannotSeeChange_cannotReadRef() throws Exception {
    String changeId = createChange().getChangeId();

    // Make read permission on master branch exclusive for admins, so that the code owner cannot
    // read master.
    projectOperations
        .allProjectsForUpdate()
        .add(allow(Permission.READ).ref("refs/heads/master").group(adminGroupUuid()))
        .setExclusiveGroup(permissionKey(Permission.READ).ref("refs/heads/master"), true)
        .update();

    testCheckCodeOwnerThatCannotSeeChange(changeId, /* canReadRef= */ false);
  }

  private void testCheckCodeOwnerThatCannotSeeChange(String changeId, boolean canReadRef)
      throws Exception {
    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);
    setAsCodeOwners("/foo/", codeOwner);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo =
        checkCodeOwnerForChange(path, codeOwner.email(), changeId);
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    if (canReadRef) {
      assertThat(checkCodeOwnerInfo).canReadRef();
    } else {
      assertThat(checkCodeOwnerInfo).cannotReadRef();
    }
    assertThat(checkCodeOwnerInfo).cannotSeeChange();
    assertThat(checkCodeOwnerInfo).canApproveChange();
  }

  @Test
  public void checkCodeOwnerThatCannotApproveChange() throws Exception {
    String changeId = createChange().getChangeId();

    // Remove permission to vote on the Code-Review label.
    projectOperations
        .allProjectsForUpdate()
        .remove(labelPermissionKey("Code-Review").ref("refs/heads/*"))
        .update();

    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);
    setAsCodeOwners("/foo/", codeOwner);

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo =
        checkCodeOwnerForChange(path, codeOwner.email(), changeId);
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo).isResolvable();
    assertThat(checkCodeOwnerInfo).canReadRef();
    assertThat(checkCodeOwnerInfo).canSeeChange();
    assertThat(checkCodeOwnerInfo).cannotApproveChange();
  }

  @Test
  public void checkCodeOwnerWithAnnotations() throws Exception {
    skipTestIfAnnotationsNotSupportedByCodeOwnersBackend();

    TestAccount codeOwner =
        accountCreator.create(
            "codeOwner", "codeOwner@example.com", "Code Owner", /* displayName= */ null);

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addCodeOwnerEmail(codeOwner.email())
                .addAnnotation(
                    codeOwner.email(), CodeOwnerAnnotations.LAST_RESORT_SUGGESTION_ANNOTATION)
                .build())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addCodeOwnerEmail(CodeOwnerResolver.ALL_USERS_WILDCARD)
                .addAnnotation(
                    CodeOwnerResolver.ALL_USERS_WILDCARD, CodeOwnerAnnotation.create("ANNOTATION"))
                .build())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addCodeOwnerEmail(codeOwner.email())
                .addAnnotation(
                    codeOwner.email(), CodeOwnerAnnotations.LAST_RESORT_SUGGESTION_ANNOTATION)
                .addAnnotation(codeOwner.email(), CodeOwnerAnnotation.create("OTHER_ANNOTATION"))
                .build())
        .create();

    String path = "/foo/bar/baz.md";
    CodeOwnerCheckInfo checkCodeOwnerInfo = checkCodeOwner(path, codeOwner.email());
    assertThat(checkCodeOwnerInfo).isCodeOwner();
    assertThat(checkCodeOwnerInfo)
        .hasAnnotationsThat()
        .containsExactly(CodeOwnerAnnotations.LAST_RESORT_SUGGESTION_ANNOTATION.key())
        .inOrder();
    assertThat(checkCodeOwnerInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "found email %s as a code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath("/foo/bar/")),
            String.format(
                "email %s is annotated with %s",
                codeOwner.email(),
                ImmutableSet.of(
                    CodeOwnerAnnotations.LAST_RESORT_SUGGESTION_ANNOTATION.key(),
                    "OTHER_ANNOTATION")),
            String.format(
                "found the all users wildcard ('%s') as a code owner in %s which makes %s a code"
                    + " owner",
                CodeOwnerResolver.ALL_USERS_WILDCARD,
                getCodeOwnerConfigFilePath("/foo/"),
                codeOwner.email()),
            String.format(
                "found annotations for the all users wildcard ('%s') which apply to %s: %s",
                CodeOwnerResolver.ALL_USERS_WILDCARD,
                codeOwner.email(),
                ImmutableSet.of("ANNOTATION")),
            String.format(
                "found email %s as a code owner in %s",
                codeOwner.email(), getCodeOwnerConfigFilePath("/")),
            String.format(
                "email %s is annotated with %s",
                codeOwner.email(),
                ImmutableSet.of(CodeOwnerAnnotations.LAST_RESORT_SUGGESTION_ANNOTATION.key())),
            String.format(
                "dropping unsupported annotations for %s: %s",
                codeOwner.email(), ImmutableSet.of("ANNOTATION", "OTHER_ANNOTATION")));
  }

  private CodeOwnerCheckInfo checkCodeOwner(String path, String email) throws RestApiException {
    return checkCodeOwner(path, email, /* user= */ null);
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

  private CodeOwnerCheckInfo checkCodeOwnerForChange(
      String path, String email, @Nullable String change) throws RestApiException {
    return projectCodeOwnersApiFactory
        .project(project)
        .branch("master")
        .checkCodeOwner()
        .path(path)
        .email(email)
        .change(change)
        .check();
  }

  private String getCodeOwnerConfigFilePath(String folderPath) {
    if (!folderPath.startsWith("/")) {
      folderPath = "/" + folderPath;
    }
    if (!folderPath.endsWith("/")) {
      folderPath = folderPath + "/";
    }
    return folderPath + getCodeOwnerConfigFileName();
  }

  @CanIgnoreReturnValue
  private CodeOwnerConfig.Key setAsRootCodeOwners(String... emails) {
    TestCodeOwnerConfigCreation.Builder builder =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath(ROOT_PATH);
    Arrays.stream(emails).forEach(builder::addCodeOwnerEmail);
    return builder.create();
  }

  @CanIgnoreReturnValue
  private CodeOwnerConfig.Key setAsDefaultCodeOwner(String email) {
    return codeOwnerConfigOperations
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
                    externalIdFactory.create(
                        "foo",
                        "bar" + accountId.get(),
                        accountId,
                        email,
                        /* hashedPassword= */ null)));
  }
}
