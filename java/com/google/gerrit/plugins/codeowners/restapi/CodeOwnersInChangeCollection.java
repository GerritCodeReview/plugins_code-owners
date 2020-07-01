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

package com.google.gerrit.plugins.codeowners.restapi;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.plugins.codeowners.JgitPath;
import com.google.gerrit.plugins.codeowners.restapi.CodeOwnersInChangeCollection.PathResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.FileResource;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import java.nio.file.Path;

/**
 * REST collection that serves requests to {@code
 * /changes/<change-id>/revisions/<revision-id>/code_owners/}.
 */
@Singleton
public class CodeOwnersInChangeCollection
    implements ChildCollection<RevisionResource, PathResource> {
  private final DynamicMap<RestView<PathResource>> views;
  private final GitRepositoryManager repoManager;
  private final ProjectCache projectCache;

  @Inject
  CodeOwnersInChangeCollection(
      DynamicMap<RestView<PathResource>> views,
      GitRepositoryManager repoManager,
      ProjectCache projectCache) {
    this.views = views;
    this.repoManager = repoManager;
    this.projectCache = projectCache;
  }

  @Override
  public RestView<RevisionResource> list() throws ResourceNotFoundException {
    // Listing paths that have code owners is not implemented.
    throw new ResourceNotFoundException();
  }

  @Override
  public PathResource parse(RevisionResource revisionResource, IdString id)
      throws RestApiException, IOException {
    // Check if the file exists in the revision only after creating the path resource. This way we
    // get a more specific error response for invalid paths ('400 Bad Request' instead of a '404 Not
    // Found').
    PathResource pathResource = PathResource.parse(revisionResource, id);
    checkThatFileExists(revisionResource, pathResource, id);
    return pathResource;
  }

  private void checkThatFileExists(
      RevisionResource revisionResource, PathResource pathResource, IdString id)
      throws RestApiException, IOException {
    ProjectState projectState =
        projectCache
            .get(revisionResource.getProject())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        String.format("project %s not found", revisionResource.getProject())));
    try {
      // TODO(ekempin): Also accept requests for the old paths of files that have been renamed or
      // deleted. When a file is renamed/deleted we require code owner approval on the old path,
      // hence it should be possible to list the code owners for these old paths.
      FileResource.create(
          repoManager,
          projectState,
          revisionResource.getPatchSet().commitId(),
          JgitPath.of(pathResource.getPath()).get());
    } catch (ResourceNotFoundException e) {
      // Make sure that the exception is thrown with the path we got as input and not the Jgit
      // version of the path which always has no leading '/'.
      throw new ResourceNotFoundException(id);
    }
  }

  @Override
  public DynamicMap<RestView<PathResource>> views() {
    return views;
  }

  /**
   * REST resource representing an arbitrary path in a branch under the {@link
   * CodeOwnersInChangeCollection} REST collection.
   */
  public static class PathResource extends AbstractPathResource {
    /**
     * The resource kind of the members in the {@link CodeOwnersInChangeCollection} REST collection.
     */
    static final TypeLiteral<RestView<PathResource>> PATH_KIND =
        new TypeLiteral<RestView<PathResource>>() {};

    static PathResource parse(RevisionResource revisionResource, IdString pathId)
        throws BadRequestException {
      return new PathResource(revisionResource, parsePath(pathId));
    }

    private PathResource(RevisionResource revisionResource, Path path) {
      super(revisionResource, path);
    }
  }
}
