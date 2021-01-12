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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.RequiredApprovalSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigUpdate;
import com.google.gerrit.plugins.codeowners.backend.FallbackCodeOwners;
import com.google.gerrit.plugins.codeowners.backend.config.BackendConfig;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.GeneralConfig;
import com.google.gerrit.plugins.codeowners.backend.config.InvalidPluginConfigurationException;
import com.google.gerrit.plugins.codeowners.backend.config.OverrideApprovalConfig;
import com.google.gerrit.plugins.codeowners.backend.config.RequiredApproval;
import com.google.gerrit.plugins.codeowners.backend.config.RequiredApprovalConfig;
import com.google.gerrit.plugins.codeowners.backend.config.StatusConfig;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.util.Providers;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnersPluginConfiguration}. */
public class CodeOwnersPluginConfigurationTest extends AbstractCodeOwnersTest {
  @Inject private ProjectOperations projectOperations;

  private CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private DynamicMap<CodeOwnerBackend> codeOwnerBackends;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnersPluginConfiguration =
        plugin.getSysInjector().getInstance(CodeOwnersPluginConfiguration.class);
    codeOwnerBackends =
        plugin.getSysInjector().getInstance(new Key<DynamicMap<CodeOwnerBackend>>() {});
  }

  @Test
  public void cannotCheckForNullProjectIfCodeOwnersFunctionalityIsDisabled() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> codeOwnersPluginConfiguration.isDisabled(/* project= */ (Project.NameKey) null));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void cannotCheckForNullBranchIfCodeOwnersFunctionalityIsDisabled() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwnersPluginConfiguration.isDisabled(
                    /* branchNameKey= */ (BranchNameKey) null));
    assertThat(npe).hasMessageThat().isEqualTo("branchNameKey");
  }

  @Test
  public void cannotCheckIfCodeOwnersFunctionalityIsDisabledForNonExistingProject()
      throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                codeOwnersPluginConfiguration.isDisabled(Project.nameKey("non-existing-project")));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "cannot get code-owners plugin config for non-existing project non-existing-project");
  }

  @Test
  public void cannotCheckIfCodeOwnersFunctionalityIsDisabledForBranchOfNonExistingProject()
      throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                codeOwnersPluginConfiguration.isDisabled(
                    BranchNameKey.create(Project.nameKey("non-existing-project"), "master")));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "cannot get code-owners plugin config for non-existing project non-existing-project");
  }

  @Test
  public void checkIfCodeOwnersFunctionalityIsDisabledForNonExistingBranch() throws Exception {
    assertThat(
            codeOwnersPluginConfiguration.isDisabled(BranchNameKey.create(project, "non-existing")))
        .isFalse();
  }

  @Test
  public void checkIfCodeOwnersFunctionalityIsDisabledForProjectWithEmptyConfig() throws Exception {
    assertThat(codeOwnersPluginConfiguration.isDisabled(project)).isFalse();
  }

  @Test
  public void checkIfCodeOwnersFunctionalityIsDisabledForBranchWithEmptyConfig() throws Exception {
    assertThat(codeOwnersPluginConfiguration.isDisabled(BranchNameKey.create(project, "master")))
        .isFalse();
  }

  @Test
  public void codeOwnersFunctionalityIsDisabledForProject() throws Exception {
    disableCodeOwnersForProject(project);
    assertThat(codeOwnersPluginConfiguration.isDisabled(project)).isTrue();
  }

  @Test
  public void codeOwnersFunctionalityIsDisabledForBranchIfItIsDisabledForProject()
      throws Exception {
    disableCodeOwnersForProject(project);
    assertThat(codeOwnersPluginConfiguration.isDisabled(BranchNameKey.create(project, "master")))
        .isTrue();
  }

  @Test
  public void codeOwnersFunctionalityIsDisabledForBranch_exactRef() throws Exception {
    configureDisabledBranch(project, "refs/heads/master");
    assertThat(codeOwnersPluginConfiguration.isDisabled(BranchNameKey.create(project, "master")))
        .isTrue();
    assertThat(codeOwnersPluginConfiguration.isDisabled(BranchNameKey.create(project, "other")))
        .isFalse();
  }

  @Test
  public void codeOwnersFunctionalityIsDisabledForBranch_refPattern() throws Exception {
    configureDisabledBranch(project, "refs/heads/*");
    assertThat(codeOwnersPluginConfiguration.isDisabled(BranchNameKey.create(project, "master")))
        .isTrue();
    assertThat(codeOwnersPluginConfiguration.isDisabled(BranchNameKey.create(project, "other")))
        .isTrue();
    assertThat(
            codeOwnersPluginConfiguration.isDisabled(
                BranchNameKey.create(project, RefNames.REFS_META)))
        .isFalse();
  }

  @Test
  public void codeOwnersFunctionalityIsDisabledForBranch_regularExpression() throws Exception {
    configureDisabledBranch(project, "^refs/heads/.*");
    assertThat(codeOwnersPluginConfiguration.isDisabled(BranchNameKey.create(project, "master")))
        .isTrue();
    assertThat(codeOwnersPluginConfiguration.isDisabled(BranchNameKey.create(project, "other")))
        .isTrue();
    assertThat(
            codeOwnersPluginConfiguration.isDisabled(
                BranchNameKey.create(project, RefNames.REFS_META)))
        .isFalse();
  }

  @Test
  public void codeOwnersFunctionalityIsDisabledForBranch_invalidRegularExpression()
      throws Exception {
    configureDisabledBranch(project, "^refs/heads/[");
    assertThat(codeOwnersPluginConfiguration.isDisabled(BranchNameKey.create(project, "master")))
        .isFalse();
  }

  @Test
  public void disabledIsInheritedFromParentProject() throws Exception {
    disableCodeOwnersForProject(allProjects);
    assertThat(codeOwnersPluginConfiguration.isDisabled(project)).isTrue();
  }

  @Test
  public void inheritedDisabledAlsoCountsForBranch() throws Exception {
    disableCodeOwnersForProject(allProjects);
    assertThat(codeOwnersPluginConfiguration.isDisabled(BranchNameKey.create(project, "master")))
        .isTrue();
  }

  @Test
  public void inheritedDisabledValueIsIgnoredIfInvalid() throws Exception {
    configureDisabled(project, "invalid");
    assertThat(codeOwnersPluginConfiguration.isDisabled(project)).isFalse();
  }

  @Test
  public void inheritedDisabledValueIsIgnoredForBranchIfInvalid() throws Exception {
    configureDisabled(project, "invalid");
    assertThat(codeOwnersPluginConfiguration.isDisabled(BranchNameKey.create(project, "master")))
        .isFalse();
  }

  @Test
  public void disabledForOtherProjectHasNoEffect() throws Exception {
    Project.NameKey otherProject = projectOperations.newProject().create();
    disableCodeOwnersForProject(otherProject);
    assertThat(codeOwnersPluginConfiguration.isDisabled(project)).isFalse();
  }

  @Test
  public void disabledBranchForOtherProjectHasNoEffect() throws Exception {
    Project.NameKey otherProject = projectOperations.newProject().create();
    configureDisabledBranch(otherProject, "refs/heads/master");
    assertThat(codeOwnersPluginConfiguration.isDisabled(BranchNameKey.create(project, "master")))
        .isFalse();
  }

  @Test
  public void disabledBranchIsInheritedFromParentProject() throws Exception {
    configureDisabledBranch(allProjects, "refs/heads/master");
    assertThat(codeOwnersPluginConfiguration.isDisabled(BranchNameKey.create(project, "master")))
        .isTrue();
  }

  @Test
  public void inheritedDisabledCanBeOverridden() throws Exception {
    disableCodeOwnersForProject(allProjects);
    enableCodeOwnersForProject(project);
    assertThat(codeOwnersPluginConfiguration.isDisabled(BranchNameKey.create(project, "master")))
        .isFalse();
  }

  @Test
  public void inheritedDisabledBranchCanBeOverridden() throws Exception {
    configureDisabledBranch(allProjects, "refs/heads/master");
    enableCodeOwnersForAllBranches(project);
    assertThat(codeOwnersPluginConfiguration.isDisabled(BranchNameKey.create(project, "master")))
        .isFalse();
  }

  @Test
  public void cannotGetBackendForNonExistingProject() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                codeOwnersPluginConfiguration.getBackend(
                    BranchNameKey.create(Project.nameKey("non-existing-project"), "master")));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "cannot get code-owners plugin config for non-existing project non-existing-project");
  }

  @Test
  public void getBackendForNonExistingBranch() throws Exception {
    assertThat(
            codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "non-existing")))
        .isInstanceOf(FindOwnersBackend.class);
  }

  @Test
  public void getDefaultBackendWhenNoBackendIsConfigured() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")))
        .isInstanceOf(FindOwnersBackend.class);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = TestCodeOwnerBackend.ID)
  public void getConfiguredDefaultBackend() throws Exception {
    try (AutoCloseable registration = registerTestBackend()) {
      assertThat(codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")))
          .isInstanceOf(TestCodeOwnerBackend.class);
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = "non-existing-backend")
  public void cannotGetBackendIfNonExistingBackendIsConfigured() throws Exception {
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () ->
                codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Invalid configuration of the code-owners plugin. Code owner backend"
                + " 'non-existing-backend' that is configured in gerrit.config (parameter"
                + " plugin.code-owners.backend) not found.");
  }

  @Test
  public void getBackendConfiguredOnProjectLevel() throws Exception {
    configureBackend(project, TestCodeOwnerBackend.ID);
    try (AutoCloseable registration = registerTestBackend()) {
      assertThat(codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")))
          .isInstanceOf(TestCodeOwnerBackend.class);
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = FindOwnersBackend.ID)
  public void backendConfiguredOnProjectLevelOverridesDefaultBackend() throws Exception {
    configureBackend(project, TestCodeOwnerBackend.ID);
    try (AutoCloseable registration = registerTestBackend()) {
      assertThat(codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")))
          .isInstanceOf(TestCodeOwnerBackend.class);
    }
  }

  @Test
  public void backendIsInheritedFromParentProject() throws Exception {
    configureBackend(allProjects, TestCodeOwnerBackend.ID);
    try (AutoCloseable registration = registerTestBackend()) {
      assertThat(codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")))
          .isInstanceOf(TestCodeOwnerBackend.class);
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = FindOwnersBackend.ID)
  public void inheritedBackendOverridesDefaultBackend() throws Exception {
    configureBackend(allProjects, TestCodeOwnerBackend.ID);
    try (AutoCloseable registration = registerTestBackend()) {
      assertThat(codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")))
          .isInstanceOf(TestCodeOwnerBackend.class);
    }
  }

  @Test
  public void projectLevelBackendOverridesInheritedBackend() throws Exception {
    configureBackend(allProjects, TestCodeOwnerBackend.ID);
    configureBackend(project, FindOwnersBackend.ID);
    try (AutoCloseable registration = registerTestBackend()) {
      assertThat(codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")))
          .isInstanceOf(FindOwnersBackend.class);
    }
  }

  @Test
  public void cannotGetBackendIfNonExistingBackendIsConfiguredOnProjectLevel() throws Exception {
    configureBackend(project, "non-existing-backend");
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () ->
                codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Invalid configuration of the code-owners plugin. Code owner backend"
                    + " 'non-existing-backend' that is configured for project %s in"
                    + " code-owners.config (parameter codeOwners.backend) not found.",
                project));
  }

  @Test
  public void projectLevelBackendForOtherProjectHasNoEffect() throws Exception {
    Project.NameKey otherProject = projectOperations.newProject().create();
    configureBackend(otherProject, TestCodeOwnerBackend.ID);
    try (AutoCloseable registration = registerTestBackend()) {
      assertThat(codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")))
          .isInstanceOf(FindOwnersBackend.class);
    }
  }

  @Test
  public void getBackendConfiguredOnBranchLevel() throws Exception {
    configureBackend(project, "refs/heads/master", TestCodeOwnerBackend.ID);
    try (AutoCloseable registration = registerTestBackend()) {
      assertThat(codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")))
          .isInstanceOf(TestCodeOwnerBackend.class);
    }
  }

  @Test
  public void getBackendConfiguredOnBranchLevelShortName() throws Exception {
    configureBackend(project, "master", TestCodeOwnerBackend.ID);
    try (AutoCloseable registration = registerTestBackend()) {
      assertThat(codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")))
          .isInstanceOf(TestCodeOwnerBackend.class);
    }
  }

  @Test
  public void branchLevelBackendOnFullNameTakesPrecedenceOverBranchLevelBackendOnShortName()
      throws Exception {
    configureBackend(project, "master", TestCodeOwnerBackend.ID);
    configureBackend(project, "refs/heads/master", FindOwnersBackend.ID);
    try (AutoCloseable registration = registerTestBackend()) {
      assertThat(codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")))
          .isInstanceOf(FindOwnersBackend.class);
    }
  }

  @Test
  public void branchLevelBackendOverridesProjectLevelBackend() throws Exception {
    configureBackend(project, TestCodeOwnerBackend.ID);
    configureBackend(project, "master", FindOwnersBackend.ID);
    try (AutoCloseable registration = registerTestBackend()) {
      assertThat(codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")))
          .isInstanceOf(FindOwnersBackend.class);
    }
  }

  @Test
  public void cannotGetBackendIfNonExistingBackendIsConfiguredOnBranchLevel() throws Exception {
    configureBackend(project, "master", "non-existing-backend");
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () ->
                codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Invalid configuration of the code-owners plugin. Code owner backend"
                    + " 'non-existing-backend' that is configured for project %s in"
                    + " code-owners.config (parameter codeOwners.master.backend) not found.",
                project));
  }

  @Test
  public void branchLevelBackendForOtherBranchHasNoEffect() throws Exception {
    configureBackend(project, "foo", TestCodeOwnerBackend.ID);
    try (AutoCloseable registration = registerTestBackend()) {
      assertThat(codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")))
          .isInstanceOf(FindOwnersBackend.class);
    }
  }

  @Test
  public void cannotGetRequiredApprovalForNonExistingProject() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                codeOwnersPluginConfiguration.getRequiredApproval(
                    Project.nameKey("non-existing-project")));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "cannot get code-owners plugin config for non-existing project non-existing-project");
  }

  @Test
  public void getDefaultRequiredApprovalWhenNoRequiredApprovalIsConfigured() throws Exception {
    RequiredApproval requiredApproval = codeOwnersPluginConfiguration.getRequiredApproval(project);
    assertThat(requiredApproval).hasLabelNameThat().isEqualTo(RequiredApprovalConfig.DEFAULT_LABEL);
    assertThat(requiredApproval).hasValueThat().isEqualTo(RequiredApprovalConfig.DEFAULT_VALUE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "Code-Review+2")
  public void getConfiguredDefaultRequireApproval() throws Exception {
    RequiredApproval requiredApproval = codeOwnersPluginConfiguration.getRequiredApproval(project);
    assertThat(requiredApproval).hasLabelNameThat().isEqualTo("Code-Review");
    assertThat(requiredApproval).hasValueThat().isEqualTo(2);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "Foo-Bar+1")
  public void cannotGetRequiredApprovalIfNonExistingLabelIsConfiguredAsRequiredApproval()
      throws Exception {
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () -> codeOwnersPluginConfiguration.getRequiredApproval(project));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Invalid configuration of the code-owners plugin. Required approval 'Foo-Bar+1'"
                    + " that is configured in gerrit.config (parameter"
                    + " plugin.code-owners.requiredApproval) is invalid: Label Foo-Bar doesn't exist"
                    + " for project %s.",
                project.get()));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "Code-Review+3")
  public void cannotGetRequiredApprovalIfNonExistingLabelValueIsConfiguredAsRequiredApproval()
      throws Exception {
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () -> codeOwnersPluginConfiguration.getRequiredApproval(project));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Invalid configuration of the code-owners plugin. Required approval"
                    + " 'Code-Review+3' that is configured in gerrit.config (parameter"
                    + " plugin.code-owners.requiredApproval) is invalid: Label Code-Review on"
                    + " project %s doesn't allow value 3.",
                project.get()));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "INVALID")
  public void cannotGetRequiredApprovalIfInvalidRequiredApprovalIsConfigured() throws Exception {
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () -> codeOwnersPluginConfiguration.getRequiredApproval(project));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Invalid configuration of the code-owners plugin. Required approval 'INVALID' that is"
                + " configured in gerrit.config (parameter plugin.code-owners.requiredApproval) is"
                + " invalid: Invalid format, expected '<label-name>+<label-value>'.");
  }

  @Test
  public void getRequiredApprovalConfiguredOnProjectLevel() throws Exception {
    configureRequiredApproval(project, "Code-Review+2");
    RequiredApproval requiredApproval = codeOwnersPluginConfiguration.getRequiredApproval(project);
    assertThat(requiredApproval).hasLabelNameThat().isEqualTo("Code-Review");
    assertThat(requiredApproval).hasValueThat().isEqualTo(2);
  }

  @Test
  public void getRequiredApprovalMultipleConfiguredOnProjectLevel() throws Exception {
    setCodeOwnersConfig(
        project,
        /* subsection= */ null,
        RequiredApprovalConfig.KEY_REQUIRED_APPROVAL,
        ImmutableList.of("Code-Review+2", "Code-Review+1"));

    // If multiple values are set for a key, the last value wins.
    RequiredApproval requiredApproval = codeOwnersPluginConfiguration.getRequiredApproval(project);
    assertThat(requiredApproval).hasLabelNameThat().isEqualTo("Code-Review");
    assertThat(requiredApproval).hasValueThat().isEqualTo(1);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "Code-Review+1")
  public void requiredApprovalConfiguredOnProjectLevelOverridesDefaultRequiredApproval()
      throws Exception {
    configureRequiredApproval(project, "Code-Review+2");
    RequiredApproval requiredApproval = codeOwnersPluginConfiguration.getRequiredApproval(project);
    assertThat(requiredApproval).hasLabelNameThat().isEqualTo("Code-Review");
    assertThat(requiredApproval).hasValueThat().isEqualTo(2);
  }

  @Test
  public void requiredApprovalIsInheritedFromParentProject() throws Exception {
    configureRequiredApproval(allProjects, "Code-Review+2");
    RequiredApproval requiredApproval = codeOwnersPluginConfiguration.getRequiredApproval(project);
    assertThat(requiredApproval).hasLabelNameThat().isEqualTo("Code-Review");
    assertThat(requiredApproval).hasValueThat().isEqualTo(2);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = FindOwnersBackend.ID)
  public void inheritedRequiredApprovalOverridesDefaultRequiredApproval() throws Exception {
    configureRequiredApproval(allProjects, "Code-Review+2");
    RequiredApproval requiredApproval = codeOwnersPluginConfiguration.getRequiredApproval(project);
    assertThat(requiredApproval).hasLabelNameThat().isEqualTo("Code-Review");
    assertThat(requiredApproval).hasValueThat().isEqualTo(2);
  }

  @Test
  public void projectLevelRequiredApprovalOverridesInheritedRequiredApproval() throws Exception {
    configureRequiredApproval(allProjects, "Code-Review+1");
    configureRequiredApproval(project, "Code-Review+2");
    RequiredApproval requiredApproval = codeOwnersPluginConfiguration.getRequiredApproval(project);
    assertThat(requiredApproval).hasLabelNameThat().isEqualTo("Code-Review");
    assertThat(requiredApproval).hasValueThat().isEqualTo(2);
  }

  @Test
  public void
      cannotGetRequiredApprovalIfNonExistingLabelIsConfiguredAsRequiredApprovalOnProjectLevel()
          throws Exception {
    configureRequiredApproval(project, "Foo-Bar+1");
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () -> codeOwnersPluginConfiguration.getRequiredApproval(project));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Invalid configuration of the code-owners plugin. Required approval 'Foo-Bar+1'"
                    + " that is configured in code-owners.config (parameter"
                    + " codeOwners.requiredApproval) is invalid: Label Foo-Bar doesn't exist for"
                    + " project %s.",
                project.get()));
  }

  @Test
  public void
      cannotGetRequiredApprovalIfNonExistingLabelValueIsConfiguredAsRequiredApprovalOnProjectLevel()
          throws Exception {
    configureRequiredApproval(project, "Code-Review+3");
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () -> codeOwnersPluginConfiguration.getRequiredApproval(project));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Invalid configuration of the code-owners plugin. Required approval"
                    + " 'Code-Review+3' that is configured in code-owners.config (parameter"
                    + " codeOwners.requiredApproval) is invalid: Label Code-Review on project %s"
                    + " doesn't allow value 3.",
                project.get()));
  }

  @Test
  public void cannotGetRequiredApprovalIfInvalidRequiredApprovalIsConfiguredOnProjectLevel()
      throws Exception {
    configureRequiredApproval(project, "INVALID");
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () -> codeOwnersPluginConfiguration.getRequiredApproval(project));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Invalid configuration of the code-owners plugin. Required approval 'INVALID' that is"
                + " configured in code-owners.config (parameter codeOwners.requiredApproval) is"
                + " invalid: Invalid format, expected '<label-name>+<label-value>'.");
  }

  @Test
  public void projectLevelRequiredApprovalForOtherProjectHasNoEffect() throws Exception {
    Project.NameKey otherProject = projectOperations.newProject().create();
    configureRequiredApproval(otherProject, "Code-Review+2");
    RequiredApproval requiredApproval = codeOwnersPluginConfiguration.getRequiredApproval(project);
    assertThat(requiredApproval).hasLabelNameThat().isEqualTo("Code-Review");
    assertThat(requiredApproval).hasValueThat().isEqualTo(1);
  }

  @Test
  public void cannotGetOverrideApprovalForNonExistingProject() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                codeOwnersPluginConfiguration.getOverrideApproval(
                    Project.nameKey("non-existing-project")));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "cannot get code-owners plugin config for non-existing project non-existing-project");
  }

  @Test
  public void getOverrideApprovalWhenNoRequiredApprovalIsConfigured() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getOverrideApproval(project)).isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Code-Review+2")
  public void getConfiguredDefaultOverrideApproval() throws Exception {
    ImmutableSet<RequiredApproval> requiredApproval =
        codeOwnersPluginConfiguration.getOverrideApproval(project);
    assertThat(requiredApproval).hasSize(1);
    assertThat(requiredApproval).element(0).hasLabelNameThat().isEqualTo("Code-Review");
    assertThat(requiredApproval).element(0).hasValueThat().isEqualTo(2);
  }

  @Test
  public void getOverrideApprovalConfiguredOnProjectLevel() throws Exception {
    configureOverrideApproval(project, "Code-Review+2");
    ImmutableSet<RequiredApproval> requiredApproval =
        codeOwnersPluginConfiguration.getOverrideApproval(project);
    assertThat(requiredApproval).hasSize(1);
    assertThat(requiredApproval).element(0).hasLabelNameThat().isEqualTo("Code-Review");
    assertThat(requiredApproval).element(0).hasValueThat().isEqualTo(2);
  }

  @Test
  public void getOverrideApprovalMultipleConfiguredOnProjectLevel() throws Exception {
    createOwnersOverrideLabel();
    createOwnersOverrideLabel("Other-Override");

    setCodeOwnersConfig(
        project,
        /* subsection= */ null,
        OverrideApprovalConfig.KEY_OVERRIDE_APPROVAL,
        ImmutableList.of("Owners-Override+1", "Other-Override+1"));

    ImmutableSet<RequiredApproval> requiredApprovals =
        codeOwnersPluginConfiguration.getOverrideApproval(project);
    assertThat(
            requiredApprovals.stream()
                .map(requiredApproval -> requiredApproval.toString())
                .collect(toImmutableSet()))
        .containsExactly("Owners-Override+1", "Other-Override+1");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "INVALID")
  public void getOverrideApprovalIfInvalidOverrideApprovalIsConfigured() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getOverrideApproval(project)).isEmpty();
  }

  @Test
  public void getOverrideApprovalIfInvalidOverrideApprovalIsConfiguredOnProjectLevel()
      throws Exception {
    configureOverrideApproval(project, "INVALID");
    assertThat(codeOwnersPluginConfiguration.getOverrideApproval(project)).isEmpty();
  }

  @Test
  public void getOverrideApprovalDuplicatesAreFilteredOut() throws Exception {
    setCodeOwnersConfig(
        project,
        /* subsection= */ null,
        OverrideApprovalConfig.KEY_OVERRIDE_APPROVAL,
        ImmutableList.of("Code-Review+2", "Code-Review+1", "Code-Review+2"));

    // If multiple values are set for a key, the last value wins.
    ImmutableSet<RequiredApproval> requiredApproval =
        codeOwnersPluginConfiguration.getOverrideApproval(project);
    assertThat(requiredApproval).hasSize(1);
    assertThat(requiredApproval).element(0).hasLabelNameThat().isEqualTo("Code-Review");
    assertThat(requiredApproval).element(0).hasValueThat().isEqualTo(1);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableExperimentalRestEndpoints", value = "false")
  public void checkExperimentalRestEndpointsEnabledThrowsExceptionIfDisabled() throws Exception {
    MethodNotAllowedException exception =
        assertThrows(
            MethodNotAllowedException.class,
            () -> codeOwnersPluginConfiguration.checkExperimentalRestEndpointsEnabled());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("experimental code owners REST endpoints are disabled");
  }

  @Test
  public void experimentalRestEndpointsNotEnabled() throws Exception {
    assertThat(codeOwnersPluginConfiguration.areExperimentalRestEndpointsEnabled()).isFalse();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableExperimentalRestEndpoints", value = "true")
  public void experimentalRestEndpointsEnabled() throws Exception {
    assertThat(codeOwnersPluginConfiguration.areExperimentalRestEndpointsEnabled()).isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableExperimentalRestEndpoints", value = "invalid")
  public void experimentalRestEndpointsNotEnabled_invalidConfig() throws Exception {
    assertThat(codeOwnersPluginConfiguration.areExperimentalRestEndpointsEnabled()).isFalse();
  }

  @Test
  public void cannotGetFileExtensionForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> codeOwnersPluginConfiguration.getFileExtension(/* project= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void getFileExtensionIfNoneIsConfigured() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getFileExtension(project)).isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fileExtension", value = "foo")
  public void getFileExtensionIfNoneIsConfiguredOnProjectLevel() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getFileExtension(project)).value().isEqualTo("foo");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fileExtension", value = "foo")
  public void fileExtensionOnProjectLevelOverridesDefaultFileExtension() throws Exception {
    configureFileExtension(project, "bar");
    assertThat(codeOwnersPluginConfiguration.getFileExtension(project)).value().isEqualTo("bar");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fileExtension", value = "foo")
  public void fileExtensionIsInheritedFromParentProject() throws Exception {
    configureFileExtension(allProjects, "bar");
    assertThat(codeOwnersPluginConfiguration.getFileExtension(project)).value().isEqualTo("bar");
  }

  @Test
  public void inheritedFileExtensionCanBeOverridden() throws Exception {
    configureFileExtension(allProjects, "foo");
    configureFileExtension(project, "bar");
    assertThat(codeOwnersPluginConfiguration.getFileExtension(project)).value().isEqualTo("bar");
  }

  @Test
  public void cannotGetMergeCommitStrategyForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> codeOwnersPluginConfiguration.getMergeCommitStrategy(/* project= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void getMergeCommitStrategyIfNoneIsConfigured() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getMergeCommitStrategy(project))
        .isEqualTo(MergeCommitStrategy.ALL_CHANGED_FILES);
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.mergeCommitStrategy",
      value = "FILES_WITH_CONFLICT_RESOLUTION")
  public void getMergeCommitStrategyIfNoneIsConfiguredOnProjectLevel() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getMergeCommitStrategy(project))
        .isEqualTo(MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION);
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.mergeCommitStrategy",
      value = "FILES_WITH_CONFLICT_RESOLUTION")
  public void mergeCommitStrategyOnProjectLevelOverridesGlobalMergeCommitStrategy()
      throws Exception {
    configureMergeCommitStrategy(project, MergeCommitStrategy.ALL_CHANGED_FILES);
    assertThat(codeOwnersPluginConfiguration.getMergeCommitStrategy(project))
        .isEqualTo(MergeCommitStrategy.ALL_CHANGED_FILES);
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.mergeCommitStrategy",
      value = "FILES_WITH_CONFLICT_RESOLUTION")
  public void mergeCommitStrategyIsInheritedFromParentProject() throws Exception {
    configureMergeCommitStrategy(allProjects, MergeCommitStrategy.ALL_CHANGED_FILES);
    assertThat(codeOwnersPluginConfiguration.getMergeCommitStrategy(project))
        .isEqualTo(MergeCommitStrategy.ALL_CHANGED_FILES);
  }

  @Test
  public void inheritedMergeCommitStrategyCanBeOverridden() throws Exception {
    configureMergeCommitStrategy(allProjects, MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION);
    configureMergeCommitStrategy(project, MergeCommitStrategy.ALL_CHANGED_FILES);
    assertThat(codeOwnersPluginConfiguration.getMergeCommitStrategy(project))
        .isEqualTo(MergeCommitStrategy.ALL_CHANGED_FILES);
  }

  @Test
  public void cannotGetFallbackCodeOwnersForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> codeOwnersPluginConfiguration.getFallbackCodeOwners(/* project= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void getFallbackCodeOwnersIfNoneIsConfigured() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getFallbackCodeOwners(project))
        .isEqualTo(FallbackCodeOwners.NONE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "ALL_USERS")
  public void getFallbackCodeOwnersIfNoneIsConfiguredOnProjectLevel() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getFallbackCodeOwners(project))
        .isEqualTo(FallbackCodeOwners.ALL_USERS);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "ALL_USERS")
  public void fallbackCodeOnwersOnProjectLevelOverridesGlobalFallbackCodeOwners() throws Exception {
    configureFallbackCodeOwners(project, FallbackCodeOwners.NONE);
    assertThat(codeOwnersPluginConfiguration.getFallbackCodeOwners(project))
        .isEqualTo(FallbackCodeOwners.NONE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "ALL_USERS")
  public void fallbackCodeOwnersIsInheritedFromParentProject() throws Exception {
    configureFallbackCodeOwners(allProjects, FallbackCodeOwners.NONE);
    assertThat(codeOwnersPluginConfiguration.getFallbackCodeOwners(project))
        .isEqualTo(FallbackCodeOwners.NONE);
  }

  @Test
  public void inheritedFallbackCodeOwnersCanBeOverridden() throws Exception {
    configureFallbackCodeOwners(allProjects, FallbackCodeOwners.ALL_USERS);
    configureFallbackCodeOwners(project, FallbackCodeOwners.NONE);
    assertThat(codeOwnersPluginConfiguration.getFallbackCodeOwners(project))
        .isEqualTo(FallbackCodeOwners.NONE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void implicitApprovalsAreDisabledIfRequiredLabelIgnoresSelfApprovals() throws Exception {
    assertThat(codeOwnersPluginConfiguration.areImplicitApprovalsEnabled(project)).isTrue();

    LabelDefinitionInput input = new LabelDefinitionInput();
    input.ignoreSelfApproval = true;
    gApi.projects().name(allProjects.get()).label("Code-Review").update(input);
    assertThat(codeOwnersPluginConfiguration.areImplicitApprovalsEnabled(project)).isFalse();
  }

  @Test
  public void cannotGetMaxPathsInChangeMessagesForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> codeOwnersPluginConfiguration.getMaxPathsInChangeMessages(/* project= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void getMaxPathsInChangeMessagesIfNoneIsConfigured() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getMaxPathsInChangeMessages(project))
        .isEqualTo(GeneralConfig.DEFAULT_MAX_PATHS_IN_CHANGE_MESSAGES);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxPathsInChangeMessages", value = "50")
  public void getMaxPathsInChangeMessagesIfNoneIsConfiguredOnProjectLevel() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getMaxPathsInChangeMessages(project)).isEqualTo(50);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxPathsInChangeMessages", value = "50")
  public void maxPathInChangeMessagesOnProjectLevelOverridesGlobalMaxPathInChangeMessages()
      throws Exception {
    configureFallbackCodeOwners(project, FallbackCodeOwners.NONE);
    assertThat(codeOwnersPluginConfiguration.getFallbackCodeOwners(project))
        .isEqualTo(FallbackCodeOwners.NONE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxPathsInChangeMessages", value = "50")
  public void maxPathInChangeMessagesIsInheritedFromParentProject() throws Exception {
    configureMaxPathsInChangeMessages(allProjects, 20);
    assertThat(codeOwnersPluginConfiguration.getMaxPathsInChangeMessages(project)).isEqualTo(20);
  }

  @Test
  public void inheritedMaxPathInChangeMessagesCanBeOverridden() throws Exception {
    configureMaxPathsInChangeMessages(allProjects, 50);
    configureMaxPathsInChangeMessages(project, 20);
    assertThat(codeOwnersPluginConfiguration.getMaxPathsInChangeMessages(project)).isEqualTo(20);
  }

  private void configureDisabled(Project.NameKey project, String disabled) throws Exception {
    setCodeOwnersConfig(project, /* subsection= */ null, StatusConfig.KEY_DISABLED, disabled);
  }

  private void configureDisabledBranch(Project.NameKey project, String disabledBranch)
      throws Exception {
    setCodeOwnersConfig(
        project, /* subsection= */ null, StatusConfig.KEY_DISABLED_BRANCH, disabledBranch);
  }

  private void enableCodeOwnersForAllBranches(Project.NameKey project) throws Exception {
    setCodeOwnersConfig(project, /* subsection= */ null, StatusConfig.KEY_DISABLED_BRANCH, "");
  }

  private void configureBackend(Project.NameKey project, String backendName) throws Exception {
    configureBackend(project, /* branch= */ null, backendName);
  }

  private void configureBackend(
      Project.NameKey project, @Nullable String branch, String backendName) throws Exception {
    setCodeOwnersConfig(project, branch, BackendConfig.KEY_BACKEND, backendName);
  }

  private void configureRequiredApproval(Project.NameKey project, String requiredApproval)
      throws Exception {
    setCodeOwnersConfig(
        project,
        /* subsection= */ null,
        RequiredApprovalConfig.KEY_REQUIRED_APPROVAL,
        requiredApproval);
  }

  private void configureOverrideApproval(Project.NameKey project, String requiredApproval)
      throws Exception {
    setCodeOwnersConfig(
        project,
        /* subsection= */ null,
        OverrideApprovalConfig.KEY_OVERRIDE_APPROVAL,
        requiredApproval);
  }

  private void configureFileExtension(Project.NameKey project, String fileExtension)
      throws Exception {
    setCodeOwnersConfig(
        project, /* subsection= */ null, GeneralConfig.KEY_FILE_EXTENSION, fileExtension);
  }

  private void configureMergeCommitStrategy(
      Project.NameKey project, MergeCommitStrategy mergeCommitStrategy) throws Exception {
    setCodeOwnersConfig(
        project,
        /* subsection= */ null,
        GeneralConfig.KEY_MERGE_COMMIT_STRATEGY,
        mergeCommitStrategy.name());
  }

  private void configureFallbackCodeOwners(
      Project.NameKey project, FallbackCodeOwners fallbackCodeOwners) throws Exception {
    setCodeOwnersConfig(
        project,
        /* subsection= */ null,
        GeneralConfig.KEY_FALLBACK_CODE_OWNERS,
        fallbackCodeOwners.name());
  }

  private void configureMaxPathsInChangeMessages(
      Project.NameKey project, int maxPathsInChangeMessages) throws Exception {
    setCodeOwnersConfig(
        project,
        /* subsection= */ null,
        GeneralConfig.KEY_MAX_PATHS_IN_CHANGE_MESSAGES,
        Integer.toString(maxPathsInChangeMessages));
  }

  private AutoCloseable registerTestBackend() {
    RegistrationHandle registrationHandle =
        ((PrivateInternals_DynamicMapImpl<CodeOwnerBackend>) codeOwnerBackends)
            .put("gerrit", TestCodeOwnerBackend.ID, Providers.of(new TestCodeOwnerBackend()));
    return registrationHandle::remove;
  }

  private static class TestCodeOwnerBackend implements CodeOwnerBackend {
    static final String ID = "test-backend";

    @Override
    public Optional<CodeOwnerConfig> getCodeOwnerConfig(
        CodeOwnerConfig.Key codeOwnerConfigKey,
        @Nullable RevWalk revWalk,
        @Nullable ObjectId revision) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<CodeOwnerConfig> upsertCodeOwnerConfig(
        CodeOwnerConfig.Key codeOwnerConfigKey,
        CodeOwnerConfigUpdate codeOwnerConfigUpdate,
        @Nullable IdentifiedUser currentUser) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean isCodeOwnerConfigFile(NameKey project, String fileName) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Path getFilePath(CodeOwnerConfig.Key codeOwnerConfigKey) {
      throw new UnsupportedOperationException("not implemented");
    }
  }
}
