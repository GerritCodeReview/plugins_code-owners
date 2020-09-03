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
import static com.google.gerrit.plugins.codeowners.config.GeneralConfig.KEY_FILE_EXTENSION;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
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
}
