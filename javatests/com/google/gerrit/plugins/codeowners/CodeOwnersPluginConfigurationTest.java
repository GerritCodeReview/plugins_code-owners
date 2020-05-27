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
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnersPluginConfiguration}. */
public class CodeOwnersPluginConfigurationTest extends AbstractCodeOwnersTest {
  private CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnersPluginConfiguration =
        plugin.getSysInjector().getInstance(CodeOwnersPluginConfiguration.class);
  }

  @Test
  public void getDefaultBackendWhenNoBackendIsConfigured() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getBackend()).isInstanceOf(FindOwnersBackend.class);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = FindOwnersBackend.ID)
  public void getConfiguredBackend() throws Exception {
    assertThat(codeOwnersPluginConfiguration.getBackend()).isInstanceOf(FindOwnersBackend.class);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = "non-existing-backend")
  public void cannotGetBackendIfNonExistingBackendIsConfigured() throws Exception {
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> codeOwnersPluginConfiguration.getBackend());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Code owner backend 'non-existing-backend' that is configured in gerrit.config"
                + " (parameter plugin.code-owners.backend) not found");
  }
}
