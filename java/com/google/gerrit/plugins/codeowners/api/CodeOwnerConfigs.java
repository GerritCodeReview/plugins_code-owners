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

package com.google.gerrit.plugins.codeowners.api;

import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Java API to for code owners configs in a branch.
 *
 * <p>To create an instance for a branch use {@link CodeOwnerConfigsFactory}.
 */
public interface CodeOwnerConfigs {
  /**
   * Gets the code owner config for the given path.
   *
   * @param path the path for which the code owner config should be returned
   * @return the code owner config if it exists
   */
  Optional<CodeOwnerConfigInfo> get(Path path) throws RestApiException;

  /**
   * Gets the code owner config for the given path.
   *
   * @param path the path for which the code owner config should be returned
   * @return the code owner config if it exists
   */
  default Optional<CodeOwnerConfigInfo> get(String path) throws RestApiException {
    return get(Paths.get(path));
  }

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements CodeOwnerConfigs {
    @Override
    public Optional<CodeOwnerConfigInfo> get(Path path) throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
