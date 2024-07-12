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
import static com.google.gerrit.plugins.codeowners.backend.CodeOwnerScore.IS_REVIEWER_SCORING_VALUE;
import static com.google.gerrit.plugins.codeowners.backend.CodeOwnerScore.NO_REVIEWER_SCORING_VALUE;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.common.AccountVisibility;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.api.CodeOwnersInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwner;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerAnnotation;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerAnnotations;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigHierarchy;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerScore;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerScoring;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.ServiceUserClassifier;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.change.SuggestReviewers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Config;

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

  private final Config cfg;
  private final ServiceUserClassifier serviceUserClassifier;

  @Inject
  GetCodeOwnersForPathInChange(
      @GerritServerConfig Config cfg,
      AccountVisibility accountVisibility,
      Accounts accounts,
      AccountControl.Factory accountControlFactory,
      PermissionBackend permissionBackend,
      CheckCodeOwnerCapability checkCodeOwnerCapability,
      CodeOwnerMetrics codeOwnerMetrics,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy,
      Provider<CodeOwnerResolver> codeOwnerResolver,
      ServiceUserClassifier serviceUserClassifier,
      CodeOwnerJson.Factory codeOwnerJsonFactory,
      CodeOwnerConfigFileJson codeOwnerConfigFileJson) {
    super(
        accountVisibility,
        accounts,
        accountControlFactory,
        permissionBackend,
        checkCodeOwnerCapability,
        codeOwnerMetrics,
        codeOwnersPluginConfiguration,
        codeOwnerConfigHierarchy,
        codeOwnerResolver,
        codeOwnerJsonFactory,
        codeOwnerConfigFileJson);
    this.cfg = cfg;
    this.serviceUserClassifier = serviceUserClassifier;
  }

  @Override
  public Response<CodeOwnersInfo> apply(CodeOwnersInChangeCollection.PathResource rsrc)
      throws RestApiException, PermissionBackendException {
    return super.applyImpl(rsrc);
  }

  @Override
  protected Optional<Long> getDefaultSeed(CodeOwnersInChangeCollection.PathResource rsrc) {
    // We are using a hash of the change number as a seed so that the sort order for a change is
    // always stable.
    // Using a hash of the change number instead of the change number itself ensures that the seeds
    // for changes in a change series are very distant. This is important because java.util.Random
    // is prone to produce the same random numbers for seeds that are nearby.
    return Optional.of(
        Hashing.sha256().hashInt(rsrc.getRevisionResource().getChange().getId().get()).asLong());
  }

  /**
   * This method is overridden to add scorings for the {@link CodeOwnerScore#IS_REVIEWER} score that
   * only applies if code owners are suggested on changes.
   */
  @Override
  protected ImmutableSet<CodeOwnerScoring> getCodeOwnerScorings(
      CodeOwnersInChangeCollection.PathResource rsrc, ImmutableSet<CodeOwner> codeOwners) {
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

    return ImmutableSet.of(isReviewerScoring.build());
  }

  @Override
  protected Stream<CodeOwner> filterCodeOwners(
      CodeOwnersInChangeCollection.PathResource rsrc,
      ImmutableMultimap<CodeOwner, CodeOwnerAnnotation> annotations,
      Stream<CodeOwner> codeOwners,
      ImmutableList.Builder<String> debugLogs) {

    // The change owner and service users should never be suggested, hence filter them out.
    ImmutableList<CodeOwner> filteredCodeOwners =
        codeOwners
            .filter(filterOutChangeOwner(rsrc, debugLogs))
            .filter(filterOutServiceUsers(debugLogs))
            .collect(toImmutableList());

    // Code owners that are annotated with #{LAST_RESORT_SUGGESTION} should be dropped from the
    // suggestion, but only if it doesn't make the result empty. In other words this means that
    // those code owners should be suggested if there are no other code owners.
    ImmutableList<CodeOwner>
        filteredCodeOwnersWithoutCodeOwnersThatAreAnnotatedWithLastResortSuggestion =
            filteredCodeOwners.stream()
                .filter(
                    filterOutCodeOwnersThatAreAnnotatedWithLastResortSuggestion(
                        rsrc, annotations, debugLogs))
                .collect(toImmutableList());
    if (filteredCodeOwnersWithoutCodeOwnersThatAreAnnotatedWithLastResortSuggestion.isEmpty()) {
      // The result would be empty, hence return code owners that are annotated with
      // #{LAST_RESORT_SUGGESTION}.
      return filteredCodeOwners.stream();
    }
    return filteredCodeOwnersWithoutCodeOwnersThatAreAnnotatedWithLastResortSuggestion.stream();
  }

  private Predicate<CodeOwner> filterOutChangeOwner(
      CodeOwnersInChangeCollection.PathResource rsrc, ImmutableList.Builder<String> debugLogs) {
    return codeOwner -> {
      if (!codeOwner.accountId().equals(rsrc.getRevisionResource().getChange().getOwner())) {
        // Returning true from the Predicate here means that the code owner should be kept.
        return true;
      }
      debugLogs.add(
          String.format("filtering out %s because this code owner is the change owner", codeOwner));
      // Returning false from the Predicate here means that the code owner should be filtered out.
      return false;
    };
  }

  private Predicate<CodeOwner> filterOutCodeOwnersThatAreAnnotatedWithLastResortSuggestion(
      CodeOwnersInChangeCollection.PathResource rsrc,
      ImmutableMultimap<CodeOwner, CodeOwnerAnnotation> annotations,
      ImmutableList.Builder<String> debugLogs) {
    return codeOwner -> {
      boolean lastResortSuggestion =
          annotations.containsEntry(
              codeOwner, CodeOwnerAnnotations.LAST_RESORT_SUGGESTION_ANNOTATION);

      // If the code owner is already a reviewer, the code owner should always be suggested, even
      // if annotated with LAST_RESORT_SUGGESTION_ANNOTATION.
      if (isReviewer(rsrc, codeOwner)) {
        if (lastResortSuggestion) {
          debugLogs.add(
              String.format(
                  "ignoring %s annotation for %s because this code owner is a reviewer",
                  CodeOwnerAnnotations.LAST_RESORT_SUGGESTION_ANNOTATION.key(), codeOwner));
        }

        // Returning true from the Predicate here means that the code owner should be kept.
        return true;
      }
      if (!lastResortSuggestion) {
        // Returning true from the Predicate here means that the code owner should be kept.
        return true;
      }
      debugLogs.add(
          String.format(
              "filtering out %s because this code owner is annotated with %s",
              codeOwner, CodeOwnerAnnotations.LAST_RESORT_SUGGESTION_ANNOTATION.key()));
      // Returning false from the Predicate here means that the code owner should be filtered out.
      return false;
    };
  }

  private boolean isReviewer(CodeOwnersInChangeCollection.PathResource rsrc, CodeOwner codeOwner) {
    return rsrc.getRevisionResource()
        .getNotes()
        .getReviewers()
        .byState(ReviewerStateInternal.REVIEWER)
        .contains(codeOwner.accountId());
  }

  private Predicate<CodeOwner> filterOutServiceUsers(ImmutableList.Builder<String> debugLogs) {
    if (!cfg.getBoolean(
        "suggest", "skipServiceUsers", SuggestReviewers.DEFAULT_SKIP_SERVICE_USERS)) {
      // Returning true from the Predicate here means that the code owner should not be filtered
      // out.
      return codeOwner -> true;
    }

    return codeOwner -> {
      if (!isServiceUser(codeOwner)) {
        // Returning true from the Predicate here means that the code owner should be kept.
        return true;
      }
      debugLogs.add(
          String.format("filtering out %s because this code owner is a service user", codeOwner));
      // Returning false from the Predicate here means that the code owner should be filtered out.
      return false;
    };
  }

  /** Whether the given code owner is a service user. */
  private boolean isServiceUser(CodeOwner codeOwner) {
    return serviceUserClassifier.isServiceUser(codeOwner.accountId());
  }
}
