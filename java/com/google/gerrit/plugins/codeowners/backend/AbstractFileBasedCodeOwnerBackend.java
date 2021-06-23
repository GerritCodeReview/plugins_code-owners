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

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginProjectConfigSnapshot;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.update.RetryHelper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Base class for all {@link CodeOwnerBackend}'s that store {@link CodeOwnerConfig}'s in files
 * inside the corresponding folders in the source branch.
 *
 * <p>E.g. the code owner configuration for folder {@code /foo/bar} folder of the {@code master}
 * branch is stored as a file in the {@code /foo/bar} folder of the {@code master}
 */
public abstract class AbstractFileBasedCodeOwnerBackend implements CodeOwnerBackend {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final GitRepositoryManager repoManager;
  private final PersonIdent serverIdent;
  private final MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory;
  private final RetryHelper retryHelper;
  private final String defaultFileName;
  private final CodeOwnerConfigFile.Factory codeOwnerConfigFileFactory;
  private final CodeOwnerConfigParser codeOwnerConfigParser;

  protected AbstractFileBasedCodeOwnerBackend(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      GitRepositoryManager repoManager,
      @GerritPersonIdent PersonIdent serverIdent,
      MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory,
      RetryHelper retryHelper,
      String defaultFileName,
      CodeOwnerConfigFile.Factory codeOwnerConfigFileFactory,
      CodeOwnerConfigParser codeOwnerConfigParser) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.repoManager = repoManager;
    this.serverIdent = serverIdent;
    this.metaDataUpdateInternalFactory = metaDataUpdateInternalFactory;
    this.retryHelper = retryHelper;
    this.defaultFileName = defaultFileName;
    this.codeOwnerConfigFileFactory = codeOwnerConfigFileFactory;
    this.codeOwnerConfigParser = codeOwnerConfigParser;
  }

  @Override
  public final Optional<CodeOwnerConfig> getCodeOwnerConfig(
      CodeOwnerConfig.Key codeOwnerConfigKey, @Nullable ObjectId revision) {
    String fileName =
        codeOwnerConfigKey.fileName().orElse(getFileName(codeOwnerConfigKey.project()));

    if (!isCodeOwnerConfigFile(codeOwnerConfigKey.project(), fileName)) {
      // The file name can mismatch if we resolve imported code owner configs. When code owner
      // configs are imported the user specifies the full path of the code owner config (including
      // the file name) in the importing code owner config. If the user specifies a file name that
      // is not supported, this is not a server error, but a user error. Invalid imports are
      // silently ignored, but users can detect them by running validation checks on the code owner
      // configs. Although this is a normal situation, still log a warning so that we can see in the
      // server logs when this happens.
      logger.atWarning().log(
          "Cannot load code owner config %s: unsupported file name", codeOwnerConfigKey);
      return Optional.empty();
    }

    return loadCodeOwnerConfigFile(codeOwnerConfigKey, fileName, revision)
        .getLoadedCodeOwnerConfig();
  }

  private CodeOwnerConfigFile loadCodeOwnerConfigFile(
      CodeOwnerConfig.Key codeOwnerConfigKey, String fileName, @Nullable ObjectId revision) {
    try (Repository repository = repoManager.openRepository(codeOwnerConfigKey.project())) {
      if (revision == null) {
        return codeOwnerConfigFileFactory.loadCurrent(
            fileName, codeOwnerConfigParser, repository, codeOwnerConfigKey);
      }

      try (RevWalk revWalk = new RevWalk(repository)) {
        return codeOwnerConfigFileFactory.load(
            fileName, codeOwnerConfigParser, revWalk, revision, codeOwnerConfigKey);
      }
    } catch (IOException e) {
      throw new CodeOwnersInternalServerErrorException(
          String.format("failed to load code owner config %s", codeOwnerConfigKey), e);
    } catch (ConfigInvalidException e) {
      throw new CodeOwnersInternalServerErrorException(
          String.format(
              "invalid code owner config file %s (project = %s, branch = %s)",
              codeOwnerConfigKey.filePath(defaultFileName),
              codeOwnerConfigKey.project(),
              codeOwnerConfigKey.branchNameKey().branch()),
          e);
    }
  }

  @Override
  public Path getFilePath(CodeOwnerConfig.Key codeOwnerConfigKey) {
    return codeOwnerConfigKey.filePath(defaultFileName);
  }

  @Override
  public boolean isCodeOwnerConfigFile(Project.NameKey project, String fileName) {
    requireNonNull(project, "project");
    requireNonNull(fileName, "fileName");

    if (getFileName(project).equals(fileName)) {
      return true;
    }

    return isCodeOwnerConfigFileWithExtension(project, fileName);
  }

  /**
   * Checks whether the given file name is code owner config file with an extension in the name.
   *
   * <p>Name extensions can appear as post- or pre-fix:
   *
   * <ul>
   *   <li>Post-fix: E.g. {@code OWNERS_<extension>} or {@code OWNERS_<extension>.<file-extension>}
   *   <li>Pre-fix: E.g. {@code <extension>_OWNERS} or {@code <extension>_OWNERS.<file-extension>}
   * </ul>
   *
   * @param project the project in which the code owner config files are stored
   * @param fileName the name of the file for which it should be checked whether is a code owner
   *     config file with extension
   * @return whether the given file name is code owner config file with an extension in the name
   */
  private boolean isCodeOwnerConfigFileWithExtension(Project.NameKey project, String fileName) {
    CodeOwnersPluginProjectConfigSnapshot codeOwnersPluginProjectConfigSnapshot =
        codeOwnersPluginConfiguration.getProjectConfig(project);
    String quotedDefaultFileName = Pattern.quote(defaultFileName);
    String quotedFileExtension =
        Pattern.quote(
            codeOwnersPluginProjectConfigSnapshot
                .getFileExtension()
                .map(ext -> "." + ext)
                .orElse(""));
    String nameExtension = "(\\w)+";

    return Pattern.compile(
                "^" + quotedDefaultFileName + "_" + nameExtension + quotedFileExtension + "$")
            .matcher(fileName)
            .matches()
        || Pattern.compile(
                "^" + nameExtension + "_" + quotedDefaultFileName + quotedFileExtension + "$")
            .matcher(fileName)
            .matches()
        || (codeOwnersPluginProjectConfigSnapshot.enableCodeOwnerConfigFilesWithFileExtensions()
            && Pattern.compile(
                    "^" + quotedDefaultFileName + Pattern.quote(".") + nameExtension + "$")
                .matcher(fileName)
                .matches());
  }

  private String getFileName(Project.NameKey project) {
    return defaultFileName
        + codeOwnersPluginConfiguration
            .getProjectConfig(project)
            .getFileExtension()
            .map(ext -> "." + ext)
            .orElse("");
  }

  @Override
  public final Optional<CodeOwnerConfig> upsertCodeOwnerConfig(
      CodeOwnerConfig.Key codeOwnerConfigKey,
      CodeOwnerConfigUpdate codeOwnerConfigUpdate,
      @Nullable IdentifiedUser currentUser) {
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
      throw new CodeOwnersInternalServerErrorException(
          String.format("failed to upsert code owner config %s", codeOwnerConfigKey), e);
    }
  }

  @Override
  public final Optional<PathExpressionMatcher> getPathExpressionMatcher(
      BranchNameKey branchNameKey) {
    Optional<PathExpressions> pathExpressions =
        codeOwnersPluginConfiguration
            .getProjectConfig(branchNameKey.project())
            .getPathExpressions(branchNameKey.branch());
    boolean hasConfiguredPathExpressions = pathExpressions.isPresent();
    if (!hasConfiguredPathExpressions) {
      pathExpressions = getDefaultPathExpressions();
    }
    logger.atFine().log(
        "using %s path expression syntax %s for project/branch %s",
        (hasConfiguredPathExpressions ? "configured" : "default"),
        pathExpressions.map(PathExpressions::name).orElse("<none>"),
        branchNameKey);
    return pathExpressions.map(PathExpressions::getMatcher);
  }

  @VisibleForTesting
  public abstract Optional<PathExpressions> getDefaultPathExpressions();

  private Optional<CodeOwnerConfig> upsertCodeOwnerConfigInSourceBranch(
      @Nullable IdentifiedUser currentUser,
      CodeOwnerConfig.Key codeOwnerConfigKey,
      CodeOwnerConfigUpdate codeOwnerConfigUpdate) {
    try (Repository repository = repoManager.openRepository(codeOwnerConfigKey.project())) {
      CodeOwnerConfigFile codeOwnerConfigFile =
          codeOwnerConfigFileFactory
              .loadCurrent(
                  getFileName(codeOwnerConfigKey.project()),
                  codeOwnerConfigParser,
                  repository,
                  codeOwnerConfigKey)
              .setCodeOwnerConfigUpdate(codeOwnerConfigUpdate);

      try (MetaDataUpdate metaDataUpdate =
          createMetaDataUpdate(codeOwnerConfigKey.project(), repository, currentUser)) {
        codeOwnerConfigFile.commit(metaDataUpdate);
      }

      return codeOwnerConfigFile.getLoadedCodeOwnerConfig();
    } catch (IOException | ConfigInvalidException e) {
      throw new CodeOwnersInternalServerErrorException(
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
      throw new CodeOwnersInternalServerErrorException("Failed to create MetaDataUpdate", t);
    }
  }
}
