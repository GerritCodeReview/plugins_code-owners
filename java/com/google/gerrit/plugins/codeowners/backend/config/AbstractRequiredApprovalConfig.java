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

import static com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.project.ProjectState;
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
   * Reads the required approvals for the specified project from the given plugin config with
   * fallback to {@code gerrit.config}.
   *
   * <p>Inherited required approvals are included into the returned list at the first position (see
   * {@link Config#getStringList(String, String, String)}).
   *
   * <p>The returned list contains duplicates if the exact same require approval is set for
   * different projects in the line of parent projects.
   *
   * @param projectState state of the project for which the required approvals should be read
   * @param pluginConfig the plugin config from which the required approvals should be read
   * @return the required approvals, an empty list if none was configured
   */
  ImmutableList<RequiredApproval> get(ProjectState projectState, Config pluginConfig) {
    requireNonNull(projectState, "projectState");
    requireNonNull(pluginConfig, "pluginConfig");

    ImmutableList.Builder<RequiredApproval> requiredApprovalList = ImmutableList.builder();
    for (String requiredApproval :
        pluginConfigFactory.getFromGerritConfig(pluginName).getStringList(getConfigKey())) {
      try {
        requiredApprovalList.add(RequiredApproval.parse(projectState, requiredApproval));
      } catch (IllegalStateException | IllegalArgumentException e) {
        throw new InvalidPluginConfigurationException(
            pluginName,
            String.format(
                "Required approval '%s' that is configured in gerrit.config"
                    + " (parameter plugin.%s.%s) is invalid: %s",
                requiredApproval, pluginName, getConfigKey(), e.getMessage()));
      }
    }
    for (String requiredApproval :
        pluginConfig.getStringList(SECTION_CODE_OWNERS, /* subsection= */ null, getConfigKey())) {
      try {
        requiredApprovalList.add(RequiredApproval.parse(projectState, requiredApproval));
      } catch (IllegalStateException | IllegalArgumentException e) {
        throw new InvalidPluginConfigurationException(
            pluginName,
            String.format(
                "Required approval '%s' that is configured in %s.config"
                    + " (parameter %s.%s) is invalid: %s",
                requiredApproval, pluginName, SECTION_CODE_OWNERS, getConfigKey(), e.getMessage()));
      }
    }
    return requiredApprovalList.build();
  }

  /**
   * Validates the required approval configuration in the given project level configuration.
   *
   * @param fileName the name of the config file
   * @param projectLevelConfig the project level plugin configuration
   * @return list of validation messages for validation errors, empty list if there are no
   *     validation errors
   */
  ImmutableList<CommitValidationMessage> validateProjectLevelConfig(
      ProjectState projectState, String fileName, Config projectLevelConfig) {
    requireNonNull(projectState, "projectState");
    requireNonNull(fileName, "fileName");
    requireNonNull(projectLevelConfig, "projectLevelConfig");

    String[] requiredApprovals =
        projectLevelConfig.getStringList(
            SECTION_CODE_OWNERS, /* subsection= */ null, getConfigKey());
    ImmutableList.Builder<CommitValidationMessage> validationMessages = ImmutableList.builder();
    for (String requiredApproval : requiredApprovals) {
      try {
        RequiredApproval.parse(projectState, requiredApproval);
      } catch (IllegalArgumentException | IllegalStateException e) {
        validationMessages.add(
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
    return validationMessages.build();
  }
}
