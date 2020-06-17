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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * A representation of a code owner config that is stored as an {@code OWNERS} file in a source
 * branch.
 *
 * <p>For reading code owner configs or creating/updating them, refer to {@link Factory#load(String,
 * CodeOwnerConfigParser, CodeOwnerConfig.Key)}.
 *
 * <p><strong>Note:</strong> Any modification (code owner config creation or update) only becomes
 * permanent (and hence written to repository) if {@link
 * #commit(com.google.gerrit.server.git.meta.MetaDataUpdate)} is called.
 */
@VisibleForTesting
public class CodeOwnerConfigFile extends VersionedMetaData {
  @Singleton
  public static class Factory {
    private final GitRepositoryManager repoManager;

    @Inject
    Factory(GitRepositoryManager repoManager) {
      this.repoManager = repoManager;
    }

    /**
     * Creates a {@link CodeOwnerConfigFile} for a code owner config.
     *
     * <p>Same as {@link #load(String, CodeOwnerConfigParser, Repository, CodeOwnerConfig.Key)}, but
     * takes care to open/close the repository.
     *
     * @param fileName name of the code owner configuration files
     * @param codeOwnerConfigParser the parser that should be used to parse code owner config files
     * @param codeOwnerConfigKey the key of the code owner config
     * @return a {@link CodeOwnerConfigFile} for the code owner config with the specified key
     * @throws IOException if the repository can't be accessed for some reason
     * @throws ConfigInvalidException if the code owner config exists but can't be read due to an
     *     invalid format
     */
    public CodeOwnerConfigFile load(
        String fileName,
        CodeOwnerConfigParser codeOwnerConfigParser,
        CodeOwnerConfig.Key codeOwnerConfigKey)
        throws IOException, ConfigInvalidException {
      try (Repository repository = repoManager.openRepository(codeOwnerConfigKey.project())) {
        return load(fileName, codeOwnerConfigParser, repository, codeOwnerConfigKey);
      }
    }

    /**
     * Creates a {@link CodeOwnerConfigFile} for a code owner config.
     *
     * <p>The code owner config is automatically loaded within this method and can be accessed via
     * {@link #getLoadedCodeOwnerConfig()}.
     *
     * <p>It's safe to call this method for non-existing code owner configs. In that case, {@link
     * #getLoadedCodeOwnerConfig()} won't return any code owner config. Thus, the existence of a
     * code owner config can be easily tested.
     *
     * <p>The code owner config represented by the returned {@link CodeOwnerConfigFile} can be
     * created/updated by setting an {@link CodeOwnerConfigUpdate} via {@link
     * #setCodeOwnerConfigUpdate(CodeOwnerConfigUpdate)} and committing the {@link
     * CodeOwnerConfigUpdate} via {@link #commit(com.google.gerrit.server.git.meta.MetaDataUpdate)}.
     *
     * @param fileName name of the code owner configuration files
     * @param codeOwnerConfigParser the parser that should be used to parse code owner config files
     * @param repository the repository in which the code owner config is stored
     * @param codeOwnerConfigKey the key of the code owner config
     * @return a {@link CodeOwnerConfigFile} for the code owner config with the specified key
     * @throws IOException if the repository can't be accessed for some reason
     * @throws ConfigInvalidException if the code owner config exists but can't be read due to an
     *     invalid format
     */
    public CodeOwnerConfigFile load(
        String fileName,
        CodeOwnerConfigParser codeOwnerConfigParser,
        Repository repository,
        CodeOwnerConfig.Key codeOwnerConfigKey)
        throws IOException, ConfigInvalidException {
      CodeOwnerConfigFile codeOwnerConfigFile =
          new CodeOwnerConfigFile(fileName, codeOwnerConfigParser, codeOwnerConfigKey);
      codeOwnerConfigFile.load(codeOwnerConfigKey.project(), repository);
      return codeOwnerConfigFile;
    }
  }

  private final String fileName;
  private final CodeOwnerConfigParser codeOwnerConfigParser;
  private final CodeOwnerConfig.Key codeOwnerConfigKey;

  private boolean isLoaded = false;
  private Optional<CodeOwnerConfig> loadedCodeOwnersConfig = Optional.empty();
  private Optional<CodeOwnerConfigUpdate> codeOwnerConfigUpdate = Optional.empty();

  private CodeOwnerConfigFile(
      String fileName,
      CodeOwnerConfigParser codeOwnerConfigParser,
      CodeOwnerConfig.Key codeOwnerConfigKey) {
    this.fileName = fileName;
    this.codeOwnerConfigParser = codeOwnerConfigParser;
    this.codeOwnerConfigKey = codeOwnerConfigKey;
  }

  /**
   * Returns the loaded code owner config if it exists.
   *
   * @return the loaded code owner config, or {@link Optional#empty()} if the code owner config
   *     doesn't exist
   */
  public Optional<CodeOwnerConfig> getLoadedCodeOwnerConfig() {
    checkLoaded();

    return loadedCodeOwnersConfig;
  }

  /**
   * Specifies how the current code owner config should be updated.
   *
   * <p>If the code owner config is newly created, the {@link CodeOwnerConfigUpdate} can be used to
   * specify optional properties.
   *
   * <p>If the update leads to an empty code owner config, the code owner config file is deleted.
   *
   * <p><strong>Note:</strong> This method doesn't perform the update. It only contains the
   * instructions for the update. To apply the update for real, call {@link
   * #commit(com.google.gerrit.server.git.meta.MetaDataUpdate)} on this {@link CodeOwnerConfigFile}.
   *
   * @param codeOwnerConfigUpdate an {@code CodeOwnerConfigUpdate} outlining the modifications which
   *     should be applied
   * @return this {@code CodeOwnerConfigFile} instance to allow chaining calls
   */
  public CodeOwnerConfigFile setCodeOwnerConfigUpdate(CodeOwnerConfigUpdate codeOwnerConfigUpdate) {
    this.codeOwnerConfigUpdate = Optional.of(codeOwnerConfigUpdate);
    return this;
  }

  @Override
  protected String getRefName() {
    return codeOwnerConfigKey.branch().branch();
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    if (revision != null) {
      Optional<String> codeOwnerConfigFileContent =
          getFileIfItExists(codeOwnerConfigKey.filePathForJgit(fileName));
      if (codeOwnerConfigFileContent.isPresent()) {
        loadedCodeOwnersConfig =
            Optional.of(
                codeOwnerConfigParser.parse(codeOwnerConfigKey, codeOwnerConfigFileContent.get()));
      }
    }

    isLoaded = true;
  }

  /**
   * Loads the file with the given path and returns the file content if the file exists.
   *
   * @param filePath the path of the file that should be loaded
   * @return the content of the file if it exists, otherwise {@link Optional#empty()}.
   */
  private Optional<String> getFileIfItExists(String filePath) throws IOException {
    try (TreeWalk tw = TreeWalk.forPath(rw.getObjectReader(), filePath, revision.getTree())) {
      if (tw != null) {
        return Optional.of(readUTF8(filePath));
      }
    }
    return Optional.empty();
  }

  @Override
  public RevCommit commit(MetaDataUpdate update) throws IOException {
    // Reject the creation of a code owner config if the branch doesn't exist.
    checkState(
        update.getRepository().exactRef(getRefName()) != null,
        "branch %s does not exist",
        getRefName());

    return super.commit(update);
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    checkLoaded();

    if (!codeOwnerConfigUpdate.isPresent()) {
      // Code owner config was neither created nor changed. -> A new commit isn't necessary.
      return false;
    }

    // Update the code owner config.
    CodeOwnerConfig originalCodeOwnerConfig =
        loadedCodeOwnersConfig.orElse(CodeOwnerConfig.builder(codeOwnerConfigKey).build());
    CodeOwnerConfig updatedCodeOwnerConfig =
        updateCodeOwnerConfig(originalCodeOwnerConfig, codeOwnerConfigUpdate.get());

    // Do not create a new commit if the code owner config didn't change.
    if (updatedCodeOwnerConfig.equals(originalCodeOwnerConfig)) {
      return false;
    }

    // Compute the new content of the code owner config file.
    String codeOwnerConfigFileContent =
        codeOwnerConfigParser.formatAsString(updatedCodeOwnerConfig);

    // Save the new code owner config.
    saveUTF8(codeOwnerConfigKey.filePathForJgit(fileName), codeOwnerConfigFileContent);

    // If the file content is empty, the update led to a deletion of the code owner config file.
    boolean isDeleted = codeOwnerConfigFileContent.isEmpty();

    // Set a commit message if none was set yet.
    if (Strings.isNullOrEmpty(commit.getMessage())) {
      commit.setMessage(
          String.format(
              "%s code owner config",
              loadedCodeOwnersConfig.isPresent() ? (isDeleted ? "Delete" : "Update") : "Create"));
    }

    loadedCodeOwnersConfig = isDeleted ? Optional.empty() : Optional.of(updatedCodeOwnerConfig);
    codeOwnerConfigUpdate = Optional.empty();

    return true;
  }

  private CodeOwnerConfig updateCodeOwnerConfig(
      CodeOwnerConfig codeOwnerConfig, CodeOwnerConfigUpdate codeOwnerConfigUpdate) {
    CodeOwnerConfig.Builder codeOwnerConfigBuilder = codeOwnerConfig.toBuilder();
    codeOwnerConfigUpdate
        .ignoreParentCodeOwners()
        .ifPresent(codeOwnerConfigBuilder::setIgnoreParentCodeOwners);
    codeOwnerConfigBuilder.setCodeOwners(
        ImmutableSet.copyOf(
            codeOwnerConfigUpdate.codeOwnerModification().apply(codeOwnerConfig.codeOwners())));
    return codeOwnerConfigBuilder.build();
  }

  private void checkLoaded() {
    checkState(isLoaded, "Code owner config %s not loaded yet", codeOwnerConfigKey);
  }
}
