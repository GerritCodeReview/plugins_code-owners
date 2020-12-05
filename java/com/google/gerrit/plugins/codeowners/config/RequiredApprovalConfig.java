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
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Class to read the required approval configuration from {@code gerrit.config} and from {@code
 * code-owners.config} in {@code refs/meta/config}.
 *
 * <p>The default required approval is configured in {@code gerrit.config} by the {@code
 * plugin.code-owners.requiredApproval} parameter.
 *
 * <p>On project-level the required approval is configured in {@code code-owners.config} in {@code
 * refs/meta/config} by the {@code codeOwners.requiredApproval} parameter.
 *
 * <p>Projects that have no required approval configuration inherit the configuration from their
 * parent projects.
 */
@Singleton
public class RequiredApprovalConfig extends AbstractRequiredApprovalConfig {
  @VisibleForTesting public static final String KEY_REQUIRED_APPROVAL = "requiredApproval";

  /** By default a {@code Code-Review+1} vote from a code owner approves the file. */
  @VisibleForTesting public static final String DEFAULT_LABEL = "Code-Review";

  @VisibleForTesting public static final short DEFAULT_VALUE = 1;

  @Inject
  RequiredApprovalConfig(@PluginName String pluginName, PluginConfigFactory pluginConfigFactory) {
    super(pluginName, pluginConfigFactory);
  }

  @Override
  protected String getConfigKey() {
    return KEY_REQUIRED_APPROVAL;
  }

  public RequiredApproval createDefault(ProjectState projectState) throws IllegalStateException {
    try {
      return RequiredApproval.createDefault(projectState, DEFAULT_LABEL, DEFAULT_VALUE);
    } catch (IllegalStateException | IllegalArgumentException e) {
      throw new InvalidPluginConfigurationException(
          pluginName,
          String.format(
              "The default required approval '%s+%d' that is used for project %s is not valid: %s"
                  + " Please configure a valid required approval in %s.config (parameter %s.%s).",
              DEFAULT_LABEL,
              DEFAULT_VALUE,
              projectState.getName(),
              e.getMessage(),
              pluginName,
              StatusConfig.SECTION_CODE_OWNERS,
              getConfigKey()));
    }
  }
}
