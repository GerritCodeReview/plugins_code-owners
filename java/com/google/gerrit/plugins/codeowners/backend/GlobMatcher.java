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

import com.google.common.flogger.FluentLogger;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.regex.PatternSyntaxException;

/**
 * Matcher that checks for a given path expression as Java NIO glob if it matches a given path.
 *
 * <p>This matcher supports standard NIO globs:
 *
 * <ul>
 *   <li>'*': matches any string that does not include slashes
 *   <li>'**': matches any string, including slashes
 *   <li>'?': matches any character
 *   <li>'[abc]': matches one character given in the bracket
 *   <li>'[a-c]': matches one character from the range given in the bracket
 *   <li>'{html,htm}': matches either of the 2 expressions, 'html' or 'htm'
 *   <li>'{**&#47;,}': matches either of the 2 expressions, any string including slashes ('**')
 *       followed by a '/' or empty string. This can be used to match a file name in a folder and
 *       all its subfolders, e.g. '{**&#47;,}BUILD' matches files that either match '**&#47;BUILD'
 *       or 'BUILD'.
 * </ul>
 */
public class GlobMatcher implements PathExpressionMatcher {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Singleton instance. */
  public static GlobMatcher INSTANCE = new GlobMatcher();

  /** Private constructor to prevent creation of further instances. */
  private GlobMatcher() {}

  @Override
  public boolean matches(String glob, Path relativePath) {
    try {
      boolean isMatching =
          FileSystems.getDefault().getPathMatcher("glob:" + glob).matches(relativePath);
      logger.atFine().log(
          "path %s %s matching %s", relativePath, isMatching ? "is" : "is not", glob);
      return isMatching;
    } catch (PatternSyntaxException e) {
      logger.atFine().log("glob %s is invalid: %s", glob, e.getMessage());
      return false;
    }
  }
}
