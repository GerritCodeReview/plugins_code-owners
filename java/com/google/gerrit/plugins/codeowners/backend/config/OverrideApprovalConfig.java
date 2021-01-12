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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Class to read the override approval configuration from {@code gerrit.config} and from {@code
 * code-owners.config} in {@code refs/meta/config}.
 *
 * <p>The default override approval is configured in {@code gerrit.config} by the {@code
 * plugin.code-owners.overrideApproval} parameter.
 *
 * <p>On project-level the override approval is configured in {@code code-owners.config} in {@code
 * refs/meta/config} by the {@code codeOwners.overrideApproval} parameter.
 *
 * <p>Projects that have no override approval configuration inherit the configuration from their
 * parent projects.
 *
 * <p>If no applying override approval exists, overriding is disabled.
 */
@Singleton
public class OverrideApprovalConfig extends AbstractRequiredApprovalConfig {
  @VisibleForTesting public static final String KEY_OVERRIDE_APPROVAL = "overrideApproval";

  @Inject
  OverrideApprovalConfig(@PluginName String pluginName, PluginConfigFactory pluginConfigFactory) {
    super(pluginName, pluginConfigFactory);
  }

  @Override
  protected String getConfigKey() {
    return KEY_OVERRIDE_APPROVAL;
  }
}
