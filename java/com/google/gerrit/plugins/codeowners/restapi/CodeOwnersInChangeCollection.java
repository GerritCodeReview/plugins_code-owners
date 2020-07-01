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
import com.google.gerrit.plugins.codeowners.restapi.CodeOwnersInChangeCollection.PathResource;
import com.google.gerrit.server.change.RevisionResource;
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

  @Inject
  CodeOwnersInChangeCollection(DynamicMap<RestView<PathResource>> views) {
    this.views = views;
  }

  @Override
  public RestView<RevisionResource> list() throws ResourceNotFoundException {
    // Listing paths that have code owners is not implemented.
    throw new ResourceNotFoundException();
  }

  @Override
  public PathResource parse(RevisionResource revisionResource, IdString id)
      throws RestApiException, IOException {
    // TODO(ekempin): Check that the path exists in the revision.
    return PathResource.parse(revisionResource, id);
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
