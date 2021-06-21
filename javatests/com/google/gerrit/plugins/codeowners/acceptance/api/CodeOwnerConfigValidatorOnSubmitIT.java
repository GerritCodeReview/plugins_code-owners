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
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.backend.config.BackendConfig;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersCodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoCodeOwnerConfigParser;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@code com.google.gerrit.plugins.codeowners.validation.CodeOwnerConfigValidator} that
 * verify the validation on submit.
 */
public class CodeOwnerConfigValidatorOnSubmitIT extends AbstractCodeOwnersIT {
  private static final ObjectId TEST_REVISION =
      ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  private FindOwnersCodeOwnerConfigParser findOwnersCodeOwnerConfigParser;
  private ProtoCodeOwnerConfigParser protoCodeOwnerConfigParser;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    backendConfig = plugin.getSysInjector().getInstance(BackendConfig.class);
    findOwnersCodeOwnerConfigParser =
        plugin.getSysInjector().getInstance(FindOwnersCodeOwnerConfigParser.class);
    protoCodeOwnerConfigParser =
        plugin.getSysInjector().getInstance(ProtoCodeOwnerConfigParser.class);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "true")
  public void canSubmitConfigWithoutIssues() throws Exception {
    setAsDefaultCodeOwners(admin);

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // Create a code owner config without issues.
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
                    .build()));
    r.assertOkStatus();

    // Approve and submit the change.
    approve(r.getChangeId());
    gApi.changes().id(r.getChangeId()).current().submit();
    assertThat(gApi.changes().id(r.getChangeId()).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "true")
  public void cannotSubmitConfigWithNewIssues() throws Exception {
    setAsDefaultCodeOwners(admin);

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // disable the code owners functionality so that we can upload a a change with a code owner
    // config that has issues
    disableCodeOwnersForProject(project);

    // upload a change with a code owner config that has issues (non-resolvable code owners)
    String unknownEmail = "non-existing-email@example.com";
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(unknownEmail))
                    .build()));
    r.assertOkStatus();

    // re-enable the code owners functionality for the project
    enableCodeOwnersForProject(project);

    // approve the change
    approve(r.getChangeId());

    // try to submit the change
    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(r.getChangeId()).current().submit());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Failed to submit 1 change due to the following problems:\n"
                    + "Change %d: [code-owners] invalid code owner config files:\n"
                    + "  ERROR: code owner email '%s' in '%s' cannot be resolved for %s",
                r.getChange().getId().get(),
                unknownEmail,
                codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
                identifiedUserFactory.create(admin.id()).getLoggableName()));
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "true")
  public void cannotSubmitConfigWithCodeOwnersThatAreNotVisibleToThePatchSetUploader()
      throws Exception {
    setAsDefaultCodeOwners(admin);

    // Create a new user that is not a member of any group. This means 'user' and 'admin' are not
    // visible to this user since they do not share any group.
    TestAccount user2 = accountCreator.user2();

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // disable the code owners functionality so that we can upload a change with a code owner
    // config that has issues
    disableCodeOwnersForProject(project);

    // upload a change as user2 with a code owner config that contains a code owner that is not
    // visible to user2
    PushOneCommit.Result r =
        createChange(
            user2,
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
                    .build()));
    r.assertOkStatus();

    // re-enable the code owners functionality for the project
    enableCodeOwnersForProject(project);

    // approve the change
    approve(r.getChangeId());

    // try to submit the change as admin who can see the code owners in the config, the submit still
    // fails because it is checked that the uploader (user2) can see the code owners
    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(r.getChangeId()).current().submit());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Failed to submit 1 change due to the following problems:\n"
                    + "Change %d: [code-owners] invalid code owner config files:\n"
                    + "  ERROR: code owner email '%s' in '%s' cannot be resolved for %s",
                r.getChange().getId().get(),
                admin.email(),
                codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
                identifiedUserFactory.create(user2.id()).getLoggableName()));
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "true")
  public void canSubmitConfigWithCodeOwnersThatAreNotVisibleToTheSubmitterButVisibleToTheUploader()
      throws Exception {
    setAsDefaultCodeOwners(admin);

    // Create a new user that is not a member of any group. This means 'user' and 'admin' are not
    // visible to this user since they do not share any group.
    TestAccount user2 = accountCreator.user2();

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // upload a change as admin with a code owner config that contains a code owner that is not
    // visible to user2
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(user.email()))
                    .build()));
    r.assertOkStatus();

    // approve the change
    approve(r.getChangeId());

    // grant user2 submit permissions
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.SUBMIT).ref("refs/*").group(REGISTERED_USERS))
        .update();

    // submit the change as user2 who cannot see the code owners in the config, the submit succeeds
    // because it is checked that the uploader (admin) can see the code owners
    requestScopeOperations.setApiUser(user2.id());
    gApi.changes().id(r.getChangeId()).current().submit();
    assertThat(gApi.changes().id(r.getChangeId()).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "false")
  public void canSubmitNonParseableConfigIfValidationIsDisabled() throws Exception {
    testCanSubmitNonParseableConfig();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "dry_run")
  public void canSubmitNonParseableConfigIfValidationIsDoneAsDryRun() throws Exception {
    testCanSubmitNonParseableConfig();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "forced_dry_run")
  public void canSubmitNonParseableConfigIfValidationIsDoneAsForcedDryRun() throws Exception {
    disableCodeOwnersForProject(project);
    testCanSubmitNonParseableConfig();
  }

  private void testCanSubmitNonParseableConfig() throws Exception {
    setAsDefaultCodeOwners(admin);

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // disable the code owners functionality so that we can upload a non-parseable code owner config
    // that we then try to submit
    disableCodeOwnersForProject(project);

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            "INVALID");
    r.assertOkStatus();

    // re-enable the code owners functionality for the project
    enableCodeOwnersForProject(project);

    // submit the change
    approve(r.getChangeId());
    gApi.changes().id(r.getChangeId()).current().submit();
    assertThat(gApi.changes().id(r.getChangeId()).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "forced")
  public void
      cannotSubmitConfigWithIssuesIfCodeOwnersFunctionalityIsDisabledButValidationIsEnforced()
          throws Exception {
    disableCodeOwnersForProject(project);

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // upload a change with a code owner config that has issues (non-resolvable code owners)
    String unknownEmail = "non-existing-email@example.com";
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(unknownEmail))
                    .build()));
    r.assertOkStatus();

    // approve the change
    approve(r.getChangeId());

    // try to submit the change
    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(r.getChangeId()).current().submit());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Failed to submit 1 change due to the following problems:\n"
                    + "Change %d: [code-owners] invalid code owner config files:\n"
                    + "  ERROR: code owner email '%s' in '%s' cannot be resolved for %s",
                r.getChange().getId().get(),
                unknownEmail,
                codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
                identifiedUserFactory.create(admin.id()).getLoggableName()));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "false")
  public void canSubmitConfigWithIssuesIfValidationIsDisabled() throws Exception {
    testCanSubmitConfigWithIssues();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "dry_run")
  public void canSubmitConfigWithIssuesIfValidationIsDoneAsDryRun() throws Exception {
    testCanSubmitConfigWithIssues();
  }

  private void testCanSubmitConfigWithIssues() throws Exception {
    setAsDefaultCodeOwners(admin);

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // disable the code owners functionality so that we can upload a code owner config with issues
    // that we then try to submit
    disableCodeOwnersForProject(project);

    // upload a code owner config that has issues (non-resolvable code owners)
    String unknownEmail1 = "non-existing-email@example.com";
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(unknownEmail1))
                    .build()));
    r.assertOkStatus();

    // re-enable the code owners functionality for the project
    enableCodeOwnersForProject(project);

    // submit the change
    approve(r.getChangeId());
    gApi.changes().id(r.getChangeId()).current().submit();
    assertThat(gApi.changes().id(r.getChangeId()).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  private CodeOwnerConfig.Key createCodeOwnerConfigKey(String folderPath) {
    return CodeOwnerConfig.Key.create(project, "master", folderPath);
  }

  private String format(CodeOwnerConfig codeOwnerConfig) throws Exception {
    if (backendConfig.getDefaultBackend() instanceof FindOwnersBackend) {
      return findOwnersCodeOwnerConfigParser.formatAsString(codeOwnerConfig);
    } else if (backendConfig.getDefaultBackend() instanceof ProtoBackend) {
      return protoCodeOwnerConfigParser.formatAsString(codeOwnerConfig);
    }

    throw new IllegalStateException(
        String.format(
            "unknown code owner backend: %s",
            backendConfig.getDefaultBackend().getClass().getName()));
  }
}
