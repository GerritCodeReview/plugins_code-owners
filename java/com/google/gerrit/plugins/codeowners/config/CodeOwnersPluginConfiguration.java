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

import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;

/**
 * The configuration of the code-owners plugin.
 *
 * <p>The global configuration of the code-owners plugin is stored in the {@code gerrit.config} file
 * in the {@code plugin.code-owners} subsection.
 *
 * <p>In addition there is configuration on project level that is stored in {@code
 * code-owners.config} files that are stored in the {@code refs/meta/config} branches of the
 * projects.
 *
 * <p>Parameters that are not set for a project are inherited from the parent project.
 */
@Singleton
public class CodeOwnersPluginConfiguration {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting public static final String SECTION_CODE_OWNERS = "codeOwners";

  @VisibleForTesting
  static final String KEY_ENABLE_EXPERIMENTAL_REST_ENDPOINTS = "enableExperimentalRestEndpoints";

  private final String pluginName;
  private final PluginConfigFactory pluginConfigFactory;
  private final ProjectCache projectCache;
  private final GeneralConfig generalConfig;
  private final StatusConfig statusConfig;
  private final BackendConfig backendConfig;
  private final RequiredApprovalConfig requiredApprovalConfig;
  private final OverrideApprovalConfig overrideApprovalConfig;

  @Inject
  CodeOwnersPluginConfiguration(
      @PluginName String pluginName,
      PluginConfigFactory pluginConfigFactory,
      ProjectCache projectCache,
      GeneralConfig generalConfig,
      StatusConfig statusConfig,
      BackendConfig backendConfig,
      RequiredApprovalConfig requiredApprovalConfig,
      OverrideApprovalConfig overrideApprovalConfig) {
    this.pluginName = pluginName;
    this.pluginConfigFactory = pluginConfigFactory;
    this.projectCache = projectCache;
    this.generalConfig = generalConfig;
    this.statusConfig = statusConfig;
    this.backendConfig = backendConfig;
    this.requiredApprovalConfig = requiredApprovalConfig;
    this.overrideApprovalConfig = overrideApprovalConfig;
  }

  /**
   * Gets the file extension that is configured for the given project.
   *
   * @param project the project for which the configured file extension should be returned
   * @return the file extension that is configured for the given project
   */
  public Optional<String> getFileExtension(Project.NameKey project) {
    requireNonNull(project, "project");
    return generalConfig.getFileExtension(getPluginConfig(project));
  }

  /**
   * Returns the email domains that are allowed to be used for code owners.
   *
   * @return the email domains that are allowed to be used for code owners, an empty set if all
   *     email domains are allowed (if {@code plugin.code-owners.allowedEmailDomain} is not set or
   *     set to an empty value)
   */
  public ImmutableSet<String> getAllowedEmailDomains() {
    return generalConfig.getAllowedEmailDomains();
  }

  /**
   * Whether the code owners functionality is disabled for the given branch.
   *
   * <p>Callers must ensure that the project of the specified branch exists. If the project doesn't
   * exist the call fails with {@link IllegalStateException}.
   *
   * <p>The configuration is evaluated in the following order:
   *
   * <ul>
   *   <li>disabled configuration for the branch (with inheritance)
   *   <li>disabled configuration for the project (with inheritance)
   *   <li>hard-coded default (not disabled)
   * </ul>
   *
   * <p>The first disabled configuration that exists counts and the evaluation is stopped.
   *
   * @param branchNameKey the branch and project for which it should be checked whether the code
   *     owners functionality is disabled
   * @return {@code true} if the code owners functionality is disabled for the given branch,
   *     otherwise {@code false}
   */
  public boolean isDisabled(BranchNameKey branchNameKey) {
    requireNonNull(branchNameKey, "branchNameKey");

    Config pluginConfig = getPluginConfig(branchNameKey.project());

    boolean isDisabled = statusConfig.isDisabledForBranch(pluginConfig, branchNameKey);
    if (isDisabled) {
      return true;
    }

    return isDisabled(branchNameKey.project());
  }

  /**
   * Whether the code owners functionality is disabled for the given project.
   *
   * <p>Callers must ensure that the project of the specified branch exists. If the project doesn't
   * exist the call fails with {@link IllegalStateException}.
   *
   * <p>The configuration is evaluated in the following order:
   *
   * <ul>
   *   <li>disabled configuration for the project (with inheritance)
   *   <li>hard-coded default (not disabled)
   * </ul>
   *
   * <p>The first disabled configuration that exists counts and the evaluation is stopped.
   *
   * @param project the project for which it should be checked whether the code owners functionality
   *     is disabled
   * @return {@code true} if the code owners functionality is disabled for the given project,
   *     otherwise {@code false}
   */
  public boolean isDisabled(Project.NameKey project) {
    requireNonNull(project, "project");

    Config pluginConfig = getPluginConfig(project);
    return statusConfig.isDisabledForProject(pluginConfig, project);
  }

  /**
   * Returns the configured {@link CodeOwnerBackend} for the given branch.
   *
   * <p>Callers must ensure that the project of the specified branch exists. If the project doesn't
   * exist the call fails with {@link IllegalStateException}.
   *
   * <p>The code owner backend configuration is evaluated in the following order:
   *
   * <ul>
   *   <li>backend configuration for branch (with inheritance, first by full branch name, then by
   *       short branch name)
   *   <li>backend configuration for project (with inheritance)
   *   <li>default backend (first globally configured backend, then hard-coded default backend)
   * </ul>
   *
   * <p>The first code owner backend configuration that exists counts and the evaluation is stopped.
   *
   * @param branchNameKey project and branch for which the configured code owner backend should be
   *     returned
   * @return the {@link CodeOwnerBackend} that should be used for the branch
   */
  public CodeOwnerBackend getBackend(BranchNameKey branchNameKey) {
    Config pluginConfig = getPluginConfig(branchNameKey.project());

    // check if a branch specific backend is configured
    Optional<CodeOwnerBackend> codeOwnerBackend =
        backendConfig.getBackendForBranch(pluginConfig, branchNameKey);
    if (codeOwnerBackend.isPresent()) {
      return codeOwnerBackend.get();
    }

    return getBackend(branchNameKey.project());
  }

  /**
   * Returns the configured {@link CodeOwnerBackend} for the given project.
   *
   * <p>Callers must ensure that the project exists. If the project doesn't exist the call fails
   * with {@link IllegalStateException}.
   *
   * <p>The code owner backend configuration is evaluated in the following order:
   *
   * <ul>
   *   <li>backend configuration for project (with inheritance)
   *   <li>default backend (first globally configured backend, then hard-coded default backend)
   * </ul>
   *
   * <p>The first code owner backend configuration that exists counts and the evaluation is stopped.
   *
   * @param project project for which the configured code owner backend should be returned
   * @return the {@link CodeOwnerBackend} that should be used for the project
   */
  public CodeOwnerBackend getBackend(Project.NameKey project) {
    Config pluginConfig = getPluginConfig(project);

    // check if a project specific backend is configured
    Optional<CodeOwnerBackend> codeOwnerBackend =
        backendConfig.getBackendForProject(pluginConfig, project);
    if (codeOwnerBackend.isPresent()) {
      return codeOwnerBackend.get();
    }

    // fall back to the default backend
    return backendConfig.getDefaultBackend();
  }

  /**
   * Returns the approval that is required from code owners to approve the files in a change of the
   * given project.
   *
   * <p>Defines which approval counts as code owner approval.
   *
   * <p>Callers must ensure that the project of the specified branch exists. If the project doesn't
   * exist the call fails with {@link IllegalStateException}.
   *
   * <p>The code owner required approval configuration is evaluated in the following order:
   *
   * <ul>
   *   <li>required approval configuration for project (with inheritance)
   *   <li>globally configured required approval
   *   <li>hard-coded default required approval
   * </ul>
   *
   * <p>The first required code owner approval that exists counts and the evaluation is stopped.
   *
   * @param project project for which the required approval should be returned
   * @return the required code owner approval that should be used for the given project
   */
  public RequiredApproval getRequiredApproval(Project.NameKey project) {
    Optional<RequiredApproval> configuredRequiredApprovalConfig =
        getConfiguredRequiredApproval(requiredApprovalConfig, project);
    if (configuredRequiredApprovalConfig.isPresent()) {
      return configuredRequiredApprovalConfig.get();
    }

    // fall back to hard-coded default required approval
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    return requiredApprovalConfig.createDefault(projectState);
  }

  /**
   * Returns the approval that is required to override the code owners submit check for a change of
   * the given project.
   *
   * <p>Callers must ensure that the project of the specified branch exists. If the project doesn't
   * exist the call fails with {@link IllegalStateException}.
   *
   * <p>The override approval configuration is evaluated in the following order:
   *
   * <ul>
   *   <li>override approval configuration for project (with inheritance)
   *   <li>globally configured override approval
   * </ul>
   *
   * <p>The first override approval that exists counts and the evaluation is stopped.
   *
   * @param project project for which the override approval should be returned
   * @return the override approval that should be used for the given project, {@link
   *     Optional#empty()} if no override approval is configured, in this case the override
   *     functionality is disabled
   */
  public Optional<RequiredApproval> getOverrideApproval(Project.NameKey project) {
    try {
      Optional<RequiredApproval> configuredOverrideApprovalConfig =
          getConfiguredRequiredApproval(overrideApprovalConfig, project);
      if (configuredOverrideApprovalConfig.isPresent()) {
        return configuredOverrideApprovalConfig;
      }
    } catch (InvalidPluginConfigurationException e) {
      logger.atWarning().withCause(e).log(
          String.format(
              "Ignoring invalid override approval configuration for project %s."
                  + " Overrides are disabled.",
              project.get()));
    }

    return Optional.empty();
  }

  /**
   * Gets the required approval that is configured for the given project.
   *
   * @param requiredApprovalConfig the config from which the required approval should be read
   * @param project the project for which the configured required approval should be returned
   * @return the required approval that is configured for the given project, {@link
   *     Optional#empty()} if no required approval is configured
   */
  private Optional<RequiredApproval> getConfiguredRequiredApproval(
      AbstractRequiredApprovalConfig requiredApprovalConfig, Project.NameKey project) {
    Config pluginConfig = getPluginConfig(project);

    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));

    // check if a project specific required approval is configured
    Optional<RequiredApproval> requiredApproval =
        requiredApprovalConfig.getForProject(projectState, pluginConfig);
    if (requiredApproval.isPresent()) {
      return requiredApproval;
    }

    // check if a required approval is globally configured
    requiredApproval = requiredApprovalConfig.getFromGlobalPluginConfig(projectState);
    if (requiredApproval.isPresent()) {
      return requiredApproval;
    }

    return Optional.empty();
  }

  /**
   * Checks whether experimental REST endpoints are enabled.
   *
   * @throws MethodNotAllowedException thrown if experimental REST endpoints are disabled
   */
  public void checkExperimentalRestEndpointsEnabled() throws MethodNotAllowedException {
    if (!areExperimentalRestEndpointsEnabled()) {
      throw new MethodNotAllowedException("experimental code owners REST endpoints are disabled");
    }
  }

  /** Whether experimental REST endpoints are enabled. */
  public boolean areExperimentalRestEndpointsEnabled() {
    try {
      return pluginConfigFactory
          .getFromGerritConfig(pluginName)
          .getBoolean(KEY_ENABLE_EXPERIMENTAL_REST_ENDPOINTS, false);
    } catch (IllegalArgumentException e) {
      logger.atWarning().withCause(e).log(
          "Value '%s' in gerrit.config (parameter plugin.%s.%s) is invalid.",
          pluginConfigFactory
              .getFromGerritConfig(pluginName)
              .getString(KEY_ENABLE_EXPERIMENTAL_REST_ENDPOINTS),
          pluginName,
          KEY_ENABLE_EXPERIMENTAL_REST_ENDPOINTS);
      return false;
    }
  }

  /**
   * Reads and returns the config from the {@code code-owners.config} file in {@code
   * refs/meta/config} branch of the given project.
   *
   * @param project the project for which the code owners configurations should be returned
   * @return the code owners configurations for the given project
   */
  private Config getPluginConfig(Project.NameKey project) {
    try {
      return pluginConfigFactory.getProjectPluginConfigWithInheritance(project, pluginName);
    } catch (NoSuchProjectException e) {
      throw new IllegalStateException(
          String.format(
              "cannot get %s plugin config for non-existing project %s", pluginName, project),
          e);
    }
  }
}
