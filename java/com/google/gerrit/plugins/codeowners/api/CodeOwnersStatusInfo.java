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

import java.util.List;

/**
 * Representation of the the code owner status configuration in the REST API.
 *
 * <p>This class determines the JSON format of code owner status configuration in the REST API.
 *
 * <p>Contains information about whether the code owners functionality is disabled for the project
 * or for any branch.
 */
public class CodeOwnersStatusInfo {
  /**
   * Whether the code owners functionality is disabled for the project.
   *
   * <p>If {@code true} the code owners API is disabled and submitting changes doesn't require code
   * owner approvals.
   *
   * <p>Not set if {@code false}.
   */
  public Boolean disabled;

  /**
   * Branches for which the code owners functionality is disabled.
   *
   * <p>Configurations for non-existing and non-visible branches are omitted.
   *
   * <p>Not set if {@link #disabled} is {@code true} or if no branch specific status configuration
   * is returned.
   */
  public List<String> disabledBranches;
}
