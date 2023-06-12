// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// limitations under the License.

package com.google.gerrit.plugins.codeowners.api;

  /**

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
