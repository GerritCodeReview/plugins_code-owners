// Copyright (C) 2021 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.plugins.codeowners.api.OwnedPathsInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerApprovalCheck;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountResolver.UnresolvableAccountException;
import com.google.gerrit.server.change.RevisionResource;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.kohsuke.args4j.Option;

/**
 * REST endpoint that lists the files of a revision that are owned by a specified user.
 *
 * <p>This REST endpoint handles {@code GET
 * /changes/<change-id>/revisions/<revision-id>/owned_paths} requests.
 */
public class GetOwnedPaths implements RestReadView<RevisionResource> {
  private final AccountResolver accountResolver;
  private final CodeOwnerApprovalCheck codeOwnerApprovalCheck;

  private String user;

  @Option(name = "--user", usage = "user for which the owned paths should be returned")
  public void setUser(String user) {
    this.user = user;
  }

  @Inject
  public GetOwnedPaths(
      AccountResolver accountResolver, CodeOwnerApprovalCheck codeOwnerApprovalCheck) {
    this.accountResolver = accountResolver;
    this.codeOwnerApprovalCheck = codeOwnerApprovalCheck;
  }

  @Override
  public Response<OwnedPathsInfo> apply(RevisionResource revisionResource)
      throws BadRequestException, ResourceConflictException, UnresolvableAccountException,
          ConfigInvalidException, IOException {
    Account.Id accountId = resolveAccount();

    ImmutableList<Path> ownedPaths =
        codeOwnerApprovalCheck.getOwnedPaths(
            revisionResource.getNotes(), revisionResource.getPatchSet(), accountId, /* limit= */ 0);

    OwnedPathsInfo ownedPathsInfo = new OwnedPathsInfo();
    ownedPathsInfo.ownedPaths = ownedPaths.stream().map(Path::toString).collect(toImmutableList());
    return Response.ok(ownedPathsInfo);
  }

  private Account.Id resolveAccount()
      throws BadRequestException, UnresolvableAccountException, ConfigInvalidException,
          IOException {
    if (Strings.isNullOrEmpty(user)) {
      throw new BadRequestException("--user required");
    }

    return accountResolver.resolve(user).asUnique().account().id();
  }
}
