// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.plugins.codeowners.restapi;

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.api.access.PluginPermission;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Global capability that allows a user to call the {@link CheckCodeOwnerConfigFiles} REST endpoint.
 */
@Singleton
public class CheckCodeOwnerConfigFilesCapability extends CapabilityDefinition {
  public static final String ID = "checkCodeOwnerConfigFiles";

  private final String pluginName;

  @Inject
  CheckCodeOwnerConfigFilesCapability(@PluginName String pluginName) {
    this.pluginName = pluginName;
  }

  @Override
  public String getDescription() {
    return "Check Code Owner Config Files";
  }

  public PluginPermission getPermission() {
    return new PluginPermission(pluginName, ID);
  }
}
