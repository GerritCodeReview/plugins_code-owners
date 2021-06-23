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
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Class to get the files that have been changed in a revision.
 *
 * <p>The {@link #getFromDiffCache(Project.NameKey, ObjectId, MergeCommitStrategy)} method is
 * retrieving the file diff from the diff cache and has rename detection enabled.
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
   * Gets the changed files from the diff cache.
   *
   * <p>Rename detection is enabled.
   *
   * @param project the project
   * @param revision the revision for which the changed files should be retrieved
   * @param mergeCommitStrategy the merge commit strategy that should be used to compute the changed
   *     files for merge commits
   * @return the files that have been changed in the given revision, sorted alphabetically by path
   */
  public ImmutableList<ChangedFile> getFromDiffCache(
      Project.NameKey project, ObjectId revision, MergeCommitStrategy mergeCommitStrategy)
      throws IOException, DiffNotAvailableException {
    requireNonNull(project, "project");
    requireNonNull(revision, "revision");
    requireNonNull(mergeCommitStrategy, "mergeCommitStrategy");

    try (Timer0.Context ctx = codeOwnerMetrics.getChangedFiles.start()) {
      Map<String, FileDiffOutput> fileDiffOutputs;
      if (mergeCommitStrategy.equals(MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION)
          || isInitialCommit(project, revision)) {
        // Use parentNum=null to do the comparison against the default base.
        // For non-merge commits the default base is the only parent (aka parent 1, initial commits
        // are not supported).
        // For merge commits the default base is the auto-merge commit which should be used as base
        // if the merge commit strategy is FILES_WITH_CONFLICT_RESOLUTION.
        fileDiffOutputs =
            diffOperations.listModifiedFilesAgainstParent(project, revision, /* parentNum=*/ null);
      } else {
        checkState(mergeCommitStrategy.equals(MergeCommitStrategy.ALL_CHANGED_FILES));
        // Always use parent 1 to do the comparison.
        // Non-merge commits should always be compared against the first parent (initial commits are
        // handled above).
        // For merge commits also the first parent should be used if the merge commit strategy is
        // ALL_CHANGED_FILES.
        fileDiffOutputs = diffOperations.listModifiedFilesAgainstParent(project, revision, 1);
      }

      return toChangedFiles(filterOutMagicFilesAndSort(fileDiffOutputs)).collect(toImmutableList());
    }
  }

  /**
   * Gets the changed files from the diff cache.
   *
   * <p>Rename detection is enabled.
   *
   * <p>Uses the configured merge commit strategy.
   *
   * @param project the project
   * @param revision the revision for which the changed files should be retrieved
   * @return the files that have been changed in the given revision, sorted alphabetically by path
   * @throws IOException thrown if the computation fails due to an I/O error
   */
  public ImmutableList<ChangedFile> getFromDiffCache(Project.NameKey project, ObjectId revision)
      throws IOException, DiffNotAvailableException {
    requireNonNull(project, "project");
    requireNonNull(revision, "revision");
    return getFromDiffCache(
        project,
        revision,
        codeOwnersPluginConfiguration.getProjectConfig(project).getMergeCommitStrategy());
  }

  /**
   * Gets the changed files from the diff cache.
   *
   * <p>Rename detection is enabled.
   *
   * <p>Uses the configured merge commit strategy.
   *
   * @param revisionResource the revision resource for which the changed files should be retrieved
   * @return the files that have been changed in the given revision, sorted alphabetically by path
   * @throws IOException thrown if the computation fails due to an I/O error
   * @see #getFromDiffCache(Project.NameKey, ObjectId, MergeCommitStrategy)
   */
  public ImmutableList<ChangedFile> getFromDiffCache(RevisionResource revisionResource)
      throws IOException, DiffNotAvailableException {
    requireNonNull(revisionResource, "revisionResource");
    return getFromDiffCache(
        revisionResource.getProject(), revisionResource.getPatchSet().commitId());
  }

  private boolean isInitialCommit(Project.NameKey project, ObjectId objectId) throws IOException {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk revWalk = new RevWalk(repo)) {
      return revWalk.parseCommit(objectId).getParentCount() == 0;
    }
  }

  private Stream<Map.Entry<String, FileDiffOutput>> filterOutMagicFilesAndSort(
      Map<String, FileDiffOutput> fileDiffOutputs) {
    return fileDiffOutputs.entrySet().stream()
        .filter(e -> !Patch.isMagic(e.getKey()))
        .sorted(comparing(Map.Entry::getKey));
  }

  private Stream<ChangedFile> toChangedFiles(
      Stream<Map.Entry<String, FileDiffOutput>> fileDiffOutputs) {
    return fileDiffOutputs.map(Map.Entry::getValue).map(ChangedFile::create);
  }
}
