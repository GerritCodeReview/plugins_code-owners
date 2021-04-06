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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
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
  @VisibleForTesting public static final String KEY_DISABLED_BRANCH = "disabledBranch";

  private final String pluginName;
  private final PluginConfig pluginConfigFromGerritConfig;

  @Inject
  StatusConfig(@PluginName String pluginName, PluginConfigFactory pluginConfigFactory) {
    this.pluginName = pluginName;
    this.pluginConfigFromGerritConfig = pluginConfigFactory.getFromGerritConfig(pluginName);
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
      String fileName, Config projectLevelConfig) {
    requireNonNull(fileName, "fileName");
    requireNonNull(projectLevelConfig, "projectLevelConfig");

    List<CommitValidationMessage> validationMessages = new ArrayList<>();

    try {
      projectLevelConfig.getBoolean(SECTION_CODE_OWNERS, null, KEY_DISABLED, false);
    } catch (IllegalArgumentException e) {
      validationMessages.add(
          new CommitValidationMessage(
              String.format(
                  "Disabled value '%s' that is configured in %s.config (parameter %s.%s) is"
                      + " invalid.",
                  projectLevelConfig.getString(SECTION_CODE_OWNERS, null, KEY_DISABLED),
                  pluginName,
                  SECTION_CODE_OWNERS,
                  KEY_DISABLED),
              ValidationMessage.Type.ERROR));
    }

    for (String refPattern :
        projectLevelConfig.getStringList(SECTION_CODE_OWNERS, null, KEY_DISABLED_BRANCH)) {
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
   * Checks whether the code owners functionality is disabled for the given project.
   *
   * <p>The disabled configuration is read from the given plugin config. If it doesn't contain any
   * disabled configuration the disabled configuration is read from the plugin configuration in
   * {@code gerrit.config}.
   *
   * @param pluginConfig the plugin config from which the disabled configuration should be read
   * @param project the project to which the plugin config belongs
   * @return {@code true} if the code owners functionality is disabled for the project, otherwise
   *     {@code false}
   */
  boolean isDisabledForProject(Config pluginConfig, Project.NameKey project) {
    requireNonNull(pluginConfig, "pluginConfig");
    requireNonNull(project, "project");

    String disabledString = pluginConfig.getString(SECTION_CODE_OWNERS, null, KEY_DISABLED);
    if (disabledString != null) {
      // a value for KEY_DISABLED is set on project-level
      try {
        return pluginConfig.getBoolean(SECTION_CODE_OWNERS, null, KEY_DISABLED, false);
      } catch (IllegalArgumentException e) {
        // if the configuration is invalid we assume that the code owners functionality is not
        // disabled, this is safe as it's the more restrictive choice
        logger.atWarning().withCause(e).log(
            "Disabled value '%s' that is configured for project %s in %s.config (parameter"
                + " %s.%s) is invalid.",
            disabledString, project, pluginName, SECTION_CODE_OWNERS, KEY_DISABLED);
        return false;
      }
    }

    // there is no project-level configuration for KEY_DISABLED, check if it's set in gerrit.config
    try {
      return pluginConfigFromGerritConfig.getBoolean(KEY_DISABLED, false);
    } catch (IllegalArgumentException e) {
      // if the configuration is invalid we assume that the code owners functionality is not
      // disabled, this is safe as it's the more restrictive choice
      logger.atWarning().withCause(e).log(
          "Disabled value '%s' that is configured in gerrit.config (parameter plugin.%s.%s)"
              + " is invalid.",
          pluginConfigFromGerritConfig.getString(KEY_DISABLED), pluginName, KEY_DISABLED);
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

    // check if the branch is disabled in gerrit.config
    boolean isDisabled =
        isDisabledForBranch(
            pluginConfigFromGerritConfig.getStringList(KEY_DISABLED_BRANCH),
            branch.branch(),
            "Disabled branch '%s' that is configured for in gerrit.config (parameter plugin."
                + pluginName
                + "."
                + KEY_DISABLED_BRANCH
                + ") is invalid.");
    if (isDisabled) {
      return true;
    }

    // check if the branch is disabled on project level
    return isDisabledForBranch(
        pluginConfig.getStringList(SECTION_CODE_OWNERS, null, KEY_DISABLED_BRANCH),
        branch.branch(),
        "Disabled branch '%s' that is configured for project "
            + branch.project()
            + " in "
            + pluginName
            + ".config (parameter "
            + SECTION_CODE_OWNERS
            + "."
            + KEY_DISABLED_BRANCH
            + ") is invalid.");
  }

  private boolean isDisabledForBranch(
      String[] refPatternList, String branch, String warningMsgForInvalidRefPattern) {
    for (String refPattern : refPatternList) {
      if (Strings.isNullOrEmpty(refPattern)) {
        continue;
      }
      try {
        if (RefPatternMatcher.getMatcher(refPattern).match(branch, null)) {
          return true;
        }
      } catch (PatternSyntaxException e) {
        // if the configuration is invalid we ignore it, this is safe since it means we disable
        // the code owners functionality for less branches and not disabling the code owners
        // functionality is the more restrictive choice
        logger.atWarning().withCause(e).log(warningMsgForInvalidRefPattern, refPattern);
      }
    }
    return false;
  }
}
