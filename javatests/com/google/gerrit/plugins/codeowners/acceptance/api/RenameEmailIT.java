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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigSubject.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.api.RenameEmailInput;
import com.google.gerrit.plugins.codeowners.api.RenameEmailResultInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigFileUpdateScanner;
import com.google.gerrit.plugins.codeowners.restapi.RenameEmail;
import com.google.inject.Inject;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

/**
 * Acceptance test for the {@link com.google.gerrit.plugins.codeowners.restapi.RenameEmail} REST
 * endpoint.
 *
 * <p>Further tests for the {@link com.google.gerrit.plugins.codeowners.restapi.RenameEmail} REST
 * endpoint that require using the REST API are implemented in {@link
 * com.google.gerrit.plugins.codeowners.acceptance.restapi.RenameEmailRestIT}.
 */
public class RenameEmailIT extends AbstractCodeOwnersIT {
  @Inject private AccountOperations accountOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  private CodeOwnerConfigFileUpdateScanner codeOwnerConfigFileUpdateScanner;

  @Before
  public void setup() throws Exception {
    codeOwnerConfigFileUpdateScanner =
        plugin.getSysInjector().getInstance(CodeOwnerConfigFileUpdateScanner.class);
  }

  @Test
  public void oldEmailIsRequired() throws Exception {
    RenameEmailInput input = new RenameEmailInput();
    input.newEmail = "new@example.com";
    BadRequestException exception =
        assertThrows(BadRequestException.class, () -> renameEmail(project, "master", input));
    assertThat(exception).hasMessageThat().isEqualTo("old email is required");
  }

  @Test
  public void newEmailIsRequired() throws Exception {
    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = "old@example.com";
    BadRequestException exception =
        assertThrows(BadRequestException.class, () -> renameEmail(project, "master", input));
    assertThat(exception).hasMessageThat().isEqualTo("new email is required");
  }

  @Test
  public void oldEmailNotResolvable() throws Exception {
    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = "unknown@example.com";
    input.newEmail = admin.email();
    UnprocessableEntityException exception =
        assertThrows(
            UnprocessableEntityException.class, () -> renameEmail(project, "master", input));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("cannot resolve email %s", input.oldEmail));
  }

  @Test
  public void newEmailNotResolvable() throws Exception {
    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = admin.email();
    input.newEmail = "unknown@example.com";
    UnprocessableEntityException exception =
        assertThrows(
            UnprocessableEntityException.class, () -> renameEmail(project, "master", input));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("cannot resolve email %s", input.newEmail));
  }

  @Test
  public void emailsMustBelongToSameAccount() throws Exception {
    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = admin.email();
    input.newEmail = user.email();
    BadRequestException exception =
        assertThrows(BadRequestException.class, () -> renameEmail(project, "master", input));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "emails must belong to the same account"
                    + " (old email %s is owned by account %d, new email %s is owned by account %d)",
                admin.email(), admin.id().get(), user.email(), user.id().get()));
  }

  @Test
  public void oldAndNewEmailMustDiffer() throws Exception {
    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = admin.email();
    input.newEmail = admin.email();
    BadRequestException exception =
        assertThrows(BadRequestException.class, () -> renameEmail(project, "master", input));
    assertThat(exception).hasMessageThat().isEqualTo("old and new email must differ");
  }

  @Test
  public void requiresAuthenticatedUser() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    AuthException authException =
        assertThrows(
            AuthException.class, () -> renameEmail(project, "master", new RenameEmailInput()));
    assertThat(authException).hasMessageThat().contains("Authentication required");
  }

  @Test
  public void renameEmailRequiresDirectPushPermissionsForNonProjectOwner() throws Exception {
    String secondaryEmail = "user-foo@example.com";
    accountOperations.account(user.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    requestScopeOperations.setApiUser(user.id());
    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = user.email();
    input.newEmail = secondaryEmail;
    AuthException exception =
        assertThrows(AuthException.class, () -> renameEmail(project, "master", input));
    assertThat(exception).hasMessageThat().isEqualTo("not permitted: update on refs/heads/master");
  }

  @Test
  public void renameEmail_noCodeOwnerConfig() throws Exception {
    String secondaryEmail = "user-foo@example.com";
    accountOperations.account(user.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = user.email();
    input.newEmail = secondaryEmail;
    RenameEmailResultInfo result = renameEmail(project, "master", input);
    assertThat(result.commit).isNull();
  }

  @Test
  public void renameEmail_noUpdateIfEmailIsNotContainedInCodeOwnerConfigs() throws Exception {
    skipTestIfRenameEmailNotSupportedByCodeOwnersBackend();

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
        .addCodeOwnerEmail(admin.email())
        .create();

    String secondaryEmail = "user-foo@example.com";
    accountOperations.account(user.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = user.email();
    input.newEmail = secondaryEmail;
    RenameEmailResultInfo result = renameEmail(project, "master", input);
    assertThat(result.commit).isNull();
  }

  @Test
  public void renameOwnEmailWithDirectPushPermission() throws Exception {
    skipTestIfRenameEmailNotSupportedByCodeOwnersBackend();

    CodeOwnerConfig.Key codeOwnerConfigKey1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addCodeOwnerEmail(user.email())
            .create();

    CodeOwnerConfig.Key codeOwnerConfigKey2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addCodeOwnerEmail(user.email())
            .create();

    // grant all users direct push permissions
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/*").group(REGISTERED_USERS))
        .update();

    String secondaryEmail = "user-foo@example.com";
    accountOperations.account(user.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    requestScopeOperations.setApiUser(user.id());
    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = user.email();
    input.newEmail = secondaryEmail;
    RenameEmailResultInfo result = renameEmail(project, "master", input);
    assertThat(result.commit).isNotNull();

    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey1).get())
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(secondaryEmail, admin.email());
    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey2).get())
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(secondaryEmail);
  }

  @Test
  public void renameOtherEmailWithDirectPushPermission() throws Exception {
    skipTestIfRenameEmailNotSupportedByCodeOwnersBackend();

    CodeOwnerConfig.Key codeOwnerConfigKey1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .addCodeOwnerEmail(user.email())
            .create();

    CodeOwnerConfig.Key codeOwnerConfigKey2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addCodeOwnerEmail(admin.email())
            .create();

    // grant all users direct push permissions
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/*").group(REGISTERED_USERS))
        .update();

    // Allow all users to see secondary emails.
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allowCapability(GlobalCapability.VIEW_SECONDARY_EMAILS).group(REGISTERED_USERS))
        .update();

    String secondaryEmail = "admin-foo@example.com";
    accountOperations.account(admin.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    requestScopeOperations.setApiUser(user.id());
    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = admin.email();
    input.newEmail = secondaryEmail;
    RenameEmailResultInfo result = renameEmail(project, "master", input);
    assertThat(result.commit).isNotNull();

    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey1).get())
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(secondaryEmail, user.email());
    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey2).get())
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(secondaryEmail);
  }

  @Test
  public void renameOwnEmailAsProjectOwner() throws Exception {
    skipTestIfRenameEmailNotSupportedByCodeOwnersBackend();

    CodeOwnerConfig.Key codeOwnerConfigKey1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(user.email())
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key codeOwnerConfigKey2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addCodeOwnerEmail(admin.email())
            .create();

    String secondaryEmail = "admin-foo@example.com";
    accountOperations.account(admin.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = admin.email();
    input.newEmail = secondaryEmail;
    RenameEmailResultInfo result = renameEmail(project, "master", input);
    assertThat(result.commit).isNotNull();

    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey1).get())
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(secondaryEmail, user.email());
    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey2).get())
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(secondaryEmail);
  }

  @Test
  public void renameOtherEmailAsProjectOwner() throws Exception {
    skipTestIfRenameEmailNotSupportedByCodeOwnersBackend();

    CodeOwnerConfig.Key codeOwnerConfigKey1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(user.email())
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key codeOwnerConfigKey2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addCodeOwnerEmail(user.email())
            .create();

    String secondaryEmail = "user-foo@example.com";
    accountOperations.account(user.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = user.email();
    input.newEmail = secondaryEmail;
    RenameEmailResultInfo result = renameEmail(project, "master", input);
    assertThat(result.commit).isNotNull();

    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey1).get())
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(secondaryEmail, admin.email());
    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey2).get())
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(secondaryEmail);
  }

  @Test
  public void renameEmail_callingUserBecomesCommitAuthor() throws Exception {
    skipTestIfRenameEmailNotSupportedByCodeOwnersBackend();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .addCodeOwnerEmail(admin.email())
        .create();

    String secondaryEmail = "user-foo@example.com";
    accountOperations.account(user.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = user.email();
    input.newEmail = secondaryEmail;
    RenameEmailResultInfo result = renameEmail(project, "master", input);
    assertThat(result.commit).isNotNull();
    assertThat(result.commit.author.email).isEqualTo(admin.email());
  }

  @Test
  public void renameEmailWithDefaultCommitMessage() throws Exception {
    skipTestIfRenameEmailNotSupportedByCodeOwnersBackend();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .addCodeOwnerEmail(admin.email())
        .create();

    String secondaryEmail = "user-foo@example.com";
    accountOperations.account(user.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = user.email();
    input.newEmail = secondaryEmail;
    RenameEmailResultInfo result = renameEmail(project, "master", input);
    assertThat(result.commit).isNotNull();
    assertThat(result.commit.message).isEqualTo(RenameEmail.DEFAULT_COMMIT_MESSAGE);
  }

  @Test
  public void renameEmailWithSpecifiedCommitMessage() throws Exception {
    skipTestIfRenameEmailNotSupportedByCodeOwnersBackend();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .addCodeOwnerEmail(admin.email())
        .create();

    String secondaryEmail = "user-foo@example.com";
    accountOperations.account(user.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = user.email();
    input.newEmail = secondaryEmail;
    input.message = "Update email with custom message";
    RenameEmailResultInfo result = renameEmail(project, "master", input);
    assertThat(result.commit).isNotNull();
    assertThat(result.commit.message).isEqualTo(input.message);
  }

  @Test
  public void renameEmail_specifiedCommitMessageIsTrimmed() throws Exception {
    skipTestIfRenameEmailNotSupportedByCodeOwnersBackend();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .addCodeOwnerEmail(admin.email())
        .create();

    String secondaryEmail = "user-foo@example.com";
    accountOperations.account(user.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    String message = "Update email with custom message";
    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = user.email();
    input.newEmail = secondaryEmail;
    input.message = "  " + message + "\t";
    RenameEmailResultInfo result = renameEmail(project, "master", input);
    assertThat(result.commit).isNotNull();
    assertThat(result.commit.message).isEqualTo(message);
  }

  @Test
  public void renameEmail_lineCommentsArePreserved() throws Exception {
    skipTestIfRenameEmailNotSupportedByCodeOwnersBackend();

    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(user.email())
            .addCodeOwnerEmail(admin.email())
            .create();

    // insert some comments
    codeOwnerConfigFileUpdateScanner.update(
        BranchNameKey.create(project, "master"),
        "Insert comments",
        (codeOwnerConfigFilePath, codeOwnerConfigFileContent) -> {
          StringBuilder b = new StringBuilder();
          // insert comment line at the top of the file
          b.append("# top comment\n");

          Iterable<String> lines = Splitter.on('\n').split(codeOwnerConfigFileContent);
          b.append(Iterables.get(lines, /* position= */ 0) + "\n");

          // insert comment line in the middle of the file
          b.append("# middle comment\n");

          for (String line : Iterables.skip(lines, /* numberToSkip= */ 1)) {
            b.append(line + "\n");
          }

          // insert comment line at the bottom of the file
          b.append("# bottom comment\n");

          return Optional.of(b.toString());
        });

    String secondaryEmail = "user-foo@example.com";
    accountOperations.account(user.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = user.email();
    input.newEmail = secondaryEmail;
    renameEmail(project, "master", input);

    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).get())
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(secondaryEmail, admin.email());

    // verify that the comments are still present
    String codeOwnerConfigFileContent =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getContent();
    Iterable<String> lines = Splitter.on('\n').split(codeOwnerConfigFileContent);
    assertThat(Iterables.get(lines, /* position= */ 0)).isEqualTo("# top comment");
    assertThat(Iterables.get(lines, /* position= */ 2)).isEqualTo("# middle comment");
    assertThat(Iterables.get(lines, /* position= */ Iterables.size(lines) - 2))
        .isEqualTo("# bottom comment");
  }

  @Test
  public void renameEmail_inlineCommentsArePreserved() throws Exception {
    skipTestIfRenameEmailNotSupportedByCodeOwnersBackend();

    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(user.email())
            .addCodeOwnerEmail(admin.email())
            .create();

    // insert some inline comments
    codeOwnerConfigFileUpdateScanner.update(
        BranchNameKey.create(project, "master"),
        "Insert comments",
        (codeOwnerConfigFilePath, codeOwnerConfigFileContent) -> {
          StringBuilder b = new StringBuilder();
          for (String line : Splitter.on('\n').split(codeOwnerConfigFileContent)) {
            if (line.contains(user.email())) {
              b.append(line + "# some comment\n");
              continue;
            }
            if (line.contains(admin.email())) {
              b.append(line + "# other comment\n");
              continue;
            }
            b.append(line + "\n");
          }

          return Optional.of(b.toString());
        });

    String secondaryEmail = "user-foo@example.com";
    accountOperations.account(user.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = user.email();
    input.newEmail = secondaryEmail;
    renameEmail(project, "master", input);

    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).get())
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(secondaryEmail, admin.email());

    // verify that the inline comments are still present
    String codeOwnerConfigFileContent =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getContent();
    for (String line : Splitter.on('\n').split(codeOwnerConfigFileContent)) {
      if (line.contains(secondaryEmail)) {
        assertThat(line).endsWith("# some comment");
      } else if (line.contains(admin.email())) {
        assertThat(line).endsWith("# other comment");
      }
    }
  }

  @Test
  public void renameEmail_emailInCommentIsReplaced() throws Exception {
    skipTestIfRenameEmailNotSupportedByCodeOwnersBackend();

    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(user.email())
            .addCodeOwnerEmail(admin.email())
            .create();

    // insert some comments
    codeOwnerConfigFileUpdateScanner.update(
        BranchNameKey.create(project, "master"),
        "Insert comments",
        (codeOwnerConfigFilePath, codeOwnerConfigFileContent) ->
            Optional.of("# foo " + user.email() + " bar\n" + codeOwnerConfigFileContent));

    String secondaryEmail = "user-foo@example.com";
    accountOperations.account(user.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = user.email();
    input.newEmail = secondaryEmail;
    renameEmail(project, "master", input);

    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).get())
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(secondaryEmail, admin.email());

    // verify that the comments are still present
    String codeOwnerConfigFileContent =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getContent();
    assertThat(
            Iterables.get(Splitter.on('\n').split(codeOwnerConfigFileContent), /* position= */ 0))
        .endsWith("# foo " + secondaryEmail + " bar");
  }

  @Test
  public void renameEmail_emailThatContainsEmailToBeReplacesAsSubstringStaysIntact()
      throws Exception {
    skipTestIfRenameEmailNotSupportedByCodeOwnersBackend();

    TestAccount otherUser1 =
        accountCreator.create(
            "otherUser1", "foo" + user.email(), "Other User 1", /* displayName= */ null);
    TestAccount otherUser2 =
        accountCreator.create(
            "otherUser2", user.email() + "bar", "Other User 2", /* displayName= */ null);
    TestAccount otherUser3 =
        accountCreator.create(
            "otherUser3", "foo" + user.email() + "bar", "Other User 3", /* displayName= */ null);

    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .addCodeOwnerEmail(user.email())
            .addCodeOwnerEmail(otherUser1.email())
            .addCodeOwnerEmail(otherUser2.email())
            .addCodeOwnerEmail(otherUser3.email())
            .create();

    // grant all users direct push permissions
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/*").group(REGISTERED_USERS))
        .update();

    String secondaryEmail = "user-new@example.com";
    accountOperations.account(user.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    requestScopeOperations.setApiUser(user.id());
    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = user.email();
    input.newEmail = secondaryEmail;
    RenameEmailResultInfo result = renameEmail(project, "master", input);
    assertThat(result.commit).isNotNull();

    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).get())
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(
            secondaryEmail, otherUser1.email(), otherUser2.email(), otherUser3.email());
  }

  private RenameEmailResultInfo renameEmail(
      Project.NameKey projectName, String branchName, RenameEmailInput input)
      throws RestApiException {
    return projectCodeOwnersApiFactory
        .project(projectName)
        .branch(branchName)
        .renameEmailInCodeOwnerConfigFiles(input);
  }

  private void skipTestIfRenameEmailNotSupportedByCodeOwnersBackend() {
    // the proto backend doesn't support renaming emails
    assumeThatCodeOwnersBackendIsNotProtoBackend();
  }
}
