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
import static com.google.gerrit.plugins.codeowners.config.GeneralConfig.DEFAULT_MAX_PATHS_IN_CHANGE_MESSAGES;
import static com.google.gerrit.plugins.codeowners.config.GeneralConfig.KEY_ENABLE_IMPLICIT_APPROVALS;
import static com.google.gerrit.plugins.codeowners.config.GeneralConfig.KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED;
import static com.google.gerrit.plugins.codeowners.config.GeneralConfig.KEY_ENABLE_VALIDATION_ON_SUBMIT;
import static com.google.gerrit.plugins.codeowners.config.GeneralConfig.KEY_FALLBACK_CODE_OWNERS;
import static com.google.gerrit.plugins.codeowners.config.GeneralConfig.KEY_FILE_EXTENSION;
import static com.google.gerrit.plugins.codeowners.config.GeneralConfig.KEY_GLOBAL_CODE_OWNER;
import static com.google.gerrit.plugins.codeowners.config.GeneralConfig.KEY_MAX_PATHS_IN_CHANGE_MESSAGES;
import static com.google.gerrit.plugins.codeowners.config.GeneralConfig.KEY_MERGE_COMMIT_STRATEGY;
import static com.google.gerrit.plugins.codeowners.config.GeneralConfig.KEY_OVERRIDE_INFO_URL;
import static com.google.gerrit.plugins.codeowners.config.GeneralConfig.KEY_READ_ONLY;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerConfigValidationPolicy;
import com.google.gerrit.plugins.codeowners.api.MergeCommitStrategy;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.FallbackCodeOwners;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.project.ProjectLevelConfig;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link GeneralConfig}. */
public class GeneralConfigTest extends AbstractCodeOwnersTest {
  private GeneralConfig generalConfig;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    generalConfig = plugin.getSysInjector().getInstance(GeneralConfig.class);
  }

  @Test
  public void cannotGetFileExtensionForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> generalConfig.getFileExtension(null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noFileExtensionConfigured() throws Exception {
    assertThat(generalConfig.getFileExtension(new Config())).isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fileExtension", value = "foo")
  public void fileExtensionIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
      throws Exception {
    assertThat(generalConfig.getFileExtension(new Config())).value().isEqualTo("foo");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fileExtension", value = "foo")
  public void fileExtensionInPluginConfigOverridesFileExtensionInGerritConfig() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_FILE_EXTENSION, "bar");
    assertThat(generalConfig.getFileExtension(cfg)).value().isEqualTo("bar");
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.allowedEmailDomain",
      values = {"example.com", "example.net"})
  public void getConfiguredEmailDomains() throws Exception {
    assertThat(generalConfig.getAllowedEmailDomains())
        .containsExactly("example.com", "example.net");
  }

  @Test
  public void noEmailDomainsConfigured() throws Exception {
    assertThat(generalConfig.getAllowedEmailDomains()).isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.allowedEmailDomain", value = "")
  public void emptyEmailDomainsConfigured() throws Exception {
    assertThat(generalConfig.getAllowedEmailDomains()).isEmpty();
  }

  @Test
  public void cannotGetReadOnlyForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> generalConfig.getReadOnly(null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noReadOnlyConfiguration() throws Exception {
    assertThat(generalConfig.getReadOnly(new Config())).isFalse();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.readOnly", value = "true")
  public void readOnlyConfigurationIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
      throws Exception {
    assertThat(generalConfig.getReadOnly(new Config())).isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.readOnly", value = "true")
  public void readOnlyConfigurationInPluginConfigOverridesReadOnlyConfigurationInGerritConfig()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_READ_ONLY, "false");
    assertThat(generalConfig.getReadOnly(cfg)).isFalse();
  }

  @Test
  public void cannotGetEnableValidationOnCommitReceivedForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceived(
                    null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void cannotGetEnableValidationOnCommitReceivedForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceived(project, null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noEnableValidationOnCommitReceivedConfiguration() throws Exception {
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceived(
                project, new Config()))
        .isEqualTo(CodeOwnerConfigValidationPolicy.TRUE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnCommitReceived", value = "false")
  public void
      enableValidationOnCommitReceivedConfigurationIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
          throws Exception {
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceived(
                project, new Config()))
        .isEqualTo(CodeOwnerConfigValidationPolicy.FALSE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnCommitReceived", value = "false")
  public void
      enableValidationOnCommitReceivedConfigurationInPluginConfigOverridesEnableValidationOnCommitReceivedConfigurationInGerritConfig()
          throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED, "true");
    assertThat(generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceived(project, cfg))
        .isEqualTo(CodeOwnerConfigValidationPolicy.TRUE);
  }

  @Test
  public void cannotGetEnableValidationOnSubmitForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getCodeOwnerConfigValidationPolicyForSubmit(null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void cannotGetEnableValidationOnSubmitForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getCodeOwnerConfigValidationPolicyForSubmit(project, null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noEnableValidationOnSubmitConfiguration() throws Exception {
    assertThat(generalConfig.getCodeOwnerConfigValidationPolicyForSubmit(project, new Config()))
        .isEqualTo(CodeOwnerConfigValidationPolicy.TRUE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "false")
  public void
      enableValidationOnSubmitConfigurationIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
          throws Exception {
    assertThat(generalConfig.getCodeOwnerConfigValidationPolicyForSubmit(project, new Config()))
        .isEqualTo(CodeOwnerConfigValidationPolicy.FALSE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "false")
  public void
      enableValidationOnSubmitConfigurationInPluginConfigOverridesEnableValidationOnSubmitConfigurationInGerritConfig()
          throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_ENABLE_VALIDATION_ON_SUBMIT, "true");
    assertThat(generalConfig.getCodeOwnerConfigValidationPolicyForSubmit(project, cfg))
        .isEqualTo(CodeOwnerConfigValidationPolicy.TRUE);
  }

  @Test
  public void cannotGetMergeCommitStrategyForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> generalConfig.getMergeCommitStrategy(project, null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void cannotGetMergeCommitStrategyForNullProjectName() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getMergeCommitStrategy(null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void noMergeCommitStrategyConfigured() throws Exception {
    assertThat(generalConfig.getMergeCommitStrategy(project, new Config()))
        .isEqualTo(MergeCommitStrategy.ALL_CHANGED_FILES);
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.mergeCommitStrategy",
      value = "FILES_WITH_CONFLICT_RESOLUTION")
  public void mergeCommitStrategyIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
      throws Exception {
    assertThat(generalConfig.getMergeCommitStrategy(project, new Config()))
        .isEqualTo(MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION);
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.mergeCommitStrategy",
      value = "FILES_WITH_CONFLICT_RESOLUTION")
  public void mergeCommitStrategyInPluginConfigOverridesMergeCommitStrategyInGerritConfig()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS,
        null,
        KEY_MERGE_COMMIT_STRATEGY,
        MergeCommitStrategy.ALL_CHANGED_FILES.name());
    assertThat(generalConfig.getMergeCommitStrategy(project, cfg))
        .isEqualTo(MergeCommitStrategy.ALL_CHANGED_FILES);
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.mergeCommitStrategy",
      value = "FILES_WITH_CONFLICT_RESOLUTION")
  public void invalidMergeCommitStrategyInPluginConfigIsIgnored() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_MERGE_COMMIT_STRATEGY, "INVALID");
    assertThat(generalConfig.getMergeCommitStrategy(project, cfg))
        .isEqualTo(MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.mergeCommitStrategy", value = "INVALID")
  public void invalidMergeCommitStrategyInGerritConfigIsIgnored() throws Exception {
    assertThat(generalConfig.getMergeCommitStrategy(project, new Config()))
        .isEqualTo(MergeCommitStrategy.ALL_CHANGED_FILES);
  }

  @Test
  public void cannotValidateProjectLevelConfigWithNullFileName() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.validateProjectLevelConfig(
                    null, new ProjectLevelConfig.Bare("code-owners.config")));
    assertThat(npe).hasMessageThat().isEqualTo("fileName");
  }

  @Test
  public void cannotValidateProjectLevelConfigWithForNullProjectLevelConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.validateProjectLevelConfig("code-owners.config", null));
    assertThat(npe).hasMessageThat().isEqualTo("projectLevelConfig");
  }

  @Test
  public void validateEmptyProjectLevelConfig() throws Exception {
    ImmutableList<CommitValidationMessage> commitValidationMessage =
        generalConfig.validateProjectLevelConfig(
            "code-owners.config", new ProjectLevelConfig.Bare("code-owners.config"));
    assertThat(commitValidationMessage).isEmpty();
  }

  @Test
  public void validateValidProjectLevelConfig() throws Exception {
    ProjectLevelConfig.Bare cfg = new ProjectLevelConfig.Bare("code-owners.config");
    cfg.getConfig()
        .setString(
            SECTION_CODE_OWNERS,
            null,
            KEY_MERGE_COMMIT_STRATEGY,
            MergeCommitStrategy.ALL_CHANGED_FILES.name());
    ImmutableList<CommitValidationMessage> commitValidationMessage =
        generalConfig.validateProjectLevelConfig("code-owners.config", cfg);
    assertThat(commitValidationMessage).isEmpty();
  }

  @Test
  public void validateInvalidProjectLevelConfig_invalidMergeCommitStrategy() throws Exception {
    ProjectLevelConfig.Bare cfg = new ProjectLevelConfig.Bare("code-owners.config");
    cfg.getConfig().setString(SECTION_CODE_OWNERS, null, KEY_MERGE_COMMIT_STRATEGY, "INVALID");
    ImmutableList<CommitValidationMessage> commitValidationMessages =
        generalConfig.validateProjectLevelConfig("code-owners.config", cfg);
    assertThat(commitValidationMessages).hasSize(1);
    CommitValidationMessage commitValidationMessage =
        Iterables.getOnlyElement(commitValidationMessages);
    assertThat(commitValidationMessage.getType()).isEqualTo(ValidationMessage.Type.ERROR);
    assertThat(commitValidationMessage.getMessage())
        .isEqualTo(
            "Merge commit strategy 'INVALID' that is configured in code-owners.config"
                + " (parameter codeOwners.mergeCommitStrategy) is invalid.");
  }

  @Test
  public void cannotGetEnableImplicitApprovalsForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> generalConfig.getEnableImplicitApprovals(null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noEnableImplicitApprovalsConfiguration() throws Exception {
    assertThat(generalConfig.getEnableImplicitApprovals(new Config())).isFalse();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void
      enableImplicitApprovalsConfigurationIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
          throws Exception {
    assertThat(generalConfig.getEnableImplicitApprovals(new Config())).isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void
      enableImplicitApprovalsConfigurationInPluginConfigOverridesEnableImplicitApprovalsConfigurationInGerritConfig()
          throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_ENABLE_IMPLICIT_APPROVALS, "false");
    assertThat(generalConfig.getEnableImplicitApprovals(cfg)).isFalse();
  }

  @Test
  public void cannotGetGlobalCodeOwnersForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> generalConfig.getGlobalCodeOwners(null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noGlobalCodeOwners() throws Exception {
    assertThat(generalConfig.getGlobalCodeOwners(new Config())).isEmpty();
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.globalCodeOwner",
      values = {"bot1@example.com", "bot2@example.com"})
  public void globalCodeOwnersAreRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
      throws Exception {
    assertThat(generalConfig.getGlobalCodeOwners(new Config()))
        .containsExactly(
            CodeOwnerReference.create("bot1@example.com"),
            CodeOwnerReference.create("bot2@example.com"));
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.globalCodeOwner",
      values = {"bot1@example.com", "bot2@example.com"})
  public void globalCodeOnwersInPluginConfigOverrideGlobalCodeOwnersInGerritConfig()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_GLOBAL_CODE_OWNER, "bot3@example.com");
    assertThat(generalConfig.getGlobalCodeOwners(cfg))
        .containsExactly(CodeOwnerReference.create("bot3@example.com"));
  }

  @Test
  public void cannotGetOverrideInfoUrlForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> generalConfig.getOverrideInfoUrl(null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noOverrideInfoUrlConfigured() throws Exception {
    assertThat(generalConfig.getOverrideInfoUrl(new Config())).isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideInfoUrl", value = "http://foo.example.com")
  public void overrideInfoIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
      throws Exception {
    assertThat(generalConfig.getOverrideInfoUrl(new Config()))
        .value()
        .isEqualTo("http://foo.example.com");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideInfoUrl", value = "http://foo.example.com")
  public void overrideInfoUrlInPluginConfigOverridesOverrideInfoUrlInGerritConfig()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_OVERRIDE_INFO_URL, "http://bar.example.com");
    assertThat(generalConfig.getOverrideInfoUrl(cfg)).value().isEqualTo("http://bar.example.com");
  }

  @Test
  public void cannotGetFallbackCodeOwnersForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getFallbackCodeOwners(/* project= */ null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void cannotGetFallbackCodeOwnersForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getFallbackCodeOwners(project, /* pluginConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noFallbackCodeOwnersConfigured() throws Exception {
    assertThat(generalConfig.getFallbackCodeOwners(project, new Config()))
        .isEqualTo(FallbackCodeOwners.NONE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "ALL_USERS")
  public void fallbackCodeOwnersIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
      throws Exception {
    assertThat(generalConfig.getFallbackCodeOwners(project, new Config()))
        .isEqualTo(FallbackCodeOwners.ALL_USERS);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "ALL_USERS")
  public void fallbackCodeOwnersInPluginConfigOverridesFallbackCodeOwnersInGerritConfig()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_FALLBACK_CODE_OWNERS, "NONE");
    assertThat(generalConfig.getFallbackCodeOwners(project, cfg))
        .isEqualTo(FallbackCodeOwners.NONE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "ALL_USERS")
  public void globalFallbackOnwersUsedIfInvalidFallbackCodeOwnersConfigured() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_FALLBACK_CODE_OWNERS, "INVALID");
    assertThat(generalConfig.getFallbackCodeOwners(project, cfg))
        .isEqualTo(FallbackCodeOwners.ALL_USERS);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "INVALID")
  public void defaultValueUsedIfInvalidGlobalFallbackCodeOwnersConfigured() throws Exception {
    assertThat(generalConfig.getFallbackCodeOwners(project, new Config()))
        .isEqualTo(FallbackCodeOwners.NONE);
  }

  @Test
  public void cannotGetMaxPathsInChangeMessagesForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getMaxPathsInChangeMessages(/* project= */ null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void cannotGetMaxPathsInChangeMessagesForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getMaxPathsInChangeMessages(project, /* pluginConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noMaxPathsInChangeMessagesConfigured() throws Exception {
    assertThat(generalConfig.getMaxPathsInChangeMessages(project, new Config()))
        .isEqualTo(DEFAULT_MAX_PATHS_IN_CHANGE_MESSAGES);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxPathsInChangeMessages", value = "50")
  public void maxPathsInChangeMessagesIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
      throws Exception {
    assertThat(generalConfig.getMaxPathsInChangeMessages(project, new Config())).isEqualTo(50);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxPathsInChangeMessages", value = "50")
  public void
      maxPathsInChangeMessagesInPluginConfigOverridesMaxPathsInChangeMessagesInGerritConfig()
          throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_MAX_PATHS_IN_CHANGE_MESSAGES, "10");
    assertThat(generalConfig.getMaxPathsInChangeMessages(project, cfg)).isEqualTo(10);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxPathsInChangeMessages", value = "50")
  public void globalMaxPathsInChangeMessagesUsedIfInvalidMaxPathsInChangeMessagesConfigured()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_MAX_PATHS_IN_CHANGE_MESSAGES, "INVALID");
    assertThat(generalConfig.getMaxPathsInChangeMessages(project, cfg)).isEqualTo(50);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxPathsInChangeMessages", value = "INVALID")
  public void defaultValueUsedIfInvalidMaxPathsInChangeMessagesConfigured() throws Exception {
    assertThat(generalConfig.getMaxPathsInChangeMessages(project, new Config()))
        .isEqualTo(DEFAULT_MAX_PATHS_IN_CHANGE_MESSAGES);
  }
}
