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
import com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerConfigFiles;
import com.google.gerrit.server.project.BranchResource;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.List;

/** Implementation of the {@link BranchCodeOwners} API. */
public class BranchCodeOwnersImpl implements BranchCodeOwners {
  interface Factory {
    BranchCodeOwnersImpl create(BranchResource branchResource);
  }

  private final GetCodeOwnerConfigFiles getCodeOwnerConfigFiles;
  private final BranchResource branchResource;

  @Inject
  public BranchCodeOwnersImpl(
      GetCodeOwnerConfigFiles getCodeOwnerConfigFiles, @Assisted BranchResource branchResource) {
    this.getCodeOwnerConfigFiles = getCodeOwnerConfigFiles;
    this.branchResource = branchResource;
  }

  @Override
  public CodeOwnerConfigFilesRequest codeOwnerConfigFiles() throws RestApiException {
    return new CodeOwnerConfigFilesRequest() {
      @Override
      public List<String> getPaths() throws RestApiException {
        return getCodeOwnerConfigFiles.apply(branchResource).value();
      }
    };
  }
}
