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

import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.List;

/**
 * Branch-level Java API of the code-owners plugin.
 *
 * <p>To create an instance for a branch use {@link ProjectCodeOwners#branch(String)}.
 */
public interface BranchCodeOwners {
  /** Create a request to retrieve code owner config files from the branch. */
  CodeOwnerConfigFilesRequest codeOwnerConfigFiles() throws RestApiException;

  abstract class CodeOwnerConfigFilesRequest {
    /** Executes the request and retrieves the paths of the requested code owner config file */
    public abstract List<String> paths() throws RestApiException;
  }
}
