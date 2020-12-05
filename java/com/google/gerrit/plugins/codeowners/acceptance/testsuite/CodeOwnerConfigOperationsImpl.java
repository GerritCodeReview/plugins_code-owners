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

package com.google.gerrit.plugins.codeowners.acceptance.testsuite;

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.plugins.codeowners.backend.BackendConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportModification;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigUpdate;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSetModification;
import com.google.gerrit.plugins.codeowners.backend.CodeOwners;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnersUpdate;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * The implementation of {@link CodeOwnerConfigOperations}.
 *
 * <p>There is only one implementation of {@link CodeOwnerConfigOperations}. Nevertheless, we keep
 * the separation between interface and implementation to enhance clarity.
 */
public class CodeOwnerConfigOperationsImpl implements CodeOwnerConfigOperations {
  private final GitRepositoryManager repoManager;
  private final CodeOwners codeOwners;
  private final Provider<CodeOwnersUpdate> codeOwnersUpdate;
  private final ProjectCache projectCache;
  private final BackendConfig backendConfig;

  @Inject
  CodeOwnerConfigOperationsImpl(
      GitRepositoryManager repoManager,
      CodeOwners codeOwners,
      @ServerInitiated Provider<CodeOwnersUpdate> codeOwnersUpdate,
      ProjectCache projectCache,
      BackendConfig backendConfig) {
    this.repoManager = repoManager;
    this.codeOwners = codeOwners;
    this.codeOwnersUpdate = codeOwnersUpdate;
    this.projectCache = projectCache;
    this.backendConfig = backendConfig;
  }

  @Override
  public PerCodeOwnerConfigOperations codeOwnerConfig(CodeOwnerConfig.Key codeOwnerConfigKey) {
    return new PerCodeOwnerConfigOperationsImpl(codeOwnerConfigKey);
  }

  @Override
  public TestCodeOwnerConfigCreation.Builder newCodeOwnerConfig() {
    return TestCodeOwnerConfigCreation.builder(this::createNewCodeOwnerConfig);
  }

  private CodeOwnerConfig.Key createNewCodeOwnerConfig(
      TestCodeOwnerConfigCreation codeOwnerConfigCreation) throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = codeOwnerConfigCreation.key();

    checkState(
        !codeOwnerConfig(codeOwnerConfigKey).exists(),
        "code owner config %s already exists",
        codeOwnerConfigKey);

    if (codeOwnerConfigCreation.isEmpty()) {
      throw new IllegalStateException("code owner config must not be empty");
    }

    return codeOwnersUpdate
        .get()
        .upsertCodeOwnerConfig(
            codeOwnerConfigKey,
            CodeOwnerConfigUpdate.builder()
                .setIgnoreParentCodeOwners(codeOwnerConfigCreation.ignoreParentCodeOwners())
                .setCodeOwnerSetsModification(
                    CodeOwnerSetModification.set(codeOwnerConfigCreation.computeCodeOwnerSets()))
                .setImportsModification(
                    CodeOwnerConfigImportModification.set(codeOwnerConfigCreation.imports()))
                .build())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format("Code owner config %s didn't get created", codeOwnerConfigKey)))
        .key();
  }

  /**
   * The implementation of {@link CodeOwnerConfigOperations.PerCodeOwnerConfigOperations}.
   *
   * <p>There is only one implementation of {@link
   * CodeOwnerConfigOperations.PerCodeOwnerConfigOperations}. Nevertheless, we keep the separation
   * between interface and implementation to enhance clarity.
   */
  private class PerCodeOwnerConfigOperationsImpl implements PerCodeOwnerConfigOperations {
    private final CodeOwnerConfig.Key codeOwnerConfigKey;

    PerCodeOwnerConfigOperationsImpl(CodeOwnerConfig.Key codeOwnerConfigKey) {
      this.codeOwnerConfigKey = codeOwnerConfigKey;
    }

    @Override
    public boolean exists() {
      return getCodeOwnerConfig().isPresent();
    }

    @Override
    public CodeOwnerConfig get() {
      return getCodeOwnerConfig()
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      String.format("code owner config %s does not exist", codeOwnerConfigKey)));
    }

    @Override
    public String getJGitFilePath() {
      return JgitPath.of(filePath()).get();
    }

    @Override
    public String getFilePath() {
      return filePath().toString();
    }

    private Path filePath() {
      if (projectCache.get(codeOwnerConfigKey.project()).isPresent()) {
        return codeOwners.getFilePath(codeOwnerConfigKey);
      }

      // if the project doesn't exist, use the backend which is configured on host level to compute
      // the file path
      return backendConfig.getDefaultBackend().getFilePath(codeOwnerConfigKey);
    }

    @Override
    public String getContent() throws Exception {
      try (TestRepository<Repository> testRepo =
          new TestRepository<>(repoManager.openRepository(codeOwnerConfigKey.project()))) {
        Ref ref = testRepo.getRepository().exactRef(codeOwnerConfigKey.ref());
        if (ref == null) {
          throw new IllegalStateException(
              String.format("code owner config %s does not exist", codeOwnerConfigKey));
        }
        RevCommit commit = testRepo.getRevWalk().parseCommit(ref.getObjectId());
        try (TreeWalk tw =
            TreeWalk.forPath(
                testRepo.getRevWalk().getObjectReader(), getJGitFilePath(), commit.getTree())) {
          if (tw == null) {
            throw new IllegalStateException(
                String.format("code owner config %s does not exist", codeOwnerConfigKey));
          }
        }
        RevObject blob = testRepo.get(commit.getTree(), getJGitFilePath());
        byte[] data = testRepo.getRepository().open(blob).getCachedBytes(Integer.MAX_VALUE);
        return RawParseUtils.decode(data);
      } catch (RepositoryNotFoundException e) {
        throw new IllegalStateException(
            String.format("code owner config %s does not exist", codeOwnerConfigKey));
      }
    }

    @Override
    public TestCodeOwnerConfigUpdate.Builder forUpdate() {
      return TestCodeOwnerConfigUpdate.builder(this::updateCodeOwnerConfig);
    }

    private Optional<CodeOwnerConfig> getCodeOwnerConfig() {
      return codeOwners.getFromCurrentRevision(codeOwnerConfigKey);
    }

    private void updateCodeOwnerConfig(TestCodeOwnerConfigUpdate codeOwnerConfigUpdate)
        throws Exception {
      codeOwnersUpdate
          .get()
          .upsertCodeOwnerConfig(
              codeOwnerConfigKey,
              CodeOwnerConfigUpdate.builder()
                  .setIgnoreParentCodeOwners(codeOwnerConfigUpdate.ignoreParentCodeOwners())
                  .setCodeOwnerSetsModification(
                      codeOwnerConfigUpdate.codeOwnerSetsModification()::apply)
                  .setImportsModification(codeOwnerConfigUpdate.importsModification()::apply)
                  .build());
    }
  }
}
