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

import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.api.CodeOwners;
import com.google.gerrit.plugins.codeowners.api.CodeOwnersInfo;
import com.google.gerrit.plugins.codeowners.restapi.CodeOwnersInBranchCollection;
import com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnersForPathInBranch;
import com.google.gerrit.server.project.BranchResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.nio.file.Path;

/** Implementation of the {@link CodeOwners} API for a branch. */
public class CodeOwnersInBranchImpl implements CodeOwners {
  interface Factory {
    CodeOwnersInBranchImpl create(BranchResource branchResource);
  }

  private final CodeOwnersInBranchCollection codeOwnersInBranchCollection;
  private final Provider<GetCodeOwnersForPathInBranch> getCodeOwnersProvider;
  private final BranchResource branchResource;

  @Inject
  public CodeOwnersInBranchImpl(
      CodeOwnersInBranchCollection codeOwnersInBranchCollection,
      Provider<GetCodeOwnersForPathInBranch> getCodeOwnersProvider,
      @Assisted BranchResource branchResource) {
    this.codeOwnersInBranchCollection = codeOwnersInBranchCollection;
    this.getCodeOwnersProvider = getCodeOwnersProvider;
    this.branchResource = branchResource;
  }

  @Override
  public QueryRequest query() {
    return new QueryRequest() {
      @Override
      public CodeOwnersInfo get(Path path) throws RestApiException {
        try {
          GetCodeOwnersForPathInBranch getCodeOwners = getCodeOwnersProvider.get();
          getOptions().forEach(getCodeOwners::addOption);
          getLimit().ifPresent(getCodeOwners::setLimit);
          getSeed().ifPresent(getCodeOwners::setSeed);
          getResolveAllUsers().ifPresent(getCodeOwners::setResolveAllUsers);
          getHighestScoreOnly().ifPresent(getCodeOwners::setHighestScoreOnly);
          getDebug().ifPresent(getCodeOwners::setDebug);
          getRevision().ifPresent(getCodeOwners::setRevision);
          CodeOwnersInBranchCollection.PathResource pathInBranchResource =
              codeOwnersInBranchCollection.parse(
                  branchResource, IdString.fromDecoded(path.toString()));
          return getCodeOwners.apply(pathInBranchResource).value();
        } catch (Exception e) {
          throw asRestApiException("Cannot get code owners", e);
        }
      }
    };
  }
}
