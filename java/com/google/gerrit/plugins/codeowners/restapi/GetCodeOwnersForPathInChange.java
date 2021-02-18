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

import static com.google.gerrit.plugins.codeowners.backend.CodeOwnerScore.IS_REVIEWER_SCORING_VALUE;
import static com.google.gerrit.plugins.codeowners.backend.CodeOwnerScore.NO_REVIEWER_SCORING_VALUE;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.common.AccountVisibility;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.api.CodeOwnersInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwner;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigHierarchy;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerScore;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerScoring;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerScorings;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.ServiceUserClassifier;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * REST endpoint that gets the code owners for an arbitrary path in a revision of a change.
 *
 * <p>This REST endpoint handles {@code GET
 * /changes/<change-id>/revisions/<revision-id>/code_owners/<path>} requests.
 *
 * <p>The path may or may not exist in the revision of the change.
 */
public class GetCodeOwnersForPathInChange
    extends AbstractGetCodeOwnersForPath<CodeOwnersInChangeCollection.PathResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ServiceUserClassifier serviceUserClassifier;

  @Inject
  GetCodeOwnersForPathInChange(
      AccountVisibility accountVisibility,
      Accounts accounts,
      AccountControl.Factory accountControlFactory,
      PermissionBackend permissionBackend,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy,
      Provider<CodeOwnerResolver> codeOwnerResolver,
      ServiceUserClassifier serviceUserClassifier,
      CodeOwnerJson.Factory codeOwnerJsonFactory) {
    super(
        accountVisibility,
        accounts,
        accountControlFactory,
        permissionBackend,
        codeOwnersPluginConfiguration,
        codeOwnerConfigHierarchy,
        codeOwnerResolver,
        codeOwnerJsonFactory);
    this.serviceUserClassifier = serviceUserClassifier;
  }

  @Override
  public Response<CodeOwnersInfo> apply(CodeOwnersInChangeCollection.PathResource rsrc)
      throws RestApiException, PermissionBackendException {
    return super.applyImpl(rsrc);
  }

  @Override
  protected Optional<Long> getDefaultSeed(CodeOwnersInChangeCollection.PathResource rsrc) {
    // use the change number as seed so that the sort order for a change is always stable
    return Optional.of(Long.valueOf(rsrc.getRevisionResource().getChange().getId().get()));
  }

  /**
   * This method is overridden to add scorings for the {@link CodeOwnerScore#IS_REVIEWER} score that
   * only applies if code owners are suggested on changes.
   */
  @Override
  protected Stream<CodeOwner> sortCodeOwners(
      CodeOwnersInChangeCollection.PathResource rsrc,
      Optional<Long> seed,
      CodeOwnerScorings scorings,
      ImmutableSet<CodeOwner> codeOwners) {
    // Add scorings for IS_REVIEWER score.
    ImmutableSet<Account.Id> reviewers =
        rsrc.getRevisionResource()
            .getNotes()
            .getReviewers()
            .byState(ReviewerStateInternal.REVIEWER);
    CodeOwnerScoring.Builder isReviewerScoring = CodeOwnerScore.IS_REVIEWER.createScoring();
    codeOwners.forEach(
        codeOwner ->
            isReviewerScoring.putValueForCodeOwner(
                codeOwner,
                reviewers.contains(codeOwner.accountId())
                    ? IS_REVIEWER_SCORING_VALUE
                    : NO_REVIEWER_SCORING_VALUE));
    scorings = CodeOwnerScorings.appendScoring(scorings, isReviewerScoring.build());

    return super.sortCodeOwners(rsrc, seed, scorings, codeOwners);
  }

  @Override
  protected Stream<CodeOwner> filterCodeOwners(
      CodeOwnersInChangeCollection.PathResource rsrc, Stream<CodeOwner> codeOwners) {
    return codeOwners.filter(filterOutChangeOwner(rsrc)).filter(filterOutServiceUsers());
  }

  private Predicate<CodeOwner> filterOutChangeOwner(
      CodeOwnersInChangeCollection.PathResource rsrc) {
    return codeOwner -> {
      if (!codeOwner.accountId().equals(rsrc.getRevisionResource().getChange().getOwner())) {
        // Returning true from the Predicate here means that the code owner should be kept.
        return true;
      }
      logger.atFine().log(
          "Filtering out %s because this code owner is the change owner", codeOwner);
      // Returning false from the Predicate here means that the code owner should be filtered out.
      return false;
    };
  }

  private Predicate<CodeOwner> filterOutServiceUsers() {
    return codeOwner -> {
      if (!isServiceUser(codeOwner)) {
        // Returning true from the Predicate here means that the code owner should be kept.
        return true;
      }
      logger.atFine().log("Filtering out %s because this code owner is a service user", codeOwner);
      // Returning false from the Predicate here means that the code owner should be filtered out.
      return false;
    };
  }

  /** Whether the given code owner is a service user. */
  private boolean isServiceUser(CodeOwner codeOwner) {
    return serviceUserClassifier.isServiceUser(codeOwner.accountId());
  }
}
