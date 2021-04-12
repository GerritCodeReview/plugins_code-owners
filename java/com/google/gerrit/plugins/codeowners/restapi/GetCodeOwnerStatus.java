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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.restapi.BadRequestException;
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
import java.io.IOException;
import org.kohsuke.args4j.Option;

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
public class GetCodeOwnerStatus implements RestReadView<ChangeResource> {
  private static final int UNLIMITED = 0;

  private final CodeOwnerApprovalCheck codeOwnerApprovalCheck;

  private int start;
  private int limit;

  @Option(
      name = "--limit",
      aliases = {"-n"},
      metaVar = "CNT",
      usage = "maximum number of file code owner statuses to return (by default 0 aka unlimited)")
  public void setLimit(int limit) {
    this.limit = limit;
  }

  @Option(
      name = "--start",
      aliases = {"-S"},
      metaVar = "CNT",
      usage = "number of owned paths to skip")
  public void setStart(int start) {
    this.start = start;
  }

  @Inject
  public GetCodeOwnerStatus(CodeOwnerApprovalCheck codeOwnerApprovalCheck) {
    this.codeOwnerApprovalCheck = codeOwnerApprovalCheck;
  }

  @Override
  public Response<CodeOwnerStatusInfo> apply(ChangeResource changeResource)
      throws RestApiException, IOException, PermissionBackendException,
          PatchListNotAvailableException, DiffNotAvailableException {
    validateStartAndLimit();

    ImmutableSet<FileCodeOwnerStatus> fileCodeOwnerStatuses =
        codeOwnerApprovalCheck.getFileStatusesAsSet(
            changeResource.getNotes(), start, limit == UNLIMITED ? UNLIMITED : limit + 1);
    CodeOwnerStatusInfo codeOwnerStatusInfo =
        CodeOwnerStatusInfoJson.format(
            changeResource.getNotes().getCurrentPatchSet().id(),
            limit == UNLIMITED
                ? fileCodeOwnerStatuses
                : fileCodeOwnerStatuses.stream().limit(limit).collect(toImmutableSet()));
    codeOwnerStatusInfo.more =
        limit != UNLIMITED && fileCodeOwnerStatuses.size() > limit ? true : null;
    return Response.ok(codeOwnerStatusInfo);
  }

  private void validateStartAndLimit() throws BadRequestException {
    if (start < 0) {
      throw new BadRequestException("start cannot be negative");
    }
    if (limit < 0) {
      throw new BadRequestException("limit cannot be negative");
    }
  }
}
