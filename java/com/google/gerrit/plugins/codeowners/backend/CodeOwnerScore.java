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

/**
 * Scores by which we rate how good we consider a code owner as reviewer/approver for a certain
 * path.
 */
public enum CodeOwnerScore {
  /**
   * The distance of the code owner configuration that defines the code owner from the owned path.
   *
   * <p>The smaller the distance the better we consider the code owner as reviewer/approver for the
   * path.
   *
   * <p>Example: If there are 2 owner configurations '/A' and '/foo/bar/B' and we are computing the
   * code owners for '/foo/bar/baz/, it can be that user X is a code owner of '/foo/bar/baz/' due to
   * the owner configuration '/A' and user Y is a code owner of '/foo/bar/baz/' due to the owner
   * configuration '/foo/bar/B'. Then user X has a distance of 3 (since the folder in which the
   * owner configuration 'A' is stored, '/', has 3 path segments less than the path '/foo/bar/baz/'
   * for which we are looking up code owners) and user Y has a distance of 1 (since the folder in
   * which the owner configuration 'B' is stored, '/foo/bar', has 1 path segment less than the path
   * '/foo/bar/baz/' for which we are looking up code owners). This means, looking at the distance,
   * user Y is a better reviewer/approver for '/foo/bar/baz/' than user X as it has a lower
   * distance.
   */
  DISTANCE(Kind.LOWER_VALUE_IS_BETTER);

  /**
   * Score kind.
   *
   * <p>Whether a greater value as scoring is better than a lower value ({@code
   * GREATER_VALUE_IS_BETTER}), or vice-versa ({@code LOWER_VALUE_IS_BETTER}).
   */
  private enum Kind {
    GREATER_VALUE_IS_BETTER,
    LOWER_VALUE_IS_BETTER;
  }

  /**
   * Score kind.
   *
   * <p>Whether a greater value as scoring is better than a lower value ({@link
   * Kind#GREATER_VALUE_IS_BETTER}), or vice-versa ({@link Kind#LOWER_VALUE_IS_BETTER}).
   */
  private final Kind kind;

  private CodeOwnerScore(Kind kind) {
    this.kind = kind;
  }

  /**
   * Creates a {@link CodeOwnerScoring.Builder} instance for this score.
   *
   * @param maxValue the max possible scoring value
   * @return the created {@link CodeOwnerScoring.Builder} instance
   */
  public CodeOwnerScoring.Builder createScoring(int maxValue) {
    return CodeOwnerScoring.builder(this, maxValue);
  }

  /**
   * Whether for this score a lower value is considered better than a greater value.
   *
   * @return whether for this score a lower value is considered better than a greater value.
   */
  boolean isLowerValueBetter() {
    return Kind.LOWER_VALUE_IS_BETTER.equals(kind);
  }
}
