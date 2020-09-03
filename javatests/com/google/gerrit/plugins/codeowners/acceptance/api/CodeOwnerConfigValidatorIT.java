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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.git.ObjectIds;
import com.google.gerrit.plugins.codeowners.JgitPath;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import com.google.gerrit.plugins.codeowners.config.BackendConfig;
import com.google.gerrit.plugins.codeowners.config.StatusConfig;
import com.google.gerrit.plugins.codeowners.testing.backend.TestCodeOwnerConfigFormatter;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@code com.google.gerrit.plugins.codeowners.validation.CodeOwnerConfigValidator}. */
public class CodeOwnerConfigValidatorIT extends AbstractCodeOwnersIT {
  private static final ObjectId TEST_REVISION =
      ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

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
            "warning: commit %s: code owner email '%s' in '%s' cannot be resolved",
            abbreviateName(r.getCommit()),
            unknownEmail1,
            getCodeOwnerConfigFilePath(codeOwnerConfigKey)));
    r.assertMessage(
        String.format(
            "warning: commit %s: code owner email '%s' in '%s' cannot be resolved",
            abbreviateName(r.getCommit()),
            unknownEmail2,
            getCodeOwnerConfigFilePath(codeOwnerConfigKey)));
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
            "warning: commit %s: code owner email '%s' in '%s' cannot be resolved",
            abbreviateName(r.getCommit()),
            unknownEmail,
            getCodeOwnerConfigFilePath(codeOwnerConfigKey)));
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
            "error: commit %s: code owner email '%s' in '%s' cannot be resolved",
            abbreviateName(r.getCommit()),
            unknownEmail1,
            getCodeOwnerConfigFilePath(codeOwnerConfigKey)));
    r.assertMessage(
        String.format(
            "error: commit %s: code owner email '%s' in '%s' cannot be resolved",
            abbreviateName(r.getCommit()),
            unknownEmail2,
            getCodeOwnerConfigFilePath(codeOwnerConfigKey)));
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
            "warning: commit %s: code owner email '%s' in '%s' cannot be resolved",
            abbreviateName(r.getCommit()),
            unknownEmail1,
            getCodeOwnerConfigFilePath(codeOwnerConfigKey)));
    r.assertMessage(
        String.format(
            "error: commit %s: code owner email '%s' in '%s' cannot be resolved",
            abbreviateName(r.getCommit()),
            unknownEmail2,
            getCodeOwnerConfigFilePath(codeOwnerConfigKey)));
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
