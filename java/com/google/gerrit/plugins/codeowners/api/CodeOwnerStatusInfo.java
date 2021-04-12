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
 * JSON entity that describes the response of the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerStatus} REST endpoint.
 */
public class CodeOwnerStatusInfo {
  /**
   * The number of the patch set for which the code owner statuses are returned.
   *
   * <p>The {@link com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerStatus} REST endpoint
   * always provides the code owner statuses for the current patch set. This field tells the caller
   * for which patch set the code owner status are returned (aka which patch set was current when
   * the call was made). Knowing the patch set number allows callers to detect races with parallel
   * requests that upload new patch sets.
   */
  public int patchSetNumber;

  /** List of the code owner statuses for the files in the change. */
  public List<FileCodeOwnerStatusInfo> fileCodeOwnerStatuses;

  /**
   * Whether the request would deliver more results if not limited.
   *
   * <p>Not set if {@code false}.
   */
  public Boolean more;
}
