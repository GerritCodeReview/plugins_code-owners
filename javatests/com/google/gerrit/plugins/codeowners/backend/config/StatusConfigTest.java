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
import static com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS;
import static com.google.gerrit.plugins.codeowners.backend.config.StatusConfig.KEY_DISABLED;
import static com.google.gerrit.plugins.codeowners.backend.config.StatusConfig.KEY_DISABLED_BRANCH;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link com.google.gerrit.plugins.codeowners.backend.config.StatusConfig}. */
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
  @GerritConfig(name = "plugin.code-owners.disabled", value = "true")
  public void isDisabledForProjectIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
      throws Exception {
    assertThat(statusConfig.isDisabledForProject(new Config(), project)).isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabled", value = "true")
  public void disabledConfigurationInPluginConfigOverridesDisabledConfigurationInGerritConfig()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(SECTION_CODE_OWNERS, null, KEY_DISABLED, false);
    assertThat(statusConfig.isDisabledForProject(cfg, project)).isFalse();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabled", value = "INVALID")
  public void isDisabledForProject_invalidValueInGerritConfigIsIgnored() throws Exception {
    assertThat(statusConfig.isDisabledForProject(new Config(), project)).isFalse();
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
  public void isDisabledForBranchForConfigWithEmptyValue() throws Exception {
    Config cfg = new Config();
    cfg.setStringList(SECTION_CODE_OWNERS, null, KEY_DISABLED_BRANCH, ImmutableList.of(""));
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "master")))
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
  public void isDisabledForBranch_regularExpressionWithNegativeLookahead() throws Exception {
    Config cfg = new Config();
    cfg.setStringList(
        SECTION_CODE_OWNERS,
        null,
        KEY_DISABLED_BRANCH,
        // match all branches except refs/heads/master
        ImmutableList.of("^refs/(?!heads/master$).*"));
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "master")))
        .isFalse();
    assertThat(
            statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "master-foo-bar")))
        .isTrue();
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "foo")))
        .isTrue();
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "other")))
        .isTrue();
    assertThat(
            statusConfig.isDisabledForBranch(
                cfg, BranchNameKey.create(project, RefNames.REFS_CONFIG)))
        .isTrue();
    assertThat(
            statusConfig.isDisabledForBranch(
                cfg, BranchNameKey.create(project, "refs/meta/master")))
        .isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabledBranch", value = "refs/heads/master")
  public void isDisabledForBranchIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
      throws Exception {
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "master")))
        .isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabledBranch", value = "refs/heads/master")
  public void
      disabledBranchConfigurationInPluginConfigExtendsDisabledBranchConfigurationInGerritConfig()
          throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_DISABLED_BRANCH, "refs/heads/test");
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "master")))
        .isTrue();
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "test")))
        .isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabledBranch", value = "refs/heads/master")
  public void
      disabledBranchConfigurationInPluginConfigCannotRemoveDisabledBranchConfigurationInGerritConfig()
          throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_DISABLED_BRANCH, "");
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "master")))
        .isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabledBranch", value = "^refs/heads/[")
  public void isDisabledForBranch_invalidValueInGerritConfigIsIgnored() throws Exception {
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "master")))
        .isFalse();
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.disabledBranch",
      values = {"^refs/heads/[", "refs/heads/master"})
  public void isDisabledForBranch_validValueInGerritConfigIsUsedEvenIfAnotherValueIsInvalid()
      throws Exception {
    assertThat(statusConfig.isDisabledForBranch(cfg, BranchNameKey.create(project, "master")))
        .isTrue();
  }

  @Test
  public void cannotValidateProjectLevelConfigWithNullFileName() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> statusConfig.validateProjectLevelConfig(null, new Config()));
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
    ImmutableList<CommitValidationMessage> commitValidationMessage =
        statusConfig.validateProjectLevelConfig("code-owners.config", new Config());
    assertThat(commitValidationMessage).isEmpty();
  }

  @Test
  public void validateValidProjectLevelConfig() throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(SECTION_CODE_OWNERS, null, KEY_DISABLED, true);
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_DISABLED_BRANCH, "refs/heads/master");
    ImmutableList<CommitValidationMessage> commitValidationMessage =
        statusConfig.validateProjectLevelConfig("code-owners.config", cfg);
    assertThat(commitValidationMessage).isEmpty();
  }

  @Test
  public void validateInvalidProjectLevelConfig_invalidDisabledValue() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_DISABLED, "INVALID");
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
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_DISABLED_BRANCH, "^refs/heads/[");
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
