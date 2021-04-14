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

import com.google.common.base.MoreObjects;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;

/** JSON entity that describes the code owner status for a path that was touched in a change. */
public class PathCodeOwnerStatusInfo {
  /**
   * The path to which the code owner status applies.
   *
   * <p>The path doesn't have any leading '/' (same formatting as for paths in other JSON entities).
   */
  public String path;

  /** The code owner status for the path. */
  public CodeOwnerStatus status;

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("path", path)
        .add("status", status.name())
        .toString();
  }
}
