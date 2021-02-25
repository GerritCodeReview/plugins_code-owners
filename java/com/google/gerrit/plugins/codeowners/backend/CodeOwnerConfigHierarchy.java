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
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Class to visit the code owner configs in a given branch that apply for a given path by following
 * the path hierarchy from the given path up to the root folder and the default code owner config in
 * {@code refs/meta/config}.
 *
 * <p>The default code owner config in {@code refs/meta/config} is the parent of the code owner
 * config in the root folder of the branch. The same as any other parent it can be ignored (e.g. by
 * using {@code set noparent} in the root code owner config if the {@code find-owners} backend is
 * used).
 */
@Singleton
public class CodeOwnerConfigHierarchy {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepositoryManager repoManager;
  private final PathCodeOwners.Factory pathCodeOwnersFactory;

  @Inject
  CodeOwnerConfigHierarchy(
      GitRepositoryManager repoManager, PathCodeOwners.Factory pathCodeOwnersFactory) {
    this.repoManager = repoManager;
    this.pathCodeOwnersFactory = pathCodeOwnersFactory;
  }

  /**
   * Visits the code owner configs in the given branch that apply for the given path by following
   * the path hierarchy from the given path up to the root folder.
   *
   * @param branchNameKey project and branch from which the code owner configs should be visited
   * @param revision the branch revision from which the code owner configs should be loaded
   * @param absolutePath the path for which the code owner configs should be visited; the path must
   *     be absolute; can be the path of a file or folder; the path may or may not exist
   * @param codeOwnerConfigVisitor visitor that should be invoked for the applying code owner
   *     configs
   */
  public void visit(
      BranchNameKey branchNameKey,
      ObjectId revision,
      Path absolutePath,
      CodeOwnerConfigVisitor codeOwnerConfigVisitor) {
    visit(
        branchNameKey,
        revision,
        absolutePath,
        codeOwnerConfigVisitor,
        /* parentCodeOwnersIgnoredCallback= */ codeOwnerConfigKey -> {});
  }

  /**
   * Visits the code owner configs in the given branch that apply for the given path by following
   * the path hierarchy from the given path up to the root folder.
   *
   * @param branchNameKey project and branch from which the code owner configs should be visited
   * @param revision the branch revision from which the code owner configs should be loaded
   * @param absolutePath the path for which the code owner configs should be visited; the path must
   *     be absolute; can be the path of a file or folder; the path may or may not exist
   * @param codeOwnerConfigVisitor visitor that should be invoked for the applying code owner
   *     configs
   * @param parentCodeOwnersIgnoredCallback callback that is invoked for the first visited code
   *     owner config that ignores parent code owners
   */
  public void visit(
      BranchNameKey branchNameKey,
      ObjectId revision,
      Path absolutePath,
      CodeOwnerConfigVisitor codeOwnerConfigVisitor,
      Consumer<CodeOwnerConfig.Key> parentCodeOwnersIgnoredCallback) {
    requireNonNull(codeOwnerConfigVisitor, "codeOwnerConfigVisitor");
    PathCodeOwnersVisitor pathCodeOwnersVisitor =
        pathCodeOwners -> codeOwnerConfigVisitor.visit(pathCodeOwners.getCodeOwnerConfig());
    visit(
        branchNameKey,
        revision,
        absolutePath,
        pathCodeOwnersVisitor,
        parentCodeOwnersIgnoredCallback);
  }

  /**
   * Visits the path code owners in the given branch that apply for the given path by following the
   * path hierarchy from the given path up to the root folder.
   *
   * @param branchNameKey project and branch from which the code owner configs should be visited
   * @param revision the branch revision from which the code owner configs should be loaded
   * @param absolutePath the path for which the code owner configs should be visited; the path must
   *     be absolute; can be the path of a file or folder; the path may or may not exist
   * @param pathCodeOwnersVisitor visitor that should be invoked for the applying path code owners
   * @param parentCodeOwnersIgnoredCallback callback that is invoked for the first visited code
   *     owner config that ignores parent code owners
   */
  public void visit(
      BranchNameKey branchNameKey,
      ObjectId revision,
      Path absolutePath,
      PathCodeOwnersVisitor pathCodeOwnersVisitor,
      Consumer<CodeOwnerConfig.Key> parentCodeOwnersIgnoredCallback) {
    requireNonNull(branchNameKey, "branch");
    requireNonNull(revision, "revision");
    requireNonNull(absolutePath, "absolutePath");
    requireNonNull(pathCodeOwnersVisitor, "pathCodeOwnersVisitor");
    requireNonNull(parentCodeOwnersIgnoredCallback, "parentCodeOwnersIgnoredCallback");
    checkState(absolutePath.isAbsolute(), "path %s must be absolute", absolutePath);

    logger.atFine().log(
        "visiting code owner configs for '%s' in branch '%s' in project '%s' (revision = '%s')",
        absolutePath, branchNameKey.shortName(), branchNameKey.project(), revision.name());

    // Next path in which we look for a code owner configuration. We start at the given path and
    // then go up the parent hierarchy.
    Path ownerConfigFolder = absolutePath;

    // Iterate over the parent code owner configurations.
    while (ownerConfigFolder != null) {
      // Read code owner config and invoke the codeOwnerConfigVisitor if the code owner config
      // exists.
      logger.atFine().log("inspecting code owner config for %s", ownerConfigFolder);
      CodeOwnerConfig.Key codeOwnerConfigKey =
          CodeOwnerConfig.Key.create(branchNameKey, ownerConfigFolder);
      Optional<PathCodeOwners> pathCodeOwners =
          pathCodeOwnersFactory.create(codeOwnerConfigKey, revision, absolutePath);
      if (pathCodeOwners.isPresent()) {
        logger.atFine().log("visit code owner config for %s", ownerConfigFolder);
        boolean visitFurtherCodeOwnerConfigs = pathCodeOwnersVisitor.visit(pathCodeOwners.get());
        boolean ignoreParentCodeOwners =
            pathCodeOwners.get().resolveCodeOwnerConfig().get().ignoreParentCodeOwners();
        if (ignoreParentCodeOwners) {
          parentCodeOwnersIgnoredCallback.accept(codeOwnerConfigKey);
        }
        logger.atFine().log(
            "visitFurtherCodeOwnerConfigs = %s, ignoreParentCodeOwners = %s",
            visitFurtherCodeOwnerConfigs, ignoreParentCodeOwners);
        if (!visitFurtherCodeOwnerConfigs || ignoreParentCodeOwners) {
          // If no further code owner configs should be visited or if all parent code owner configs
          // are ignored, we are done.
          // No need to check further parent code owner configs (including the default code owner
          // config in refs/meta/config which is the parent of the root code owner config), hence we
          // can return here.
          return;
        }
      } else {
        logger.atFine().log("no code owner config found in %s", ownerConfigFolder);
      }

      // Continue the loop with the next parent folder.
      ownerConfigFolder = ownerConfigFolder.getParent();
    }

    if (!RefNames.REFS_CONFIG.equals(branchNameKey.branch())) {
      visitCodeOwnerConfigInRefsMetaConfig(
          branchNameKey.project(), absolutePath, pathCodeOwnersVisitor);
    }
  }

  /**
   * Visits the code owner config file at the root of the {@code refs/meta/config} branch in the
   * given project.
   *
   * <p>The root code owner config file in the {@code refs/meta/config} branch defines default code
   * owners for all branches.
   *
   * <p>There is no inheritance of code owner config files from parent projects. If code owners
   * should be defined for child projects, this is possible via global code owners, but not via the
   * default code owner config file in {@code refs/meta/config}.
   *
   * @param project the project in which we want to visit the code owner config file at the root of
   *     the {@code refs/meta/config} branch
   * @param absolutePath the path for which the code owner configs should be visited; the path must
   *     be absolute; can be the path of a file or folder; the path may or may not exist
   * @param pathCodeOwnersVisitor visitor that should be invoked
   */
  private void visitCodeOwnerConfigInRefsMetaConfig(
      Project.NameKey project, Path absolutePath, PathCodeOwnersVisitor pathCodeOwnersVisitor) {
    CodeOwnerConfig.Key metaCodeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, RefNames.REFS_CONFIG, "/");
    logger.atFine().log("visiting code owner config %s", metaCodeOwnerConfigKey);
    try (Repository repository = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repository)) {
      Ref ref = repository.exactRef(RefNames.REFS_CONFIG);
      if (ref == null) {
        logger.atFine().log("%s not found", RefNames.REFS_CONFIG);
        return;
      }
      RevCommit metaRevision = rw.parseCommit(ref.getObjectId());
      Optional<PathCodeOwners> pathCodeOwners =
          pathCodeOwnersFactory.create(metaCodeOwnerConfigKey, metaRevision, absolutePath);
      if (pathCodeOwners.isPresent()) {
        logger.atFine().log("visit code owner config %s", metaCodeOwnerConfigKey);
        pathCodeOwnersVisitor.visit(pathCodeOwners.get());
      } else {
        logger.atFine().log("code owner config %s not found", metaCodeOwnerConfigKey);
      }
    } catch (IOException e) {
      throw new CodeOwnersInternalServerErrorException(
          String.format("failed to read %s", metaCodeOwnerConfigKey), e);
    }
  }
}
