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
 * The input for the {@link com.google.gerrit.plugins.codeowners.restapi.CheckCodeOwnerConfigFiles}
 * REST endpoint.
 */
public class CheckCodeOwnerConfigFilesInput {
  /**
   * Whether code owner config files in branches for which the code owners functionality should be
   * validated too.
   *
   * <p>By default unset, {@code false}.
   */
  public Boolean validateDisabledBranches;

  /**
   * Branches for which code owner config files should be validated.
   *
   * <p>The {@code refs/heads/} prefix may be omitted.
   *
   * <p>By default unset, which means that code owner config files in all branches should be
   * validated.
   */
  public List<String> branches;
}
