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

package com.google.gerrit.plugins.codeowners.api.impl;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.api.CheckCodeOwnerConfigFilesInRevisionInput;
import com.google.gerrit.plugins.codeowners.api.RevisionCodeOwners;
import com.google.gerrit.plugins.codeowners.restapi.CheckCodeOwnerConfigFilesInRevision;
import com.google.gerrit.server.change.RevisionResource;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.List;
import java.util.Map;

/** Implementation of the {@link RevisionCodeOwners} API. */
public class RevisionCodeOwnersImpl implements RevisionCodeOwners {
  interface Factory {
    RevisionCodeOwnersImpl create(RevisionResource revisionResource);
  }

  private final CheckCodeOwnerConfigFilesInRevision checkCodeOwnerConfigFilesInRevision;
  private final RevisionResource revisionResource;

  @Inject
  public RevisionCodeOwnersImpl(
      CheckCodeOwnerConfigFilesInRevision checkCodeOwnerConfigFilesInRevision,
      @Assisted RevisionResource revisionResource) {
    this.checkCodeOwnerConfigFilesInRevision = checkCodeOwnerConfigFilesInRevision;
    this.revisionResource = revisionResource;
  }

  @Override
  public CheckCodeOwnerConfigFilesRequest checkCodeOwnerConfigFiles() throws RestApiException {
    return new CheckCodeOwnerConfigFilesRequest() {
      @Override
      public Map<String, List<ConsistencyProblemInfo>> check() throws RestApiException {
        try {
          CheckCodeOwnerConfigFilesInRevisionInput input =
              new CheckCodeOwnerConfigFilesInRevisionInput();
          input.path = getPath();
          input.verbosity = getVerbosity();
          return checkCodeOwnerConfigFilesInRevision.apply(revisionResource, input).value();
        } catch (Exception e) {
          throw asRestApiException("Cannot check code owner config files", e);
        }
      }
    };
  }
}
