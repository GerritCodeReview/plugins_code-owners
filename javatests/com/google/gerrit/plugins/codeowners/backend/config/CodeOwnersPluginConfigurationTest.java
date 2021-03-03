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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link
 * com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration}.
 */
public class CodeOwnersPluginConfigurationTest extends AbstractCodeOwnersTest {
  private CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnersPluginConfiguration =
        plugin.getSysInjector().getInstance(CodeOwnersPluginConfiguration.class);
  }

  @Test
  public void cannotGetProjectConfigForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> codeOwnersPluginConfiguration.getProjectConfig(/* projectName= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("projectName");
  }

  @Test
  public void cannotGetProjectConfigForNonExistingProject() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                codeOwnersPluginConfiguration.getProjectConfig(
                    Project.nameKey("non-existing-project")));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "cannot get code-owners plugin config for non-existing project non-existing-project");
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
  public void codeOwnerConfigCacheSizeIsUnlimitedByDefault() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getMaxCodeOwnerConfigCacheSize()).isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxCodeOwnerConfigCacheSize", value = "0")
  public void codeOwnerConfigCacheSizeIsUnlimited() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getMaxCodeOwnerConfigCacheSize()).isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxCodeOwnerConfigCacheSize", value = "10")
  public void codeOwnerConfigCacheSizeIsLimited() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getMaxCodeOwnerConfigCacheSize())
        .value()
        .isEqualTo(10);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxCodeOwnerConfigCacheSize", value = "invalid")
  public void maxCodeOwnerConfigCacheSize_invalidConfig() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getMaxCodeOwnerConfigCacheSize()).isEmpty();
  }
}
