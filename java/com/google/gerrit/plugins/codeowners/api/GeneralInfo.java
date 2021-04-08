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

import com.google.gerrit.plugins.codeowners.backend.FallbackCodeOwners;
import com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy;

/**
 * Representation of the general code owners configuration in the REST API.
 *
 * <p>This class determines the JSON format of the general code owners configuration in the REST
 * API.
 */
public class GeneralInfo {
  /**
   * The file extension that should be used for code owner config files in this project.
   *
   * <p>Unset if no file extension is used.
   */
  public String fileExtension;

  /** Strategy that defines for merge commits which files require code owner approvals. */
  public MergeCommitStrategy mergeCommitStrategy;

  /**
   * Whether an implicit code owner approval from the last uploader is assumed.
   *
   * <p>Not set, if {@code false}.
   */
  public Boolean implicitApprovals;

  /**
   * Optional URL for a page that provides project/host-specific information about how to request a
   * code owner override.
   */
  public String overrideInfoUrl;

  /**
   * Optional URL for a page that provides project/host-specific information about how to deal with
   * invalid code owner config files.
   */
  public String invalidCodeOwnerConfigInfoUrl;

  /** Policy that controls who should own paths that have no code owners defined. */
  public FallbackCodeOwners fallbackCodeOwners;
}
