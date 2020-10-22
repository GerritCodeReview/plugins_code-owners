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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.List;
import java.util.Map;

/**
 * Revision-level Java API of the code-owners plugin.
 *
 * <p>To create an instance for a revision use {@link ChangeCodeOwners#revision(String)} or {@link
 * ChangeCodeOwners#current()}.
 */
public interface RevisionCodeOwners {
  /**
   * Create a request to check the code owner config files that have been added/modified in the
   * revision.
   */
  CheckCodeOwnerConfigFilesRequest checkCodeOwnerConfigFiles() throws RestApiException;

  /** Request to check code owner config files. */
  abstract class CheckCodeOwnerConfigFilesRequest {
    private String path;
    private ConsistencyProblemInfo.Status verbosity;

    /**
     * Sets a glob that limits the validation to code owner config files that have a path that
     * matches this glob.
     */
    public CheckCodeOwnerConfigFilesRequest setPath(String path) {
      this.path = path;
      return this;
    }

    /**
     * Gets the glob that limits the validation to code owner config files that have a path that
     * matches this glob.
     */
    @Nullable
    public String getPath() {
      return path;
    }

    /**
     * Sets the verbosity level that controls which kind of issues should be returned.
     *
     * <p>The following values are supported:
     *
     * <ul>
     *   <li>{@code FATAL}: only fatal issues are returned
     *   <li>{@code ERROR}: only fatal and error issues are returned
     *   <li>{@code WARNING}: all issues (warning, error and fatal) are returned
     * </ul>
     *
     * <p>If unset, {@code WARNING} is used.
     */
    public CheckCodeOwnerConfigFilesRequest setVerbosity(
        @Nullable ConsistencyProblemInfo.Status verbosity) {
      this.verbosity = verbosity;
      return this;
    }

    /** Gets the verbosity level that controls which kind of issues should be returned. */
    public ConsistencyProblemInfo.Status getVerbosity() {
      return verbosity;
    }

    /**
     * Executes the request to check the code owner config files and retrieves the result of the
     * validation.
     */
    public abstract Map<String, List<ConsistencyProblemInfo>> check() throws RestApiException;
  }

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements RevisionCodeOwners {
    @Override
    public CheckCodeOwnerConfigFilesRequest checkCodeOwnerConfigFiles() throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
