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

package com.google.gerrit.plugins.codeowners.testing.backend;

import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Optional;
import java.util.function.Consumer;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;

/** Test utility to read/write code owner configs directly from/to a repository. */
public class TestCodeOwnerConfigStorage {
  public interface Factory {
    TestCodeOwnerConfigStorage create(String fileName, CodeOwnerConfigParser codeOwnerConfigParser);
  }

  private final String defaultFileName;
  private final GitRepositoryManager repoManager;
  private final CodeOwnerConfigParser codeOwnerConfigParser;

  @Inject
  TestCodeOwnerConfigStorage(
      GitRepositoryManager repoManager,
      @Assisted String defaultFileName,
      @Assisted CodeOwnerConfigParser codeOwnerConfigParser) {
    this.defaultFileName = defaultFileName;
    this.repoManager = repoManager;
    this.codeOwnerConfigParser = codeOwnerConfigParser;
  }

  /**
   * Write a code owner config directly to a repository.
   *
   * <p>Requires that the repository and the branch in which the code owner config should be stored
   * already exist.
   *
   * @param codeOwnerConfigKey the key of the code owner config that should be written
   * @param codeOwnerConfigUpdater consumer for a code owner config builder on which the code owner
   *     config properties should be set
   * @return the code owner config that was written
   */
  public CodeOwnerConfig writeCodeOwnerConfig(
      CodeOwnerConfig.Key codeOwnerConfigKey,
      Consumer<CodeOwnerConfig.Builder> codeOwnerConfigUpdater)
      throws Exception {
    CodeOwnerConfig.Builder codeOwnerConfigBuilder =
        readCodeOwnerConfig(codeOwnerConfigKey)
            .map(key -> key.toBuilder())
            .orElse(
                CodeOwnerConfig.builder(
                    codeOwnerConfigKey,
                    ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef")));
    codeOwnerConfigUpdater.accept(codeOwnerConfigBuilder);
    CodeOwnerConfig codeOwnerConfig = codeOwnerConfigBuilder.build();

    String formattedCodeOwnerConfig = codeOwnerConfigParser.formatAsString(codeOwnerConfig);
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(codeOwnerConfigKey.project()))) {
      Ref ref = testRepo.getRepository().exactRef(codeOwnerConfigKey.ref());
      RevCommit head = testRepo.getRevWalk().parseCommit(ref.getObjectId());
      RevCommit revision =
          testRepo.update(
              codeOwnerConfigKey.ref(),
              testRepo
                  .commit()
                  .parent(head)
                  .message("Add test code owner config")
                  .add(
                      JgitPath.of(codeOwnerConfigKey.filePath(defaultFileName)).get(),
                      formattedCodeOwnerConfig));

      return codeOwnerConfig.toBuilder().setRevision(revision).build();
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
      String filePath = JgitPath.of(codeOwnerConfigKey.filePath(defaultFileName)).get();
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
          codeOwnerConfigParser.parse(commit, codeOwnerConfigKey, RawParseUtils.decode(data)));
    }
  }
}
