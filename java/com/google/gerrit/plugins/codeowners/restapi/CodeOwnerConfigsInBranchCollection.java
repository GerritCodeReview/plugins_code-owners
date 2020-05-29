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

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.restapi.CodeOwnerConfigsInBranchCollection.PathResource;
import com.google.gerrit.server.project.BranchResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * REST collection that serves requests to {@code
 * /projects/<project-name>/branches/<branch-name>/code_owners.config/}.
 */
@Singleton
public class CodeOwnerConfigsInBranchCollection
    implements ChildCollection<BranchResource, PathResource> {
  private final DynamicMap<RestView<PathResource>> views;

  @Inject
  CodeOwnerConfigsInBranchCollection(DynamicMap<RestView<PathResource>> views) {
    this.views = views;
  }

  @Override
  public RestView<BranchResource> list() throws ResourceNotFoundException {
    throw new ResourceNotFoundException();
  }

  @Override
  public PathResource parse(BranchResource branchResource, IdString id) throws BadRequestException {
    // We accept arbitrary paths, including non-existing paths.
    Path path = parsePath(id);
    if (!path.isAbsolute()) {
      // we assume that all paths are absolute, add the missing leading '/'
      path = Paths.get("/").resolve(path);
    }

    return new PathResource(branchResource, path);
  }

  private Path parsePath(IdString id) throws BadRequestException {
    try {
      return Paths.get(id.get());
    } catch (InvalidPathException e) {
      throw new BadRequestException("invalid path: " + e.getReason());
    }
  }

  @Override
  public DynamicMap<RestView<PathResource>> views() {
    return views;
  }

  /**
   * REST resource representing an arbitrary path in a branch under the {@link
   * CodeOwnerConfigsInBranchCollection} REST collection.
   *
   * <p>The path may or may not exist in the branch.
   */
  public static class PathResource implements RestResource {
    /**
     * The resource kind of the members in the {@link CodeOwnerConfigsInBranchCollection} REST
     * collection.
     */
    static final TypeLiteral<RestView<PathResource>> PATH_KIND =
        new TypeLiteral<RestView<PathResource>>() {};

    private final BranchResource branchResource;
    private final Path path;

    public PathResource(BranchResource branchResource, Path path) {
      this.branchResource = branchResource;
      this.path = path;
    }

    /**
     * Returns the branch of this path resource.
     *
     * @return the branch of this path resource
     */
    public BranchNameKey getBranch() {
      return branchResource.getBranchKey();
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

    public CodeOwnerConfig.Key getCodeOwnerConfigKey() {
      return CodeOwnerConfig.Key.create(getBranch(), path);
    }
  }
}
