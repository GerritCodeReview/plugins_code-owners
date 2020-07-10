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

import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigHierarchy;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.List;

/**
 * REST endpoint that gets the code owners for an arbitrary path in a revision of a change.
 *
 * <p>This REST endpoint handles {@code GET
 * /changes/<change-id>/revisions/<revision-id>/code_owners/<path>} requests.
 *
 * <p>The path may or may not exist in the revision of the change.
 */
public class GetCodeOwnersForPathInChange extends AbstractGetCodeOwnersForPath
    implements RestReadView<CodeOwnersInChangeCollection.PathResource> {
  @Inject
  GetCodeOwnersForPathInChange(
      PermissionBackend permissionBackend,
      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy,
      Provider<CodeOwnerResolver> codeOwnerResolver,
      CodeOwnerJson.Factory codeOwnerJsonFactory) {
    super(permissionBackend, codeOwnerConfigHierarchy, codeOwnerResolver, codeOwnerJsonFactory);
  }

  @Override
  public Response<List<CodeOwnerInfo>> apply(CodeOwnersInChangeCollection.PathResource rsrc)
      throws RestApiException, PermissionBackendException {
    return super.applyImpl(rsrc);
  }
}
