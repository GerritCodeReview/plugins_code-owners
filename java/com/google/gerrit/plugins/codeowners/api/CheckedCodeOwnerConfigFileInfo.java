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
 */
public class CheckedCodeOwnerConfigFileInfo {
  /** The code owner config file that was checked. */
  public CodeOwnerConfigFileInfo codeOwnerConfigFileInfo;

  /**
   * Whether this code owner config file assigns code ownership to the specified email and path.
   *
   * <p>Note that if code ownership is assigned to the email via a code owner config file, but the
   * email is not resolvable (see the {@link CodeOwnerCheckInfo#isResolvable} field), the user is
   * not a code owner.
   */
  public boolean assignsCodeOwnershipToUser;

  /** Whether code owners from parent directory are ignored. */
  public boolean areParentCodeOwnersIgnored;

  /**
   * Whether folder code owners are ignored (i.e. whether there is a matching per-file rule that
   * ignores parent code owners).
   */
  public boolean areFolderCodeOwnersIgnored;
}
