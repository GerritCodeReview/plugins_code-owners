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

package com.google.gerrit.plugins.codeowners.api;

import java.util.Map;

/**
 * Representation of the the code owner backend configuration in the REST API.
 *
 * <p>This class determines the JSON format of code owner backend configuration in the REST API.
 */
public class BackendInfo {
  /**
   * ID of the code owner backend that is configured for the project.
   *
   * <p>May be overwritten per branch by {@link #idsByBranch}.
   */
  public String id;

  /**
   * IDs of the code owner backends that are configured for individual branches.
   *
   * <p>Only contains entries for branches for which a code owner backend is configured that differs
   * from the backend that is configured for the project (see {@link #id}).
   *
   * <p>Configurations for non-existing and non-visible branches are omitted.
   *
   * <p>The map key is the full branch name. The map value is the ID of the code owner backend.
   *
   * <p>Not set if no branch specific backend configuration is returned.
   */
  public Map<String, String> idsByBranch;
}
