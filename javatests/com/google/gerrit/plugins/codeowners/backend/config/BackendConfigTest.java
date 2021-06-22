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

package com.google.gerrit.plugins.codeowners.backend.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.codeowners.backend.config.BackendConfig.KEY_BACKEND;
import static com.google.gerrit.plugins.codeowners.backend.config.BackendConfig.KEY_PATH_EXPRESSIONS;
import static com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackendId;
import com.google.gerrit.plugins.codeowners.backend.PathExpressions;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link com.google.gerrit.plugins.codeowners.backend.config.BackendConfig}. */
public class BackendConfigTest extends AbstractCodeOwnersTest {
  private BackendConfig backendConfig;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    backendConfig = plugin.getSysInjector().getInstance(BackendConfig.class);
  }

  @Test
  public void cannotGetBackendForBranchWithNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> backendConfig.getBackendForBranch(null, BranchNameKey.create(project, "master")));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void cannotGetBackendForBranchForNullBranch() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> backendConfig.getBackendForBranch(new Config(), null));
    assertThat(npe).hasMessageThat().isEqualTo("branch");
  }

  @Test
  public void getBackendForBranchWhenBackendIsNotSet() throws Exception {
    assertThat(
            backendConfig.getBackendForBranch(
                new Config(), BranchNameKey.create(project, "master")))
        .isEmpty();
  }

  @Test
  public void getBackendForBranch() throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS,
        "refs/heads/master",
        KEY_BACKEND,
        CodeOwnerBackendId.FIND_OWNERS.getBackendId());
    assertThat(backendConfig.getBackendForBranch(cfg, BranchNameKey.create(project, "master")))
        .value()
        .isInstanceOf(FindOwnersBackend.class);
  }

  @Test
  public void getBackendForBranchShortName() throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS, "master", KEY_BACKEND, CodeOwnerBackendId.FIND_OWNERS.getBackendId());
    assertThat(backendConfig.getBackendForBranch(cfg, BranchNameKey.create(project, "master")))
        .value()
        .isInstanceOf(FindOwnersBackend.class);
  }

  @Test
  public void cannotGetBackendForBranchIfConfigIsInvalid() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, "master", KEY_BACKEND, "INVALID");
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () -> backendConfig.getBackendForBranch(cfg, BranchNameKey.create(project, "master")));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Invalid configuration of the code-owners plugin. Code owner backend 'INVALID'"
                    + " that is configured for project %s in code-owners.config (parameter"
                    + " codeOwners.master.backend) not found.",
                project.get()));
  }

  @Test
  public void cannotGetBackendForProjectWithNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> backendConfig.getBackendForProject(null, project));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void cannotGetBackendForProjectForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> backendConfig.getBackendForProject(new Config(), null));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void getBackendForProjectWhenBackendIsNotSet() throws Exception {
    assertThat(backendConfig.getBackendForProject(new Config(), project)).isEmpty();
  }

  @Test
  public void getBackendForProject() throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS, null, KEY_BACKEND, CodeOwnerBackendId.FIND_OWNERS.getBackendId());
    assertThat(backendConfig.getBackendForProject(cfg, project))
        .value()
        .isInstanceOf(FindOwnersBackend.class);
  }

  @Test
  public void cannotGetBackendForProjectIfConfigIsInvalid() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_BACKEND, "INVALID");
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () -> backendConfig.getBackendForProject(cfg, project));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Invalid configuration of the code-owners plugin. Code owner backend 'INVALID'"
                    + " that is configured for project %s in code-owners.config (parameter"
                    + " codeOwners.backend) not found.",
                project.get()));
  }

  @Test
  public void getDefaultBackend() throws Exception {
    assertThat(backendConfig.getDefaultBackend()).isInstanceOf(FindOwnersBackend.class);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = ProtoBackend.ID)
  public void getConfiguredDefaultBackend() throws Exception {
    assertThat(backendConfig.getDefaultBackend()).isInstanceOf(ProtoBackend.class);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = "INVALID")
  public void cannotGetDefaultBackendIfConfigIsInvalid() throws Exception {
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class, () -> backendConfig.getDefaultBackend());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Invalid configuration of the code-owners plugin. Code owner backend 'INVALID' that"
                + " is configured in gerrit.config (parameter plugin.code-owners.backend) not"
                + " found.");
  }

  @Test
  public void cannotGetPathExpressionsForBranchWithNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                backendConfig.getPathExpressionsForBranch(
                    null, BranchNameKey.create(project, "master")));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void cannotGetPathExpressionsForBranchForNullBranch() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> backendConfig.getPathExpressionsForBranch(new Config(), null));
    assertThat(npe).hasMessageThat().isEqualTo("branch");
  }

  @Test
  public void getPathExpressionsForBranchWhenPathExpressionsAreNotSet() throws Exception {
    assertThat(
            backendConfig.getPathExpressionsForBranch(
                new Config(), BranchNameKey.create(project, "master")))
        .isEmpty();
  }

  @Test
  public void getPathExpressionsForBranch() throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS,
        "refs/heads/master",
        KEY_PATH_EXPRESSIONS,
        PathExpressions.GLOB.name());
    assertThat(
            backendConfig.getPathExpressionsForBranch(cfg, BranchNameKey.create(project, "master")))
        .value()
        .isEqualTo(PathExpressions.GLOB);
  }

  @Test
  public void getPathExpressionsForBranchShortName() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, "master", KEY_PATH_EXPRESSIONS, PathExpressions.GLOB.name());
    assertThat(
            backendConfig.getPathExpressionsForBranch(cfg, BranchNameKey.create(project, "master")))
        .value()
        .isEqualTo(PathExpressions.GLOB);
  }

  @Test
  public void cannotGetPathExpressionsForBranchIfConfigIsInvalid() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, "master", KEY_PATH_EXPRESSIONS, "INVALID");
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () ->
                backendConfig.getPathExpressionsForBranch(
                    cfg, BranchNameKey.create(project, "master")));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Invalid configuration of the code-owners plugin. Path expressions 'INVALID'"
                    + " that are configured for project %s in code-owners.config (parameter"
                    + " codeOwners.master.pathExpressions) not found.",
                project.get()));
  }

  @Test
  public void cannotGetPathExpressionsForProjectWithNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> backendConfig.getPathExpressionsForProject(null, project));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void cannotGetPathExpressionsForProjectForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> backendConfig.getPathExpressionsForProject(new Config(), null));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void getPathExpressionsForProjectWhenBackendIsNotSet() throws Exception {
    assertThat(backendConfig.getPathExpressionsForProject(new Config(), project)).isEmpty();
  }

  @Test
  public void getPathExpressionsForProject() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_PATH_EXPRESSIONS, PathExpressions.GLOB.name());
    assertThat(backendConfig.getPathExpressionsForProject(cfg, project))
        .value()
        .isEqualTo(PathExpressions.GLOB);
  }

  @Test
  public void cannotGetPathExpressionsForProjectIfConfigIsInvalid() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_PATH_EXPRESSIONS, "INVALID");
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () -> backendConfig.getPathExpressionsForProject(cfg, project));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Invalid configuration of the code-owners plugin. Path expressions 'INVALID'"
                    + " that are configured for project %s in code-owners.config (parameter"
                    + " codeOwners.pathExpressions) not found.",
                project.get()));
  }

  @Test
  public void cannotValidateProjectLevelConfigWithNullFileName() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> backendConfig.validateProjectLevelConfig(null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("fileName");
  }

  @Test
  public void cannotValidateProjectLevelConfigWithForNullProjectLevelConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> backendConfig.validateProjectLevelConfig("code-owners.config", null));
    assertThat(npe).hasMessageThat().isEqualTo("projectLevelConfig");
  }

  @Test
  public void getDefaultPathExpressions() throws Exception {
    assertThat(backendConfig.getDefaultPathExpressions()).isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.pathExpressions", value = "GLOB")
  public void getConfiguredDefaultPathExpressions() throws Exception {
    assertThat(backendConfig.getDefaultPathExpressions()).value().isEqualTo(PathExpressions.GLOB);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.pathExpressions", value = "INVALID")
  public void cannotGetDefaultPathExpressionsIfConfigIsInvalid() throws Exception {
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () -> backendConfig.getDefaultPathExpressions());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Invalid configuration of the code-owners plugin. Path expressions 'INVALID' that"
                + " are configured in gerrit.config (parameter plugin.code-owners.pathExpressions)"
                + " not found.");
  }

  @Test
  public void validateEmptyProjectLevelConfig() throws Exception {
    ImmutableList<CommitValidationMessage> commitValidationMessage =
        backendConfig.validateProjectLevelConfig("code-owners.config", new Config());
    assertThat(commitValidationMessage).isEmpty();
  }

  @Test
  public void validateValidProjectLevelConfig() throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS, null, KEY_BACKEND, CodeOwnerBackendId.FIND_OWNERS.getBackendId());
    ImmutableList<CommitValidationMessage> commitValidationMessage =
        backendConfig.validateProjectLevelConfig("code-owners.config", cfg);
    assertThat(commitValidationMessage).isEmpty();
  }

  @Test
  public void validateInvalidProjectLevelConfig_invalidProjectConfiguration() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_BACKEND, "INVALID");
    ImmutableList<CommitValidationMessage> commitValidationMessages =
        backendConfig.validateProjectLevelConfig("code-owners.config", cfg);
    assertThat(commitValidationMessages).hasSize(1);
    CommitValidationMessage commitValidationMessage =
        Iterables.getOnlyElement(commitValidationMessages);
    assertThat(commitValidationMessage.getType()).isEqualTo(ValidationMessage.Type.ERROR);
    assertThat(commitValidationMessage.getMessage())
        .isEqualTo(
            "Code owner backend 'INVALID' that is configured in code-owners.config (parameter"
                + " codeOwners.backend) not found.");
  }

  @Test
  public void validateInvalidProjectLevelConfig_invalidBranchConfiguration() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, "someBranch", KEY_BACKEND, "INVALID");
    ImmutableList<CommitValidationMessage> commitValidationMessages =
        backendConfig.validateProjectLevelConfig("code-owners.config", cfg);
    assertThat(commitValidationMessages).hasSize(1);
    CommitValidationMessage commitValidationMessage =
        Iterables.getOnlyElement(commitValidationMessages);
    assertThat(commitValidationMessage.getType()).isEqualTo(ValidationMessage.Type.ERROR);
    assertThat(commitValidationMessage.getMessage())
        .isEqualTo(
            "Code owner backend 'INVALID' that is configured in code-owners.config (parameter"
                + " codeOwners.someBranch.backend) not found.");
  }
}
