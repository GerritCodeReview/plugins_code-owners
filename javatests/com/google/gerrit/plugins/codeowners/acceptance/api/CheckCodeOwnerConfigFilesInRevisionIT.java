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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.backend.config.BackendConfig;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersCodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoCodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

/**
 * Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.CheckCodeOwnerConfigFilesInRevision} REST endpoint.
 */
public class CheckCodeOwnerConfigFilesInRevisionIT extends AbstractCodeOwnersIT {
  private static final ObjectId TEST_REVISION =
      ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

  private BackendConfig backendConfig;
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
  public void noCodeOwnerConfigFile() throws Exception {
    assertThat(checkCodeOwnerConfigFilesIn(createChange().getChangeId())).isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabledBranch", value = "refs/heads/master")
  public void noCodeOwnerConfigFile_codeOwnersFunctionalityIsDisabled() throws Exception {
    assertThat(checkCodeOwnerConfigFilesIn(createChange().getChangeId())).isEmpty();
  }

  @Test
  public void codeOwnerConfigFileWithoutIssues() throws Exception {
    testCodeOwnerConfigFileWithoutIssues();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabledBranch", value = "refs/heads/master")
  public void codeOwnerConfigFileWithoutIssues_codeOwnersFunctionalityIsDisabled()
      throws Exception {
    testCodeOwnerConfigFileWithoutIssues();
  }

  private void testCodeOwnerConfigFileWithoutIssues() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");
    String codeOwnerConfigPath =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath();
    String changeId =
        createChange(
                "Add code owners",
                JgitPath.of(codeOwnerConfigPath).get(),
                format(
                    CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                        .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
                        .build()))
            .getChangeId();
    assertThat(checkCodeOwnerConfigFilesIn(changeId))
        .containsExactly(codeOwnerConfigPath, ImmutableList.of());
  }

  @Test
  public void nonParseableCodeOwnerConfigFile() throws Exception {
    testNonParseableCodeOwnerConfigFile();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabledBranch", value = "refs/heads/master")
  public void nonParseableCodeOwnerConfigFile_codeOwnersFunctionalityIsDisabled() throws Exception {
    testNonParseableCodeOwnerConfigFile();
  }

  private void testNonParseableCodeOwnerConfigFile() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");
    String codeOwnerConfigPath =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath();

    disableCodeOwnersForProject(project);
    String changeId =
        createChange("Add code owners", JgitPath.of(codeOwnerConfigPath).get(), "INVALID")
            .getChangeId();
    enableCodeOwnersForProject(project);

    assertThat(checkCodeOwnerConfigFilesIn(changeId))
        .containsExactly(
            codeOwnerConfigPath,
            ImmutableList.of(
                fatal(
                    String.format(
                        "invalid code owner config file '%s' (project = %s, branch = master):\n  %s",
                        codeOwnerConfigPath,
                        project,
                        getParsingErrorMessage(
                            ImmutableMap.of(
                                FindOwnersBackend.class,
                                "invalid line: INVALID",
                                ProtoBackend.class,
                                "1:8: Expected \"{\"."))))));
  }

  @Test
  public void codeOwnerConfigFileWithIssues() throws Exception {
    testCodeOwnerConfigFileWithIssues();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabledBranch", value = "refs/heads/master")
  public void codeOwnerConfigFileWithIssues_codeOwnersFunctionalityIsDisabled() throws Exception {
    testCodeOwnerConfigFileWithIssues();
  }

  private void testCodeOwnerConfigFileWithIssues() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");
    String codeOwnerConfigPath =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath();
    String unknownEmail1 = "non-existing-email@example.com";
    String unknownEmail2 = "another-unknown-email@example.com";

    disableCodeOwnersForProject(project);
    String changeId =
        createChange(
                "Add code owners",
                JgitPath.of(codeOwnerConfigPath).get(),
                format(
                    CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                        .addCodeOwnerSet(
                            CodeOwnerSet.createWithoutPathExpressions(
                                unknownEmail1, admin.email(), unknownEmail2))
                        .build()))
            .getChangeId();
    enableCodeOwnersForProject(project);

    Map<String, List<ConsistencyProblemInfo>> problemsByPath =
        checkCodeOwnerConfigFilesIn(changeId);
    assertThat(problemsByPath.keySet()).containsExactly(codeOwnerConfigPath);
    assertThat(problemsByPath.get(codeOwnerConfigPath))
        .containsExactly(
            error(
                String.format(
                    "code owner email '%s' in '%s' cannot be" + " resolved for admin",
                    unknownEmail1, codeOwnerConfigPath)),
            error(
                String.format(
                    "code owner email '%s' in '%s' cannot be" + " resolved for admin",
                    unknownEmail2, codeOwnerConfigPath)));
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void validationIsDoneFromThePerspectiveOfTheUploader() throws Exception {
    // Create a new user that is not a member of any group. This means 'user' and 'admin' are not
    // visible to this user since they do not share any group.
    TestAccount user2 = accountCreator.user2();

    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");
    String codeOwnerConfigPath =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath();

    // Upload the change as user2 who cannot see 'admin' and 'user'.
    disableCodeOwnersForProject(project);
    String changeId =
        createChange(
                user2,
                "Add code owners",
                JgitPath.of(codeOwnerConfigPath).get(),
                format(
                    CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                        .addCodeOwnerSet(
                            CodeOwnerSet.createWithoutPathExpressions(admin.email(), user.email()))
                        .build()))
            .getChangeId();
    enableCodeOwnersForProject(project);

    // The validation request is done by 'admin' which can see 'admin' and 'user', however the
    // validation is performed from the perspective of the uploader which is 'user2' and 'user2'
    // cannot see 'admin' and 'user.
    Map<String, List<ConsistencyProblemInfo>> problemsByPath =
        checkCodeOwnerConfigFilesIn(changeId);
    assertThat(problemsByPath.keySet()).containsExactly(codeOwnerConfigPath);
    assertThat(problemsByPath.get(codeOwnerConfigPath))
        .containsExactly(
            error(
                String.format(
                    "code owner email '%s' in '%s' cannot be" + " resolved for user2",
                    admin.email(), codeOwnerConfigPath)),
            error(
                String.format(
                    "code owner email '%s' in '%s' cannot be" + " resolved for user2",
                    user.email(), codeOwnerConfigPath)));
  }

  @Test
  public void nonModifiedCodeOwnerConfigFilesAreNotValidated() throws Exception {
    // Create a code owner config file with issues in the repository.
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");
    String codeOwnerConfigPath =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath();
    String unknownEmail1 = "non-existing-email@example.com";
    String unknownEmail2 = "another-unknown-email@example.com";
    disableCodeOwnersForProject(project);
    String changeId =
        createChange(
                "Add code owners",
                JgitPath.of(codeOwnerConfigPath).get(),
                format(
                    CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                        .addCodeOwnerSet(
                            CodeOwnerSet.createWithoutPathExpressions(
                                unknownEmail1, admin.email(), unknownEmail2))
                        .build()))
            .getChangeId();
    approve(changeId);
    gApi.changes().id(changeId).current().submit();
    enableCodeOwnersForProject(project);

    // Create a change that adds another code owner config file without issues.
    CodeOwnerConfig.Key codeOwnerConfigKey2 = createCodeOwnerConfigKey("/foo/");
    String codeOwnerConfigPath2 =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey2).getFilePath();
    String changeId2 =
        createChange(
                "Add code owners",
                JgitPath.of(codeOwnerConfigPath2).get(),
                format(
                    CodeOwnerConfig.builder(codeOwnerConfigKey2, TEST_REVISION)
                        .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
                        .build()))
            .getChangeId();
    assertThat(checkCodeOwnerConfigFilesIn(changeId2))
        .containsExactly(codeOwnerConfigPath2, ImmutableList.of());
  }

  @Test
  public void deletedCodeOwnerConfigFilesAreNotValidated() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createCodeOwnerConfigKey("/");
    String codeOwnerConfigPath =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath();
    disableCodeOwnersForProject(project);
    String changeId = createChangeWithFileDeletion(codeOwnerConfigPath);
    enableCodeOwnersForProject(project);
    assertThat(checkCodeOwnerConfigFilesIn(changeId)).isEmpty();
  }

  @Test
  public void validateExactFile() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey1 = createCodeOwnerConfigKey("/");
    CodeOwnerConfig.Key codeOwnerConfigKey2 = createCodeOwnerConfigKey("/foo/");
    String codeOwnerConfigPath1 =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey1).getFilePath();
    String codeOwnerConfigPath2 =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey2).getFilePath();
    String unknownEmail1 = "non-existing-email@example.com";
    String unknownEmail2 = "another-unknown-email@example.com";

    disableCodeOwnersForProject(project);
    String changeId =
        createChange(
                "Add code owners",
                ImmutableMap.of(
                    JgitPath.of(codeOwnerConfigPath1).get(),
                    format(
                        CodeOwnerConfig.builder(codeOwnerConfigKey1, TEST_REVISION)
                            .addCodeOwnerSet(
                                CodeOwnerSet.createWithoutPathExpressions(
                                    unknownEmail1, admin.email()))
                            .build()),
                    JgitPath.of(codeOwnerConfigPath2).get(),
                    format(
                        CodeOwnerConfig.builder(codeOwnerConfigKey2, TEST_REVISION)
                            .addCodeOwnerSet(
                                CodeOwnerSet.createWithoutPathExpressions(
                                    unknownEmail2, admin.email()))
                            .build())))
            .getChangeId();
    enableCodeOwnersForProject(project);

    Map<String, List<ConsistencyProblemInfo>> problemsByPath =
        changeCodeOwnersApiFactory
            .change(changeId)
            .current()
            .checkCodeOwnerConfigFiles()
            .setPath(codeOwnerConfigPath1)
            .check();
    assertThat(problemsByPath)
        .containsExactly(
            codeOwnerConfigPath1,
            ImmutableList.of(
                error(
                    String.format(
                        "code owner email '%s' in '%s' cannot be" + " resolved for admin",
                        unknownEmail1, codeOwnerConfigPath1))));
  }

  @Test
  public void validateFilesMatchingGlob() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey1 = createCodeOwnerConfigKey("/");
    CodeOwnerConfig.Key codeOwnerConfigKey2 = createCodeOwnerConfigKey("/foo/");
    CodeOwnerConfig.Key codeOwnerConfigKey3 = createCodeOwnerConfigKey("/foo/bar/");
    String codeOwnerConfigPath1 =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey1).getFilePath();
    String codeOwnerConfigPath2 =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey2).getFilePath();
    String codeOwnerConfigPath3 =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey3).getFilePath();
    String unknownEmail1 = "non-existing-email@example.com";
    String unknownEmail2 = "another-unknown-email@example.com";
    String unknownEmail3 = "yet-another-unknown-email@example.com";

    disableCodeOwnersForProject(project);
    String changeId =
        createChange(
                "Add code owners",
                ImmutableMap.of(
                    JgitPath.of(codeOwnerConfigPath1).get(),
                    format(
                        CodeOwnerConfig.builder(codeOwnerConfigKey1, TEST_REVISION)
                            .addCodeOwnerSet(
                                CodeOwnerSet.createWithoutPathExpressions(
                                    unknownEmail1, admin.email()))
                            .build()),
                    JgitPath.of(codeOwnerConfigPath2).get(),
                    format(
                        CodeOwnerConfig.builder(codeOwnerConfigKey2, TEST_REVISION)
                            .addCodeOwnerSet(
                                CodeOwnerSet.createWithoutPathExpressions(
                                    unknownEmail2, admin.email()))
                            .build()),
                    JgitPath.of(codeOwnerConfigPath3).get(),
                    format(
                        CodeOwnerConfig.builder(codeOwnerConfigKey3, TEST_REVISION)
                            .addCodeOwnerSet(
                                CodeOwnerSet.createWithoutPathExpressions(
                                    unknownEmail3, admin.email()))
                            .build())))
            .getChangeId();
    enableCodeOwnersForProject(project);

    Map<String, List<ConsistencyProblemInfo>> problemsByPath =
        changeCodeOwnersApiFactory
            .change(changeId)
            .current()
            .checkCodeOwnerConfigFiles()
            .setPath("/foo/**")
            .check();
    assertThat(problemsByPath)
        .containsExactly(
            codeOwnerConfigPath2,
            ImmutableList.of(
                error(
                    String.format(
                        "code owner email '%s' in '%s' cannot be" + " resolved for admin",
                        unknownEmail2, codeOwnerConfigPath2))),
            codeOwnerConfigPath3,
            ImmutableList.of(
                error(
                    String.format(
                        "code owner email '%s' in '%s' cannot be" + " resolved for admin",
                        unknownEmail3, codeOwnerConfigPath3))));
  }

  @Test
  public void allIssuesAreReturnedIfNoLevelIsSpecified() throws Exception {
    testIssuesAreFilteredByVerbosity(
        /** verbosity */
        null);
  }

  @Test
  public void allIssuesAreReturnedIfLevelIsSetToWarning() throws Exception {
    testIssuesAreFilteredByVerbosity(ConsistencyProblemInfo.Status.WARNING);
  }

  @Test
  public void onlyFatalAndErrorIssuesAreReturnedIfLevelIsSetToError() throws Exception {
    testIssuesAreFilteredByVerbosity(ConsistencyProblemInfo.Status.ERROR);
  }

  @Test
  public void onlyFatalIssuesAreReturnedIfLevelIsSetToFatal() throws Exception {
    testIssuesAreFilteredByVerbosity(ConsistencyProblemInfo.Status.FATAL);
  }

  private void testIssuesAreFilteredByVerbosity(@Nullable ConsistencyProblemInfo.Status verbosity)
      throws Exception {
    CodeOwnerConfig.Key keyOfNonParseableCodeOwnerConfig = createCodeOwnerConfigKey("/");
    String pathOfNonParseableCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfNonParseableCodeOwnerConfig).getFilePath();

    CodeOwnerConfig.Key keyOfInvalidCodeOwnerConfig = createCodeOwnerConfigKey("/foo/");
    String pathOfInvalidCodeOwnerConfig =
        codeOwnerConfigOperations.codeOwnerConfig(keyOfInvalidCodeOwnerConfig).getFilePath();
    String unknownEmail = "unknown@example.com";

    // create a change with a) a non-parseable code owner config that will be reported as fatal and
    // b) an invalid code owner config with an unknown email that will be reported as error
    // (there is currently nothing that triggers a warning)
    disableCodeOwnersForProject(project);
    String changeId =
        createChange(
                "Add code owners",
                ImmutableMap.of(
                    JgitPath.of(pathOfNonParseableCodeOwnerConfig).get(),
                    "INVALID",
                    JgitPath.of(pathOfInvalidCodeOwnerConfig).get(),
                    format(
                        CodeOwnerConfig.builder(keyOfInvalidCodeOwnerConfig, TEST_REVISION)
                            .addCodeOwnerSet(
                                CodeOwnerSet.createWithoutPathExpressions(unknownEmail))
                            .build())))
            .getChangeId();
    enableCodeOwnersForProject(project);

    Map<String, List<ConsistencyProblemInfo>> expectedIssues = new HashMap<>();
    // the fatal issue is always expected
    expectedIssues.put(
        pathOfNonParseableCodeOwnerConfig,
        ImmutableList.of(
            fatal(
                String.format(
                    "invalid code owner config file '%s' (project = %s, branch = master):\n  %s",
                    pathOfNonParseableCodeOwnerConfig,
                    project,
                    getParsingErrorMessage(
                        ImmutableMap.of(
                            FindOwnersBackend.class,
                            "invalid line: INVALID",
                            ProtoBackend.class,
                            "1:8: Expected \"{\"."))))));
    if (verbosity == null
        || ConsistencyProblemInfo.Status.ERROR.equals(verbosity)
        || ConsistencyProblemInfo.Status.WARNING.equals(verbosity)) {
      expectedIssues.put(
          pathOfInvalidCodeOwnerConfig,
          ImmutableList.of(
              error(
                  String.format(
                      "code owner email '%s' in '%s' cannot be" + " resolved for admin",
                      unknownEmail, pathOfInvalidCodeOwnerConfig))));
    } else {
      expectedIssues.put(pathOfInvalidCodeOwnerConfig, ImmutableList.of());
    }

    Map<String, List<ConsistencyProblemInfo>> result =
        changeCodeOwnersApiFactory
            .change(changeId)
            .current()
            .checkCodeOwnerConfigFiles()
            .setVerbosity(verbosity)
            .check();
    assertThat(result).isEqualTo(expectedIssues);
  }

  private ConsistencyProblemInfo fatal(String message) {
    return new ConsistencyProblemInfo(ConsistencyProblemInfo.Status.FATAL, message);
  }

  private ConsistencyProblemInfo error(String message) {
    return new ConsistencyProblemInfo(ConsistencyProblemInfo.Status.ERROR, message);
  }

  private Map<String, List<ConsistencyProblemInfo>> checkCodeOwnerConfigFilesIn(String changeId)
      throws RestApiException {
    return changeCodeOwnersApiFactory
        .change(changeId)
        .current()
        .checkCodeOwnerConfigFiles()
        .check();
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
