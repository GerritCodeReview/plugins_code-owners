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

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.server.cache.PerThreadCache;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

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

  private static final String KEY_MAX_CODE_OWNER_CONFIG_CACHE_SIZE = "maxCodeOwnerConfigCacheSize";

  private final CodeOwnersPluginConfigSnapshot.Factory codeOwnersPluginConfigSnapshotFactory;
  private final String pluginName;
  private final PluginConfigFactory pluginConfigFactory;
  private final GeneralConfig generalConfig;

  @Inject
  CodeOwnersPluginConfiguration(
      CodeOwnersPluginConfigSnapshot.Factory codeOwnersPluginConfigSnapshotFactory,
      @PluginName String pluginName,
      PluginConfigFactory pluginConfigFactory,
      GeneralConfig generalConfig) {
    this.codeOwnersPluginConfigSnapshotFactory = codeOwnersPluginConfigSnapshotFactory;
    this.pluginName = pluginName;
    this.pluginConfigFactory = pluginConfigFactory;
    this.generalConfig = generalConfig;
  }

  /**
   * Returns the code-owner plugin configuration for the given projects.
   *
   * <p>Callers must ensure that the project of the specified branch exists. If the project doesn't
   * exist the call fails with {@link IllegalStateException}.
   */
  public CodeOwnersPluginConfigSnapshot getProjectConfig(Project.NameKey projectName) {
    requireNonNull(projectName, "projectName");
    return PerThreadCache.getOrCompute(
        PerThreadCache.Key.create(CodeOwnersPluginConfigSnapshot.class, projectName),
        () -> codeOwnersPluginConfigSnapshotFactory.create(projectName));
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
          .getBoolean(KEY_ENABLE_EXPERIMENTAL_REST_ENDPOINTS, /* defaultValue= */ false);
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
   * Gets the maximum size for the {@link
   * com.google.gerrit.plugins.codeowners.backend.TransientCodeOwnerConfigCache}.
   *
   * @return the maximum cache size, {@link Optional#empty()} if the cache size is not limited
   */
  public Optional<Integer> getMaxCodeOwnerConfigCacheSize() {
    try {
      int maxCodeOwnerConfigCacheSize =
          pluginConfigFactory
              .getFromGerritConfig(pluginName)
              .getInt(KEY_MAX_CODE_OWNER_CONFIG_CACHE_SIZE, /* defaultValue= */ 0);
      return maxCodeOwnerConfigCacheSize > 0
          ? Optional.of(maxCodeOwnerConfigCacheSize)
          : Optional.empty();
    } catch (IllegalArgumentException e) {
      logger.atWarning().withCause(e).log(
          "Value '%s' in gerrit.config (parameter plugin.%s.%s) is invalid.",
          pluginConfigFactory
              .getFromGerritConfig(pluginName)
              .getString(KEY_MAX_CODE_OWNER_CONFIG_CACHE_SIZE),
          pluginName,
          KEY_MAX_CODE_OWNER_CONFIG_CACHE_SIZE);
      return Optional.empty();
    }
  }
}
