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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.codeowners.JgitPath;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportMode;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import com.google.gerrit.plugins.codeowners.config.BackendConfig;
import com.google.gerrit.plugins.codeowners.config.StatusConfig;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.Before;
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

  private BackendConfig backendConfig;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    backendConfig = plugin.getSysInjector().getInstance(BackendConfig.class);
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
    createInvalidCodeOwnerConfig(codeOwnerConfigPath);

    assertThat(checkCodeOwnerConfigFilesIn(project))
        .containsExactly(
            "refs/heads/master",
                ImmutableMap.of(
                    codeOwnerConfigPath,
                    ImmutableList.of(
                        error(
                            String.format(
                                "invalid code owner config file '%s':\n  %s",
                                codeOwnerConfigPath,
                                getParsingErrorMessage(
                                    ImmutableMap.of(
                                        FindOwnersBackend.class,
                                        "invalid line: INVALID",
                                        ProtoBackend.class,
                                        "1:8: Expected \"{\".")))))),
            "refs/meta/config", ImmutableMap.of());
  }

  @Test
  public void issuesInCodeOwnerConfigFile() throws Exception {
    // imports are not supported for the proto backend
    assume().that(backendConfig.getDefaultBackend()).isNotInstanceOf(ProtoBackend.class);

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

    CodeOwnerConfig.Key keyOfInvalidConfig2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail("unknown1@example.com")
            .addCodeOwnerEmail("unknown2@example.com")
            .create();

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
                    getCodeOwnerConfigFilePath(keyOfInvalidConfig1),
                        ImmutableList.of(
                            error(
                                String.format(
                                    "invalid global import in '%s': '/not-a-code-owner-config' is"
                                        + " not a code owner config file",
                                    getCodeOwnerConfigFilePath(keyOfInvalidConfig1)))),
                    getCodeOwnerConfigFilePath(keyOfInvalidConfig2),
                        ImmutableList.of(
                            error(
                                String.format(
                                    "code owner email 'unknown1@example.com' in '%s' cannot be"
                                        + " resolved for admin",
                                    getCodeOwnerConfigFilePath(keyOfInvalidConfig2))),
                            error(
                                String.format(
                                    "code owner email 'unknown2@example.com' in '%s' cannot be"
                                        + " resolved for admin",
                                    getCodeOwnerConfigFilePath(keyOfInvalidConfig2))))),
            "refs/meta/config", ImmutableMap.of());
  }

  @Test
  public void validateSpecifiedBranches() throws Exception {
    gApi.projects().name(project.get()).branch("stable-1.0").create(new BranchInput());
    gApi.projects().name(project.get()).branch("stable-1.1").create(new BranchInput());

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
    gApi.projects().name(project.get()).branch("stable-1.0").create(new BranchInput());
    gApi.projects().name(project.get()).branch("stable-1.1").create(new BranchInput());

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
    // imports are not supported for the proto backend
    assume().that(backendConfig.getDefaultBackend()).isNotInstanceOf(ProtoBackend.class);

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

    CodeOwnerConfig.Key keyOfInvalidConfig2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail("unknown1@example.com")
            .addCodeOwnerEmail("unknown2@example.com")
            .create();

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .checkCodeOwnerConfigFiles()
                .setBranches(ImmutableList.of("master"))
                .setPath(getCodeOwnerConfigFilePath(keyOfInvalidConfig1))
                .check())
        .containsExactly(
            "refs/heads/master",
            ImmutableMap.of(
                getCodeOwnerConfigFilePath(keyOfInvalidConfig1),
                ImmutableList.of(
                    error(
                        String.format(
                            "invalid global import in '%s': '/not-a-code-owner-config' is"
                                + " not a code owner config file",
                            getCodeOwnerConfigFilePath(keyOfInvalidConfig1))))));

    assertThat(
            projectCodeOwnersApiFactory
                .project(project)
                .checkCodeOwnerConfigFiles()
                .setBranches(ImmutableList.of("master"))
                .setPath(getCodeOwnerConfigFilePath(keyOfInvalidConfig2))
                .check())
        .containsExactly(
            "refs/heads/master",
            ImmutableMap.of(
                getCodeOwnerConfigFilePath(keyOfInvalidConfig2),
                ImmutableList.of(
                    error(
                        String.format(
                            "code owner email 'unknown1@example.com' in '%s' cannot be"
                                + " resolved for admin",
                            getCodeOwnerConfigFilePath(keyOfInvalidConfig2))),
                    error(
                        String.format(
                            "code owner email 'unknown2@example.com' in '%s' cannot be"
                                + " resolved for admin",
                            getCodeOwnerConfigFilePath(keyOfInvalidConfig2))))));
  }

  @Test
  public void validateFilesMatchingGlob() throws Exception {
    // imports are not supported for the proto backend
    assume().that(backendConfig.getDefaultBackend()).isNotInstanceOf(ProtoBackend.class);

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

    CodeOwnerConfig.Key keyOfInvalidConfig3 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail("unknown2@example.com")
            .create();

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
                getCodeOwnerConfigFilePath(keyOfInvalidConfig2),
                ImmutableList.of(
                    error(
                        String.format(
                            "code owner email 'unknown1@example.com' in '%s' cannot be"
                                + " resolved for admin",
                            getCodeOwnerConfigFilePath(keyOfInvalidConfig2)))),
                getCodeOwnerConfigFilePath(keyOfInvalidConfig3),
                ImmutableList.of(
                    error(
                        String.format(
                            "code owner email 'unknown2@example.com' in '%s' cannot be"
                                + " resolved for admin",
                            getCodeOwnerConfigFilePath(keyOfInvalidConfig3))))));
  }

  private ConsistencyProblemInfo error(String message) {
    return new ConsistencyProblemInfo(ConsistencyProblemInfo.Status.ERROR, message);
  }

  private Map<String, Map<String, List<ConsistencyProblemInfo>>> checkCodeOwnerConfigFilesIn(
      Project.NameKey projectName) throws RestApiException {
    return projectCodeOwnersApiFactory.project(projectName).checkCodeOwnerConfigFiles().check();
  }

  private String getCodeOwnerConfigFilePath(CodeOwnerConfig.Key codeOwnerConfigKey) {
    return backendConfig.getDefaultBackend().getFilePath(codeOwnerConfigKey).toString();
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

  private void createInvalidCodeOwnerConfig(String path) throws Exception {
    disableCodeOwnersForProject(project);
    String changeId =
        createChange("Add invalid code owners file", JgitPath.of(path).get(), "INVALID")
            .getChangeId();
    approve(changeId);
    gApi.changes().id(changeId).current().submit();
    setCodeOwnersConfig(project, null, StatusConfig.KEY_DISABLED, "false");
  }

  private String getParsingErrorMessage(
      ImmutableMap<Class<? extends CodeOwnerBackend>, String> messagesByBackend) {
    CodeOwnerBackend codeOwnerBackend = backendConfig.getDefaultBackend();
    assertThat(messagesByBackend).containsKey(codeOwnerBackend.getClass());
    return messagesByBackend.get(codeOwnerBackend.getClass());
  }
}
