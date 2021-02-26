// Copyright (C) 2021 The Android Open Source Project
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

import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

/** API to load {@link CodeOwnerConfig}s. */
public interface CodeOwnerConfigLoader {
  /**
   * Retrieves the code owner config for the given key from the given branch revision.
   *
   * @param codeOwnerConfigKey the key of the code owner config that should be retrieved
   * @param revision the branch revision from which the code owner config should be loaded
   * @return the code owner config for the given key if it exists, otherwise {@link
   *     Optional#empty()}
   */
  public Optional<CodeOwnerConfig> get(CodeOwnerConfig.Key codeOwnerConfigKey, ObjectId revision);

  /**
   * Retrieves the code owner config for the given key from the current revision of the branch.
   *
   * @param codeOwnerConfigKey the key of the code owner config that should be retrieved
   * @return the code owner config for the given key if it exists, otherwise {@link
   *     Optional#empty()}
   */
  public Optional<CodeOwnerConfig> getFromCurrentRevision(CodeOwnerConfig.Key codeOwnerConfigKey);
}
