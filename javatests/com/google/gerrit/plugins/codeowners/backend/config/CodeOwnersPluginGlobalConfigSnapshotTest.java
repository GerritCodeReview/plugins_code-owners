// Copyright (C) 2021 The Android Open Source Project
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
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnersPluginGlobalConfigSnapshot}. */
public class CodeOwnersPluginGlobalConfigSnapshotTest extends AbstractCodeOwnersTest {
  private CodeOwnersPluginGlobalConfigSnapshot.Factory codeOwnersPluginGlobalConfigSnapshotFactory;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnersPluginGlobalConfigSnapshotFactory =
        plugin.getSysInjector().getInstance(CodeOwnersPluginGlobalConfigSnapshot.Factory.class);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableExperimentalRestEndpoints", value = "false")
  public void checkExperimentalRestEndpointsEnabledThrowsExceptionIfDisabled() throws Exception {
    MethodNotAllowedException exception =
        assertThrows(
            MethodNotAllowedException.class,
            () -> cfgSnapshot().checkExperimentalRestEndpointsEnabled());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("experimental code owners REST endpoints are disabled");
  }

  @Test
  public void experimentalRestEndpointsNotEnabled() throws Exception {
    assertThat(cfgSnapshot().areExperimentalRestEndpointsEnabled()).isFalse();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableExperimentalRestEndpoints", value = "true")
  public void experimentalRestEndpointsEnabled() throws Exception {
    assertThat(cfgSnapshot().areExperimentalRestEndpointsEnabled()).isTrue();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableExperimentalRestEndpoints", value = "invalid")
  public void experimentalRestEndpointsNotEnabled_invalidConfig() throws Exception {
    assertThat(cfgSnapshot().areExperimentalRestEndpointsEnabled()).isFalse();
  }

  @Test
  public void codeOwnerConfigCacheSizeIsLimitedByDefault() throws Exception {
    assertThat(cfgSnapshot().getMaxCodeOwnerConfigCacheSize())
        .value()
        .isEqualTo(CodeOwnersPluginGlobalConfigSnapshot.DEFAULT_MAX_CODE_OWNER_CONFIG_CACHE_SIZE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxCodeOwnerConfigCacheSize", value = "0")
  public void codeOwnerConfigCacheSizeIsUnlimited() throws Exception {
    assertThat(cfgSnapshot().getMaxCodeOwnerConfigCacheSize()).isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxCodeOwnerConfigCacheSize", value = "10")
  public void codeOwnerConfigCacheSizeIsLimited() throws Exception {
    assertThat(cfgSnapshot().getMaxCodeOwnerConfigCacheSize()).value().isEqualTo(10);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxCodeOwnerConfigCacheSize", value = "invalid")
  public void maxCodeOwnerConfigCacheSize_invalidConfig() throws Exception {
    assertThat(cfgSnapshot().getMaxCodeOwnerConfigCacheSize()).isEmpty();
  }

  @Test
  public void codeOwnerCacheSizeIsLimitedByDefault() throws Exception {
    assertThat(cfgSnapshot().getMaxCodeOwnerCacheSize())
        .value()
        .isEqualTo(CodeOwnersPluginGlobalConfigSnapshot.DEFAULT_MAX_CODE_OWNER_CACHE_SIZE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxCodeOwnerCacheSize", value = "0")
  public void codeOwnerCacheSizeIsUnlimited() throws Exception {
    assertThat(cfgSnapshot().getMaxCodeOwnerCacheSize()).isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxCodeOwnerCacheSize", value = "10")
  public void codeOwnerCacheSizeIsLimited() throws Exception {
    assertThat(cfgSnapshot().getMaxCodeOwnerCacheSize()).value().isEqualTo(10);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.maxCodeOwnerCacheSize", value = "invalid")
  public void maxCodeOwnerCacheSize_invalidConfig() throws Exception {
    assertThat(cfgSnapshot().getMaxCodeOwnerCacheSize()).isEmpty();
  }

  private CodeOwnersPluginGlobalConfigSnapshot cfgSnapshot() {
    return codeOwnersPluginGlobalConfigSnapshotFactory.create();
  }
}
