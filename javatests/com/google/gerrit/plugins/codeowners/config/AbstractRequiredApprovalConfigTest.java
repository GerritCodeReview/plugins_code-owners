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

package com.google.gerrit.plugins.codeowners.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS;
import static com.google.gerrit.plugins.codeowners.testing.RequiredApprovalSubject.assertThat;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.project.ProjectLevelConfig;
import com.google.gerrit.server.project.ProjectState;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

/** Tests for subclasses of {@link AbstractRequiredApprovalConfig}. */
public abstract class AbstractRequiredApprovalConfigTest extends AbstractCodeOwnersTest {
  /** Must return the {@link AbstractRequiredApprovalConfig} that should be tested. */
  protected abstract AbstractRequiredApprovalConfig getRequiredApprovalConfig();

  protected void testCannotGetIfGlobalConfigIsInvalid(String invalidValue) throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () -> getRequiredApprovalConfig().get(projectState, new Config()));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Invalid configuration of the code-owners plugin. Required approval '%s' that is"
                    + " configured in gerrit.config (parameter plugin.code-owners.%s) is"
                    + " invalid: Invalid format, expected '<label-name>+<label-value>'.",
                invalidValue, getRequiredApprovalConfig().getConfigKey()));
  }

  @Test
  public void cannotGetForNullProjectState() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> getRequiredApprovalConfig().get(/* projectState= */ null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("projectState");
  }

  @Test
  public void cannotGetForNullConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> getRequiredApprovalConfig().get(projectState, /* pluginConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void getWhenRequiredApprovalIsNotSet() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    assertThat(getRequiredApprovalConfig().get(projectState, new Config())).isEmpty();
  }

  @Test
  public void getFromPluginConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS,
        /* subsection= */ null,
        getRequiredApprovalConfig().getConfigKey(),
        "Code-Review+2");
    ImmutableList<RequiredApproval> requiredApproval =
        getRequiredApprovalConfig().get(projectState, cfg);
    assertThat(requiredApproval).hasSize(1);
    assertThat(requiredApproval).element(0).hasLabelNameThat().isEqualTo("Code-Review");
    assertThat(requiredApproval).element(0).hasValueThat().isEqualTo(2);
  }

  @Test
  public void cannotGetFromPluginConfigIfConfigIsInvalid() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS,
        /* subsection= */ null,
        getRequiredApprovalConfig().getConfigKey(),
        "INVALID");
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () -> getRequiredApprovalConfig().get(projectState, cfg));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Invalid configuration of the code-owners plugin. Required approval 'INVALID' that is"
                    + " configured in code-owners.config (parameter codeOwners.%s) is"
                    + " invalid: Invalid format, expected '<label-name>+<label-value>'.",
                getRequiredApprovalConfig().getConfigKey()));
  }

  @Test
  public void cannotValidateProjectLevelConfigWithNullProjectState() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                getRequiredApprovalConfig()
                    .validateProjectLevelConfig(
                        /* projectState= */ null,
                        "code-owners.config",
                        new ProjectLevelConfig.Bare("code-owners.config")));
    assertThat(npe).hasMessageThat().isEqualTo("projectState");
  }

  @Test
  public void cannotValidateProjectLevelConfigWithNullFileName() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                getRequiredApprovalConfig()
                    .validateProjectLevelConfig(
                        projectState,
                        /* fileName= */ null,
                        new ProjectLevelConfig.Bare("code-owners.config")));
    assertThat(npe).hasMessageThat().isEqualTo("fileName");
  }

  @Test
  public void cannotValidateProjectLevelConfigWithNullProjectLevelConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                getRequiredApprovalConfig()
                    .validateProjectLevelConfig(
                        projectState, "code-owners.config", /* projectLevelConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("projectLevelConfig");
  }

  @Test
  public void validateEmptyProjectLevelConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    ImmutableList<CommitValidationMessage> commitValidationMessage =
        getRequiredApprovalConfig()
            .validateProjectLevelConfig(
                projectState,
                "code-owners.config",
                new ProjectLevelConfig.Bare("code-owners.config"));
    assertThat(commitValidationMessage).isEmpty();
  }

  @Test
  public void validateValidProjectLevelConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    ProjectLevelConfig.Bare cfg = new ProjectLevelConfig.Bare("code-owners.config");
    cfg.getConfig()
        .setString(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            getRequiredApprovalConfig().getConfigKey(),
            "Code-Review+2");
    ImmutableList<CommitValidationMessage> commitValidationMessage =
        getRequiredApprovalConfig()
            .validateProjectLevelConfig(projectState, "code-owners.config", cfg);
    assertThat(commitValidationMessage).isEmpty();
  }

  @Test
  public void validateInvalidProjectLevelConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    ProjectLevelConfig.Bare cfg = new ProjectLevelConfig.Bare("code-owners.config");
    cfg.getConfig()
        .setString(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            getRequiredApprovalConfig().getConfigKey(),
            "INVALID");
    ImmutableList<CommitValidationMessage> commitValidationMessage =
        getRequiredApprovalConfig()
            .validateProjectLevelConfig(projectState, "code-owners.config", cfg);
    assertThat(commitValidationMessage).hasSize(1);
    assertThat(commitValidationMessage.get(0).getType()).isEqualTo(ValidationMessage.Type.ERROR);
    assertThat(commitValidationMessage.get(0).getMessage())
        .isEqualTo(
            String.format(
                "Required approval 'INVALID' that is configured in code-owners.config (parameter"
                    + " codeOwners.%s) is invalid: Invalid format, expected"
                    + " '<label-name>+<label-value>'.",
                getRequiredApprovalConfig().getConfigKey()));
  }
}
