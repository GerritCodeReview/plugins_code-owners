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

  /**
   * The scoring values for the code owners.
   *
   * <p>These are the original non-scaled scoring values. The scaling to values between '0.0'
   * (lowest scoring) and '1.0' (highest scoring) is done by {@link #scoring(CodeOwner)}.
   *
   * <p>It would be possible to do the scaling right when adding the scoring values, but we prefer
   * to store the original non-scaled scoring values here, so that we are able to return them to the
   * frontend if they ask for it. The frontend may want to show details about scores to the users
   * since some users don't like automatic sorting if they are not able to understand how the score
   * for the sorting is computed. It's nothing that the frontend intends to implement immediately,
   * but in discussions this question comes up again and again, so we want to be prepared to provide
   * them this data.
   *
   * <p>Code owners can have multiple scoring values, e.g. if a user is assigned as code owner in
   * several applying code owner configs, the user has a {@link CodeOwnerScore#DISTANCE} scoring
   * value for each of the code owner configs. In the end we are only interested in the best scoring
   * value (see {@link #bestValue(CodeOwner)}), so we might have stored only the best values here.
   * We didn't do this since implementing this is not straight-forward and would have a negative
   * impact on performance. The problem is that {@link Builder#putValueForCodeOwner(CodeOwner, int)}
   * would need to check if there is any previous value and override it if the new value is better.
   * Getting access to the values map and the score (needed to know if greater or lower values are
   * better) in the builder is possible by adding getter methods to the {@link Builder}, however
   * overriding the previous value is not easily possible since the values would be stored in an
   * {@link com.google.common.collect.ImmutableMap} which doesn't allow modifications. This means we
   * would need to copy the whole map each time a value is overridden.
   */
  abstract ImmutableListMultimap<CodeOwner, Integer> values();

  /**
   * Computes the normalized scoring for a code owner.
   *
   * <p>The computation considers the {code CodeOwnerScore.Kind} of the {@link #score()} and
   * normalizes the best value for the code owner (see {@link #values()}) to a scoring between
   * {@code 0.0} (lowest scoring) and {@code 1.0} (highest scoring).
   *
   * <p>If there is no scoring value for the given code owner, the lowest possible scoring value is
   * assumed and ({@code 0.0}) is returned as scoring.
   *
   * @param codeOwner for which the normalized scoring should be computed
   * @return the normalized scoring for the code owner, {@code 0.0} if no scoring value for the code
   *     owner exists
   */
  public double scoring(CodeOwner codeOwner) {
    Optional<Integer> bestValue = bestValue(codeOwner);
    if (!bestValue.isPresent()) {
      return 0.0;
    }

    double scoring = (double) bestValue.get() / maxValue();
    if (score().isLowerValueBetter()) {
      scoring = 1.0 - scoring;
    }
    return scoring;
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
   * Computes the weighted scoring for a code owner.
   *
   * <p>The result of {@link #scoring(CodeOwner)} multiplied with the {@code score().weight()}.
   *
   * @param codeOwner for which the weighted scoring should be computed
   * @return the weighted scoring for the code owner
   */
  public double weightedScoring(CodeOwner codeOwner) {
    return score().weight() * scoring(codeOwner);
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

    abstract int maxValue();

    /** Gets a builder to add scoring values for code owners. */
    abstract ImmutableListMultimap.Builder<CodeOwner, Integer> valuesBuilder();

    /**
     * Puts a scoring value for a code owner.
     *
     * <p>The scoring value must be >= 0 and <= {@link #maxValue()}.
     *
     * @param codeOwner the code owner for which the scoring value should be put
     * @param value the scoring value that should be put for the code owner
     * @return the Builder instance for chaining calls
     */
    public Builder putValueForCodeOwner(CodeOwner codeOwner, int value) {
      requireNonNull(codeOwner, "codeOwner");
      checkState(value >= 0, "value cannot be negative: %s", value);
      checkState(
          value <= maxValue(), "value cannot be greater than max value %s: %s", maxValue(), value);

      valuesBuilder().put(codeOwner, value);
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
