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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.server.IdentifiedUser;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Interface for code owner backends.
 *
 * <p>Allows to implement different syntaxes and storages for code owner configurations.
 *
 * <p>New code owner backend implementations must be added to {@link CodeOwnerBackendId}.
 */
public interface CodeOwnerBackend {
  /**
   * Checks whether the given file name is a code owner config file.
   *
   * @param project the project in which the code owner config files are stored
   * @param fileName the name of the file for which it should be checked whether is a code owner
   *     config file
   * @return {@code true} if the given file name is a code owner config file, otherwise {@code
   *     false}
   */
  public boolean isCodeOwnerConfigFile(Project.NameKey project, String fileName);

  /**
   * Gets the code owner config for the given key if it exists.
   *
   * @param codeOwnerConfigKey the code owner config key for which the code owner config should be
   *     returned
   * @param revision the branch revision from which the code owner config should be loaded, if
   *     {@code null} the code owner config is loaded from the current revision of the branch
   * @return code owner config for the given key if it exists, otherwise {@link Optional#empty()}
   */
  Optional<CodeOwnerConfig> getCodeOwnerConfig(
      CodeOwnerConfig.Key codeOwnerConfigKey, @Nullable ObjectId revision);

  /**
   * Returns the absolute file path of the specified code owner config.
   *
   * <p>This method is useful to resolve the file path if the {@link CodeOwnerConfig.Key#fileName()}
   * in the code owner config key is not set.
   *
   * <p>The specified code owner config may or may not exist.
   *
   * @param codeOwnerConfigKey the key of the code owner config for which the file path should be
   *     returned
   * @return the absolute file path of the specified code owner config
   */
  Path getFilePath(CodeOwnerConfig.Key codeOwnerConfigKey);

  /**
   * Updates/Creates a code owner config.
   *
   * <p>If the specified code owner config doesn't exist yet, it is created.
   *
   * <p>Fails with {@link IllegalStateException} if the branch in which the code owner config should
   * be updated doesn't exist.
   *
   * @param codeOwnerConfigKey key of the code owner config
   * @param codeOwnerConfigUpdate definition of the update that should be performed
   * @param currentUser the current user if the code owner config update/creation was user
   *     initiated, otherwise {@code null}
   * @return the updated/created code owner config, {@link Optional#empty()} if the update led to a
   *     deletion of the code owner config or if the creation was a no-op
   */
  Optional<CodeOwnerConfig> upsertCodeOwnerConfig(
      CodeOwnerConfig.Key codeOwnerConfigKey,
      CodeOwnerConfigUpdate codeOwnerConfigUpdate,
      @Nullable IdentifiedUser currentUser);

  /**
   * Gets the {@link PathExpressionMatcher} that should be used to match path expressions.
   *
   * <p>May return {@link Optional#empty()} if path expressions are not supported by the code owner
   * backend. It this case all {@link CodeOwnerSet}s that have path expressions are ignored and will
   * not have any effect.
   *
   * @param branchNameKey project and branch for which the path expression matcher should be
   *     returned
   */
  Optional<PathExpressionMatcher> getPathExpressionMatcher(BranchNameKey branchNameKey);

  /**
   * Replaces the old email in the given code owner config file content with the new email.
   *
   * @param codeOwnerConfigFileContent the code owner config file content in which the old email
   *     should be replaced with the new email
   * @param oldEmail the email that should be replaced by the new email
   * @param newEmail the email that should replace the old email
   * @return the updated content
   * @throws NotImplementedException if the backend doesn't support replacing emails in code owner
   *     config files
   */
  default String replaceEmail(String codeOwnerConfigFileContent, String oldEmail, String newEmail) {
    throw new NotImplementedException();
  }
}
