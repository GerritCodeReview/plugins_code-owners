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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.common.ChangedFile;
import com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.patch.AutoMerger;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ThreeWayMergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/**
 * Class to compute the files that have been changed in a revision.
 *
 * <p>The file diff is newly computed on each access and not retrieved from any cache. This is
 * better than using {@link com.google.gerrit.server.patch.PatchListCache} which does a lot of
 * unneeded computations and hence is slower. The Gerrit diff caches are currently being redesigned.
 * Once the envisioned {@code ModifiedFilesCache} is available we should consider using it.
 */
@Singleton
public class ChangedFiles {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static int MAX_CHANGED_FILES_TO_LOG = 25;

  private final GitRepositoryManager repoManager;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final Provider<AutoMerger> autoMergerProvider;
  private final CodeOwnerMetrics codeOwnerMetrics;
  private final ThreeWayMergeStrategy mergeStrategy;
  private final boolean saveAutoMergeCommits;

  @Inject
  public ChangedFiles(
      @GerritServerConfig Config cfg,
      GitRepositoryManager repoManager,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      Provider<AutoMerger> autoMergerProvider,
      CodeOwnerMetrics codeOwnerMetrics) {
    this.repoManager = repoManager;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.autoMergerProvider = autoMergerProvider;
    this.codeOwnerMetrics = codeOwnerMetrics;
    this.mergeStrategy = MergeUtil.getMergeStrategy(cfg);
    this.saveAutoMergeCommits = AutoMerger.cacheAutomerge(cfg);
  }

  /**
   * Computes the files that have been changed in the given revision.
   *
   * <p>The diff is computed against the parent commit.
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

    try (Repository repository = repoManager.openRepository(project);
        RevWalk revWalk = new RevWalk(repository)) {
      RevCommit revCommit = revWalk.parseCommit(revision);
      return compute(project, repository.getConfig(), revWalk, revCommit);
    }
  }

  public ImmutableList<ChangedFile> compute(
      Project.NameKey project, Config repoConfig, RevWalk revWalk, RevCommit revCommit)
      throws IOException {
    return compute(
        project,
        repoConfig,
        revWalk,
        revCommit,
        codeOwnersPluginConfiguration.getProjectConfig(project).getMergeCommitStrategy());
  }

  public ImmutableList<ChangedFile> compute(
      Project.NameKey project,
      Config repoConfig,
      RevWalk revWalk,
      RevCommit revCommit,
      MergeCommitStrategy mergeCommitStrategy)
      throws IOException {
    requireNonNull(project, "project");
    requireNonNull(repoConfig, "repoConfig");
    requireNonNull(revWalk, "revWalk");
    requireNonNull(revCommit, "revCommit");
    requireNonNull(mergeCommitStrategy, "mergeCommitStrategy");

    logger.atFine().log(
        "computing changed files for revision %s in project %s", revCommit.name(), project);

    if (revCommit.getParentCount() > 1
        && MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION.equals(mergeCommitStrategy)) {
      RevCommit autoMergeCommit = getAutoMergeCommit(project, revCommit);
      return compute(repoConfig, revWalk, revCommit, autoMergeCommit);
    }

    RevCommit baseCommit = revCommit.getParentCount() > 0 ? revCommit.getParent(0) : null;
    return compute(repoConfig, revWalk, revCommit, baseCommit);
  }

  private RevCommit getAutoMergeCommit(Project.NameKey project, RevCommit mergeCommit)
      throws IOException {
    try (Timer0.Context ctx = codeOwnerMetrics.getAutoMerge.start();
        Repository repository = repoManager.openRepository(project);
        ObjectInserter inserter =
            saveAutoMergeCommits
                ? repository.newObjectInserter()
                : new InMemoryInserter(repository);
        ObjectReader reader = inserter.newReader();
        RevWalk revWalk = new RevWalk(reader)) {
      return autoMergerProvider
          .get()
          .merge(repository, revWalk, inserter, mergeCommit, mergeStrategy);
    }
  }

  /**
   * Computes the changed files by comparing the given commit against the given base commit.
   *
   * <p>The computation also works if the commit doesn't have any parent.
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
}
