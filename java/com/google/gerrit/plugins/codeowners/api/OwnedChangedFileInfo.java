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

/**
 * JSON representation of a file that was changed in a change for which the user owns the new path,
 * the old path or both paths.
 */
public class OwnedChangedFileInfo {
  /**
   * Owner information for the new path.
   *
   * <p>Not set for deletions.
   */
  public OwnedPathInfo newPath;

  /**
   * Owner information for the old path.
   *
   * <p>Only set for deletions and renames.
   */
  public OwnedPathInfo oldPath;
}
