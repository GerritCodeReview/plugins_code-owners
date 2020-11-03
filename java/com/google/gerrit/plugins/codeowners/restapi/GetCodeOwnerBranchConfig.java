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
import com.google.gerrit.plugins.codeowners.api.CodeOwnerBranchConfigInfo;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.BranchResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

/**
 * REST endpoint that gets the code owner branch configuration.
 *
 * <p>This REST endpoint handles {@code GET
 * /projects/<project-name>/branches/<branch-name>/code_owners.branch_config} requests.
 */
@Singleton
public class GetCodeOwnerBranchConfig implements RestReadView<BranchResource> {
  private final CodeOwnerProjectConfigJson codeOwnerProjectConfigJson;

  @Inject
  public GetCodeOwnerBranchConfig(CodeOwnerProjectConfigJson codeOwnerProjectConfigJson) {
    this.codeOwnerProjectConfigJson = codeOwnerProjectConfigJson;
  }

  @Override
  public Response<CodeOwnerBranchConfigInfo> apply(BranchResource branchResource)
      throws RestApiException, PermissionBackendException, IOException {
    branchResource.getProjectState().checkStatePermitsRead();

    return Response.ok(codeOwnerProjectConfigJson.format(branchResource));
  }
}
