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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.plugins.codeowners.backend.ChangedFiles;
import com.google.gerrit.plugins.codeowners.restapi.CodeOwnersInChangeCollection.PathResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * REST collection that serves requests to {@code
 * /changes/<change-id>/revisions/<revision-id>/code_owners/}.
 */
@Singleton
public class CodeOwnersInChangeCollection
    implements ChildCollection<RevisionResource, PathResource> {
  private final DynamicMap<RestView<PathResource>> views;
  private final GitRepositoryManager repoManager;
  private final ChangedFiles changedFiles;

  @Inject
  CodeOwnersInChangeCollection(
      DynamicMap<RestView<PathResource>> views,
      GitRepositoryManager repoManager,
      ChangedFiles changedFiles) {
    this.views = views;
    this.repoManager = repoManager;
    this.changedFiles = changedFiles;
  }

  @Override
  public RestView<RevisionResource> list() throws ResourceNotFoundException {
    // Listing paths that have code owners is not implemented.
    throw new ResourceNotFoundException();
  }

  @Override
  public PathResource parse(RevisionResource revisionResource, IdString id)
      throws RestApiException, IOException, PatchListNotAvailableException,
          DiffNotAvailableException {
    // Check if the file exists in the revision only after creating the path resource. This way we
    // get a more specific error response for invalid paths ('400 Bad Request' instead of a '404 Not
    // Found').
    PathResource pathResource =
        PathResource.parse(
            revisionResource, getDestBranchRevision(revisionResource.getChange()), id);
    checkThatFileExists(revisionResource, pathResource, id);
    return pathResource;
  }

  /**
   * Gets the current revision of the destination branch of the given change.
   *
   * <p>This is the revision from which the code owner configs should be read when computing code
   * owners for the files that are touched in the change.
   */
  private ObjectId getDestBranchRevision(Change change) throws IOException {
    try (Repository repository = repoManager.openRepository(change.getProject());
        RevWalk rw = new RevWalk(repository)) {
      Ref ref = repository.exactRef(change.getDest().branch());
      checkNotNull(
          ref,
          "branch %s in repository %s not found",
          change.getDest().branch(),
          change.getProject().get());
      return rw.parseCommit(ref.getObjectId());
    }
  }

  private void checkThatFileExists(
      RevisionResource revisionResource, PathResource pathResource, IdString id)
      throws RestApiException, IOException, DiffNotAvailableException {
    if (!changedFiles.getFromDiffCache(revisionResource).stream()
        .anyMatch(
            changedFile ->
                // Check whether the path matches any file in the change.
                changedFile.hasNewPath(pathResource.getPath())
                    // For renamed and deleted files we also accept requests for the old path.
                    // Listing code owners for the old path of renamed/deleted files should be
                    // possible because these files require a code owner approval on the old path
                    // for submit and users need to know whom they need to add as reviewer for this.
                    // For copied files the old path is not modified and hence no code owner
                    // approval for the old path is required. This is why users do not need to get
                    // code owners for the old path in case of copy.
                    || ((changedFile.isRename() || changedFile.isDeletion())
                        && changedFile.hasOldPath(pathResource.getPath())))) {
      // Throw the exception with the path we got as input.
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

    static PathResource parse(
        RevisionResource revisionResource, ObjectId branchRevision, IdString pathId)
        throws BadRequestException {
      return new PathResource(revisionResource, branchRevision, parsePath(pathId));
    }

    private final RevisionResource revisionResource;

    private PathResource(RevisionResource revisionResource, ObjectId branchRevision, Path path) {
      super(revisionResource, branchRevision, path);
      this.revisionResource = revisionResource;
    }

    public RevisionResource getRevisionResource() {
      return revisionResource;
    }
  }
}
