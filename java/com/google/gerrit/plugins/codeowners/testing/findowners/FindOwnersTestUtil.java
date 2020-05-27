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

package com.google.gerrit.plugins.codeowners.testing.findowners;

import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.findowners.CodeOwnerConfigFile;
import com.google.gerrit.plugins.codeowners.backend.findowners.CodeOwnerConfigParser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import java.util.Optional;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;

/** Test utility to read/write find-owners code owner configs directly from/to a repository. */
public class FindOwnersTestUtil {
  private final GitRepositoryManager repoManager;
  private final CodeOwnerConfigParser codeOwnerConfigParser;

  @Inject
  FindOwnersTestUtil(
      GitRepositoryManager repoManager, CodeOwnerConfigParser codeOwnerConfigParser) {
    this.repoManager = repoManager;
    this.codeOwnerConfigParser = codeOwnerConfigParser;
  }

  /**
   * Write a code owner config directly to a repository.
   *
   * <p>Requires that the repository and the branch in which the code owner config should be stored
   * already exist.
   *
   * @param codeOwnerConfig the code owner config that should be written
   */
  public void writeCodeOwnerConfig(CodeOwnerConfig codeOwnerConfig) throws Exception {
    String formattedCodeOwnerConfig = codeOwnerConfigParser.formatAsString(codeOwnerConfig);
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(codeOwnerConfig.key().project()))) {
      Ref ref = testRepo.getRepository().exactRef(codeOwnerConfig.key().ref());
      RevCommit head = testRepo.getRevWalk().parseCommit(ref.getObjectId());
      testRepo.update(
          codeOwnerConfig.key().ref(),
          testRepo
              .commit()
              .parent(head)
              .message("Add test code owner config")
              .add(
                  codeOwnerConfig.key().filePathForJgit(CodeOwnerConfigFile.FILE_NAME),
                  formattedCodeOwnerConfig));
    }
  }

  /**
   * Reads a code owner config directly from a repository.
   *
   * @param codeOwnerConfigKey the key of the code owner config that should be read
   * @return the code owner config if it exist, otherwise {@link Optional#empty()}
   */
  public Optional<CodeOwnerConfig> readCodeOwnerConfig(CodeOwnerConfig.Key codeOwnerConfigKey)
      throws Exception {
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(codeOwnerConfigKey.project()))) {
      Ref ref = testRepo.getRepository().exactRef(codeOwnerConfigKey.ref());
      if (ref == null) {
        // branch does not exist
        return Optional.empty();
      }

      RevCommit commit = testRepo.getRevWalk().parseCommit(ref.getObjectId());
      String filePath = codeOwnerConfigKey.filePathForJgit(CodeOwnerConfigFile.FILE_NAME);
      try (TreeWalk tw =
          TreeWalk.forPath(testRepo.getRevWalk().getObjectReader(), filePath, commit.getTree())) {
        if (tw == null) {
          // file does not exist
          return Optional.empty();
        }
      }
      RevObject blob = testRepo.get(commit.getTree(), filePath);
      byte[] data = testRepo.getRepository().open(blob).getCachedBytes(Integer.MAX_VALUE);
      return Optional.of(
          codeOwnerConfigParser.parse(codeOwnerConfigKey, RawParseUtils.decode(data)));
    }
  }
}
