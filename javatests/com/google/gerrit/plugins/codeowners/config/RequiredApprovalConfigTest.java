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
import static com.google.gerrit.plugins.codeowners.config.RequiredApprovalConfig.KEY_REQUIRED_APPROVAL;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.OptionalSubject.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.project.ProjectLevelConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

/** Tests for {@link RequiredApprovalConfig}. */
public class RequiredApprovalConfigTest extends AbstractCodeOwnersTest {
  @Inject private PluginConfigFactory pluginConfigFactory;

  @Test
  public void cannotGetForProjectWithNullPluginName() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> RequiredApprovalConfig.getForProject(null, projectState, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("pluginName");
  }

  @Test
  public void cannotGetForProjectForNullProjectState() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> RequiredApprovalConfig.getForProject("code-owners", null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("projectState");
  }

  @Test
  public void cannotGetForProjectForNullConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> RequiredApprovalConfig.getForProject("code-owners", projectState, null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void getForProjectWhenRequiredApprovalIsNotSet() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    assertThat(RequiredApprovalConfig.getForProject("code-owners", projectState, new Config()))
        .isEmpty();
  }

  @Test
  public void getForProject() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_REQUIRED_APPROVAL, "Code-Review+2");
    Optional<RequiredApproval> requiredApproval =
        RequiredApprovalConfig.getForProject("code-owners", projectState, cfg);
    assertThat(requiredApproval).isPresent();
    assertThat(requiredApproval.get().labelType().getName()).isEqualTo("Code-Review");
    assertThat(requiredApproval.get().value()).isEqualTo(2);
  }

  @Test
  public void cannotGetForProjectIfConfigIsInvalid() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_REQUIRED_APPROVAL, "INVALID");
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () -> RequiredApprovalConfig.getForProject("code-owners", projectState, cfg));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Invalid configuration of the code-owners plugin. Required approval 'INVALID' that is"
                + " configured in code-owners.config (parameter codeOwners.requiredApproval) is"
                + " invalid: Invalid format, expected '<label-name>+<label-value>'.");
  }

  @Test
  public void cannotGetFromGlobalPluginConfigWithNullPluginConfigFactory() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                RequiredApprovalConfig.getFromGlobalPluginConfig(
                    null, "code-owners", projectState));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfigFactory");
  }

  @Test
  public void cannotGetFromGlobalPluginConfigWithNullPluginName() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                RequiredApprovalConfig.getFromGlobalPluginConfig(
                    pluginConfigFactory, null, projectState));
    assertThat(npe).hasMessageThat().isEqualTo("pluginName");
  }

  @Test
  public void cannotGetFromGlobalPluginConfigForNullProjectState() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                RequiredApprovalConfig.getFromGlobalPluginConfig(
                    pluginConfigFactory, "code-owners", null));
    assertThat(npe).hasMessageThat().isEqualTo("projectState");
  }

  @Test
  public void getFromGlobalPluginConfigWhenRequiredApprovalIsNotSet() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    assertThat(
            RequiredApprovalConfig.getFromGlobalPluginConfig(
                pluginConfigFactory, "code-owners", projectState))
        .isEmpty();
  }

  @Test
  public void getFromGlobalPluginConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    Config cfg = new Config();
    cfg.setString("plugin", "code-owners", KEY_REQUIRED_APPROVAL, "Code-Review+2");
    PluginConfigFactory pluginConfigFactory = mock(PluginConfigFactory.class);
    when(pluginConfigFactory.getFromGerritConfig("code-owners"))
        .thenReturn(new PluginConfig("code-owners", cfg));
    Optional<RequiredApproval> requiredApproval =
        RequiredApprovalConfig.getFromGlobalPluginConfig(
            pluginConfigFactory, "code-owners", projectState);
    assertThat(requiredApproval).isPresent();
    assertThat(requiredApproval.get().labelType().getName()).isEqualTo("Code-Review");
    assertThat(requiredApproval.get().value()).isEqualTo(2);
  }

  @Test
  public void createDefaultRequiresProjectState() throws Exception {
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> RequiredApprovalConfig.createDefault(null));
    assertThat(npe).hasMessageThat().isEqualTo("projectState");
  }

  @Test
  public void createDefault() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    RequiredApproval requiredApproval = RequiredApprovalConfig.createDefault(projectState);
    assertThat(requiredApproval.labelType().getName())
        .isEqualTo(RequiredApprovalConfig.DEFAULT_LABEL);
    assertThat(requiredApproval.value()).isEqualTo(RequiredApprovalConfig.DEFAULT_VALUE);
  }

  @Test
  public void cannotGetFromGlobalPluginConfigIfConfigIsInvalid() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    Config cfg = new Config();
    cfg.setString("plugin", "code-owners", KEY_REQUIRED_APPROVAL, "INVALID");
    PluginConfigFactory pluginConfigFactory = mock(PluginConfigFactory.class);
    when(pluginConfigFactory.getFromGerritConfig("code-owners"))
        .thenReturn(new PluginConfig("code-owners", cfg));
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () ->
                RequiredApprovalConfig.getFromGlobalPluginConfig(
                    pluginConfigFactory, "code-owners", projectState));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Invalid configuration of the code-owners plugin. Required approval 'INVALID' that is"
                + " configured in gerrit.config (parameter plugin.code-owners.requiredApproval) is"
                + " invalid: Invalid format, expected '<label-name>+<label-value>'.");
  }

  @Test
  public void cannotValidateProjectLevelConfigWithNullProjectState() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                RequiredApprovalConfig.validateProjectLevelConfig(
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
                RequiredApprovalConfig.validateProjectLevelConfig(
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
                RequiredApprovalConfig.validateProjectLevelConfig(
                    projectState, "code-owners.config", null));
    assertThat(npe).hasMessageThat().isEqualTo("projectLevelConfig");
  }

  @Test
  public void validateEmptyProjectLevelConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    Optional<CommitValidationMessage> commitValidationMessage =
        RequiredApprovalConfig.validateProjectLevelConfig(
            projectState,
            "code-owners.config",
            new ProjectLevelConfig("code-owners.config", projectState));
    assertThat(commitValidationMessage).isEmpty();
  }

  @Test
  public void validateValidProjectLevelConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    ProjectLevelConfig cfg = new ProjectLevelConfig("code-owners.config", projectState);
    cfg.get().setString(SECTION_CODE_OWNERS, null, KEY_REQUIRED_APPROVAL, "Code-Review+2");
    Optional<CommitValidationMessage> commitValidationMessage =
        RequiredApprovalConfig.validateProjectLevelConfig(projectState, "code-owners.config", cfg);
    assertThat(commitValidationMessage).isEmpty();
  }

  @Test
  public void validateInvalidProjectLevelConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    ProjectLevelConfig cfg = new ProjectLevelConfig("code-owners.config", projectState);
    cfg.get().setString(SECTION_CODE_OWNERS, null, KEY_REQUIRED_APPROVAL, "INVALID");
    Optional<CommitValidationMessage> commitValidationMessage =
        RequiredApprovalConfig.validateProjectLevelConfig(projectState, "code-owners.config", cfg);
    assertThat(commitValidationMessage).isPresent();
    assertThat(commitValidationMessage.get().getType()).isEqualTo(ValidationMessage.Type.ERROR);
    assertThat(commitValidationMessage.get().getMessage())
        .isEqualTo(
            "Required approval 'INVALID' that is configured in code-owners.config (parameter"
                + " codeOwners.requiredApproval) is invalid: Invalid format, expected"
                + " '<label-name>+<label-value>'.");
  }
}
