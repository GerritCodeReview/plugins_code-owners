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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwner;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigHierarchy;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.plugins.codeowners.restapi.CodeOwnersInBranchCollection.PathResource;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * REST endpoint that gets the code owners for an arbitrary path in a branch.
 *
 * <p>This REST endpoint handles {@code GET
 * /projects/<project-name>/branches/<branch-name>/code_owners/<path>} requests.
 *
 * <p>The path may or may not exist in the branch.
 */
@Singleton
public class GetCodeOwnersForPathInBranch
    implements RestReadView<CodeOwnersInBranchCollection.PathResource> {
  private final CodeOwnerConfigHierarchy codeOwnerConfigHierarchy;
  private final CodeOwnerResolver codeOwnerResolver;
  private final CodeOwnerJson.Factory codeOwnerJsonFactory;

  @Inject
  GetCodeOwnersForPathInBranch(
      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy,
      CodeOwnerResolver codeOwnerResolver,
      CodeOwnerJson.Factory codeOwnerJsonFactory) {
    this.codeOwnerConfigHierarchy = codeOwnerConfigHierarchy;
    this.codeOwnerResolver = codeOwnerResolver;
    this.codeOwnerJsonFactory = codeOwnerJsonFactory;
  }

  @Override
  public Response<List<CodeOwnerInfo>> apply(PathResource rsrc) throws PermissionBackendException {
    Set<CodeOwner> codeOwners = new HashSet<>();
    codeOwnerConfigHierarchy.visit(
        rsrc.getBranch(),
        rsrc.getPath(),
        codeOwnerConfig ->
            codeOwnerConfig
                .localCodeOwners(codeOwnerConfig.relativize(rsrc.getPath()))
                .forEach(
                    codeOwnerReference ->
                        codeOwnerResolver.resolve(codeOwnerReference).ifPresent(codeOwners::add)));
    return Response.ok(
        codeOwnerJsonFactory
            .create(EnumSet.of(FillOptions.ID))
            .format(sortCodeOwnersByAccountId(ImmutableSet.copyOf(codeOwners))));
  }

  private static ImmutableList<CodeOwner> sortCodeOwnersByAccountId(
      ImmutableSet<CodeOwner> codeOwners) {
    return codeOwners.stream()
        .sorted(Comparator.comparing(CodeOwner::accountId))
        .collect(toImmutableList());
  }
}
