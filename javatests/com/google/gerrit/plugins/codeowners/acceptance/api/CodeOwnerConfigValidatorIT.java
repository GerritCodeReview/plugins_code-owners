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
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.git.ObjectIds;
import com.google.gerrit.plugins.codeowners.JgitPath;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportMode;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportType;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import com.google.gerrit.plugins.codeowners.config.BackendConfig;
import com.google.gerrit.plugins.codeowners.config.StatusConfig;
import com.google.gerrit.plugins.codeowners.testing.backend.TestCodeOwnerConfigFormatter;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@code com.google.gerrit.plugins.codeowners.validation.CodeOwnerConfigValidator}. */
public class CodeOwnerConfigValidatorIT extends AbstractCodeOwnersIT {
  private static final ObjectId TEST_REVISION =
      ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

  @Inject private ProjectOperations projectOperations;

  private BackendConfig backendConfig;
  private TestCodeOwnerConfigFormatter testCodeOwnerConfigFormatter;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    backendConfig = plugin.getSysInjector().getInstance(BackendConfig.class);
    testCodeOwnerConfigFormatter =
        plugin.getSysInjector().getInstance(TestCodeOwnerConfigFormatter.class);
  }

  @Test
  public void nonCodeOwnerConfigFileIsNotValidated() throws Exception {
    PushOneCommit.Result r = createChange("Add arbitrary file", "arbitrary-file.txt", "INVALID");
    r.assertOkStatus();
  }

  @Test
  public void canUploadConfigWithoutIssues() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // Create a code owner config without issues.
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(codeOwnerConfigKey)).get(),
            testCodeOwnerConfigFormatter.format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
                    .build()));
    r.assertOkStatus();
    r.assertNotMessage("error");
    r.assertNotMessage("warning");
  }

  @Test
  public void canSubmitConfigWithoutIssues() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // Create a code owner config without issues.
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(codeOwnerConfigKey)).get(),
            testCodeOwnerConfigFormatter.format(
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
  public void canUploadConfigWithoutIssues_withImports() throws Exception {
    // imports are not supported for the proto backend
    assume().that(backendConfig.getDefaultBackend()).isNotInstanceOf(ProtoBackend.class);

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // Create a code owner config to be imported.
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addCodeOwnerEmail(user.email())
            .create();

    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
            getCodeOwnerConfigFilePath(keyOfImportedCodeOwnerConfig));

    // Create a code owner config with imports and without issues.
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(codeOwnerConfigKey)).get(),
            testCodeOwnerConfigFormatter.format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addImport(codeOwnerConfigReference)
                    .addCodeOwnerSet(
                        CodeOwnerSet.builder()
                            .addCodeOwnerEmail(admin.email())
                            .addPathExpression("foo")
                            .addImport(codeOwnerConfigReference)
                            .build())
                    .build()));
    r.assertOkStatus();
    r.assertNotMessage("error");
    r.assertNotMessage("warning");
  }

  @Test
  public void canUploadNonParseableConfigIfCodeOwnersFunctionalityIsDisabled() throws Exception {
    disableCodeOwnersForProject(project);

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(createCodeOwnerConfigKey("/"))).get(),
            "INVALID");
    r.assertOkStatus();
  }

  @Test
  public void canUploadNonParseableConfigIfItWasAlreadyNonParseable() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // disable the code owners functionality so that we can upload an initial code owner config that
    // is not parseable
    disableCodeOwnersForProject(project);

    // upload an initial code owner config that is not parseable
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(codeOwnerConfigKey)).get(),
            "INVALID");
    r.assertOkStatus();

    // re-enable the code owners functionality for the project
    setCodeOwnersConfig(project, null, StatusConfig.KEY_DISABLED, "false");

    // update the code owner config so that it is still not parseable
    r =
        createChange(
            "Update code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(codeOwnerConfigKey)).get(),
            "STILL INVALID");
    r.assertOkStatus();
    r.assertMessage(
        String.format(
            "warning: commit %s: invalid code owner config file '%s':\n  %s",
            abbreviateName(r.getCommit()),
            getCodeOwnerConfigFilePath(codeOwnerConfigKey),
            getParsingErrorMessage(
                ImmutableMap.of(
                    FindOwnersBackend.class,
                    "invalid line: STILL INVALID",
                    ProtoBackend.class,
                    "1:7: expected \"{\""))));
  }

  @Test
  public void canUploadConfigWithIssuesIfItWasNonParseableBefore() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // disable the code owners functionality so that we can upload an initial code owner config that
    // is not parseable
    disableCodeOwnersForProject(project);

    // upload an initial code owner config that is not parseable
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(codeOwnerConfigKey)).get(),
            "INVALID");
    r.assertOkStatus();

    // re-enable the code owners functionality for the project
    setCodeOwnersConfig(project, null, StatusConfig.KEY_DISABLED, "false");

    // update the code owner config so that it is parseable now, but has validation issues
    String unknownEmail1 = "non-existing-email@example.com";
    String unknownEmail2 = "another-unknown-email@example.com";
    r =
        createChange(
            "Update code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(codeOwnerConfigKey)).get(),
            testCodeOwnerConfigFormatter.format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(
                        CodeOwnerSet.createWithoutPathExpressions(
                            unknownEmail1, admin.email(), unknownEmail2))
                    .build()));
    r.assertOkStatus();
    r.assertMessage(
        String.format(
            "warning: commit %s: code owner email '%s' in '%s' cannot be resolved for %s",
            abbreviateName(r.getCommit()),
            unknownEmail1,
            getCodeOwnerConfigFilePath(codeOwnerConfigKey),
            identifiedUserFactory.create(admin.id()).getLoggableName()));
    r.assertMessage(
        String.format(
            "warning: commit %s: code owner email '%s' in '%s' cannot be resolved for %s",
            abbreviateName(r.getCommit()),
            unknownEmail2,
            getCodeOwnerConfigFilePath(codeOwnerConfigKey),
            identifiedUserFactory.create(admin.id()).getLoggableName()));
  }

  @Test
  public void canUploadConfigWithIssuesIfTheyExistedBefore() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // disable the code owners functionality so that we can upload an initial code owner config that
    // has issues
    disableCodeOwnersForProject(project);

    // upload an initial code owner config that has issues (non-resolvable code owners)
    String unknownEmail = "non-existing-email@example.com";
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(codeOwnerConfigKey)).get(),
            testCodeOwnerConfigFormatter.format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(unknownEmail))
                    .build()));
    r.assertOkStatus();

    // re-enable the code owners functionality for the project
    setCodeOwnersConfig(project, null, StatusConfig.KEY_DISABLED, "false");

    // update the code owner config so that the validation issue still exists, but no new issue is
    // introduced
    r =
        createChange(
            "Update code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(codeOwnerConfigKey)).get(),
            testCodeOwnerConfigFormatter.format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(
                        CodeOwnerSet.createWithoutPathExpressions(unknownEmail, admin.email()))
                    .build()));
    r.assertOkStatus();
    r.assertMessage(
        String.format(
            "warning: commit %s: code owner email '%s' in '%s' cannot be resolved for %s",
            abbreviateName(r.getCommit()),
            unknownEmail,
            getCodeOwnerConfigFilePath(codeOwnerConfigKey),
            identifiedUserFactory.create(admin.id()).getLoggableName()));
  }

  @Test
  public void cannotUploadNonParseableConfig() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(codeOwnerConfigKey)).get(),
            "INVALID");
    r.assertErrorStatus("invalid code owner config files");
    r.assertMessage(
        String.format(
            "error: commit %s: invalid code owner config file '%s':\n  %s",
            abbreviateName(r.getCommit()),
            getCodeOwnerConfigFilePath(codeOwnerConfigKey),
            getParsingErrorMessage(
                ImmutableMap.of(
                    FindOwnersBackend.class,
                    "invalid line: INVALID",
                    ProtoBackend.class,
                    "1:8: expected \"{\""))));
  }

  @Test
  public void issuesAreReportedForAllInvalidConfigs() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey1 = createCodeOwnerConfigKey("/");
    CodeOwnerConfig.Key codeOwnerConfigKey2 = createCodeOwnerConfigKey("/foo/bar/");

    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Add code owners",
            ImmutableMap.of(
                JgitPath.of(getCodeOwnerConfigFilePath(codeOwnerConfigKey1)).get(),
                "INVALID",
                JgitPath.of(getCodeOwnerConfigFilePath(codeOwnerConfigKey2)).get(),
                "ALSO-INVALID"));
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertErrorStatus("invalid code owner config files");
    r.assertMessage(
        String.format(
            "error: commit %s: invalid code owner config file '%s':\n  %s",
            abbreviateName(r.getCommit()),
            getCodeOwnerConfigFilePath(codeOwnerConfigKey1),
            getParsingErrorMessage(
                ImmutableMap.of(
                    FindOwnersBackend.class,
                    "invalid line: INVALID",
                    ProtoBackend.class,
                    "1:8: expected \"{\""))));
    r.assertMessage(
        String.format(
            "error: commit %s: invalid code owner config file '%s':\n  %s",
            abbreviateName(r.getCommit()),
            getCodeOwnerConfigFilePath(codeOwnerConfigKey2),
            getParsingErrorMessage(
                ImmutableMap.of(
                    FindOwnersBackend.class,
                    "invalid line: ALSO-INVALID",
                    ProtoBackend.class,
                    "1:1: expected identifier. found 'ALSO-INVALID'"))));
  }

  @Test
  public void cannotUploadConfigWithNonResolvableCodeOwners() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    String unknownEmail1 = "non-existing-email@example.com";
    String unknownEmail2 = "another-unknown-email@example.com";
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(codeOwnerConfigKey)).get(),
            testCodeOwnerConfigFormatter.format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(
                        CodeOwnerSet.createWithoutPathExpressions(
                            unknownEmail1, admin.email(), unknownEmail2))
                    .build()));
    r.assertErrorStatus("invalid code owner config files");
    r.assertMessage(
        String.format(
            "error: commit %s: code owner email '%s' in '%s' cannot be resolved for %s",
            abbreviateName(r.getCommit()),
            unknownEmail1,
            getCodeOwnerConfigFilePath(codeOwnerConfigKey),
            identifiedUserFactory.create(admin.id()).getLoggableName()));
    r.assertMessage(
        String.format(
            "error: commit %s: code owner email '%s' in '%s' cannot be resolved for %s",
            abbreviateName(r.getCommit()),
            unknownEmail2,
            getCodeOwnerConfigFilePath(codeOwnerConfigKey),
            identifiedUserFactory.create(admin.id()).getLoggableName()));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.allowedEmailDomain", value = "example.com")
  public void cannotUploadConfigThatAssignsCodeOwnershipToAnEmailWithANonAllowedEmailDomain()
      throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    String emailWithNonAllowedDomain = "foo@example.net";
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(codeOwnerConfigKey)).get(),
            testCodeOwnerConfigFormatter.format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(
                        CodeOwnerSet.createWithoutPathExpressions(
                            emailWithNonAllowedDomain, admin.email()))
                    .build()));
    r.assertErrorStatus("invalid code owner config files");
    r.assertMessage(
        String.format(
            "error: commit %s: the domain of the code owner email '%s' in '%s' is not allowed for"
                + " code owners",
            abbreviateName(r.getCommit()),
            emailWithNonAllowedDomain,
            getCodeOwnerConfigFilePath(codeOwnerConfigKey)));
  }

  @Test
  public void cannotUploadConfigWithNewIssues() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // disable the code owners functionality so that we can upload an initial code owner config that
    // has issues
    disableCodeOwnersForProject(project);

    // upload an initial code owner config that has issues (non-resolvable code owners)
    String unknownEmail1 = "non-existing-email@example.com";
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(codeOwnerConfigKey)).get(),
            testCodeOwnerConfigFormatter.format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(unknownEmail1))
                    .build()));
    r.assertOkStatus();

    // re-enable the code owners functionality for the project
    setCodeOwnersConfig(project, null, StatusConfig.KEY_DISABLED, "false");

    // update the code owner config so that the validation issue still exists and a new issue is
    // introduced
    String unknownEmail2 = "another-unknown-email@example.com";
    r =
        createChange(
            "Update code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(codeOwnerConfigKey)).get(),
            testCodeOwnerConfigFormatter.format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(
                        CodeOwnerSet.createWithoutPathExpressions(unknownEmail1, unknownEmail2))
                    .build()));
    r.assertErrorStatus("invalid code owner config files");
    r.assertMessage(
        String.format(
            "warning: commit %s: code owner email '%s' in '%s' cannot be resolved for %s",
            abbreviateName(r.getCommit()),
            unknownEmail1,
            getCodeOwnerConfigFilePath(codeOwnerConfigKey),
            identifiedUserFactory.create(admin.id()).getLoggableName()));
    r.assertMessage(
        String.format(
            "error: commit %s: code owner email '%s' in '%s' cannot be resolved for %s",
            abbreviateName(r.getCommit()),
            unknownEmail2,
            getCodeOwnerConfigFilePath(codeOwnerConfigKey),
            identifiedUserFactory.create(admin.id()).getLoggableName()));
  }

  @Test
  public void cannotSubmitConfigWithNewIssues() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // disable the code owners functionality so that we can upload a a change with a code owner
    // config that has issues
    disableCodeOwnersForProject(project);

    // upload a change with a code owner config that has issues (non-resolvable code owners)
    String unknownEmail = "non-existing-email@example.com";
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(codeOwnerConfigKey)).get(),
            testCodeOwnerConfigFormatter.format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(unknownEmail))
                    .build()));
    r.assertOkStatus();

    // re-enable the code owners functionality for the project
    setCodeOwnersConfig(project, null, StatusConfig.KEY_DISABLED, "false");

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
                    + "Change %d: invalid code owner config files:\n"
                    + "  ERROR: code owner email '%s' in '%s' cannot be resolved for %s",
                r.getChange().getId().get(),
                unknownEmail,
                getCodeOwnerConfigFilePath(codeOwnerConfigKey),
                identifiedUserFactory.create(admin.id()).getLoggableName()));
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void cannotSubmitConfigWithCodeOwnersThatAreNotVisibleToThePatchSetUploader()
      throws Exception {
    // Create a new user that is not a member of any group. This means 'user' and 'admin' are not
    // visible to this user since they do not share any group.
    TestAccount user2 = accountCreator.user2();

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // disable the code owners functionality so that we can upload a a change with a code owner
    // config that has issues
    disableCodeOwnersForProject(project);

    // upload a change as user2 with a code owner config that contains a code owner that is not
    // visible to user2
    PushOneCommit.Result r =
        createChange(
            user2,
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(codeOwnerConfigKey)).get(),
            testCodeOwnerConfigFormatter.format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
                    .build()));
    r.assertOkStatus();

    // re-enable the code owners functionality for the project
    setCodeOwnersConfig(project, null, StatusConfig.KEY_DISABLED, "false");

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
                    + "Change %d: invalid code owner config files:\n"
                    + "  ERROR: code owner email '%s' in '%s' cannot be resolved for %s",
                r.getChange().getId().get(),
                admin.email(),
                getCodeOwnerConfigFilePath(codeOwnerConfigKey),
                identifiedUserFactory.create(user2.id()).getLoggableName()));
  }

  @Test
  public void cannotUploadConfigWithGlobalImportFromNonExistingProject() throws Exception {
    testUploadConfigWithImportFromNonExistingProject(CodeOwnerConfigImportType.GLOBAL);
  }

  @Test
  public void cannotUploadConfigWithPerFileImportFromNonExistingProject() throws Exception {
    testUploadConfigWithImportFromNonExistingProject(CodeOwnerConfigImportType.PER_FILE);
  }

  private void testUploadConfigWithImportFromNonExistingProject(
      CodeOwnerConfigImportType importType) throws Exception {
    // imports are not supported for the proto backend
    assume().that(backendConfig.getDefaultBackend()).isNotInstanceOf(ProtoBackend.class);

    // create a code owner config that imports a code owner config from a non-existing project
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");
    Project.NameKey nonExistingProject = Project.nameKey("non-existing");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                getCodeOwnerConfigFilePath(
                    CodeOwnerConfig.Key.create(nonExistingProject, "master", "/")))
            .setProject(nonExistingProject)
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig, importType, codeOwnerConfigReference);

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(keyOfImportingCodeOwnerConfig)).get(),
            testCodeOwnerConfigFormatter.format(codeOwnerConfig));
    r.assertErrorStatus("invalid code owner config files");
    r.assertMessage(
        String.format(
            "error: commit %s: invalid %s import in '%s': project '%s' not found",
            abbreviateName(r.getCommit()),
            importType.getType(),
            getCodeOwnerConfigFilePath(keyOfImportingCodeOwnerConfig),
            nonExistingProject.get()));
  }

  @Test
  public void cannotUploadConfigWithGlobalImportFromNonVisibleProject() throws Exception {
    testUploadConfigWithImportFromNonVisibleProject(CodeOwnerConfigImportType.GLOBAL);
  }

  @Test
  public void cannotUploadConfigWithPerFileImportFromNonVisibleProject() throws Exception {
    testUploadConfigWithImportFromNonVisibleProject(CodeOwnerConfigImportType.PER_FILE);
  }

  private void testUploadConfigWithImportFromNonVisibleProject(CodeOwnerConfigImportType importType)
      throws Exception {
    // imports are not supported for the proto backend
    assume().that(backendConfig.getDefaultBackend()).isNotInstanceOf(ProtoBackend.class);

    // create a non-visible project with a code owner config file that we try to import
    Project.NameKey nonVisibleProject =
        projectOperations.newProject().name(name("non-visible-project")).create();
    projectOperations
        .project(nonVisibleProject)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(nonVisibleProject)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();

    // create a code owner config that imports a code owner config from a non-visible project
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                getCodeOwnerConfigFilePath(
                    CodeOwnerConfig.Key.create(nonVisibleProject, "master", "/")))
            .setProject(nonVisibleProject)
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig, importType, codeOwnerConfigReference);

    PushOneCommit.Result r =
        createChange(
            user,
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(keyOfImportingCodeOwnerConfig)).get(),
            testCodeOwnerConfigFormatter.format(codeOwnerConfig));
    r.assertErrorStatus("invalid code owner config files");
    r.assertMessage(
        String.format(
            "error: commit %s: invalid %s import in '%s': project '%s' not found",
            abbreviateName(r.getCommit()),
            importType.getType(),
            getCodeOwnerConfigFilePath(keyOfImportingCodeOwnerConfig),
            nonVisibleProject.get()));
  }

  @Test
  public void cannotUploadConfigWithGlobalImportFromHiddenProject() throws Exception {
    testUploadConfigWithImportFromHiddenProject(CodeOwnerConfigImportType.GLOBAL);
  }

  @Test
  public void cannotUploadConfigWithPerFileImportFromHiddenProject() throws Exception {
    testUploadConfigWithImportFromHiddenProject(CodeOwnerConfigImportType.PER_FILE);
  }

  private void testUploadConfigWithImportFromHiddenProject(CodeOwnerConfigImportType importType)
      throws Exception {
    // imports are not supported for the proto backend
    assume().that(backendConfig.getDefaultBackend()).isNotInstanceOf(ProtoBackend.class);

    // create a hidden project with a code owner config file
    Project.NameKey hiddenProject =
        projectOperations.newProject().name(name("hidden-project")).create();
    ConfigInput configInput = new ConfigInput();
    configInput.state = ProjectState.HIDDEN;
    gApi.projects().name(hiddenProject.get()).config(configInput);
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(hiddenProject)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();

    // create a code owner config that imports a code owner config from a hidden project
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                getCodeOwnerConfigFilePath(
                    CodeOwnerConfig.Key.create(hiddenProject, "master", "/")))
            .setProject(hiddenProject)
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig, importType, codeOwnerConfigReference);

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(keyOfImportingCodeOwnerConfig)).get(),
            testCodeOwnerConfigFormatter.format(codeOwnerConfig));
    r.assertErrorStatus("invalid code owner config files");
    r.assertMessage(
        String.format(
            "error: commit %s: invalid %s import in '%s':"
                + " project '%s' has state 'hidden' that doesn't permit read",
            abbreviateName(r.getCommit()),
            importType.getType(),
            getCodeOwnerConfigFilePath(keyOfImportingCodeOwnerConfig),
            hiddenProject.get()));
  }

  @Test
  public void cannotUploadConfigWithGlobalImportFromNonExistingBranch() throws Exception {
    testUploadConfigWithImportFromNonExistingBranch(CodeOwnerConfigImportType.GLOBAL);
  }

  @Test
  public void cannotUploadConfigWithPerFileImportFromNonExistingBranch() throws Exception {
    testUploadConfigWithImportFromNonExistingBranch(CodeOwnerConfigImportType.PER_FILE);
  }

  private void testUploadConfigWithImportFromNonExistingBranch(CodeOwnerConfigImportType importType)
      throws Exception {
    // imports are not supported for the proto backend
    assume().that(backendConfig.getDefaultBackend()).isNotInstanceOf(ProtoBackend.class);

    // create a code owner config that imports a code owner config from a non-existing branch
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");
    Project.NameKey otherProject = projectOperations.newProject().name(name("other")).create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                getCodeOwnerConfigFilePath(
                    CodeOwnerConfig.Key.create(otherProject, "non-existing", "/")))
            .setProject(otherProject)
            .setBranch("non-existing")
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig, importType, codeOwnerConfigReference);

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(keyOfImportingCodeOwnerConfig)).get(),
            testCodeOwnerConfigFormatter.format(codeOwnerConfig));
    r.assertErrorStatus("invalid code owner config files");
    r.assertMessage(
        String.format(
            "error: commit %s: invalid %s import in '%s':"
                + " branch 'non-existing' not found in project '%s'",
            abbreviateName(r.getCommit()),
            importType.getType(),
            getCodeOwnerConfigFilePath(keyOfImportingCodeOwnerConfig),
            otherProject.get()));
  }

  @Test
  public void cannotUploadConfigWithGlobalImportFromNonVisibleBranch() throws Exception {
    testUploadConfigWithImportFromNonVisibleBranch(CodeOwnerConfigImportType.GLOBAL);
  }

  @Test
  public void cannotUploadConfigWithPerFileImportFromNonVisibleBranch() throws Exception {
    testUploadConfigWithImportFromNonVisibleBranch(CodeOwnerConfigImportType.PER_FILE);
  }

  private void testUploadConfigWithImportFromNonVisibleBranch(CodeOwnerConfigImportType importType)
      throws Exception {
    // imports are not supported for the proto backend
    assume().that(backendConfig.getDefaultBackend()).isNotInstanceOf(ProtoBackend.class);

    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");

    // create a project with a non-visible branch that contains a code owner config file
    Project.NameKey otherProject =
        projectOperations.newProject().name(name("non-visible-project")).create();
    projectOperations
        .project(otherProject)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/heads/master").group(REGISTERED_USERS))
        .update();
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(otherProject)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();

    // create a code owner config that imports a code owner config from a non-visible branch
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                getCodeOwnerConfigFilePath(CodeOwnerConfig.Key.create(otherProject, "master", "/")))
            .setProject(otherProject)
            .setBranch("master")
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig, importType, codeOwnerConfigReference);

    PushOneCommit.Result r =
        createChange(
            user,
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(keyOfImportingCodeOwnerConfig)).get(),
            testCodeOwnerConfigFormatter.format(codeOwnerConfig));
    r.assertErrorStatus("invalid code owner config files");
    r.assertMessage(
        String.format(
            "error: commit %s: invalid %s import in '%s':"
                + " branch 'master' not found in project '%s'",
            abbreviateName(r.getCommit()),
            importType.getType(),
            getCodeOwnerConfigFilePath(keyOfImportingCodeOwnerConfig),
            otherProject.get()));
  }

  @Test
  public void cannotUploadConfigWithGlobalImportOfNonCodeOwnerConfigFile() throws Exception {
    testUploadConfigWithImportOfNonCodeOwnerConfigFile(CodeOwnerConfigImportType.GLOBAL);
  }

  @Test
  public void cannotUploadConfigWithPerFileImportOfNonCodeOwnerConfigFile() throws Exception {
    testUploadConfigWithImportOfNonCodeOwnerConfigFile(CodeOwnerConfigImportType.PER_FILE);
  }

  private void testUploadConfigWithImportOfNonCodeOwnerConfigFile(
      CodeOwnerConfigImportType importType) throws Exception {
    // imports are not supported for the proto backend
    assume().that(backendConfig.getDefaultBackend()).isNotInstanceOf(ProtoBackend.class);

    // create a code owner config that imports a non code owner config file
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "non-code-owner-config.txt")
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig, importType, codeOwnerConfigReference);

    PushOneCommit.Result r =
        createChange(
            user,
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(keyOfImportingCodeOwnerConfig)).get(),
            testCodeOwnerConfigFormatter.format(codeOwnerConfig));
    r.assertErrorStatus("invalid code owner config files");
    r.assertMessage(
        String.format(
            "error: commit %s: invalid %s import in '%s':"
                + " 'non-code-owner-config.txt' is not a code owner config file",
            abbreviateName(r.getCommit()),
            importType.getType(),
            getCodeOwnerConfigFilePath(keyOfImportingCodeOwnerConfig)));
  }

  @Test
  public void cannotUploadConfigWithGlobalImportOfNonExistingCodeOwnerConfig() throws Exception {
    testUploadConfigWithImportOfNonExistingCodeOwnerConfig(CodeOwnerConfigImportType.GLOBAL);
  }

  @Test
  public void cannotUploadConfigWithPerFileImportOfNonExistingCodeOwnerConfig() throws Exception {
    testUploadConfigWithImportOfNonExistingCodeOwnerConfig(CodeOwnerConfigImportType.PER_FILE);
  }

  private void testUploadConfigWithImportOfNonExistingCodeOwnerConfig(
      CodeOwnerConfigImportType importType) throws Exception {
    // imports are not supported for the proto backend
    assume().that(backendConfig.getDefaultBackend()).isNotInstanceOf(ProtoBackend.class);

    // create a code owner config that imports a non-existing code owner config
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");
    CodeOwnerConfig.Key keyOfNonExistingCodeOwnerConfig =
        CodeOwnerConfig.Key.create(project, "master", "/foo/");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                getCodeOwnerConfigFilePath(keyOfNonExistingCodeOwnerConfig))
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig, importType, codeOwnerConfigReference);

    PushOneCommit.Result r =
        createChange(
            user,
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(keyOfImportingCodeOwnerConfig)).get(),
            testCodeOwnerConfigFormatter.format(codeOwnerConfig));
    r.assertErrorStatus("invalid code owner config files");
    r.assertMessage(
        String.format(
            "error: commit %s: invalid %s import in '%s':"
                + " '%s' does not exist (project = %s, branch = master)",
            abbreviateName(r.getCommit()),
            importType.getType(),
            getCodeOwnerConfigFilePath(keyOfImportingCodeOwnerConfig),
            getCodeOwnerConfigFilePath(keyOfNonExistingCodeOwnerConfig),
            project.get()));
  }

  @Test
  public void cannotUploadConfigWithGlobalImportOfNonParseableCodeOwnerConfig() throws Exception {
    testUploadConfigWithImportOfNonParseableCodeOwnerConfig(CodeOwnerConfigImportType.GLOBAL);
  }

  @Test
  public void cannotUploadConfigWithPerFileImportOfNonParseableCodeOwnerConfig() throws Exception {
    testUploadConfigWithImportOfNonParseableCodeOwnerConfig(CodeOwnerConfigImportType.PER_FILE);
  }

  private void testUploadConfigWithImportOfNonParseableCodeOwnerConfig(
      CodeOwnerConfigImportType importType) throws Exception {
    // imports are not supported for the proto backend
    assume().that(backendConfig.getDefaultBackend()).isNotInstanceOf(ProtoBackend.class);

    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        CodeOwnerConfig.Key.create(project, "master", "/foo/");

    // disable the code owners functionality so that we can upload a non-parseable code owner config
    // that we then try to import
    disableCodeOwnersForProject(project);

    // upload a non-parseable code owner config that we then try to import
    PushOneCommit.Result r =
        createChange(
            "Add invalid code owner config",
            JgitPath.of(getCodeOwnerConfigFilePath(keyOfImportedCodeOwnerConfig)).get(),
            "INVALID");
    r.assertOkStatus();
    approve(r.getChangeId());
    gApi.changes().id(r.getChangeId()).current().submit();

    // re-enable the code owners functionality for the project
    setCodeOwnersConfig(project, null, StatusConfig.KEY_DISABLED, "false");

    // create a code owner config that imports a non-parseable code owner config
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                getCodeOwnerConfigFilePath(keyOfImportedCodeOwnerConfig))
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig, importType, codeOwnerConfigReference);

    r =
        createChange(
            "Add code owners",
            JgitPath.of(getCodeOwnerConfigFilePath(keyOfImportingCodeOwnerConfig)).get(),
            testCodeOwnerConfigFormatter.format(codeOwnerConfig));
    r.assertErrorStatus("invalid code owner config files");
    r.assertMessage(
        String.format(
            "error: commit %s: invalid %s import in '%s':"
                + " '%s' is not parseable (project = %s, branch = master)",
            abbreviateName(r.getCommit()),
            importType.getType(),
            getCodeOwnerConfigFilePath(keyOfImportingCodeOwnerConfig),
            getCodeOwnerConfigFilePath(keyOfImportedCodeOwnerConfig),
            project.get()));
  }

  private CodeOwnerConfig createCodeOwnerConfigWithImport(
      CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig,
      CodeOwnerConfigImportType importType,
      CodeOwnerConfigReference codeOwnerConfigReference) {
    CodeOwnerConfig.Builder codeOwnerConfigBuilder =
        CodeOwnerConfig.builder(keyOfImportingCodeOwnerConfig, TEST_REVISION);
    switch (importType) {
      case GLOBAL:
        codeOwnerConfigBuilder.addImport(codeOwnerConfigReference);
        break;
      case PER_FILE:
        codeOwnerConfigBuilder.addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression("foo")
                .addImport(codeOwnerConfigReference)
                .build());
        break;
      default:
        throw new IllegalStateException("unknown import type: " + importType);
    }
    return codeOwnerConfigBuilder.build();
  }

  private CodeOwnerConfig.Key createCodeOwnerConfigKey(String folderPath) {
    return CodeOwnerConfig.Key.create(project, "master", folderPath);
  }

  private String getCodeOwnerConfigFilePath(CodeOwnerConfig.Key codeOwnerConfigKey) {
    return backendConfig.getDefaultBackend().getFilePath(codeOwnerConfigKey).toString();
  }

  private String getParsingErrorMessage(
      ImmutableMap<Class<? extends CodeOwnerBackend>, String> messagesByBackend) {
    CodeOwnerBackend codeOwnerBackend = backendConfig.getDefaultBackend();
    assertThat(messagesByBackend).containsKey(codeOwnerBackend.getClass());
    return messagesByBackend.get(codeOwnerBackend.getClass());
  }

  private String abbreviateName(AnyObjectId id) throws Exception {
    return ObjectIds.abbreviateName(id, testRepo.getRevWalk().getObjectReader());
  }
}
