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
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.project.ProjectLevelConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;

/**
 * Class to read the required approval configuration from {@code gerrit.config} and from {@code
 * code-owners.config} in {@code refs/meta/config}.
 *
 * <p>The default required approval is configured in {@code gerrit.config} by the {@code
 * plugin.code-owners.requiredApproval} parameter.
 *
 * <p>On project-level the required approval is configured in {@code code-owners.config} in {@code
 * refs/meta/config} by the {@code codeOwners.requiredApproval} parameter.
 *
 * <p>Projects that have no required approval configuration inherit the configuration from their
 * parent projects.
 */
@Singleton
public class RequiredApprovalConfig {
  @VisibleForTesting public static final String KEY_REQUIRED_APPROVAL = "requiredApproval";

  /** By default a {@code Code-Review+1} vote from a code owner approves the file. */
  @VisibleForTesting public static final String DEFAULT_LABEL = "Code-Review";

  @VisibleForTesting public static final short DEFAULT_VALUE = 1;

  private final String pluginName;
  private final PluginConfigFactory pluginConfigFactory;

  @Inject
  RequiredApprovalConfig(@PluginName String pluginName, PluginConfigFactory pluginConfigFactory) {
    this.pluginName = pluginName;
    this.pluginConfigFactory = pluginConfigFactory;
  }

  Optional<RequiredApproval> getForProject(ProjectState projectState, Config pluginConfig) {
    requireNonNull(projectState, "projectState");
    requireNonNull(pluginConfig, "pluginConfig");
    String requiredApproval =
        pluginConfig.getString(SECTION_CODE_OWNERS, null, KEY_REQUIRED_APPROVAL);
    if (requiredApproval == null) {
      return Optional.empty();
    }

    try {
      return Optional.of(RequiredApproval.parse(projectState, requiredApproval));
    } catch (IllegalStateException | IllegalArgumentException e) {
      throw new InvalidPluginConfigurationException(
          pluginName,
          String.format(
              "Required approval '%s' that is configured in %s.config"
                  + " (parameter %s.%s) is invalid: %s",
              requiredApproval,
              pluginName,
              SECTION_CODE_OWNERS,
              KEY_REQUIRED_APPROVAL,
              e.getMessage()));
    }
  }

  Optional<RequiredApproval> getFromGlobalPluginConfig(ProjectState projectState) {
    requireNonNull(projectState, "projectState");

    String requiredApproval =
        pluginConfigFactory.getFromGerritConfig(pluginName).getString(KEY_REQUIRED_APPROVAL);
    if (requiredApproval == null) {
      return Optional.empty();
    }

    try {
      return Optional.of(RequiredApproval.parse(projectState, requiredApproval));
    } catch (IllegalStateException | IllegalArgumentException e) {
      throw new InvalidPluginConfigurationException(
          pluginName,
          String.format(
              "Required approval '%s' that is configured in gerrit.config"
                  + " (parameter plugin.%s.%s) is invalid: %s",
              requiredApproval, pluginName, KEY_REQUIRED_APPROVAL, e.getMessage()));
    }
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
      ProjectState projectState, String fileName, ProjectLevelConfig projectLevelConfig) {
    requireNonNull(projectState, "projectState");
    requireNonNull(fileName, "fileName");
    requireNonNull(projectLevelConfig, "projectLevelConfig");

    String requiredApproval =
        projectLevelConfig.get().getString(SECTION_CODE_OWNERS, null, KEY_REQUIRED_APPROVAL);
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
                    KEY_REQUIRED_APPROVAL,
                    e.getMessage()),
                ValidationMessage.Type.ERROR));
      }
    }
    return Optional.empty();
  }

  RequiredApproval createDefault(ProjectState projectState) throws IllegalStateException {
    return RequiredApproval.createDefault(projectState, DEFAULT_LABEL, DEFAULT_VALUE);
  }
}
