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
 * Representation of a list of owned paths in the REST API.
 *
 * <p>This class determines the JSON format for the response of the {@code
 * com.google.gerrit.plugins.codeowners.restapi.GetOwnedPaths} REST endpoint.
 */
public class OwnedPathsInfo {
  /**
   * List of files that were changed in a change for which the user owns the new path, the old path
   * or both paths.
   *
   * <p>The entries are sorted alphabetically by new path, and by old path if new path is not
   * present.
   *
   * <p>Contains at most as many entries as the limit that was specified on the request.
   */
  public List<OwnedChangedFileInfo> ownedChangedFiles;

  /**
   * The list of the owned new and old paths that are contained in {@link #ownedChangedFiles}.
   *
   * <p>The paths are returned as absolute paths.
   *
   * <p>The paths are sorted alphabetically.
   *
   * <p>May contain more entries than the limit that was specified on the request (if the users owns
   * new and old path of renamed files).
   */
  public List<String> ownedPaths;

  /**
   * Whether the request would deliver more results if not limited.
   *
   * <p>Not set if {@code false}.
   */
  public Boolean more;
}
