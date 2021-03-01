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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
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
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

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

    CodeOwnerBackend codeOwnerBackend =
        codeOwnersPluginConfiguration
            .getProjectConfig(branchNameKey.project())
            .getBackend(branchNameKey.branch());
    logger.atFine().log(
        "updating code owner files in branch %s of project %s",
        branchNameKey.branch(), branchNameKey.project());

    try (Repository repository = repoManager.openRepository(branchNameKey.project());
        RevWalk rw = new RevWalk(repository);
        ObjectInserter oi = repository.newObjectInserter();
        CodeOwnerConfigTreeWalk treeWalk =
            new CodeOwnerConfigTreeWalk(
                codeOwnerBackend,
                branchNameKey,
                repository,
                rw,
                /** pathGlob */
                null)) {
      RevCommit revision = treeWalk.getRevision();
      DirCache newTree = DirCache.newInCore();
      DirCacheEditor editor = newTree.editor();

      boolean dirty = false;
      while (treeWalk.next()) {
        Optional<String> updatedContent =
            codeOwnerConfigFileUpdater.update(treeWalk.getFilePath(), treeWalk.getFileContent());
        if (updatedContent.isPresent()) {
          dirty = true;

          // insert blob with new file content
          ObjectId blobId = oi.insert(Constants.OBJ_BLOB, updatedContent.get().getBytes(UTF_8));

          // append edit command to set the new blob for the code owner config file
          editor.add(createEditCommand(treeWalk.getPathString(), blobId));
        }
      }

      if (!dirty) {
        return Optional.empty();
      }

      editor.finish();
      ObjectId treeId = newTree.writeTree(oi);
      ObjectId commitId = createCommit(oi, commitMessage, revision, treeId);
      updateBranch(branchNameKey.branch(), repository, revision, commitId);
      return Optional.of(rw.parseCommit(commitId));
    } catch (IOException e) {
      throw new CodeOwnersInternalServerErrorException(
          String.format(
              "Failed to scan for code owner configs in branch %s of project %s",
              branchNameKey.branch(), branchNameKey.project()),
          e);
    }
  }

  /**
   * Creates an edit command that sets the given blob for the given path
   *
   * @param jgitFilePath path of the file for which the blob should be set, as jgit path (not
   *     starting with '/')
   * @param blobId the ID of the blob that should be set for the file path
   * @return the edit command
   */
  private PathEdit createEditCommand(String jgitFilePath, ObjectId blobId) {
    return new PathEdit(jgitFilePath) {
      @Override
      public void apply(DirCacheEntry entry) {
        entry.setFileMode(FileMode.REGULAR_FILE);
        entry.setObjectId(blobId);
      }
    };
  }

  /**
   * Creates a new commit.
   *
   * @param objectInserter object inserter that should be used to insert the new commit
   * @param commitMessage the commit message that should be used for the new commit
   * @param parentCommit the commit that should be set as parent commit of the new commit
   * @param treeId the tree of the new commit
   * @return the commit ID
   */
  private ObjectId createCommit(
      ObjectInserter objectInserter, String commitMessage, ObjectId parentCommit, ObjectId treeId)
      throws IOException {
    PersonIdent serverIdent = serverIdentProvider.get();
    CommitBuilder cb = new CommitBuilder();
    cb.setParentId(parentCommit);
    cb.setTreeId(treeId);
    cb.setCommitter(serverIdent);
    cb.setAuthor(
        identifiedUser.get().newCommitterIdent(serverIdent.getWhen(), serverIdent.getTimeZone()));
    cb.setMessage(commitMessage);
    ObjectId id = objectInserter.insert(cb);
    objectInserter.flush();
    return id;
  }

  /**
   * Update the given branch.
   *
   * @param branchName the name of the branch that should be updated
   * @param repository the repository in which the branch should be updated
   * @param oldObjectId the expected old object ID of the branch
   * @param newObjectId the new object ID that should be set for the branch
   */
  private void updateBranch(
      String branchName, Repository repository, ObjectId oldObjectId, ObjectId newObjectId)
      throws IOException {
    RefUpdate ru = repository.updateRef(branchName);
    ru.setExpectedOldObjectId(oldObjectId);
    ru.setNewObjectId(newObjectId);
    ru.update();
    RefUpdateUtil.checkResult(ru);
  }
}
