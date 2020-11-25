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

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.project.ProjectLevelConfig;
import com.google.gerrit.server.project.ProjectState;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;

/**
 * Base class to read the required approval configuration from {@code gerrit.config} and from {@code
 * code-owners.config} in {@code refs/meta/config}.
 *
 * <p>The default required approval is configured in {@code gerrit.config}.
 *
 * <p>On project-level the required approval is configured in {@code code-owners.config}.
 *
 * <p>Projects that have no required approval configuration inherit the configuration from their
 * parent projects.
 */
abstract class AbstractRequiredApprovalConfig {
  protected final String pluginName;

  private final PluginConfigFactory pluginConfigFactory;

  AbstractRequiredApprovalConfig(
      @PluginName String pluginName, PluginConfigFactory pluginConfigFactory) {
    this.pluginName = pluginName;
    this.pluginConfigFactory = pluginConfigFactory;
  }

  protected abstract String getConfigKey();

  /**
   * Reads the required approval for the specified project from the given plugin config with
   * fallback to {@code gerrit.config}.
   *
   * @param projectState state of the project for which the required approval should be read
   * @param pluginConfig the plugin config from which the required approval should be read
   * @return the required approval, {@link Optional#empty} if none was configured
   */
  Optional<RequiredApproval> get(ProjectState projectState, Config pluginConfig) {
    requireNonNull(projectState, "projectState");
    requireNonNull(pluginConfig, "pluginConfig");

    String requiredApproval =
        pluginConfig.getString(SECTION_CODE_OWNERS, /* subsection= */ null, getConfigKey());
    if (requiredApproval != null) {
      try {
        return Optional.of(RequiredApproval.parse(projectState, requiredApproval));
      } catch (IllegalStateException | IllegalArgumentException e) {
        throw new InvalidPluginConfigurationException(
            pluginName,
            String.format(
                "Required approval '%s' that is configured in %s.config"
                    + " (parameter %s.%s) is invalid: %s",
                requiredApproval, pluginName, SECTION_CODE_OWNERS, getConfigKey(), e.getMessage()));
      }
    }

    requiredApproval =
        pluginConfigFactory.getFromGerritConfig(pluginName).getString(getConfigKey());
    if (requiredApproval != null) {
      try {
        return Optional.of(RequiredApproval.parse(projectState, requiredApproval));
      } catch (IllegalStateException | IllegalArgumentException e) {
        throw new InvalidPluginConfigurationException(
            pluginName,
            String.format(
                "Required approval '%s' that is configured in gerrit.config"
                    + " (parameter plugin.%s.%s) is invalid: %s",
                requiredApproval, pluginName, getConfigKey(), e.getMessage()));
      }
    }

    return Optional.empty();
  }

  /**
   * Validates the required approval configuration in the given project level configuration.
   *
   * @param fileName the name of the config file
   * @param projectLevelConfig the project level plugin configuration
   * @return list of validation messages for validation errors, empty list if there are no
   *     validation errors
   */
  Optional<CommitValidationMessage> validateProjectLevelConfig(
      ProjectState projectState, String fileName, ProjectLevelConfig.Bare projectLevelConfig) {
    requireNonNull(projectState, "projectState");
    requireNonNull(fileName, "fileName");
    requireNonNull(projectLevelConfig, "projectLevelConfig");

    String requiredApproval =
        projectLevelConfig
            .getConfig()
            .getString(SECTION_CODE_OWNERS, /* subsection= */ null, getConfigKey());
    if (requiredApproval != null) {
      try {
        RequiredApproval.parse(projectState, requiredApproval);
      } catch (IllegalArgumentException | IllegalStateException e) {
        return Optional.of(
            new CommitValidationMessage(
                String.format(
                    "Required approval '%s' that is configured in %s (parameter %s.%s) is invalid: %s",
                    requiredApproval,
                    fileName,
                    SECTION_CODE_OWNERS,
                    getConfigKey(),
                    e.getMessage()),
                ValidationMessage.Type.ERROR));
      }
    }
    return Optional.empty();
  }
}
