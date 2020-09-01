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
import com.google.gerrit.git.ObjectIds;
import com.google.gerrit.plugins.codeowners.JgitPath;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import com.google.gerrit.plugins.codeowners.config.BackendConfig;
import com.google.gerrit.plugins.codeowners.config.StatusConfig;
import org.eclipse.jgit.lib.AnyObjectId;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@code com.google.gerrit.plugins.codeowners.validation.CodeOwnerConfigValidator}. */
public class CodeOwnerConfigValidatorIT extends AbstractCodeOwnersIT {
  private BackendConfig backendConfig;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    backendConfig = plugin.getSysInjector().getInstance(BackendConfig.class);
  }

  @Test
  public void nonCodeOwnerConfigFileIsNotValidated() throws Exception {
    PushOneCommit.Result r = createChange("Add arbitrary file", "arbitrary-file.txt", "INVALID");
    r.assertOkStatus();
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
  public void noValidationOnDeletionOfConfig() throws Exception {
    // Disable the code owners functionality so that we can upload an invalid config that we can
    // delete afterwards.
    disableCodeOwnersForProject(project);

    String path = JgitPath.of(getCodeOwnerConfigFilePath(createCodeOwnerConfigKey("/"))).get();
    PushOneCommit.Result r = createChange("Add code owners", path, "INVALID");
    r.assertOkStatus();

    // re-enable the code owners functionality for the project
    setCodeOwnersConfig(project, null, StatusConfig.KEY_DISABLED, "false");

    // delete the invalid code owner config file
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "Delete code owner config", path, "");
    r = push.rm("refs/for/master");
    r.assertOkStatus();
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
