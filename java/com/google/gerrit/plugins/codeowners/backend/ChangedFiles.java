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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
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
  private final GitRepositoryManager repoManager;

  @Inject
  public ChangedFiles(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  /**
   * Computes the files that have been changed in the given revision.
   *
   * <p>The diff is computed against the parent commit.
   *
   * @param revisionResource the revision resource for which the changed files should be computed
   * @return the files that have been changed in the given revision
   * @throws IOException thrown if the computation fails due to an I/O error
   */
  public ImmutableSet<ChangedFile> compute(RevisionResource revisionResource) throws IOException {
    requireNonNull(revisionResource, "revisionResource");
    try (Repository repository = repoManager.openRepository(revisionResource.getProject());
        RevWalk revWalk = new RevWalk(repository)) {
      RevCommit revCommit = revWalk.parseCommit(revisionResource.getPatchSet().commitId());
      RevCommit baseCommit = revCommit.getParentCount() > 0 ? revCommit.getParent(0) : null;
      try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
        diffFormatter.setReader(revWalk.getObjectReader(), repository.getConfig());
        diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
        diffFormatter.setDetectRenames(true);
        List<DiffEntry> diffEntries = diffFormatter.scan(baseCommit, revCommit);
        return diffEntries.stream().map(ChangedFile::create).collect(toImmutableSet());
      }
    }
  }
}
