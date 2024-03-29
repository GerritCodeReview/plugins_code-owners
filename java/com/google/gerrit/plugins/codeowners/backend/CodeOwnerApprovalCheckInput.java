// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.plugins.codeowners.backend;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.ImmutableSetMultimap.toImmutableSetMultimap;
import static java.util.Comparator.comparing;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginProjectConfigSnapshot;
import com.google.gerrit.plugins.codeowners.backend.config.RequiredApproval;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.util.LabelVote;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Input for {@link CodeOwnerApprovalCheck}.
 *
 * <p>Provides all data that is needed to check the code owner approvals on a change.
 */
@AutoValue
public abstract class CodeOwnerApprovalCheckInput {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Gets the IDs of the accounts of all reviewers that can possibly code owner approve the change
   * (if they are code owners).
   *
   * <p>If self approvals are ignored the patch set uploader is filtered out since in this case the
   * patch set uploader cannot approve the change even if they are a code owner.
   */
  public abstract ImmutableSet<Account.Id> reviewers();

  /**
   * Gets the IDs of the accounts that have an approval on the current patch set that possibly
   * counts as code owner approval (if they are code owners).
   *
   * <p>If self approvals are ignored the patch set uploader is filtered out since in this case the
   * approval of the patch set uploader is ignored even if they are a code owner.
   */
  public abstract ImmutableSet<Account.Id> approvers();

  /**
   * Gets a map of previous patch sets to the IDs of the accounts that have an approval on that
   * patch set that is sticky and possibly counts as code owner approval (if they are code owners).
   *
   * <p>If self approvals are ignored the patch set uploader is filtered out for all patch sets
   * since in this case the approval of the patch set uploader is ignored even if they are a code
   * owner.
   */
  public abstract ImmutableMultimap<PatchSet.Id, Account.Id> approversFromPreviousPatchSets();

  @Memoized
  public ImmutableSortedSet<PatchSet.Id> previouslyApprovedPatchSetsInReverseOrder() {
    return ImmutableSortedSet.orderedBy(comparing(PatchSet.Id::get).reversed())
        .addAll(approversFromPreviousPatchSets().keySet())
        .build();
  }

  /**
   * Account from which an implicit code owner approval should be assumed.
   *
   * @see CodeOwnersPluginProjectConfigSnapshot#areImplicitApprovalsEnabled()
   * @return the account of the change owner if implicit approvals are enabled, otherwise {@link
   *     Optional#empty()}
   */
  public abstract Optional<Account.Id> implicitApprover();

  /**
   * Gets the approvals from the current patch set that count as code owner overrides.
   *
   * <p>If self approvals are ignored an override of the patch set uploader is filtered out since it
   * doesn't count as code owner override.
   */
  public abstract ImmutableSet<PatchSetApproval> overrides();

  /** Gets the configured global code owners. */
  public abstract CodeOwnerResolverResult globalCodeOwners();

  /** Gets the policy that defines who owns paths for which no code owners are defined. */
  public abstract FallbackCodeOwners fallbackCodeOwners();

  /**
   * Whether all code owners should be checked. *
   *
   * <p>If {@code true} {@link PathCodeOwnerStatus#owners()} are expected to be set in {@link
   * PathCodeOwnerStatus} instances that are created by {@link CodeOwnerApprovalCheck}.
   */
  public abstract boolean checkAllOwners();

  /**
   * Creates a {@link CodeOwnerApprovalCheckInput} instance for computing the paths in a change that
   * are owned by the given accounts.
   *
   * @param codeOwnersConfig the code-owners plugin configuration
   * @param codeOwnerResolver the {@link CodeOwnerResolver} that should be used to resolve the
   *     configured global code owners
   * @param changeNotes the notes of the change for which owned paths should be computed
   * @param accounts the accounts for which the owned paths should be computed
   * @return the created {@link CodeOwnerApprovalCheckInput} instance
   */
  public static CodeOwnerApprovalCheckInput createForComputingOwnedPaths(
      CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig,
      CodeOwnerResolver codeOwnerResolver,
      ChangeNotes changeNotes,
      ImmutableSet<Account.Id> accounts) {
    CodeOwnerResolverResult globalCodeOwners =
        codeOwnerResolver.resolveGlobalCodeOwners(changeNotes.getProjectName());
    logger.atFine().log("global code owners = %s", globalCodeOwners);

    FallbackCodeOwners fallbackCodeOwners = codeOwnersConfig.getFallbackCodeOwners();
    logger.atFine().log("fallbackCodeOwner = %s", fallbackCodeOwners);

    return create(
        /* reviewers= */ ImmutableSet.of(),
        /* approvers= */ accounts,
        /* approversFromPreviousPatchSets= */ ImmutableMultimap.of(),
        // Do not check for implicit approvals since implicit approvals of other users
        // should be ignored. For the given account we do not need to check for
        // implicit approvals since all owned files are already covered by the
        // explicit approval.
        /* implicitApprover= */ Optional.empty(),
        /* overrides= */ ImmutableSet.of(),
        globalCodeOwners,
        fallbackCodeOwners,
        /* checkAllOwners= */ true);
  }

  /**
   * Creates a {@link CodeOwnerApprovalCheckInput} instance.
   *
   * @param reviewers the reviewers that can possibly code owner approve the change (if they are
   *     code owners)
   * @param approvers the accounts that have an approval on the current patch set that possibly
   *     counts as code owner approval (if they are code owners)
   * @param implicitApprover account from which an implicit code owner approval should be assumed
   * @param overrides the approvals from the current patch set that count as code owner overrides
   * @param globalCodeOwners the configured global code owners
   * @param fallbackCodeOwners the policy that defines who owns paths for which no code owners are
   *     defined
   * @param checkAllOwners Whether all code owners are checked. If {@code true} {@link
   *     PathCodeOwnerStatus#owners()} will be set in the the {@link PathCodeOwnerStatus} instances
   *     that are created by {@link CodeOwnerApprovalCheck}. Checking all owners means that no
   *     shortcuts can be applied, hence checking the code owner approvals with {@code
   *     checkAllOwners=true} is more expensive.
   * @return the created {@link CodeOwnerApprovalCheckInput} instance
   */
  private static CodeOwnerApprovalCheckInput create(
      ImmutableSet<Account.Id> reviewers,
      ImmutableSet<Account.Id> approvers,
      ImmutableMultimap<PatchSet.Id, Account.Id> approversFromPreviousPatchSets,
      Optional<Account.Id> implicitApprover,
      ImmutableSet<PatchSetApproval> overrides,
      CodeOwnerResolverResult globalCodeOwners,
      FallbackCodeOwners fallbackCodeOwners,
      boolean checkAllOwners) {
    return new AutoValue_CodeOwnerApprovalCheckInput(
        reviewers,
        approvers,
        approversFromPreviousPatchSets,
        implicitApprover,
        overrides,
        globalCodeOwners,
        fallbackCodeOwners,
        checkAllOwners);
  }

  /**
   * Class to load all inputs that are required for checking the code owner approvals on a change.
   */
  public static class Loader {
    /** Factory to create the {@link Loader} with injected dependencies. */
    interface Factory {
      Loader create(
          CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig,
          CodeOwnerResolver codeOwnerResolver,
          ChangeNotes changeNotes);
    }

    private final ApprovalsUtil approvalsUtil;
    private final CodeOwnerMetrics codeOwnerMetrics;
    private final CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig;
    private final CodeOwnerResolver codeOwnerResolver;
    private final ChangeNotes changeNotes;

    @Inject
    Loader(
        ApprovalsUtil approvalsUtil,
        CodeOwnerMetrics codeOwnerMetrics,
        @Assisted CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig,
        @Assisted CodeOwnerResolver codeOwnerResolver,
        @Assisted ChangeNotes changeNotes) {
      this.approvalsUtil = approvalsUtil;
      this.codeOwnerMetrics = codeOwnerMetrics;
      this.codeOwnersConfig = codeOwnersConfig;
      this.codeOwnerResolver = codeOwnerResolver;
      this.changeNotes = changeNotes;
    }

    CodeOwnerApprovalCheckInput load() {
      logger.atFine().log(
          "requiredApproval = %s, overrideApprovals = %s",
          codeOwnersConfig.getRequiredApproval().formatForLogging(),
          RequiredApproval.formatForLogging(codeOwnersConfig.getOverrideApprovals()));
      return CodeOwnerApprovalCheckInput.create(
          getReviewers(),
          getApprovers(),
          getApproversFromPreviousPatchSets(),
          getImplicitApprover(),
          getOverrides(),
          getGlobalCodeOwners(),
          getFallbackCodeOwners(),
          /* checkAllOwners= */ false);
    }

    /**
     * Gets the IDs of the accounts of all reviewers that can possibly code owner approve the change
     * (if they are code owners).
     *
     * <p>If self approvals are ignored the patch set uploader is filtered out since in this case
     * the patch set uploader cannot approve the change even if they are a code owner.
     */
    private ImmutableSet<Account.Id> getReviewers() {
      ImmutableSet<Account.Id> reviewerAccountIds =
          changeNotes.getReviewers().byState(ReviewerStateInternal.REVIEWER);
      RequiredApproval requiredApproval = codeOwnersConfig.getRequiredApproval();
      Account.Id patchSetUploader = changeNotes.getCurrentPatchSet().uploader();
      if (requiredApproval.labelType().isIgnoreSelfApproval()
          && reviewerAccountIds.contains(patchSetUploader)) {
        logger.atFine().log(
            "Removing patch set uploader %s from reviewers since the label of the required"
                + " approval (%s) is configured to ignore self approvals",
            patchSetUploader, requiredApproval.labelType());
        return filterOutAccount(reviewerAccountIds, patchSetUploader);
      }
      logger.atFine().log("reviewers = %s", reviewerAccountIds);
      return reviewerAccountIds;
    }

    /**
     * Gets the IDs of the accounts that have an approval on the current patch set that possibly
     * counts as code owner approval (if they are code owners).
     *
     * <p>If self approvals are ignored the patch set uploader is filtered out since in this case
     * the approval of the patch set uploader is ignored even if they are a code owner.
     */
    private ImmutableSet<Account.Id> getApprovers() {
      ImmutableList<PatchSetApproval> currentPatchSetApprovals =
          getCurrentPatchSetApprovals(changeNotes);
      RequiredApproval requiredApproval = codeOwnersConfig.getRequiredApproval();
      ImmutableSet<Account.Id> approverAccountIds =
          currentPatchSetApprovals.stream()
              .filter(requiredApproval::isApprovedBy)
              .map(PatchSetApproval::accountId)
              .collect(toImmutableSet());
      Account.Id patchSetUploader = changeNotes.getCurrentPatchSet().uploader();
      if (requiredApproval.labelType().isIgnoreSelfApproval()
          && approverAccountIds.contains(patchSetUploader)) {
        logger.atFine().log(
            "Removing patch set uploader %s from approvers since the label of the required"
                + " approval (%s) is configured to ignore self approvals",
            patchSetUploader, requiredApproval.labelType());
        return filterOutAccount(approverAccountIds, patchSetUploader);
      }
      logger.atFine().log("approvers = %s", approverAccountIds);
      return approverAccountIds;
    }

    private Optional<Account.Id> getImplicitApprover() {
      Account.Id changeOwner = changeNotes.getChange().getOwner();
      Account.Id patchSetUploader = changeNotes.getCurrentPatchSet().uploader();
      boolean implicitApprovalConfig = codeOwnersConfig.areImplicitApprovalsEnabled();
      boolean enableImplicitApproval =
          implicitApprovalConfig && changeOwner.equals(patchSetUploader);
      logger.atFine().log(
          "changeOwner = %d, patchSetUploader = %d, implict approval config = %s\n"
              + "=> implicit approval is %s",
          changeOwner.get(),
          patchSetUploader.get(),
          implicitApprovalConfig,
          enableImplicitApproval ? "enabled" : "disabled");
      return enableImplicitApproval ? Optional.of(changeOwner) : Optional.empty();
    }

    /**
     * Gets a map of previous patch sets to the IDs of the accounts that have an approval on that
     * patch set that is sticky and possibly counts as code owner approval (if they are code
     * owners).
     *
     * <p>If self approvals are ignored the patch set uploader is filtered out for all patch sets
     * since in this case the approval of the patch set uploader is ignored even if they are a code
     * owner.
     */
    private ImmutableMultimap<PatchSet.Id, Account.Id> getApproversFromPreviousPatchSets() {
      if (!codeOwnersConfig.areStickyApprovalsEnabled()) {
        logger.atFine().log("sticky approvals are disabled");
        return ImmutableMultimap.of();
      }

      // Filter out approvals on the current patch set, since here we are only interested in code
      // owner approvals on previous patch sets that should be considered as sticky.
      PatchSet.Id currentPatchSetId = changeNotes.getCurrentPatchSet().id();
      ImmutableSetMultimap<PatchSet.Id, Account.Id> approversFromPreviousPatchSets =
          getLastCodeOwnerApprovalsByAccount().values().stream()
              .filter(psa -> psa.patchSetId().get() < currentPatchSetId.get())
              .collect(
                  toImmutableSetMultimap(
                      PatchSetApproval::patchSetId, PatchSetApproval::accountId));
      logger.atFine().log(
          "sticky approvals are enabled, approversFromPreviousPatchSets=%s",
          approversFromPreviousPatchSets);
      return approversFromPreviousPatchSets;
    }

    /**
     * Returns the last code owner approvals by account.
     *
     * <p>The returned map contains for each user their last approval on the change that counts as a
     * code owner approval. Approvals that are invalidated by code owner votes on newer patch sets
     * are filtered out.
     */
    private ImmutableMap<Account.Id, PatchSetApproval> getLastCodeOwnerApprovalsByAccount() {
      RequiredApproval requiredApproval = codeOwnersConfig.getRequiredApproval();

      Map<Account.Id, PatchSetApproval> lastCodeOwnerVotesByAccount = new HashMap<>();
      ImmutableSetMultimap<PatchSet.Id, PatchSetApproval> allCodeOwnerApprovals =
          changeNotes.getApprovals().all().entries().stream()
              // Only look at approvals on the label that is configured for code owner approvals.
              .filter(e -> e.getValue().label().equals(requiredApproval.labelType().getName()))
              .collect(toImmutableSetMultimap(Map.Entry::getKey, Map.Entry::getValue));
      logger.atFine().log("allCodeOwnerApprovals=%s", allCodeOwnerApprovals);
      // Iterate over the patch sets in reverse order (latest patch set first).
      for (PatchSet.Id patchSetId : getPatchSetIdsInReverseOrder()) {
        // Only store the code owner approval if we didn't find a code owner approval for that
        // account on a newer patch set yet.
        // If a code owner approval on a newer patch set exist, it invalidated the code owner
        // approval on the older patch set and we can ignore it.
        allCodeOwnerApprovals
            .get(patchSetId)
            .forEach(psa -> lastCodeOwnerVotesByAccount.putIfAbsent(psa.accountId(), psa));
      }

      ImmutableMap<Account.Id, PatchSetApproval> lastCodeOwnerApprovalsByAccount =
          lastCodeOwnerVotesByAccount.entrySet().stream()
              // Remove all approvals which do not count as a code owner approval because the voting
              // value is insufficient.
              .filter(e -> requiredApproval.isApprovedBy(e.getValue()))
              .filter(filterOutSelfApprovalsIfSelfApprovalsAreIgnored())
              .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
      logger.atFine().log(
          "lastCodeOwnerApprovalsByAccount=%s, lastCodeOwnerVotesByAccount=%s",
          lastCodeOwnerApprovalsByAccount, lastCodeOwnerVotesByAccount);
      return lastCodeOwnerApprovalsByAccount;
    }

    /**
     * Creates a filter that filters out self approvals by the patch set uploader if self approvals
     * are ignored
     */
    private Predicate<Map.Entry<Account.Id, PatchSetApproval>>
        filterOutSelfApprovalsIfSelfApprovalsAreIgnored() {
      RequiredApproval requiredApproval = codeOwnersConfig.getRequiredApproval();
      if (!requiredApproval.labelType().isIgnoreSelfApproval()) {
        logger.atFine().log("s");
        return e -> true;
      }

      Account.Id patchSetUploader = changeNotes.getCurrentPatchSet().uploader();
      return e -> {
        if (e.getKey().equals(patchSetUploader)) {
          logger.atFine().log(
              "Removing approvals of the patch set uploader %s since the label of the required"
                  + " approval (%s) is configured to ignore self approvals",
              patchSetUploader, requiredApproval.labelType());
          return false;
        }
        return true;
      };
    }

    private ImmutableSortedSet<PatchSet.Id> getPatchSetIdsInReverseOrder() {
      return ImmutableSortedSet.orderedBy(comparing(PatchSet.Id::get).reversed())
          .addAll(changeNotes.getPatchSets().keySet())
          .build();
    }

    /**
     * Gets the approvals from the current patch set that count as code owner overrides.
     *
     * <p>If self approvals are ignored an override of the patch set uploader is filtered out since
     * it doesn't count as code owner override.
     */
    private ImmutableSet<PatchSetApproval> getOverrides() {
      ImmutableList<PatchSetApproval> currentPatchSetApprovals =
          getCurrentPatchSetApprovals(changeNotes);
      ImmutableSortedSet<RequiredApproval> overrideApprovals =
          codeOwnersConfig.getOverrideApprovals();
      ImmutableSet<RequiredApproval> overrideApprovalsThatIgnoreSelfApprovals =
          overrideApprovals.stream()
              .filter(overrideApproval -> overrideApproval.labelType().isIgnoreSelfApproval())
              .collect(toImmutableSet());
      Account.Id patchSetUploader = changeNotes.getCurrentPatchSet().uploader();
      ImmutableSet<PatchSetApproval> overrides =
          currentPatchSetApprovals.stream()
              .filter(
                  approval -> {
                    // If the approval is from the patch set uploader and if it matches any of the
                    // labels
                    // for which self approvals are ignored, filter it out.
                    if (approval.accountId().equals(patchSetUploader)
                        && overrideApprovalsThatIgnoreSelfApprovals.stream()
                            .anyMatch(
                                requiredApproval ->
                                    requiredApproval
                                        .labelType()
                                        .getLabelId()
                                        .equals(approval.key().labelId()))) {
                      logger.atFine().log(
                          "Filtered out self-override %s of patch set uploader",
                          LabelVote.create(approval.label(), approval.value()));
                      return false;
                    }
                    return true;
                  })
              .filter(
                  patchSetApproval ->
                      overrideApprovals.stream()
                          .anyMatch(
                              overrideApproval -> overrideApproval.isApprovedBy(patchSetApproval)))
              .collect(toImmutableSet());
      logger.atFine().log("hasOverride = %s (overrides = %s)", !overrides.isEmpty(), overrides);
      return overrides;
    }

    private CodeOwnerResolverResult getGlobalCodeOwners() {
      CodeOwnerResolverResult globalCodeOwners =
          codeOwnerResolver.resolveGlobalCodeOwners(changeNotes.getProjectName());
      logger.atFine().log("global code owners = %s", globalCodeOwners);
      return globalCodeOwners;
    }

    private FallbackCodeOwners getFallbackCodeOwners() {
      FallbackCodeOwners fallbackCodeOwners = codeOwnersConfig.getFallbackCodeOwners();
      logger.atFine().log("fallbackCodeOwners = %s", fallbackCodeOwners);
      return fallbackCodeOwners;
    }

    private ImmutableList<PatchSetApproval> getCurrentPatchSetApprovals(ChangeNotes changeNotes) {
      try (Timer0.Context ctx = codeOwnerMetrics.computePatchSetApprovals.start()) {
        return ImmutableList.copyOf(
            approvalsUtil.byPatchSet(changeNotes, changeNotes.getCurrentPatchSet().id()));
      }
    }

    private static ImmutableSet<Account.Id> filterOutAccount(
        ImmutableSet<Account.Id> accountIds, Account.Id accountIdToFilterOut) {
      return accountIds.stream()
          .filter(accountId -> !accountId.equals(accountIdToFilterOut))
          .collect(toImmutableSet());
    }
  }
}
