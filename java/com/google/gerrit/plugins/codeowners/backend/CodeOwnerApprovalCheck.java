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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginProjectConfigSnapshot;
import com.google.gerrit.plugins.codeowners.backend.config.RequiredApproval;
import com.google.gerrit.plugins.codeowners.common.ChangedFile;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.PureRevertCache;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.util.LabelVote;
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

  private final PermissionBackend permissionBackend;
  private final GitRepositoryManager repoManager;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final ChangedFiles changedFiles;
  private final PureRevertCache pureRevertCache;
  private final Provider<CodeOwnerConfigHierarchy> codeOwnerConfigHierarchyProvider;
  private final Provider<CodeOwnerResolver> codeOwnerResolverProvider;
  private final ApprovalsUtil approvalsUtil;
  private final CodeOwnerMetrics codeOwnerMetrics;

  @Inject
  CodeOwnerApprovalCheck(
      PermissionBackend permissionBackend,
      GitRepositoryManager repoManager,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      ChangedFiles changedFiles,
      PureRevertCache pureRevertCache,
      Provider<CodeOwnerConfigHierarchy> codeOwnerConfigHierarchyProvider,
      Provider<CodeOwnerResolver> codeOwnerResolverProvider,
      ApprovalsUtil approvalsUtil,
      CodeOwnerMetrics codeOwnerMetrics) {
    this.permissionBackend = permissionBackend;
    this.repoManager = repoManager;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.changedFiles = changedFiles;
    this.pureRevertCache = pureRevertCache;
    this.codeOwnerConfigHierarchyProvider = codeOwnerConfigHierarchyProvider;
    this.codeOwnerResolverProvider = codeOwnerResolverProvider;
    this.approvalsUtil = approvalsUtil;
    this.codeOwnerMetrics = codeOwnerMetrics;
  }

  /**
   * Returns the paths of the files in the given patch set that are owned by the specified account.
   *
   * @param changeNotes the change notes for which the owned files should be returned
   * @param patchSet the patch set for which the owned files should be returned
   * @param accountId account ID of the code owner for which the owned files should be returned
   * @param start number of owned paths to skip
   * @param limit the max number of owned paths that should be returned (0 = unlimited)
   * @return the paths of the files in the given patch set that are owned by the specified account
   * @throws ResourceConflictException if the destination branch of the change no longer exists
   */
  public ImmutableList<Path> getOwnedPaths(
      ChangeNotes changeNotes, PatchSet patchSet, Account.Id accountId, int start, int limit)
      throws ResourceConflictException {
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
      Stream<Path> ownedPaths =
          getFileStatusesForAccount(changeNotes, patchSet, accountId)
              .flatMap(
                  fileCodeOwnerStatus ->
                      Stream.of(
                              fileCodeOwnerStatus.newPathStatus(),
                              fileCodeOwnerStatus.oldPathStatus())
                          .filter(Optional::isPresent)
                          .map(Optional::get))
              .filter(
                  pathCodeOwnerStatus -> pathCodeOwnerStatus.status() == CodeOwnerStatus.APPROVED)
              .map(PathCodeOwnerStatus::path);
      if (start > 0) {
        ownedPaths = ownedPaths.skip(start);
      }
      if (limit > 0) {
        ownedPaths = ownedPaths.limit(limit);
      }
      return ownedPaths.collect(toImmutableList());
    } catch (IOException | PatchListNotAvailableException | DiffNotAvailableException e) {
      throw new CodeOwnersInternalServerErrorException(
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
      throws ResourceConflictException, IOException, PatchListNotAvailableException,
          DiffNotAvailableException {
    requireNonNull(changeNotes, "changeNotes");
    logger.atFine().log(
        "checking if change %d in project %s is submittable",
        changeNotes.getChangeId().get(), changeNotes.getProjectName());
    CodeOwnerConfigHierarchy codeOwnerConfigHierarchy = codeOwnerConfigHierarchyProvider.get();
    CodeOwnerResolver codeOwnerResolver = codeOwnerResolverProvider.get().enforceVisibility(false);
    try {
      boolean isSubmittable =
          !getFileStatuses(codeOwnerConfigHierarchy, codeOwnerResolver, changeNotes)
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
      codeOwnerMetrics.codeOwnerConfigCacheReadsPerChange.record(
          codeOwnerResolver.getCodeOwnerCounters().getCacheReadCount());
    }
  }

  /**
   * Gets the code owner statuses for all files/paths that were changed in the current revision of
   * the given change as a set.
   *
   * @param start number of file statuses to skip
   * @param limit the max number of file statuses that should be returned (0 = unlimited)
   * @see #getFileStatuses(CodeOwnerConfigHierarchy, CodeOwnerResolver, ChangeNotes)
   */
  public ImmutableSet<FileCodeOwnerStatus> getFileStatusesAsSet(
      ChangeNotes changeNotes, int start, int limit)
      throws ResourceConflictException, IOException, PatchListNotAvailableException,
          DiffNotAvailableException {
    requireNonNull(changeNotes, "changeNotes");
    try (Timer0.Context ctx = codeOwnerMetrics.computeFileStatuses.start()) {
      logger.atFine().log(
          "compute file statuses (project = %s, change = %d, start = %d, limit = %d)",
          changeNotes.getProjectName(), changeNotes.getChangeId().get(), start, limit);
      Stream<FileCodeOwnerStatus> fileStatuses =
          getFileStatuses(
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
      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy,
      CodeOwnerResolver codeOwnerResolver,
      ChangeNotes changeNotes)
      throws ResourceConflictException, IOException, PatchListNotAvailableException,
          DiffNotAvailableException {
    requireNonNull(changeNotes, "changeNotes");
    try (Timer0.Context ctx = codeOwnerMetrics.prepareFileStatusComputation.start()) {
      logger.atFine().log(
          "prepare stream to compute file statuses (project = %s, change = %d)",
          changeNotes.getProjectName(), changeNotes.getChangeId().get());

      CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig =
          codeOwnersPluginConfiguration.getProjectConfig(changeNotes.getProjectName());

      Account.Id changeOwner = changeNotes.getChange().getOwner();
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
                ChangeMessagesUtil.getAccountTemplate(patchSetUploader)));
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

      boolean implicitApprovalConfig = codeOwnersConfig.areImplicitApprovalsEnabled();
      boolean enableImplicitApproval =
          implicitApprovalConfig && changeOwner.equals(patchSetUploader);
      logger.atFine().log(
          "changeOwner = %d, patchSetUploader = %d, implict approval config = %s\n=> implicit approval is %s",
          changeOwner.get(),
          patchSetUploader.get(),
          implicitApprovalConfig,
          enableImplicitApproval ? "enabled" : "disabled");

      ImmutableList<PatchSetApproval> currentPatchSetApprovals =
          getCurrentPatchSetApprovals(changeNotes);

      RequiredApproval requiredApproval = codeOwnersConfig.getRequiredApproval();
      logger.atFine().log("requiredApproval = %s", requiredApproval);

      ImmutableSet<RequiredApproval> overrideApprovals = codeOwnersConfig.getOverrideApprovals();
      ImmutableSet<PatchSetApproval> overrides =
          getOverride(currentPatchSetApprovals, overrideApprovals, patchSetUploader);
      logger.atFine().log(
          "hasOverride = %s (overrideApprovals = %s, overrides = %s)",
          !overrides.isEmpty(),
          overrideApprovals.stream()
              .map(
                  overrideApproval ->
                      String.format(
                          "%s (ignoreSelfApproval = %s)",
                          overrideApproval, overrideApproval.labelType().isIgnoreSelfApproval()))
              .collect(toImmutableList()),
          overrides);

      BranchNameKey branch = changeNotes.getChange().getDest();
      ObjectId revision = getDestBranchRevision(changeNotes.getChange());
      logger.atFine().log("dest branch %s has revision %s", branch.branch(), revision.name());

      CodeOwnerResolverResult globalCodeOwners =
          codeOwnerResolver.resolveGlobalCodeOwners(changeNotes.getProjectName());
      logger.atFine().log("global code owners = %s", globalCodeOwners);

      ImmutableSet<Account.Id> reviewerAccountIds =
          getReviewerAccountIds(requiredApproval, changeNotes, patchSetUploader);
      ImmutableSet<Account.Id> approverAccountIds =
          getApproverAccountIds(currentPatchSetApprovals, requiredApproval, patchSetUploader);
      logger.atFine().log("reviewers = %s, approvers = %s", reviewerAccountIds, approverAccountIds);

      FallbackCodeOwners fallbackCodeOwners = codeOwnersConfig.getFallbackCodeOwners();

      return changedFiles
          .getOrCompute(changeNotes.getProjectName(), changeNotes.getCurrentPatchSet().commitId())
          .stream()
          .map(
              changedFile ->
                  getFileStatus(
                      codeOwnerConfigHierarchy,
                      codeOwnerResolver,
                      branch,
                      revision,
                      globalCodeOwners,
                      enableImplicitApproval ? changeOwner : null,
                      reviewerAccountIds,
                      approverAccountIds,
                      fallbackCodeOwners,
                      overrides,
                      changedFile));
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
   * @param changeNotes the notes of the change for which the code owner statuses should be returned
   * @param patchSet the patch set for which the code owner statuses should be returned
   * @param accountId the ID of the account for which an approval should be assumed
   */
  @VisibleForTesting
  public Stream<FileCodeOwnerStatus> getFileStatusesForAccount(
      ChangeNotes changeNotes, PatchSet patchSet, Account.Id accountId)
      throws ResourceConflictException, IOException, PatchListNotAvailableException,
          DiffNotAvailableException {
    requireNonNull(changeNotes, "changeNotes");
    requireNonNull(patchSet, "patchSet");
    requireNonNull(accountId, "accountId");
    try (Timer0.Context ctx = codeOwnerMetrics.prepareFileStatusComputationForAccount.start()) {
      logger.atFine().log(
          "prepare stream to compute file statuses for account %d (project = %s, change = %d,"
              + " patch set = %d)",
          accountId.get(),
          changeNotes.getProjectName(),
          changeNotes.getChangeId().get(),
          patchSet.id().get());

      CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig =
          codeOwnersPluginConfiguration.getProjectConfig(changeNotes.getProjectName());

      RequiredApproval requiredApproval = codeOwnersConfig.getRequiredApproval();
      logger.atFine().log("requiredApproval = %s", requiredApproval);

      BranchNameKey branch = changeNotes.getChange().getDest();
      ObjectId revision = getDestBranchRevision(changeNotes.getChange());
      logger.atFine().log("dest branch %s has revision %s", branch.branch(), revision.name());

      boolean isProjectOwner = isProjectOwner(changeNotes.getProjectName(), accountId);
      FallbackCodeOwners fallbackCodeOwners = codeOwnersConfig.getFallbackCodeOwners();
      logger.atFine().log(
          "fallbackCodeOwner = %s, isProjectOwner = %s", fallbackCodeOwners, isProjectOwner);

      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy = codeOwnerConfigHierarchyProvider.get();
      CodeOwnerResolver codeOwnerResolver =
          codeOwnerResolverProvider.get().enforceVisibility(false);
      return changedFiles.getOrCompute(changeNotes.getProjectName(), patchSet.commitId()).stream()
          .map(
              changedFile ->
                  getFileStatus(
                      codeOwnerConfigHierarchy,
                      codeOwnerResolver,
                      branch,
                      revision,
                      /* globalCodeOwners= */ CodeOwnerResolverResult.createEmpty(),
                      // Do not check for implicit approvals since implicit approvals of other users
                      // should be ignored. For the given account we do not need to check for
                      // implicit approvals since all owned files are already covered by the
                      // explicit approval.
                      /* implicitApprover= */ null,
                      /* reviewerAccountIds= */ ImmutableSet.of(),
                      // Assume an explicit approval of the given account.
                      /* approverAccountIds= */ ImmutableSet.of(accountId),
                      fallbackCodeOwners,
                      /* overrides= */ ImmutableSet.of(),
                      changedFile));
    }
  }

  private boolean isPureRevert(ChangeNotes changeNotes) throws IOException {
    try {
      return changeNotes.getChange().getRevertOf() != null
          && pureRevertCache.isPureRevert(changeNotes);
    } catch (BadRequestException e) {
      throw new CodeOwnersInternalServerErrorException(
          String.format(
              "failed to check if change %s in project %s is a pure revert",
              changeNotes.getChangeId(), changeNotes.getProjectName()),
          e);
    }
  }

  private Stream<FileCodeOwnerStatus> getAllPathsAsApproved(
      ChangeNotes changeNotes, PatchSet patchSet, String reason)
      throws IOException, PatchListNotAvailableException, DiffNotAvailableException {
    return changedFiles.getOrCompute(changeNotes.getProjectName(), patchSet.commitId()).stream()
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
      BranchNameKey branch,
      ObjectId revision,
      CodeOwnerResolverResult globalCodeOwners,
      @Nullable Account.Id implicitApprover,
      ImmutableSet<Account.Id> reviewerAccountIds,
      ImmutableSet<Account.Id> approverAccountIds,
      FallbackCodeOwners fallbackCodeOwners,
      ImmutableSet<PatchSetApproval> overrides,
      ChangedFile changedFile) {
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
                          branch,
                          revision,
                          globalCodeOwners,
                          implicitApprover,
                          reviewerAccountIds,
                          approverAccountIds,
                          fallbackCodeOwners,
                          overrides,
                          newPath));

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
                    branch,
                    revision,
                    globalCodeOwners,
                    implicitApprover,
                    reviewerAccountIds,
                    approverAccountIds,
                    fallbackCodeOwners,
                    overrides,
                    changedFile.oldPath().get()));
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
      BranchNameKey branch,
      ObjectId revision,
      CodeOwnerResolverResult globalCodeOwners,
      @Nullable Account.Id implicitApprover,
      ImmutableSet<Account.Id> reviewerAccountIds,
      ImmutableSet<Account.Id> approverAccountIds,
      FallbackCodeOwners fallbackCodeOwners,
      ImmutableSet<PatchSetApproval> overrides,
      Path absolutePath) {
    logger.atFine().log("computing path status for %s", absolutePath);

    if (!overrides.isEmpty()) {
      logger.atFine().log(
          "the status for path %s is %s since an override is present (overrides = %s)",
          absolutePath, CodeOwnerStatus.APPROVED.name(), overrides);
      Optional<PatchSetApproval> override = overrides.stream().findAny();
      checkState(override.isPresent(), "no override found");
      return PathCodeOwnerStatus.create(
          absolutePath,
          CodeOwnerStatus.APPROVED,
          String.format(
              "override approval %s by %s is present",
              override.get().label() + LabelValue.formatValue(override.get().value()),
              ChangeMessagesUtil.getAccountTemplate(override.get().accountId())));
    }

    AtomicReference<CodeOwnerStatus> codeOwnerStatus =
        new AtomicReference<>(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    AtomicReference<String> reason = new AtomicReference<>(/* initialValue= */ null);

    if (isApproved(
        absolutePath,
        globalCodeOwners,
        CodeOwnerKind.GLOBAL_CODE_OWNER,
        approverAccountIds,
        implicitApprover,
        reason)) {
      codeOwnerStatus.set(CodeOwnerStatus.APPROVED);
    } else {
      logger.atFine().log("%s was not approved by a global code owner", absolutePath);

      if (isPending(
          absolutePath,
          globalCodeOwners,
          CodeOwnerKind.GLOBAL_CODE_OWNER,
          reviewerAccountIds,
          reason)) {
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
                    approverAccountIds,
                    implicitApprover,
                    reason)) {
                  codeOwnerStatus.set(CodeOwnerStatus.APPROVED);
                  return false;
                } else if (isPending(
                    absolutePath, codeOwners, codeOwnerKind, reviewerAccountIds, reason)) {
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
                branch,
                implicitApprover,
                reviewerAccountIds,
                approverAccountIds,
                fallbackCodeOwners,
                absolutePath,
                reason);
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

    PathCodeOwnerStatus pathCodeOwnerStatus =
        PathCodeOwnerStatus.create(absolutePath, codeOwnerStatus.get(), reason.get());
    logger.atFine().log("pathCodeOwnerStatus = %s", pathCodeOwnerStatus);
    return pathCodeOwnerStatus;
  }

  /**
   * Gets the code owner status for the given path when project owners are configured as fallback
   * code owners.
   */
  private CodeOwnerStatus getCodeOwnerStatusForProjectOwnersAsFallbackCodeOwners(
      BranchNameKey branch,
      @Nullable Account.Id implicitApprover,
      ImmutableSet<Account.Id> reviewerAccountIds,
      ImmutableSet<Account.Id> approverAccountIds,
      Path absolutePath,
      AtomicReference<String> reason) {
    logger.atFine().log(
        "computing code owner status for %s with project owners as fallback code owners",
        absolutePath);

    CodeOwnerStatus codeOwnerStatus = CodeOwnerStatus.INSUFFICIENT_REVIEWERS;
    if (isApprovedByProjectOwner(
        branch.project(), absolutePath, approverAccountIds, implicitApprover, reason)) {
      codeOwnerStatus = CodeOwnerStatus.APPROVED;
    } else if (isPendingByProjectOwner(
        branch.project(), absolutePath, reviewerAccountIds, reason)) {
      codeOwnerStatus = CodeOwnerStatus.PENDING;
    }

    logger.atFine().log("codeOwnerStatus = %s", codeOwnerStatus);
    return codeOwnerStatus;
  }

  private boolean isApprovedByProjectOwner(
      Project.NameKey projectName,
      Path absolutePath,
      ImmutableSet<Account.Id> approverAccountIds,
      @Nullable Account.Id implicitApprover,
      AtomicReference<String> reason) {
    return (implicitApprover != null
            && isImplicitlyApprovedByProjectOwner(
                projectName, absolutePath, implicitApprover, reason))
        || isExplicitlyApprovedByProjectOwner(
            projectName, absolutePath, approverAccountIds, reason);
  }

  private boolean isImplicitlyApprovedByProjectOwner(
      Project.NameKey projectName,
      Path absolutePath,
      Account.Id implicitApprover,
      AtomicReference<String> reason) {
    requireNonNull(implicitApprover, "implicitApprover");
    if (isProjectOwner(projectName, implicitApprover)) {
      // The uploader of the patch set is a project owner and thus a code owner. This means there
      // is an implicit code owner approval from the patch set uploader so that the path is
      // automatically approved.
      logger.atFine().log(
          "%s was implicitly approved by the patch set uploader who is a project owner",
          absolutePath);
      reason.set(
          String.format(
              "implicitly approved by the patch set uploader %s who is a %s"
                  + " (all project owners are %ss)",
              ChangeMessagesUtil.getAccountTemplate(implicitApprover),
              CodeOwnerKind.FALLBACK_CODE_OWNER.getDisplayName(),
              CodeOwnerKind.FALLBACK_CODE_OWNER.getDisplayName()));
      return true;
    }
    return false;
  }

  private boolean isExplicitlyApprovedByProjectOwner(
      Project.NameKey projectName,
      Path absolutePath,
      ImmutableSet<Account.Id> approverAccountIds,
      AtomicReference<String> reason) {
    Optional<Account.Id> approver =
        approverAccountIds.stream()
            .filter(approverAccountId -> isProjectOwner(projectName, approverAccountId))
            .findAny();
    if (approver.isPresent()) {
      // At least one of the approvers is a project owner and thus a code owner.
      logger.atFine().log(
          "%s was approved by %s who is a project owner", absolutePath, approver.get());
      reason.set(
          String.format(
              "approved by %s who is a %s (all project owners are %ss)",
              ChangeMessagesUtil.getAccountTemplate(approver.get()),
              CodeOwnerKind.FALLBACK_CODE_OWNER.getDisplayName(),
              CodeOwnerKind.FALLBACK_CODE_OWNER.getDisplayName()));
      return true;
    }
    return false;
  }

  private boolean isPendingByProjectOwner(
      Project.NameKey projectName,
      Path absolutePath,
      ImmutableSet<Account.Id> reviewerAccountIds,
      AtomicReference<String> reason) {
    Optional<Account.Id> reviewer =
        reviewerAccountIds.stream()
            .filter(reviewerAccountId -> isProjectOwner(projectName, reviewerAccountId))
            .findAny();
    if (reviewer.isPresent()) {
      // At least one of the reviewers is a project owner and thus a code owner.
      logger.atFine().log("reviewer %s is a project owner and owns %s", reviewer, absolutePath);
      reason.set(
          String.format(
              "reviewer %s is a %s (all project owners are %ss)",
              ChangeMessagesUtil.getAccountTemplate(reviewer.get()),
              CodeOwnerKind.FALLBACK_CODE_OWNER.getDisplayName(),
              CodeOwnerKind.FALLBACK_CODE_OWNER.getDisplayName()));
      return true;
    }

    return false;
  }

  /**
   * Computes the code owner status for the given path based on the configured fallback code owners.
   */
  private CodeOwnerStatus getCodeOwnerStatusForFallbackCodeOwners(
      CodeOwnerStatus codeOwnerStatus,
      BranchNameKey branch,
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
      case PROJECT_OWNERS:
        return getCodeOwnerStatusForProjectOwnersAsFallbackCodeOwners(
            branch, implicitApprover, reviewerAccountIds, approverAccountIds, absolutePath, reason);
      case ALL_USERS:
        return getCodeOwnerStatusIfAllUsersAreCodeOwners(
            implicitApprover, reviewerAccountIds, approverAccountIds, absolutePath, reason);
    }

    throw new CodeOwnersInternalServerErrorException(
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
      logger.atFine().log(
          "%s was implicitly approved by the patch set uploader %s who is a fallback code owner",
          absolutePath, implicitApprover);
      reason.set(
          String.format(
              "implicitly approved by the patch set uploader %s who is a %s"
                  + " (all users are %ss)",
              ChangeMessagesUtil.getAccountTemplate(implicitApprover),
              CodeOwnerKind.FALLBACK_CODE_OWNER.getDisplayName(),
              CodeOwnerKind.FALLBACK_CODE_OWNER.getDisplayName()));
      return CodeOwnerStatus.APPROVED;
    }

    if (!approverAccountIds.isEmpty()) {
      Optional<Account.Id> approver = approverAccountIds.stream().findAny();
      checkState(approver.isPresent(), "no approver found");
      logger.atFine().log(
          "%s was approved by %s who is a fallback code owner", absolutePath, approver.get());
      reason.set(
          String.format(
              "approved by %s who is a %s (all users are %ss)",
              ChangeMessagesUtil.getAccountTemplate(approver.get()),
              CodeOwnerKind.FALLBACK_CODE_OWNER.getDisplayName(),
              CodeOwnerKind.FALLBACK_CODE_OWNER.getDisplayName()));
      return CodeOwnerStatus.APPROVED;
    } else if (!reviewerAccountIds.isEmpty()) {
      Optional<Account.Id> reviewer = reviewerAccountIds.stream().findAny();
      checkState(reviewer.isPresent(), "no reviewer found");
      logger.atFine().log(
          "%s has %s as a reviewer who is a fallback code owner", absolutePath, reviewer.get());
      reason.set(
          String.format(
              "reviewer %s is a %s (all users are %ss)",
              ChangeMessagesUtil.getAccountTemplate(reviewer.get()),
              CodeOwnerKind.FALLBACK_CODE_OWNER.getDisplayName(),
              CodeOwnerKind.FALLBACK_CODE_OWNER.getDisplayName()));
      return CodeOwnerStatus.PENDING;
    }

    logger.atFine().log("%s has no fallback code owner as a reviewer", absolutePath);
    return CodeOwnerStatus.INSUFFICIENT_REVIEWERS;
  }

  /**
   * Checks whether the given path was implicitly or explicitly approved.
   *
   * @param absolutePath the path of the file for which the code owner approval is checked
   * @param codeOwners users that own the path
   * @param codeOwnerKind the kind of the given {@code codeOwners}
   * @param approverAccountIds the IDs of the accounts that have approved the change
   * @param implicitApprover the ID of the account the could be an implicit approver (aka last patch
   *     set uploader)
   * @param reason {@link AtomicReference} on which the reason is being set if the path is approved
   * @return whether the path was approved
   */
  private boolean isApproved(
      Path absolutePath,
      CodeOwnerResolverResult codeOwners,
      CodeOwnerKind codeOwnerKind,
      ImmutableSet<Account.Id> approverAccountIds,
      @Nullable Account.Id implicitApprover,
      AtomicReference<String> reason) {
    if (implicitApprover != null) {
      if (codeOwners.codeOwnersAccountIds().contains(implicitApprover)
          || codeOwners.ownedByAllUsers()) {
        // If the uploader of the patch set owns the path, there is an implicit code owner
        // approval from the patch set uploader so that the path is automatically approved.
        logger.atFine().log(
            "%s was implicitly approved by the patch set uploader %s who is a %s",
            absolutePath, implicitApprover, codeOwnerKind.getDisplayName());
        reason.set(
            String.format(
                "implicitly approved by the patch set uploader %s who is a %s%s",
                ChangeMessagesUtil.getAccountTemplate(implicitApprover),
                codeOwnerKind.getDisplayName(),
                codeOwners.ownedByAllUsers()
                    ? String.format(" (all users are %ss)", codeOwnerKind.getDisplayName())
                    : ""));
        return true;
      }
    }

    if (!Collections.disjoint(approverAccountIds, codeOwners.codeOwnersAccountIds())
        || (codeOwners.ownedByAllUsers() && !approverAccountIds.isEmpty())) {
      // At least one of the code owners approved the change.
      Optional<Account.Id> approver =
          codeOwners.ownedByAllUsers()
              ? approverAccountIds.stream().findAny()
              : approverAccountIds.stream()
                  .filter(accountId -> codeOwners.codeOwnersAccountIds().contains(accountId))
                  .findAny();
      checkState(approver.isPresent(), "no approver found");
      logger.atFine().log(
          "%s was explicitly approved by %s who is a %s",
          absolutePath, approver, codeOwnerKind.getDisplayName());
      reason.set(
          String.format(
              "approved by %s who is a %s%s",
              ChangeMessagesUtil.getAccountTemplate(approver.get()),
              codeOwnerKind.getDisplayName(),
              codeOwners.ownedByAllUsers()
                  ? String.format(" (all users are %ss)", codeOwnerKind.getDisplayName())
                  : ""));
      return true;
    }

    return false;
  }

  /**
   * Checks whether any of the reviewers is a code owner of the path.
   *
   * @param absolutePath the path of the file for which the code owner status is checked
   * @param codeOwners users that own the path
   * @param codeOwnerKind the kind of the given {@code codeOwners}
   * @param reviewerAccountIds the IDs of the accounts that are reviewer of the change
   * @param reason {@link AtomicReference} on which the reason is being set if the status for the
   *     path is {@code PENDING}
   * @return whether the path was approved
   */
  private boolean isPending(
      Path absolutePath,
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
      logger.atFine().log(
          "reviewer %s owns %s and is a %s",
          reviewer, absolutePath, codeOwnerKind.getDisplayName());
      reason.set(
          String.format(
              "reviewer %s is a %s%s",
              ChangeMessagesUtil.getAccountTemplate(reviewer.get()),
              codeOwnerKind.getDisplayName(),
              codeOwners.ownedByAllUsers()
                  ? String.format(" (all users are %ss)", codeOwnerKind.getDisplayName())
                  : ""));
      return true;
    }

    return false;
  }

  /** Whether the given account is a project owner of the given project. */
  private boolean isProjectOwner(Project.NameKey project, Account.Id accountId) {
    try {
      boolean isProjectOwner =
          permissionBackend
              .absentUser(accountId)
              .project(project)
              .test(ProjectPermission.WRITE_CONFIG);
      if (isProjectOwner) {
        logger.atFine().log("Account %d is a project owner", accountId.get());
      }
      return isProjectOwner;
    } catch (PermissionBackendException e) {
      throw new CodeOwnersInternalServerErrorException(
          String.format(
              "failed to check owner permission of project %s for account %d",
              project.get(), accountId.get()),
          e);
    }
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
   * Gets the IDs of the accounts that are reviewer on the given change.
   *
   * @param changeNotes the change notes
   */
  private ImmutableSet<Account.Id> getReviewerAccountIds(
      RequiredApproval requiredApproval, ChangeNotes changeNotes, Account.Id patchSetUploader) {
    ImmutableSet<Account.Id> reviewerAccountIds =
        changeNotes.getReviewers().byState(ReviewerStateInternal.REVIEWER);
    if (requiredApproval.labelType().isIgnoreSelfApproval()
        && reviewerAccountIds.contains(patchSetUploader)) {
      logger.atFine().log(
          "Removing patch set uploader %s from reviewers since the label of the required"
              + " approval (%s) is configured to ignore self approvals",
          patchSetUploader, requiredApproval.labelType());
      return filterOutAccount(reviewerAccountIds, patchSetUploader);
    }
    return reviewerAccountIds;
  }

  /**
   * Gets the IDs of the accounts that posted a patch set approval on the given revisions that
   * counts as code owner approval.
   *
   * @param requiredApproval approval that is required from code owners to approve the files in a
   *     change
   */
  private ImmutableSet<Account.Id> getApproverAccountIds(
      ImmutableList<PatchSetApproval> currentPatchSetApprovals,
      RequiredApproval requiredApproval,
      Account.Id patchSetUploader) {
    ImmutableSet<Account.Id> approverAccountIds =
        currentPatchSetApprovals.stream()
            .filter(requiredApproval::isApprovedBy)
            .map(PatchSetApproval::accountId)
            .collect(toImmutableSet());

    if (requiredApproval.labelType().isIgnoreSelfApproval()
        && approverAccountIds.contains(patchSetUploader)) {
      logger.atFine().log(
          "Removing patch set uploader %s from approvers since the label of the required"
              + " approval (%s) is configured to ignore self approvals",
          patchSetUploader, requiredApproval.labelType());
      return filterOutAccount(approverAccountIds, patchSetUploader);
    }

    return approverAccountIds;
  }

  private ImmutableList<PatchSetApproval> getCurrentPatchSetApprovals(ChangeNotes changeNotes) {
    try (Timer0.Context ctx = codeOwnerMetrics.computePatchSetApprovals.start()) {
      return ImmutableList.copyOf(
          approvalsUtil.byPatchSet(
              changeNotes,
              changeNotes.getCurrentPatchSet().id(),
              /** revWalk */
              null,
              /** repoConfig */
              null));
    }
  }

  private ImmutableSet<Account.Id> filterOutAccount(
      ImmutableSet<Account.Id> accountIds, Account.Id accountIdToFilterOut) {
    return accountIds.stream()
        .filter(accountId -> !accountId.equals(accountIdToFilterOut))
        .collect(toImmutableSet());
  }

  /**
   * Gets the overrides that were applied on the change.
   *
   * @param overrideApprovals approvals that count as override for the code owners submit check.
   * @param patchSetUploader account ID of the patch set uploader
   * @return the overrides that were applied on the change
   */
  private ImmutableSet<PatchSetApproval> getOverride(
      ImmutableList<PatchSetApproval> currentPatchSetApprovals,
      ImmutableSet<RequiredApproval> overrideApprovals,
      Account.Id patchSetUploader) {
    ImmutableSet<RequiredApproval> overrideApprovalsThatIgnoreSelfApprovals =
        overrideApprovals.stream()
            .filter(overrideApproval -> overrideApproval.labelType().isIgnoreSelfApproval())
            .collect(toImmutableSet());
    return currentPatchSetApprovals.stream()
        .filter(
            approval -> {
              // If the approval is from the patch set uploader and if it matches any of the labels
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
                    .anyMatch(overrideApproval -> overrideApproval.isApprovedBy(patchSetApproval)))
        .collect(toImmutableSet());
  }

  /**
   * Gets the current revision of the destination branch of the given change.
   *
   * <p>This is the revision from which the code owner configs should be read when computing code
   * owners for the files that are touched in the change.
   *
   * @throws ResourceConflictException thrown if the destination branch is not found, e.g. when the
   *     branch got deleted after the change was created
   */
  private ObjectId getDestBranchRevision(Change change)
      throws IOException, ResourceConflictException {
    try (Repository repository = repoManager.openRepository(change.getProject());
        RevWalk rw = new RevWalk(repository)) {
      Ref ref = repository.exactRef(change.getDest().branch());
      if (ref == null) {
        throw new ResourceConflictException("destination branch not found");
      }
      return rw.parseCommit(ref.getObjectId());
    }
  }
}
