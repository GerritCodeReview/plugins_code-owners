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
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.common.ChangedFile;
import com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.DiffOperationsForCommitValidation;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Class to get the files that have been changed in a revision.
 *
 * <p>If possible changed files should be retrieved without rename detection, since rename detection
 * may be expensive.
 *
 * <p>The changed files are retrieved from {@link DiffOperations}.
 *
 * <p>The {@link com.google.gerrit.server.patch.PatchListCache} is deprecated, and hence it not
 * being used here.
 */
@Singleton
public class ChangedFiles {
  private final GitRepositoryManager repoManager;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final DiffOperations diffOperations;
  private final CodeOwnerMetrics codeOwnerMetrics;

  @Inject
  public ChangedFiles(
      GitRepositoryManager repoManager,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      DiffOperations diffOperations,
      CodeOwnerMetrics codeOwnerMetrics) {
    this.repoManager = repoManager;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.diffOperations = diffOperations;
    this.codeOwnerMetrics = codeOwnerMetrics;
  }

  /**
   * Gets the changed files.
   *
   * <p>Rename detection is disabled.
   *
   * <p>Uses the configured merge commit strategy.
   *
   * @param project the project
   * @param revision the revision for which the changed files should be retrieved
   * @return the files that have been changed in the given revision, sorted alphabetically by path
   * @throws IOException thrown if the computation fails due to an I/O error
   */
  public ImmutableList<ChangedFile> getWithoutRenameDetection(
      Project.NameKey project, ObjectId revision) throws IOException, DiffNotAvailableException {
    requireNonNull(project, "project");
    requireNonNull(revision, "revision");
    return getWithoutRenameDetection(
        project,
        revision,
        codeOwnersPluginConfiguration.getProjectConfig(project).getMergeCommitStrategy());
  }

  /**
   * Gets the changed files.
   *
   * <p>Rename detection is disabled.
   *
   * @param project the project
   * @param revision the revision for which the changed files should be retrieved
   * @param mergeCommitStrategy the merge commit strategy that should be used to compute the changed
   *     files for merge commits
   * @return the files that have been changed in the given revision, sorted alphabetically by path
   */
  public ImmutableList<ChangedFile> getWithoutRenameDetection(
      Project.NameKey project, ObjectId revision, MergeCommitStrategy mergeCommitStrategy)
      throws IOException, DiffNotAvailableException {
    return get(project, revision, mergeCommitStrategy, /* enableRenameDetection= */ false);
  }

  /**
   * Gets the changed files.
   *
   * <p>Rename detection is disabled.
   *
   * <p>Uses the configured merge commit strategy.
   *
   * @param revisionResource the revision resource for which the changed files should be retrieved
   * @return the files that have been changed in the given revision, sorted alphabetically by path
   * @throws IOException thrown if the computation fails due to an I/O error
   * @see #get(Project.NameKey, ObjectId, MergeCommitStrategy, boolean)
   */
  public ImmutableList<ChangedFile> getWithoutRenameDetection(RevisionResource revisionResource)
      throws IOException, DiffNotAvailableException {
    requireNonNull(revisionResource, "revisionResource");
    return getWithoutRenameDetection(
        revisionResource.getProject(), revisionResource.getPatchSet().commitId());
  }

  /**
   * Gets the changed files.
   *
   * <p>Uses the configured merge commit strategy.
   *
   * @param project the project
   * @param revision the revision for which the changed files should be retrieved
   * @param enableRenameDetection whether the rename detection is enabled
   * @return the files that have been changed in the given revision, sorted alphabetically by path
   * @throws IOException thrown if the computation fails due to an I/O error
   */
  public ImmutableList<ChangedFile> get(
      Project.NameKey project, ObjectId revision, boolean enableRenameDetection)
      throws IOException, DiffNotAvailableException {
    requireNonNull(project, "project");
    requireNonNull(revision, "revision");
    return get(
        project,
        revision,
        codeOwnersPluginConfiguration.getProjectConfig(project).getMergeCommitStrategy(),
        enableRenameDetection);
  }

  /**
   * Gets the changed files.
   *
   * @param project the project
   * @param revision the revision for which the changed files should be retrieved
   * @param mergeCommitStrategy the merge commit strategy that should be used to compute the changed
   *     files for merge commits
   * @param enableRenameDetection whether the rename detection is enabled
   * @return the files that have been changed in the given revision, sorted alphabetically by path
   */
  public ImmutableList<ChangedFile> get(
      Project.NameKey project,
      ObjectId revision,
      MergeCommitStrategy mergeCommitStrategy,
      boolean enableRenameDetection)
      throws IOException, DiffNotAvailableException {
    requireNonNull(project, "project");
    requireNonNull(revision, "revision");
    requireNonNull(mergeCommitStrategy, "mergeCommitStrategy");

    try (Timer0.Context ctx = codeOwnerMetrics.getChangedFiles.start()) {
      List<ModifiedFile> modifiedFiles;
      if (mergeCommitStrategy.equals(MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION)
          || isInitialCommit(project, revision)) {
        // Use parentNum=0 to do the comparison against the default base.
        // For non-merge commits the default base is the only parent (aka parent 1).
        // Initial commits are supported when using parentNum=0.
        // For merge commits the default base is the auto-merge commit which should be used as base
        // if the merge commit strategy is FILES_WITH_CONFLICT_RESOLUTION.
        modifiedFiles =
            diffOperations.getModifiedFiles(
                project, revision, /* parentNum= */ 0, enableRenameDetection);
      } else {
        checkState(mergeCommitStrategy.equals(MergeCommitStrategy.ALL_CHANGED_FILES));
        // Always use parent 1 to do the comparison.
        // Non-merge commits should always be compared against the first parent (initial commits are
        // handled above).
        // For merge commits also the first parent should be used if the merge commit strategy is
        // ALL_CHANGED_FILES.
        modifiedFiles =
            diffOperations.getModifiedFiles(
                project, revision, /* parentNum= */ 1, enableRenameDetection);
      }

      return modifiedFilesToChangedFiles(filterOutMagicFilesFromModifiedFilesAndSort(modifiedFiles))
          .collect(toImmutableList());
    }
  }

  /**
   * Gets the changed files from {@link DiffOperationsForCommitValidation} which needs to be used to
   * retrieve modified files during commit validation.
   *
   * <p>Rename detection is enabled.
   *
   * @param diffOperationsForCommitValidation the {@link DiffOperationsForCommitValidation} instance
   *     (e.g. from {@link com.google.gerrit.server.events.CommitReceivedEvent#diffOperations}) to
   *     be used to retrieve the modified files
   * @param project the project
   * @param revision the revision for which the changed files should be retrieved
   * @param mergeCommitStrategy the merge commit strategy that should be used to compute the changed
   *     files for merge commits
   * @return the files that have been changed in the given revision, sorted alphabetically by path
   */
  public ImmutableList<ChangedFile> getDuringCommitValidation(
      DiffOperationsForCommitValidation diffOperationsForCommitValidation,
      Project.NameKey project,
      ObjectId revision,
      MergeCommitStrategy mergeCommitStrategy)
      throws IOException, DiffNotAvailableException {
    requireNonNull(project, "project");
    requireNonNull(revision, "revision");
    requireNonNull(mergeCommitStrategy, "mergeCommitStrategy");

    try (Timer0.Context ctx = codeOwnerMetrics.getChangedFiles.start()) {
      Map<String, ModifiedFile> modifiedFiles;
      if (mergeCommitStrategy.equals(MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION)
          || isInitialCommit(project, revision)) {
        // Use parentNum=0 to do the comparison against the default base.
        // For non-merge commits the default base is the only parent (aka parent 1).
        // Initial commits are supported when using parentNum=0.
        // For merge commits the default base is the auto-merge commit which should be used as base
        // if the merge commit strategy is FILES_WITH_CONFLICT_RESOLUTION.
        modifiedFiles =
            diffOperationsForCommitValidation.loadModifiedFilesAgainstParentIfNecessary(
                project, revision, /* parentNum= */ 0, /* enableRenameDetection= */ true);
      } else {
        checkState(mergeCommitStrategy.equals(MergeCommitStrategy.ALL_CHANGED_FILES));
        // Always use parent 1 to do the comparison.
        // Non-merge commits should always be compared against the first parent (initial commits are
        // handled above).
        // For merge commits also the first parent should be used if the merge commit strategy is
        // ALL_CHANGED_FILES.
        modifiedFiles =
            diffOperationsForCommitValidation.loadModifiedFilesAgainstParentIfNecessary(
                project, revision, 1, /* enableRenameDetection= */ true);
      }

      return modifiedFilesToChangedFiles(
              filterOutMagicFilesFromModifiedFilesAndSort(modifiedFiles.values()))
          .collect(toImmutableList());
    }
  }

  private boolean isInitialCommit(Project.NameKey project, ObjectId objectId) throws IOException {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk revWalk = new RevWalk(repo)) {
      return revWalk.parseCommit(objectId).getParentCount() == 0;
    }
  }

  private Stream<ModifiedFile> filterOutMagicFilesFromModifiedFilesAndSort(
      Collection<ModifiedFile> modifiedFiles) {
    return modifiedFiles.stream()
        .filter(modifiedFile -> !Patch.isMagic(modifiedFile.getDefaultPath()))
        .sorted(comparing(ModifiedFile::getDefaultPath));
  }

  private Stream<ChangedFile> modifiedFilesToChangedFiles(Stream<ModifiedFile> modifiedFiles) {
    return modifiedFiles.map(ChangedFile::create);
  }
}
