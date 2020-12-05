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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.RawParseUtils;

/** {@link TreeWalk} that filters for code owner config files in the tree. */
public class CodeOwnerConfigTreeWalk extends TreeWalk {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CodeOwnerBackend codeOwnerBackend;
  private final BranchNameKey branchNameKey;
  private final RevCommit revision;

  public CodeOwnerConfigTreeWalk(
      CodeOwnerBackend codeOwnerBackend,
      BranchNameKey branchNameKey,
      Repository repository,
      RevWalk revWalk,
      @Nullable String pathGlob)
      throws IOException {
    super(repository);

    this.codeOwnerBackend = requireNonNull(codeOwnerBackend, "codeOwnerBackend");
    this.branchNameKey = requireNonNull(branchNameKey, "branchNameKey");
    this.revision =
        getRevision(
            branchNameKey,
            requireNonNull(repository, "repository"),
            requireNonNull(revWalk, "revWalk"));

    addTree(revision.getTree());
    setRecursive(true);
    setFilter(createCodeOwnerConfigFilter(codeOwnerBackend, branchNameKey.project(), pathGlob));
  }

  /**
   * Returns the revision from which the tree was loaded.
   *
   * @return the revision ID
   */
  public RevCommit getRevision() {
    return revision;
  }

  /** Returns the absolute file path of the current entry. */
  public Path getFilePath() {
    return JgitPath.of(getPathString()).getAsAbsolutePath();
  }

  /** Returns the file content of the current entry. */
  public String getFileContent() throws IOException {
    ObjectLoader obj = getObjectReader().open(getObjectId(0), Constants.OBJ_BLOB);
    byte[] raw = obj.getCachedBytes(Integer.MAX_VALUE);
    return raw.length != 0 ? RawParseUtils.decode(raw) : "";
  }

  /** Returns the code owner config key of the current entry. */
  public CodeOwnerConfig.Key getCodeOwnerConfigKey() {
    Path filePath = getFilePath();
    Path folderPath =
        filePath.getParent() != null
            ? JgitPath.of(filePath.getParent()).getAsAbsolutePath()
            : Paths.get("/");
    String fileName = Paths.get(getPathString()).getFileName().toString();
    return CodeOwnerConfig.Key.create(branchNameKey, folderPath, fileName);
  }

  /**
   * Loads the code owner config file at the current entry's path.
   *
   * @return the loaded code owner config
   */
  public CodeOwnerConfig getCodeOwnerConfig() {
    CodeOwnerConfig.Key codeOwnerConfigKey = getCodeOwnerConfigKey();
    return codeOwnerBackend
        .getCodeOwnerConfig(codeOwnerConfigKey, revision)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format("code owner config %s not found", codeOwnerConfigKey)));
  }

  /**
   * Looks up the current revision of the branch.
   *
   * @param branchNameKey the project and branch for which the current revision should be loaded
   * @param repository the repository from which the branch revision should be loaded
   * @return the current revision of the branch
   */
  private static RevCommit getRevision(
      BranchNameKey branchNameKey, Repository repository, RevWalk revWalk) throws IOException {
    Ref ref = repository.exactRef(branchNameKey.branch());
    checkState(
        ref != null,
        "branch %s of project %s not found",
        branchNameKey.branch(),
        branchNameKey.project());

    return revWalk.parseCommit(ref.getObjectId());
  }

  /**
   * Creates a {@link TreeFilter} that matches code owner config files in the given project.
   *
   * @param codeOwnerBackend the code owner backend that is being used
   * @param project the name of the project in which code owner config files should be matched
   * @param pathGlob optional Java NIO glob that the paths of code owner config files must match
   * @return the created {@link TreeFilter}
   */
  private static TreeFilter createCodeOwnerConfigFilter(
      CodeOwnerBackend codeOwnerBackend, Project.NameKey project, @Nullable String pathGlob) {
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
        if (pathGlob != null
            && !FileSystems.getDefault()
                .getPathMatcher("glob:" + pathGlob)
                .matches(JgitPath.of(walker.getPathString()).getAsAbsolutePath())) {
          logger.atFine().log(
              "%s filtered out because it doesn't match the path glob", walker.getPathString());
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
