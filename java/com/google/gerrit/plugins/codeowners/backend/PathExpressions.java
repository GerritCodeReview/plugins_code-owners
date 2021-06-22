// Copyright (C) 2021 The Android Open Source Project
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

import java.util.Locale;
import java.util.Optional;

/** Enum listing the supported options for path expressions syntaxes. */
public enum PathExpressions {
  /** Simple path expressions as implemented by {@link SimplePathExpressionMatcher}. */
  SIMPLE(SimplePathExpressionMatcher.INSTANCE),

  /** Plain glob path expressions as implemented by {@link GlobMatcher}. */
  GLOB(GlobMatcher.INSTANCE),

  /**
   * Find-owners compatible glob path expressions as implemented by {@link FindOwnersGlobMatcher}.
   */
  FIND_OWNERS_GLOB(FindOwnersGlobMatcher.INSTANCE);

  private final PathExpressionMatcher matcher;

  private PathExpressions(PathExpressionMatcher matcher) {
    this.matcher = matcher;
  }

  /** Gets the path expression matcher. */
  public PathExpressionMatcher getMatcher() {
    return matcher;
  }

  /**
   * Tries to parse a string as a {@link PathExpressions} enum.
   *
   * @param value the string value to be parsed
   * @return the parsed {@link PathExpressions} enum, {@link Optional#empty()} if the given value
   *     couldn't be parsed as {@link PathExpressions} enum
   */
  public static Optional<PathExpressions> tryParse(String value) {
    try {
      return Optional.of(PathExpressions.valueOf(value.toUpperCase(Locale.US)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
