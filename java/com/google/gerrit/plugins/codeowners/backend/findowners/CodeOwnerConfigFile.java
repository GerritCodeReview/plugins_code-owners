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

package com.google.gerrit.plugins.codeowners.backend.findowners;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * A representation of a code owner config that is stored as an {@code OWNERS} file in a source
 * branch.
 *
 * <p>For reading code owner configs, refer to {@link Factory#load(Repository,
 * CodeOwnerConfig.Key)}.
 */
class CodeOwnerConfigFile extends VersionedMetaData {
  /** Name of the file in which the code owner config for a folder in a branch is stored. */
  @VisibleForTesting static final String FILE_NAME = "OWNERS";

  @Singleton
  static class Factory {
    private final CodeOwnerConfigParser codeOwnerConfigParser;

    @Inject
    Factory(CodeOwnerConfigParser codeOwnerConfigParser) {
      this.codeOwnerConfigParser = codeOwnerConfigParser;
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
     * @param repository the repository which the code owner config is stored
     * @param codeOwnerConfigKey the key of the code owner config
     * @return a {@link CodeOwnerConfigFile} for the code owner config with the specified key
     * @throws IOException if the repository can't be accessed for some reason
     * @throws ConfigInvalidException if the code owner config exists but can't be read due to an
     *     invalid format
     */
    CodeOwnerConfigFile load(Repository repository, CodeOwnerConfig.Key codeOwnerConfigKey)
        throws IOException, ConfigInvalidException {
      CodeOwnerConfigFile codeOwnerConfigFile =
          new CodeOwnerConfigFile(codeOwnerConfigParser, codeOwnerConfigKey);
      codeOwnerConfigFile.load(codeOwnerConfigKey.project(), repository);
      return codeOwnerConfigFile;
    }
  }

  private final CodeOwnerConfigParser codeOwnerConfigParser;
  private final CodeOwnerConfig.Key codeOwnerConfigKey;

  private boolean isLoaded = false;
  private Optional<CodeOwnerConfig> loadedCodeOwnersConfig = Optional.empty();

  private CodeOwnerConfigFile(
      CodeOwnerConfigParser codeOwnerConfigParser, CodeOwnerConfig.Key codeOwnerConfigKey) {
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

  @Override
  protected String getRefName() {
    return codeOwnerConfigKey.branch().branch();
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    if (revision != null) {
      loadFileIfItExists(
          codeOwnerConfigKey.filePathForJgit(FILE_NAME),
          codeOwnerConfigFileContent ->
              loadedCodeOwnersConfig =
                  Optional.of(
                      codeOwnerConfigParser.parse(codeOwnerConfigKey, codeOwnerConfigFileContent)));
    }

    isLoaded = true;
  }

  /**
   * Loads the file with the given path and invokes the given consumer with the file content.
   *
   * <p>No-op if a file with the given path doesn't exist.
   *
   * <p>This method allows us to differentiate between a non-existing file ({@code
   * fileContentConsumer} is not invoked) and an empty file ({@code fileContentConsumer} is invoked
   * with empty content).
   *
   * @param filePath the path of the file that should be loaded
   * @param fileContentConsumer the consumer for the file content Os
   */
  private void loadFileIfItExists(String filePath, Consumer<String> fileContentConsumer)
      throws IOException {
    try (TreeWalk tw = TreeWalk.forPath(rw.getObjectReader(), filePath, revision.getTree())) {
      if (tw != null) {
        fileContentConsumer.accept(readUTF8(filePath));
      }
    }
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    checkLoaded();

    // TODO(ekempin): Implement this method
    throw new NotImplementedException();
  }

  private void checkLoaded() {
    checkState(isLoaded, "Code owner config %s not loaded yet", codeOwnerConfigKey);
  }
}
