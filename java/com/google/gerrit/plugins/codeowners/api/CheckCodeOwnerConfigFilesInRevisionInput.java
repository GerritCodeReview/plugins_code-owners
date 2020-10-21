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

import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;

/**
 * The input for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.CheckCodeOwnerConfigFilesInRevision} REST endpoint.
 */
public class CheckCodeOwnerConfigFilesInRevisionInput {
  /**
   * Glob that limits the validation to code owner config files that have a path that matches this
   * glob.
   *
   * <p>By default unset, which means that all code owner config files should be validated.
   */
  public String path;

  /**
   * Level that controls which code owner config file issues are returned.
   *
   * <p>The following values are supported:
   *
   * <ul>
   *   <li>{@code FATAL}: only fatal issues are returned
   *   <li>{@code ERROR}: only fatal and error issues are returned
   *   <li>{@code WARNING}: all issues (warning, error and fatal) are returned
   * </ul>
   *
   * <p>If unset, {@code WARNING} is used.
   */
  public ConsistencyProblemInfo.Status verbosity;
}
