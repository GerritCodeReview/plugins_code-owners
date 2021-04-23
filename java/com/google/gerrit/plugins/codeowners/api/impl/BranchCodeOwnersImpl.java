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

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.api.BranchCodeOwners;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerBranchConfigInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerCheckInfo;
import com.google.gerrit.plugins.codeowners.api.RenameEmailInput;
import com.google.gerrit.plugins.codeowners.api.RenameEmailResultInfo;
import com.google.gerrit.plugins.codeowners.restapi.CheckCodeOwner;
import com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerBranchConfig;
import com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerConfigFiles;
import com.google.gerrit.plugins.codeowners.restapi.RenameEmail;
import com.google.gerrit.server.project.BranchResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.util.List;

/** Implementation of the {@link BranchCodeOwners} API. */
public class BranchCodeOwnersImpl implements BranchCodeOwners {
  interface Factory {
    BranchCodeOwnersImpl create(BranchResource branchResource);
  }

  private final GetCodeOwnerBranchConfig getCodeOwnerBranchConfig;
  private final Provider<GetCodeOwnerConfigFiles> getCodeOwnerConfigFilesProvider;
  private final RenameEmail renameEmail;
  private final Provider<CheckCodeOwner> checkCodeOwnerProvider;
  private final BranchResource branchResource;

  @Inject
  public BranchCodeOwnersImpl(
      GetCodeOwnerBranchConfig getCodeOwnerBranchConfig,
      Provider<GetCodeOwnerConfigFiles> getCodeOwnerConfigFilesProvider,
      RenameEmail renameEmail,
      Provider<CheckCodeOwner> checkCodeOwnerProvider,
      @Assisted BranchResource branchResource) {
    this.getCodeOwnerConfigFilesProvider = getCodeOwnerConfigFilesProvider;
    this.getCodeOwnerBranchConfig = getCodeOwnerBranchConfig;
    this.renameEmail = renameEmail;
    this.checkCodeOwnerProvider = checkCodeOwnerProvider;
    this.branchResource = branchResource;
  }

  @Override
  public CodeOwnerBranchConfigInfo getConfig() throws RestApiException {
    try {
      return getCodeOwnerBranchConfig.apply(branchResource).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get code owner branch config", e);
    }
  }

  @Override
  public CodeOwnerConfigFilesRequest codeOwnerConfigFiles() throws RestApiException {
    return new CodeOwnerConfigFilesRequest() {
      @Override
      public List<String> paths() throws RestApiException {
        GetCodeOwnerConfigFiles getCodeOwnerConfigFiles = getCodeOwnerConfigFilesProvider.get();
        getCodeOwnerConfigFiles.setIncludeNonParsableFiles(getIncludeNonParsableFiles());
        getCodeOwnerConfigFiles.setEmail(getEmail());
        getCodeOwnerConfigFiles.setPath(getPath());
        return getCodeOwnerConfigFiles.apply(branchResource).value();
      }
    };
  }

  @Override
  public RenameEmailResultInfo renameEmailInCodeOwnerConfigFiles(RenameEmailInput input)
      throws RestApiException {
    try {
      return renameEmail.apply(branchResource, input).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot rename email", e);
    }
  }

  @Override
  public CodeOwnerCheckRequest checkCodeOwner() throws RestApiException {
    return new CodeOwnerCheckRequest() {
      @Override
      public CodeOwnerCheckInfo check() throws RestApiException {
        CheckCodeOwner checkCodeOwner = checkCodeOwnerProvider.get();
        checkCodeOwner.setEmail(getEmail());
        checkCodeOwner.setPath(getPath());
        checkCodeOwner.setChange(getChange());
        checkCodeOwner.setUser(getUser());
        try {
          return checkCodeOwner.apply(branchResource).value();
        } catch (Exception e) {
          throw asRestApiException("Cannot check code owner", e);
        }
      }
    };
  }
}
