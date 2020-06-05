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
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.plugins.codeowners.restapi.CodeOwnersInBranchCollection.PathResource;
import com.google.gerrit.server.project.BranchResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import java.nio.file.Path;

/**
 * REST collection that serves requests to {@code
 * /projects/<project-name>/branches/<branch-name>/code_owners/}.
 */
@Singleton
public class CodeOwnersInBranchCollection implements ChildCollection<BranchResource, PathResource> {
  private final DynamicMap<RestView<PathResource>> views;

  @Inject
  CodeOwnersInBranchCollection(DynamicMap<RestView<PathResource>> views) {
    this.views = views;
  }

  @Override
  public RestView<BranchResource> list() throws ResourceNotFoundException {
    throw new ResourceNotFoundException();
  }

  @Override
  public PathResource parse(BranchResource branchResource, IdString id) throws BadRequestException {
    return PathResource.parse(branchResource, id);
  }

  @Override
  public DynamicMap<RestView<PathResource>> views() {
    return views;
  }

  /**
   * REST resource representing an arbitrary path in a branch under the {@link
   * CodeOwnersInBranchCollection} REST collection.
   *
   * <p>The path may or may not exist in the branch.
   */
  public static class PathResource extends AbstractPathResource {
    /**
     * The resource kind of the members in the {@link CodeOwnerConfigsInBranchCollection} REST
     * collection.
     */
    static final TypeLiteral<RestView<PathResource>> PATH_KIND =
        new TypeLiteral<RestView<PathResource>>() {};

    static PathResource parse(BranchResource branchResource, IdString pathId)
        throws BadRequestException {
      return new PathResource(branchResource, parsePath(pathId));
    }

    private PathResource(BranchResource branchResource, Path path) {
      super(branchResource, path);
    }
  }
}
