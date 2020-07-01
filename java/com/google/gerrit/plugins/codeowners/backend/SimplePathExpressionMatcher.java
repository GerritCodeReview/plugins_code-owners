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
 * Matcher that checks for a given simple path expression if it matches a given path.
 *
 * <p>Simple path expressions are similar to Java NIO globs, but less powerful:
 *
 * <ul>
 *   <li>'*': Represents any string that does not include slashes.
 *   <li>'...': Represents any string, including slashes.
 *   <li>Patterns such as '{**&#47;,}' or '[1-4]' are not supported.
 * </ul>
 */
public class SimplePathExpressionMatcher implements PathExpressionMatcher {
  /** Singleton instance. */
  public static SimplePathExpressionMatcher INSTANCE = new SimplePathExpressionMatcher();

  /** Private constructor to prevent creation of further instances. */
  private SimplePathExpressionMatcher() {}

  @Override
  public boolean matches(String pathExpression, Path relativePath) {
    return GlobMatcher.INSTANCE.matches(asGlob(pathExpression), relativePath);
  }

  private static String asGlob(String pathExpression) {
    return escape(pathExpression, '{', '}', '[', ']').replace("...", "**");
  }

  private static String escape(String pathExpression, char... charsToEscape) {
    for (char charToEscape : charsToEscape) {
      pathExpression = pathExpression.replace(String.valueOf(charToEscape), "\\" + charToEscape);
    }
    return pathExpression;
  }
}
