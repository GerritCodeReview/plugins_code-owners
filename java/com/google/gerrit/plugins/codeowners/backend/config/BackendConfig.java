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
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackendId;
import com.google.gerrit.plugins.codeowners.backend.PathExpressions;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;

/**
 * Class to read the {@link CodeOwnerBackend} configuration from {@code gerrit.config} and from
 * {@code code-owners.config} in {@code refs/meta/config}.
 *
 * <p>The default {@link CodeOwnerBackend} is configured in {@code gerrit.config} by the {@code
 * plugin.code-owners.backend} parameter.
 *
 * <p>On project-level the {@link CodeOwnerBackend} is configured in {@code code-owners.config} in
 * {@code refs/meta/config}. The {@code codeOwners.backend} parameter configures the {@link
 * CodeOwnerBackend} for the project and the {@code codeOwners.<branch>.backend} parameter for a
 * branch.
 *
 * <p>Projects that have no {@link CodeOwnerBackend} configuration inherit the configuration from
 * their parent projects.
 */
@Singleton
@VisibleForTesting
public class BackendConfig {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting public static final String KEY_BACKEND = "backend";
  @VisibleForTesting public static final String KEY_PATH_EXPRESSIONS = "pathExpressions";

  private final String pluginName;
  private final DynamicMap<CodeOwnerBackend> codeOwnerBackends;

  /** The name of the configured code owners default backend. */
  private final String defaultBackendName;

  /** The name of the configured default path expressions. */
  private final Optional<String> defaultPathExpressionsName;

  @Inject
  BackendConfig(
      @PluginName String pluginName,
      PluginConfigFactory pluginConfigFactory,
      DynamicMap<CodeOwnerBackend> codeOwnerBackends) {
    this.pluginName = pluginName;
    this.codeOwnerBackends = codeOwnerBackends;

    this.defaultBackendName =
        pluginConfigFactory
            .getFromGerritConfig(pluginName)
            .getString(KEY_BACKEND, CodeOwnerBackendId.FIND_OWNERS.getBackendId());

    this.defaultPathExpressionsName =
        Optional.ofNullable(
            pluginConfigFactory
                .getFromGerritConfig(pluginName)
                .getString(KEY_PATH_EXPRESSIONS, /* defaultValue= */ null));
  }

  /**
   * Validates the backend configuration in the given project level configuration.
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

    String backendName = projectLevelConfig.getString(SECTION_CODE_OWNERS, null, KEY_BACKEND);
    if (backendName != null) {
      if (!lookupBackend(backendName).isPresent()) {
        validationMessages.add(
            new CommitValidationMessage(
                String.format(
                    "Code owner backend '%s' that is configured in %s (parameter %s.%s) not found.",
                    backendName, fileName, SECTION_CODE_OWNERS, KEY_BACKEND),
                ValidationMessage.Type.ERROR));
      }
    }

    for (String subsection : projectLevelConfig.getSubsections(SECTION_CODE_OWNERS)) {
      backendName = projectLevelConfig.getString(SECTION_CODE_OWNERS, subsection, KEY_BACKEND);
      if (backendName != null) {
        if (!lookupBackend(backendName).isPresent()) {
          validationMessages.add(
              new CommitValidationMessage(
                  String.format(
                      "Code owner backend '%s' that is configured in %s (parameter %s.%s.%s) not found.",
                      backendName, fileName, SECTION_CODE_OWNERS, subsection, KEY_BACKEND),
                  ValidationMessage.Type.ERROR));
        }
      }
    }

    return ImmutableList.copyOf(validationMessages);
  }

  /**
   * Gets the code owner backend that is configured for the given branch.
   *
   * <p>The code owner backend configuration is evaluated in the following order:
   *
   * <ul>
   *   <li>backend configuration for branch by full name (with inheritance)
   *   <li>backend configuration for branch by short name (with inheritance)
   * </ul>
   *
   * @param pluginConfig the plugin config from which the code owner backend should be read.
   * @param branch the project and branch for which the configured code owner backend should be read
   * @return the code owner backend that is configured for the given branch, {@link
   *     Optional#empty()} if there is no branch-specific code owner backend configuration
   */
  Optional<CodeOwnerBackend> getBackendForBranch(Config pluginConfig, BranchNameKey branch) {
    requireNonNull(pluginConfig, "pluginConfig");
    requireNonNull(branch, "branch");

    // check for branch specific backend by full branch name
    Optional<CodeOwnerBackend> backend =
        getBackendForBranch(pluginConfig, branch.project(), branch.branch());
    if (!backend.isPresent()) {
      // check for branch specific backend by short branch name
      backend = getBackendForBranch(pluginConfig, branch.project(), branch.shortName());
    }
    return backend;
  }

  private Optional<CodeOwnerBackend> getBackendForBranch(
      Config pluginConfig, Project.NameKey project, String branch) {
    String backendName = pluginConfig.getString(SECTION_CODE_OWNERS, branch, KEY_BACKEND);
    if (backendName == null) {
      return Optional.empty();
    }
    return Optional.of(
        lookupBackend(backendName)
            .orElseThrow(
                () -> {
                  InvalidPluginConfigurationException e =
                      new InvalidPluginConfigurationException(
                          pluginName,
                          String.format(
                              "Code owner backend '%s' that is configured for project %s in"
                                  + " %s.config (parameter %s.%s.%s) not found.",
                              backendName,
                              project,
                              pluginName,
                              SECTION_CODE_OWNERS,
                              branch,
                              KEY_BACKEND));
                  logger.atSevere().log(e.getMessage());
                  return e;
                }));
  }

  /**
   * Gets the code owner backend that is configured for the given project.
   *
   * @param pluginConfig the plugin config from which the code owner backend should be read.
   * @param project the project for which the configured code owner backend should be read
   * @return the code owner backend that is configured for the given project, {@link
   *     Optional#empty()} if there is no project-specific code owner backend configuration
   */
  Optional<CodeOwnerBackend> getBackendForProject(Config pluginConfig, Project.NameKey project) {
    requireNonNull(pluginConfig, "pluginConfig");
    requireNonNull(project, "project");

    String backendName = pluginConfig.getString(SECTION_CODE_OWNERS, null, KEY_BACKEND);
    if (backendName == null) {
      return Optional.empty();
    }
    return Optional.of(
        lookupBackend(backendName)
            .orElseThrow(
                () -> {
                  InvalidPluginConfigurationException e =
                      new InvalidPluginConfigurationException(
                          pluginName,
                          String.format(
                              "Code owner backend '%s' that is configured for project %s in"
                                  + " %s.config (parameter %s.%s) not found.",
                              backendName, project, pluginName, SECTION_CODE_OWNERS, KEY_BACKEND));
                  logger.atSevere().log(e.getMessage());
                  return e;
                }));
  }

  /** Gets the default code owner backend. */
  public CodeOwnerBackend getDefaultBackend() {
    return lookupBackend(defaultBackendName)
        .orElseThrow(
            () -> {
              InvalidPluginConfigurationException e =
                  new InvalidPluginConfigurationException(
                      pluginName,
                      String.format(
                          "Code owner backend '%s' that is configured in gerrit.config"
                              + " (parameter plugin.%s.%s) not found.",
                          defaultBackendName, pluginName, KEY_BACKEND));
              logger.atSevere().log(e.getMessage());
              return e;
            });
  }

  private Optional<CodeOwnerBackend> lookupBackend(String backendName) {
    // We must use "gerrit" as plugin name since DynamicMapProvider#get() hard-codes "gerrit" as
    // plugin name.
    return Optional.ofNullable(codeOwnerBackends.get("gerrit", backendName));
  }

  /**
   * Gets the path expressions that are configured for the given branch.
   *
   * <p>The path expressions configuration is evaluated in the following order:
   *
   * <ul>
   *   <li>path expressions for branch by full name (with inheritance)
   *   <li>path expressions for branch by short name (with inheritance)
   * </ul>
   *
   * @param pluginConfig the plugin config from which the path expressions should be read.
   * @param branch the project and branch for which the configured path expressions should be read
   * @return the path expressions that are configured for the given branch, {@link Optional#empty()}
   *     if there is no branch-specific path expressions configuration
   */
  Optional<PathExpressions> getPathExpressionsForBranch(Config pluginConfig, BranchNameKey branch) {
    requireNonNull(pluginConfig, "pluginConfig");
    requireNonNull(branch, "branch");

    // check for branch specific path expressions by full branch name
    Optional<PathExpressions> pathExpressions =
        getPathExpressionsForBranch(pluginConfig, branch.project(), branch.branch());
    if (!pathExpressions.isPresent()) {
      // check for branch specific path expressions by short branch name
      pathExpressions =
          getPathExpressionsForBranch(pluginConfig, branch.project(), branch.shortName());
    }
    return pathExpressions;
  }

  private Optional<PathExpressions> getPathExpressionsForBranch(
      Config pluginConfig, Project.NameKey project, String branch) {
    String pathExpressionsName =
        pluginConfig.getString(SECTION_CODE_OWNERS, branch, KEY_PATH_EXPRESSIONS);
    if (pathExpressionsName == null) {
      return Optional.empty();
    }
    return Optional.of(
        PathExpressions.tryParse(pathExpressionsName)
            .orElseThrow(
                () -> {
                  InvalidPluginConfigurationException e =
                      new InvalidPluginConfigurationException(
                          pluginName,
                          String.format(
                              "Path expressions '%s' that are configured for project %s in"
                                  + " %s.config (parameter %s.%s.%s) not found.",
                              pathExpressionsName,
                              project,
                              pluginName,
                              SECTION_CODE_OWNERS,
                              branch,
                              KEY_PATH_EXPRESSIONS));
                  logger.atSevere().log(e.getMessage());
                  return e;
                }));
  }

  /**
   * Gets the path expressions that are configured for the given project.
   *
   * @param pluginConfig the plugin config from which the path expressions should be read.
   * @param project the project for which the configured path expressions should be read
   * @return the path expressions that are configured for the given project, {@link
   *     Optional#empty()} if there is no project-specific path expression configuration
   */
  Optional<PathExpressions> getPathExpressionsForProject(
      Config pluginConfig, Project.NameKey project) {
    requireNonNull(pluginConfig, "pluginConfig");
    requireNonNull(project, "project");

    String pathExpressionsName =
        pluginConfig.getString(SECTION_CODE_OWNERS, null, KEY_PATH_EXPRESSIONS);
    if (pathExpressionsName == null) {
      return Optional.empty();
    }
    return Optional.of(
        PathExpressions.tryParse(pathExpressionsName)
            .orElseThrow(
                () -> {
                  InvalidPluginConfigurationException e =
                      new InvalidPluginConfigurationException(
                          pluginName,
                          String.format(
                              "Path expressions '%s' that are configured for project %s in"
                                  + " %s.config (parameter %s.%s) not found.",
                              pathExpressionsName,
                              project,
                              pluginName,
                              SECTION_CODE_OWNERS,
                              KEY_PATH_EXPRESSIONS));
                  logger.atSevere().log(e.getMessage());
                  return e;
                }));
  }

  /** Gets the default path expressions. */
  public Optional<PathExpressions> getDefaultPathExpressions() {
    if (!defaultPathExpressionsName.isPresent()) {
      return Optional.empty();
    }
    return Optional.of(
        PathExpressions.tryParse(defaultPathExpressionsName.get())
            .orElseThrow(
                () -> {
                  InvalidPluginConfigurationException e =
                      new InvalidPluginConfigurationException(
                          pluginName,
                          String.format(
                              "Path expressions '%s' that are configured in gerrit.config"
                                  + " (parameter plugin.%s.%s) not found.",
                              defaultPathExpressionsName.get(), pluginName, KEY_PATH_EXPRESSIONS));
                  logger.atSevere().log(e.getMessage());
                  return e;
                }));
  }
}
