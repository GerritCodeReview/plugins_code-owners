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
 * Representation of the code owner branch configuration in the REST API.
 *
 * <p>This class determines the JSON format of code owner branch configuration in the REST API.
 */
public class CodeOwnerBranchConfigInfo {
  /**
   * The general code owners configuration.
   *
   * <p>Not set if {@link #disabled} is {@code true}.
   */
  public GeneralInfo general;

  /**
   * Whether the code owners functionality is disabled for the branch.
   *
   * <p>If {@code true} the code owners API is disabled and submitting changes doesn't require code
   * owner approvals.
   *
   * <p>Not set if {@code false}.
   */
  public Boolean disabled;

  /**
   * ID of the code owner backend that is configured for the branch.
   *
   * <p>Not set if {@link #disabled} is {@code true}.
   */
  public String backendId;

  /**
   * The approval that is required from code owners to approve the files in a change.
   *
   * <p>Defines which approval counts as code owner approval.
   *
   * <p>Not set if {@link #disabled} is {@code true}.
   */
  public RequiredApprovalInfo requiredApproval;

  /**
   * The approvals that count as override for the code owners submit check.
   *
   * <p>If multiple approvals are returned, any of them is sufficient to override the code owners
   * submit check.
   *
   * <p>Not set if {@link #disabled} is {@code true}.
   */
  public List<RequiredApprovalInfo> overrideApproval;
}
