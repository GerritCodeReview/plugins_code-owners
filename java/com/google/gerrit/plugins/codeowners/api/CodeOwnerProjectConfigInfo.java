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
 * Representation of the code owner project configuration in the REST API.
 *
 * <p>This class determines the JSON format of code owner project configuration in the REST API.
 */
public class CodeOwnerProjectConfigInfo {
  /**
   * The general code owners configuration.
   *
   * <p>Not set if {@code status.disabled} is {@code true}.
   */
  public GeneralInfo general;

  /**
   * The code owners status configuration.
   *
   * <p>Contains information about whether the code owners functionality is disabled for the project
   * or for any branch.
   */
  public CodeOwnersStatusInfo status;

  /**
   * The code owner backend configuration.
   *
   * <p>Not set if {@code status.disabled} is {@code true}.
   */
  public BackendInfo backend;

  /**
   * The approval that is required from code owners to approve the files in a change.
   *
   * <p>Defines which approval counts as code owner approval.
   *
   * <p>Not set if {@code status.disabled} is {@code true}.
   */
  public RequiredApprovalInfo requiredApproval;

  /**
   * The approval that count as override for the code owners submit check.
   *
   * <p>If multiple approvals are returned, any of them is sufficient to override the code owners
   * submit check.
   *
   * <p>Not set if {@code status.disabled} is {@code true}.
   */
  public List<RequiredApprovalInfo> overrideApproval;
}
