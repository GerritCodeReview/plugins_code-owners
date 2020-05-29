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

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.project.BranchResource;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.BranchesCollection;
import com.google.gerrit.server.restapi.project.ProjectsCollection;
import com.google.inject.Inject;

/**
 * Factory to instantiate the {@link CodeOwnerConfigs} API.
 *
 * <p>Allows to instantiate the {@link CodeOwnerConfigs} API for a branch and maybe later for a
 * change.
 */
public class CodeOwnerConfigsFactory {
  private final CodeOwnerConfigsInBranchImpl.Factory codeOwnerConfigsInBranchFactory;
  private final ProjectsCollection projectsCollection;
  private final BranchesCollection branchesCollection;

  @Inject
  CodeOwnerConfigsFactory(
      CodeOwnerConfigsInBranchImpl.Factory codeOwnerConfigsInBranchFactory,
      ProjectsCollection projectsCollection,
      BranchesCollection branchesCollection) {
    this.codeOwnerConfigsInBranchFactory = codeOwnerConfigsInBranchFactory;
    this.projectsCollection = projectsCollection;
    this.branchesCollection = branchesCollection;
  }

  /**
   * Returns the {@link CodeOwnerConfigs} API for the given branch.
   *
   * @param project the project for which the {@link CodeOwnerConfigs} API should be returned
   * @param branch the name of the branch for which the {@link CodeOwnerConfigs} API should be
   *     returned
   * @return the {@link CodeOwnerConfigs} API for the given branch
   */
  public CodeOwnerConfigs branch(Project.NameKey project, String branch) throws RestApiException {
    return branch(BranchNameKey.create(project, branch));
  }

  /**
   * Returns the {@link CodeOwnerConfigs} API for the given branch.
   *
   * @param branchNameKey the project + branch for which the {@link CodeOwnerConfigs} API should be
   *     returned
   * @return the {@link CodeOwnerConfigs} API for the given branch
   */
  public CodeOwnerConfigs branch(BranchNameKey branchNameKey) throws RestApiException {
    return codeOwnerConfigsInBranchFactory.create(getBranchResource(branchNameKey));
  }

  /**
   * Creates a {@link BranchResource} for the given branch.
   *
   * @param branchNameKey the branch for which a {@link BranchResource} should be created
   * @return the {@link BranchResource} for the given branch
   */
  private BranchResource getBranchResource(BranchNameKey branchNameKey) throws RestApiException {
    try {
      ProjectResource projectResource =
          projectsCollection.parse(
              TopLevelResource.INSTANCE, IdString.fromDecoded(branchNameKey.project().get()));
      return branchesCollection.parse(
          projectResource, IdString.fromDecoded(branchNameKey.branch()));
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve branch", e);
    }
  }
}
