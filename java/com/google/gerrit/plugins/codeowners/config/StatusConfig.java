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
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.project.ProjectLevelConfig;
import com.google.gerrit.server.project.RefPatternMatcher;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;
import org.eclipse.jgit.lib.Config;

/**
 * Class to read the code owners status configuration from {@code code-owners.config} in {@code
 * refs/meta/config}.
 *
 * <p>The status configuration controls whether the code owners functionality is disabled for the
 * project or for any branch.
 *
 * <p>The code owners functionality can be disabled for a project (parameter {@code
 * codeOwners.disabled}) or for particular branches (parameter {@code codeOwners.disabledBranch}
 * which can be an exact ref, a ref pattern or a regular expression, can be specified multiple
 * times).
 *
 * <p>Projects that have no status configuration inherit the configuration from their parent
 * projects.
 */
@Singleton
@VisibleForTesting
public class StatusConfig {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting public static final String KEY_DISABLED = "disabled";
  @VisibleForTesting static final String KEY_DISABLED_BRANCH = "disabledBranch";

  private final String pluginName;

  @Inject
  StatusConfig(@PluginName String pluginName) {
    this.pluginName = pluginName;
  }

  /**
   * Validates the status configuration in the given project level configuration.
   *
   * @param fileName the name of the config file
   * @param projectLevelConfig the project level plugin configuration
   * @return list of validation messages for validation errors, empty list if there are no
   *     validation errors
   */
  ImmutableList<CommitValidationMessage> validateProjectLevelConfig(
      String fileName, ProjectLevelConfig projectLevelConfig) {
    requireNonNull(fileName, "fileName");
    requireNonNull(projectLevelConfig, "projectLevelConfig");

    List<CommitValidationMessage> validationMessages = new ArrayList<>();

    try {
      projectLevelConfig.get().getBoolean(SECTION_CODE_OWNERS, null, KEY_DISABLED, false);
    } catch (IllegalArgumentException e) {
      validationMessages.add(
          new CommitValidationMessage(
              String.format(
                  "Disabled value '%s' that is configured in %s.config (parameter %s.%s) is"
                      + " invalid.",
                  projectLevelConfig.get().getString(SECTION_CODE_OWNERS, null, KEY_DISABLED),
                  pluginName,
                  SECTION_CODE_OWNERS,
                  KEY_DISABLED),
              ValidationMessage.Type.ERROR));
    }

    for (String refPattern :
        projectLevelConfig.get().getStringList(SECTION_CODE_OWNERS, null, KEY_DISABLED_BRANCH)) {
      try {
        RefPatternMatcher.getMatcher(refPattern).match("refs/heads/master", null);
      } catch (PatternSyntaxException e) {
        validationMessages.add(
            new CommitValidationMessage(
                String.format(
                    "Disabled branch '%s' that is configured in %s.config (parameter %s.%s) is"
                        + " invalid: %s",
                    refPattern,
                    pluginName,
                    SECTION_CODE_OWNERS,
                    KEY_DISABLED_BRANCH,
                    e.getMessage()),
                ValidationMessage.Type.ERROR));
      }
    }

    return ImmutableList.copyOf(validationMessages);
  }

  /**
   * Checks whether the given plugin config disables the code owners functionality for the project.
   *
   * @param pluginConfig the plugin config from which the disabled configuration should be read
   * @param project the project to which the plugin config belongs
   * @return {@code true} if the code owners functionality is disabled for the project, otherwise
   *     {@code false}
   */
  boolean isDisabledForProject(Config pluginConfig, Project.NameKey project) {
    requireNonNull(pluginConfig, "pluginConfig");
    requireNonNull(project, "project");

    try {
      return pluginConfig.getBoolean(SECTION_CODE_OWNERS, null, KEY_DISABLED, false);
    } catch (IllegalArgumentException e) {
      // if the configuration is invalid we assume that the code owners functionality is not
      // disabled, this is safe as it's the more restrictive choice
      logger.atWarning().withCause(e).log(
          "Disabled value '%s' that is configured for project %s in %s.config (parameter"
              + " %s.%s) is invalid.",
          pluginConfig.getString(SECTION_CODE_OWNERS, null, KEY_DISABLED),
          project,
          pluginName,
          SECTION_CODE_OWNERS,
          KEY_DISABLED);
      return false;
    }
  }

  /**
   * Checks whether the given plugin config disables the code owners functionality for the given
   * branch.
   *
   * @param pluginConfig the plugin config from which the disabled configuration should be read
   * @param branch branch and project for which it should be checked whether the code owners
   *     functionality is disabled for it
   * @return {@code true} if the code owners functionality is disabled for the branch, otherwise
   *     {@code false}
   */
  boolean isDisabledForBranch(Config pluginConfig, BranchNameKey branch) {
    requireNonNull(pluginConfig, "pluginConfig");
    requireNonNull(branch, "branch");

    for (String refPattern :
        pluginConfig.getStringList(SECTION_CODE_OWNERS, null, KEY_DISABLED_BRANCH)) {
      try {
        if (RefPatternMatcher.getMatcher(refPattern).match(branch.branch(), null)) {
          return true;
        }
      } catch (PatternSyntaxException e) {
        // if the configuration is invalid we ignore it, this is safe since it means we disable the
        // code owners functionality for less branches and not disabling the code owners
        // functionality is the more restrictive choice
        logger.atWarning().withCause(e).log(
            "Disabled branch '%s' that is configured for project %s in %s.config (parameter"
                + " %s.%s) is invalid.",
            refPattern, branch.project(), pluginName, SECTION_CODE_OWNERS, KEY_DISABLED_BRANCH);
      }
    }

    return false;
  }
}
