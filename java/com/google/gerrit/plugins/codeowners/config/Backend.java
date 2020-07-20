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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackendId;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;

@Singleton
@VisibleForTesting
public class Backend {
  @VisibleForTesting public static final String KEY_BACKEND = "backend";

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String pluginName;
  private final DynamicMap<CodeOwnerBackend> codeOwnerBackends;

  /** The name of the configured code owners default backend. */
  private final String defaultBackendName;

  @Inject
  Backend(
      @PluginName String pluginName,
      PluginConfigFactory pluginConfigFactory,
      DynamicMap<CodeOwnerBackend> codeOwnerBackends) {
    this.pluginName = pluginName;
    this.codeOwnerBackends = codeOwnerBackends;

    this.defaultBackendName =
        pluginConfigFactory
            .getFromGerritConfig(pluginName)
            .getString(KEY_BACKEND, CodeOwnerBackendId.FIND_OWNERS.getBackendId());
  }

  Optional<CodeOwnerBackend> getForBranch(Config pluginConfig, BranchNameKey branch)
      throws InvalidPluginConfigurationException {
    requireNonNull(pluginConfig, "pluginConfig");
    requireNonNull(branch, "branch");

    // check for branch specific backend by full branch name
    Optional<CodeOwnerBackend> backend =
        getForBranch(pluginConfig, branch.project(), branch.branch());
    if (!backend.isPresent()) {
      // check for branch specific backend by short branch name
      backend = getForBranch(pluginConfig, branch.project(), branch.shortName());
    }
    return backend;
  }

  private Optional<CodeOwnerBackend> getForBranch(
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

  Optional<CodeOwnerBackend> getForProject(Config pluginConfig, Project.NameKey project)
      throws InvalidPluginConfigurationException {
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

  @VisibleForTesting
  public CodeOwnerBackend getDefault() throws InvalidPluginConfigurationException {
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
}
