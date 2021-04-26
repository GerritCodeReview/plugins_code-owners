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
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.List;

/**
 * Branch-level Java API of the code-owners plugin.
 *
 * <p>To create an instance for a branch use {@link ProjectCodeOwners#branch(String)}.
 */
public interface BranchCodeOwners {
  /** Returns the code owner project configuration. */
  CodeOwnerBranchConfigInfo getConfig() throws RestApiException;

  /** Create a request to retrieve code owner config files from the branch. */
  CodeOwnerConfigFilesRequest codeOwnerConfigFiles() throws RestApiException;

  /** Request to retrieve code owner config files from the branch. */
  abstract class CodeOwnerConfigFilesRequest {
    private boolean includeNonParsableFiles;
    private String email;
    private String path;

    /** Includes non-parsable code owner config files into the result. */
    public CodeOwnerConfigFilesRequest includeNonParsableFiles(boolean includeNonParsableFiles) {
      this.includeNonParsableFiles = includeNonParsableFiles;
      return this;
    }

    /** Whether non-parsable code owner config files should be included into the result. */
    public boolean getIncludeNonParsableFiles() {
      return includeNonParsableFiles;
    }

    /**
     * Limits the returned code owner config files to those that contain the given email.
     *
     * @param email the email that should appear in the returned code owner config files
     */
    public CodeOwnerConfigFilesRequest withEmail(String email) {
      this.email = email;
      return this;
    }

    /** Returns the email that should appear in the returned code owner config files. */
    @Nullable
    public String getEmail() {
      return email;
    }

    /**
     * Limits the returned code owner config files to those that have a path matching the given
     * glob.
     *
     * @param path the path glob that should be matched
     */
    public CodeOwnerConfigFilesRequest withPath(String path) {
      this.path = path;
      return this;
    }

    /** Returns the path glob that should be matched by the returned code owner config files. */
    @Nullable
    public String getPath() {
      return path;
    }

    /** Executes the request and retrieves the paths of the requested code owner config file. */
    public abstract List<String> paths() throws RestApiException;
  }

  /** Renames an email in the code owner config files of the branch. */
  RenameEmailResultInfo renameEmailInCodeOwnerConfigFiles(RenameEmailInput input)
      throws RestApiException;

  /** Checks the code ownership of a user for a path in a branch. */
  CodeOwnerCheckRequest checkCodeOwner() throws RestApiException;

  /** Request for checking the code ownership of a user for a path in a branch. */
  abstract class CodeOwnerCheckRequest {
    private String email;
    private String path;
    private String change;
    private String user;

    /**
     * Sets the email for which the code ownership should be checked.
     *
     * @param email the email for which the code ownership should be checked
     */
    public CodeOwnerCheckRequest email(String email) {
      this.email = email;
      return this;
    }

    /** Returns the email for which the code ownership should be checked. */
    @Nullable
    public String getEmail() {
      return email;
    }

    /**
     * Sets the path for which the code ownership should be checked.
     *
     * @param path the path for which the code ownership should be checked
     */
    public CodeOwnerCheckRequest path(String path) {
      this.path = path;
      return this;
    }

    /** Returns the path for which the code ownership should be checked. */
    @Nullable
    public String getPath() {
      return path;
    }

    /**
     * Sets the change for which permissions should be checked.
     *
     * <p>If not specified change permissions are not checked.
     *
     * @param change the change for which permissions should be checked
     */
    public CodeOwnerCheckRequest change(@Nullable String change) {
      this.change = change;
      return this;
    }

    /** Returns the change for which permissions should be checked. */
    @Nullable
    public String getChange() {
      return change;
    }

    /**
     * Sets the user for which the code owner visibility should be checked.
     *
     * <p>If not specified the code owner visibility is not checked.
     *
     * @param user the user for which the code owner visibility should be checked
     */
    public CodeOwnerCheckRequest user(@Nullable String user) {
      this.user = user;
      return this;
    }

    /** Returns the user for which the code owner visibility should be checked. */
    @Nullable
    public String getUser() {
      return user;
    }

    /** Executes the request and retrieves the result. */
    public abstract CodeOwnerCheckInfo check() throws RestApiException;
  }

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements BranchCodeOwners {
    @Override
    public CodeOwnerBranchConfigInfo getConfig() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public CodeOwnerConfigFilesRequest codeOwnerConfigFiles() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public RenameEmailResultInfo renameEmailInCodeOwnerConfigFiles(RenameEmailInput input)
        throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public CodeOwnerCheckRequest checkCodeOwner() throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
