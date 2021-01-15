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
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.api.BranchCodeOwners;
import com.google.gerrit.plugins.codeowners.api.CheckCodeOwnerConfigFilesInput;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerProjectConfigInfo;
import com.google.gerrit.plugins.codeowners.api.ProjectCodeOwners;
import com.google.gerrit.plugins.codeowners.restapi.CheckCodeOwnerConfigFiles;
import com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerProjectConfig;
import com.google.gerrit.server.project.BranchResource;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.BranchesCollection;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.List;
import java.util.Map;

/** Implementation of the {@link ProjectCodeOwners} API. */
public class ProjectCodeOwnersImpl implements ProjectCodeOwners {
  interface Factory {
    ProjectCodeOwnersImpl create(ProjectResource projectResource);
  }

  private final BranchesCollection branchesCollection;
  private final BranchCodeOwnersImpl.Factory branchCodeOwnersApi;
  private final GetCodeOwnerProjectConfig getCodeOwnerProjectConfig;
  private final CheckCodeOwnerConfigFiles checkCodeOwnerConfigFiles;
  private final ProjectResource projectResource;

  @Inject
  public ProjectCodeOwnersImpl(
      BranchesCollection branchesCollection,
      BranchCodeOwnersImpl.Factory branchCodeOwnersApi,
      GetCodeOwnerProjectConfig getCodeOwnerProjectConfig,
      CheckCodeOwnerConfigFiles checkCodeOwnerConfigFiles,
      @Assisted ProjectResource projectResource) {
    this.branchesCollection = branchesCollection;
    this.branchCodeOwnersApi = branchCodeOwnersApi;
    this.getCodeOwnerProjectConfig = getCodeOwnerProjectConfig;
    this.checkCodeOwnerConfigFiles = checkCodeOwnerConfigFiles;
    this.projectResource = projectResource;
  }

  @Override
  public BranchCodeOwners branch(String branchName) throws RestApiException {
    try {
      BranchResource branchResource =
          branchesCollection.parse(projectResource, IdString.fromDecoded(branchName));
      return branchCodeOwnersApi.create(branchResource);
    } catch (Exception e) {
      throw asRestApiException("Cannot get branch code owners API", e);
    }
  }

  @Override
  public CodeOwnerProjectConfigInfo getConfig() throws RestApiException {
    try {
      return getCodeOwnerProjectConfig.apply(projectResource).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get code owner project config", e);
    }
  }

  @Override
  public CheckCodeOwnerConfigFilesRequest checkCodeOwnerConfigFiles() throws RestApiException {
    return new CheckCodeOwnerConfigFilesRequest() {
      @Override
      public Map<String, Map<String, List<ConsistencyProblemInfo>>> check()
          throws RestApiException {
        try {
          CheckCodeOwnerConfigFilesInput input = new CheckCodeOwnerConfigFilesInput();
          input.validateDisabledBranches = isValidateDisabledBranches();
          input.branches = getBranches();
          input.path = getPath();
          input.verbosity = getVerbosity();
          return checkCodeOwnerConfigFiles.apply(projectResource, input).value();
        } catch (Exception e) {
          throw asRestApiException("Cannot check code owner config files", e);
        }
      }
    };
  }
}
