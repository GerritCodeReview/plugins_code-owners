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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.plugins.codeowners.JgitPath;
import com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Class to scan a branch for code owner config files and update them.
 *
 * <p>Doesn't parse the code owner config files but provides the raw content to the callback.
 *
 * <p>All updates to the code owner config files are done atomically with a single commit.
 */
@Singleton
public class CodeOwnerConfigFileUpdateScanner {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepositoryManager repoManager;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final Provider<PersonIdent> serverIdentProvider;
  private final Provider<IdentifiedUser> identifiedUser;

  @Inject
  CodeOwnerConfigFileUpdateScanner(
      GitRepositoryManager repoManager,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      @GerritPersonIdent Provider<PersonIdent> serverIdentProvider,
      Provider<IdentifiedUser> identifiedUser) {
    this.repoManager = repoManager;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.serverIdentProvider = serverIdentProvider;
    this.identifiedUser = identifiedUser;
  }

  /**
   * Visits and updates all code owner config files in the given project and branch.
   *
   * <p>All updates are done in a single commit. If none of the code owner config files is updated,
   * no new commit is created.
   *
   * @param branchNameKey the project and branch for which the code owner config files should be
   *     updated
   * @param commitMessage commit message for the new commit if an update is performed
   * @param codeOwnerConfigFileUpdater the callback that is invoked for each code owner config file
   * @return the commit that renamed the email if any update was performed
   */
  public Optional<RevCommit> update(
      BranchNameKey branchNameKey,
      String commitMessage,
      CodeOwnerConfigFileUpdater codeOwnerConfigFileUpdater) {
    requireNonNull(branchNameKey, "branchNameKey");
    requireNonNull(commitMessage, "commitMessage");
    requireNonNull(codeOwnerConfigFileUpdater, "codeOwnerConfigFileUpdater");

    CodeOwnerBackend codeOwnerBackend = codeOwnersPluginConfiguration.getBackend(branchNameKey);
    logger.atFine().log(
        "updating code owner files in branch %s of project %s",
        branchNameKey.branch(), branchNameKey.project());

    try (Repository repository = repoManager.openRepository(branchNameKey.project());
        RevWalk rw = new RevWalk(repository);
        ObjectReader reader = repository.newObjectReader();
        ObjectInserter oi = repository.newObjectInserter();
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
      treeWalk.setFilter(
          CodeOwnerConfigScanner.createCodeOwnerConfigFilter(
              codeOwnerBackend, branchNameKey.project(), null));

      DirCache newTree = DirCache.newInCore();
      DirCacheEditor editor = newTree.editor();

      boolean dirty = false;
      while (treeWalk.next()) {
        ObjectLoader obj = reader.open(treeWalk.getObjectId(0), Constants.OBJ_BLOB);
        byte[] raw = obj.getCachedBytes(Integer.MAX_VALUE);
        String content = raw.length != 0 ? RawParseUtils.decode(raw) : "";
        Optional<String> updatedContent =
            codeOwnerConfigFileUpdater.update(
                JgitPath.of(treeWalk.getPathString()).getAsAbsolutePath(), content);
        if (updatedContent.isPresent()) {
          dirty = true;
          ObjectId blobId = oi.insert(Constants.OBJ_BLOB, updatedContent.get().getBytes(UTF_8));
          editor.add(
              new PathEdit(treeWalk.getPathString()) {
                @Override
                public void apply(DirCacheEntry entry) {
                  entry.setFileMode(FileMode.REGULAR_FILE);
                  entry.setObjectId(blobId);
                }
              });
        }
      }

      if (!dirty) {
        return Optional.empty();
      }

      editor.finish();
      ObjectId treeId = newTree.writeTree(oi);

      PersonIdent serverIdent = serverIdentProvider.get();
      CommitBuilder cb = new CommitBuilder();
      cb.setParentId(revision);
      cb.setTreeId(treeId);
      cb.setCommitter(serverIdent);
      cb.setAuthor(
          identifiedUser.get().newCommitterIdent(serverIdent.getWhen(), serverIdent.getTimeZone()));
      cb.setMessage(commitMessage);
      ObjectId id = oi.insert(cb);
      oi.flush();

      RefUpdate ru = repository.updateRef(branchNameKey.branch());
      ru.setExpectedOldObjectId(revision);
      ru.setNewObjectId(id);
      ru.update();
      RefUpdateUtil.checkResult(ru);

      return Optional.of(rw.parseCommit(id));
    } catch (IOException e) {
      throw new StorageException(
          String.format(
              "Failed to scan for code owner configs in branch %s of project %s",
              branchNameKey.branch(), branchNameKey.project()),
          e);
    }
  }
}
