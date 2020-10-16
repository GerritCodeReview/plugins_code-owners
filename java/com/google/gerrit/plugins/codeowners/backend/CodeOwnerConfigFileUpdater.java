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

import java.nio.file.Path;
import java.util.Optional;

/** Callback interface to update a code owner config file. */
public interface CodeOwnerConfigFileUpdater {
  /**
   * Callback for a code owner config file.
   *
   * @param codeOwnerConfigFilePath absolute path of the code owner config file
   * @param codeOwnerConfigFileContent the content of the code owner config, can be also the content
   *     of a non-parseable code owner config
   * @return the updated content of the code owner config file, {@link Optional#empty()} if no
   *     update should be performed
   */
  Optional<String> update(Path codeOwnerConfigFilePath, String codeOwnerConfigFileContent);
}
