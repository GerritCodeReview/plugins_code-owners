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

/**
 * Glob matcher that is compatible with how globs are interpreted by the {@code find-owners} plugin.
 *
 * <p>This matcher has the same behaviour as the {@link GlobMatcher} except that:
 *
 * <ul>
 *   <li>'*': matches any string, including slashes (same as '**')
 * </ul>
 */
public class FindOwnersGlobMatcher implements PathExpressionMatcher {
  /** Singleton instance. */
  public static FindOwnersGlobMatcher INSTANCE = new FindOwnersGlobMatcher();

  /** Private constructor to prevent creation of further instances. */
  private FindOwnersGlobMatcher() {}

  @Override
  public boolean matches(String glob, Path relativePath) {
    // always match files in all subdirectories
    return GlobMatcher.INSTANCE.matches("{**/,}" + glob, relativePath);
  }
}
