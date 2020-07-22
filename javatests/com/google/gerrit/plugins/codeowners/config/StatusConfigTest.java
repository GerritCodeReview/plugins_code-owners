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
import static com.google.gerrit.plugins.codeowners.config.StatusConfig.KEY_DISABLED;
import static com.google.gerrit.plugins.codeowners.config.StatusConfig.KEY_DISABLED_BRANCH;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.project.ProjectLevelConfig;
import com.google.gerrit.server.project.ProjectState;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link StatusConfig}. */
public class StatusConfigTest extends AbstractCodeOwnersTest {
  private StatusConfig statusConfig;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    statusConfig = plugin.getSysInjector().getInstance(StatusConfig.class);
  }

  @Test
  public void cannotCheckIfDisabledForProjectWithNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> statusConfig.isDisabledForProject(null, project));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void cannotCheckIfDisabledForProjectWithNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> statusConfig.isDisabledForProject(new Config(), null));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void isDisabledForProjectForEmptyConfig() throws Exception {
    assertThat(statusConfig.isDisabledForProject(new Config(), project)).isFalse();
  }

  @Test
  public void isDisabledForProject() throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(SECTION_CODE_OWNERS, null, KEY_DISABLED, true);
    assertThat(statusConfig.isDisabledForProject(cfg, project)).isTrue();
  }

  @Test
  public void isDisabledForProject_invalidValueIsIgnored() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_DISABLED, "invalid");
    assertThat(statusConfig.isDisabledForProject(cfg, project)).isFalse();
  }

  @Test
  public void cannotCheckIfDisabledForBranchWithNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> statusConfig.isDisabledForBranch(null, BranchNameKey.create(project, "master")));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void cannotCheckIfDisabledForBranchWithNullBranch() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> statusConfig.isDisabledForBranch(new Config(), null));
    assertThat(npe).hasMessageThat().isEqualTo("branch");
  }

  @Test
  public void isDisabledForBranchForEmptyConfig() throws Exception {
    assertThat(
            statusConfig.isDisabledForBranch(new Config(), BranchNameKey.create(project, "master")))
        .isFalse();
  }

  @Test
  public void isDisabledForBranch_exactRef() throws Exception {
    Config cfg = new Config();
    cfg.setStringList(
        SECTION_CODE_OWNERS,
        null,
        KEY_DISABLED_BRANCH,
        ImmutableList.of("refs/heads/master", "refs/heads/foo"));
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "master")))
        .isTrue();
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "foo")))
        .isTrue();
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "other")))
        .isFalse();
  }

  @Test
  public void isDisabledForBranch_refPattern() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_DISABLED_BRANCH, "refs/heads/*");
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "master")))
        .isTrue();
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "other")))
        .isTrue();
    assertThat(
            statusConfig.isDisabledForBranch(
                cfg, BranchNameKey.create(project, RefNames.REFS_META)))
        .isFalse();
  }

  @Test
  public void isDisabledForBranch_regularExpression() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_DISABLED_BRANCH, "^refs/heads/.*");
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "master")))
        .isTrue();
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "other")))
        .isTrue();
    assertThat(
            statusConfig.isDisabledForBranch(
                cfg, BranchNameKey.create(project, RefNames.REFS_META)))
        .isFalse();
  }

  @Test
  public void isDisabledForBranch_invalidRegularExpressionIsIgnored() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_DISABLED_BRANCH, "^refs/heads/[");
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "master")))
        .isFalse();
  }

  @Test
  public void isDisabledForBranch_invalidRegularExpressionDoesNotPreventMatchByOtherPattern()
      throws Exception {
    Config cfg = new Config();
    cfg.setStringList(
        SECTION_CODE_OWNERS,
        null,
        KEY_DISABLED_BRANCH,
        ImmutableList.of("^refs/heads/[", "refs/heads/master"));
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "master")))
        .isTrue();
  }

  @Test
  public void cannotValidateProjectLevelConfigWithNullFileName() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                statusConfig.validateProjectLevelConfig(
                    null, new ProjectLevelConfig("code-owners.config", projectState)));
    assertThat(npe).hasMessageThat().isEqualTo("fileName");
  }

  @Test
  public void cannotValidateProjectLevelConfigWithForNullProjectLevelConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> statusConfig.validateProjectLevelConfig("code-owners.config", null));
    assertThat(npe).hasMessageThat().isEqualTo("projectLevelConfig");
  }

  @Test
  public void validateEmptyProjectLevelConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    ImmutableList<CommitValidationMessage> commitValidationMessage =
        statusConfig.validateProjectLevelConfig(
            "code-owners.config", new ProjectLevelConfig("code-owners.config", projectState));
    assertThat(commitValidationMessage).isEmpty();
  }

  @Test
  public void validateValidProjectLevelConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    ProjectLevelConfig cfg = new ProjectLevelConfig("code-owners.config", projectState);
    cfg.get().setBoolean(SECTION_CODE_OWNERS, null, KEY_DISABLED, true);
    cfg.get().setString(SECTION_CODE_OWNERS, null, KEY_DISABLED_BRANCH, "refs/heads/master");
    ImmutableList<CommitValidationMessage> commitValidationMessage =
        statusConfig.validateProjectLevelConfig("code-owners.config", cfg);
    assertThat(commitValidationMessage).isEmpty();
  }

  @Test
  public void validateInvalidProjectLevelConfig_invalidDisabledValue() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    ProjectLevelConfig cfg = new ProjectLevelConfig("code-owners.config", projectState);
    cfg.get().setString(SECTION_CODE_OWNERS, null, KEY_DISABLED, "INVALID");
    ImmutableList<CommitValidationMessage> commitValidationMessages =
        statusConfig.validateProjectLevelConfig("code-owners.config", cfg);
    assertThat(commitValidationMessages).hasSize(1);
    CommitValidationMessage commitValidationMessage =
        Iterables.getOnlyElement(commitValidationMessages);
    assertThat(commitValidationMessage.getType()).isEqualTo(ValidationMessage.Type.ERROR);
    assertThat(commitValidationMessage.getMessage())
        .isEqualTo(
            "Disabled value 'INVALID' that is configured in code-owners.config (parameter"
                + " codeOwners.disabled) is invalid.");
  }

  @Test
  public void validateInvalidProjectLevelConfig_invalidDisabledBranch() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    ProjectLevelConfig cfg = new ProjectLevelConfig("code-owners.config", projectState);
    cfg.get().setString(SECTION_CODE_OWNERS, null, KEY_DISABLED_BRANCH, "^refs/heads/[");
    ImmutableList<CommitValidationMessage> commitValidationMessages =
        statusConfig.validateProjectLevelConfig("code-owners.config", cfg);
    assertThat(commitValidationMessages).hasSize(1);
    CommitValidationMessage commitValidationMessage =
        Iterables.getOnlyElement(commitValidationMessages);
    assertThat(commitValidationMessage.getType()).isEqualTo(ValidationMessage.Type.ERROR);
    assertThat(commitValidationMessage.getMessage())
        .contains(
            "Disabled branch '^refs/heads/[' that is configured in code-owners.config (parameter"
                + " codeOwners.disabledBranch) is invalid: Unclosed character class");
  }
}
