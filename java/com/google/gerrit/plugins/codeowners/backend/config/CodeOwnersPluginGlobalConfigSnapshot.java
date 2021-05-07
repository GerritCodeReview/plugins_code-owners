// Copyright (C) 2021 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import java.util.Optional;

/** Snapshot of the global code-owners plugin configuration. */
public class CodeOwnersPluginGlobalConfigSnapshot {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting
  static final String KEY_ENABLE_EXPERIMENTAL_REST_ENDPOINTS = "enableExperimentalRestEndpoints";

  @VisibleForTesting static final int DEFAULT_MAX_CODE_OWNER_CONFIG_CACHE_SIZE = 10000;

  private static final String KEY_MAX_CODE_OWNER_CONFIG_CACHE_SIZE = "maxCodeOwnerConfigCacheSize";

  public interface Factory {
    CodeOwnersPluginGlobalConfigSnapshot create();
  }

  private final String pluginName;
  private final PluginConfigFactory pluginConfigFactory;
  private final GeneralConfig generalConfig;

  @Nullable private ImmutableSet<String> allowedEmailDomains;
  @Nullable private Boolean enabledExperimentalRestEndpoints;
  @Nullable private Optional<Integer> maxCodeOwnerConfigCacheSize;

  @Inject
  CodeOwnersPluginGlobalConfigSnapshot(
      @PluginName String pluginName,
      PluginConfigFactory pluginConfigFactory,
      GeneralConfig generalConfig) {
    this.pluginName = pluginName;
    this.pluginConfigFactory = pluginConfigFactory;
    this.generalConfig = generalConfig;
  }

  /**
   * Returns the email domains that are allowed to be used for code owners.
   *
   * @return the email domains that are allowed to be used for code owners, an empty set if all
   *     email domains are allowed (if {@code plugin.code-owners.allowedEmailDomain} is not set or
   *     set to an empty value)
   */
  public ImmutableSet<String> getAllowedEmailDomains() {
    if (allowedEmailDomains == null) {
      allowedEmailDomains = generalConfig.getAllowedEmailDomains();
    }
    return allowedEmailDomains;
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
    if (enabledExperimentalRestEndpoints == null) {
      enabledExperimentalRestEndpoints = readEnabledExperimentalRestEndpoints();
    }
    return enabledExperimentalRestEndpoints;
  }

  private boolean readEnabledExperimentalRestEndpoints() {
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
    if (maxCodeOwnerConfigCacheSize == null) {
      maxCodeOwnerConfigCacheSize = readMaxCodeOwnerConfigCacheSize();
    }
    return maxCodeOwnerConfigCacheSize;
  }

  private Optional<Integer> readMaxCodeOwnerConfigCacheSize() {
    try {
      int maxCodeOwnerConfigCacheSize =
          pluginConfigFactory
              .getFromGerritConfig(pluginName)
              .getInt(
                  KEY_MAX_CODE_OWNER_CONFIG_CACHE_SIZE, DEFAULT_MAX_CODE_OWNER_CONFIG_CACHE_SIZE);
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
      return Optional.of(DEFAULT_MAX_CODE_OWNER_CONFIG_CACHE_SIZE);
    }
  }
}
