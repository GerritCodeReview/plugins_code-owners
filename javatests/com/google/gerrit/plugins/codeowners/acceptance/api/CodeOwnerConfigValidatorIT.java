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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.MergeInput;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.git.ObjectIds;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.TestCodeOwnerConfigCreation;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportMode;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportType;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.backend.config.BackendConfig;
import com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersCodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoCodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerConfigValidationPolicy;
import com.google.gerrit.plugins.codeowners.validation.SkipCodeOwnerConfigValidationCapability;
import com.google.gerrit.plugins.codeowners.validation.SkipCodeOwnerConfigValidationPushOption;
import com.google.inject.Inject;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@code com.google.gerrit.plugins.codeowners.validation.CodeOwnerConfigValidator}.
 * {@link CodeOwnerConfigValidatorOnSubmitIT} and {@link CodeOwnerConfigValidatorErrorHandlingIT}
 * contain further tests for {@code
 * com.google.gerrit.plugins.codeowners.validation.CodeOwnerConfigValidator}.
 */
public class CodeOwnerConfigValidatorIT extends AbstractCodeOwnersIT {
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
  public void nonCodeOwnerConfigFileIsNotValidated() throws Exception {
    PushOneCommit.Result r = createChange("Add arbitrary file", "arbitrary-file.txt", "INVALID");
    assertOkWithoutMessages(r);
  }

  @Test
  public void codeOwnerConfigFileWithNonMatchingFileExtensionIsNotValidated() throws Exception {
    PushOneCommit.Result r =
        createChange(
            "Add code owner config with file extension",
            getCodeOwnerConfigFileName() + ".foo",
            "INVALID");
    assertOkWithoutMessages(r);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fileExtension", value = "foo")
  public void codeOwnerConfigFileWithMatchingFileExtensionIsValidated() throws Exception {
    PushOneCommit.Result r =
        createChange(
            "Add code owner config with file extension",
            getCodeOwnerConfigFileName() + ".foo",
            "INVALID");
    String abbreviatedCommit = abbreviateName(r.getCommit());
    r.assertErrorStatus(
        String.format(
            "commit %s: [code-owners] %s", abbreviatedCommit, "invalid code owner config files"));
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.enableCodeOwnerConfigFilesWithFileExtensions",
      value = "true")
  public void codeOwnerConfigFileWithFileExtensionIsValidatedIfFileExtensionsAreEnabled()
      throws Exception {
    PushOneCommit.Result r =
        createChange(
            "Add code owner config with file extension",
            getCodeOwnerConfigFileName() + ".foo",
            "INVALID");
    String abbreviatedCommit = abbreviateName(r.getCommit());
    r.assertErrorStatus(
        String.format(
            "commit %s: [code-owners] %s", abbreviatedCommit, "invalid code owner config files"));
  }

  @Test
  public void canUploadConfigWithoutIssues() throws Exception {
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
    assertOkWithHints(r, "code owner config files validated, no issues found");
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void canUploadConfigWithoutIssuesInInitialCommit() throws Exception {
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
    assertOkWithHints(r, "code owner config files validated, no issues found");
  }

  @Test
  public void canUploadConfigWhichAssignsCodeOwnershipToAllUsers() throws Exception {
    testCanUploadConfigWhichAssignsCodeOwnershipToAllUsers();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.allowedEmailDomain", value = "example.com")
  public void canUploadConfigWhichAssignsCodeOwnershipToAllUsers_restrictedAllowedEmailDomain()
      throws Exception {
    testCanUploadConfigWhichAssignsCodeOwnershipToAllUsers();
  }

  private void testCanUploadConfigWhichAssignsCodeOwnershipToAllUsers() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // Create a code owner config without issues that assigns code ownership to all users.
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(
                        CodeOwnerSet.createWithoutPathExpressions(
                            CodeOwnerResolver.ALL_USERS_WILDCARD))
                    .build()));
    assertOkWithHints(r, "code owner config files validated, no issues found");
  }

  @Test
  public void canUploadConfigWithoutIssues_withImport() throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

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

    // Fetch the commit that created the imported code owner config into the local repository so
    // that the commit that creates the importing code owner config becomes a successor of this
    // commit.
    GitUtil.fetch(testRepo, "refs/*:refs/*");
    testRepo.reset(projectOperations.project(project).getHead("master"));

    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).getFilePath());

    // Create a code owner config with import and without issues.
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addImport(codeOwnerConfigReference)
                    .addCodeOwnerSet(
                        CodeOwnerSet.builder()
                            .addCodeOwnerEmail(admin.email())
                            .addPathExpression("foo")
                            .addImport(codeOwnerConfigReference)
                            .build())
                    .build()));
    assertOkWithHints(r, "code owner config files validated, no issues found");
  }

  @Test
  public void canUploadConfigWithoutIssues_withImportFromOtherProject() throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // Create a code owner config to be imported.
    Project.NameKey otherProject = projectOperations.newProject().create();
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(otherProject)
            .branch("master")
            .folderPath("/foo/")
            .addCodeOwnerEmail(user.email())
            .create();

    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                codeOwnerConfigOperations
                    .codeOwnerConfig(keyOfImportedCodeOwnerConfig)
                    .getFilePath())
            .setProject(otherProject)
            .build();

    // Create a code owner config with import from other project, and without issues.
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addImport(codeOwnerConfigReference)
                    .addCodeOwnerSet(
                        CodeOwnerSet.builder()
                            .addCodeOwnerEmail(admin.email())
                            .addPathExpression("foo")
                            .addImport(codeOwnerConfigReference)
                            .build())
                    .build()));
    assertOkWithHints(r, "code owner config files validated, no issues found");
  }

  @Test
  public void canUploadConfigWithoutIssues_withImportFromOtherProjectAndBranch() throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // Create a code owner config to be imported.
    String otherBranch = "foo";
    Project.NameKey otherProject = projectOperations.newProject().branches(otherBranch).create();
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(otherProject)
            .branch(otherBranch)
            .folderPath("/foo/")
            .addCodeOwnerEmail(user.email())
            .create();

    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                codeOwnerConfigOperations
                    .codeOwnerConfig(keyOfImportedCodeOwnerConfig)
                    .getFilePath())
            .setProject(otherProject)
            .setBranch(otherBranch)
            .build();

    // Create a code owner config with import from other project and branch, and without issues.
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addImport(codeOwnerConfigReference)
                    .addCodeOwnerSet(
                        CodeOwnerSet.builder()
                            .addCodeOwnerEmail(admin.email())
                            .addPathExpression("foo")
                            .addImport(codeOwnerConfigReference)
                            .build())
                    .build()));
    assertOkWithHints(r, "code owner config files validated, no issues found");
  }

  @Test
  public void
      canUploadConfigWithImportOfConfigThatIsAddedInSameCommit_importModeGlobalCodeOwnersOnly()
          throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig = createCodeOwnerConfigKey("/foo/");

    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).getFilePath());

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            ImmutableMap.of(
                codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
                format(
                    CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                        .addImport(codeOwnerConfigReference)
                        .addCodeOwnerSet(
                            CodeOwnerSet.builder()
                                .addCodeOwnerEmail(admin.email())
                                .addPathExpression("foo")
                                .addImport(codeOwnerConfigReference)
                                .build())
                        .build()),
                codeOwnerConfigOperations
                    .codeOwnerConfig(keyOfImportedCodeOwnerConfig)
                    .getJGitFilePath(),
                format(
                    CodeOwnerConfig.builder(keyOfImportedCodeOwnerConfig, TEST_REVISION)
                        .addCodeOwnerSet(
                            CodeOwnerSet.builder().addCodeOwnerEmail(user.email()).build())
                        .build())));
    assertOkWithHints(r, "code owner config files validated, no issues found");
  }

  @Test
  public void canUploadConfigWithImportOfConfigThatIsAddedInSameCommit_importModeAll()
      throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig = createCodeOwnerConfigKey("/foo/");

    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.ALL,
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).getFilePath());

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            ImmutableMap.of(
                codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
                format(
                    CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                        .addImport(codeOwnerConfigReference)
                        .addCodeOwnerSet(
                            CodeOwnerSet.builder().addCodeOwnerEmail(admin.email()).build())
                        .build()),
                codeOwnerConfigOperations
                    .codeOwnerConfig(keyOfImportedCodeOwnerConfig)
                    .getJGitFilePath(),
                format(
                    CodeOwnerConfig.builder(keyOfImportedCodeOwnerConfig, TEST_REVISION)
                        .addCodeOwnerSet(
                            CodeOwnerSet.builder().addCodeOwnerEmail(user.email()).build())
                        .build())));
    assertOkWithHints(r, "code owner config files validated, no issues found");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = "non-existing-backend")
  public void canUploadNonParseableConfigIfCodeOwnersPluginConfigurationIsInvalid()
      throws Exception {
    PushOneCommit.Result r = createChange("Add code owners", "OWNERS", "INVALID");
    assertOkWithWarnings(
        r,
        "skipping validation of code owner config files",
        "code-owners plugin configuration is invalid, cannot validate code owner config files");
  }

  @Test
  public void canUploadNonParseableConfigIfCodeOwnersFunctionalityIsDisabled() throws Exception {
    disableCodeOwnersForProject(project);

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations
                .codeOwnerConfig(createCodeOwnerConfigKey("/"))
                .getJGitFilePath(),
            "INVALID");
    assertOkWithHints(
        r,
        "skipping validation of code owner config files",
        "code-owners functionality is disabled");
  }

  @Test
  public void userCannotSkipCodeOwnerConfigValidationWithoutCapability() throws Exception {
    PushOneCommit.Result r =
        uploadNonParseableConfigWithPushOption(
            user, String.format("code-owners~%s", SkipCodeOwnerConfigValidationPushOption.NAME));
    assertErrorWithMessages(
        r,
        "skipping code owner config validation not allowed",
        String.format(
            "%s for plugin code-owners not permitted", SkipCodeOwnerConfigValidationCapability.ID));
  }

  @Test
  public void adminCanSkipCodeOwnerConfigValidation() throws Exception {
    PushOneCommit.Result r =
        uploadNonParseableConfigWithPushOption(
            admin, String.format("code-owners~%s", SkipCodeOwnerConfigValidationPushOption.NAME));
    assertOkWithHints(
        r,
        "skipping validation of code owner config files",
        String.format(
            "the validation is skipped due to the --code-owners~%s push option",
            SkipCodeOwnerConfigValidationPushOption.NAME));
  }

  @Test
  public void canUploadNonParseableConfigWithSkipOption() throws Exception {
    allowRegisteredUsersToSkipValidation();

    // with --code-owners~skip-validation
    PushOneCommit.Result r =
        uploadNonParseableConfigWithPushOption(
            user, String.format("code-owners~%s", SkipCodeOwnerConfigValidationPushOption.NAME));
    assertOkWithHints(
        r,
        "skipping validation of code owner config files",
        String.format(
            "the validation is skipped due to the --code-owners~%s push option",
            SkipCodeOwnerConfigValidationPushOption.NAME));

    // with --code-owners~skip-validation=true
    r =
        uploadNonParseableConfigWithPushOption(
            user,
            String.format("code-owners~%s=true", SkipCodeOwnerConfigValidationPushOption.NAME));
    assertOkWithHints(
        r,
        "skipping validation of code owner config files",
        String.format(
            "the validation is skipped due to the --code-owners~%s push option",
            SkipCodeOwnerConfigValidationPushOption.NAME));

    // with --code-owners~skip-validation=TRUE
    r =
        uploadNonParseableConfigWithPushOption(
            user,
            String.format("code-owners~%s=TRUE", SkipCodeOwnerConfigValidationPushOption.NAME));
    assertOkWithHints(
        r,
        "skipping validation of code owner config files",
        String.format(
            "the validation is skipped due to the --code-owners~%s push option",
            SkipCodeOwnerConfigValidationPushOption.NAME));
  }

  @Test
  public void cannotUploadNonParseableConfigIfSkipOptionIsFalse() throws Exception {
    allowRegisteredUsersToSkipValidation();

    // with --code-owners~skip-validation=false
    PushOneCommit.Result r =
        uploadNonParseableConfigWithPushOption(
            user,
            String.format("code-owners~%s=false", SkipCodeOwnerConfigValidationPushOption.NAME));
    String abbreviatedCommit = abbreviateName(r.getCommit());
    r.assertErrorStatus(
        String.format(
            "commit %s: [code-owners] %s", abbreviatedCommit, "invalid code owner config files"));
  }

  @Test
  public void cannotUploadNonParseableConfigWithInvalidSkipOption() throws Exception {
    allowRegisteredUsersToSkipValidation();

    PushOneCommit.Result r =
        uploadNonParseableConfigWithPushOption(
            user,
            String.format("code-owners~%s=INVALID", SkipCodeOwnerConfigValidationPushOption.NAME));
    assertErrorWithMessages(
        r,
        "invalid push option",
        String.format(
            "Invalid value for --code-owners~%s push option: INVALID",
            SkipCodeOwnerConfigValidationPushOption.NAME));
  }

  @Test
  public void cannotUploadNonParseableConfigIfSkipOptionIsSetMultipleTimes() throws Exception {
    allowRegisteredUsersToSkipValidation();

    PushOneCommit.Result r =
        uploadNonParseableConfigWithPushOption(
            user,
            String.format("code-owners~%s", SkipCodeOwnerConfigValidationPushOption.NAME),
            String.format("code-owners~%s=false", SkipCodeOwnerConfigValidationPushOption.NAME));
    assertErrorWithMessages(
        r,
        "invalid push option",
        String.format(
            "--code-owners~%s push option can be specified only once, received multiple values: [, false]",
            SkipCodeOwnerConfigValidationPushOption.NAME));
  }

  private PushOneCommit.Result uploadNonParseableConfigWithPushOption(
      TestAccount testAccount, String... pushOptions) throws Exception {
    TestRepository<InMemoryRepository> userRepo = cloneProject(project, testAccount);
    PushOneCommit push =
        pushFactory.create(
            testAccount.newIdent(),
            userRepo,
            "Add code owners",
            codeOwnerConfigOperations
                .codeOwnerConfig(createCodeOwnerConfigKey("/"))
                .getJGitFilePath(),
            "INVALID");
    push.setPushOptions(ImmutableList.copyOf(pushOptions));
    return push.to("refs/for/master");
  }

  private void allowRegisteredUsersToSkipValidation() {
    // grant the global capability that is required to use the
    // --code-owners~skip-validation push option to registered users
    projectOperations
        .allProjectsForUpdate()
        .add(
            allowCapability("code-owners-" + SkipCodeOwnerConfigValidationCapability.ID)
                .group(REGISTERED_USERS))
        .update();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnCommitReceived", value = "forced")
  public void
      cannotUploadNonParseableConfigIfCodeOwnersFunctionalityIsDisabledButValidationIsEnforced()
          throws Exception {
    disableCodeOwnersForProject(project);

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            "INVALID");
    assertFatalWithMessages(
        r,
        "invalid code owner config files",
        String.format(
            "invalid code owner config file '%s' (project = %s, branch = master):\n  %s",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
            project,
            getParsingErrorMessage(
                ImmutableMap.of(
                    FindOwnersBackend.class,
                    "invalid line: INVALID",
                    ProtoBackend.class,
                    "1:8: expected \"{\""))));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnCommitReceived", value = "dry_run")
  public void canUploadNonParseableConfigIfValidationIsDoneAsDryRun() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            "INVALID");
    assertOkWithFatals(
        r,
        "invalid code owner config files",
        String.format(
            "invalid code owner config file '%s' (project = %s, branch = master):\n  %s",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
            project,
            getParsingErrorMessage(
                ImmutableMap.of(
                    FindOwnersBackend.class,
                    "invalid line: INVALID",
                    ProtoBackend.class,
                    "1:8: expected \"{\""))));
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.enableValidationOnCommitReceived",
      value = "forced_dry_run")
  public void
      canUploadNonParseableConfigIfCodeOwnersFunctionalityIsDisabledButDryRunValidationIsEnforced()
          throws Exception {
    disableCodeOwnersForProject(project);

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            "INVALID");
    assertOkWithFatals(
        r,
        "invalid code owner config files",
        String.format(
            "invalid code owner config file '%s' (project = %s, branch = master):\n  %s",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
            project,
            getParsingErrorMessage(
                ImmutableMap.of(
                    FindOwnersBackend.class,
                    "invalid line: INVALID",
                    ProtoBackend.class,
                    "1:8: expected \"{\""))));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.readOnly", value = "true")
  public void cannotUploadConfigIfConfigsAreConfiguredToBeReadOnly() throws Exception {
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations
                .codeOwnerConfig(createCodeOwnerConfigKey("/"))
                .getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(createCodeOwnerConfigKey("/"), TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
                    .build()));
    assertErrorWithMessages(
        r,
        "modifying code owner config files not allowed",
        "code owner config files are configured to be read-only");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnCommitReceived", value = "false")
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "true")
  public void onReceiveCommitValidationDisabled() throws Exception {
    setAsDefaultCodeOwners(admin);

    // upload a change with a code owner config that has issues (non-resolvable code owners)
    String unknownEmail = "non-existing-email@example.com";
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(unknownEmail))
                    .build()));
    assertOkWithHints(
        r,
        "skipping validation of code owner config files",
        "code owners config validation is disabled");

    // approve the change
    approve(r.getChangeId());

    // try to submit the change, we expect that this fails since the validation on submit is enabled
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
  public void noValidationOnDeletionOfConfig() throws Exception {
    // Disable the code owners functionality so that we can upload an invalid config that we can
    // delete afterwards.
    disableCodeOwnersForProject(project);

    String path =
        codeOwnerConfigOperations.codeOwnerConfig(createCodeOwnerConfigKey("/")).getJGitFilePath();
    PushOneCommit.Result r = createChange("Add code owners", path, "INVALID");
    r.assertOkStatus();

    // re-enable the code owners functionality for the project
    enableCodeOwnersForProject(project);

    // delete the invalid code owner config file
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "Delete code owner config", path, "");
    r = push.rm("refs/for/master");
    assertOkWithoutMessages(r);
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
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            "INVALID");
    r.assertOkStatus();

    // re-enable the code owners functionality for the project
    enableCodeOwnersForProject(project);

    // update the code owner config so that it is still not parseable
    r =
        createChange(
            "Update code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            "STILL INVALID");
    assertOkWithWarnings(
        r,
        "invalid code owner config files",
        String.format(
            "invalid code owner config file '%s' (project = %s, branch = master):\n  %s",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
            project,
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
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            "INVALID");
    r.assertOkStatus();

    // re-enable the code owners functionality for the project
    enableCodeOwnersForProject(project);

    // update the code owner config so that it is parseable now, but has validation issues
    String unknownEmail1 = "non-existing-email@example.com";
    String unknownEmail2 = "another-unknown-email@example.com";
    r =
        createChange(
            "Update code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(
                        CodeOwnerSet.createWithoutPathExpressions(
                            unknownEmail1, admin.email(), unknownEmail2))
                    .build()));
    assertOkWithWarnings(
        r,
        "invalid code owner config files",
        String.format(
            "code owner email '%s' in '%s' cannot be resolved for %s",
            unknownEmail1,
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
            identifiedUserFactory.create(admin.id()).getLoggableName()),
        String.format(
            "code owner email '%s' in '%s' cannot be resolved for %s",
            unknownEmail2,
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
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
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(unknownEmail))
                    .build()));
    r.assertOkStatus();

    // re-enable the code owners functionality for the project
    enableCodeOwnersForProject(project);

    // update the code owner config so that the validation issue still exists, but no new issue is
    // introduced
    r =
        createChange(
            "Update code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(
                        CodeOwnerSet.createWithoutPathExpressions(unknownEmail, admin.email()))
                    .build()));
    assertOkWithWarnings(
        r,
        "invalid code owner config files",
        String.format(
            "code owner email '%s' in '%s' cannot be resolved for %s",
            unknownEmail,
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
            identifiedUserFactory.create(admin.id()).getLoggableName()));
  }

  @Test
  public void cannotUploadNonParseableConfig() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            "INVALID");
    assertFatalWithMessages(
        r,
        "invalid code owner config files",
        String.format(
            "invalid code owner config file '%s' (project = %s, branch = master):\n  %s",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
            project,
            getParsingErrorMessage(
                ImmutableMap.of(
                    FindOwnersBackend.class,
                    "invalid line: INVALID",
                    ProtoBackend.class,
                    "1:8: expected \"{\""))));
  }

  @Test
  public void cannotUpdateConfigToBeNonParseable() throws Exception {
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

    r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            "INVALID");
    assertFatalWithMessages(
        r,
        "invalid code owner config files",
        String.format(
            "invalid code owner config file '%s' (project = %s, branch = master):\n  %s",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
            project,
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
                codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey1).getJGitFilePath(),
                "INVALID",
                codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey2).getJGitFilePath(),
                "ALSO-INVALID"));
    PushOneCommit.Result r = push.to("refs/for/master");
    assertFatalWithMessages(
        r,
        "invalid code owner config files",
        String.format(
            "invalid code owner config file '%s' (project = %s, branch = master):\n  %s",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey1).getFilePath(),
            project,
            getParsingErrorMessage(
                ImmutableMap.of(
                    FindOwnersBackend.class,
                    "invalid line: INVALID",
                    ProtoBackend.class,
                    "1:8: expected \"{\""))),
        String.format(
            "invalid code owner config file '%s' (project = %s, branch = master):\n  %s",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey2).getFilePath(),
            project,
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
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(
                        CodeOwnerSet.createWithoutPathExpressions(
                            unknownEmail1, admin.email(), unknownEmail2))
                    .build()));
    assertErrorWithMessages(
        r,
        "invalid code owner config files",
        String.format(
            "code owner email '%s' in '%s' cannot be resolved for %s",
            unknownEmail1,
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
            identifiedUserFactory.create(admin.id()).getLoggableName()),
        String.format(
            "code owner email '%s' in '%s' cannot be resolved for %s",
            unknownEmail2,
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
            identifiedUserFactory.create(admin.id()).getLoggableName()));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.allowedEmailDomain", value = "example.com")
  public void canUploadConfigThatAssignsCodeOwnershipToAnEmailWithAnAllowedEmailDomain()
      throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    assertThat(admin.email()).endsWith("@example.com");
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
                    .build()));
    assertOkWithHints(r, "code owner config files validated, no issues found");
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
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(
                        CodeOwnerSet.createWithoutPathExpressions(
                            emailWithNonAllowedDomain, admin.email()))
                    .build()));
    assertErrorWithMessages(
        r,
        "invalid code owner config files",
        String.format(
            "the domain of the code owner email '%s' in '%s' is not allowed for" + " code owners",
            emailWithNonAllowedDomain,
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath()));
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
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(unknownEmail1))
                    .build()));
    r.assertOkStatus();

    // re-enable the code owners functionality for the project
    enableCodeOwnersForProject(project);

    // update the code owner config so that the validation issue still exists and a new issue is
    // introduced
    String unknownEmail2 = "another-unknown-email@example.com";
    r =
        createChange(
            "Update code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(
                        CodeOwnerSet.createWithoutPathExpressions(unknownEmail1, unknownEmail2))
                    .build()));

    String abbreviatedCommit = abbreviateName(r.getCommit());
    r.assertErrorStatus(
        String.format(
            "commit %s: [code-owners] %s", abbreviatedCommit, "invalid code owner config files"));
    r.assertMessage(
        String.format(
            "error: commit %s: [code-owners] %s",
            abbreviatedCommit,
            String.format(
                "code owner email '%s' in '%s' cannot be resolved for %s",
                unknownEmail2,
                codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
                identifiedUserFactory.create(admin.id()).getLoggableName())));

    // the pre-existing issue is returned as warning
    r.assertMessage(
        String.format(
            "warning: commit %s: [code-owners] code owner email '%s' in '%s' cannot be resolved for %s",
            abbreviatedCommit,
            unknownEmail1,
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
            identifiedUserFactory.create(admin.id()).getLoggableName()));

    r.assertNotMessage("hint");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnCommitReceived", value = "dry_run")
  public void canUploadConfigWithNewIssuesIfValidationIsDoneAsDryRun() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // upload an initial code owner config that has issues (non-resolvable code owners)
    String unknownEmail1 = "non-existing-email@example.com";
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(unknownEmail1))
                    .build()));
    assertOkWithErrors(
        r,
        String.format(
            "code owner email '%s' in '%s' cannot be resolved for %s",
            unknownEmail1,
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
            identifiedUserFactory.create(admin.id()).getLoggableName()));
    r.assertOkStatus();

    // update the code owner config so that the validation issue still exists and a new issue is
    // introduced
    String unknownEmail2 = "another-unknown-email@example.com";
    r =
        createChange(
            "Update code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(
                        CodeOwnerSet.createWithoutPathExpressions(unknownEmail1, unknownEmail2))
                    .build()));

    String abbreviatedCommit = abbreviateName(r.getCommit());
    r.assertOkStatus();
    r.assertMessage(
        String.format(
            "error: commit %s: [code-owners] %s",
            abbreviatedCommit,
            String.format(
                "code owner email '%s' in '%s' cannot be resolved for %s",
                unknownEmail2,
                codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
                identifiedUserFactory.create(admin.id()).getLoggableName())));

    // the pre-existing issue is returned as warning
    r.assertMessage(
        String.format(
            "warning: commit %s: [code-owners] code owner email '%s' in '%s' cannot be resolved"
                + " for %s",
            abbreviatedCommit,
            unknownEmail1,
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
            identifiedUserFactory.create(admin.id()).getLoggableName()));

    r.assertNotMessage("hint");
  }

  @Test
  public void uploadConfigWithGlobalSelfImportReportsAWarning() throws Exception {
    testUploadConfigWithSelfImport(CodeOwnerConfigImportType.GLOBAL);
  }

  @Test
  public void uploadConfigWithPerFileSelfImportReportsAWarning() throws Exception {
    testUploadConfigWithSelfImport(CodeOwnerConfigImportType.PER_FILE);
  }

  private void testUploadConfigWithSelfImport(CodeOwnerConfigImportType importType)
      throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    // create a code owner config that imports itself
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                codeOwnerConfigOperations
                    .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                    .getFilePath())
            .setProject(project)
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig, importType, codeOwnerConfigReference);

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getJGitFilePath(),
            format(codeOwnerConfig));
    assertOkWithWarnings(
        r,
        "invalid code owner config files",
        String.format(
            "invalid %s import in '%s': code owner config imports itself",
            importType.getType(),
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getFilePath()));
  }

  @Test
  public void canUploadConfigWithGlobalImportOfFileWithExtensionFromSameFolder() throws Exception {
    testUploadConfigWithImportOfFileWithExtensionFromSameFolder(CodeOwnerConfigImportType.GLOBAL);
  }

  @Test
  public void canUploadConfigWithPerFileImportOfFileWithExtensionFromSameFolder() throws Exception {
    testUploadConfigWithImportOfFileWithExtensionFromSameFolder(CodeOwnerConfigImportType.PER_FILE);
  }

  private void testUploadConfigWithImportOfFileWithExtensionFromSameFolder(
      CodeOwnerConfigImportType importType) throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    // create a code owner config that imports a code owner config from the same folder but with an
    // extension in the file name
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .fileName(getCodeOwnerConfigFileName() + "_extension")
            .addCodeOwnerEmail(user.email())
            .create();
    GitUtil.fetch(testRepo, "refs/*:refs/*");
    testRepo.reset(projectOperations.project(project).getHead("master"));
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                codeOwnerConfigOperations
                    .codeOwnerConfig(keyOfImportedCodeOwnerConfig)
                    .getFilePath())
            .setProject(project)
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig, importType, codeOwnerConfigReference);

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getJGitFilePath(),
            format(codeOwnerConfig));
    r.assertOkStatus();
  }

  @Test
  public void cannotUploadConfigWithGlobalImportOfFileWithFileExtension() throws Exception {
    testCannotUploadConfigWithImportOfFileWithFileExtension(CodeOwnerConfigImportType.GLOBAL);
  }

  @Test
  public void cannotUploadConfigWithPerFileImportOfFileWithFileExtension() throws Exception {
    testCannotUploadConfigWithImportOfFileWithFileExtension(CodeOwnerConfigImportType.PER_FILE);
  }

  private void testCannotUploadConfigWithImportOfFileWithFileExtension(
      CodeOwnerConfigImportType importType) throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    // create a code owner config that imports a code owner config from the same folder but with a
    // file extension in the file name
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .fileName(getCodeOwnerConfigFileName() + ".extension")
            .addCodeOwnerEmail(user.email())
            .create();
    GitUtil.fetch(testRepo, "refs/*:refs/*");
    testRepo.reset(projectOperations.project(project).getHead("master"));
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                codeOwnerConfigOperations
                    .codeOwnerConfig(keyOfImportedCodeOwnerConfig)
                    .getFilePath())
            .setProject(project)
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig, importType, codeOwnerConfigReference);

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getJGitFilePath(),
            format(codeOwnerConfig));
    assertErrorWithMessages(
        r,
        "invalid code owner config files",
        String.format(
            "invalid %s import in '%s':" + " '%s' is not a code owner config file",
            importType.getType(),
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).getFilePath(),
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).getFilePath()));
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.enableCodeOwnerConfigFilesWithFileExtensions",
      value = "true")
  public void canUploadConfigWithGlobalImportOfFileWithFileExtensionIfFileExtensionsAreEnabled()
      throws Exception {
    testUploadConfigWithImportOfFileWithFileExtension(CodeOwnerConfigImportType.GLOBAL);
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.enableCodeOwnerConfigFilesWithFileExtensions",
      value = "true")
  public void canUploadConfigWithPerFileImportOfFileWithFileExtensionIfFileExtensionsAreEnabled()
      throws Exception {
    testUploadConfigWithImportOfFileWithFileExtension(CodeOwnerConfigImportType.PER_FILE);
  }

  private void testUploadConfigWithImportOfFileWithFileExtension(
      CodeOwnerConfigImportType importType) throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    // create a code owner config that imports a code owner config from the same folder but with a
    // file extension in the file name
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .fileName(getCodeOwnerConfigFileName() + ".extension")
            .addCodeOwnerEmail(user.email())
            .create();
    GitUtil.fetch(testRepo, "refs/*:refs/*");
    testRepo.reset(projectOperations.project(project).getHead("master"));
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                codeOwnerConfigOperations
                    .codeOwnerConfig(keyOfImportedCodeOwnerConfig)
                    .getFilePath())
            .setProject(project)
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig, importType, codeOwnerConfigReference);

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getJGitFilePath(),
            format(codeOwnerConfig));
    r.assertOkStatus();
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
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    // create a code owner config that imports a code owner config from a non-existing project
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");
    Project.NameKey nonExistingProject = Project.nameKey("non-existing");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                codeOwnerConfigOperations
                    .codeOwnerConfig(CodeOwnerConfig.Key.create(nonExistingProject, "master", "/"))
                    .getFilePath())
            .setProject(nonExistingProject)
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig, importType, codeOwnerConfigReference);

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getJGitFilePath(),
            format(codeOwnerConfig));
    assertErrorWithMessages(
        r,
        "invalid code owner config files",
        String.format(
            "invalid %s import in '%s': project '%s' not found",
            importType.getType(),
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).getFilePath(),
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
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

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
                codeOwnerConfigOperations
                    .codeOwnerConfig(CodeOwnerConfig.Key.create(nonVisibleProject, "master", "/"))
                    .getFilePath())
            .setProject(nonVisibleProject)
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig, importType, codeOwnerConfigReference);

    PushOneCommit.Result r =
        createChange(
            user,
            "Add code owners",
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getJGitFilePath(),
            format(codeOwnerConfig));
    assertErrorWithMessages(
        r,
        "invalid code owner config files",
        String.format(
            "invalid %s import in '%s': project '%s' not found",
            importType.getType(),
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).getFilePath(),
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
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

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
                codeOwnerConfigOperations
                    .codeOwnerConfig(CodeOwnerConfig.Key.create(hiddenProject, "master", "/"))
                    .getFilePath())
            .setProject(hiddenProject)
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig, importType, codeOwnerConfigReference);

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getJGitFilePath(),
            format(codeOwnerConfig));
    assertErrorWithMessages(
        r,
        "invalid code owner config files",
        String.format(
            "invalid %s import in '%s': project '%s' has state 'hidden' that doesn't permit read",
            importType.getType(),
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).getFilePath(),
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
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    // create a code owner config that imports a code owner config from a non-existing branch
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");
    Project.NameKey otherProject = projectOperations.newProject().name(name("other")).create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                codeOwnerConfigOperations
                    .codeOwnerConfig(CodeOwnerConfig.Key.create(otherProject, "non-existing", "/"))
                    .getFilePath())
            .setProject(otherProject)
            .setBranch("non-existing")
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig, importType, codeOwnerConfigReference);

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getJGitFilePath(),
            format(codeOwnerConfig));
    assertErrorWithMessages(
        r,
        "invalid code owner config files",
        String.format(
            "invalid %s import in '%s': branch 'non-existing' not found in project '%s'",
            importType.getType(),
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).getFilePath(),
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
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

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
                codeOwnerConfigOperations
                    .codeOwnerConfig(CodeOwnerConfig.Key.create(otherProject, "master", "/"))
                    .getFilePath())
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
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getJGitFilePath(),
            format(codeOwnerConfig));
    assertErrorWithMessages(
        r,
        "invalid code owner config files",
        String.format(
            "invalid %s import in '%s': branch 'master' not found in project '%s'",
            importType.getType(),
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).getFilePath(),
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
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

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
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getJGitFilePath(),
            format(codeOwnerConfig));
    assertErrorWithMessages(
        r,
        "invalid code owner config files",
        String.format(
            "invalid %s import in '%s':"
                + " 'non-code-owner-config.txt' is not a code owner config file",
            importType.getType(),
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getFilePath()));
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
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    // create a code owner config that imports a non-existing code owner config
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");
    CodeOwnerConfig.Key keyOfNonExistingCodeOwnerConfig =
        CodeOwnerConfig.Key.create(project, "master", "/foo/");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                codeOwnerConfigOperations
                    .codeOwnerConfig(keyOfNonExistingCodeOwnerConfig)
                    .getFilePath())
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig, importType, codeOwnerConfigReference);

    PushOneCommit.Result r =
        createChange(
            user,
            "Add code owners",
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getJGitFilePath(),
            format(codeOwnerConfig));
    assertErrorWithMessages(
        r,
        "invalid code owner config files",
        String.format(
            "invalid %s import in '%s': '%s' does not exist (project = %s, branch = master,"
                + " revision = %s)",
            importType.getType(),
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).getFilePath(),
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfNonExistingCodeOwnerConfig)
                .getFilePath(),
            project.get(),
            r.getCommit().name()));
  }

  @Test
  public void
      forMergeCommitsNonResolvableGlobalImportsFromOtherProjectsAreReportedAsWarningsIfImportsDontSpecifyBranch()
          throws Exception {
    testForMergeCommitsThatNonResolvableImportsFromOtherProjectsAreReportedAsWarningsIfImportsDontSpecifyBranch(
        CodeOwnerConfigImportType.GLOBAL);
  }

  @Test
  public void cannotUploadConfigWithPerFileImportWithImportModeAll() throws Exception {
    assume().that(backendConfig.getDefaultBackend()).isInstanceOf(FindOwnersBackend.class);

    // create a code owner config that can be imported
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .folderPath("/")
            .addCodeOwnerEmail(user.email())
            .create();
    GitUtil.fetch(testRepo, "refs/*:refs/*");

    // codeOwnerConfigOperations cannot create a code owner config that has a per file import with
    // import mode ALL, hence we just hard-code the contents of the OWNERS file here.
    String codeOwnerConfig =
        "per-file foo=include "
            + codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).getFilePath();

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            codeOwnerConfig);
    assertFatalWithMessages(
        r,
        "invalid code owner config files",
        String.format(
            "invalid code owner config file '%s' (project = %s, branch = master):\n  %s",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
            project,
            "keyword 'include' is not supported for per file imports: " + codeOwnerConfig));
  }

  @Test
  public void
      forMergeCommitsNonResolvablePerFileImportsFromOtherProjectsAreReportedAsWarningsIfImportsDontSpecifyBranch()
          throws Exception {
    testForMergeCommitsThatNonResolvableImportsFromOtherProjectsAreReportedAsWarningsIfImportsDontSpecifyBranch(
        CodeOwnerConfigImportType.PER_FILE);
  }

  private void
      testForMergeCommitsThatNonResolvableImportsFromOtherProjectsAreReportedAsWarningsIfImportsDontSpecifyBranch(
          CodeOwnerConfigImportType importType) throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    // Create a second project from which we will import a code owner config.
    Project.NameKey otherProject = projectOperations.newProject().create();

    // Create a target branch for into which we will merge later.
    String targetBranchName = "target";
    BranchInput branchInput = new BranchInput();
    branchInput.ref = targetBranchName;
    branchInput.revision = projectOperations.project(project).getHead("master").name();
    gApi.projects().name(project.get()).branch(branchInput.ref).create(branchInput);
    branchInput.revision = projectOperations.project(otherProject).getHead("master").name();
    gApi.projects().name(otherProject.get()).branch(branchInput.ref).create(branchInput);

    // Create the code owner config file in the second project that we will import.
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(otherProject)
            .branch("master")
            .folderPath("/foo/")
            .addCodeOwnerEmail(admin.email())
            .create();

    // Create a code owner config that imports the code owner config from the other project, without
    // specifying the branch for the import (if the branch is not specified the code owner config is
    // imported from the same branch that contains the importing code owner config).
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                codeOwnerConfigOperations
                    .codeOwnerConfig(keyOfImportedCodeOwnerConfig)
                    .getFilePath())
            .setProject(otherProject)
            .build();
    TestCodeOwnerConfigCreation.Builder codeOwnerConfigBuilder =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/");
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
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = codeOwnerConfigBuilder.create();
    GitUtil.fetch(testRepo, "refs/*:refs/*");

    // Create the merge commit.
    RevCommit parent1 = projectOperations.project(project).getHead(targetBranchName);
    RevCommit parent2 = projectOperations.project(project).getHead("master");
    PushOneCommit m =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "merge",
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getJGitFilePath(),
            format(codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get()));
    m.setParents(ImmutableList.of(parent1, parent2));
    PushOneCommit.Result r = m.to("refs/for/" + targetBranchName);
    assertOkWithWarnings(
        r,
        "invalid code owner config files",
        String.format(
            "invalid %s import in '%s': '%s' does not exist (project = %s, branch = %s,"
                + " revision = %s)",
            importType.getType(),
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).getFilePath(),
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).getFilePath(),
            otherProject.get(),
            targetBranchName,
            projectOperations.project(otherProject).getHead(targetBranchName).getName()));
  }

  @Test
  public void
      forMergeCommitsNonResolvableGlobalImportsFromOtherProjectsAreReportedAsErrorsIfImportsSpecifyBranch()
          throws Exception {
    testForMergeCommitsThatNonResolvableImportsFromOtherProjectsAreReportedAsErrorsIfImportsSpecifyBranch(
        CodeOwnerConfigImportType.GLOBAL);
  }

  @Test
  public void
      forMergeCommitsNonResolvablePerFileImportsFromOtherProjectsAreReportedAsErrorsIfImportsSpecifyBranch()
          throws Exception {
    testForMergeCommitsThatNonResolvableImportsFromOtherProjectsAreReportedAsErrorsIfImportsSpecifyBranch(
        CodeOwnerConfigImportType.PER_FILE);
  }

  private void
      testForMergeCommitsThatNonResolvableImportsFromOtherProjectsAreReportedAsErrorsIfImportsSpecifyBranch(
          CodeOwnerConfigImportType importType) throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    // Create a second project from which we will import a non-existing code owner config.
    Project.NameKey otherProject = projectOperations.newProject().create();

    // Create a target branch for into which we will merge later.
    String targetBranchName = "target";
    BranchInput branchInput = new BranchInput();
    branchInput.ref = targetBranchName;
    branchInput.revision = projectOperations.project(project).getHead("master").name();
    gApi.projects().name(project.get()).branch(branchInput.ref).create(branchInput);

    // Create a code owner config that imports a non-existing code owner config from the other
    // project, with specifying the branch for the import. When this code owner config is merged
    // into another branch later we expect that it is rejected by the validation.
    CodeOwnerConfig.Key keyOfNonExistingCodeOwnerConfig =
        CodeOwnerConfig.Key.create(project, "master", "/foo/");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                codeOwnerConfigOperations
                    .codeOwnerConfig(keyOfNonExistingCodeOwnerConfig)
                    .getFilePath())
            .setProject(otherProject)
            .setBranch("master")
            .build();
    TestCodeOwnerConfigCreation.Builder codeOwnerConfigBuilder =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/");
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
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = codeOwnerConfigBuilder.create();
    GitUtil.fetch(testRepo, "refs/*:refs/*");

    // Create the merge commit.
    RevCommit parent1 = projectOperations.project(project).getHead(targetBranchName);
    RevCommit parent2 = projectOperations.project(project).getHead("master");
    PushOneCommit m =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "merge",
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getJGitFilePath(),
            format(codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).get()));
    m.setParents(ImmutableList.of(parent1, parent2));
    PushOneCommit.Result r = m.to("refs/for/" + targetBranchName);
    assertErrorWithMessages(
        r,
        "invalid code owner config files",
        String.format(
            "invalid %s import in '%s': '%s' does not exist (project = %s, branch = master,"
                + " revision = %s)",
            importType.getType(),
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).getFilePath(),
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfNonExistingCodeOwnerConfig)
                .getFilePath(),
            otherProject.get(),
            projectOperations.project(otherProject).getHead("master").getName()));
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
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        CodeOwnerConfig.Key.create(project, "master", "/foo/");

    // disable the code owners functionality so that we can upload a non-parseable code owner config
    // that we then try to import
    disableCodeOwnersForProject(project);

    // upload a non-parseable code owner config that we then try to import
    PushOneCommit.Result r =
        createChange(
            "Add invalid code owner config",
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportedCodeOwnerConfig)
                .getJGitFilePath(),
            "INVALID");
    r.assertOkStatus();
    approve(r.getChangeId());
    gApi.changes().id(r.getChangeId()).current().submit();

    // re-enable the code owners functionality for the project
    enableCodeOwnersForProject(project);

    // create a code owner config that imports a non-parseable code owner config
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                codeOwnerConfigOperations
                    .codeOwnerConfig(keyOfImportedCodeOwnerConfig)
                    .getFilePath())
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig, importType, codeOwnerConfigReference);

    r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getJGitFilePath(),
            format(codeOwnerConfig));
    assertErrorWithMessages(
        r,
        "invalid code owner config files",
        String.format(
            "invalid %s import in '%s': '%s' is not parseable (project = %s, branch = master)",
            importType.getType(),
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).getFilePath(),
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).getFilePath(),
            project.get()));
  }

  @Test
  public void validateMergeCommitCreatedViaTheCreateChangeRestApi() throws Exception {
    testValidateMergeCommitCreatedViaTheCreateChangeRestApi();
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.mergeCommitStrategy",
      value = "FILES_WITH_CONFLICT_RESOLUTION")
  public void
      validateMergeCommitCreatedViaTheCreateChangeRestApi_filesWithConflictResolutionAsMergeCommitStrategy()
          throws Exception {
    testValidateMergeCommitCreatedViaTheCreateChangeRestApi();
  }

  private void testValidateMergeCommitCreatedViaTheCreateChangeRestApi() throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    // Create another branch.
    String branchName = "stable";
    createBranch(BranchNameKey.create(project, branchName));

    // Create a code owner config file in the other branch that can be imported.
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branchName)
            .folderPath("/foo/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).getFilePath());

    // Create a code owner config file in the other branch that contains an import.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(branchName)
        .folderPath("/")
        .addImport(codeOwnerConfigReference)
        .addCodeOwnerEmail(user.email())
        .create();

    // Create a change that merges the other branch into master. The code owner config files in the
    // created merge commit will be validated.
    ChangeInput changeInput = new ChangeInput();
    changeInput.project = project.get();
    changeInput.branch = "master";
    changeInput.subject = "A change";
    changeInput.status = ChangeStatus.NEW;
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = gApi.projects().name(project.get()).branch(branchName).get().revision;
    changeInput.merge = mergeInput;
    gApi.changes().create(changeInput);
  }

  @Test
  public void skipValidationForMergeCommitCreatedViaTheCreateChangeRestApi() throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    // Create another branch.
    String branchName = "stable";
    createBranch(BranchNameKey.create(project, branchName));

    // Create a code owner config file in the other branch.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(branchName)
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    // Create a conflicting code owner config file in the target branch.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();

    // Try creating a change that merges the other branch into master. The change creation fails
    // because the code owner config file in the other branch conflicts with the code owner config
    // file in the master branch.
    ChangeInput changeInput = new ChangeInput();
    changeInput.project = project.get();
    changeInput.branch = "master";
    changeInput.subject = "A change";
    changeInput.status = ChangeStatus.NEW;
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = gApi.projects().name(project.get()).branch(branchName).get().revision;
    changeInput.merge = mergeInput;
    MergeConflictException mergeConflictException =
        assertThrows(MergeConflictException.class, () -> gApi.changes().create(changeInput));
    assertThat(mergeConflictException)
        .hasMessageThat()
        .isEqualTo(String.format("merge conflict(s):\n%s", getCodeOwnerConfigFileName()));

    // Try creating the merge change with conflicts. Fails because the code owner config file
    // contains conflict markers which fails the code owner config file validation.
    mergeInput.allowConflicts = true;
    ResourceConflictException resourceConflictException =
        assertThrows(ResourceConflictException.class, () -> gApi.changes().create(changeInput));
    assertThat(resourceConflictException)
        .hasMessageThat()
        .contains(
            String.format(
                "[code-owners] invalid code owner config file '/%s'",
                getCodeOwnerConfigFileName()));

    // Create the merge change with skipping code owners validation.
    changeInput.validationOptions =
        ImmutableMap.of(
            String.format("code-owners~%s", SkipCodeOwnerConfigValidationPushOption.NAME), "true");
    gApi.changes().create(changeInput);
  }

  @Test
  public void userWithoutCapabilitySkipValidationCannotSkipValidationWithCreateChangeRestApi()
      throws Exception {
    requestScopeOperations.setApiUser(user.id());

    ChangeInput changeInput = new ChangeInput();
    changeInput.project = project.get();
    changeInput.branch = "master";
    changeInput.subject = "A change";
    changeInput.status = ChangeStatus.NEW;
    changeInput.validationOptions =
        ImmutableMap.of(
            String.format("code-owners~%s", SkipCodeOwnerConfigValidationPushOption.NAME), "true");
    ResourceConflictException resourceConflictException =
        assertThrows(ResourceConflictException.class, () -> gApi.changes().create(changeInput));
    assertThat(resourceConflictException)
        .hasMessageThat()
        .contains(
            String.format(
                "[code-owners] %s for plugin code-owners not permitted",
                SkipCodeOwnerConfigValidationCapability.ID));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableCodeOwners", value = "false")
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "true")
  public void canUploadAndSubmitConfigWithUnresolvableCodeOwners() throws Exception {
    setAsDefaultCodeOwners(admin);

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");

    // upload a code owner config that has issues (non-resolvable code owners)
    String unknownEmail = "non-existing-email@example.com";
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(unknownEmail))
                    .build()));
    assertOkWithWarnings(
        r,
        "invalid code owner config files",
        String.format(
            "code owner email '%s' in '%s' cannot be resolved for %s",
            unknownEmail,
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
            identifiedUserFactory.create(admin.id()).getLoggableName()));

    // submit the change
    approve(r.getChangeId());
    gApi.changes().id(r.getChangeId()).current().submit();
    assertThat(gApi.changes().id(r.getChangeId()).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableImports", value = "false")
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "true")
  public void canUploadAndSubmitConfigWithUnresolvableImports() throws Exception {
    setAsDefaultCodeOwners(admin);

    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");

    // upload a code owner config that has issues (non-resolvable imports)
    Project.NameKey nonExistingProject = Project.nameKey("non-existing");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                codeOwnerConfigOperations
                    .codeOwnerConfig(CodeOwnerConfig.Key.create(nonExistingProject, "master", "/"))
                    .getFilePath())
            .setProject(nonExistingProject)
            .build();
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(keyOfImportingCodeOwnerConfig, TEST_REVISION)
                    .addImport(codeOwnerConfigReference)
                    .addCodeOwnerSet(
                        CodeOwnerSet.builder()
                            .addPathExpression("foo")
                            .addImport(codeOwnerConfigReference)
                            .build())
                    .build()));
    assertOkWithWarnings(
        r,
        "invalid code owner config files",
        String.format(
            "invalid %s import in '%s': project '%s' not found",
            CodeOwnerConfigImportType.GLOBAL.getType(),
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).getFilePath(),
            nonExistingProject.get()),
        String.format(
            "invalid %s import in '%s': project '%s' not found",
            CodeOwnerConfigImportType.PER_FILE.getType(),
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).getFilePath(),
            nonExistingProject.get()));

    // submit the change
    approve(r.getChangeId());
    gApi.changes().id(r.getChangeId()).current().submit();
    assertThat(gApi.changes().id(r.getChangeId()).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableCodeOwners", value = "true")
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableImports", value = "true")
  @GerritConfig(name = "plugin.code-owners.enableValidationOnCommitReceived", value = "false")
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "false")
  public void rejectConfigOptionsAreIgnoredIfValidationIsDisabled() throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    setAsDefaultCodeOwners(admin);

    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");

    // upload a code owner config that has issues (non-resolvable code owners and non-resolvable
    // imports)
    String unknownEmail = "non-existing-email@example.com";
    Project.NameKey nonExistingProject = Project.nameKey("non-existing");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                codeOwnerConfigOperations
                    .codeOwnerConfig(CodeOwnerConfig.Key.create(nonExistingProject, "master", "/"))
                    .getFilePath())
            .setProject(nonExistingProject)
            .build();
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(keyOfImportingCodeOwnerConfig, TEST_REVISION)
                    .addImport(codeOwnerConfigReference)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(unknownEmail))
                    .addCodeOwnerSet(
                        CodeOwnerSet.builder()
                            .addPathExpression("foo")
                            .addImport(codeOwnerConfigReference)
                            .build())
                    .build()));
    assertOkWithHints(
        r,
        "skipping validation of code owner config files",
        "code owners config validation is disabled");

    // submit the change
    approve(r.getChangeId());
    gApi.changes().id(r.getChangeId()).current().submit();
    assertThat(gApi.changes().id(r.getChangeId()).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "true")
  public void disableValidationForBranch() throws Exception {
    setAsDefaultCodeOwners(admin);

    // Disable the validation for the master branch.
    updateCodeOwnersConfig(
        project,
        codeOwnersConfig -> {
          codeOwnersConfig.setString(
              GeneralConfig.SECTION_VALIDATION,
              "refs/heads/master",
              GeneralConfig.KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED,
              CodeOwnerConfigValidationPolicy.FALSE.name());
          codeOwnersConfig.setString(
              GeneralConfig.SECTION_VALIDATION,
              "refs/heads/master",
              GeneralConfig.KEY_ENABLE_VALIDATION_ON_SUBMIT,
              CodeOwnerConfigValidationPolicy.FALSE.name());
        });

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations
                .codeOwnerConfig(createCodeOwnerConfigKey("/"))
                .getJGitFilePath(),
            "INVALID");
    assertOkWithHints(
        r,
        "skipping validation of code owner config files",
        "code owners config validation is disabled");

    approve(r.getChangeId());
    gApi.changes().id(r.getChangeId()).current().submit();
    assertThat(gApi.changes().id(r.getChangeId()).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "true")
  public void disableRejectionOfNonResolvableCodeOwnersForBranch() throws Exception {
    setAsDefaultCodeOwners(admin);

    // Disable the rejection of non-resolvable code owners for the master branch.
    updateCodeOwnersConfig(
        project,
        codeOwnersConfig ->
            codeOwnersConfig.setBoolean(
                GeneralConfig.SECTION_VALIDATION,
                "refs/heads/master",
                GeneralConfig.KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS,
                false));

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");
    String unknownEmail = "non-existing-email@example.com";
    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath(),
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(unknownEmail))
                    .build()));
    assertOkWithWarnings(
        r,
        "invalid code owner config files",
        String.format(
            "code owner email '%s' in '%s' cannot be resolved for %s",
            unknownEmail,
            codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath(),
            identifiedUserFactory.create(admin.id()).getLoggableName()));

    approve(r.getChangeId());
    gApi.changes().id(r.getChangeId()).current().submit();
    assertThat(gApi.changes().id(r.getChangeId()).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "true")
  public void disableRejectionOfNonResolvableImportsForBranch() throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    setAsDefaultCodeOwners(admin);

    // Disable the rejection of non-resolvable imports for the master branch.
    updateCodeOwnersConfig(
        project,
        codeOwnersConfig ->
            codeOwnersConfig.setBoolean(
                GeneralConfig.SECTION_VALIDATION,
                "refs/heads/master",
                GeneralConfig.KEY_REJECT_NON_RESOLVABLE_IMPORTS,
                false));

    // create a code owner config that imports a code owner config from a non-existing project
    CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig = createCodeOwnerConfigKey("/");
    Project.NameKey nonExistingProject = Project.nameKey("non-existing");
    CodeOwnerConfigReference codeOwnerConfigReference =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
                codeOwnerConfigOperations
                    .codeOwnerConfig(CodeOwnerConfig.Key.create(nonExistingProject, "master", "/"))
                    .getFilePath())
            .setProject(nonExistingProject)
            .build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfigWithImport(
            keyOfImportingCodeOwnerConfig,
            CodeOwnerConfigImportType.GLOBAL,
            codeOwnerConfigReference);

    PushOneCommit.Result r =
        createChange(
            "Add code owners",
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfImportingCodeOwnerConfig)
                .getJGitFilePath(),
            format(codeOwnerConfig));
    assertOkWithWarnings(
        r,
        "invalid code owner config files",
        String.format(
            "invalid %s import in '%s': project '%s' not found",
            CodeOwnerConfigImportType.GLOBAL.getType(),
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportingCodeOwnerConfig).getFilePath(),
            nonExistingProject.get()));

    approve(r.getChangeId());
    gApi.changes().id(r.getChangeId()).current().submit();
    assertThat(gApi.changes().id(r.getChangeId()).get().status).isEqualTo(ChangeStatus.MERGED);
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

  private String abbreviateName(AnyObjectId id) throws Exception {
    return ObjectIds.abbreviateName(id, testRepo.getRevWalk().getObjectReader());
  }

  private static void assertOkWithoutMessages(PushOneCommit.Result pushResult) {
    pushResult.assertOkStatus();
    pushResult.assertNotMessage("fatal");
    pushResult.assertNotMessage("error");
    pushResult.assertNotMessage("warning");
    pushResult.assertNotMessage("hint");
  }

  private void assertOkWithHints(PushOneCommit.Result pushResult, String... hints)
      throws Exception {
    pushResult.assertOkStatus();
    for (String hint : hints) {
      pushResult.assertMessage(
          String.format(
              "hint: commit %s: [code-owners] %s", abbreviateName(pushResult.getCommit()), hint));
    }
    pushResult.assertNotMessage("fatal");
    pushResult.assertNotMessage("error");
    pushResult.assertNotMessage("warning");
  }

  private void assertOkWithFatals(PushOneCommit.Result pushResult, String... errors)
      throws Exception {
    pushResult.assertOkStatus();
    for (String error : errors) {
      pushResult.assertMessage(
          String.format(
              "fatal: commit %s: [code-owners] %s", abbreviateName(pushResult.getCommit()), error));
    }
    pushResult.assertNotMessage("error");
    pushResult.assertNotMessage("warning");
    pushResult.assertNotMessage("hint");
  }

  private void assertOkWithErrors(PushOneCommit.Result pushResult, String... errors)
      throws Exception {
    pushResult.assertOkStatus();
    for (String error : errors) {
      pushResult.assertMessage(
          String.format(
              "error: commit %s: [code-owners] %s", abbreviateName(pushResult.getCommit()), error));
    }
    pushResult.assertNotMessage("fatal");
    pushResult.assertNotMessage("warning");
    pushResult.assertNotMessage("hint");
  }

  private void assertOkWithWarnings(PushOneCommit.Result pushResult, String... warnings)
      throws Exception {
    pushResult.assertOkStatus();
    for (String warning : warnings) {
      pushResult.assertMessage(
          String.format(
              "warning: commit %s: [code-owners] %s",
              abbreviateName(pushResult.getCommit()), warning));
    }
    pushResult.assertNotMessage("fatal");
    pushResult.assertNotMessage("error");
    pushResult.assertNotMessage("hint");
  }

  private void assertErrorWithMessages(
      PushOneCommit.Result pushResult, String summaryMessage, String... errors) throws Exception {
    String abbreviatedCommit = abbreviateName(pushResult.getCommit());
    pushResult.assertErrorStatus(
        String.format("commit %s: [code-owners] %s", abbreviatedCommit, summaryMessage));
    for (String error : errors) {
      pushResult.assertMessage(
          String.format("error: commit %s: [code-owners] %s", abbreviatedCommit, error));
    }
    pushResult.assertNotMessage("fatal");
    pushResult.assertNotMessage("warning");
    pushResult.assertNotMessage("hint");
  }

  private void assertFatalWithMessages(
      PushOneCommit.Result pushResult, String summaryMessage, String... errors) throws Exception {
    String abbreviatedCommit = abbreviateName(pushResult.getCommit());
    pushResult.assertErrorStatus(
        String.format("commit %s: [code-owners] %s", abbreviatedCommit, summaryMessage));
    for (String error : errors) {
      pushResult.assertMessage(
          String.format("fatal: commit %s: [code-owners] %s", abbreviatedCommit, error));
    }
    pushResult.assertNotMessage("error");
    pushResult.assertNotMessage("warning");
    pushResult.assertNotMessage("hint");
  }
}
