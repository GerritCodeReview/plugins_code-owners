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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.common.ChangedFile;
import com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.patch.AutoMerger;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ThreeWayMergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/**
 * Class to get/compute the files that have been changed in a revision.
 *
 * <p>The {@link #getFromDiffCache(Project.NameKey, ObjectId)} method is retrieving the file diff
 * from the diff cache and has rename detection enabled.
 *
 * <p>In contrast to this, for the {@code compute} methods the file diff is newly computed on each
 * access and rename detection is disabled (as it's too expensive to do it on each access).
 *
 * <p>If possible, using {@link #getFromDiffCache(Project.NameKey, ObjectId)} is preferred.
 *
 * <p>The {@link com.google.gerrit.server.patch.PatchListCache} is deprecated, and hence it not
 * being used here.
 */
@Singleton
public class ChangedFiles {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static int MAX_CHANGED_FILES_TO_LOG = 25;

  private final GitRepositoryManager repoManager;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final DiffOperations diffOperations;
  private final Provider<AutoMerger> autoMergerProvider;
  private final CodeOwnerMetrics codeOwnerMetrics;
  private final ThreeWayMergeStrategy mergeStrategy;
  private final ExperimentFeatures experimentFeatures;

  @Inject
  public ChangedFiles(
      @GerritServerConfig Config cfg,
      GitRepositoryManager repoManager,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      DiffOperations diffOperations,
      Provider<AutoMerger> autoMergerProvider,
      CodeOwnerMetrics codeOwnerMetrics,
      ExperimentFeatures experimentFeatures) {
    this.repoManager = repoManager;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.diffOperations = diffOperations;
    this.autoMergerProvider = autoMergerProvider;
    this.codeOwnerMetrics = codeOwnerMetrics;
    this.experimentFeatures = experimentFeatures;
    this.mergeStrategy = MergeUtil.getMergeStrategy(cfg);
  }

  /**
   * Returns the changed files for the given revision.
   *
   * <p>By default the changed files are computed on access (see {@link #compute(Project.NameKey,
   * ObjectId)}).
   *
   * <p>Only if enabled via the {@link CodeOwnersExperimentFeaturesConstants#USE_DIFF_CACHE}
   * experiment feature flag the changed files are retrieved from the diff cache (see {@link
   * #getFromDiffCache(Project.NameKey, ObjectId)}).
   *
   * @param project the project
   * @param revision the revision for which the changed files should be computed
   * @return the files that have been changed in the given revision, sorted alphabetically by path
   */
  public ImmutableList<ChangedFile> getOrCompute(Project.NameKey project, ObjectId revision)
      throws IOException, PatchListNotAvailableException, DiffNotAvailableException {
    if (experimentFeatures.isFeatureEnabled(CodeOwnersExperimentFeaturesConstants.USE_DIFF_CACHE)) {
      return getFromDiffCache(project, revision);
    }
    return compute(project, revision);
  }

  /**
   * Computes the files that have been changed in the given revision.
   *
   * <p>The diff is computed against the parent commit.
   *
   * <p>Rename detection is disabled.
   *
   * @param revisionResource the revision resource for which the changed files should be computed
   * @return the files that have been changed in the given revision, sorted alphabetically by path
   * @throws IOException thrown if the computation fails due to an I/O error
   * @throws PatchListNotAvailableException thrown if getting the patch list for a merge commit
   *     against the auto merge failed
   */
  public ImmutableList<ChangedFile> compute(RevisionResource revisionResource)
      throws IOException, PatchListNotAvailableException {
    requireNonNull(revisionResource, "revisionResource");
    return compute(revisionResource.getProject(), revisionResource.getPatchSet().commitId());
  }

  /**
   * Computes the files that have been changed in the given revision.
   *
   * <p>The diff is computed against the parent commit.
   *
   * <p>Rename detection is disabled.
   *
   * @param project the project
   * @param revision the revision for which the changed files should be computed
   * @return the files that have been changed in the given revision, sorted alphabetically by path
   * @throws IOException thrown if the computation fails due to an I/O error
   * @throws PatchListNotAvailableException thrown if getting the patch list for a merge commit
   *     against the auto merge failed
   */
  public ImmutableList<ChangedFile> compute(Project.NameKey project, ObjectId revision)
      throws IOException, PatchListNotAvailableException {
    requireNonNull(project, "project");
    requireNonNull(revision, "revision");

    return compute(
        project,
        revision,
        codeOwnersPluginConfiguration.getProjectConfig(project).getMergeCommitStrategy());
  }

  public ImmutableList<ChangedFile> compute(
      Project.NameKey project, ObjectId revision, MergeCommitStrategy mergeCommitStrategy)
      throws IOException {
    requireNonNull(project, "project");
    requireNonNull(revision, "revision");
    requireNonNull(mergeCommitStrategy, "mergeCommitStrategy");

    logger.atFine().log(
        "computing changed files for revision %s in project %s", revision.name(), project);

    try (Repository repo = repoManager.openRepository(project);
        RevWalk revWalk = new RevWalk(repo)) {
      RevCommit revCommit = revWalk.parseCommit(revision);
      if (revCommit.getParentCount() > 1
          && MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION.equals(mergeCommitStrategy)) {
        RevCommit autoMergeCommit = getAutoMergeCommit(project, revCommit);
        return compute(repo.getConfig(), revWalk, revCommit, autoMergeCommit);
      }

      RevCommit baseCommit = revCommit.getParentCount() > 0 ? revCommit.getParent(0) : null;
      return compute(repo.getConfig(), revWalk, revCommit, baseCommit);
    }
  }

  private RevCommit getAutoMergeCommit(Project.NameKey project, RevCommit mergeCommit)
      throws IOException {
    try (Timer0.Context ctx = codeOwnerMetrics.getAutoMerge.start();
        Repository repository = repoManager.openRepository(project);
        InMemoryInserter inserter = new InMemoryInserter(repository);
        ObjectReader reader = inserter.newReader();
        RevWalk revWalk = new RevWalk(reader)) {
      return autoMergerProvider
          .get()
          .lookupFromGitOrMergeInMemory(repository, revWalk, inserter, mergeCommit, mergeStrategy);
    }
  }

  /**
   * Computes the changed files by comparing the given commit against the given base commit.
   *
   * <p>The computation also works if the commit doesn't have any parent.
   *
   * <p>Rename detection is disabled.
   *
   * @param repoConfig the repository configuration
   * @param revWalk the rev walk
   * @param commit the commit for which the changed files should be computed
   * @param baseCommit the base commit against which the given commit should be compared, {@code
   *     null} if the commit doesn't have any parent commit
   * @return the changed files for the given commit, sorted alphabetically by path
   */
  private ImmutableList<ChangedFile> compute(
      Config repoConfig, RevWalk revWalk, RevCommit commit, @Nullable RevCommit baseCommit)
      throws IOException {
    logger.atFine().log("baseCommit = %s", baseCommit != null ? baseCommit.name() : "n/a");
    try (Timer0.Context ctx = codeOwnerMetrics.computeChangedFiles.start()) {
      // Detecting renames is expensive (since it requires Git to load and compare file contents of
      // added and deleted files) and can significantly increase the latency for changes that touch
      // large files. To avoid this latency we do not enable the rename detection on the
      // DiffFormater. As a result of this renamed files will be returned as 2 ChangedFile's, one
      // for the deletion of the old path and one for the addition of the new path.
      try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
        diffFormatter.setReader(revWalk.getObjectReader(), repoConfig);
        diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
        List<DiffEntry> diffEntries = diffFormatter.scan(baseCommit, commit);
        ImmutableList<ChangedFile> changedFiles =
            diffEntries.stream().map(ChangedFile::create).collect(toImmutableList());
        if (changedFiles.size() <= MAX_CHANGED_FILES_TO_LOG) {
          logger.atFine().log("changed files = %s", changedFiles);
        } else {
          logger.atFine().log(
              "changed files = %s (and %d more)",
              changedFiles.asList().subList(0, MAX_CHANGED_FILES_TO_LOG),
              changedFiles.size() - MAX_CHANGED_FILES_TO_LOG);
        }
        return changedFiles;
      }
    }
  }

  /**
   * Gets the changed files from the diff cache.
   *
   * <p>Rename detection is enabled.
   */
  @VisibleForTesting
  ImmutableList<ChangedFile> getFromDiffCache(Project.NameKey project, ObjectId revision)
      throws IOException, DiffNotAvailableException {
    requireNonNull(project, "project");
    requireNonNull(revision, "revision");

    MergeCommitStrategy mergeCommitStrategy =
        codeOwnersPluginConfiguration.getProjectConfig(project).getMergeCommitStrategy();

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
