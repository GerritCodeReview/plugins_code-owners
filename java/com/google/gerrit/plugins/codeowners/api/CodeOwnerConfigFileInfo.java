// Copyright (C) 2023 The Android Open Source Project
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

import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportMode;
import java.util.List;

/**
 * Representation of a code owner config file in the REST API.
 *
 * <p>This class determines the JSON format of code owner configs in the REST API.
 */
public class CodeOwnerConfigFileInfo {
  /**
   * The name of the project from which the code owner config was loaded, or for unresolved imports,
   * from which the code owner config was supposed to be loaded.
   */
  public String project;

  /**
   * The name of the branch from which the code owner config was loaded, or for unresolved imports,
   * from which the code owner config was supposed to be loaded.
   */
  public String branch;

  /** The path of the code owner config file. */
  public String path;

  /** Imported code owner config files. */
  public List<CodeOwnerConfigFileInfo> imports;

  /** Imported code owner config files that couldn't be resolved. */
  public List<CodeOwnerConfigFileInfo> unresolvedImports;

  /**
   * Message explaining why this code owner config couldn't be resolved.
   *
   * <p>Only set for unresolved imports.
   */
  public String unresolvedErrorMessage;

  /**
   * The import mode.
   *
   * <p>Only set for imports.
   */
  public CodeOwnerConfigImportMode importMode;
}
