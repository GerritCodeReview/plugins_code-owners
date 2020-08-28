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

import static com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;

/**
 * Class to read the general code owners configuration from {@code gerrit.config} and from {@code
 * code-owners.config} in {@code refs/meta/config}.
 *
 * <p>Default values are configured in {@code gerrit.config}.
 *
 * <p>The default values can be overridden on project-level in {@code code-owners.config} in {@code
 * refs/meta/config}.
 *
 * <p>Projects that have no configuration inherit the configuration from their parent projects.
 */
@Singleton
public class GeneralConfig {
  @VisibleForTesting public static final String KEY_FILE_EXTENSION = "fileExtension";

  private final PluginConfig pluginConfigFromGerritConfig;

  @Inject
  GeneralConfig(@PluginName String pluginName, PluginConfigFactory pluginConfigFactory) {
    this.pluginConfigFromGerritConfig = pluginConfigFactory.getFromGerritConfig(pluginName);
  }

  /**
   * Gets the file extension that should be used for code owner config files in the given project.
   *
   * @param pluginConfig the plugin config from which the file extension should be read.
   * @return the file extension that should be used for code owner config files in the given
   *     project, {@link Optional#empty()} if no file extension should be used
   */
  Optional<String> getFileExtension(Config pluginConfig) {
    requireNonNull(pluginConfig, "pluginConfig");

    String fileExtension = pluginConfig.getString(SECTION_CODE_OWNERS, null, KEY_FILE_EXTENSION);
    if (fileExtension != null) {
      return Optional.of(fileExtension);
    }

    return Optional.ofNullable(pluginConfigFromGerritConfig.getString(KEY_FILE_EXTENSION));
  }
}
