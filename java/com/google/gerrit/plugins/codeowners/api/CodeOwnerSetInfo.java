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

import java.util.List;

/**
 * Representation of a {@link com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet} in the REST
 * API.
 *
 * <p>This class determines the JSON format of code owner sets in the REST API.
 */
public class CodeOwnerSetInfo {
  /**
   * The code owners of this code owner config.
   *
   * <p>Not set if there are no code owners defined in this code owner set.
   */
  public List<CodeOwnerReferenceInfo> codeOwners;
}
