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
import static com.google.gerrit.plugins.codeowners.config.BackendConfig.KEY_BACKEND;
import static com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackendId;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link BackendConfig}. */
public class BackendConfigTest extends AbstractCodeOwnersTest {
  private BackendConfig backendConfig;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    backendConfig = plugin.getSysInjector().getInstance(BackendConfig.class);
  }

  @Test
  public void cannotGetForBranchWithNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> backendConfig.getForBranch(null, BranchNameKey.create(project, "master")));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void cannotGetForBranchForNullBranch() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> backendConfig.getForBranch(new Config(), null));
    assertThat(npe).hasMessageThat().isEqualTo("branch");
  }

  @Test
  public void getForBranchWhenBackendIsNotSet() throws Exception {
    assertThat(backendConfig.getForBranch(new Config(), BranchNameKey.create(project, "master")))
        .isEmpty();
  }

  @Test
  public void getForBranch() throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS,
        "refs/heads/master",
        KEY_BACKEND,
        CodeOwnerBackendId.FIND_OWNERS.getBackendId());
    assertThat(backendConfig.getForBranch(cfg, BranchNameKey.create(project, "master")))
        .value()
        .isInstanceOf(FindOwnersBackend.class);
  }

  @Test
  public void getForBranchShortName() throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS, "master", KEY_BACKEND, CodeOwnerBackendId.FIND_OWNERS.getBackendId());
    assertThat(backendConfig.getForBranch(cfg, BranchNameKey.create(project, "master")))
        .value()
        .isInstanceOf(FindOwnersBackend.class);
  }

  @Test
  public void cannotGetForBranchIfConfigIsInvalid() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, "master", KEY_BACKEND, "INVALID");
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () -> backendConfig.getForBranch(cfg, BranchNameKey.create(project, "master")));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Invalid configuration of the code-owners plugin. Code owner backend 'INVALID'"
                    + " that is configured for project %s in code-owners.config (parameter"
                    + " codeOwners.master.backend) not found.",
                project.get()));
  }

  @Test
  public void cannotGetForProjectWithNullPluginConfig() throws Exception {
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> backendConfig.getForProject(null, project));
    assertThat(npe).hasMessageThat().isEqualTo("pluginConfig");
  }

  @Test
  public void cannotGetForProjectForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> backendConfig.getForProject(new Config(), null));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void getForProjectWhenBackendIsNotSet() throws Exception {
    assertThat(backendConfig.getForProject(new Config(), project)).isEmpty();
  }

  @Test
  public void getForProject() throws Exception {
    Config cfg = new Config();
    cfg.setString(
        SECTION_CODE_OWNERS, null, KEY_BACKEND, CodeOwnerBackendId.FIND_OWNERS.getBackendId());
    assertThat(backendConfig.getForProject(cfg, project))
        .value()
        .isInstanceOf(FindOwnersBackend.class);
  }

  @Test
  public void cannotGetForProjectIfConfigIsInvalid() throws Exception {
    Config cfg = new Config();
    cfg.setString(SECTION_CODE_OWNERS, null, KEY_BACKEND, "INVALID");
    InvalidPluginConfigurationException exception =
        assertThrows(
            InvalidPluginConfigurationException.class,
            () -> backendConfig.getForProject(cfg, project));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Invalid configuration of the code-owners plugin. Code owner backend 'INVALID'"
                    + " that is configured for project %s in code-owners.config (parameter"
                    + " codeOwners.backend) not found.",
                project.get()));
  }

  @Test
  public void getDefault() throws Exception {
    assertThat(backendConfig.getDefault()).isInstanceOf(FindOwnersBackend.class);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = ProtoBackend.ID)
  public void getConfiguredDefault() throws Exception {
    assertThat(backendConfig.getDefault()).isInstanceOf(ProtoBackend.class);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = "INVALID")
  public void cannotGetDefaultIfConfigIsInvalid() throws Exception {
    InvalidPluginConfigurationException exception =
        assertThrows(InvalidPluginConfigurationException.class, () -> backendConfig.getDefault());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Invalid configuration of the code-owners plugin. Code owner backend 'INVALID' that"
                + " is configured in gerrit.config (parameter plugin.code-owners.backend) not"
                + " found.");
  }
}
