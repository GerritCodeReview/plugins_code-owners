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

package com.google.gerrit.plugins.codeowners.backend.proto;

import com.google.gerrit.plugins.codeowners.backend.AbstractFileBasedCodeOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigFile;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.PersonIdent;

/** Backend that supports a proto syntax for storing {@link CodeOwnerConfig}s. */
@Singleton
public class ProtoBackend extends AbstractFileBasedCodeOwnersBackend {
  /** The ID of this code owner backend. */
  public static final String ID = "proto";

  /** The name of the files in which {@link CodeOwnerConfig}s are stored. */
  static final String CODE_OWNER_CONFIG_FILE_NAME = "OWNERS_METADATA";

  @Inject
  ProtoBackend(
      CodeOwnerConfigFile.Factory codeOwnerConfigFileFactory,
      GitRepositoryManager repoManager,
      @GerritPersonIdent PersonIdent serverIdent,
      MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory,
      RetryHelper retryHelper,
      ProtoCodeOwnerConfigParser codeOwnerConfigParser) {
    super(
        codeOwnerConfigFileFactory,
        repoManager,
        serverIdent,
        metaDataUpdateInternalFactory,
        retryHelper,
        CODE_OWNER_CONFIG_FILE_NAME,
        codeOwnerConfigParser);
  }
}
