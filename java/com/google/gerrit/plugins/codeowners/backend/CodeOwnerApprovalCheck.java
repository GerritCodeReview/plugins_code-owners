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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.config.RequiredApproval;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
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
  private final GitRepositoryManager repoManager;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final ChangedFiles changedFiles;
  private final CodeOwnerConfigHierarchy codeOwnerConfigHierarchy;
  private final Provider<CodeOwnerResolver> codeOwnerResolver;

  @Inject
  CodeOwnerApprovalCheck(
      GitRepositoryManager repoManager,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      ChangedFiles changedFiles,
      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy,
      Provider<CodeOwnerResolver> codeOwnerResolver) {
    this.repoManager = repoManager;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.changedFiles = changedFiles;
    this.codeOwnerConfigHierarchy = codeOwnerConfigHierarchy;
    this.codeOwnerResolver = codeOwnerResolver;
  }

  /**
   * Whether the given change has sufficient code owner approvals to be submittable.
   *
   * @param changeNotes the change notes
   * @return whether the given change has sufficient code owner approvals to be submittable
   */
  public boolean isSubmittable(ChangeNotes changeNotes) throws IOException {
    requireNonNull(changeNotes, "changeNotes");
    return !getFileStatuses(changeNotes)
        .anyMatch(
            fileStatus ->
                (fileStatus.newPathStatus().isPresent()
                        && fileStatus.newPathStatus().get().status() != CodeOwnerStatus.APPROVED)
                    || (fileStatus.oldPathStatus().isPresent()
                        && fileStatus.oldPathStatus().get().status() != CodeOwnerStatus.APPROVED));
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
  public Stream<FileCodeOwnerStatus> getFileStatuses(ChangeNotes changeNotes) throws IOException {
    requireNonNull(changeNotes, "changeNotes");

    RequiredApproval requiredApproval =
        codeOwnersPluginConfiguration.getRequiredApproval(changeNotes.getProjectName());
    Optional<RequiredApproval> overrideApproval =
        codeOwnersPluginConfiguration.getOverrideApproval(changeNotes.getProjectName());
    boolean hasOverride =
        overrideApproval.isPresent() && hasOverride(overrideApproval.get(), changeNotes);

    BranchNameKey branch = changeNotes.getChange().getDest();
    ObjectId revision = getDestBranchRevision(changeNotes.getChange());
    Account.Id patchSetUploader = changeNotes.getCurrentPatchSet().uploader();

    ImmutableSet<Account.Id> reviewerAccountIds = getReviewerAccountIds(changeNotes);
    ImmutableSet<Account.Id> approverAccountIds =
        getApproverAccountIds(requiredApproval, changeNotes);

    return changedFiles
        .compute(changeNotes.getProjectName(), changeNotes.getCurrentPatchSet().commitId()).stream()
        .map(
            changedFile ->
                getFileStatus(
                    branch,
                    revision,
                    patchSetUploader,
                    reviewerAccountIds,
                    approverAccountIds,
                    hasOverride,
                    changedFile));
  }

  private FileCodeOwnerStatus getFileStatus(
      BranchNameKey branch,
      ObjectId revision,
      Account.Id patchSetUploader,
      ImmutableSet<Account.Id> reviewerAccountIds,
      ImmutableSet<Account.Id> approverAccountIds,
      boolean hasOverride,
      ChangedFile changedFile) {
    // Compute the code owner status for the new path, if there is a new path.
    Optional<PathCodeOwnerStatus> newPathStatus =
        changedFile
            .newPath()
            .map(
                newPath ->
                    getPathCodeOwnerStatus(
                        branch,
                        revision,
                        patchSetUploader,
                        reviewerAccountIds,
                        approverAccountIds,
                        hasOverride,
                        newPath));

    // Compute the code owner status for the old path, if the file was deleted or renamed.
    Optional<PathCodeOwnerStatus> oldPathStatus = Optional.empty();
    if (changedFile.isDeletion() || changedFile.isRename()) {
      checkState(changedFile.oldPath().isPresent(), "old path must be present for deletion/rename");
      oldPathStatus =
          Optional.of(
              getPathCodeOwnerStatus(
                  branch,
                  revision,
                  patchSetUploader,
                  reviewerAccountIds,
                  approverAccountIds,
                  hasOverride,
                  changedFile.oldPath().get()));
    }

    return FileCodeOwnerStatus.create(changedFile, newPathStatus, oldPathStatus);
  }

  private PathCodeOwnerStatus getPathCodeOwnerStatus(
      BranchNameKey branch,
      ObjectId revision,
      Account.Id patchSetUploader,
      ImmutableSet<Account.Id> reviewerAccountIds,
      ImmutableSet<Account.Id> approverAccountIds,
      boolean hasOverride,
      Path absolutePath) {
    if (hasOverride) {
      return PathCodeOwnerStatus.create(absolutePath, CodeOwnerStatus.APPROVED);
    }

    AtomicReference<CodeOwnerStatus> codeOwnerStatus =
        new AtomicReference<>(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    codeOwnerConfigHierarchy.visit(
        branch,
        revision,
        absolutePath,
        codeOwnerConfig -> {
          ImmutableSet<Account.Id> codeOwnerAccountIds =
              getCodeOwnerAccountIds(codeOwnerConfig, absolutePath);

          if (codeOwnerAccountIds.contains(patchSetUploader)) {
            // If the uploader of the patch set owns the path, there is an implicit code owner
            // approval from the patch set uploader so that the path is automatically approved.
            codeOwnerStatus.set(CodeOwnerStatus.APPROVED);

            // We can abort since we already found that the path was approved.
            return false;
          }

          if (Collections.disjoint(codeOwnerAccountIds, reviewerAccountIds)) {
            // We need to continue to check if any of the higher-level code owners is a reviewer.
            return true;
          }

          if (Collections.disjoint(codeOwnerAccountIds, approverAccountIds)) {
            // At least one of the code owners is a reviewer on the change.
            codeOwnerStatus.set(CodeOwnerStatus.PENDING);

            // We need to continue to check if any of the higher-level code owners has approved the
            // change.
            return true;
          }

          // At least one of the code owners approved the change.
          codeOwnerStatus.set(CodeOwnerStatus.APPROVED);

          // We can abort since we already found that the path was approved.
          return false;
        });

    return PathCodeOwnerStatus.create(absolutePath, codeOwnerStatus.get());
  }

  /**
   * Gets the IDs of the accounts that own the given path according to the given code owner config.
   *
   * @param codeOwnerConfig the code owner config from which the code owners should be retrieved
   * @param absolutePath the path for which the code owners should be retrieved
   */
  private ImmutableSet<Account.Id> getCodeOwnerAccountIds(
      CodeOwnerConfig codeOwnerConfig, Path absolutePath) {
    return codeOwnerResolver.get().enforceVisibility(false)
        .resolvePathCodeOwners(codeOwnerConfig, absolutePath).stream()
        .map(CodeOwner::accountId)
        .collect(toImmutableSet());
  }

  /**
   * Gets the IDs of the accounts that are reviewer on the given change.
   *
   * @param changeNotes the change notes
   */
  private ImmutableSet<Account.Id> getReviewerAccountIds(ChangeNotes changeNotes) {
    return changeNotes.getReviewers().byState(ReviewerStateInternal.REVIEWER);
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
      RequiredApproval requiredApproval, ChangeNotes changeNotes) {
    return changeNotes.getApprovals().get(changeNotes.getCurrentPatchSet().id()).stream()
        .filter(requiredApproval::isApprovedBy)
        .map(PatchSetApproval::accountId)
        .collect(toImmutableSet());
  }

  /**
   * Checks whether the given change has an override approval.
   *
   * @param overrideApproval approval that is required to override the code owners submit check.
   * @param changeNotes the change notes
   * @return whether the given change has an override approval
   */
  private boolean hasOverride(RequiredApproval overrideApproval, ChangeNotes changeNotes) {
    return changeNotes.getApprovals().get(changeNotes.getCurrentPatchSet().id()).stream()
        .anyMatch(overrideApproval::isApprovedBy);
  }

  /**
   * Gets the current revision of the destination branch of the given change.
   *
   * <p>This is the revision from which the code owner configs should be read when computing code
   * owners for the files that are touched in the change.
   */
  private ObjectId getDestBranchRevision(Change change) throws IOException {
    try (Repository repository = repoManager.openRepository(change.getProject());
        RevWalk rw = new RevWalk(repository)) {
      Ref ref = repository.exactRef(change.getDest().branch());
      checkNotNull(
          ref,
          "branch %s in repository %s not found",
          change.getDest().branch(),
          change.getProject().get());
      return rw.parseCommit(ref.getObjectId());
    }
  }
}
