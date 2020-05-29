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

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.restapi.CodeOwnerConfigsInBranchCollection;
import com.google.gerrit.plugins.codeowners.restapi.CodeOwnerConfigsInBranchCollection.PathResource;
import com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerConfigForPathInBranch;
import com.google.gerrit.server.project.BranchResource;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.nio.file.Path;
import java.util.Optional;

/** Implementation of the {@link CodeOwnerConfigs} API for a branch. */
public class CodeOwnerConfigsInBranchImpl implements CodeOwnerConfigs {
  interface Factory {
    CodeOwnerConfigsInBranchImpl create(BranchResource branchResource);
  }

  private final CodeOwnerConfigsInBranchCollection codeOwnerConfigsInBranchCollection;
  private final GetCodeOwnerConfigForPathInBranch getCodeOwnerConfigForPathInBranch;
  private final BranchResource branchResource;

  @Inject
  public CodeOwnerConfigsInBranchImpl(
      CodeOwnerConfigsInBranchCollection codeOwnerConfigsInBranchCollection,
      GetCodeOwnerConfigForPathInBranch getCodeOwnerConfigForPathInBranch,
      @Assisted BranchResource branchResource) {
    this.codeOwnerConfigsInBranchCollection = codeOwnerConfigsInBranchCollection;
    this.getCodeOwnerConfigForPathInBranch = getCodeOwnerConfigForPathInBranch;
    this.branchResource = branchResource;
  }

  @Override
  public Optional<CodeOwnerConfigInfo> get(Path path) throws RestApiException {
    try {
      PathResource pathResource =
          codeOwnerConfigsInBranchCollection.parse(
              branchResource, IdString.fromDecoded(path.toString()));
      Response<CodeOwnerConfigInfo> response =
          getCodeOwnerConfigForPathInBranch.apply(pathResource);
      if (response.isNone()) {
        return Optional.empty();
      }
      return Optional.of(response.value());
    } catch (Exception e) {
      throw asRestApiException("Cannot get code owner config", e);
    }
  }
}
