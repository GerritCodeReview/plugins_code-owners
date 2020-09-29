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
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.ListAccountsOption;
import com.google.gerrit.extensions.client.ListOption;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwner;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigHierarchy;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerScore;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerScoring;
import com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.ServiceUserClassifier;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.kohsuke.args4j.Option;

/**
 * Abstract base class for REST endpoints that get the code owners for an arbitrary path in a branch
 * or a revision of a change.
 */
public abstract class AbstractGetCodeOwnersForPath {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting public static final int DEFAULT_LIMIT = 10;

  private final PermissionBackend permissionBackend;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final CodeOwnerConfigHierarchy codeOwnerConfigHierarchy;
  private final Provider<CodeOwnerResolver> codeOwnerResolver;
  private final ServiceUserClassifier serviceUserClassifier;
  private final CodeOwnerJson.Factory codeOwnerJsonFactory;
  private final EnumSet<ListAccountsOption> options;
  private final Set<String> hexOptions;

  private int limit = DEFAULT_LIMIT;

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
      usage = "maximum number of code owners to list (default = " + DEFAULT_LIMIT + ")")
  public void setLimit(int limit) {
    this.limit = limit;
  }

  protected AbstractGetCodeOwnersForPath(
      PermissionBackend permissionBackend,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy,
      Provider<CodeOwnerResolver> codeOwnerResolver,
      ServiceUserClassifier serviceUserClassifier,
      CodeOwnerJson.Factory codeOwnerJsonFactory) {
    this.permissionBackend = permissionBackend;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.codeOwnerConfigHierarchy = codeOwnerConfigHierarchy;
    this.codeOwnerResolver = codeOwnerResolver;
    this.serviceUserClassifier = serviceUserClassifier;
    this.codeOwnerJsonFactory = codeOwnerJsonFactory;
    this.options = EnumSet.noneOf(ListAccountsOption.class);
    this.hexOptions = new HashSet<>();
  }

  protected Response<List<CodeOwnerInfo>> applyImpl(AbstractPathResource rsrc)
      throws AuthException, BadRequestException, PermissionBackendException {
    parseHexOptions();
    validateLimit();

    // The distance that applies to code owners that are defined in the root code owner
    // configuration.
    int rootDistance = rsrc.getPath().getNameCount();

    // The maximal possible distance. This is the distance that applies to global code owners and is
    // by 1 greater than the distance that applies to code owners that are defined in the root code
    // owner configuration.
    int maxDistance = rootDistance + 1;

    CodeOwnerScoring.Builder distanceScoring = CodeOwnerScore.DISTANCE.createScoring(maxDistance);

    Set<CodeOwner> codeOwners = new HashSet<>();
    codeOwnerConfigHierarchy.visit(
        rsrc.getBranch(),
        rsrc.getRevision(),
        rsrc.getPath(),
        codeOwnerConfig -> {
          ImmutableSet<CodeOwner> pathCodeOwners =
              codeOwnerResolver.get().resolvePathCodeOwners(codeOwnerConfig, rsrc.getPath());
          codeOwners.addAll(filterCodeOwners(rsrc, pathCodeOwners));
          int distance = rootDistance - codeOwnerConfig.key().folderPath().getNameCount();
          pathCodeOwners.forEach(
              localCodeOwner -> distanceScoring.putValueForCodeOwner(localCodeOwner, distance));

          // If codeOwners.size() >= limit we have gathered enough code owners and do not need to
          // look at further code owner configs.
          // We can abort here, since all further code owners will have a lower distance scoring
          // and hence they would appear at the end of the sorted code owners list and be dropped
          // due to the limit.
          return codeOwners.size() < limit;
        });

    if (codeOwners.size() < limit) {
      ImmutableSet<CodeOwner> globalCodeOwners = getGlobalCodeOwners(rsrc.getBranch().project());
      globalCodeOwners.forEach(
          codeOwner -> distanceScoring.putValueForCodeOwner(codeOwner, maxDistance));
      codeOwners.addAll(filterCodeOwners(rsrc, globalCodeOwners));
    }

    return Response.ok(
        codeOwnerJsonFactory
            .create(getFillOptions())
            .format(sortAndLimit(distanceScoring.build(), ImmutableSet.copyOf(codeOwners))));
  }

  private ImmutableSet<CodeOwner> getGlobalCodeOwners(Project.NameKey projectName) {
    ImmutableSet<CodeOwner> globalCodeOwners =
        codeOwnerResolver
            .get()
            .resolve(codeOwnersPluginConfiguration.getGlobalCodeOwners(projectName))
            .collect(toImmutableSet());
    logger.atFine().log("including global code owners = %s", globalCodeOwners);
    return globalCodeOwners;
  }

  /**
   * Filters out code owners that should not be suggested.
   *
   * <p>The following code owners are filtered out:
   *
   * <ul>
   *   <li>code owners that cannot see the branch: Code owners that cannot see the branch cannot
   *       approve paths in this branch. Hence returning them to the client is not useful.
   *   <li>code owners that are service users: Requesting a code owner approval from a service user
   *       normally doesn't make sense since they will not react to review requests.
   * </ul>
   */
  private ImmutableSet<CodeOwner> filterCodeOwners(
      AbstractPathResource rsrc, ImmutableSet<CodeOwner> codeOwners) {
    return codeOwners.stream()
        .filter(
            codeOwner -> {
              if (isVisibleTo(rsrc, codeOwner)) {
                return true;
              }
              logger.atFine().log(
                  "Filtering out %s because this code owner cannot see the branch %s",
                  codeOwner, rsrc.getBranch().branch());
              return false;
            })
        .filter(
            codeOwner -> {
              if (!isServiceUser(codeOwner)) {
                return true;
              }
              logger.atFine().log(
                  "Filtering out %s because this code owner is a service user", codeOwner);
              return false;
            })
        .collect(toImmutableSet());
  }

  /** Whether the given resource is visible to the given code owner. */
  private boolean isVisibleTo(AbstractPathResource rsrc, CodeOwner codeOwner) {
    // We always check for the visibility of the branch.
    // This is also correct for the GetCodeOwnersForPathInChange subclass where branch is the
    // destination branch of the change. For changes the intention of the visibility check is to
    // check whether the code owner could be added as reviewer to the change. For this it is
    // important whether the destination branch is visible to the code owner, rather than whether
    // the change is visible to the code owner. E.g. private changes are not visible to other users
    // unless they are added as a reviewer. This means, for private changes we want to suggest code
    // owners that cannot see the change, since adding them as a reviewer is possible. By adding the
    // code owner as a reviewer to the private change, the change becomes visible to them. This
    // behavior is consistent with the suggest reviewer implementation (see
    // SuggestChangeReviewers#getVisibility(ChangeControl).
    return permissionBackend
        .absentUser(codeOwner.accountId())
        .ref(rsrc.getBranch())
        .testOrFalse(RefPermission.READ);
  }

  /** Whether the given code owner is a service user. */
  private boolean isServiceUser(CodeOwner codeOwner) {
    return serviceUserClassifier.isServiceUser(codeOwner.accountId());
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

  private void validateLimit() throws BadRequestException {
    if (limit <= 0) {
      throw new BadRequestException("limit must be positive");
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

  private ImmutableList<CodeOwner> sortAndLimit(
      CodeOwnerScoring distanceScoring, ImmutableSet<CodeOwner> codeOwners) {
    return sortCodeOwners(distanceScoring, codeOwners).limit(limit).collect(toImmutableList());
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
  private static Stream<CodeOwner> sortCodeOwners(
      CodeOwnerScoring distanceScoring, ImmutableSet<CodeOwner> codeOwners) {
    return randomizeOrder(codeOwners).sorted(distanceScoring.comparingByScoring());
  }

  /**
   * Returns the given code owners in a random order.
   *
   * @param codeOwners the code owners that should be returned in a random order
   * @return the given code owners in a random order
   */
  private static Stream<CodeOwner> randomizeOrder(ImmutableSet<CodeOwner> codeOwners) {
    List<CodeOwner> randomlyOrderedCodeOwners = new ArrayList<>(codeOwners);
    Collections.shuffle(randomlyOrderedCodeOwners);
    return randomlyOrderedCodeOwners.stream();
  }
}
