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
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerScore;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerScoring;
import com.google.gerrit.plugins.codeowners.restapi.CodeOwnersInBranchCollection.PathResource;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
  private static final int UNLIMITED = 0;

  private final PermissionBackend permissionBackend;
  private final CodeOwnerConfigHierarchy codeOwnerConfigHierarchy;
  private final CodeOwnerResolver codeOwnerResolver;
  private final CodeOwnerJson.Factory codeOwnerJsonFactory;

  private EnumSet<ListAccountsOption> options;
  private Set<String> hexOptions;
  private int limit;
  private int start;

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

  @Option(
      name = "--limit",
      aliases = {"-n"},
      metaVar = "CNT",
      usage = "maximum number of code owners to list (0 = unlimited)")
  public void setLimit(int limit) {
    this.limit = limit;
  }

  @Option(
      name = "--start",
      aliases = {"-S"},
      metaVar = "CNT",
      usage = "Number of code owners to skip")
  public void setStart(int start) {
    this.start = start;
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

    if (limit < 0) {
      throw new BadRequestException("limit cannot be negative");
    }
    if (start < 0) {
      throw new BadRequestException("start cannot be negative");
    }

    // The maximal possible distance. This is the distance that applies to code owners that are
    // defined in the root code owner configuration.
    int maxDistance = rsrc.getPath().getNameCount();

    CodeOwnerScoring.Builder distanceScoring = CodeOwnerScore.DISTANCE.createScoring(maxDistance);

    AtomicInteger skippedCodeOwnersCount = new AtomicInteger(0);
    Set<CodeOwner> codeOwners = new HashSet<>();
    codeOwnerConfigHierarchy.visit(
        rsrc.getBranch(),
        rsrc.getPath(),
        codeOwnerConfig -> {
          ImmutableSet<CodeOwner> localCodeOwners =
              codeOwnerResolver.resolveLocalCodeOwners(codeOwnerConfig, rsrc.getPath());
          int distance = maxDistance - codeOwnerConfig.key().folderPath().getNameCount();
          localCodeOwners.forEach(
              localCodeOwner -> {
                if (skippedCodeOwnersCount.get() < start) {
                  skippedCodeOwnersCount.incrementAndGet();
                } else {
                  codeOwners.add(localCodeOwner);
                  distanceScoring.putValueForCodeOwner(localCodeOwner, distance);
                }
              });
          if (limit != UNLIMITED && codeOwners.size() >= limit + 1) {
            // We have gathered enough code owners and do not need to look at further code owner
            // configs.
            // We can abort here, since all further code owners will have a lower distance scoring
            // and hence they would appear at the end of the sorted code owners list and be dropped
            // due to the limit.
            return false;
          }
          return true;
        });

    boolean moreCodeOwners = limit != UNLIMITED && codeOwners.size() > limit;
    return Response.ok(
        codeOwnerJsonFactory
            .create(getFillOptions())
            .format(
                limit(sortCodeOwners(distanceScoring.build(), ImmutableSet.copyOf(codeOwners))),
                moreCodeOwners));
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

  private ImmutableList<CodeOwner> limit(ImmutableList<CodeOwner> codeOwners) {
    if (limit == UNLIMITED) {
      return codeOwners;
    }
    return codeOwners.stream().limit(limit).collect(toImmutableList());
  }

  /**
   * Sorts the code owners.
   *
   * <p>Code owners with higher distance score are returned first.
   *
   * <p>The order of code owners with the same distance score is random.
   *
   * @param distanceScoring the distance scorings for the code owners
   * @param codeOwners the code owners that should be sorted
   * @return the sorted code owners
   */
  private static ImmutableList<CodeOwner> sortCodeOwners(
      CodeOwnerScoring distanceScoring, ImmutableSet<CodeOwner> codeOwners) {
    ImmutableList<CodeOwner> randomlyOrderedCodeOwners = randomizeOrder(codeOwners);
    return ImmutableList.sortedCopyOf(
        distanceScoring.comparingByScoring().thenComparing(randomlyOrderedCodeOwners::indexOf),
        codeOwners);
  }

  /**
   * Returns the given code owners in a random order.
   *
   * @param codeOwners the code owners that should be returned in a random order
   * @return the given code owners in a random order
   */
  private static ImmutableList<CodeOwner> randomizeOrder(ImmutableSet<CodeOwner> codeOwners) {
    List<CodeOwner> randomlyOrderedCodeOwners = new ArrayList<>();
    randomlyOrderedCodeOwners.addAll(codeOwners);
    Collections.shuffle(randomlyOrderedCodeOwners);
    return ImmutableList.copyOf(randomlyOrderedCodeOwners);
  }
}
