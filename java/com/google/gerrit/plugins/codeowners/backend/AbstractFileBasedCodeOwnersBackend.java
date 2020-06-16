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

import com.google.common.base.Throwables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig.Key;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.update.RetryHelper;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/**
 * Base class for all {@link CodeOwnersBackend}'s that store {@link CodeOwnerConfig}'s in files
 * inside the corresponding folders in the source branch.
 *
 * <p>E.g. the code owner configuration for folder {@code /foo/bar} folder of the {@code master}
 * branch is stored as a file in the {@code /foo/bar} folder of the {@code master}
 */
public abstract class AbstractFileBasedCodeOwnersBackend implements CodeOwnersBackend {
  private final CodeOwnerConfigFile.Factory codeOwnerConfigFileFactory;
  private final GitRepositoryManager repoManager;
  private final PersonIdent serverIdent;
  private final MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory;
  private final RetryHelper retryHelper;
  private final String fileName;
  private final CodeOwnerConfigParser codeOwnerConfigParser;

  protected AbstractFileBasedCodeOwnersBackend(
      CodeOwnerConfigFile.Factory codeOwnerConfigFileFactory,
      GitRepositoryManager repoManager,
      @GerritPersonIdent PersonIdent serverIdent,
      MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory,
      RetryHelper retryHelper,
      String fileName,
      CodeOwnerConfigParser codeOwnerConfigParser) {
    this.codeOwnerConfigFileFactory = codeOwnerConfigFileFactory;
    this.repoManager = repoManager;
    this.serverIdent = serverIdent;
    this.metaDataUpdateInternalFactory = metaDataUpdateInternalFactory;
    this.retryHelper = retryHelper;
    this.fileName = fileName;
    this.codeOwnerConfigParser = codeOwnerConfigParser;
  }

  @Override
  public final Optional<CodeOwnerConfig> getCodeOwnerConfig(Key codeOwnerConfigKey) {
    try {
      return codeOwnerConfigFileFactory
          .load(fileName, codeOwnerConfigParser, codeOwnerConfigKey)
          .getLoadedCodeOwnerConfig();
    } catch (IOException | ConfigInvalidException e) {
      throw new StorageException(
          String.format("failed to load code owner config %s", codeOwnerConfigKey), e);
    }
  }

  @Override
  public final Optional<CodeOwnerConfig> upsertCodeOwnerConfig(
      CodeOwnerConfig.Key codeOwnerConfigKey,
      CodeOwnerConfigUpdate codeOwnerConfigUpdate,
      @Nullable IdentifiedUser currentUser)
      throws StorageException {
    try {
      return retryHelper
          .pluginUpdate(
              "upsertCodeOwnerConfigInSourceBranch",
              () ->
                  upsertCodeOwnerConfigInSourceBranch(
                      currentUser, codeOwnerConfigKey, codeOwnerConfigUpdate))
          .call();
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      throw new StorageException(e);
    }
  }

  private Optional<CodeOwnerConfig> upsertCodeOwnerConfigInSourceBranch(
      @Nullable IdentifiedUser currentUser,
      CodeOwnerConfig.Key codeOwnerConfigKey,
      CodeOwnerConfigUpdate codeOwnerConfigUpdate) {
    try (Repository repository = repoManager.openRepository(codeOwnerConfigKey.project())) {
      CodeOwnerConfigFile codeOwnerConfigFile =
          codeOwnerConfigFileFactory
              .load(fileName, codeOwnerConfigParser, repository, codeOwnerConfigKey)
              .setCodeOwnerConfigUpdate(codeOwnerConfigUpdate);

      try (MetaDataUpdate metaDataUpdate =
          createMetaDataUpdate(codeOwnerConfigKey.project(), repository, currentUser)) {
        codeOwnerConfigFile.commit(metaDataUpdate);
      }

      return codeOwnerConfigFile.getLoadedCodeOwnerConfig();
    } catch (IOException | ConfigInvalidException e) {
      throw new StorageException(
          String.format("failed to upsert code owner config %s", codeOwnerConfigKey), e);
    }
  }

  private MetaDataUpdate createMetaDataUpdate(
      Project.NameKey project, Repository repository, @Nullable IdentifiedUser currentUser) {
    MetaDataUpdate metaDataUpdate = metaDataUpdateInternalFactory.create(project, repository, null);
    try {
      metaDataUpdate.getCommitBuilder().setCommitter(serverIdent);
      if (currentUser != null) {
        // Using MetaDataUpdate#setAuthor copies the timezone and timestamp from the committer
        // identity, so that it's ensured that the author and committer identities have the same
        // timezone and timestamp.
        metaDataUpdate.setAuthor(currentUser);
      } else {
        // In this case the author identity is the same as the committer identity, hence it already
        // has the correct timezone and timestamp and we can set it on the commit builder directly.
        metaDataUpdate.getCommitBuilder().setAuthor(serverIdent);
      }
      return metaDataUpdate;
    } catch (Throwable t) {
      metaDataUpdate.close();
      Throwables.throwIfUnchecked(t);
      throw new StorageException("Failed to create MetaDataUpdate", t);
    }
  }
}
