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

package com.google.gerrit.plugins.codeowners;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigUpdate;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.server.IdentifiedUser;
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
  private CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private DynamicMap<CodeOwnersBackend> codeOwnersBackends;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnersPluginConfiguration =
        plugin.getSysInjector().getInstance(CodeOwnersPluginConfiguration.class);
    codeOwnersBackends =
        plugin.getSysInjector().getInstance(new Key<DynamicMap<CodeOwnersBackend>>() {});
  }

  @Test
  public void cannotGetBackendForNonExistingProject() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                codeOwnersPluginConfiguration.getBackend(Project.nameKey("non-existing-project")));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "cannot code-owners plugin config for non-existing project non-existing-project");
  }

  @Test
  public void getDefaultBackendWhenNoBackendIsConfigured() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getBackend(project))
        .isInstanceOf(FindOwnersBackend.class);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = TestCodeOwnersBackend.ID)
  public void getConfiguredDefaultBackend() throws Exception {
    try (AutoCloseable registration = registerTestBackend()) {
      assertThat(codeOwnersPluginConfiguration.getBackend(project))
          .isInstanceOf(TestCodeOwnersBackend.class);
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = "non-existing-backend")
  public void cannotGetBackendIfNonExistingBackendIsConfigured() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> codeOwnersPluginConfiguration.getBackend(project));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Code owner backend 'non-existing-backend' that is configured in gerrit.config"
                + " (parameter plugin.code-owners.backend) not found");
  }

  @Test
  public void getBackendConfiguredOnProjectLevel() throws Exception {
    configureBackend(project, TestCodeOwnersBackend.ID);
    try (AutoCloseable registration = registerTestBackend()) {
      assertThat(codeOwnersPluginConfiguration.getBackend(project))
          .isInstanceOf(TestCodeOwnersBackend.class);
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = FindOwnersBackend.ID)
  public void backendConfiguredOnProjectLevelOverridesDefaultBackend() throws Exception {
    configureBackend(project, TestCodeOwnersBackend.ID);
    try (AutoCloseable registration = registerTestBackend()) {
      assertThat(codeOwnersPluginConfiguration.getBackend(project))
          .isInstanceOf(TestCodeOwnersBackend.class);
    }
  }

  @Test
  public void backendIsInheritedFromParentProject() throws Exception {
    configureBackend(allProjects, TestCodeOwnersBackend.ID);
    try (AutoCloseable registration = registerTestBackend()) {
      assertThat(codeOwnersPluginConfiguration.getBackend(project))
          .isInstanceOf(TestCodeOwnersBackend.class);
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = FindOwnersBackend.ID)
  public void inheritedBackendOverridesDefaultBackend() throws Exception {
    configureBackend(allProjects, TestCodeOwnersBackend.ID);
    try (AutoCloseable registration = registerTestBackend()) {
      assertThat(codeOwnersPluginConfiguration.getBackend(project))
          .isInstanceOf(TestCodeOwnersBackend.class);
    }
  }

  @Test
  public void projectLevelBackendOverridesInheritedBackend() throws Exception {
    configureBackend(allProjects, TestCodeOwnersBackend.ID);
    configureBackend(project, FindOwnersBackend.ID);
    try (AutoCloseable registration = registerTestBackend()) {
      assertThat(codeOwnersPluginConfiguration.getBackend(project))
          .isInstanceOf(FindOwnersBackend.class);
    }
  }

  @Test
  public void cannotGetBackendIfNonExistingBackendIsConfiguredOnProjectLevel() throws Exception {
    configureBackend(project, "non-existing-backend");
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> codeOwnersPluginConfiguration.getBackend(project));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Code owner backend 'non-existing-backend' that is configured in code-owners.config"
                + " (parameter codeOwners.backend) not found");
  }

  private void configureBackend(Project.NameKey project, String backendName) throws Exception {
    Config codeOwnersConfig = new Config();
    codeOwnersConfig.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        null,
        CodeOwnersPluginConfiguration.KEY_BACKEND,
        backendName);
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      Ref ref = testRepo.getRepository().exactRef(RefNames.REFS_CONFIG);
      RevCommit head = testRepo.getRevWalk().parseCommit(ref.getObjectId());
      testRepo.update(
          RefNames.REFS_CONFIG,
          testRepo
              .commit()
              .parent(head)
              .message("Configure code owners backend")
              .add("code-owners.config", codeOwnersConfig.toText()));
    }
  }

  private AutoCloseable registerTestBackend() {
    RegistrationHandle registrationHandle =
        ((PrivateInternals_DynamicMapImpl<CodeOwnersBackend>) codeOwnersBackends)
            .put("gerrit", TestCodeOwnersBackend.ID, Providers.of(new TestCodeOwnersBackend()));
    return () -> registrationHandle.remove();
  }

  private static class TestCodeOwnersBackend implements CodeOwnersBackend {
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
