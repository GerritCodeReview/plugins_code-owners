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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.common.ChangedFile;
import com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
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
  private final PatchListCache patchListCache;
  private final CodeOwnerMetrics codeOwnerMetrics;

  @Inject
  public ChangedFiles(
      GitRepositoryManager repoManager,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      PatchListCache patchListCache,
      CodeOwnerMetrics codeOwnerMetrics) {
    this.repoManager = repoManager;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.patchListCache = patchListCache;
    this.codeOwnerMetrics = codeOwnerMetrics;
  }

  /**
   * Computes the files that have been changed in the given revision.
   *
   * <p>The diff is computed against the parent commit.
   *
   * @param revisionResource the revision resource for which the changed files should be computed
   * @return the files that have been changed in the given revision
   * @throws IOException thrown if the computation fails due to an I/O error
   * @throws PatchListNotAvailableException thrown if getting the patch list for a merge commit
   *     against the auto merge failed
   */
  public ImmutableSet<ChangedFile> compute(RevisionResource revisionResource)
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
   * @return the files that have been changed in the given revision
   * @throws IOException thrown if the computation fails due to an I/O error
   * @throws PatchListNotAvailableException thrown if getting the patch list for a merge commit
   *     against the auto merge failed
   */
  public ImmutableSet<ChangedFile> compute(Project.NameKey project, ObjectId revision)
      throws IOException, PatchListNotAvailableException {
    requireNonNull(project, "project");
    requireNonNull(revision, "revision");

    try (Repository repository = repoManager.openRepository(project);
        RevWalk revWalk = new RevWalk(repository)) {
      RevCommit revCommit = revWalk.parseCommit(revision);
      return compute(project, repository.getConfig(), revWalk, revCommit);
    }
  }

  public ImmutableSet<ChangedFile> compute(
      Project.NameKey project, Config repoConfig, RevWalk revWalk, RevCommit revCommit)
      throws IOException, PatchListNotAvailableException {
    return compute(
        project,
        repoConfig,
        revWalk,
        revCommit,
        codeOwnersPluginConfiguration.getProjectConfig(project).getMergeCommitStrategy());
  }

  public ImmutableSet<ChangedFile> compute(
      Project.NameKey project,
      Config repoConfig,
      RevWalk revWalk,
      RevCommit revCommit,
      MergeCommitStrategy mergeCommitStrategy)
      throws IOException, PatchListNotAvailableException {
    requireNonNull(project, "project");
    requireNonNull(repoConfig, "repoConfig");
    requireNonNull(revWalk, "revWalk");
    requireNonNull(revCommit, "revCommit");
    requireNonNull(mergeCommitStrategy, "mergeCommitStrategy");

    logger.atFine().log(
        "computing changed files for revision %s in project %s", revCommit.name(), project);

    if (revCommit.getParentCount() > 1
        && MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION.equals(mergeCommitStrategy)) {
      return computeByComparingAgainstAutoMerge(project, revCommit);
    }

    return computeByComparingAgainstFirstParent(repoConfig, revWalk, revCommit);
  }

  /**
   * Computes the changed files by comparing the given merge commit against the auto merge.
   *
   * <p>Since computing the auto merge is expensive, we do not compute it and diff against it on our
   * own, but rather ask the patch list cache for it.
   *
   * @param project the project that contains the merge commit
   * @param mergeCommit the merge commit for which the changed files should be computed
   * @return the changed files for the given merge commit
   */
  private ImmutableSet<ChangedFile> computeByComparingAgainstAutoMerge(
      Project.NameKey project, RevCommit mergeCommit) throws PatchListNotAvailableException {
    checkState(
        mergeCommit.getParentCount() > 1, "expected %s to be a merge commit", mergeCommit.name());

    try (Timer0.Context ctx = codeOwnerMetrics.computeChangedFilesAgainstAutoMerge.start()) {
      // for merge commits the default base is the auto merge commit
      PatchListKey patchListKey =
          PatchListKey.againstDefaultBase(mergeCommit, Whitespace.IGNORE_NONE);

      return patchListCache.get(patchListKey, project).getPatches().stream()
          .filter(
              patchListEntry ->
                  patchListEntry.getNewName() == null
                      || !Patch.isMagic(patchListEntry.getNewName()))
          .map(ChangedFile::create)
          .collect(toImmutableSet());
    }
  }

  /**
   * Computes the changed files by comparing the given commit against its first parent.
   *
   * <p>The computation also works if the commit doesn't have any parent.
   *
   * @param repoConfig the repository configuration
   * @param revWalk the rev walk
   * @param revCommit the commit for which the changed files should be computed
   * @return the changed files for the given commit
   */
  private ImmutableSet<ChangedFile> computeByComparingAgainstFirstParent(
      Config repoConfig, RevWalk revWalk, RevCommit revCommit) throws IOException {
    try (Timer0.Context ctx = codeOwnerMetrics.computeChangedFilesAgainstFirstParent.start()) {
      RevCommit baseCommit = revCommit.getParentCount() > 0 ? revCommit.getParent(0) : null;
      logger.atFine().log("baseCommit = %s", baseCommit != null ? baseCommit.name() : "n/a");

      try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
        diffFormatter.setReader(revWalk.getObjectReader(), repoConfig);
        diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
        List<DiffEntry> diffEntries = diffFormatter.scan(baseCommit, revCommit);
        ImmutableSet<ChangedFile> changedFiles =
            diffEntries.stream().map(ChangedFile::create).collect(toImmutableSet());
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
