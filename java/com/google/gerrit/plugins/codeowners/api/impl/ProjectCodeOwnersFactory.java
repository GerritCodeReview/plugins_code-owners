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

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.plugins.codeowners.api.ProjectCodeOwners;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.ProjectsCollection;
import com.google.inject.Inject;

/**
 * Factory to instantiate the {@link ProjectCodeOwners} API.
 *
 * <p>Allows to instantiate the {@link ProjectCodeOwners} API for a project.
 */
public class ProjectCodeOwnersFactory {
  private final ProjectCodeOwnersImpl.Factory projectCodeOwnersFactory;
  private final ProjectsCollection projectsCollection;

  @Inject
  ProjectCodeOwnersFactory(
      ProjectCodeOwnersImpl.Factory projectCodeOwnersFactory,
      ProjectsCollection projectsCollection) {
    this.projectCodeOwnersFactory = projectCodeOwnersFactory;
    this.projectsCollection = projectsCollection;
  }

  /**
   * Returns the {@link ProjectCodeOwners} API for the given project.
   *
   * @param project the project for which the {@link ProjectCodeOwners} API should be returned
   * @return the {@link ProjectCodeOwners} API for the given project
   */
  public ProjectCodeOwners project(Project.NameKey project) throws RestApiException {
    return projectCodeOwnersFactory.create(getProjectResource(project));
  }

  /**
   * Creates a {@link ProjectResource} for the given project.
   *
   * @param project the project for which a {@link ProjectResource} should be created
   * @return the {@link ProjectResource} for the given project
   */
  private ProjectResource getProjectResource(Project.NameKey project) throws RestApiException {
    try {
      return projectsCollection.parse(
          TopLevelResource.INSTANCE, IdString.fromDecoded(project.get()));
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve project", e);
    }
  }
}
