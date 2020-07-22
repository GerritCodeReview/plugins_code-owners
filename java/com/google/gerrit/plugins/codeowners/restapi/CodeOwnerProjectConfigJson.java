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

package com.google.gerrit.plugins.codeowners.restapi;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.plugins.codeowners.api.BackendInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerProjectConfigInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnersStatusInfo;
import com.google.gerrit.plugins.codeowners.api.RequiredApprovalInfo;
import com.google.gerrit.plugins.codeowners.config.RequiredApproval;
import java.util.Map;

public class CodeOwnerProjectConfigJson {

  static CodeOwnerProjectConfigInfo format(
      boolean isDisabled,
      ImmutableList<BranchNameKey> disabledBranches,
      String backendId,
      ImmutableMap<BranchNameKey, String> backendIdsPerBranch,
      RequiredApproval requiredApproval) {
    CodeOwnerProjectConfigInfo info = new CodeOwnerProjectConfigInfo();
    info.status = formatStatusInfo(isDisabled, disabledBranches);
    info.backend = formatBackendInfo(backendId, backendIdsPerBranch);
    info.requiredApproval = formatRequiredApprovalInfo(requiredApproval);
    return info;
  }

  @VisibleForTesting
  @Nullable
  static CodeOwnersStatusInfo formatStatusInfo(
      boolean isDisabled, ImmutableList<BranchNameKey> disabledBranches) {
    requireNonNull(disabledBranches, "disabledBranches");

    if (!isDisabled && disabledBranches.isEmpty()) {
      return null;
    }

    CodeOwnersStatusInfo info = new CodeOwnersStatusInfo();
    info.disabled = isDisabled ? true : null;
    if (!isDisabled && !disabledBranches.isEmpty()) {
      info.disabledBranches =
          disabledBranches.stream().map(BranchNameKey::branch).collect(toImmutableList());
    }
    return info;
  }

  @VisibleForTesting
  static BackendInfo formatBackendInfo(
      String backendId, ImmutableMap<BranchNameKey, String> backendIdsPerBranch) {
    requireNonNull(backendId, "backendId");
    requireNonNull(backendIdsPerBranch, "backendIdsPerBranch");

    BackendInfo info = new BackendInfo();
    info.id = backendId;

    ImmutableMap<String, String> idsByBranch =
        backendIdsPerBranch.entrySet().stream()
            .filter(e -> !e.getValue().equals(backendId))
            .collect(toImmutableMap(e -> e.getKey().branch(), Map.Entry::getValue));
    info.idsByBranch = !idsByBranch.isEmpty() ? idsByBranch : null;

    return info;
  }

  @VisibleForTesting
  static RequiredApprovalInfo formatRequiredApprovalInfo(RequiredApproval requiredApproval) {
    requireNonNull(requiredApproval, "requiredApproval");

    RequiredApprovalInfo info = new RequiredApprovalInfo();
    info.label = requiredApproval.labelType().getName();
    info.value = requiredApproval.value();
    return info;
  }

  /**
   * Private constructor to prevent instantiation of this class.
   *
   * <p>The class only contains static methods, hence the class never needs to be instantiated.
   */
  private CodeOwnerProjectConfigJson() {}
}
