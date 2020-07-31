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
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.project.ProjectLevelConfig;
import com.google.gerrit.server.project.ProjectState;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

/** Tests for subclasses of {@link AbstractRequiredApprovalConfig}. */
public abstract class AbstractRequiredApprovalConfigTest extends AbstractCodeOwnersTest {
  /** Must return the {@link AbstractRequiredApprovalConfig} that should be tested. */
  protected abstract AbstractRequiredApprovalConfig getRequiredApprovalConfig();

  protected void testGetFromGlobalPluginConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    Optional<RequiredApproval> requiredApproval =
        getRequiredApprovalConfig().getFromGlobalPluginConfig(projectState);
    assertThat(requiredApproval).isPresent();
    assertThat(requiredApproval.get().labelType().getName()).isEqualTo("Code-Review");
    assertThat(requiredApproval.get().value()).isEqualTo(2);
  }

  protected void testCannotGetFromGlobalPluginConfigIfConfigIsInvalid() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () -> getRequiredApprovalConfig().getFromGlobalPluginConfig(projectState));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Invalid configuration of the code-owners plugin. Required approval 'INVALID' that is"
                    + " configured in gerrit.config (parameter plugin.code-owners.%s) is"
                    + " invalid: Invalid format, expected '<label-name>+<label-value>'.",
                getRequiredApprovalConfig().getConfigKey()));
  }

  @Test
  public void cannotGetForProjectForNullProjectState() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> getRequiredApprovalConfig().getForProject(null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("projectState");
  }

  @Test
  public void cannotGetForProjectForNullConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> getRequiredApprovalConfig().getForProject(projectState, null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void getForProjectWhenRequiredApprovalIsNotSet() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    assertThat(getRequiredApprovalConfig().getForProject(projectState, new Config())).isEmpty();
  }

  @Test
  public void getForProject() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS, null, getRequiredApprovalConfig().getConfigKey(), "Code-Review+2");
    Optional<RequiredApproval> requiredApproval =
        getRequiredApprovalConfig().getForProject(projectState, cfg);
    assertThat(requiredApproval).isPresent();
    assertThat(requiredApproval.get().labelType().getName()).isEqualTo("Code-Review");
    assertThat(requiredApproval.get().value()).isEqualTo(2);
  }

  @Test
  public void cannotGetForProjectIfConfigIsInvalid() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, getRequiredApprovalConfig().getConfigKey(), "INVALID");
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () -> getRequiredApprovalConfig().getForProject(projectState, cfg));
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
  public void cannotGetFromGlobalPluginConfigForNullProjectState() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> getRequiredApprovalConfig().getFromGlobalPluginConfig(null));
    assertThat(npe).hasMessageThat().isEqualTo("projectState");
  }

  @Test
  public void getFromGlobalPluginConfigWhenRequiredApprovalIsNotSet() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    assertThat(getRequiredApprovalConfig().getFromGlobalPluginConfig(projectState)).isEmpty();
  }

  @Test
  public void cannotValidateProjectLevelConfigWithNullProjectState() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                getRequiredApprovalConfig()
                    .validateProjectLevelConfig(
                        null,
                        "code-owners.config",
                        new ProjectLevelConfig("code-owners.config", projectState)));
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
                        null,
                        new ProjectLevelConfig("code-owners.config", projectState)));
    assertThat(npe).hasMessageThat().isEqualTo("fileName");
  }

  @Test
  public void cannotValidateProjectLevelConfigWithForNullProjectLevelConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                getRequiredApprovalConfig()
                    .validateProjectLevelConfig(projectState, "code-owners.config", null));
    assertThat(npe).hasMessageThat().isEqualTo("projectLevelConfig");
  }

  @Test
  public void validateEmptyProjectLevelConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    Optional<CommitValidationMessage> commitValidationMessage =
        getRequiredApprovalConfig()
            .validateProjectLevelConfig(
                projectState,
                "code-owners.config",
                new ProjectLevelConfig("code-owners.config", projectState));
    assertThat(commitValidationMessage).isEmpty();
  }

  @Test
  public void validateValidProjectLevelConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    ProjectLevelConfig cfg = new ProjectLevelConfig("code-owners.config", projectState);
    cfg.get()
        .setString(
            SECTION_CODE_OWNERS, null, getRequiredApprovalConfig().getConfigKey(), "Code-Review+2");
    Optional<CommitValidationMessage> commitValidationMessage =
        getRequiredApprovalConfig()
            .validateProjectLevelConfig(projectState, "code-owners.config", cfg);
    assertThat(commitValidationMessage).isEmpty();
  }

  @Test
  public void validateInvalidProjectLevelConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    ProjectLevelConfig cfg = new ProjectLevelConfig("code-owners.config", projectState);
    cfg.get()
        .setString(
            SECTION_CODE_OWNERS, null, getRequiredApprovalConfig().getConfigKey(), "INVALID");
    Optional<CommitValidationMessage> commitValidationMessage =
        getRequiredApprovalConfig()
            .validateProjectLevelConfig(projectState, "code-owners.config", cfg);
    assertThat(commitValidationMessage).isPresent();
    assertThat(commitValidationMessage.get().getType()).isEqualTo(ValidationMessage.Type.ERROR);
    assertThat(commitValidationMessage.get().getMessage())
        .isEqualTo(
            String.format(
                "Required approval 'INVALID' that is configured in code-owners.config (parameter"
                    + " codeOwners.%s) is invalid: Invalid format, expected"
                    + " '<label-name>+<label-value>'.",
                getRequiredApprovalConfig().getConfigKey()));
  }
}
