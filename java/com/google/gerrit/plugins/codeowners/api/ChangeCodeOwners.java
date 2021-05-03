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

import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.Optional;

/**
 * Java API for change code owners.
 *
 * <p>To create an instance for a change use {@code ChangeCodeOwnersFactory}.
 */
public interface ChangeCodeOwners {
  /** Creates a request to retrieve the code owner status for the files in the change. */
  CodeOwnerStatusRequest getCodeOwnerStatus() throws RestApiException;

  /** Returns the revision-level code owners API for the current revision. */
  default RevisionCodeOwners current() throws RestApiException {
    return revision("current");
  }

  /** Returns the revision-level code owners API for the given revision. */
  RevisionCodeOwners revision(String id) throws RestApiException;

  /**
   * Request to compute code owner status.
   *
   * <p>Allows to set parameters on the request before executing it by calling {@link #get()}.
   */
  abstract class CodeOwnerStatusRequest {
    private Integer start;
    private Integer limit;

    /**
     * Sets a limit on the number of code owner statuses that should be returned.
     *
     * @param start number of code owner statuses to skip
     */
    public CodeOwnerStatusRequest withStart(int start) {
      this.start = start;
      return this;
    }

    /** Returns the number of code owner statuses to skip. */
    public Optional<Integer> getStart() {
      return Optional.ofNullable(start);
    }

    /**
     * Sets a limit on the number of code owner statuses that should be returned.
     *
     * @param limit the limit
     */
    public CodeOwnerStatusRequest withLimit(int limit) {
      this.limit = limit;
      return this;
    }

    /** Returns the limit. */
    public Optional<Integer> getLimit() {
      return Optional.ofNullable(limit);
    }

    /**
     * Executes this request and retrieves the code owner status.
     *
     * @return the code owner status
     */
    public abstract CodeOwnerStatusInfo get() throws RestApiException;
  }

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements ChangeCodeOwners {
    @Override
    public CodeOwnerStatusRequest getCodeOwnerStatus() {
      throw new NotImplementedException();
    }

    @Override
    public RevisionCodeOwners revision(String id) throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
