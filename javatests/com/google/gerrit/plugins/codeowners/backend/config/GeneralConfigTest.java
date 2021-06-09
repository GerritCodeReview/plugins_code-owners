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
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.DEFAULT_MAX_PATHS_IN_CHANGE_MESSAGES;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_ENABLE_CODE_OWNER_CONFIG_FILES_WITH_FILE_EXTENSIONS;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_ENABLE_IMPLICIT_APPROVALS;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_ENABLE_VALIDATION_ON_SUBMIT;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_EXEMPTED_USER;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_EXEMPT_PURE_REVERTS;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_FALLBACK_CODE_OWNERS;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_FILE_EXTENSION;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_GLOBAL_CODE_OWNER;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_INVALID_CODE_OWNER_CONFIG_INFO_URL;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_MAX_PATHS_IN_CHANGE_MESSAGES;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_MERGE_COMMIT_STRATEGY;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_OVERRIDE_INFO_URL;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_READ_ONLY;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.KEY_REJECT_NON_RESOLVABLE_IMPORTS;
import static com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig.SECTION_VALIDATION;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.EnableImplicitApprovals;
import com.google.gerrit.plugins.codeowners.backend.FallbackCodeOwners;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerConfigValidationPolicy;
import com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig}. */
public class GeneralConfigTest extends AbstractCodeOwnersTest {
  private GeneralConfig generalConfig;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    generalConfig = plugin.getSysInjector().getInstance(GeneralConfig.class);
  }

  @Test
  public void cannotGetFileExtensionForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getFileExtension(/* pluginConfig= */ null));
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
    cfg.setString(SECTION_CODE_OWNERS, /* subsection= */ null, KEY_FILE_EXTENSION, "bar");
    assertThat(generalConfig.getFileExtension(cfg)).value().isEqualTo("bar");
  }

  @Test
  public void cannotGetEnableCodeOwnerConfigFilesWithFileExtensionsForNullProject()
      throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.enableCodeOwnerConfigFilesWithFileExtensions(
                    /* project= */ null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void cannotGetEnableCodeOwnerConfigFilesWithFileExtensionsForNullPluginConfig()
      throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.enableCodeOwnerConfigFilesWithFileExtensions(
                    project, /* pluginConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noEnableCodeOwnerConfigFilesWithFileExtensionsConfiguration() throws Exception {
    assertThat(generalConfig.enableCodeOwnerConfigFilesWithFileExtensions(project, new Config()))
        .isFalse();
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.enableCodeOwnerConfigFilesWithFileExtensions",
      value = "true")
  public void
      enableCodeOwnerConfigFilesWithFileExtensionsConfigurationIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
          throws Exception {
    assertThat(generalConfig.enableCodeOwnerConfigFilesWithFileExtensions(project, new Config()))
        .isTrue();
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.enableCodeOwnerConfigFilesWithFileExtensions",
      value = "true")
  public void
      enableCodeOwnerConfigFilesWithFileExtensionsConfigurationInPluginConfigOverridesReadOnlyConfigurationInGerritConfig()
          throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS,
        /* subsection= */ null,
        KEY_ENABLE_CODE_OWNER_CONFIG_FILES_WITH_FILE_EXTENSIONS,
        "false");
    assertThat(generalConfig.enableCodeOwnerConfigFilesWithFileExtensions(project, cfg)).isFalse();
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.enableCodeOwnerConfigFilesWithFileExtensions",
      value = "true")
  public void
      invalidEnableCodeOwnerConfigFilesWithFileExtensionsConfigurationInPluginConfigIsIgnored()
          throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS,
        /* subsection= */ null,
        KEY_ENABLE_CODE_OWNER_CONFIG_FILES_WITH_FILE_EXTENSIONS,
        "INVALID");
    assertThat(generalConfig.enableCodeOwnerConfigFilesWithFileExtensions(project, cfg)).isTrue();
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.enableCodeOwnerConfigFilesWithFileExtensions",
      value = "INVALID")
  public void
      invalidEnableCodeOwnerConfigFilesWithFileExtensionsConfigurationInGerritConfigIsIgnored()
          throws Exception {
    assertThat(generalConfig.enableCodeOwnerConfigFilesWithFileExtensions(project, new Config()))
        .isFalse();
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
  public void cannotGetReadOnlyForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getReadOnly(/* project= */ null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void cannotGetReadOnlyForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getReadOnly(project, /* pluginConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noReadOnlyConfiguration() throws Exception {
    assertThat(generalConfig.getReadOnly(project, new Config())).isFalse();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.readOnly", value = "true")
  public void readOnlyConfigurationIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
      throws Exception {
    assertThat(generalConfig.getReadOnly(project, new Config())).isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.readOnly", value = "true")
  public void readOnlyConfigurationInPluginConfigOverridesReadOnlyConfigurationInGerritConfig()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, /* subsection= */ null, KEY_READ_ONLY, "false");
    assertThat(generalConfig.getReadOnly(project, cfg)).isFalse();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.readOnly", value = "true")
  public void invalidReadOnlyConfigurationInPluginConfigIsIgnored() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, /* subsection= */ null, KEY_READ_ONLY, "INVALID");
    assertThat(generalConfig.getReadOnly(project, cfg)).isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.readOnly", value = "INVALID")
  public void invalidReadOnlyConfigurationInGerritConfigIsIgnored() throws Exception {
    assertThat(generalConfig.getReadOnly(project, new Config())).isFalse();
  }

  @Test
  public void cannotGetExemptPureRevertsForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getExemptPureReverts(/* project= */ null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void cannotGetExemptPureRevertsForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getExemptPureReverts(project, /* pluginConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noExemptPureRevertsConfiguration() throws Exception {
    assertThat(generalConfig.getExemptPureReverts(project, new Config())).isFalse();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.exemptPureReverts", value = "true")
  public void
      exemptPureRevertsConfigurationIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
          throws Exception {
    assertThat(generalConfig.getExemptPureReverts(project, new Config())).isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.exemptPureReverts", value = "true")
  public void
      exemptPureRevertsConfigurationInPluginConfigOverridesExemptPureRevertsConfigurationInGerritConfig()
          throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_EXEMPT_PURE_REVERTS, "false");
    assertThat(generalConfig.getExemptPureReverts(project, cfg)).isFalse();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.exemptPureReverts", value = "true")
  public void invalidExemptPureRevertsInPluginConfigIsIgnored() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, /* subsection= */ null, KEY_EXEMPT_PURE_REVERTS, "INVALID");
    assertThat(generalConfig.getExemptPureReverts(project, cfg)).isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.exemptPureReverts", value = "INVALID")
  public void invalidExemptPureRevertsConfigurationInGerritConfigIsIgnored() throws Exception {
    assertThat(generalConfig.getExemptPureReverts(project, new Config())).isFalse();
  }

  @Test
  public void cannotGetRejectNonResolvableCodeOwnersForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.getRejectNonResolvableCodeOwners(/* project= */ null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void cannotGetRejectNonResolvableCodeOwnersForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.getRejectNonResolvableCodeOwners(project, /* pluginConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noRejectNonResolvableCodeOwnersConfiguration() throws Exception {
    assertThat(generalConfig.getRejectNonResolvableCodeOwners(project, new Config())).isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableCodeOwners", value = "false")
  public void
      rejectNonResolvableCodeOwnersConfigurationIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
          throws Exception {
    assertThat(generalConfig.getRejectNonResolvableCodeOwners(project, new Config())).isFalse();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableCodeOwners", value = "false")
  public void
      rejectNonResolvableCodeOwnersConfigurationInPluginConfigOverridesRejectNonResolvableCodeOwnersConfigurationInGerritConfig()
          throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS, /* subsection= */ null, KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS, "true");
    assertThat(generalConfig.getRejectNonResolvableCodeOwners(project, cfg)).isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableCodeOwners", value = "false")
  public void invalidRejectNonResolvableCodeOwnersInPluginConfigIsIgnored() throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS,
        /* subsection= */ null,
        KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS,
        "INVALID");
    assertThat(generalConfig.getRejectNonResolvableCodeOwners(project, cfg)).isFalse();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableCodeOwners", value = "INVALID")
  public void invalidRejectNonResolvableCodeOwnersConfigurationInGerritConfigIsIgnored()
      throws Exception {
    assertThat(generalConfig.getRejectNonResolvableCodeOwners(project, new Config())).isTrue();
  }

  @Test
  public void cannotGetRejectNonResolvableCodeOwnersForBranchForNullBranch() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.getRejectNonResolvableCodeOwnersForBranch(
                    /* branchNameKey= */ null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("branchNameKey");
  }

  @Test
  public void cannotGetRejectNonResolvableCodeOwnersForBranchForNullConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.getRejectNonResolvableCodeOwnersForBranch(
                    BranchNameKey.create(project, "master"), /* pluginConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noBranchSpecificRejectNonResolvableCodeOwnersConfiguration() throws Exception {
    assertThat(
            generalConfig.getRejectNonResolvableCodeOwnersForBranch(
                BranchNameKey.create(project, "master"), new Config()))
        .isEmpty();
  }

  @Test
  public void noMatchingBranchSpecificRejectNonResolvableCodeOwnersConfiguration_exact()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(
        SECTION_VALIDATION,
        "refs/heads/foo",
        KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS,
        /* value= */ false);
    assertThat(
            generalConfig.getRejectNonResolvableCodeOwnersForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void noMatchingBranchSpecificRejectNonResolvableCodeOwnersConfiguration_refPattern()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(
        SECTION_VALIDATION,
        "refs/heads/foo/*",
        KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS,
        /* value= */ false);
    assertThat(
            generalConfig.getRejectNonResolvableCodeOwnersForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void noMatchingBranchSpecificRejectNonResolvableCodeOwnersConfiguration_regEx()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(
        SECTION_VALIDATION,
        "^refs/heads/.*foo.*",
        KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS,
        /* value= */ false);
    assertThat(
            generalConfig.getRejectNonResolvableCodeOwnersForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void noMatchingBranchSpecificRejectNonResolvableCodeOwnersConfiguration_invalidRegEx()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(
        SECTION_VALIDATION,
        "^refs/heads/[",
        KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS,
        /* value= */ false);
    assertThat(
            generalConfig.getRejectNonResolvableCodeOwnersForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void matchingBranchSpecificRejectNonResolvableCodeOwnersConfiguration_exact()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(
        SECTION_VALIDATION,
        "refs/heads/master",
        KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS,
        /* value= */ false);
    assertThat(
            generalConfig.getRejectNonResolvableCodeOwnersForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .value()
        .isEqualTo(false);
  }

  @Test
  public void matchingBranchSpecificRejectNonResolvableCodeOwnersConfiguration_refPattern()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(
        SECTION_VALIDATION,
        "refs/heads/*",
        KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS,
        /* value= */ false);
    assertThat(
            generalConfig.getRejectNonResolvableCodeOwnersForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .value()
        .isEqualTo(false);
  }

  @Test
  public void matchingBranchSpecificRejectNonResolvableCodeOwnersConfiguration_regEx()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(
        SECTION_VALIDATION,
        "^refs/heads/.*bar.*",
        KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS,
        /* value= */ false);
    assertThat(
            generalConfig.getRejectNonResolvableCodeOwnersForBranch(
                BranchNameKey.create(project, "foobarbaz"), cfg))
        .value()
        .isEqualTo(false);
  }

  @Test
  public void branchSpecificRejectNonResolvableCodeOwnersConfigurationIsIgnoredIfValueIsInvalid()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_VALIDATION, "refs/heads/master", KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS, "INVALID");
    assertThat(
            generalConfig.getRejectNonResolvableCodeOwnersForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void multipleMatchingBranchSpecificRejectNonResolvableCodeOwnersConfiguration()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(
        SECTION_VALIDATION,
        "refs/heads/master",
        KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS,
        /* value= */ false);
    cfg.setBoolean(
        SECTION_VALIDATION,
        "refs/heads/*",
        KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS,
        /* value= */ false);
    cfg.setBoolean(
        SECTION_VALIDATION,
        "^refs/heads/.*",
        KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS,
        /* value= */ false);

    // it is non-deterministic which of the branch-specific configurations takes precedence, but
    // since they all configure the same value it's not important for this assertion
    assertThat(
            generalConfig.getRejectNonResolvableCodeOwnersForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .value()
        .isEqualTo(false);
  }

  @Test
  public void cannotGetRejectNonResolvableImportsForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getRejectNonResolvableImports(/* project= */ null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void cannotGetRejectNonResolvableImportsForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getRejectNonResolvableImports(project, /* pluginConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noRejectNonResolvableImportsConfiguration() throws Exception {
    assertThat(generalConfig.getRejectNonResolvableImports(project, new Config())).isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableImports", value = "false")
  public void
      rejectNonResolvableImportsConfigurationIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
          throws Exception {
    assertThat(generalConfig.getRejectNonResolvableImports(project, new Config())).isFalse();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableImports", value = "false")
  public void
      rejectNonResolvableImportsConfigurationInPluginConfigOverridesRejectNonResolvableImportsConfigurationInGerritConfig()
          throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS, /* scubsection= */ null, KEY_REJECT_NON_RESOLVABLE_IMPORTS, "true");
    assertThat(generalConfig.getRejectNonResolvableImports(project, cfg)).isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableImports", value = "false")
  public void invalidRejectNonResolvableImportsInPluginConfigIsIgnored() throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS, /* subsection= */ null, KEY_REJECT_NON_RESOLVABLE_IMPORTS, "INVALID");
    assertThat(generalConfig.getRejectNonResolvableImports(project, cfg)).isFalse();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.rejectNonResolvableImports", value = "INVALID")
  public void invalidRejectNonResolvableImportsConfigurationInGerritConfigIsIgnored()
      throws Exception {
    assertThat(generalConfig.getRejectNonResolvableImports(project, new Config())).isTrue();
  }

  @Test
  public void cannotGetRejectNonResolvableImportsForBranchForNullBranch() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.getRejectNonResolvableImportsForBranch(
                    /* branchNameKey= */ null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("branchNameKey");
  }

  @Test
  public void cannotGetRejectNonResolvableImportsForBranchForNullConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.getRejectNonResolvableImportsForBranch(
                    BranchNameKey.create(project, "master"), /* pluginConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noBranchSpecificRejectNonResolvableImportsConfiguration() throws Exception {
    assertThat(
            generalConfig.getRejectNonResolvableImportsForBranch(
                BranchNameKey.create(project, "master"), new Config()))
        .isEmpty();
  }

  @Test
  public void noMatchingBranchSpecificRejectNonResolvableImportsConfiguration_exact()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(
        SECTION_VALIDATION,
        "refs/heads/foo",
        KEY_REJECT_NON_RESOLVABLE_IMPORTS,
        /* value= */ false);
    assertThat(
            generalConfig.getRejectNonResolvableImportsForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void noMatchingBranchSpecificRejectNonResolvableImportsConfiguration_refPattern()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(
        SECTION_VALIDATION,
        "refs/heads/foo/*",
        KEY_REJECT_NON_RESOLVABLE_IMPORTS,
        /* value= */ false);
    assertThat(
            generalConfig.getRejectNonResolvableImportsForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void noMatchingBranchSpecificRejectNonResolvableImportsConfiguration_regEx()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(
        SECTION_VALIDATION,
        "^refs/heads/.*foo.*",
        KEY_REJECT_NON_RESOLVABLE_IMPORTS,
        /* value= */ false);
    assertThat(
            generalConfig.getRejectNonResolvableImportsForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void noMatchingBranchSpecificRejectNonResolvableImportsConfiguration_invalidRegEx()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(
        SECTION_VALIDATION, "^refs/heads/[", KEY_REJECT_NON_RESOLVABLE_IMPORTS, /* value= */ false);
    assertThat(
            generalConfig.getRejectNonResolvableImportsForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void matchingBranchSpecificRejectNonResolvableImportsConfiguration_exact()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(
        SECTION_VALIDATION,
        "refs/heads/master",
        KEY_REJECT_NON_RESOLVABLE_IMPORTS,
        /* value= */ false);
    assertThat(
            generalConfig.getRejectNonResolvableImportsForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .value()
        .isEqualTo(false);
  }

  @Test
  public void matchingBranchSpecificRejectNonResolvableImportsConfiguration_refPattern()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(
        SECTION_VALIDATION, "refs/heads/*", KEY_REJECT_NON_RESOLVABLE_IMPORTS, /* value= */ false);
    assertThat(
            generalConfig.getRejectNonResolvableImportsForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .value()
        .isEqualTo(false);
  }

  @Test
  public void matchingBranchSpecificRejectNonResolvableImportsConfiguration_regEx()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(
        SECTION_VALIDATION,
        "^refs/heads/.*bar.*",
        KEY_REJECT_NON_RESOLVABLE_IMPORTS,
        /* value= */ false);
    assertThat(
            generalConfig.getRejectNonResolvableImportsForBranch(
                BranchNameKey.create(project, "foobarbaz"), cfg))
        .value()
        .isEqualTo(false);
  }

  @Test
  public void branchSpecificRejectNonResolvableImportsConfigurationIsIgnoredIfValueIsInvalid()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_VALIDATION, "refs/heads/master", KEY_REJECT_NON_RESOLVABLE_IMPORTS, "INVALID");
    assertThat(
            generalConfig.getRejectNonResolvableImportsForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void multipleMatchingBranchSpecificRejectNonResolvableImportsConfiguration()
      throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(
        SECTION_VALIDATION,
        "refs/heads/master",
        KEY_REJECT_NON_RESOLVABLE_IMPORTS,
        /* value= */ false);
    cfg.setBoolean(
        SECTION_VALIDATION, "refs/heads/*", KEY_REJECT_NON_RESOLVABLE_IMPORTS, /* value= */ false);
    cfg.setBoolean(
        SECTION_VALIDATION,
        "^refs/heads/.*",
        KEY_REJECT_NON_RESOLVABLE_IMPORTS,
        /* value= */ false);

    // it is non-deterministic which of the branch-specific configurations takes precedence, but
    // since they all configure the same value it's not important for this assertion
    assertThat(
            generalConfig.getRejectNonResolvableImportsForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .value()
        .isEqualTo(false);
  }

  @Test
  public void cannotGetEnableValidationOnCommitReceivedForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceived(
                    /* project= */ null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void cannotGetEnableValidationOnCommitReceivedForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceived(
                    project, /* pluginConfig= */ null));
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
    cfg.setString(
        SECTION_CODE_OWNERS,
        /* subsection= */ null,
        KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED,
        "true");
    assertThat(generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceived(project, cfg))
        .isEqualTo(CodeOwnerConfigValidationPolicy.TRUE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnCommitReceived", value = "false")
  public void invalidEnableValidationOnCommitReceivedConfigurationInPluginConfigIsIgnored()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS,
        /* subsection= */ null,
        KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED,
        "INVALID");
    assertThat(generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceived(project, cfg))
        .isEqualTo(CodeOwnerConfigValidationPolicy.FALSE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnCommitReceived", value = "INVALID")
  public void invalidEnableValidationOnCommitReceivedConfigurationInGerritConfigIsIgnored()
      throws Exception {
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceived(
                project, new Config()))
        .isEqualTo(CodeOwnerConfigValidationPolicy.TRUE);
  }

  @Test
  public void cannotGetEnableValidationOnCommitReceivedForBranchForNullBranch() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceivedForBranch(
                    /* branchNameKey= */ null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("branchNameKey");
  }

  @Test
  public void cannotGetEnableValidationOnCommitReceivedForBranchForNullPluginConfig()
      throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceivedForBranch(
                    BranchNameKey.create(project, "master"), /* pluginConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noBranchSpecificEnableValidationOnCommitReceivedConfiguration() throws Exception {
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceivedForBranch(
                BranchNameKey.create(project, "master"), new Config()))
        .isEmpty();
  }

  @Test
  public void noMatchingBranchSpecificEnableValidationOnCommitReceivedConfiguration_exact()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_VALIDATION, "refs/heads/foo", KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED, "false");
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceivedForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void noMatchingBranchSpecificEnableValidationOnCommitReceivedConfiguration_refPattern()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_VALIDATION, "refs/heads/foo/*", KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED, "false");
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceivedForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void noMatchingBranchSpecificEnableValidationOnCommitReceivedConfiguration_regEx()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_VALIDATION,
        "^refs/heads/.*foo.*",
        KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED,
        "false");
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceivedForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void noMatchingBranchSpecificEnableValidationOnCommitReceivedConfiguration_invalidRegEx()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_VALIDATION, "^refs/heads/[", KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED, "false");
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceivedForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void matchingBranchSpecificEnableValidationOnCommitReceivedConfiguration_exact()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_VALIDATION, "refs/heads/master", KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED, "false");
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceivedForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .value()
        .isEqualTo(CodeOwnerConfigValidationPolicy.FALSE);
  }

  @Test
  public void matchingBranchSpecificEnableValidationOnCommitReceivedConfiguration_refPattern()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_VALIDATION, "refs/heads/*", KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED, "false");
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceivedForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .value()
        .isEqualTo(CodeOwnerConfigValidationPolicy.FALSE);
  }

  @Test
  public void matchingBranchSpecificEnableValidationOnCommitReceivedConfiguration_regEx()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_VALIDATION,
        "^refs/heads/.*bar.*",
        KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED,
        "false");
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceivedForBranch(
                BranchNameKey.create(project, "foobarbaz"), cfg))
        .value()
        .isEqualTo(CodeOwnerConfigValidationPolicy.FALSE);
  }

  @Test
  public void branchSpecificEnableValidationOnCommitReceivedConfigurationIsIgnoredIfValueIsInvalid()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_VALIDATION,
        "refs/heads/master",
        KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED,
        "INVALID");
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceivedForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void multipleMatchingBranchSpecificEnableValidationOnCommitReceivedConfiguration()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_VALIDATION, "refs/heads/master", KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED, "false");
    cfg.setString(
        SECTION_VALIDATION, "refs/heads/*", KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED, "false");
    cfg.setString(
        SECTION_VALIDATION, "^refs/heads/.*", KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED, "false");

    // it is non-deterministic which of the branch-specific configurations takes precedence, but
    // since they all configure the same value it's not important for this assertion
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceivedForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .value()
        .isEqualTo(CodeOwnerConfigValidationPolicy.FALSE);
  }

  @Test
  public void cannotGetEnableValidationOnSubmitForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.getCodeOwnerConfigValidationPolicyForSubmit(
                    /* project= */ null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void cannotGetEnableValidationOnSubmitForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.getCodeOwnerConfigValidationPolicyForSubmit(
                    project, /* pluginConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noEnableValidationOnSubmitConfiguration() throws Exception {
    assertThat(generalConfig.getCodeOwnerConfigValidationPolicyForSubmit(project, new Config()))
        .isEqualTo(CodeOwnerConfigValidationPolicy.FALSE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "true")
  public void
      enableValidationOnSubmitConfigurationIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
          throws Exception {
    assertThat(generalConfig.getCodeOwnerConfigValidationPolicyForSubmit(project, new Config()))
        .isEqualTo(CodeOwnerConfigValidationPolicy.TRUE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "true")
  public void
      enableValidationOnSubmitConfigurationInPluginConfigOverridesEnableValidationOnSubmitConfigurationInGerritConfig()
          throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS, /* subsection= */ null, KEY_ENABLE_VALIDATION_ON_SUBMIT, "false");
    assertThat(generalConfig.getCodeOwnerConfigValidationPolicyForSubmit(project, cfg))
        .isEqualTo(CodeOwnerConfigValidationPolicy.FALSE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "true")
  public void invalidEnableValidationOnSubmitConfigurationInPluginConfigIsIgnored()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS, /* subsection= */ null, KEY_ENABLE_VALIDATION_ON_SUBMIT, "INVALID");
    assertThat(generalConfig.getCodeOwnerConfigValidationPolicyForSubmit(project, cfg))
        .isEqualTo(CodeOwnerConfigValidationPolicy.TRUE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "INVALID")
  public void invalidEnableValidationOnSubmitConfigurationInGerritConfigIsIgnored()
      throws Exception {
    assertThat(generalConfig.getCodeOwnerConfigValidationPolicyForSubmit(project, new Config()))
        .isEqualTo(CodeOwnerConfigValidationPolicy.FALSE);
  }

  @Test
  public void cannotGetEnableValidationOnSubmitForBranchForNullBranch() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.getCodeOwnerConfigValidationPolicyForSubmitForBranch(
                    /* branchNameKey= */ null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("branchNameKey");
  }

  @Test
  public void cannotGetEnableValidationOnSubmitForBranchForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.getCodeOwnerConfigValidationPolicyForSubmitForBranch(
                    BranchNameKey.create(project, "master"), /* pluginConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noBranchSpecificEnableValidationOnSubmitConfiguration() throws Exception {
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForSubmitForBranch(
                BranchNameKey.create(project, "master"), new Config()))
        .isEmpty();
  }

  @Test
  public void noMatchingBranchSpecificEnableValidationOnSubmitConfiguration_exact()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_VALIDATION, "refs/heads/foo", KEY_ENABLE_VALIDATION_ON_SUBMIT, "false");
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForSubmitForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void noMatchingBranchSpecificEnableValidationOnSubmitConfiguration_refPattern()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_VALIDATION, "refs/heads/foo/*", KEY_ENABLE_VALIDATION_ON_SUBMIT, "false");
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForSubmitForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void noMatchingBranchSpecificEnableValidationOnSubmitConfiguration_regEx()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_VALIDATION, "^refs/heads/.*foo.*", KEY_ENABLE_VALIDATION_ON_SUBMIT, "false");
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceivedForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void noMatchingBranchSpecificEnableValidationOnSubmitConfiguration_invalidRegEx()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_VALIDATION, "^refs/heads/[", KEY_ENABLE_VALIDATION_ON_SUBMIT, "false");
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForSubmitForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void matchingBranchSpecificEnableValidationOnSubmitConfiguration_exact() throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_VALIDATION, "refs/heads/master", KEY_ENABLE_VALIDATION_ON_SUBMIT, "false");
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForSubmitForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .value()
        .isEqualTo(CodeOwnerConfigValidationPolicy.FALSE);
  }

  @Test
  public void matchingBranchSpecificEnableValidationOnSubmitConfiguration_refPattern()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_VALIDATION, "refs/heads/*", KEY_ENABLE_VALIDATION_ON_SUBMIT, "false");
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForSubmitForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .value()
        .isEqualTo(CodeOwnerConfigValidationPolicy.FALSE);
  }

  @Test
  public void matchingBranchSpecificEnableValidationOnSubmitConfiguration_regEx() throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_VALIDATION, "^refs/heads/.*bar.*", KEY_ENABLE_VALIDATION_ON_SUBMIT, "false");
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForSubmitForBranch(
                BranchNameKey.create(project, "foobarbaz"), cfg))
        .value()
        .isEqualTo(CodeOwnerConfigValidationPolicy.FALSE);
  }

  @Test
  public void branchSpecificEnableValidationOnSubmitConfigurationIsIgnoredIfValueIsInvalid()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_VALIDATION, "refs/heads/master", KEY_ENABLE_VALIDATION_ON_SUBMIT, "INVALID");
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForSubmitForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .isEmpty();
  }

  @Test
  public void multipleMatchingBranchSpecificEnableValidationOnSubmitConfiguration()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_VALIDATION, "refs/heads/master", KEY_ENABLE_VALIDATION_ON_SUBMIT, "false");
    cfg.setString(SECTION_VALIDATION, "refs/heads/*", KEY_ENABLE_VALIDATION_ON_SUBMIT, "false");
    cfg.setString(SECTION_VALIDATION, "^refs/heads/.*", KEY_ENABLE_VALIDATION_ON_SUBMIT, "false");

    // it is non-deterministic which of the branch-specific configurations takes precedence, but
    // since they all configure the same value it's not important for this assertion
    assertThat(
            generalConfig.getCodeOwnerConfigValidationPolicyForSubmitForBranch(
                BranchNameKey.create(project, "master"), cfg))
        .value()
        .isEqualTo(CodeOwnerConfigValidationPolicy.FALSE);
  }

  @Test
  public void cannotGetMergeCommitStrategyForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getMergeCommitStrategy(project, /* pluginConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void cannotGetMergeCommitStrategyForNullProjectName() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getMergeCommitStrategy(/* project= */ null, new Config()));
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
        /* subsection= */ null,
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
    cfg.setString(
        SECTION_CODE_OWNERS, /* subsection= */ null, KEY_MERGE_COMMIT_STRATEGY, "INVALID");
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
            () -> generalConfig.validateProjectLevelConfig(/*project= */ null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("fileName");
  }

  @Test
  public void cannotValidateProjectLevelConfigWithForNullProjectLevelConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                generalConfig.validateProjectLevelConfig(
                    "code-owners.config", /* pluginConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("projectLevelConfig");
  }

  @Test
  public void validateEmptyProjectLevelConfig() throws Exception {
    ImmutableList<CommitValidationMessage> commitValidationMessage =
        generalConfig.validateProjectLevelConfig("code-owners.config", new Config());
    assertThat(commitValidationMessage).isEmpty();
  }

  @Test
  public void validateValidProjectLevelConfig() throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS,
        /* subsection= */ null,
        KEY_MERGE_COMMIT_STRATEGY,
        MergeCommitStrategy.ALL_CHANGED_FILES.name());
    ImmutableList<CommitValidationMessage> commitValidationMessage =
        generalConfig.validateProjectLevelConfig("code-owners.config", cfg);
    assertThat(commitValidationMessage).isEmpty();
  }

  @Test
  public void validateInvalidProjectLevelConfig_invalidMergeCommitStrategy() throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS, /* subsection= */ null, KEY_MERGE_COMMIT_STRATEGY, "INVALID");
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
  public void cannotGetEnableImplicitApprovalsForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getEnableImplicitApprovals(/* project= */ null, new Config()));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void cannotGetEnableImplicitApprovalsForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getEnableImplicitApprovals(project, /* pluginConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noEnableImplicitApprovalsConfiguration() throws Exception {
    assertThat(generalConfig.getEnableImplicitApprovals(project, new Config()))
        .isEqualTo(EnableImplicitApprovals.FALSE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void
      enableImplicitApprovalsConfigurationIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
          throws Exception {
    assertThat(generalConfig.getEnableImplicitApprovals(project, new Config()))
        .isEqualTo(EnableImplicitApprovals.TRUE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void
      enableImplicitApprovalsConfigurationInPluginConfigOverridesEnableImplicitApprovalsConfigurationInGerritConfig()
          throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS, /* subsection= */ null, KEY_ENABLE_IMPLICIT_APPROVALS, "false");
    assertThat(generalConfig.getEnableImplicitApprovals(project, cfg))
        .isEqualTo(EnableImplicitApprovals.FALSE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void invalidEnableImplicitApprovalsConfigurationInPluginConfigIsIgnored()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS, /* subsection= */ null, KEY_ENABLE_IMPLICIT_APPROVALS, "INVALID");
    assertThat(generalConfig.getEnableImplicitApprovals(project, cfg))
        .isEqualTo(EnableImplicitApprovals.TRUE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "INVALID")
  public void invalidEnableImplicitApprovalsConfigurationInGerritConfigIsIgnored()
      throws Exception {
    assertThat(generalConfig.getEnableImplicitApprovals(project, cfg))
        .isEqualTo(EnableImplicitApprovals.FALSE);
  }

  @Test
  public void cannotGetGlobalCodeOwnersForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getGlobalCodeOwners(/* pluginConfig= */ null));
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
  public void globalCodeOwnersInPluginConfigExtendGlobalCodeOwnersInGerritConfig()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS, /* subsection= */ null, KEY_GLOBAL_CODE_OWNER, "bot3@example.com");
    assertThat(generalConfig.getGlobalCodeOwners(cfg))
        .containsExactly(
            CodeOwnerReference.create("bot1@example.com"),
            CodeOwnerReference.create("bot2@example.com"),
            CodeOwnerReference.create("bot3@example.com"));
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.globalCodeOwner",
      values = {"bot1@example.com", "bot2@example.com"})
  public void
      globalCodeOwnersInPluginConfigExtendGlobalCodeOwnersInGerritConfig_duplicatesFilteredOut()
          throws Exception {
    Config cfg = new Config();
    cfg.setStringList(
        SECTION_CODE_OWNERS,
        /* subsection= */ null,
        KEY_GLOBAL_CODE_OWNER,
        ImmutableList.of("bot1@example.com", "bot3@example.com"));
    assertThat(generalConfig.getGlobalCodeOwners(cfg))
        .containsExactly(
            CodeOwnerReference.create("bot1@example.com"),
            CodeOwnerReference.create("bot2@example.com"),
            CodeOwnerReference.create("bot3@example.com"));
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.globalCodeOwner",
      values = {"bot1@example.com", "bot2@example.com"})
  public void inheritedGlobalOwnersCannotBeRemovedOnProjectLevel() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, /* subsection= */ null, KEY_GLOBAL_CODE_OWNER, "");
    assertThat(generalConfig.getGlobalCodeOwners(cfg))
        .containsExactly(
            CodeOwnerReference.create("bot1@example.com"),
            CodeOwnerReference.create("bot2@example.com"));
  }

  @Test
  public void cannotGetExemptedUsersForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getExemptedUsers(/* pluginConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noExemptedUsers() throws Exception {
    assertThat(generalConfig.getExemptedUsers(new Config())).isEmpty();
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.exemptedUser",
      values = {"bot1@example.com", "bot2@example.com"})
  public void exemptedUsersAreRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
      throws Exception {
    assertThat(generalConfig.getExemptedUsers(new Config()))
        .containsExactly("bot1@example.com", "bot2@example.com");
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.exemptedUser",
      values = {"bot1@example.com", "bot2@example.com"})
  public void exemptedUsersInPluginConfigExtendExemptedUsersInGerritConfig() throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS, /* subsection= */ null, KEY_EXEMPTED_USER, "bot3@example.com");
    assertThat(generalConfig.getExemptedUsers(cfg))
        .containsExactly("bot1@example.com", "bot2@example.com", "bot3@example.com");
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.exemptedUser",
      values = {"bot1@example.com", "bot2@example.com"})
  public void exemptedUsersInPluginConfigExtendExemptedUsersInGerritConfig_duplicatesFilteredOut()
      throws Exception {
    Config cfg = new Config();
    cfg.setStringList(
        SECTION_CODE_OWNERS,
        /* subsection= */ null,
        KEY_EXEMPTED_USER,
        ImmutableList.of("bot1@example.com", "bot3@example.com"));
    assertThat(generalConfig.getExemptedUsers(cfg))
        .containsExactly("bot1@example.com", "bot2@example.com", "bot3@example.com");
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.exemptedUser",
      values = {"bot1@example.com", "bot2@example.com"})
  public void inheritedExemptedUsersCannotBeRemovedOnProjectLevel() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, /* subsection= */ null, KEY_EXEMPTED_USER, "");
    assertThat(generalConfig.getExemptedUsers(cfg))
        .containsExactly("bot1@example.com", "bot2@example.com");
  }

  @Test
  public void cannotGetOverrideInfoUrlForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getOverrideInfoUrl(/* pluginConfig= */ null));
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
    cfg.setString(
        SECTION_CODE_OWNERS,
        /* subsection= */ null,
        KEY_OVERRIDE_INFO_URL,
        "http://bar.example.com");
    assertThat(generalConfig.getOverrideInfoUrl(cfg)).value().isEqualTo("http://bar.example.com");
  }

  @Test
  public void cannotGetInvalidCodeOwnerConfigInfoUrlForNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> generalConfig.getInvalidCodeOwnerConfigInfoUrl(/* pluginConfig= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void noInvalidCodeOwnerConfigInfoUrlConfigured() throws Exception {
    assertThat(generalConfig.getInvalidCodeOwnerConfigInfoUrl(new Config())).isEmpty();
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.invalidCodeOwnerConfigInfoUrl",
      value = "http://foo.example.com")
  public void invalidCodeOwnerConfigInfoIsRetrievedFromGerritConfigIfNotSpecifiedOnProjectLevel()
      throws Exception {
    assertThat(generalConfig.getInvalidCodeOwnerConfigInfoUrl(new Config()))
        .value()
        .isEqualTo("http://foo.example.com");
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.invalidCodeOwnerConfigInfoUrl",
      value = "http://foo.example.com")
  public void invalidCodeOwnerConfigInfoUrlInPluginConfigOverridesOverrideInfoUrlInGerritConfig()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS,
        /* subsection= */ null,
        KEY_INVALID_CODE_OWNER_CONFIG_INFO_URL,
        "http://bar.example.com");
    assertThat(generalConfig.getInvalidCodeOwnerConfigInfoUrl(cfg))
        .value()
        .isEqualTo("http://bar.example.com");
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
    cfg.setString(SECTION_CODE_OWNERS, /* subsection= */ null, KEY_FALLBACK_CODE_OWNERS, "NONE");
    assertThat(generalConfig.getFallbackCodeOwners(project, cfg))
        .isEqualTo(FallbackCodeOwners.NONE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "ALL_USERS")
  public void globalFallbackOnwersUsedIfInvalidFallbackCodeOwnersConfigured() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, /* subsection= */ null, KEY_FALLBACK_CODE_OWNERS, "INVALID");
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
    cfg.setString(
        SECTION_CODE_OWNERS, /* subsection= */ null, KEY_MAX_PATHS_IN_CHANGE_MESSAGES, "10");
    assertThat(generalConfig.getMaxPathsInChangeMessages(project, cfg)).isEqualTo(10);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxPathsInChangeMessages", value = "50")
  public void globalMaxPathsInChangeMessagesUsedIfInvalidMaxPathsInChangeMessagesConfigured()
      throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS, /* subsection= */ null, KEY_MAX_PATHS_IN_CHANGE_MESSAGES, "INVALID");
    assertThat(generalConfig.getMaxPathsInChangeMessages(project, cfg)).isEqualTo(50);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxPathsInChangeMessages", value = "INVALID")
  public void defaultValueUsedIfInvalidMaxPathsInChangeMessagesConfigured() throws Exception {
    assertThat(generalConfig.getMaxPathsInChangeMessages(project, new Config()))
        .isEqualTo(DEFAULT_MAX_PATHS_IN_CHANGE_MESSAGES);
  }
}
