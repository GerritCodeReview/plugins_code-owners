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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.plugins.codeowners.api.OwnedChangedFileInfo;
import com.google.gerrit.plugins.codeowners.api.OwnedPathInfo;
import com.google.gerrit.plugins.codeowners.api.OwnedPathsInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerApprovalCheck;
import com.google.gerrit.plugins.codeowners.backend.OwnedChangedFile;
import com.google.gerrit.plugins.codeowners.backend.OwnedPath;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountResolver.UnresolvableAccountException;
import com.google.gerrit.server.change.RevisionResource;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.kohsuke.args4j.Option;

/**
 * REST endpoint that lists the files of a revision that are owned by a specified user.
 *
 * <p>This REST endpoint handles {@code GET
 * /changes/<change-id>/revisions/<revision-id>/owned_paths} requests.
 */
public class GetOwnedPaths implements RestReadView<RevisionResource> {
  @VisibleForTesting public static final int DEFAULT_LIMIT = 50;
  private final AccountResolver accountResolver;
  private final CodeOwnerApprovalCheck codeOwnerApprovalCheck;

  private int start;
  private int limit = DEFAULT_LIMIT;
  private String user;
  private boolean checkReviewers;

  @Option(
      name = "--check_reviewers",
      usage = "whether or not to also compute which reviewers are owners")
  public void setCheckReviewers(boolean checkReviewers) {
    this.checkReviewers = checkReviewers;
  }

  @Option(
      name = "--limit",
      aliases = {"-n"},
      metaVar = "CNT",
      usage = "maximum number of owned path to return (default = " + DEFAULT_LIMIT + ")")
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
    validateStartAndLimit();

    Account.Id accountId = resolveAccount();

    ImmutableList<OwnedChangedFile> ownedChangedFiles =
        codeOwnerApprovalCheck.getOwnedPaths(
            revisionResource.getNotes(),
            revisionResource.getPatchSet(),
            accountId,
            start,
            limit + 1,
            checkReviewers);

    OwnedPathsInfo ownedPathsInfo = new OwnedPathsInfo();
    ownedPathsInfo.more = ownedChangedFiles.size() > limit ? true : null;
    ownedPathsInfo.ownedChangedFiles =
        ownedChangedFiles.stream()
            .limit(limit)
            .map(GetOwnedPaths::toOwnedChangedFileInfo)
            .collect(toImmutableList());
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

  private void validateStartAndLimit() throws BadRequestException {
    if (start < 0) {
      throw new BadRequestException("start cannot be negative");
    }
    if (limit <= 0) {
      throw new BadRequestException("limit must be positive");
    }
  }

  private static OwnedChangedFileInfo toOwnedChangedFileInfo(OwnedChangedFile ownedChangedFile) {
    OwnedChangedFileInfo info = new OwnedChangedFileInfo();
    if (ownedChangedFile.newPath().isPresent()) {
      info.newPath = toOwnedPathInfo(ownedChangedFile.newPath().get());
    }
    if (ownedChangedFile.oldPath().isPresent()) {
      info.oldPath = toOwnedPathInfo(ownedChangedFile.oldPath().get());
    }
    return info;
  }

  private static OwnedPathInfo toOwnedPathInfo(OwnedPath ownedPath) {
    OwnedPathInfo info = new OwnedPathInfo();
    info.path = ownedPath.path().toString();
    info.owned = ownedPath.owned() ? true : null;
    info.owners =
        Streams.stream(ownedPath.owners())
            .map(GetOwnedPaths::toAccountInfo)
            .collect(toImmutableList());

    return info;
  }

  private static AccountInfo toAccountInfo(Account.Id accountId) {
    return new AccountInfo(accountId.get());
  }
}
