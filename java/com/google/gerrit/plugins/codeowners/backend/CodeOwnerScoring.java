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
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableListMultimap;
import java.util.Comparator;
import java.util.Optional;

/**
 * Scorings for code owners on a particular {@link CodeOwnerScore} that express how good the code
 * owners are considered as reviewers/approvers for a certain path.
 */
@AutoValue
public abstract class CodeOwnerScoring {
  /** The score on which the scoring is done. */
  public abstract CodeOwnerScore score();

  /** The maximal possible scoring value. */
  public abstract int maxValue();

  /** The scoring values for the code owners. */
  public abstract ImmutableListMultimap<CodeOwner, Integer> values();

  /**
   * Computes the scoring for a code owner.
   *
   * <p>A value between '0.0' (lowest scoring) and '1.0' (highest scoring).
   *
   * @param codeOwner for which the scoring should be computed
   * @return the scoring for the code owner if scoring value for the code owner exists, otherwise
   *     {@link Optional#empty()} is returned
   */
  public Optional<Double> scoring(CodeOwner codeOwner) {
    Optional<Integer> bestValue = bestValue(codeOwner);
    if (!bestValue.isPresent()) {
      return Optional.empty();
    }

    double scoring = (double) bestValue.get() / maxValue();
    if (score().isLowerValueBetter()) {
      scoring = 1.0 - scoring;
    }
    checkState(scoring >= 0.0, "scoring  cannot be negative");
    checkState(scoring <= 1.0, "scoring cannot be greater than 1.0");
    return Optional.of(scoring);
  }

  /**
   * Returns the best scoring value for the given code owner.
   *
   * <p>If there are multiple scoring values for a code owner, finds and returns the best one.
   *
   * @param codeOwner the code owner for which the best scoring value should be returned
   * @return the best scoring value for the given code owner if there is at least one scoring value
   *     for the code owner, otherwise {@link Optional#empty()}
   */
  @VisibleForTesting
  Optional<Integer> bestValue(CodeOwner codeOwner) {
    Comparator<Integer> valueComparator = Comparator.naturalOrder();
    if (score().isLowerValueBetter()) {
      valueComparator = valueComparator.reversed();
    }
    return values().get(codeOwner).stream().max(valueComparator);
  }

  /**
   * Returns a comparator to sort code owners by the scorings collected in this {@link
   * CodeOwnerScoring} instance.
   *
   * <p>Code owners with higher scoring come first. The order of code owners with the same scoring
   * is undefined.
   */
  public Comparator<CodeOwner> comparingByScoring() {
    return Comparator.<CodeOwner>comparingDouble(
            codeOwner ->
                scoring(codeOwner)
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                String.format(
                                    "No %s scoring for code owner %s", score().name(), codeOwner))))
        .reversed();
  }

  /**
   * Creates a builder for a {@link CodeOwnerScoring}
   *
   * <p>The minimal possible value on the score is assumed to be {@code 0}.
   *
   * @param score the score for which scorings should be collected
   * @param maxValue the maximal possible value on the score
   * @return builder for a {@link CodeOwnerScoring}
   */
  public static CodeOwnerScoring.Builder builder(CodeOwnerScore score, int maxValue) {
    return new AutoValue_CodeOwnerScoring.Builder().setScore(score).setMaxValue(maxValue);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Sets the score on which the scoring is done.
     *
     * @param score the score on which the scoring is done
     * @return the Builder instance for chaining calls
     */
    abstract Builder setScore(CodeOwnerScore score);

    /**
     * Sets the maximal possible scoring value.
     *
     * @param maxValue the maximal possible scoring value
     * @return the Builder instance for chaining calls
     */
    abstract Builder setMaxValue(int maxValue);

    /** Gets a builder to add scoring values for code owners. */
    abstract ImmutableListMultimap.Builder<CodeOwner, Integer> valuesBuilder();

    /**
     * Puts a scoring value for a code owner.
     *
     * @param codeOwner the code owner for which the scoring value should be put
     * @param value the scoring value that should be put for the code owner
     * @return the Builder instance for chaining calls
     */
    public Builder putValueForCodeOwner(CodeOwner codeOwner, int value) {
      valuesBuilder().put(requireNonNull(codeOwner, "codeOwner"), value);
      return this;
    }

    /**
     * Builds the {@link CodeOwnerScoring} instance.
     *
     * @return the {@link CodeOwnerScoring} instance
     */
    public abstract CodeOwnerScoring build();
  }
}
