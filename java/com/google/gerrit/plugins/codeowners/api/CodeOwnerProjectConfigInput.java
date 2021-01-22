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
 * Input for the {@code com.google.gerrit.plugins.codeowners.restapi.PutCodeOwnerProjectConfig} REST
 * endpoint
 *
 * <p>This class determines the JSON format of the input in the REST API.
 *
 * <p>If a field in this input entity is not set, the corresponding parameter in the {@code
 * code-owners.config} file is not updated.
 */
public class CodeOwnerProjectConfigInput {
  /** Whether the code owners functionality should be disabled/enabled for the project. */
  public Boolean disabled;

  /**
   * Branches for which the code owners functionality is disabled.
   *
   * <p>Can be exact refs, ref patterns or regular expressions.
   *
   * <p>Overrides any existing disabled branch configuration.
   */
  public List<String> disabledBranches;

  /** The file extension that should be used for code owner config files in this project. */
  public String fileExtension;

  /**
   * The approval that is required from code owners.
   *
   * <p>The required approval must be specified in the format {@code <label-name>+<label-value>}.
   */
  public String requiredApproval;

  /**
   * The approvals that count as override for the code owners submit check.
   *
   * <p>The override approvals must be specified in the format {@code <label-name>+<label-value>}.
   */
  public List<String> overrideApprovals;
}
