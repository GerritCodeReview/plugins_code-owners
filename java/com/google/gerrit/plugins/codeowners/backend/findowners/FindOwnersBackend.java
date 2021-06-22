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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.plugins.codeowners.backend.AbstractFileBasedCodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigFile;
import com.google.gerrit.plugins.codeowners.backend.PathExpressions;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Backend that supports the syntax in which the {@code find-owners} plugin stores {@link
 * CodeOwnerConfig}s.
 */
@Singleton
public class FindOwnersBackend extends AbstractFileBasedCodeOwnerBackend {
  /** The ID of this code owner backend. */
  public static final String ID = "find-owners";

  /** The name of the files in which {@link CodeOwnerConfig}s are stored. */
  @VisibleForTesting public static final String CODE_OWNER_CONFIG_FILE_NAME = "OWNERS";

  @Inject
  FindOwnersBackend(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerConfigFile.Factory codeOwnerConfigFileFactory,
      FindOwnersCodeOwnerConfigParser codeOwnerConfigParser,
      GitRepositoryManager repoManager,
      @GerritPersonIdent PersonIdent serverIdent,
      MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory,
      RetryHelper retryHelper) {
    super(
        codeOwnersPluginConfiguration,
        repoManager,
        serverIdent,
        metaDataUpdateInternalFactory,
        retryHelper,
        CODE_OWNER_CONFIG_FILE_NAME,
        codeOwnerConfigFileFactory,
        codeOwnerConfigParser);
  }

  @Override
  public Optional<PathExpressions> getDefaultPathExpressions() {
    return Optional.of(PathExpressions.FIND_OWNERS_GLOB);
  }

  @Override
  public String replaceEmail(String codeOwnerConfigFileContent, String oldEmail, String newEmail) {
    return FindOwnersCodeOwnerConfigParser.replaceEmail(
        codeOwnerConfigFileContent, oldEmail, newEmail);
  }
}
