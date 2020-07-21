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

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.project.BranchResource;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Abstract REST resource that represents a path in a REST collection under a branch or revision in
 * a change.
 *
 * <p>We have several REST collections under a branch or revision in a change that have paths as
 * members (see {@link CodeOwnerConfigsInBranchCollection}, {@link CodeOwnersInBranchCollection} and
 * {@link CodeOwnersInChangeCollection}). Due to the REST framework the resources in each REST
 * collection must be represented by an own class. To avoid code duplication for the path resources
 * in these different REST collection we have this abstract REST resource for a path that can be
 * extended for each of these REST collections.
 */
abstract class AbstractPathResource implements RestResource {
  protected static Path parsePath(IdString id) throws BadRequestException {
    Path path;
    try {
      path = Paths.get(id.get());
    } catch (InvalidPathException e) {
      throw new BadRequestException("invalid path: " + e.getReason());
    }

    if (!path.isAbsolute()) {
      // we assume that all paths are absolute, add the missing leading '/'
      path = Paths.get("/").resolve(path);
    }
    return path;
  }

  private final BranchNameKey branchNameKey;
  private final ObjectId revision;
  private final Path path;

  protected AbstractPathResource(BranchResource branchResource, Path path) {
    this.branchNameKey = branchResource.getBranchKey();

    checkState(
        branchResource.getRevision() != null,
        "branch %s in project %s wasn't created yet",
        branchResource.getBranchKey().branch(),
        branchResource.getBranchKey().project().get());
    this.revision = ObjectId.fromString(branchResource.getRevision());
    this.path = path;
  }

  protected AbstractPathResource(
      RevisionResource revisionResource, ObjectId branchRevision, Path path) {
    this.branchNameKey = revisionResource.getChange().getDest();
    this.revision = branchRevision;
    this.path = path;
  }

  /**
   * Returns the branch of this path resource.
   *
   * @return the branch of this path resource
   */
  public BranchNameKey getBranch() {
    return branchNameKey;
  }

  /**
   * Returns the revision of the branch.
   *
   * @return the revision of the branch
   */
  public ObjectId getRevision() {
    return revision;
  }

  /**
   * Returns the path of this path resource.
   *
   * <p>The path may or may not exist in the branch.
   *
   * @return the path of this path resource
   */
  public Path getPath() {
    return path;
  }
}
