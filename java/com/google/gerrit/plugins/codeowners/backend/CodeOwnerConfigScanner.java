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
import static java.util.Objects.requireNonNull;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.plugins.codeowners.JgitPath;
import com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/** Class to scan a branch for code owner config files. */
@Singleton
public class CodeOwnerConfigScanner {
  private final GitRepositoryManager repoManager;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;

  @Inject
  CodeOwnerConfigScanner(
      GitRepositoryManager repoManager,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration) {
    this.repoManager = repoManager;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
  }

  /**
   * Visits all code owner config files in the given project and branch.
   *
   * @param branchNameKey the project and branch for which the code owner config files should be
   *     visited
   * @param codeOwnerConfigVisitor the callback that is invoked for each code owner config file
   */
  public void visit(BranchNameKey branchNameKey, CodeOwnerConfigVisitor codeOwnerConfigVisitor) {
    requireNonNull(branchNameKey, "branchNameKey");
    requireNonNull(codeOwnerConfigVisitor, "codeOwnerConfigVisitor");

    CodeOwnerBackend codeOwnerBackend = codeOwnersPluginConfiguration.getBackend(branchNameKey);

    try (Repository repository = repoManager.openRepository(branchNameKey.project());
        RevWalk rw = new RevWalk(repository);
        TreeWalk treeWalk = new TreeWalk(repository)) {
      Ref ref = repository.exactRef(branchNameKey.branch());
      checkState(
          ref != null,
          "branch %s of project %s not found",
          branchNameKey.branch(),
          branchNameKey.project());

      RevCommit revision = rw.parseCommit(ref.getObjectId());
      treeWalk.addTree(revision.getTree());
      treeWalk.setRecursive(true);
      treeWalk.setFilter(createCodeOwnerConfigFilter(codeOwnerBackend, branchNameKey.project()));

      while (treeWalk.next()) {
        Path filePath = Paths.get(treeWalk.getPathString());
        Path folderPath =
            filePath.getParent() != null
                ? JgitPath.of(filePath.getParent()).getAsAbsolutePath()
                : Paths.get("/");
        String fileName = filePath.getFileName().toString();
        CodeOwnerConfig.Key codeOwnerConfigKey =
            CodeOwnerConfig.Key.create(branchNameKey, folderPath, fileName);
        Optional<CodeOwnerConfig> codeOwnerConfig =
            codeOwnerBackend.getCodeOwnerConfig(codeOwnerConfigKey, revision);
        checkState(codeOwnerConfig.isPresent(), "code owner config %s not found", codeOwnerConfig);
        boolean visitFurtherCodeOwnerConfigFiles =
            codeOwnerConfigVisitor.visit(codeOwnerConfig.get());
        if (!visitFurtherCodeOwnerConfigFiles) {
          break;
        }
      }
    } catch (IOException e) {
      throw new StorageException(
          String.format(
              "Failed to scan for code owner configs in branch %s of project %s",
              branchNameKey.branch(), branchNameKey.project()),
          e);
    }
  }

  /** Creates a {@link TreeFilter} that matches code owner config files in the given project. */
  private static TreeFilter createCodeOwnerConfigFilter(
      CodeOwnerBackend codeOwnerBackend, Project.NameKey project) {
    return new TreeFilter() {
      @Override
      public boolean shouldBeRecursive() {
        return true;
      }

      @Override
      public boolean include(TreeWalk walker) throws IOException {
        if (walker.isSubtree()) {
          walker.enterSubtree();
          return false;
        }
        String fileName = Paths.get(walker.getPathString()).getFileName().toString();
        return codeOwnerBackend.isCodeOwnerConfigFile(project, fileName);
      }

      @Override
      public TreeFilter clone() {
        return this;
      }
    };
  }
}
