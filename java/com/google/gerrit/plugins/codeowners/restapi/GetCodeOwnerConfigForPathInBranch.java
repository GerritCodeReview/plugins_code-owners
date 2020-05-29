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
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerConfigInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwners;
import com.google.gerrit.plugins.codeowners.restapi.CodeOwnerConfigsInBranchCollection.PathResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

/**
 * REST endpoint that gets the code owner config for an arbitrary path in a branch.
 *
 * <p>This REST endpoint handles {@code GET
 * /projects/<project-name>/branches/<branch-name>/code_owners.config/<path>} requests.
 *
 * <p>The path may or may not exist in the branch.
 */
@Singleton
public class GetCodeOwnerConfigForPathInBranch
    implements RestReadView<CodeOwnerConfigsInBranchCollection.PathResource> {
  private final CodeOwners codeOwners;

  @Inject
  GetCodeOwnerConfigForPathInBranch(CodeOwners codeOwners) {
    this.codeOwners = codeOwners;
  }

  @Override
  public Response<CodeOwnerConfigInfo> apply(PathResource rsrc) {
    Optional<CodeOwnerConfig> codeOwnerConfig = codeOwners.get(rsrc.getCodeOwnerConfigKey());
    return codeOwnerConfig
        .map(CodeOwnerConfigJson::format)
        .map(Response::ok)
        .orElse(Response.none());
  }
}
