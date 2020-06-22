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

package com.google.gerrit.plugins.codeowners;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnersBackendId;
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
  @VisibleForTesting public static final String KEY_BACKEND = "backend";
  @VisibleForTesting public static final String SECTION_CODE_OWNERS = "codeOwners";

  private final String pluginName;
  private final PluginConfigFactory pluginConfigFactory;
  private final DynamicMap<CodeOwnersBackend> codeOwnersBackends;

  /** The name of the configured code owners default backend. */
  private final String defaultBackendName;

  @Inject
  CodeOwnersPluginConfiguration(
      @PluginName String pluginName,
      PluginConfigFactory pluginConfigFactory,
      DynamicMap<CodeOwnersBackend> codeOwnersBackends) {
    this.pluginName = pluginName;
    this.pluginConfigFactory = pluginConfigFactory;
    this.codeOwnersBackends = codeOwnersBackends;

    this.defaultBackendName =
        pluginConfigFactory
            .getFromGerritConfig(pluginName)
            .getString(KEY_BACKEND, CodeOwnersBackendId.FIND_OWNERS.getBackendId());
  }

  /**
   * Returns the configured {@link CodeOwnersBackend}.
   *
   * <p>Callers must ensure that the project of the specified branch exists. If the project doesn't
   * exist the call fails with {@link IllegalStateException}.
   *
   * <p>The code owners backend configuration is evaluated in the following order:
   *
   * <ul>
   *   <li>backend configuration for branch (with inheritance, first by full branch name, then by
   *       short branch name)
   *   <li>backend configuration for project (with inheritance)
   *   <li>default backend (first globally configured backend, then hard-coded default backend)
   * </ul>
   *
   * <p>The first code owners backend configuration that exists counts and the evaluation is
   * stopped.
   *
   * @param branchNameKey project and branch for which the configured code owners backend should be
   *     returned
   * @return the {@link CodeOwnersBackend} that should be used
   */
  public CodeOwnersBackend getBackend(BranchNameKey branchNameKey) {
    Config pluginConfig = getPluginConfig(branchNameKey.project());

    // check if a branch specific backend is configured
    Optional<CodeOwnersBackend> codeOwnersBackend =
        getBackendForBranch(pluginConfig, branchNameKey);
    if (codeOwnersBackend.isPresent()) {
      return codeOwnersBackend.get();
    }

    // check if a project specific backend is configured
    codeOwnersBackend = getBackendForProject(pluginConfig);
    if (codeOwnersBackend.isPresent()) {
      return codeOwnersBackend.get();
    }

    // fall back to the default backend
    return getDefaultBackend();
  }

  private Optional<CodeOwnersBackend> getBackendForBranch(
      Config pluginConfig, BranchNameKey branch) {
    // check for branch specific backend by full branch name
    Optional<CodeOwnersBackend> backend = getBackendForBranch(pluginConfig, branch.branch());
    if (!backend.isPresent()) {
      // check for branch specific backend by short branch name
      backend = getBackendForBranch(pluginConfig, branch.shortName());
    }
    return backend;
  }

  private Optional<CodeOwnersBackend> getBackendForBranch(Config pluginConfig, String branch) {
    String backendName = pluginConfig.getString(SECTION_CODE_OWNERS, branch, KEY_BACKEND);
    if (backendName == null) {
      return Optional.empty();
    }
    return Optional.of(
        lookupBackend(backendName)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format(
                            "Code owner backend '%s' that is configured in %s.config"
                                + " (parameter %s.%s.%s) not found",
                            backendName, pluginName, SECTION_CODE_OWNERS, branch, KEY_BACKEND))));
  }

  private Optional<CodeOwnersBackend> getBackendForProject(Config pluginConfig) {
    String backendName = pluginConfig.getString(SECTION_CODE_OWNERS, null, KEY_BACKEND);
    if (backendName == null) {
      return Optional.empty();
    }
    return Optional.of(
        lookupBackend(backendName)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format(
                            "Code owner backend '%s' that is configured in %s.config"
                                + " (parameter %s.%s) not found",
                            backendName, pluginName, SECTION_CODE_OWNERS, KEY_BACKEND))));
  }

  private CodeOwnersBackend getDefaultBackend() {
    return lookupBackend(defaultBackendName)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Code owner backend '%s' that is configured in gerrit.config"
                            + " (parameter plugin.%s.%s) not found",
                        defaultBackendName, pluginName, KEY_BACKEND)));
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

  private Optional<CodeOwnersBackend> lookupBackend(String backendName) {
    // We must use "gerrit" as plugin name since DynamicMapProvider#get() hard-codes "gerrit" as
    // plugin name.
    return Optional.ofNullable(codeOwnersBackends.get("gerrit", backendName));
  }
}
