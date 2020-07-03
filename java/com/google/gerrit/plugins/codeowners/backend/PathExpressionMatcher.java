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
 * Matcher that checks for a given path expression if it matches a given path.
 *
 * <p>This interface allows {@link CodeOwnersBackend}s to support different kinds of path
 * expressions (e.g. globs or regular expressions).
 */
public interface PathExpressionMatcher {
  /**
   * Whether the given path expression matches the given path.
   *
   * <p>This method is invoked for any path expression, regardless of whether the path expression
   * contains any wildcard. This means that the given path expression can also be a plain
   * folder/file name.
   *
   * @param pathExpression path expression relative to the code owner config file location, may
   *     contain wildcards, but may also be a plain folder/file name
   * @param relativePath path relative to the code owner config
   * @return {@code true} if the given path expression matches the given path, otherwise {@code
   *     false}
   */
  boolean matches(String pathExpression, Path relativePath);
}
