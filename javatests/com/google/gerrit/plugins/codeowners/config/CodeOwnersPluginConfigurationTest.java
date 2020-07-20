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
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigUpdate;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.util.Providers;
import java.util.Optional;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
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
                    BranchNameKey.create(Project.nameKey("non-existing-project"), "master")));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "cannot get code-owners plugin config for non-existing project non-existing-project");
  }

  @Test
  public void getRequiredApprovalForNonExistingBranch() throws Exception {
    assertThat(
            codeOwnersPluginConfiguration.getRequiredApproval(
                BranchNameKey.create(project, "non-existing")))
        .isEqualTo(
            RequiredApproval.createDefault(
                projectCache.get(project).orElseThrow(illegalState(project))));
  }

  @Test
  public void getDefaultRequiredApprovalWhenNoRequiredApprovalIsConfigured() throws Exception {
    assertThat(
            codeOwnersPluginConfiguration.getRequiredApproval(
                BranchNameKey.create(project, "master")))
        .isEqualTo(
            RequiredApproval.createDefault(
                projectCache.get(project).orElseThrow(illegalState(project))));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "Code-Review+2")
  public void getConfiguredDefaultRequireApproval() throws Exception {
    RequiredApproval requiredApproval =
        codeOwnersPluginConfiguration.getRequiredApproval(BranchNameKey.create(project, "master"));
    assertThat(requiredApproval.labelType().getName()).isEqualTo("Code-Review");
    assertThat(requiredApproval.value()).isEqualTo(2);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "Foo-Bar+1")
  public void cannotGetRequiredApprovalIfNonExistingLabelIsConfiguredAsRequiredApproval()
      throws Exception {
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () ->
                codeOwnersPluginConfiguration.getRequiredApproval(
                    BranchNameKey.create(project, "master")));
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
            () ->
                codeOwnersPluginConfiguration.getRequiredApproval(
                    BranchNameKey.create(project, "master")));
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
            () ->
                codeOwnersPluginConfiguration.getRequiredApproval(
                    BranchNameKey.create(project, "master")));
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
    RequiredApproval requiredApproval =
        codeOwnersPluginConfiguration.getRequiredApproval(BranchNameKey.create(project, "master"));
    assertThat(requiredApproval.labelType().getName()).isEqualTo("Code-Review");
    assertThat(requiredApproval.value()).isEqualTo(2);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "Code-Review+1")
  public void requiredApprovalConfiguredOnProjectLevelOverridesDefaultRequiredApproval()
      throws Exception {
    configureRequiredApproval(project, "Code-Review+2");
    RequiredApproval requiredApproval =
        codeOwnersPluginConfiguration.getRequiredApproval(BranchNameKey.create(project, "master"));
    assertThat(requiredApproval.labelType().getName()).isEqualTo("Code-Review");
    assertThat(requiredApproval.value()).isEqualTo(2);
  }

  @Test
  public void requiredApprovalIsInheritedFromParentProject() throws Exception {
    configureRequiredApproval(allProjects, "Code-Review+2");
    RequiredApproval requiredApproval =
        codeOwnersPluginConfiguration.getRequiredApproval(BranchNameKey.create(project, "master"));
    assertThat(requiredApproval.labelType().getName()).isEqualTo("Code-Review");
    assertThat(requiredApproval.value()).isEqualTo(2);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = FindOwnersBackend.ID)
  public void inheritedRequiredApprovalOverridesDefaultRequiredApproval() throws Exception {
    configureRequiredApproval(allProjects, "Code-Review+2");
    RequiredApproval requiredApproval =
        codeOwnersPluginConfiguration.getRequiredApproval(BranchNameKey.create(project, "master"));
    assertThat(requiredApproval.labelType().getName()).isEqualTo("Code-Review");
    assertThat(requiredApproval.value()).isEqualTo(2);
  }

  @Test
  public void projectLevelRequiredApprovalOverridesInheritedRequiredApproval() throws Exception {
    configureRequiredApproval(allProjects, "Code-Review+1");
    configureRequiredApproval(project, "Code-Review+2");
    RequiredApproval requiredApproval =
        codeOwnersPluginConfiguration.getRequiredApproval(BranchNameKey.create(project, "master"));
    assertThat(requiredApproval.labelType().getName()).isEqualTo("Code-Review");
    assertThat(requiredApproval.value()).isEqualTo(2);
  }

  @Test
  public void
      cannotGetRequiredApprovalIfNonExistingLabelIsConfiguredAsRequiredApprovalOnProjectLevel()
          throws Exception {
    configureRequiredApproval(project, "Foo-Bar+1");
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () ->
                codeOwnersPluginConfiguration.getRequiredApproval(
                    BranchNameKey.create(project, "master")));
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
            () ->
                codeOwnersPluginConfiguration.getRequiredApproval(
                    BranchNameKey.create(project, "master")));
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
            () ->
                codeOwnersPluginConfiguration.getRequiredApproval(
                    BranchNameKey.create(project, "master")));
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
    RequiredApproval requiredApproval =
        codeOwnersPluginConfiguration.getRequiredApproval(BranchNameKey.create(project, "master"));
    assertThat(requiredApproval.labelType().getName()).isEqualTo("Code-Review");
    assertThat(requiredApproval.value()).isEqualTo(1);
  }

  private void configureBackend(Project.NameKey project, String backendName) throws Exception {
    configureBackend(project, null, backendName);
  }

  private void configureBackend(
      Project.NameKey project, @Nullable String branch, String backendName) throws Exception {
    setConfig(project, branch, BackendConfig.KEY_BACKEND, backendName);
  }

  private void configureRequiredApproval(Project.NameKey project, String requiredApproval)
      throws Exception {
    setConfig(project, null, RequiredApproval.KEY_REQUIRED_APPROVAL, requiredApproval);
  }

  private void setConfig(Project.NameKey project, String subsection, String key, String value)
      throws Exception {
    Config codeOwnersConfig = new Config();
    codeOwnersConfig.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS, subsection, key, value);
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      Ref ref = testRepo.getRepository().exactRef(RefNames.REFS_CONFIG);
      RevCommit head = testRepo.getRevWalk().parseCommit(ref.getObjectId());
      testRepo.update(
          RefNames.REFS_CONFIG,
          testRepo
              .commit()
              .parent(head)
              .message("Configure code owner backend")
              .add("code-owners.config", codeOwnersConfig.toText()));
    }
    projectCache.evict(project);
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
    public Optional<CodeOwnerConfig> getCodeOwnerConfig(CodeOwnerConfig.Key codeOwnerConfigKey) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<CodeOwnerConfig> upsertCodeOwnerConfig(
        CodeOwnerConfig.Key codeOwnerConfigKey,
        CodeOwnerConfigUpdate codeOwnerConfigUpdate,
        @Nullable IdentifiedUser currentUser) {
      throw new UnsupportedOperationException("not implemented");
    }
  }
}
