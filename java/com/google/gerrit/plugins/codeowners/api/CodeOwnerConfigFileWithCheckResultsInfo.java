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

package com.google.gerrit.plugins.codeowners.api;

/**
 * Representation of a code owner config file with check results in the REST API.
 *
 * <p>This class determines the JSON format of code owner config files with check results in the
 * REST API.
 *
 * <p>In addition to the inherited fields from {@link CodeOwnerConfigFileInfo} the JSON contains
 * fields for results of checks which are performed by the {@link
 * com.google.gerrit.plugins.codeowners.restapi.CheckCodeOwner} REST endpoint.
 */
public class CodeOwnerConfigFileWithCheckResultsInfo extends CodeOwnerConfigFileInfo {
  /**
   * Whether this code owner config file assigns code ownership to the specified email and path.
   *
   * <p>Note that if code ownership is assigned to the email via a code owner config files, but the
   * email is not resolvable (see field `is_resolvable` field), the user is not a code owner.
   */
  public boolean assignsCodeOwnershipToUser;
}
