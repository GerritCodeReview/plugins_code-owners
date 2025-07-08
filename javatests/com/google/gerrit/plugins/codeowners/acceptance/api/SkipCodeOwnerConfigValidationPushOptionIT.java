// Copyright (C) 2025 The Android Open Source Project
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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.change.TestChangeCreation;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.ValidationOptionInfo;
import com.google.gerrit.extensions.common.ValidationOptionInfos;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.backend.config.BackendConfig;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersCodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoCodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.validation.SkipCodeOwnerConfigValidationCapability;
import com.google.gerrit.plugins.codeowners.validation.SkipCodeOwnerConfigValidationPushOption;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

public class SkipCodeOwnerConfigValidationPushOptionIT extends AbstractCodeOwnersIT {
  private static final ObjectId TEST_REVISION =
      ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

  @Inject private ChangeOperations changeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

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
  public void getCodeOwnersSkipOptionAsAdmin() throws Exception {
    // Use the admin user that has the SkipCodeOwnerConfigValidationCapability global capability
    // implicitly assigned.
    requestScopeOperations.setApiUser(admin.id());

    Change.Id changeId = createChangeWithCodeOwnerConfigFile(project);
    ValidationOptionInfos validationOptionsInfos =
        gApi.changes().id(project.get(), changeId.get()).getValidationOptions();
    ValidationOptionInfos branchValidationOptionInfos =
        gApi.projects().name(project.get()).branch("refs/heads/master").getValidationOptions();
    assertThat(validationOptionsInfos.validationOptions)
        .isEqualTo(
            ImmutableList.of(
                new ValidationOptionInfo(
                    "code-owners~" + SkipCodeOwnerConfigValidationPushOption.NAME,
                    SkipCodeOwnerConfigValidationPushOption.DESCRIPTION)));

    assertThat(branchValidationOptionInfos.validationOptions)
        .isEqualTo(
            ImmutableList.of(
                new ValidationOptionInfo(
                    "code-owners~" + SkipCodeOwnerConfigValidationPushOption.NAME,
                    SkipCodeOwnerConfigValidationPushOption.DESCRIPTION)));
  }

  @Test
  public void getCodeOwnersSkipOptionAsUserWithTheSkipCodeOwnerConfigValidationCapability()
      throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(
            allowCapability("code-owners-" + SkipCodeOwnerConfigValidationCapability.ID)
                .group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    Change.Id changeId = createChangeWithCodeOwnerConfigFile(project);
    ValidationOptionInfos validationOptionsInfos =
        gApi.changes().id(project.get(), changeId.get()).getValidationOptions();
    ValidationOptionInfos branchValidationOptionInfos =
        gApi.projects().name(project.get()).branch("refs/heads/master").getValidationOptions();
    assertThat(validationOptionsInfos.validationOptions)
        .isEqualTo(
            ImmutableList.of(
                new ValidationOptionInfo(
                    "code-owners~" + SkipCodeOwnerConfigValidationPushOption.NAME,
                    SkipCodeOwnerConfigValidationPushOption.DESCRIPTION)));

    assertThat(branchValidationOptionInfos.validationOptions)
        .isEqualTo(
            ImmutableList.of(
                new ValidationOptionInfo(
                    "code-owners~" + SkipCodeOwnerConfigValidationPushOption.NAME,
                    SkipCodeOwnerConfigValidationPushOption.DESCRIPTION)));
  }

  @Test
  public void codeOwnersSkipOptionIsOmittedIfUserCannotSkipTheCodeOwnersValidation()
      throws Exception {
    // Use non-admin user that doesn't have the SkipCodeOwnerConfigValidationCapability global
    // capability.
    requestScopeOperations.setApiUser(user.id());

    Change.Id changeId = createChangeWithCodeOwnerConfigFile(project);
    ValidationOptionInfos validationOptionsInfos =
        gApi.changes().id(project.get(), changeId.get()).getValidationOptions();
    ValidationOptionInfos branchValidationOptionInfos =
        gApi.projects().name(project.get()).branch("refs/heads/master").getValidationOptions();
    assertThat(validationOptionsInfos.validationOptions).isEmpty();
    assertThat(branchValidationOptionInfos.validationOptions).isEmpty();
  }

  @GerritConfig(name = "plugin.code-owners.disabled", value = "true")
  @Test
  public void codeOwnersSkipOptionIsOmittedIfCodeOwnersFunctionalityIsDisabledForProject()
      throws Exception {
    Change.Id changeId = createChangeWithCodeOwnerConfigFile(project);
    ValidationOptionInfos validationOptionsInfos =
        gApi.changes().id(project.get(), changeId.get()).getValidationOptions();
    ValidationOptionInfos branchValidationOptionInfos =
        gApi.projects().name(project.get()).branch("refs/heads/master").getValidationOptions();
    assertThat(validationOptionsInfos.validationOptions).isEmpty();
    assertThat(branchValidationOptionInfos.validationOptions).isEmpty();
  }

  @GerritConfig(name = "plugin.code-owners.disabledBranch", value = "refs/heads/master")
  @Test
  public void codeOwnersSkipOptionIsOmittedIfCodeOwnersFunctionalityIsDisabledForBranch()
      throws Exception {
    Change.Id changeId = createChangeWithCodeOwnerConfigFile(project, "master");
    ValidationOptionInfos validationOptionsInfos =
        gApi.changes().id(project.get(), changeId.get()).getValidationOptions();
    ValidationOptionInfos branchValidationOptionInfos =
        gApi.projects().name(project.get()).branch("refs/heads/master").getValidationOptions();
    assertThat(validationOptionsInfos.validationOptions).isEmpty();
    assertThat(branchValidationOptionInfos.validationOptions).isEmpty();
  }

  @Test
  public void codeOwnersSkipOptionIsOmittedIfChangeDoesNotTouchCodeOwnerConfigs() throws Exception {
    requestScopeOperations.setApiUser(admin.id());
    Change.Id changeId = changeOperations.newChange().project(project).createV1();
    ValidationOptionInfos validationOptionsInfos =
        gApi.changes().id(project.get(), changeId.get()).getValidationOptions();
    ValidationOptionInfos branchValidationOptionInfos =
        gApi.projects().name(project.get()).branch("refs/heads/master").getValidationOptions();
    assertThat(validationOptionsInfos.validationOptions).isEmpty();
    assertThat(branchValidationOptionInfos.validationOptions)
        .isEqualTo(
            ImmutableList.of(
                new ValidationOptionInfo(
                    "code-owners~" + SkipCodeOwnerConfigValidationPushOption.NAME,
                    SkipCodeOwnerConfigValidationPushOption.DESCRIPTION)));
  }

  private Change.Id createChangeWithCodeOwnerConfigFile(Project.NameKey project) throws Exception {
    return createChangeWithCodeOwnerConfigFile(project, /* branch= */ null);
  }

  private Change.Id createChangeWithCodeOwnerConfigFile(
      Project.NameKey project, @Nullable String branch) throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    String codeOwnerConfigFile =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath();

    TestChangeCreation.Builder testChangeCreationBuilder =
        changeOperations.newChange().project(project);
    if (branch != null) {
      testChangeCreationBuilder.branch(branch);
    }

    return testChangeCreationBuilder
        .file(codeOwnerConfigFile)
        .content(
            format(
                CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                    .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
                    .build()))
        .createV1();
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
