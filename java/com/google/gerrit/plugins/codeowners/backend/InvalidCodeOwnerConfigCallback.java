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

/** Callback interface to let callers handle invalid code owner config files. */
public interface InvalidCodeOwnerConfigCallback {
  /**
   * Invoked when an invalid code owner config file is found.
   *
   * @param codeOwnerConfigFilePath the path of the invalid code owner config file
   * @param invalidCodeOwnerConfigException the parsing exception
   */
  void onInvalidCodeOwnerConfig(
      Path codeOwnerConfigFilePath,
      InvalidCodeOwnerConfigException invalidCodeOwnerConfigException);
}
