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
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.RequiredApproval;
import com.google.gerrit.plugins.codeowners.common.ChangedFile;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.PureRevertCache;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
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
import java.util.stream.StreamSupport;
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
  private final CodeOwnerConfigHierarchy codeOwnerConfigHierarchy;
  private final Provider<CodeOwnerResolver> codeOwnerResolver;
  private final ApprovalsUtil approvalsUtil;
  private final CodeOwnerMetrics codeOwnerMetrics;

  @Inject
  CodeOwnerApprovalCheck(
      PermissionBackend permissionBackend,
      GitRepositoryManager repoManager,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      ChangedFiles changedFiles,
      PureRevertCache pureRevertCache,
      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy,
      Provider<CodeOwnerResolver> codeOwnerResolver,
      ApprovalsUtil approvalsUtil,
      CodeOwnerMetrics codeOwnerMetrics) {
    this.permissionBackend = permissionBackend;
    this.repoManager = repoManager;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.changedFiles = changedFiles;
    this.pureRevertCache = pureRevertCache;
    this.codeOwnerConfigHierarchy = codeOwnerConfigHierarchy;
    this.codeOwnerResolver = codeOwnerResolver;
    this.approvalsUtil = approvalsUtil;
    this.codeOwnerMetrics = codeOwnerMetrics;
  }

  /**
   * Returns the paths of the files in the given patch set that are owned by the specified account.
   *
   * @param changeNotes the change notes for which the owned files should be returned
   * @param patchSet the patch set for which the owned files should be returned
   * @param accountId account ID of the code owner for which the owned files should be returned
   * @return the paths of the files in the given patch set that are owned by the specified account
   * @throws ResourceConflictException if the destination branch of the change no longer exists
   */
  public ImmutableList<Path> getOwnedPaths(
      ChangeNotes changeNotes, PatchSet patchSet, Account.Id accountId)
      throws ResourceConflictException {
    try (Timer0.Context ctx = codeOwnerMetrics.computeOwnedPaths.start()) {
      logger.atFine().log(
          "compute owned paths for account %d (project = %s, change = %d, patch set = %d)",
          accountId.get(),
          changeNotes.getProjectName(),
          changeNotes.getChangeId().get(),
          patchSet.id().get());
      return getFileStatusesForAccount(changeNotes, patchSet, accountId)
          .flatMap(
              fileCodeOwnerStatus ->
                  Stream.of(
                          fileCodeOwnerStatus.newPathStatus(), fileCodeOwnerStatus.oldPathStatus())
                      .filter(Optional::isPresent)
                      .map(Optional::get))
          .filter(pathCodeOwnerStatus -> pathCodeOwnerStatus.status() == CodeOwnerStatus.APPROVED)
          .map(PathCodeOwnerStatus::path)
          .sorted(comparing(Path::toString))
          .collect(toImmutableList());
    } catch (IOException | PatchListNotAvailableException e) {
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
      throws ResourceConflictException, IOException, PatchListNotAvailableException {
    requireNonNull(changeNotes, "changeNotes");
    logger.atFine().log(
        "checking if change %d in project %s is submittable",
        changeNotes.getChangeId().get(), changeNotes.getProjectName());
    boolean isSubmittable =
        !getFileStatuses(changeNotes)
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
  }

  /**
   * Gets the code owner statuses for all files/paths that were changed in the current revision of
   * the given change as a set.
   *
   * @see #getFileStatuses(ChangeNotes)
   */
  public ImmutableSet<FileCodeOwnerStatus> getFileStatusesAsSet(ChangeNotes changeNotes)
      throws ResourceConflictException, IOException, PatchListNotAvailableException {
    try (Timer0.Context ctx = codeOwnerMetrics.computeFileStatuses.start()) {
      logger.atFine().log(
          "compute file statuses (project = %s, change = %d)",
          changeNotes.getProjectName(), changeNotes.getChangeId().get());
      return getFileStatuses(changeNotes).collect(toImmutableSet());
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
   * @param changeNotes the notes of the change for which the current code owner statuses should be
   *     returned
   */
  public Stream<FileCodeOwnerStatus> getFileStatuses(ChangeNotes changeNotes)
      throws ResourceConflictException, IOException, PatchListNotAvailableException {
    requireNonNull(changeNotes, "changeNotes");
    try (Timer0.Context ctx = codeOwnerMetrics.prepareFileStatusComputation.start()) {
      logger.atFine().log(
          "prepare stream to compute file statuses (project = %s, change = %d)",
          changeNotes.getProjectName(), changeNotes.getChangeId().get());

      if (codeOwnersPluginConfiguration.arePureRevertsExempted(changeNotes.getProjectName())
          && isPureRevert(changeNotes)) {
        return getAllPathsAsApproved(changeNotes, changeNotes.getCurrentPatchSet());
      }

      boolean enableImplicitApprovalFromUploader =
          codeOwnersPluginConfiguration.areImplicitApprovalsEnabled(changeNotes.getProjectName());
      Account.Id patchSetUploader = changeNotes.getCurrentPatchSet().uploader();
      logger.atFine().log(
          "patchSetUploader = %d, implicit approval from uploader is %s",
          patchSetUploader.get(), enableImplicitApprovalFromUploader ? "enabled" : "disabled");

      RequiredApproval requiredApproval =
          codeOwnersPluginConfiguration.getRequiredApproval(changeNotes.getProjectName());
      logger.atFine().log("requiredApproval = %s", requiredApproval);

      ImmutableSet<RequiredApproval> overrideApprovals =
          codeOwnersPluginConfiguration.getOverrideApproval(changeNotes.getProjectName());
      boolean hasOverride = hasOverride(overrideApprovals, changeNotes, patchSetUploader);
      logger.atFine().log(
          "hasOverride = %s (overrideApprovals = %s)",
          hasOverride,
          overrideApprovals.stream()
              .map(
                  overrideApproval ->
                      String.format(
                          "%s (ignoreSelfApproval = %s)",
                          overrideApproval, overrideApproval.labelType().isIgnoreSelfApproval()))
              .collect(toImmutableList()));

      BranchNameKey branch = changeNotes.getChange().getDest();
      ObjectId revision = getDestBranchRevision(changeNotes.getChange());
      logger.atFine().log("dest branch %s has revision %s", branch.branch(), revision.name());

      CodeOwnerResolverResult globalCodeOwners =
          codeOwnerResolver
              .get()
              .enforceVisibility(false)
              .resolveGlobalCodeOwners(changeNotes.getProjectName());
      logger.atFine().log("global code owners = %s", globalCodeOwners);

      ImmutableSet<Account.Id> reviewerAccountIds =
          getReviewerAccountIds(requiredApproval, changeNotes, patchSetUploader);
      ImmutableSet<Account.Id> approverAccountIds =
          getApproverAccountIds(requiredApproval, changeNotes, patchSetUploader);
      logger.atFine().log("reviewers = %s, approvers = %s", reviewerAccountIds, approverAccountIds);

      return changedFiles
          .compute(changeNotes.getProjectName(), changeNotes.getCurrentPatchSet().commitId())
          .stream()
          .map(
              changedFile ->
                  getFileStatus(
                      branch,
                      revision,
                      globalCodeOwners,
                      enableImplicitApprovalFromUploader,
                      patchSetUploader,
                      reviewerAccountIds,
                      approverAccountIds,
                      hasOverride,
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
      throws ResourceConflictException, IOException, PatchListNotAvailableException {
    requireNonNull(changeNotes, "changeNotes");
    requireNonNull(patchSet, "patchSet");
    requireNonNull(accountId, "accountId");
    try (Timer0.Context ctx = codeOwnerMetrics.prepareFileStatusComputationForAccount.start()) {
      logger.atFine().log(
          "prepare stream to compute file statuses for account %d (project = %s, change = %d, patch set = %d)",
          accountId.get(),
          changeNotes.getProjectName(),
          changeNotes.getChangeId().get(),
          patchSet.id().get());

      RequiredApproval requiredApproval =
          codeOwnersPluginConfiguration.getRequiredApproval(changeNotes.getProjectName());
      logger.atFine().log("requiredApproval = %s", requiredApproval);

      BranchNameKey branch = changeNotes.getChange().getDest();
      ObjectId revision = getDestBranchRevision(changeNotes.getChange());
      logger.atFine().log("dest branch %s has revision %s", branch.branch(), revision.name());

      boolean isProjectOwner = isProjectOwner(changeNotes.getProjectName(), accountId);
      FallbackCodeOwners fallbackCodeOwners =
          codeOwnersPluginConfiguration.getFallbackCodeOwners(branch.project());
      logger.atFine().log(
          "fallbackCodeOwner = %s, isProjectOwner = %s", fallbackCodeOwners, isProjectOwner);
      if (fallbackCodeOwners.equals(FallbackCodeOwners.PROJECT_OWNERS) && isProjectOwner) {
        return getAllPathsAsApproved(changeNotes, patchSet);
      }

      return changedFiles.compute(changeNotes.getProjectName(), patchSet.commitId()).stream()
          .map(
              changedFile ->
                  getFileStatus(
                      branch,
                      revision,
                      /* globalCodeOwners= */ CodeOwnerResolverResult.createEmpty(),
                      // Do not check for implicit approvals since implicit approvals of other users
                      // should be ignored. For the given account we do not need to check for
                      // implicit approvals since all owned files are already covered by the
                      // explicit approval.
                      /* enableImplicitApprovalFromUploader= */ false,
                      /* patchSetUploader= */ null,
                      /* reviewerAccountIds= */ ImmutableSet.of(),
                      // Assume an explicit approval of the given account.
                      /* approverAccountIds= */ ImmutableSet.of(accountId),
                      /* hasOverride= */ false,
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
      ChangeNotes changeNotes, PatchSet patchSet)
      throws IOException, PatchListNotAvailableException {
    return changedFiles.compute(changeNotes.getProjectName(), patchSet.commitId()).stream()
        .map(
            changedFile ->
                FileCodeOwnerStatus.create(
                    changedFile,
                    changedFile
                        .newPath()
                        .map(
                            newPath ->
                                PathCodeOwnerStatus.create(newPath, CodeOwnerStatus.APPROVED)),
                    changedFile
                        .oldPath()
                        .map(
                            oldPath ->
                                PathCodeOwnerStatus.create(oldPath, CodeOwnerStatus.APPROVED))));
  }

  private FileCodeOwnerStatus getFileStatus(
      BranchNameKey branch,
      ObjectId revision,
      CodeOwnerResolverResult globalCodeOwners,
      boolean enableImplicitApprovalFromUploader,
      @Nullable Account.Id patchSetUploader,
      ImmutableSet<Account.Id> reviewerAccountIds,
      ImmutableSet<Account.Id> approverAccountIds,
      boolean hasOverride,
      ChangedFile changedFile) {
    logger.atFine().log("computing file status for %s", changedFile);

    // Compute the code owner status for the new path, if there is a new path.
    Optional<PathCodeOwnerStatus> newPathStatus =
        changedFile
            .newPath()
            .map(
                newPath ->
                    getPathCodeOwnerStatus(
                        branch,
                        revision,
                        globalCodeOwners,
                        enableImplicitApprovalFromUploader,
                        patchSetUploader,
                        reviewerAccountIds,
                        approverAccountIds,
                        hasOverride,
                        newPath));

    // Compute the code owner status for the old path, if the file was deleted or renamed.
    Optional<PathCodeOwnerStatus> oldPathStatus = Optional.empty();
    if (changedFile.isDeletion() || changedFile.isRename()) {
      checkState(changedFile.oldPath().isPresent(), "old path must be present for deletion/rename");
      logger.atFine().log(
          "file was %s (old path = %s)",
          changedFile.isDeletion() ? "deleted" : "renamed", changedFile.oldPath().get());
      oldPathStatus =
          Optional.of(
              getPathCodeOwnerStatus(
                  branch,
                  revision,
                  globalCodeOwners,
                  enableImplicitApprovalFromUploader,
                  patchSetUploader,
                  reviewerAccountIds,
                  approverAccountIds,
                  hasOverride,
                  changedFile.oldPath().get()));
    }

    FileCodeOwnerStatus fileCodeOwnerStatus =
        FileCodeOwnerStatus.create(changedFile, newPathStatus, oldPathStatus);
    logger.atFine().log("fileCodeOwnerStatus = %s", fileCodeOwnerStatus);
    return fileCodeOwnerStatus;
  }

  private PathCodeOwnerStatus getPathCodeOwnerStatus(
      BranchNameKey branch,
      ObjectId revision,
      CodeOwnerResolverResult globalCodeOwners,
      boolean enableImplicitApprovalFromUploader,
      @Nullable Account.Id patchSetUploader,
      ImmutableSet<Account.Id> reviewerAccountIds,
      ImmutableSet<Account.Id> approverAccountIds,
      boolean hasOverride,
      Path absolutePath) {
    logger.atFine().log("computing path status for %s", absolutePath);

    if (hasOverride) {
      logger.atFine().log(
          "the status for path %s is %s since an override is present",
          absolutePath, CodeOwnerStatus.APPROVED.name());
      return PathCodeOwnerStatus.create(absolutePath, CodeOwnerStatus.APPROVED);
    }

    AtomicReference<CodeOwnerStatus> codeOwnerStatus =
        new AtomicReference<>(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);

    if (isApproved(
        absolutePath,
        globalCodeOwners,
        approverAccountIds,
        enableImplicitApprovalFromUploader,
        patchSetUploader)) {
      logger.atFine().log("%s was approved by a global code owner", absolutePath);
      codeOwnerStatus.set(CodeOwnerStatus.APPROVED);
    } else {
      logger.atFine().log("%s was not approved by a global code owner", absolutePath);

      if (isPending(absolutePath, globalCodeOwners, reviewerAccountIds)) {
        logger.atFine().log("%s is owned by a reviewer who is a global owner", absolutePath);
        codeOwnerStatus.set(CodeOwnerStatus.PENDING);
      }

      AtomicBoolean hasRevelantCodeOwnerDefinitions = new AtomicBoolean(false);
      AtomicBoolean parentCodeOwnersAreIgnored = new AtomicBoolean(false);
      codeOwnerConfigHierarchy.visit(
          branch,
          revision,
          absolutePath,
          codeOwnerConfig -> {
            CodeOwnerResolverResult codeOwners = getCodeOwners(codeOwnerConfig, absolutePath);
            logger.atFine().log(
                "code owners = %s (code owner config folder path = %s, file name = %s)",
                codeOwners,
                codeOwnerConfig.key().folderPath(),
                codeOwnerConfig.key().fileName().orElse("<default>"));

            if (codeOwners.hasRevelantCodeOwnerDefinitions()) {
              hasRevelantCodeOwnerDefinitions.set(true);
            }

            if (isApproved(
                absolutePath,
                codeOwners,
                approverAccountIds,
                enableImplicitApprovalFromUploader,
                patchSetUploader)) {
              codeOwnerStatus.set(CodeOwnerStatus.APPROVED);
              return false;
            } else if (isPending(absolutePath, codeOwners, reviewerAccountIds)) {
              codeOwnerStatus.set(CodeOwnerStatus.PENDING);

              // We need to continue to check if any of the higher-level code owners approved the
              // change.
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
        codeOwnerStatus.set(
            getCodeOwnerStatusForFallbackCodeOwners(
                codeOwnerStatus.get(),
                branch,
                globalCodeOwners,
                enableImplicitApprovalFromUploader,
                patchSetUploader,
                reviewerAccountIds,
                approverAccountIds,
                absolutePath));
      }
    }

    PathCodeOwnerStatus pathCodeOwnerStatus =
        PathCodeOwnerStatus.create(absolutePath, codeOwnerStatus.get());
    logger.atFine().log("pathCodeOwnerStatus = %s", pathCodeOwnerStatus);
    return pathCodeOwnerStatus;
  }

  /**
   * Gets the code owner status for the given path when project owners are configured as fallback
   * code owners.
   */
  private CodeOwnerStatus getCodeOwnerStatusForProjectOwnersAsFallbackCodeOwners(
      BranchNameKey branch,
      CodeOwnerResolverResult globalCodeOwners,
      boolean enableImplicitApprovalFromUploader,
      @Nullable Account.Id patchSetUploader,
      ImmutableSet<Account.Id> reviewerAccountIds,
      ImmutableSet<Account.Id> approverAccountIds,
      Path absolutePath) {
    logger.atFine().log(
        "computing code owner status for %s with project owners as fallback code owners",
        absolutePath);

    CodeOwnerStatus codeOwnerStatus = CodeOwnerStatus.INSUFFICIENT_REVIEWERS;
    if (isApprovedByProjectOwnerOrGlobalOwner(
        branch.project(),
        absolutePath,
        globalCodeOwners,
        approverAccountIds,
        enableImplicitApprovalFromUploader,
        patchSetUploader)) {
      codeOwnerStatus = CodeOwnerStatus.APPROVED;
    } else if (isPendingByProjectOwnerOrGlobalOwner(
        branch.project(), absolutePath, globalCodeOwners, reviewerAccountIds)) {
      codeOwnerStatus = CodeOwnerStatus.PENDING;
    }

    logger.atFine().log("codeOwnerStatus = %s", codeOwnerStatus);
    return codeOwnerStatus;
  }

  private boolean isApprovedByProjectOwnerOrGlobalOwner(
      Project.NameKey projectName,
      Path absolutePath,
      CodeOwnerResolverResult globalCodeOwners,
      ImmutableSet<Account.Id> approverAccountIds,
      boolean enableImplicitApprovalFromUploader,
      @Nullable Account.Id patchSetUploader) {
    return (enableImplicitApprovalFromUploader
            && isImplicitlyApprovedByProjectOwnerOrGlobalOwner(
                projectName, absolutePath, globalCodeOwners, patchSetUploader))
        || isExplicitlyApprovedByProjectOwnerOrGlobalOwner(
            projectName, absolutePath, globalCodeOwners, approverAccountIds);
  }

  private boolean isImplicitlyApprovedByProjectOwnerOrGlobalOwner(
      Project.NameKey projectName,
      Path absolutePath,
      CodeOwnerResolverResult globalCodeOwners,
      Account.Id patchSetUploader) {
    requireNonNull(
        patchSetUploader, "patchSetUploader must be set if implicit approvals are enabled");
    if (isProjectOwner(projectName, patchSetUploader)) {
      // The uploader of the patch set is a project owner and thus a code owner. This means there
      // is an implicit code owner approval from the patch set uploader so that the path is
      // automatically approved.
      logger.atFine().log(
          "%s was implicitly approved by the patch set uploader who is a project owner",
          absolutePath);
      return true;
    }

    if (globalCodeOwners.ownedByAllUsers()
        || globalCodeOwners.codeOwnersAccountIds().contains(patchSetUploader)) {
      // If the uploader of the patch set is a global code owner, there is an implicit code owner
      // approval from the patch set uploader so that the path is automatically approved.
      logger.atFine().log(
          "%s was implicitly approved by the patch set uploader who is a global owner",
          absolutePath);
      return true;
    }

    return false;
  }

  private boolean isExplicitlyApprovedByProjectOwnerOrGlobalOwner(
      Project.NameKey projectName,
      Path absolutePath,
      CodeOwnerResolverResult globalCodeOwners,
      ImmutableSet<Account.Id> approverAccountIds) {
    if (!Collections.disjoint(approverAccountIds, globalCodeOwners.codeOwnersAccountIds())
        || (globalCodeOwners.ownedByAllUsers() && !approverAccountIds.isEmpty())) {
      // At least one of the global code owners approved the change.
      logger.atFine().log("%s was approved by a global code owner", absolutePath);
      return true;
    }

    if (approverAccountIds.stream()
        .anyMatch(approverAccountId -> isProjectOwner(projectName, approverAccountId))) {
      // At least one of the approvers is a project owner and thus a code owner.
      logger.atFine().log("%s was approved by a project owner", absolutePath);
      return true;
    }

    return false;
  }

  private boolean isPendingByProjectOwnerOrGlobalOwner(
      Project.NameKey projectName,
      Path absolutePath,
      CodeOwnerResolverResult globalCodeOwners,
      ImmutableSet<Account.Id> reviewerAccountIds) {
    if (reviewerAccountIds.stream()
        .anyMatch(reviewerAccountId -> isProjectOwner(projectName, reviewerAccountId))) {
      // At least one of the reviewers is a project owner and thus a code owner.
      logger.atFine().log("%s is owned by a reviewer who is project owner", absolutePath);
      return true;
    }

    if (isPending(absolutePath, globalCodeOwners, reviewerAccountIds)) {
      // At least one of the reviewers is a global code owner.
      logger.atFine().log("%s is owned by a reviewer who is a global owner", absolutePath);
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
      CodeOwnerResolverResult globalCodeOwners,
      boolean enableImplicitApprovalFromUploader,
      @Nullable Account.Id patchSetUploader,
      ImmutableSet<Account.Id> reviewerAccountIds,
      ImmutableSet<Account.Id> approverAccountIds,
      Path absolutePath) {
    FallbackCodeOwners fallbackCodeOwners =
        codeOwnersPluginConfiguration.getFallbackCodeOwners(branch.project());
    logger.atFine().log(
        "getting code owner status for fallback code owners (fallback code owners = %s)",
        fallbackCodeOwners);
    switch (fallbackCodeOwners) {
      case NONE:
        logger.atFine().log("no fallback code owners");
        return codeOwnerStatus;
      case PROJECT_OWNERS:
        return getCodeOwnerStatusForProjectOwnersAsFallbackCodeOwners(
            branch,
            globalCodeOwners,
            enableImplicitApprovalFromUploader,
            patchSetUploader,
            reviewerAccountIds,
            approverAccountIds,
            absolutePath);
      case ALL_USERS:
        return getCodeOwnerStatusIfAllUsersAreCodeOwners(
            enableImplicitApprovalFromUploader,
            reviewerAccountIds,
            approverAccountIds,
            absolutePath);
    }

    throw new CodeOwnersInternalServerErrorException(
        String.format("unknown fallback code owners configured: %s", fallbackCodeOwners));
  }

  /** Computes the code owner status for the given path assuming that all users are code owners. */
  private CodeOwnerStatus getCodeOwnerStatusIfAllUsersAreCodeOwners(
      boolean enableImplicitApprovalFromUploader,
      ImmutableSet<Account.Id> reviewerAccountIds,
      ImmutableSet<Account.Id> approverAccountIds,
      Path absolutePath) {
    logger.atFine().log(
        "getting code owner status for fallback code owners (all users are fallback code owners)");

    if (enableImplicitApprovalFromUploader) {
      logger.atFine().log(
          "%s was implicitly approved by the patch set uploader since the uploader is a fallback"
              + " code owner",
          absolutePath);
      return CodeOwnerStatus.APPROVED;
    }

    if (!approverAccountIds.isEmpty()) {
      logger.atFine().log("%s was approved by a fallback code owner", absolutePath);
      return CodeOwnerStatus.APPROVED;
    } else if (!reviewerAccountIds.isEmpty()) {
      logger.atFine().log("%s has a fallback code owner as reviewer", absolutePath);
      return CodeOwnerStatus.PENDING;
    }

    logger.atFine().log("%s has no fallback code owner as a reviewer", absolutePath);
    return CodeOwnerStatus.INSUFFICIENT_REVIEWERS;
  }

  private boolean isApproved(
      Path absolutePath,
      CodeOwnerResolverResult codeOwners,
      ImmutableSet<Account.Id> approverAccountIds,
      boolean enableImplicitApprovalFromUploader,
      @Nullable Account.Id patchSetUploader) {
    if (enableImplicitApprovalFromUploader) {
      requireNonNull(
          patchSetUploader, "patchSetUploader must be set if implicit approvals are enabled");
      if (codeOwners.codeOwnersAccountIds().contains(patchSetUploader)
          || codeOwners.ownedByAllUsers()) {
        // If the uploader of the patch set owns the path, there is an implicit code owner
        // approval from the patch set uploader so that the path is automatically approved.
        logger.atFine().log("%s was implicitly approved by the patch set uploader", absolutePath);
        return true;
      }
    }

    if (!Collections.disjoint(approverAccountIds, codeOwners.codeOwnersAccountIds())
        || (codeOwners.ownedByAllUsers() && !approverAccountIds.isEmpty())) {
      // At least one of the global code owners approved the change.
      logger.atFine().log("%s was explicitly approved by a code owner", absolutePath);
      return true;
    }

    return false;
  }

  private boolean isPending(
      Path absolutePath,
      CodeOwnerResolverResult codeOwners,
      ImmutableSet<Account.Id> reviewerAccountIds) {
    if (!Collections.disjoint(codeOwners.codeOwnersAccountIds(), reviewerAccountIds)
        || (codeOwners.ownedByAllUsers() && !reviewerAccountIds.isEmpty())) {
      logger.atFine().log("%s is owned by a reviewer", absolutePath);
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
   * Gets the code owners that own the given path according to the given code owner config.
   *
   * @param codeOwnerConfig the code owner config from which the code owners should be retrieved
   * @param absolutePath the path for which the code owners should be retrieved
   */
  private CodeOwnerResolverResult getCodeOwners(
      CodeOwnerConfig codeOwnerConfig, Path absolutePath) {
    return codeOwnerResolver
        .get()
        .enforceVisibility(false)
        .resolvePathCodeOwners(codeOwnerConfig, absolutePath);
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
   * @param changeNotes the change notes
   */
  private ImmutableSet<Account.Id> getApproverAccountIds(
      RequiredApproval requiredApproval, ChangeNotes changeNotes, Account.Id patchSetUploader) {
    ImmutableSet<Account.Id> approverAccountIds =
        StreamSupport.stream(
                approvalsUtil
                    .byPatchSet(
                        changeNotes,
                        changeNotes.getCurrentPatchSet().id(),
                        /** revWalk */
                        null,
                        /** repoConfig */
                        null)
                    .spliterator(),
                /** parallel */
                false)
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

  private ImmutableSet<Account.Id> filterOutAccount(
      ImmutableSet<Account.Id> accountIds, Account.Id accountIdToFilterOut) {
    return accountIds.stream()
        .filter(accountId -> !accountId.equals(accountIdToFilterOut))
        .collect(toImmutableSet());
  }

  /**
   * Checks whether the given change has an override approval.
   *
   * @param overrideApprovals approvals that count as override for the code owners submit check.
   * @param changeNotes the change notes
   * @param patchSetUploader account ID of the patch set uploader
   * @return whether the given change has an override approval
   */
  private boolean hasOverride(
      ImmutableSet<RequiredApproval> overrideApprovals,
      ChangeNotes changeNotes,
      Account.Id patchSetUploader) {
    ImmutableSet<RequiredApproval> overrideApprovalsThatIgnoreSelfApprovals =
        overrideApprovals.stream()
            .filter(overrideApproval -> overrideApproval.labelType().isIgnoreSelfApproval())
            .collect(toImmutableSet());
    return StreamSupport.stream(
            approvalsUtil
                .byPatchSet(
                    changeNotes,
                    changeNotes.getCurrentPatchSet().id(),
                    /** revWalk */
                    null,
                    /** repoConfig */
                    null)
                .spliterator(),
            /** parallel */
            false)
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
        .anyMatch(
            patchSetApproval ->
                overrideApprovals.stream()
                    .anyMatch(overrideApproval -> overrideApproval.isApprovedBy(patchSetApproval)));
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
