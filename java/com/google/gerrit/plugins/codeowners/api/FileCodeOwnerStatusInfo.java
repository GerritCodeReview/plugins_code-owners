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
import com.google.gerrit.extensions.common.ChangeType;

/** JSON entity that describes the code owner status for a file that was touched in a change. */
public class FileCodeOwnerStatusInfo {
  /**
   * The type of the file modification.
   *
   * <p>Not set if {@link ChangeType#MODIFIED}.
   */
  public ChangeType changeType;

  /**
   * Code owner status for the old path of the file.
   *
   * <p>Only set if the file was deleted or renamed.
   */
  public PathCodeOwnerStatusInfo oldPathStatus;

  /**
   * Code owner status for the new path of the file.
   *
   * <p>Not set if the file was deleted.
   */
  public PathCodeOwnerStatusInfo newPathStatus;

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("changeType", changeType.name())
        .add("oldPathStatus", oldPathStatus)
        .add("newPathStatus", newPathStatus)
        .toString();
  }
}
