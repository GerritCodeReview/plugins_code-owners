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

package com.google.gerrit.plugins.codeowners.backend;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginProjectConfigSnapshot;
import com.google.gerrit.plugins.codeowners.backend.config.RequiredApproval;
import com.google.gerrit.plugins.codeowners.common.ChangedFile;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.PureRevertCache;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.util.AccountTemplateUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Class to check code owner approvals on a change.
 *
 * <p>Every file, or rather touched path, in a change needs to be approved by a code owner:
 *
 * <ul>
 *   <li>New file: requires approval on the new path
 *   <li>Modified file: requires approval on the old/new path (old path == new path)
 *   <li>Deleted file: requires approval on the old path
 *   <li>Renamed file: requires approval on the old and new path (equivalent to delete old path +
 *       add new path)
 *   <li>Copied file: requires approval on the new path (an approval on the old path is not needed
 *       since the file at this path was not touched)
 * </ul>
 */
@Singleton
public class CodeOwnerApprovalCheck {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepositoryManager repoManager;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final ChangedFiles changedFiles;
  private final PureRevertCache pureRevertCache;
  private final Provider<CodeOwnerConfigHierarchy> codeOwnerConfigHierarchyProvider;
  private final Provider<CodeOwnerResolver> codeOwnerResolverProvider;
  private final CodeOwnerApprovalCheckInput.Loader.Factory inputLoaderFactory;
  private final CodeOwnerMetrics codeOwnerMetrics;
  private final ChangedFilesByPatchSetCache.Factory changedFilesByPatchSetCacheFactory;

  @Inject
  CodeOwnerApprovalCheck(
      GitRepositoryManager repoManager,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      ChangedFiles changedFiles,
      PureRevertCache pureRevertCache,
      Provider<CodeOwnerConfigHierarchy> codeOwnerConfigHierarchyProvider,
      Provider<CodeOwnerResolver> codeOwnerResolverProvider,
      CodeOwnerApprovalCheckInput.Loader.Factory codeOwnerApprovalCheckInputLoaderFactory,
      CodeOwnerMetrics codeOwnerMetrics,
      ChangedFilesByPatchSetCache.Factory changedFilesByPatchSetCacheFactory) {
    this.repoManager = repoManager;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.changedFiles = changedFiles;
    this.pureRevertCache = pureRevertCache;
    this.codeOwnerConfigHierarchyProvider = codeOwnerConfigHierarchyProvider;
    this.codeOwnerResolverProvider = codeOwnerResolverProvider;
    this.inputLoaderFactory = codeOwnerApprovalCheckInputLoaderFactory;
    this.codeOwnerMetrics = codeOwnerMetrics;
    this.changedFilesByPatchSetCacheFactory = changedFilesByPatchSetCacheFactory;
  }

  /**
   * Returns the paths of the files in the given patch set that are owned by the specified account.
   *
   * @param changeNotes the change notes for which the owned files should be returned
   * @param patchSet the patch set for which the owned files should be returned
   * @param accountId account ID of the code owner for which the owned files should be returned
   * @param start number of owned paths to skip
   * @param limit the max number of owned paths that should be returned (0 = unlimited)
   * @param checkReviewers whether to check if the reviewers are in the owners.
   * @return the paths of the files in the given patch set that are owned by the specified account
   */
  public ImmutableList<OwnedChangedFile> getOwnedPaths(
      ChangeNotes changeNotes,
      PatchSet patchSet,
      Account.Id accountId,
      int start,
      int limit,
      boolean checkReviewers) {
    try (Timer0.Context ctx = codeOwnerMetrics.computeOwnedPaths.start()) {
      logger.atFine().log(
          "compute owned paths for account %d (project = %s, change = %d, patch set = %d,"
              + " start = %d, limit = %d)",
          accountId.get(),
          changeNotes.getProjectName(),
          changeNotes.getChangeId().get(),
          patchSet.id().get(),
          start,
          limit);
      ImmutableSet.Builder<Account.Id> checkOwnerIds = ImmutableSet.builder();
      checkOwnerIds.add(accountId);
      if (checkReviewers) {
        checkOwnerIds.addAll(changeNotes.getReviewers().byState(ReviewerStateInternal.REVIEWER));
      }
      Stream<FileCodeOwnerStatus> fileStatuses =
          getFileStatusesForAccounts(changeNotes, patchSet, checkOwnerIds.build())
              .filter(
                  fileStatus ->
                      (fileStatus.newPathStatus().isPresent()
                              && fileStatus.newPathStatus().get().owners().isPresent()
                              && !fileStatus.newPathStatus().get().owners().get().isEmpty())
                          || (fileStatus.oldPathStatus().isPresent()
                              && fileStatus.oldPathStatus().get().owners().isPresent()
                              && !fileStatus.oldPathStatus().get().owners().get().isEmpty()));
      if (start > 0) {
        fileStatuses = fileStatuses.skip(start);
      }
      if (limit > 0) {
        fileStatuses = fileStatuses.limit(limit);
      }

      return fileStatuses
          .map(
              fileStatus ->
                  OwnedChangedFile.create(
                      fileStatus
                          .newPathStatus()
                          .map(
                              newPathStatus ->
                                  OwnedPath.create(
                                      newPathStatus.path(),
                                      newPathStatus.owners().isPresent()
                                          && newPathStatus.owners().get().contains(accountId),
                                      newPathStatus.owners().isPresent()
                                          ? newPathStatus.owners().get()
                                          : ImmutableSet.of()))
                          .orElse(null),
                      fileStatus
                          .oldPathStatus()
                          .map(
                              oldPathStatus ->
                                  OwnedPath.create(
                                      oldPathStatus.path(),
                                      oldPathStatus.owners().isPresent()
                                          && oldPathStatus.owners().get().contains(accountId),
                                      oldPathStatus.owners().isPresent()
                                          ? oldPathStatus.owners().get()
                                          : ImmutableSet.of()))
                          .orElse(null)))
          .collect(toImmutableList());
    } catch (IOException | DiffNotAvailableException e) {
      throw new StorageException(
          String.format(
              "failed to compute owned paths of patch set %s for account %d",
              patchSet.id(), accountId.get()),
          e);
    }
  }

  /**
   * Whether the given change has sufficient code owner approvals to be submittable.
   *
   * @param changeNotes the change notes
   * @return whether the given change has sufficient code owner approvals to be submittable
   */
  public boolean isSubmittable(ChangeNotes changeNotes)
      throws IOException, DiffNotAvailableException {
    requireNonNull(changeNotes, "changeNotes");
    logger.atFine().log(
        "checking if change %d in project %s is submittable",
        changeNotes.getChangeId().get(), changeNotes.getProjectName());
    CodeOwnerConfigHierarchy codeOwnerConfigHierarchy = codeOwnerConfigHierarchyProvider.get();
    CodeOwnerResolver codeOwnerResolver = codeOwnerResolverProvider.get().enforceVisibility(false);
    CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig =
        codeOwnersPluginConfiguration.getProjectConfig(changeNotes.getProjectName());
    try {
      boolean isSubmittable =
          !getFileStatuses(
                  codeOwnersConfig, codeOwnerConfigHierarchy, codeOwnerResolver, changeNotes)
              .anyMatch(
                  fileStatus ->
                      (fileStatus.newPathStatus().isPresent()
                              && fileStatus.newPathStatus().get().status()
                                  != CodeOwnerStatus.APPROVED)
                          || (fileStatus.oldPathStatus().isPresent()
                              && fileStatus.oldPathStatus().get().status()
                                  != CodeOwnerStatus.APPROVED));
      logger.atFine().log(
          "change %d in project %s %s submittable",
          changeNotes.getChangeId().get(),
          changeNotes.getProjectName(),
          isSubmittable ? "is" : "is not");
      return isSubmittable;
    } finally {
      codeOwnerMetrics.codeOwnerConfigBackendReadsPerChange.record(
          codeOwnerConfigHierarchy.getCodeOwnerConfigCounters().getBackendReadCount());
      codeOwnerMetrics.codeOwnerConfigCacheReadsPerChange.record(
          codeOwnerConfigHierarchy.getCodeOwnerConfigCounters().getCacheReadCount());
      codeOwnerMetrics.codeOwnerResolutionsPerChange.record(
          codeOwnerResolver.getCodeOwnerCounters().getResolutionCount());
      codeOwnerMetrics.codeOwnerCacheReadsPerChange.record(
          codeOwnerResolver.getCodeOwnerCounters().getCacheReadCount());
    }
  }

  /**
   * Gets the code owner statuses for all files/paths that were changed in the current revision of
   * the given change as a set.
   *
   * @param start number of file statuses to skip
   * @param limit the max number of file statuses that should be returned (0 = unlimited)
   * @see #getFileStatuses(CodeOwnersPluginProjectConfigSnapshot, CodeOwnerConfigHierarchy,
   *     CodeOwnerResolver, ChangeNotes)
   */
  public ImmutableSet<FileCodeOwnerStatus> getFileStatusesAsSet(
      ChangeNotes changeNotes, int start, int limit) throws IOException, DiffNotAvailableException {
    requireNonNull(changeNotes, "changeNotes");
    CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig =
        codeOwnersPluginConfiguration.getProjectConfig(changeNotes.getProjectName());
    try (Timer1.Context<Boolean> ctx =
        codeOwnerMetrics.computeFileStatuses.start(codeOwnersConfig.areStickyApprovalsEnabled())) {
      logger.atFine().log(
          "compute file statuses (project = %s, change = %d, start = %d, limit = %d)",
          changeNotes.getProjectName(), changeNotes.getChangeId().get(), start, limit);
      Stream<FileCodeOwnerStatus> fileStatuses =
          getFileStatuses(
              codeOwnersConfig,
              codeOwnerConfigHierarchyProvider.get(),
              codeOwnerResolverProvider.get().enforceVisibility(false),
              changeNotes);
      if (start > 0) {
        fileStatuses = fileStatuses.skip(start);
      }
      if (limit > 0) {
        fileStatuses = fileStatuses.limit(limit);
      }
      return fileStatuses.collect(toImmutableSet());
    }
  }

  /**
   * Gets the code owner statuses for all files/paths that were changed in the current revision of
   * the given change.
   *
   * <p>The code owner statuses tell the user which code owner approvals are still missing in order
   * to make the change submittable.
   *
   * <p>Computing the code owner statuses for non-current revisions is not supported since the
   * semantics are unclear, e.g.:
   *
   * <ul>
   *   <li>non-current revisions are never submittable, hence asking which code owner approvals are
   *       still missing in order to make the revision submittable makes no sense
   *   <li>the code owner status {@link CodeOwnerStatus#PENDING} doesn't make sense for an old
   *       revision, from the perspective of the change owner this status looks like the change is
   *       waiting for the code owner to approve, but since voting on old revisions is not possible
   *       the code-owner is not able to provide this approval
   *   <li>the code owner statuses are computed from the approvals that were given by code owners,
   *       the UI always shows the current approval even when looking at an old revision, showing
   *       code owner statuses that mismatch the shown approvals (because they are computed from
   *       approvals that were present on an old revision) would only confuse users
   * </ul>
   *
   * @param codeOwnerConfigHierarchy {@link CodeOwnerConfigHierarchy} instance that should be used
   *     to iterate over code owner config hierarchies
   * @param changeNotes the notes of the change for which the current code owner statuses should be
   *     returned
   */
  private Stream<FileCodeOwnerStatus> getFileStatuses(
      CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig,
      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy,
      CodeOwnerResolver codeOwnerResolver,
      ChangeNotes changeNotes)
      throws IOException, DiffNotAvailableException {
    requireNonNull(changeNotes, "changeNotes");
    try (Timer0.Context ctx = codeOwnerMetrics.prepareFileStatusComputation.start()) {
      logger.atFine().log(
          "prepare stream to compute file statuses (project = %s, change = %d)",
          changeNotes.getProjectName(), changeNotes.getChangeId().get());

      Account.Id patchSetUploader = changeNotes.getCurrentPatchSet().uploader();
      ImmutableSet<Account.Id> exemptedAccounts = codeOwnersConfig.getExemptedAccounts();
      logger.atFine().log("exemptedAccounts = %s", exemptedAccounts);
      if (exemptedAccounts.contains(patchSetUploader)) {
        logger.atFine().log(
            "patch set uploader %d is exempted from requiring code owner approvals",
            patchSetUploader.get());
        return getAllPathsAsApproved(
            changeNotes,
            changeNotes.getCurrentPatchSet(),
            String.format(
                "patch set uploader %s is exempted from requiring code owner approvals",
                AccountTemplateUtil.getAccountTemplate(patchSetUploader)));
      }

      boolean arePureRevertsExempted = codeOwnersConfig.arePureRevertsExempted();
      logger.atFine().log("arePureRevertsExempted = %s", arePureRevertsExempted);
      if (arePureRevertsExempted && isPureRevert(changeNotes)) {
        logger.atFine().log(
            "change is a pure revert and is exempted from requiring code owner approvals");
        return getAllPathsAsApproved(
            changeNotes,
            changeNotes.getCurrentPatchSet(),
            "change is a pure revert and is exempted from requiring code owner approvals");
      }

      BranchNameKey branch = changeNotes.getChange().getDest();
      Optional<ObjectId> revision = getDestBranchRevision(changeNotes.getChange());
      if (revision.isPresent()) {
        logger.atFine().log(
            "dest branch %s has revision %s", branch.branch(), revision.get().name());
      } else {
        logger.atFine().log("dest branch %s does not exist", branch.branch());
      }

      CodeOwnerApprovalCheckInput input =
          inputLoaderFactory.create(codeOwnersConfig, codeOwnerResolver, changeNotes).load();
      ChangedFilesByPatchSetCache changedFilesByPatchSetCache =
          changedFilesByPatchSetCacheFactory.create(codeOwnersConfig, changeNotes);
      return changedFiles
          .get(changeNotes.getProjectName(), changeNotes.getCurrentPatchSet().commitId())
          .stream()
          .map(
              changedFile ->
                  getFileStatus(
                      codeOwnerConfigHierarchy,
                      codeOwnerResolver,
                      codeOwnersConfig,
                      changedFilesByPatchSetCache,
                      branch,
                      revision.orElse(null),
                      changedFile,
                      input));
    }
  }

  /**
   * Gets the code owner status for all files/paths that were changed in the current revision of the
   * given change assuming that there is only an approval from the given account.
   *
   * <p>This method doesn't take approvals from other users and global code owners into account.
   *
   * <p>The purpose of this method is to find the files/paths in a change that are owned by the
   * given account.
   *
   * <p>As a side-effect, it also computes, for each file, who the approvers are if the file is not
   * approved.
   *
   * @param changeNotes the notes of the change for which the code owner statuses should be returned
   * @param patchSet the patch set for which the code owner statuses should be returned
   * @param accountIds The accounts to check whether they have owners permission.
   */
  @VisibleForTesting
  public Stream<FileCodeOwnerStatus> getFileStatusesForAccounts(
      ChangeNotes changeNotes, PatchSet patchSet, ImmutableSet<Account.Id> accountIds)
      throws IOException, DiffNotAvailableException {
    requireNonNull(changeNotes, "changeNotes");
    requireNonNull(patchSet, "patchSet");
    requireNonNull(accountIds, "accountIds");
    try (Timer0.Context ctx = codeOwnerMetrics.prepareFileStatusComputationForAccount.start()) {
      logger.atFine().log(
          "prepare stream to compute file statuses for accounts %s (project = %s, change = %d,"
              + " patch set = %d)",
          accountIds,
          changeNotes.getProjectName(),
          changeNotes.getChangeId().get(),
          patchSet.id().get());

      CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig =
          codeOwnersPluginConfiguration.getProjectConfig(changeNotes.getProjectName());

      RequiredApproval requiredApproval = codeOwnersConfig.getRequiredApproval();
      logger.atFine().log("requiredApproval = %s", requiredApproval);

      BranchNameKey branch = changeNotes.getChange().getDest();
      Optional<ObjectId> revision = getDestBranchRevision(changeNotes.getChange());
      if (revision.isPresent()) {
        logger.atFine().log(
            "dest branch %s has revision %s", branch.branch(), revision.get().name());
      } else {
        logger.atFine().log("dest branch %s does not exist", branch.branch());
      }

      FallbackCodeOwners fallbackCodeOwners = codeOwnersConfig.getFallbackCodeOwners();
      logger.atFine().log("fallbackCodeOwner = %s", fallbackCodeOwners);

      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy = codeOwnerConfigHierarchyProvider.get();
      CodeOwnerResolver codeOwnerResolver =
          codeOwnerResolverProvider.get().enforceVisibility(false);
      CodeOwnerApprovalCheckInput input =
          CodeOwnerApprovalCheckInput.createForComputingOwnedPaths(
              codeOwnersConfig, codeOwnerResolver, changeNotes, accountIds);
      ChangedFilesByPatchSetCache changedFilesByPatchSetCache =
          changedFilesByPatchSetCacheFactory.create(codeOwnersConfig, changeNotes);
      return changedFiles.get(changeNotes.getProjectName(), patchSet.commitId()).stream()
          .map(
              changedFile ->
                  getFileStatus(
                      codeOwnerConfigHierarchy,
                      codeOwnerResolver,
                      codeOwnersConfig,
                      changedFilesByPatchSetCache,
                      branch,
                      revision.orElse(null),
                      changedFile,
                      input));
    }
  }

  private boolean isPureRevert(ChangeNotes changeNotes) throws IOException {
    try {
      return changeNotes.getChange().getRevertOf() != null
          && pureRevertCache.isPureRevert(changeNotes);
    } catch (BadRequestException e) {
      throw new StorageException(
          String.format(
              "failed to check if change %s in project %s is a pure revert",
              changeNotes.getChangeId(), changeNotes.getProjectName()),
          e);
    }
  }

  private Stream<FileCodeOwnerStatus> getAllPathsAsApproved(
      ChangeNotes changeNotes, PatchSet patchSet, String reason)
      throws IOException, DiffNotAvailableException {
    logger.atFine().log("all paths are approved (reason = %s)", reason);
    return changedFiles.get(changeNotes.getProjectName(), patchSet.commitId()).stream()
        .map(
            changedFile ->
                FileCodeOwnerStatus.create(
                    changedFile,
                    changedFile
                        .newPath()
                        .map(
                            newPath ->
                                PathCodeOwnerStatus.create(
                                    newPath, CodeOwnerStatus.APPROVED, reason)),
                    changedFile
                        .oldPath()
                        .map(
                            oldPath ->
                                PathCodeOwnerStatus.create(
                                    oldPath, CodeOwnerStatus.APPROVED, reason))));
  }

  private FileCodeOwnerStatus getFileStatus(
      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy,
      CodeOwnerResolver codeOwnerResolver,
      CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig,
      ChangedFilesByPatchSetCache changedFilesByPatchSetCache,
      BranchNameKey branch,
      @Nullable ObjectId revision,
      ChangedFile changedFile,
      CodeOwnerApprovalCheckInput input) {
    try (Timer0.Context ctx = codeOwnerMetrics.computeFileStatus.start()) {
      logger.atFine().log("computing file status for %s", changedFile);

      // Compute the code owner status for the new path, if there is a new path.
      Optional<PathCodeOwnerStatus> newPathStatus =
          changedFile
              .newPath()
              .map(
                  newPath ->
                      getPathCodeOwnerStatus(
                          codeOwnerConfigHierarchy,
                          codeOwnerResolver,
                          codeOwnersConfig,
                          changedFilesByPatchSetCache,
                          branch,
                          revision,
                          newPath,
                          input));

      // Compute the code owner status for the old path, if the file was deleted or renamed.
      Optional<PathCodeOwnerStatus> oldPathStatus = Optional.empty();
      if (changedFile.isDeletion() || changedFile.isRename()) {
        checkState(
            changedFile.oldPath().isPresent(), "old path must be present for deletion/rename");
        logger.atFine().log(
            "file was %s (old path = %s)",
            changedFile.isDeletion() ? "deleted" : "renamed", changedFile.oldPath().get());
        oldPathStatus =
            Optional.of(
                getPathCodeOwnerStatus(
                    codeOwnerConfigHierarchy,
                    codeOwnerResolver,
                    codeOwnersConfig,
                    changedFilesByPatchSetCache,
                    branch,
                    revision,
                    changedFile.oldPath().get(),
                    input));
      }

      FileCodeOwnerStatus fileCodeOwnerStatus =
          FileCodeOwnerStatus.create(changedFile, newPathStatus, oldPathStatus);
      logger.atFine().log("fileCodeOwnerStatus = %s", fileCodeOwnerStatus);
      return fileCodeOwnerStatus;
    }
  }

  private PathCodeOwnerStatus getPathCodeOwnerStatus(
      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy,
      CodeOwnerResolver codeOwnerResolver,
      CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig,
      ChangedFilesByPatchSetCache changedFilesByPatchSetCache,
      BranchNameKey branch,
      @Nullable ObjectId revision,
      Path absolutePath,
      CodeOwnerApprovalCheckInput input) {
    logger.atFine().log("computing path status for %s", absolutePath);

    if (!input.overrides().isEmpty()) {
      logger.atFine().log(
          "the status for path %s is %s since an override is present (overrides = %s)",
          absolutePath, CodeOwnerStatus.APPROVED.name(), input.overrides());
      Optional<PatchSetApproval> override = input.overrides().stream().findAny();
      checkState(override.isPresent(), "no override found");
      return PathCodeOwnerStatus.create(
          absolutePath,
          CodeOwnerStatus.APPROVED,
          String.format(
              "override approval %s by %s is present",
              override.get().label() + LabelValue.formatValue(override.get().value()),
              AccountTemplateUtil.getAccountTemplate(override.get().accountId())));
    }

    AtomicReference<CodeOwnerStatus> codeOwnerStatus =
        new AtomicReference<>(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    AtomicReference<String> reason = new AtomicReference<>(/* initialValue= */ null);
    ImmutableSet.Builder<Account.Id> activeOwners = ImmutableSet.builder();

    boolean isGloballyApproved =
        isApproved(
            absolutePath,
            input.globalCodeOwners(),
            CodeOwnerKind.GLOBAL_CODE_OWNER,
            codeOwnersConfig,
            changedFilesByPatchSetCache,
            input,
            reason);

    if (isGloballyApproved) {
      codeOwnerStatus.set(CodeOwnerStatus.APPROVED);
    }

    if (input.globalCodeOwners().ownedByAllUsers()) {
      activeOwners.addAll(input.approvers());
      activeOwners.addAll(input.reviewers());
    } else {
      activeOwners.addAll(
          Sets.intersection(input.globalCodeOwners().codeOwnersAccountIds(), input.approvers()));
      activeOwners.addAll(
          Sets.intersection(input.globalCodeOwners().codeOwnersAccountIds(), input.reviewers()));
    }

    // Only check recursively for all OWNERs in two scenarios:
    // 1. The path was not globally approved
    // 2. The path was globally approved but is not owned by all users and we
    //    want to calculate all ownerIds.
    if (!isGloballyApproved
        || (input.checkAllOwners() && !input.globalCodeOwners().ownedByAllUsers())) {
      logger.atFine().log("%s was not approved by a global code owner", absolutePath);

      if (isPending(
          input.globalCodeOwners(), CodeOwnerKind.GLOBAL_CODE_OWNER, input.reviewers(), reason)) {
        codeOwnerStatus.set(CodeOwnerStatus.PENDING);
      }

      AtomicBoolean hasRevelantCodeOwnerDefinitions = new AtomicBoolean(false);
      AtomicBoolean parentCodeOwnersAreIgnored = new AtomicBoolean(false);
      codeOwnerConfigHierarchy.visitForFile(
          branch,
          revision,
          absolutePath,
          (PathCodeOwnersVisitor)
              pathCodeOwners -> {
                CodeOwnerKind codeOwnerKind =
                    RefNames.REFS_CONFIG.equals(pathCodeOwners.getCodeOwnerConfig().key().ref())
                        ? CodeOwnerKind.DEFAULT_CODE_OWNER
                        : CodeOwnerKind.REGULAR_CODE_OWNER;

                CodeOwnerResolverResult codeOwners =
                    resolveCodeOwners(codeOwnerResolver, pathCodeOwners);

                boolean ownedByAllUsers = codeOwners.ownedByAllUsers();
                if (ownedByAllUsers) {
                  activeOwners.addAll(input.approvers());
                  activeOwners.addAll(input.reviewers());
                } else {
                  activeOwners.addAll(
                      Sets.intersection(codeOwners.codeOwnersAccountIds(), input.approvers()));
                  activeOwners.addAll(
                      Sets.intersection(codeOwners.codeOwnersAccountIds(), input.reviewers()));
                }
                logger.atFine().log(
                    "code owners = %s (code owner kind = %s, code owner config folder path = %s,"
                        + " file name = %s)",
                    codeOwners,
                    codeOwnerKind,
                    pathCodeOwners.getCodeOwnerConfig().key().folderPath(),
                    pathCodeOwners.getCodeOwnerConfig().key().fileName().orElse("<default>"));

                if (codeOwners.hasRevelantCodeOwnerDefinitions()) {
                  hasRevelantCodeOwnerDefinitions.set(true);
                }

                if (isApproved(
                    absolutePath,
                    codeOwners,
                    codeOwnerKind,
                    codeOwnersConfig,
                    changedFilesByPatchSetCache,
                    input,
                    reason)) {
                  codeOwnerStatus.set(CodeOwnerStatus.APPROVED);
                  // No need to recurse if we are not checking all owners or all owners are
                  // are already added.
                  return input.checkAllOwners() && !ownedByAllUsers;
                } else if (isPending(codeOwners, codeOwnerKind, input.reviewers(), reason)) {
                  codeOwnerStatus.set(CodeOwnerStatus.PENDING);

                  // We need to continue to check if any of the higher-level code owners approved
                  // the change.
                  return true;
                }

                // We need to continue to check if any of the higher-level code owners approved the
                // change or is a reviewer.
                return true;
              },
          codeOwnerConfigKey -> {
            logger.atFine().log(
                "code owner config %s ignores parent code owners for %s",
                codeOwnerConfigKey, absolutePath);
            parentCodeOwnersAreIgnored.set(true);
          });

      // If no code owners have been defined for the file and if parent code owners are not ignored,
      // the fallback code owners apply if they are configured. We can skip checking them if we
      // already found that the file was approved.
      if (codeOwnerStatus.get() != CodeOwnerStatus.APPROVED
          && !hasRevelantCodeOwnerDefinitions.get()
          && !parentCodeOwnersAreIgnored.get()) {
        CodeOwnerStatus codeOwnerStatusForFallbackCodeOwners =
            getCodeOwnerStatusForFallbackCodeOwners(
                codeOwnerStatus.get(),
                input.implicitApprover().orElse(null),
                input.reviewers(),
                input.approvers(),
                input.fallbackCodeOwners(),
                absolutePath,
                reason);

        if (codeOwnerStatusForFallbackCodeOwners.equals(CodeOwnerStatus.APPROVED)) {
          activeOwners.addAll(input.approvers());
        } else if (codeOwnerStatusForFallbackCodeOwners.equals(CodeOwnerStatus.PENDING)) {
          switch (input.fallbackCodeOwners()) {
            case NONE:
              // do nothing, if codeOwnerStatus is PENDING, the reviewers that are code owners have
              // already been added to activeOwners
              break;
            case ALL_USERS:
              // all users are code owners
              activeOwners.addAll(input.approvers());
              activeOwners.addAll(input.reviewers());
              break;
          }
        }
        // Merge codeOwnerStatusForFallbackCodeOwners into codeOwnerStatus:
        // * codeOwnerStatus is the code owner status without taking fallback code owners into
        //   account
        // * codeOwnerStatusForFallbackCodeOwners is the code owner status for fallback code owners
        //   only
        // When merging both the "better" code owner status should take precedence (APPROVED is
        // better than PENDING which is better than INSUFFICIENT_REVIEWERS):
        // * if codeOwnerStatus == APPROVED we do not compute the code owner status for the fallback
        //   code owners and never reach this point. Hence we can ignore this case below.
        // * if codeOwnerStatus == PENDING (e.g. because a global code owner is a reviewer) we must
        //   override it if codeOwnerStatusForFallbackCodeOwners is APPROVED
        // * if codeOwnerStatus == INSUFFICIENT_REVIEWERS we must override it if
        //   codeOwnerStatusForFallbackCodeOwners is PENDING or APPROVED
        // This means if codeOwnerStatusForFallbackCodeOwners is INSUFFICIENT_REVIEWERS it is never
        // "better" than codeOwnerStatus, hence in this case we do not override codeOwnerStatus.
        // On the other hand if codeOwnerStatusForFallbackCodeOwners is PENDING or APPROVED (aka not
        // INSUFFICIENT_REVIEWERS) it is always as good or "better" than codeOwnerStatus (which can
        // only be INSUFFICIENT_REVIEWERS or PENDING at this point), hence in this case we can/must
        // override codeOwnerStatus.
        if (!codeOwnerStatusForFallbackCodeOwners.equals(CodeOwnerStatus.INSUFFICIENT_REVIEWERS)) {
          codeOwnerStatus.set(codeOwnerStatusForFallbackCodeOwners);
        }
      }
    }

    logger.atFine().log(
        "%s has code owner status %s (reason = %s)",
        absolutePath, codeOwnerStatus.get(), reason.get() != null ? reason.get() : "n/a");
    PathCodeOwnerStatus pathCodeOwnerStatus =
        PathCodeOwnerStatus.create(
            absolutePath,
            codeOwnerStatus.get(),
            reason.get(),
            input.checkAllOwners() ? Optional.of(activeOwners.build()) : Optional.empty());
    logger.atFine().log("pathCodeOwnerStatus = %s", pathCodeOwnerStatus);
    return pathCodeOwnerStatus;
  }

  /**
   * Computes the code owner status for the given path based on the configured fallback code owners.
   */
  private CodeOwnerStatus getCodeOwnerStatusForFallbackCodeOwners(
      CodeOwnerStatus codeOwnerStatus,
      @Nullable Account.Id implicitApprover,
      ImmutableSet<Account.Id> reviewerAccountIds,
      ImmutableSet<Account.Id> approverAccountIds,
      FallbackCodeOwners fallbackCodeOwners,
      Path absolutePath,
      AtomicReference<String> reason) {
    logger.atFine().log(
        "getting code owner status for fallback code owners (fallback code owners = %s)",
        fallbackCodeOwners);
    switch (fallbackCodeOwners) {
      case NONE:
        logger.atFine().log("no fallback code owners");
        return codeOwnerStatus;
      case ALL_USERS:
        return getCodeOwnerStatusIfAllUsersAreCodeOwners(
            implicitApprover, reviewerAccountIds, approverAccountIds, absolutePath, reason);
    }

    throw new StorageException(
        String.format("unknown fallback code owners configured: %s", fallbackCodeOwners));
  }

  /** Computes the code owner status for the given path assuming that all users are code owners. */
  private CodeOwnerStatus getCodeOwnerStatusIfAllUsersAreCodeOwners(
      @Nullable Account.Id implicitApprover,
      ImmutableSet<Account.Id> reviewerAccountIds,
      ImmutableSet<Account.Id> approverAccountIds,
      Path absolutePath,
      AtomicReference<String> reason) {
    logger.atFine().log(
        "getting code owner status for fallback code owners (all users are fallback code owners)");

    if (implicitApprover != null) {
      reason.set(
          String.format(
              "implicitly approved by the patch set uploader %s who is a %s"
                  + " (all users are %ss)",
              AccountTemplateUtil.getAccountTemplate(implicitApprover),
              CodeOwnerKind.FALLBACK_CODE_OWNER.getDisplayName(),
              CodeOwnerKind.FALLBACK_CODE_OWNER.getDisplayName()));
      return CodeOwnerStatus.APPROVED;
    }

    if (!approverAccountIds.isEmpty()) {
      Optional<Account.Id> approver = approverAccountIds.stream().findAny();
      checkState(approver.isPresent(), "no approver found");
      reason.set(
          String.format(
              "approved by %s who is a %s (all users are %ss)",
              AccountTemplateUtil.getAccountTemplate(approver.get()),
              CodeOwnerKind.FALLBACK_CODE_OWNER.getDisplayName(),
              CodeOwnerKind.FALLBACK_CODE_OWNER.getDisplayName()));
      return CodeOwnerStatus.APPROVED;
    } else if (!reviewerAccountIds.isEmpty()) {
      Optional<Account.Id> reviewer = reviewerAccountIds.stream().findAny();
      checkState(reviewer.isPresent(), "no reviewer found");
      reason.set(
          String.format(
              "reviewer %s is a %s (all users are %ss)",
              AccountTemplateUtil.getAccountTemplate(reviewer.get()),
              CodeOwnerKind.FALLBACK_CODE_OWNER.getDisplayName(),
              CodeOwnerKind.FALLBACK_CODE_OWNER.getDisplayName()));
      return CodeOwnerStatus.PENDING;
    }

    logger.atFine().log("%s has no fallback code owner as a reviewer", absolutePath);
    return CodeOwnerStatus.INSUFFICIENT_REVIEWERS;
  }

  /**
   * Checks whether the given path was approved implicitly, explicitly or by sticky approvals.
   *
   * @param absolutePath the absolute path for which it should be checked whether it is code owner
   *     approved
   * @param codeOwners users that own the path
   * @param codeOwnerKind the kind of the given {@code codeOwners}
   * @param codeOwnersConfig the code-owners plugin configuration that applies to the project that
   *     contains the change for which the code owner statuses are checked
   * @param changedFilesByPatchSetCache cache that allows to lookup changed files by patch set
   * @param input input data for checking if a path is code owner approved
   * @param reason {@link AtomicReference} on which the reason is being set if the path is approved
   * @return whether the path was approved
   */
  private boolean isApproved(
      Path absolutePath,
      CodeOwnerResolverResult codeOwners,
      CodeOwnerKind codeOwnerKind,
      CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig,
      ChangedFilesByPatchSetCache changedFilesByPatchSetCache,
      CodeOwnerApprovalCheckInput input,
      AtomicReference<String> reason) {
    if (input.implicitApprover().isPresent()) {
      if (codeOwners.codeOwnersAccountIds().contains(input.implicitApprover().get())
          || codeOwners.ownedByAllUsers()) {
        // If the uploader of the patch set owns the path, there is an implicit code owner
        // approval from the patch set uploader so that the path is automatically approved.
        reason.set(
            String.format(
                "implicitly approved by the patch set uploader %s who is a %s%s",
                AccountTemplateUtil.getAccountTemplate(input.implicitApprover().get()),
                codeOwnerKind.getDisplayName(),
                codeOwners.ownedByAllUsers()
                    ? String.format(" (all users are %ss)", codeOwnerKind.getDisplayName())
                    : ""));
        return true;
      }
    }

    ImmutableSet<Account.Id> approvers = input.approvers();
    if (!Collections.disjoint(approvers, codeOwners.codeOwnersAccountIds())
        || (codeOwners.ownedByAllUsers() && !approvers.isEmpty())) {
      // At least one of the code owners approved the change.
      Optional<Account.Id> approver =
          codeOwners.ownedByAllUsers()
              ? approvers.stream().findAny()
              : approvers.stream()
                  .filter(accountId -> codeOwners.codeOwnersAccountIds().contains(accountId))
                  .findAny();
      checkState(approver.isPresent(), "no approver found");
      reason.set(
          String.format(
              "approved by %s who is a %s%s",
              AccountTemplateUtil.getAccountTemplate(approver.get()),
              codeOwnerKind.getDisplayName(),
              codeOwners.ownedByAllUsers()
                  ? String.format(" (all users are %ss)", codeOwnerKind.getDisplayName())
                  : ""));
      return true;
    }

    return codeOwnersConfig.areStickyApprovalsEnabled()
        && isApprovedByStickyApproval(
            absolutePath, codeOwners, codeOwnerKind, changedFilesByPatchSetCache, input, reason);
  }

  /**
   * Checks whether the given path is code owner approved by a sticky approval on a previous patch
   * set.
   */
  private boolean isApprovedByStickyApproval(
      Path absolutePath,
      CodeOwnerResolverResult codeOwners,
      CodeOwnerKind codeOwnerKind,
      ChangedFilesByPatchSetCache changedFilesByPatchSetCache,
      CodeOwnerApprovalCheckInput input,
      AtomicReference<String> reason) {
    for (PatchSet.Id patchSetId : input.previouslyApprovedPatchSetsInReverseOrder()) {
      // changedFilesByPatchSetCache doesn't detect renames. That's fine since we only check whether
      // the path has been code-owner approved in a previous patch set.
      if (changedFilesByPatchSetCache.get(patchSetId).stream()
          .anyMatch(
              changedFile ->
                  changedFile.hasNewPath(absolutePath) || changedFile.hasOldPath(absolutePath))) {
        logger.atFine().log(
            "previously approved patch set %d contains path %s", patchSetId.get(), absolutePath);
        Optional<Account.Id> approver =
            input.approversFromPreviousPatchSets().get(patchSetId).stream()
                .filter(
                    accountId ->
                        codeOwners.codeOwnersAccountIds().contains(accountId)
                            || codeOwners.ownedByAllUsers())
                .findAny();
        if (!approver.isPresent()) {
          logger.atFine().log(
              "none of the approvals on previous patch set %d is from a user that owns path %s"
                  + " (approvers=%s)",
              patchSetId.get(),
              absolutePath,
              input.approversFromPreviousPatchSets().get(patchSetId));
          continue;
        }

        reason.set(
            String.format(
                "approved on patch set %d by %s who is a %s%s",
                patchSetId.get(),
                AccountTemplateUtil.getAccountTemplate(approver.get()),
                codeOwnerKind.getDisplayName(),
                codeOwners.ownedByAllUsers()
                    ? String.format(" (all users are %ss)", codeOwnerKind.getDisplayName())
                    : ""));
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether any of the reviewers is a code owner of the path.
   *
   * @param codeOwners users that own the path
   * @param codeOwnerKind the kind of the given {@code codeOwners}
   * @param reviewerAccountIds the IDs of the accounts that are reviewer of the change
   * @param reason {@link AtomicReference} on which the reason is being set if the status for the
   *     path is {@code PENDING}
   * @return whether the path was approved
   */
  private boolean isPending(
      CodeOwnerResolverResult codeOwners,
      CodeOwnerKind codeOwnerKind,
      ImmutableSet<Account.Id> reviewerAccountIds,
      AtomicReference<String> reason) {
    if (!Collections.disjoint(codeOwners.codeOwnersAccountIds(), reviewerAccountIds)
        || (codeOwners.ownedByAllUsers() && !reviewerAccountIds.isEmpty())) {
      Optional<Account.Id> reviewer =
          codeOwners.ownedByAllUsers()
              ? reviewerAccountIds.stream().findAny()
              : reviewerAccountIds.stream()
                  .filter(accountId -> codeOwners.codeOwnersAccountIds().contains(accountId))
                  .findAny();
      checkState(reviewer.isPresent(), "no reviewer found");
      reason.set(
          String.format(
              "reviewer %s is a %s%s",
              AccountTemplateUtil.getAccountTemplate(reviewer.get()),
              codeOwnerKind.getDisplayName(),
              codeOwners.ownedByAllUsers()
                  ? String.format(" (all users are %ss)", codeOwnerKind.getDisplayName())
                  : ""));
      return true;
    }

    return false;
  }

  /**
   * Resolves the given path code owners.
   *
   * @param codeOwnerResolver the {@code CodeOwnerResolver} that should be used to resolve code
   *     owners
   * @param pathCodeOwners the path code owners that should be resolved
   */
  private CodeOwnerResolverResult resolveCodeOwners(
      CodeOwnerResolver codeOwnerResolver, PathCodeOwners pathCodeOwners) {
    return codeOwnerResolver.resolvePathCodeOwners(pathCodeOwners);
  }

  /**
   * Gets the current revision of the destination branch of the given change.
   *
   * <p>This is the revision from which the code owner configs should be read when computing code
   * owners for the files that are touched in the change.
   *
   * @return the current revision of the destination branch of the given change, {@link
   *     Optional#empty()} if the destination branch is not found (e.g. when the initial change is
   *     uploaded to an unborn branch or when the branch got deleted after the change was created)
   */
  private Optional<ObjectId> getDestBranchRevision(Change change) throws IOException {
    try (Repository repository = repoManager.openRepository(change.getProject());
        RevWalk rw = new RevWalk(repository)) {
      Ref ref = repository.exactRef(change.getDest().branch());
      if (ref == null) {
        return Optional.empty();
      }
      return Optional.of(rw.parseCommit(ref.getObjectId()));
    }
  }
}
