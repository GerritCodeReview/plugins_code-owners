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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportMode;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.CheckCodeOwnerConfigFiles} REST endpoint.
 *
 * <p>Further tests for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.CheckCodeOwnerConfigFiles} REST endpoint that
 * require using the REST API are implemented in {@link
 * com.google.gerrit.plugins.codeowners.acceptance.restapi.CheckCodeOwnerConfigFilesRestIT}.
 */
public class CheckCodeOwnerConfigFilesIT extends AbstractCodeOwnersIT {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  @Test
  public void requiresAuthenticatedUser() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    AuthException authException =
        assertThrows(AuthException.class, () -> checkCodeOwnerConfigFilesIn(project));
    assertThat(authException).hasMessageThat().contains("Authentication required");
  }

  @Test
  public void requiresCallerToBeProjectOwner() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    AuthException authException =
        assertThrows(AuthException.class, () -> checkCodeOwnerConfigFilesIn(project));
    assertThat(authException).hasMessageThat().isEqualTo("write refs/meta/config not permitted");
  }

  @Test
  public void noCodeOwnerConfigFile() throws Exception {
    assertThat(checkCodeOwnerConfigFilesIn(project))
        .containsExactly(
            "refs/heads/master", ImmutableMap.of(),
            "refs/meta/config", ImmutableMap.of());
  }

  @Test
  public void nonVisibleBranchesAreSkipped() throws Exception {
    String branchName = "non-visible";
    createBranch(BranchNameKey.create(project, branchName));

    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref(RefNames.fullName(branchName)).group(REGISTERED_USERS))
        .update();

    assertThat(checkCodeOwnerConfigFilesIn(project))
        .containsExactly(
            "refs/heads/master", ImmutableMap.of(), "refs/meta/config", ImmutableMap.of());
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabledBranch", value = "refs/meta/config")
  public void disabledBranchesAreSkipped() throws Exception {
    assertThat(checkCodeOwnerConfigFilesIn(project))
        .containsExactly("refs/heads/master", ImmutableMap.of());
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabledBranch", value = "refs/meta/config")
  public void validateDisabledBranches() throws Exception {
    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .checkCodeOwnerConfigFiles()
                .validateDisabledBranches()
                .check())
        .containsExactly(
            "refs/heads/master", ImmutableMap.of(),
            "refs/meta/config", ImmutableMap.of());
  }

  @Test
  public void noIssuesInCodeOwnerConfigFile() throws Exception {
    // Create some code owner config files.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(user.email())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/baz/")
        .addCodeOwnerEmail(admin.email())
        .create();

    assertThat(checkCodeOwnerConfigFilesIn(project))
        .containsExactly(
            "refs/heads/master", ImmutableMap.of(),
            "refs/meta/config", ImmutableMap.of());
  }

  @Test
  public void nonParseableCodeOwnerConfigFile() throws Exception {
    String codeOwnerConfigPath = "/" + getCodeOwnerConfigFileName();
    createNonParseableCodeOwnerConfig(codeOwnerConfigPath);

    assertThat(checkCodeOwnerConfigFilesIn(project))
        .containsExactly(
            "refs/heads/master",
                ImmutableMap.of(
                    codeOwnerConfigPath,
                    ImmutableList.of(
                        fatal(
                            String.format(
                                "invalid code owner config file '%s' (project = %s,"
                                    + " branch = master):\n  %s",
                                codeOwnerConfigPath,
                                project,
                                getParsingErrorMessageForNonParseableCodeOwnerConfig())))),
            "refs/meta/config", ImmutableMap.of());
  }

  @Test
  public void issuesInCodeOwnerConfigFile() throws Exception {
    testIssuesInCodeOwnerConfigFile(ConsistencyProblemInfo.Status.ERROR);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableCodeOwners", value = "false")
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableImports", value = "false")
  public void issuesInCodeOwnerConfigFileReportedAsWarnings() throws Exception {
    testIssuesInCodeOwnerConfigFile(ConsistencyProblemInfo.Status.WARNING);
  }

  private void testIssuesInCodeOwnerConfigFile(ConsistencyProblemInfo.Status expectedStatus)
      throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    // Create some code owner config files with issues.
    CodeOwnerConfig.Key keyOfInvalidConfig1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addImport(
                CodeOwnerConfigReference.create(
                    CodeOwnerConfigImportMode.ALL, "/not-a-code-owner-config"))
            .create();
    String pathOfInvalidConfig1 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfInvalidConfig1).getFilePath();

    CodeOwnerConfig.Key keyOfInvalidConfig2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail("unknown1@example.com")
            .addCodeOwnerEmail("unknown2@example.com")
            .create();
    String pathOfInvalidConfig2 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfInvalidConfig2).getFilePath();

    // Also create a code owner config files without issues.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/baz")
        .addCodeOwnerEmail(user.email())
        .create();

    assertThat(checkCodeOwnerConfigFilesIn(project))
        .containsExactly(
            "refs/heads/master",
                ImmutableMap.of(
                    pathOfInvalidConfig1,
                    ImmutableList.of(
                        problem(
                            expectedStatus,
                            String.format(
                                "invalid global import in '%s': '/not-a-code-owner-config' is"
                                    + " not a code owner config file",
                                pathOfInvalidConfig1))),
                    pathOfInvalidConfig2,
                    ImmutableList.of(
                        problem(
                            expectedStatus,
                            String.format(
                                "code owner email 'unknown1@example.com' in '%s' cannot be"
                                    + " resolved for admin",
                                pathOfInvalidConfig2)),
                        problem(
                            expectedStatus,
                            String.format(
                                "code owner email 'unknown2@example.com' in '%s' cannot be"
                                    + " resolved for admin",
                                pathOfInvalidConfig2)))),
            "refs/meta/config", ImmutableMap.of());
  }

  @Test
  public void issuesInDefaultCodeOwnerConfigFile() throws Exception {
    CodeOwnerConfig.Key invalidDefaultConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(RefNames.REFS_CONFIG)
            .folderPath("/")
            .addCodeOwnerEmail("unknown@example.com")
            .create();
    String pathOfInvalidConfig =
        codeOwnerConfigOperations.codeOwnerConfig(invalidDefaultConfig).getFilePath();

    assertThat(checkCodeOwnerConfigFilesIn(project))
        .containsExactly(
            "refs/heads/master",
            ImmutableMap.of(),
            RefNames.REFS_CONFIG,
            ImmutableMap.of(
                pathOfInvalidConfig,
                ImmutableList.of(
                    error(
                        String.format(
                            "code owner email 'unknown@example.com' in '%s' cannot be"
                                + " resolved for admin",
                            pathOfInvalidConfig)))));
  }

  @Test
  public void validateSpecifiedBranches() throws Exception {
    createBranch(BranchNameKey.create(project, "stable-1.0"));
    createBranch(BranchNameKey.create(project, "stable-1.1"));

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .checkCodeOwnerConfigFiles()
                .setBranches(ImmutableList.of("refs/heads/stable-1.0", "refs/heads/stable-1.1"))
                .check())
        .containsExactly(
            "refs/heads/stable-1.0", ImmutableMap.of(),
            "refs/heads/stable-1.1", ImmutableMap.of());
  }

  @Test
  public void validateSpecifiedBranches_shortNames() throws Exception {
    createBranch(BranchNameKey.create(project, "stable-1.0"));
    createBranch(BranchNameKey.create(project, "stable-1.1"));

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .checkCodeOwnerConfigFiles()
                .setBranches(ImmutableList.of("stable-1.0", "stable-1.1"))
                .check())
        .containsExactly(
            "refs/heads/stable-1.0", ImmutableMap.of(),
            "refs/heads/stable-1.1", ImmutableMap.of());
  }

  @Test
  public void cannotValidateNonExistingBranch() throws Exception {
    UnprocessableEntityException exception =
        assertThrows(
            UnprocessableEntityException.class,
            () ->
                projectCodeOwnersApiFactory
                    .project(project)
                    .checkCodeOwnerConfigFiles()
                    .setBranches(ImmutableList.of("refs/heads/non-existing"))
                    .check());
    assertThat(exception).hasMessageThat().isEqualTo("branch refs/heads/non-existing not found");
  }

  @Test
  public void cannotValidateNonVisibleBranch() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();

    UnprocessableEntityException exception =
        assertThrows(
            UnprocessableEntityException.class,
            () ->
                projectCodeOwnersApiFactory
                    .project(project)
                    .checkCodeOwnerConfigFiles()
                    .setBranches(ImmutableList.of("refs/heads/master"))
                    .check());
    assertThat(exception).hasMessageThat().isEqualTo("branch refs/heads/master not found");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabledBranch", value = "refs/meta/config")
  public void cannotValidateDisabledBranchWithoutEnablingValidationForDisabledBranches()
      throws Exception {
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                projectCodeOwnersApiFactory
                    .project(project)
                    .checkCodeOwnerConfigFiles()
                    .setBranches(ImmutableList.of("refs/meta/config"))
                    .check());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "code owners functionality for branch refs/meta/config is disabled,"
                + " set 'validate_disabled_braches' in the input to 'true' if code owner config"
                + " files in this branch should be validated");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabledBranch", value = "refs/meta/config")
  public void validateSpecifiedBranchThatIsDisabled() throws Exception {
    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .checkCodeOwnerConfigFiles()
                .validateDisabledBranches()
                .setBranches(ImmutableList.of("refs/meta/config"))
                .check())
        .containsExactly("refs/meta/config", ImmutableMap.of());
  }

  @Test
  public void validateExactFile() throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    // Create some code owner config files with issues.
    CodeOwnerConfig.Key keyOfInvalidConfig1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addImport(
                CodeOwnerConfigReference.create(
                    CodeOwnerConfigImportMode.ALL, "/not-a-code-owner-config"))
            .create();
    String pathOfInvalidConfig1 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfInvalidConfig1).getFilePath();

    CodeOwnerConfig.Key keyOfInvalidConfig2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail("unknown1@example.com")
            .addCodeOwnerEmail("unknown2@example.com")
            .create();
    String pathOfInvalidConfig2 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfInvalidConfig2).getFilePath();

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .checkCodeOwnerConfigFiles()
                .setBranches(ImmutableList.of("master"))
                .setPath(pathOfInvalidConfig1)
                .check())
        .containsExactly(
            "refs/heads/master",
            ImmutableMap.of(
                pathOfInvalidConfig1,
                ImmutableList.of(
                    error(
                        String.format(
                            "invalid global import in '%s': '/not-a-code-owner-config' is"
                                + " not a code owner config file",
                            pathOfInvalidConfig1)))));

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .checkCodeOwnerConfigFiles()
                .setBranches(ImmutableList.of("master"))
                .setPath(pathOfInvalidConfig2)
                .check())
        .containsExactly(
            "refs/heads/master",
            ImmutableMap.of(
                pathOfInvalidConfig2,
                ImmutableList.of(
                    error(
                        String.format(
                            "code owner email 'unknown1@example.com' in '%s' cannot be"
                                + " resolved for admin",
                            pathOfInvalidConfig2)),
                    error(
                        String.format(
                            "code owner email 'unknown2@example.com' in '%s' cannot be"
                                + " resolved for admin",
                            pathOfInvalidConfig2)))));
  }

  @Test
  public void validateFilesMatchingGlob() throws Exception {
    skipTestIfImportsNotSupportedByCodeOwnersBackend();

    // Create some code owner config files with issues.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addImport(
            CodeOwnerConfigReference.create(
                CodeOwnerConfigImportMode.ALL, "/not-a-code-owner-config"))
        .create();

    CodeOwnerConfig.Key keyOfInvalidConfig2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addCodeOwnerEmail("unknown1@example.com")
            .create();
    String pathOfInvalidConfig2 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfInvalidConfig2).getFilePath();

    CodeOwnerConfig.Key keyOfInvalidConfig3 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail("unknown2@example.com")
            .create();
    String pathOfInvalidConfig3 =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfInvalidConfig3).getFilePath();

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .checkCodeOwnerConfigFiles()
                .setBranches(ImmutableList.of("master"))
                .setPath("/foo/**")
                .check())
        .containsExactly(
            "refs/heads/master",
            ImmutableMap.of(
                pathOfInvalidConfig2,
                ImmutableList.of(
                    error(
                        String.format(
                            "code owner email 'unknown1@example.com' in '%s' cannot be"
                                + " resolved for admin",
                            pathOfInvalidConfig2))),
                codeOwnerConfigOperations.codeOwnerConfig(keyOfInvalidConfig3).getFilePath(),
                ImmutableList.of(
                    error(
                        String.format(
                            "code owner email 'unknown2@example.com' in '%s' cannot be"
                                + " resolved for admin",
                            pathOfInvalidConfig3)))));
  }

  @Test
  public void allIssuesAreReturnedIfNoLevelIsSpecified() throws Exception {
    testIssuesAreFilteredByVerbosity(/* verbosity= */ null, ConsistencyProblemInfo.Status.ERROR);
  }

  @Test
  public void allIssuesAreReturnedIfLevelIsSetToWarning() throws Exception {
    testIssuesAreFilteredByVerbosity(
        ConsistencyProblemInfo.Status.WARNING, ConsistencyProblemInfo.Status.ERROR);
  }

  @Test
  public void onlyFatalAndErrorIssuesAreReturnedIfLevelIsSetToError() throws Exception {
    testIssuesAreFilteredByVerbosity(
        ConsistencyProblemInfo.Status.ERROR, ConsistencyProblemInfo.Status.ERROR);
  }

  @Test
  public void onlyFatalIssuesAreReturnedIfLevelIsSetToFatal() throws Exception {
    testIssuesAreFilteredByVerbosity(
        ConsistencyProblemInfo.Status.FATAL, ConsistencyProblemInfo.Status.ERROR);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableCodeOwners", value = "false")
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableImports", value = "false")
  public void
      allIssuesAreReturnedIfNoLevelIsSpecified_issuesInCodeOwnerConfigFileReportedAsWarnings()
          throws Exception {
    testIssuesAreFilteredByVerbosity(/* verbosity= */ null, ConsistencyProblemInfo.Status.WARNING);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableCodeOwners", value = "false")
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableImports", value = "false")
  public void
      allIssuesAreReturnedIfLevelIsSetToWarning_issuesInCodeOwnerConfigFileReportedAsWarnings()
          throws Exception {
    testIssuesAreFilteredByVerbosity(
        ConsistencyProblemInfo.Status.WARNING, ConsistencyProblemInfo.Status.WARNING);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableCodeOwners", value = "false")
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableImports", value = "false")
  public void
      onlyFatalAndErrorIssuesAreReturnedIfLevelIsSetToError_issuesInCodeOwnerConfigFileReportedAsWarnings()
          throws Exception {
    testIssuesAreFilteredByVerbosity(
        ConsistencyProblemInfo.Status.ERROR, ConsistencyProblemInfo.Status.WARNING);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableCodeOwners", value = "false")
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableImports", value = "false")
  public void
      onlyFatalIssuesAreReturnedIfLevelIsSetToFatal_issuesInCodeOwnerConfigFileReportedAsWarnings()
          throws Exception {
    testIssuesAreFilteredByVerbosity(
        ConsistencyProblemInfo.Status.FATAL, ConsistencyProblemInfo.Status.WARNING);
  }

  private void testIssuesAreFilteredByVerbosity(
      @Nullable ConsistencyProblemInfo.Status verbosity,
      ConsistencyProblemInfo.Status expectedStatus)
      throws Exception {
    // create a non-parseable code owner config, that will be reported as fatal
    String pathOfNonParseableCodeOwnerConfig = "/" + getCodeOwnerConfigFileName();
    createNonParseableCodeOwnerConfig(pathOfNonParseableCodeOwnerConfig);

    // create an invalid code owner config, that will be reported as error
    CodeOwnerConfig.Key keyOfInvalidConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addCodeOwnerEmail("unknown@example.com")
            .create();
    String pathOfInvalidConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfInvalidConfig).getFilePath();

    // there is currently nothing that triggers a warning

    Map<String, List<ConsistencyProblemInfo>> expectedMasterIssues = new HashMap<>();
    // the fatal issue is always expected
    expectedMasterIssues.put(
        pathOfNonParseableCodeOwnerConfig,
        ImmutableList.of(
            fatal(
                String.format(
                    "invalid code owner config file '%s' (project = %s, branch = master):\n  %s",
                    pathOfNonParseableCodeOwnerConfig,
                    project,
                    getParsingErrorMessageForNonParseableCodeOwnerConfig()))));
    if (verbosity == null
        || (ConsistencyProblemInfo.Status.ERROR.equals(verbosity)
            && (expectedStatus.equals(ConsistencyProblemInfo.Status.FATAL)
                || expectedStatus.equals(ConsistencyProblemInfo.Status.ERROR)))
        || (ConsistencyProblemInfo.Status.WARNING.equals(verbosity))) {
      expectedMasterIssues.put(
          pathOfInvalidConfig,
          ImmutableList.of(
              problem(
                  expectedStatus,
                  String.format(
                      "code owner email 'unknown@example.com' in '%s' cannot be"
                          + " resolved for admin",
                      pathOfInvalidConfig))));
    }

    Map<String, Map<String, List<ConsistencyProblemInfo>>> result =
        projectCodeOwnersApiFactory
            .project(project)
            .checkCodeOwnerConfigFiles()
            .setVerbosity(verbosity)
            .check();
    assertThat(result)
        .containsExactly(
            "refs/heads/master", expectedMasterIssues, "refs/meta/config", ImmutableMap.of());
  }

  private ConsistencyProblemInfo fatal(String message) {
    return new ConsistencyProblemInfo(ConsistencyProblemInfo.Status.FATAL, message);
  }

  private ConsistencyProblemInfo error(String message) {
    return new ConsistencyProblemInfo(ConsistencyProblemInfo.Status.ERROR, message);
  }

  private ConsistencyProblemInfo problem(ConsistencyProblemInfo.Status status, String message) {
    return new ConsistencyProblemInfo(status, message);
  }

  private Map<String, Map<String, List<ConsistencyProblemInfo>>> checkCodeOwnerConfigFilesIn(
      Project.NameKey projectName) throws RestApiException {
    return projectCodeOwnersApiFactory.project(projectName).checkCodeOwnerConfigFiles().check();
  }
}
