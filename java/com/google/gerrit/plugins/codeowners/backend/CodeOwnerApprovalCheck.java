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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.config.InvalidPluginConfigurationException;
import com.google.gerrit.plugins.codeowners.config.RequiredApproval;
import com.google.gerrit.server.change.RevisionResource;
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
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final ChangedFiles changedFiles;
  private final CodeOwnerConfigHierarchy codeOwnerConfigHierarchy;
  private final Provider<CodeOwnerResolver> codeOwnerResolver;

  @Inject
  CodeOwnerApprovalCheck(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      ChangedFiles changedFiles,
      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy,
      Provider<CodeOwnerResolver> codeOwnerResolver) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.changedFiles = changedFiles;
    this.codeOwnerConfigHierarchy = codeOwnerConfigHierarchy;
    this.codeOwnerResolver = codeOwnerResolver;
  }

  /**
   * Gets the code owner statuses for all files/paths that were changed in the given revision.
   *
   * @param revisionResource the revision for which the code owner statuses should be returned
   */
  public ImmutableSet<FileCodeOwnerStatus> getFileStatuses(RevisionResource revisionResource)
      throws InvalidPluginConfigurationException, IOException {
    requireNonNull(revisionResource, "revisionResource");

    RequiredApproval requiredApproval =
        codeOwnersPluginConfiguration.getRequiredApproval(revisionResource.getChange().getDest());

    ChangeNotes changeNotes = revisionResource.getNotes();
    BranchNameKey branch = changeNotes.getChange().getDest();

    ImmutableSet<Account.Id> reviewerAccountIds = getReviewerAccountIds(changeNotes);
    ImmutableSet<Account.Id> approverAccountIds =
        getApproverAccountIds(requiredApproval, revisionResource);

    ImmutableSet.Builder<FileCodeOwnerStatus> fileCodeOwnerStatusBuilder = ImmutableSet.builder();

    // Iterate over all files that have been changed in the revision.
    for (ChangedFile changedFile : changedFiles.compute(revisionResource)) {
      // Compute the code owner status for the new path, if there is a new path.
      Optional<PathCodeOwnerStatus> newPathStatus = Optional.empty();
      if (changedFile.newPath().isPresent()) {

        newPathStatus =
            Optional.of(
                getCodeOwnerStatus(
                    branch, reviewerAccountIds, approverAccountIds, changedFile.newPath().get()));
      }

      // Compute the code owner status for the old path, if the file was deleted or renamed.
      Optional<PathCodeOwnerStatus> oldPathStatus = Optional.empty();
      if (changedFile.isDeletion() || changedFile.isRename()) {
        checkState(
            changedFile.oldPath().isPresent(), "old path must be present for deletion/rename");
        oldPathStatus =
            Optional.of(
                getCodeOwnerStatus(
                    branch, reviewerAccountIds, approverAccountIds, changedFile.oldPath().get()));
      }

      fileCodeOwnerStatusBuilder.add(
          FileCodeOwnerStatus.create(changedFile, newPathStatus, oldPathStatus));
    }

    return fileCodeOwnerStatusBuilder.build();
  }

  private PathCodeOwnerStatus getCodeOwnerStatus(
      BranchNameKey branch,
      ImmutableSet<Account.Id> reviewerAccountIds,
      ImmutableSet<Account.Id> approverAccountIds,
      Path absolutePath)
      throws InvalidPluginConfigurationException {
    // TODO(ekempin): Check if the path is owned by the uploader of the patch set since paths that
    // are owned by the patch set uploader should be considered as approved.
    if (reviewerAccountIds.isEmpty()) {
      // Short-cut, if there are no reviewers, any path has the INSUFFICIENT_REVIEWERS code owner
      // status.
      return PathCodeOwnerStatus.create(absolutePath, CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    }

    AtomicReference<CodeOwnerStatus> codeOwnerStatus =
        new AtomicReference<>(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    codeOwnerConfigHierarchy.visit(
        branch,
        absolutePath,
        codeOwnerConfig -> {
          ImmutableSet<Account.Id> codeOwnerAccountIds =
              getCodeOwnerAccountIds(codeOwnerConfig, absolutePath);

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
      CodeOwnerConfig codeOwnerConfig, Path absolutePath)
      throws InvalidPluginConfigurationException {
    return codeOwnerResolver.get().enforceVisibility(false)
        .resolveLocalCodeOwners(codeOwnerConfig, absolutePath).stream()
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
   * @param revisionResource the revision resource
   */
  private ImmutableSet<Account.Id> getApproverAccountIds(
      RequiredApproval requiredApproval, RevisionResource revisionResource) {
    return revisionResource.getNotes().getApprovals().get(revisionResource.getPatchSet().id())
        .stream()
        .filter(requiredApproval::isCodeOwnerApproval)
        .map(PatchSetApproval::accountId)
        .collect(toImmutableSet());
  }
}
