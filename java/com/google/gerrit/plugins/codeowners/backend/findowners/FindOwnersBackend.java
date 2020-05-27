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

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnersBackend;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class FindOwnersBackend implements CodeOwnersBackend {
  private final CodeOwnerConfigFile.Factory codeOwnerConfigFileFactory;

  @Inject
  FindOwnersBackend(CodeOwnerConfigFile.Factory codeOwnerConfigFileFactory) {
    this.codeOwnerConfigFileFactory = codeOwnerConfigFileFactory;
  }

  @Override
  public Optional<CodeOwnerConfig> getCodeOwnerConfig(CodeOwnerConfig.Key codeOwnerConfigKey) {
    try {
      return codeOwnerConfigFileFactory.load(codeOwnerConfigKey).getLoadedCodeOwnerConfig();
    } catch (IOException | ConfigInvalidException e) {
      throw new StorageException(
          String.format("failed to load code owner config %s", codeOwnerConfigKey), e);
    }
  }
}
