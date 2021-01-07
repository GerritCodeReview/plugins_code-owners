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

/**
 * Java API for change code owners.
 *
 * <p>To create an instance for a change use {@code ChangeCodeOwnersFactory}.
 */
public interface ChangeCodeOwners {
  /** Returns the code owner status for the files in the change. */
  CodeOwnerStatusInfo getCodeOwnerStatus() throws RestApiException;

  /** Returns the revision-level code owners API for the current revision. */
  default RevisionCodeOwners current() throws RestApiException {
    return revision("current");
  }

  /** Returns the revision-level code owners API for the given revision. */
  default RevisionCodeOwners revision(int id) throws RestApiException {
    return revision(Integer.toString(id));
  }

  /** Returns the revision-level code owners API for the given revision. */
  RevisionCodeOwners revision(String id) throws RestApiException;

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements ChangeCodeOwners {
    @Override
    public CodeOwnerStatusInfo getCodeOwnerStatus() {
      throw new NotImplementedException();
    }

    @Override
    public RevisionCodeOwners revision(String id) throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
