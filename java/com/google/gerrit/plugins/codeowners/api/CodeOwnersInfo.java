// Copyright (C) 2021 The Android Open Source Project
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
 * Representation of a code owners list in the REST API.
 *
 * <p>This class determines the JSON format for the response of the {@code
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnersForPathInBranch} and {@code
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnersForPathInChange} REST endpoints.
 */
public class CodeOwnersInfo {
  /** List of code owners. */
  public List<CodeOwnerInfo> codeOwners;

  /**
   * Whether the path is owned by all users.
   *
   * <p>Not set if {@code false}.
   */
  public Boolean ownedByAllUsers;

  /**
   * Debug logs that may help to understand why a user is or isn't suggested as a code owner.
   *
   * <p>Only set if requested via {@code --debug}.
   */
  public List<String> debugLogs;
}
