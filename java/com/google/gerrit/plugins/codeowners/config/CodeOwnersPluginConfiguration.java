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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackendId;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.project.NoSuchProjectException;
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

  @VisibleForTesting public static final String KEY_BACKEND = "backend";
  @VisibleForTesting public static final String SECTION_CODE_OWNERS = "codeOwners";

  private final String pluginName;
  private final PluginConfigFactory pluginConfigFactory;
  private final DynamicMap<CodeOwnerBackend> codeOwnerBackends;

  /** The name of the configured code owners default backend. */
  private final String defaultBackendName;

  @Inject
  CodeOwnersPluginConfiguration(
      @PluginName String pluginName,
      PluginConfigFactory pluginConfigFactory,
      DynamicMap<CodeOwnerBackend> codeOwnerBackends) {
    this.pluginName = pluginName;
    this.pluginConfigFactory = pluginConfigFactory;
    this.codeOwnerBackends = codeOwnerBackends;

    this.defaultBackendName =
        pluginConfigFactory
            .getFromGerritConfig(pluginName)
            .getString(KEY_BACKEND, CodeOwnerBackendId.FIND_OWNERS.getBackendId());
  }

  /**
   * Returns the configured {@link CodeOwnerBackend}.
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
   * @return the {@link CodeOwnerBackend} that should be used
   */
  public CodeOwnerBackend getBackend(BranchNameKey branchNameKey)
      throws InvalidPluginConfigurationException {
    Config pluginConfig = getPluginConfig(branchNameKey.project());

    // check if a branch specific backend is configured
    Optional<CodeOwnerBackend> codeOwnerBackend = getBackendForBranch(pluginConfig, branchNameKey);
    if (codeOwnerBackend.isPresent()) {
      return codeOwnerBackend.get();
    }

    // check if a project specific backend is configured
    codeOwnerBackend = getBackendForProject(pluginConfig, branchNameKey.project());
    if (codeOwnerBackend.isPresent()) {
      return codeOwnerBackend.get();
    }

    // fall back to the default backend
    return getDefaultBackend();
  }

  private Optional<CodeOwnerBackend> getBackendForBranch(Config pluginConfig, BranchNameKey branch)
      throws InvalidPluginConfigurationException {
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
      Config pluginConfig, Project.NameKey project, String branch)
      throws InvalidPluginConfigurationException {
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

  private Optional<CodeOwnerBackend> getBackendForProject(
      Config pluginConfig, Project.NameKey project) throws InvalidPluginConfigurationException {
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

  @VisibleForTesting
  public CodeOwnerBackend getDefaultBackend() throws InvalidPluginConfigurationException {
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

  private Optional<CodeOwnerBackend> lookupBackend(String backendName) {
    // We must use "gerrit" as plugin name since DynamicMapProvider#get() hard-codes "gerrit" as
    // plugin name.
    return Optional.ofNullable(codeOwnerBackends.get("gerrit", backendName));
  }
}
