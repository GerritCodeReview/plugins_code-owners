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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.List;
import java.util.Map;

/**
 * Project-level Java API of the code-owners plugin.
 *
 * <p>To create an instance for a project use {@code ProjectCodeOwnersFactory}.
 */
public interface ProjectCodeOwners {
  /** Returns the code owner project configuration. */
  CodeOwnerProjectConfigInfo getConfig() throws RestApiException;

  /**
   * Update the code owner project configuration.
   *
   * @param input the input specifying which parameters should be updated
   * @return the update code owner project configuration
   */
  CodeOwnerProjectConfigInfo updateConfig(CodeOwnerProjectConfigInput input)
      throws RestApiException;

  /** Create a request to check the code owner config files in the project. */
  CheckCodeOwnerConfigFilesRequest checkCodeOwnerConfigFiles() throws RestApiException;

  /** Request to check code owner config files. */
  abstract class CheckCodeOwnerConfigFilesRequest {
    private boolean validateDisabledBranches;
    private ImmutableList<String> branches;
    private String path;
    private ConsistencyProblemInfo.Status verbosity;

    /**
     * Includes code owner config files in branches for which the code owners functionality is
     * disabled into the validation.
     */
    public CheckCodeOwnerConfigFilesRequest validateDisabledBranches() {
      return validateDisabledBranches(true);
    }

    /**
     * Sets whether code owner config files in branches for which the code owners functionality is
     * disabled should be validated.
     */
    public CheckCodeOwnerConfigFilesRequest validateDisabledBranches(
        boolean validateDisabledBranches) {
      this.validateDisabledBranches = validateDisabledBranches;
      return this;
    }

    /**
     * Whether code owner config files in branches for which the code owners functionality is
     * disabled should be validated.
     */
    public boolean isValidateDisabledBranches() {
      return validateDisabledBranches;
    }

    /** Sets the branches for which code owner config files should be validated. */
    public CheckCodeOwnerConfigFilesRequest setBranches(List<String> branches) {
      this.branches = ImmutableList.copyOf(branches);
      return this;
    }

    /**
     * Gets the branches for which code owner config files should be validated.
     *
     * <p>Returns {@code null} if no branches have been set.
     */
    @Nullable
    public ImmutableList<String> getBranches() {
      return branches;
    }

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
    public abstract Map<String, Map<String, List<ConsistencyProblemInfo>>> check()
        throws RestApiException;
  }

  /** Returns the branch-level code owners API for the given branch. */
  BranchCodeOwners branch(String branchName) throws RestApiException;

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements ProjectCodeOwners {
    @Override
    public CodeOwnerProjectConfigInfo getConfig() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public CodeOwnerProjectConfigInfo updateConfig(CodeOwnerProjectConfigInput input)
        throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public CheckCodeOwnerConfigFilesRequest checkCodeOwnerConfigFiles() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public BranchCodeOwners branch(String branchName) throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
