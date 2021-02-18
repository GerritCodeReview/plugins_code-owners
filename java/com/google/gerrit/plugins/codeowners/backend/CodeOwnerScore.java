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

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.common.Nullable;
import java.util.Optional;

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
   * user Y is a better reviewer/approver for '/foo/bar/baz/' than user X as they have a lower
   * distance.
   */
  DISTANCE(Kind.LOWER_VALUE_IS_BETTER, /* weight= */ 1, /* maxValue= */ null),

  /**
   * Score to take into account whether a code owner is a reviewer.
   *
   * <p>Code owners that are reviewers get scored with 1 (see {@link #IS_REVIEWER_SCORING_VALUE}),
   * while code owners that are not a reviewer get scored with 0 (see {@link
   * #NO_REVIEWER_SCORING_VALUE}).
   *
   * <p>The IS_REVIEWER score has a higher weight than the {@link #DISTANCE} score so that it takes
   * precedence and code owners that are reviewers are always returned first.
   */
  IS_REVIEWER(Kind.GREATER_VALUE_IS_BETTER, /* weight= */ 2, /* maxValue= */ 1);

  /**
   * Scoring value for the {@link #IS_REVIEWER} score for users that are not a reviewer of the
   * change.
   */
  public static int NO_REVIEWER_SCORING_VALUE = 0;

  /**
   * Scoring value for the {@link #IS_REVIEWER} score for users that are a reviewer of the change.
   */
  public static int IS_REVIEWER_SCORING_VALUE = 1;

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

  /**
   * The weight that this score should have when sorting code owners.
   *
   * <p>The higher the weight the larger the impact that this score has on the sorting.
   */
  private final double weight;

  /**
   * The max value that this score can have.
   *
   * <p>Not set if max value is not hard-coded, but is different case by case.
   *
   * <p>For scores that have a max value set scorings must be created by the {@link
   * #createScoring()} method, for scores with flexible max values (maxValue = null) scorings must
   * be created by the {@link #createScoring(int)} method.
   */
  @Nullable private final Integer maxValue;

  private CodeOwnerScore(Kind kind, double weight, @Nullable Integer maxValue) {
    this.kind = kind;
    this.weight = weight;
    this.maxValue = maxValue;
  }

  /**
   * Creates a {@link CodeOwnerScoring.Builder} instance for this score.
   *
   * <p>Use {@link #createScoring()} instead if the score has a max value set.
   *
   * @param maxValue the max possible scoring value
   * @return the created {@link CodeOwnerScoring.Builder} instance
   */
  public CodeOwnerScoring.Builder createScoring(int maxValue) {
    checkState(
        this.maxValue == null,
        "score %s has defined a maxValue, setting maxValue not allowed",
        name());
    return CodeOwnerScoring.builder(this, maxValue);
  }

  /**
   * Creates a {@link CodeOwnerScoring.Builder} instance for this score.
   *
   * <p>Use {@link #createScoring(int)} instead if the score doesn't have a max value set.
   *
   * @return the created {@link CodeOwnerScoring.Builder} instance
   */
  public CodeOwnerScoring.Builder createScoring() {
    checkState(
        maxValue != null,
        "score %s doesn't have a maxValue defined, setting maxValue is required",
        name());
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

  double weight() {
    return weight;
  }

  Optional<Integer> maxValue() {
    return Optional.ofNullable(maxValue);
  }
}
