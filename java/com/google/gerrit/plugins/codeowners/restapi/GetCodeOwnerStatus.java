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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerStatusInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerApprovalCheck;
import com.google.gerrit.plugins.codeowners.backend.FileCodeOwnerStatus;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

/**
 * REST endpoint that gets the code owner statuses for the files in a change.
 *
 * <p>This REST endpoint handles {@code GET /changes/<change-id>/code_owners.status} requests.
 *
 * <p>The code owner statuses are always listed for the files in the current revision of the change
 * (latest patch set).
 *
 * <p>This REST endpoint is offered on change level, rather than on revision level because:
 *
 * <ul>
 *   <li>the code owner statuses are based on votes and we always show the current votes on the
 *       change screen even if users view an old revision / patch set (it would be confusing for
 *       users to see the current votes, but non-matching code owner statuses)
 *   <li>the code owner statuses for old revisions are never relevant and may confuse users (e.g.
 *       the code owner status {@code PENDING} means that we are waiting for the code owner approval
 *       of a current reviewer, but the UI doesn’t allow reviewers to vote on non-current revisions,
 *       so that this approval can never happen)
 * </ul>
 */
@Singleton
public class GetCodeOwnerStatus implements RestReadView<ChangeResource> {
  private final CodeOwnerApprovalCheck codeOwnerApprovalCheck;

  @Inject
  public GetCodeOwnerStatus(CodeOwnerApprovalCheck codeOwnerApprovalCheck) {
    this.codeOwnerApprovalCheck = codeOwnerApprovalCheck;
  }

  @Override
  public Response<CodeOwnerStatusInfo> apply(ChangeResource changeResource)
      throws RestApiException, IOException, PermissionBackendException,
          PatchListNotAvailableException, DiffNotAvailableException {
    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(changeResource.getNotes());
    return Response.ok(
        CodeOwnerStatusInfoJson.format(
            changeResource.getNotes().getCurrentPatchSet().id(), fileCodeOwnerStatuses));
  }
}
