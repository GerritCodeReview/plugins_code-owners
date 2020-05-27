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

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

/**
 * The configuration of the code-owners plugin.
 *
 * <p>The configuration of the code-owners plugin is stored globally in the {@code gerrit.config}
 * file in the {@code plugin.code-owners} subsection.
 */
@Singleton
public class CodeOwnersPluginConfiguration {
  private static final String KEY_BACKEND = "backend";

  private final String pluginName;
  private final DynamicMap<CodeOwnersBackend> codeOwnersBackends;

  /** The name of the configured code owners backend. */
  private final String backendName;

  @Inject
  CodeOwnersPluginConfiguration(
      @PluginName String pluginName,
      PluginConfigFactory pluginConfigFactory,
      DynamicMap<CodeOwnersBackend> codeOwnersBackends) {
    this.pluginName = pluginName;
    this.codeOwnersBackends = codeOwnersBackends;

    this.backendName =
        pluginConfigFactory
            .getFromGerritConfig(pluginName)
            .getString(KEY_BACKEND, FindOwnersBackend.ID);
  }

  /**
   * Returns the configured {@link CodeOwnersBackend}.
   *
   * @return the {@link CodeOwnersBackend} that should be used
   */
  public CodeOwnersBackend getBackend() {
    return lookupBackend(backendName)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Code owner backend '%s' that is configured in gerrit.config"
                            + " (parameter plugin.%s.%s) not found",
                        backendName, pluginName, KEY_BACKEND)));
  }

  private Optional<CodeOwnersBackend> lookupBackend(String backendName) {
    // We must use "gerrit" as plugin name since DynamicMapProvider#get() hard-codes "gerrit" as
    // plugin name.
    return Optional.ofNullable(codeOwnersBackends.get("gerrit", backendName));
  }
}
