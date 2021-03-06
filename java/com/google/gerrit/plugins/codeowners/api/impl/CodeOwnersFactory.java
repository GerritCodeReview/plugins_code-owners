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

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.plugins.codeowners.api.CodeOwners;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.project.BranchResource;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.change.ChangesCollection;
import com.google.gerrit.server.restapi.change.Revisions;
import com.google.gerrit.server.restapi.project.BranchesCollection;
import com.google.gerrit.server.restapi.project.ProjectsCollection;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Factory to instantiate the {@link CodeOwners} API.
 *
 * <p>Allows to instantiate the {@link CodeOwners} API for a branch and maybe later for a change.
 */
@Singleton
public class CodeOwnersFactory {
  private final CodeOwnersInBranchImpl.Factory codeOwnersInBranchFactory;
  private final CodeOwnersInChangeImpl.Factory codeOwnersInChangeFactory;
  private final ProjectsCollection projectsCollection;
  private final BranchesCollection branchesCollection;
  private final ChangesCollection changesCollection;
  private final Revisions revisions;

  @Inject
  CodeOwnersFactory(
      CodeOwnersInBranchImpl.Factory codeOwnersInBranchFactory,
      CodeOwnersInChangeImpl.Factory codeOwnersInChangeFactory,
      ProjectsCollection projectsCollection,
      BranchesCollection branchesCollection,
      ChangesCollection changesCollection,
      Revisions revisions) {
    this.codeOwnersInBranchFactory = codeOwnersInBranchFactory;
    this.codeOwnersInChangeFactory = codeOwnersInChangeFactory;
    this.projectsCollection = projectsCollection;
    this.branchesCollection = branchesCollection;
    this.changesCollection = changesCollection;
    this.revisions = revisions;
  }

  /**
   * Returns the {@link CodeOwners} API for the given branch.
   *
   * @param project the project for which the {@link CodeOwners} API should be returned
   * @param branch the branch for which the {@link CodeOwners} API should be returned
   * @return the {@link CodeOwners} API for the given branch
   */
  public CodeOwners branch(Project.NameKey project, String branch) throws RestApiException {
    return branch(BranchNameKey.create(project, branch));
  }

  /**
   * Returns the {@link CodeOwners} API for the given branch.
   *
   * @param branchNameKey the project and branch for which the {@link CodeOwners} API should be
   *     returned
   * @return the {@link CodeOwners} API for the given branch
   */
  public CodeOwners branch(BranchNameKey branchNameKey) throws RestApiException {
    return codeOwnersInBranchFactory.create(getBranchResource(branchNameKey));
  }

  /**
   * Returns the {@link CodeOwners} API for the given revision.
   *
   * @param changeId the ID of the change that contains the revision
   * @param revisionId the ID of the revision for which the {@link CodeOwners} API should be
   *     returned
   * @return the {@link CodeOwners} API for the given revision
   */
  public CodeOwners change(String changeId, String revisionId) throws RestApiException {
    return codeOwnersInChangeFactory.create(getRevisionResource(changeId, revisionId));
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

  /**
   * Creates a {@link RevisionResource} for the given revision.
   *
   * @param changeId ID of the change that contains the revision
   * @param revisionId ID of the revision for which a {@link RevisionResource} should be created
   * @return the {@link RevisionResource} for the given revision
   */
  private RevisionResource getRevisionResource(String changeId, String revisionId)
      throws RestApiException {
    try {
      ChangeResource changeResource =
          changesCollection.parse(TopLevelResource.INSTANCE, IdString.fromDecoded(changeId));
      return revisions.parse(changeResource, IdString.fromDecoded(revisionId));
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve revision", e);
    }
  }
}
