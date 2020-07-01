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

import java.nio.file.FileSystems;
import java.nio.file.Path;

/** Matcher that checks for a given path expression as Java NIO glob if it matches a given path. */
public class GlobMatcher implements PathExpressionMatcher {
  /** Singleton instance. */
  public static GlobMatcher INSTANCE = new GlobMatcher();

  /** Private constructor to prevent creation of further instances. */
  private GlobMatcher() {}

  @Override
  public boolean matches(String glob, Path relativePath) {
    return FileSystems.getDefault().getPathMatcher("glob:" + glob).matches(relativePath);
  }
}
