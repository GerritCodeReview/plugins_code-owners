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
import com.google.gerrit.extensions.client.ListAccountsOption;
import com.google.gerrit.extensions.client.ListOption;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwner;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigHierarchy;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.plugins.codeowners.restapi.CodeOwnersInBranchCollection.PathResource;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.kohsuke.args4j.Option;

/**
 * REST endpoint that gets the code owners for an arbitrary path in a branch.
 *
 * <p>This REST endpoint handles {@code GET
 * /projects/<project-name>/branches/<branch-name>/code_owners/<path>} requests.
 *
 * <p>The path may or may not exist in the branch.
 */
public class GetCodeOwnersForPathInBranch
    implements RestReadView<CodeOwnersInBranchCollection.PathResource> {
  private final PermissionBackend permissionBackend;
  private final CodeOwnerConfigHierarchy codeOwnerConfigHierarchy;
  private final CodeOwnerResolver codeOwnerResolver;
  private final CodeOwnerJson.Factory codeOwnerJsonFactory;

  private EnumSet<ListAccountsOption> options;
  private Set<String> hexOptions;

  @Option(
      name = "-o",
      usage = "Options to control which fields should be populated for the returned account")
  public void addOption(ListAccountsOption o) {
    options.add(o);
  }

  @Option(
      name = "-O",
      usage =
          "Options to control which fields should be populated for the returned account, in hex")
  void setOptionFlagsHex(String hex) {
    hexOptions.add(hex);
  }

  @Inject
  GetCodeOwnersForPathInBranch(
      PermissionBackend permissionBackend,
      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy,
      CodeOwnerResolver codeOwnerResolver,
      CodeOwnerJson.Factory codeOwnerJsonFactory) {
    this.permissionBackend = permissionBackend;
    this.codeOwnerConfigHierarchy = codeOwnerConfigHierarchy;
    this.codeOwnerResolver = codeOwnerResolver;
    this.codeOwnerJsonFactory = codeOwnerJsonFactory;
    this.options = EnumSet.noneOf(ListAccountsOption.class);
    this.hexOptions = new HashSet<>();
  }

  @Override
  public Response<List<CodeOwnerInfo>> apply(PathResource rsrc)
      throws AuthException, BadRequestException, PermissionBackendException {
    parseHexOptions();

    Set<CodeOwner> codeOwners = new HashSet<>();
    codeOwnerConfigHierarchy.visit(
        rsrc.getBranch(),
        rsrc.getPath(),
        codeOwnerConfig ->
            codeOwners.addAll(
                codeOwnerResolver.resolveLocalCodeOwners(codeOwnerConfig, rsrc.getPath())));
    return Response.ok(
        codeOwnerJsonFactory
            .create(getFillOptions())
            .format(sortCodeOwnersByAccountId(ImmutableSet.copyOf(codeOwners))));
  }

  private void parseHexOptions() throws BadRequestException {
    for (String hexOption : hexOptions) {
      try {
        options.addAll(
            ListOption.fromBits(ListAccountsOption.class, Integer.parseInt(hexOption, 16)));
      } catch (IllegalArgumentException e) {
        throw new BadRequestException(
            String.format("\"%s\" is not a valid value for \"-O\"", hexOption), e);
      }
    }
  }

  private Set<FillOptions> getFillOptions() throws AuthException, PermissionBackendException {
    Set<FillOptions> fillOptions = EnumSet.of(FillOptions.ID);
    if (options.contains(ListAccountsOption.DETAILS)) {
      fillOptions.addAll(AccountLoader.DETAILED_OPTIONS);
    }
    if (options.contains(ListAccountsOption.ALL_EMAILS)) {
      // Secondary emails are only visible to users that have the 'Modify Account' global
      // capability.
      permissionBackend.currentUser().check(GlobalPermission.MODIFY_ACCOUNT);
      fillOptions.add(FillOptions.EMAIL);
      fillOptions.add(FillOptions.SECONDARY_EMAILS);
    }
    return fillOptions;
  }

  private static ImmutableList<CodeOwner> sortCodeOwnersByAccountId(
      ImmutableSet<CodeOwner> codeOwners) {
    return codeOwners.stream()
        .sorted(Comparator.comparing(CodeOwner::accountId))
        .collect(toImmutableList());
  }
}
