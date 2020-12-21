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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
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
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Singleton instance. */
  public static FindOwnersGlobMatcher INSTANCE = new FindOwnersGlobMatcher();

  /** Private constructor to prevent creation of further instances. */
  private FindOwnersGlobMatcher() {}

  @Override
  public boolean matches(String glob, Path relativePath) {
    String adaptedGlob = replaceSingleStarWithDoubleStar(glob);
    logger.atFine().log("adapted glob = %s", adaptedGlob);
    return GlobMatcher.INSTANCE.matches(adaptedGlob, relativePath);
  }

  /**
   * Replaces any single '*' in the given glob with '**'. Non-single '*'s, like '**' or '***', stay
   * unchanged.
   *
   * @param glob glob in which any single '*' should be replaced by '**'
   */
  @VisibleForTesting
  String replaceSingleStarWithDoubleStar(String glob) {
    StringBuilder adaptedGlob = new StringBuilder();
    Character previousChar = null;
    boolean maybeSingleStar = false;
    for (char nextCharacter : glob.toCharArray()) {
      if (maybeSingleStar && nextCharacter != '*') {
        // the previous character was a '*' that was not preceded by '*' (maybeSingleStar == true),
        // since the next character is not '*', we are now sure that the previous character was a
        // single '*' which should be replaced by '**',
        // to do this append another '*'
        adaptedGlob.append('*');
      }
      adaptedGlob.append(nextCharacter);

      // the current character may be a single '*' if it's not preceded by '*'
      maybeSingleStar =
          nextCharacter == '*' && (previousChar == null || previousChar.charValue() != '*');
      previousChar = nextCharacter;
    }

    if (maybeSingleStar) {
      // the last character was a '*' that was not preceded by '*'
      adaptedGlob.append('*');
    }

    return adaptedGlob.toString();
  }
}
