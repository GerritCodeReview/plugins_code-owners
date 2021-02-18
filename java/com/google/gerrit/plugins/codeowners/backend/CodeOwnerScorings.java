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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class to sort code owners based on their scorings on different {@link CodeOwnerScore}s.
 *
 * <p>To determine the sort order the scorings are weighted based on the {@link
 * CodeOwnerScore#weight()} of the {@link CodeOwnerScore} on which the scoring was done.
 */
@AutoValue
public abstract class CodeOwnerScorings {
  /** The scorings that should be taken into account for sorting the code owners. */
  public abstract ImmutableSet<CodeOwnerScoring> scorings();

  public static CodeOwnerScorings create(CodeOwnerScoring... codeOwnerScorings) {
    return new AutoValue_CodeOwnerScorings(ImmutableSet.copyOf(codeOwnerScorings));
  }

  public static CodeOwnerScorings create(Set<CodeOwnerScoring> codeOwnerScorings) {
    return new AutoValue_CodeOwnerScorings(ImmutableSet.copyOf(codeOwnerScorings));
  }

  public static CodeOwnerScorings appendScoring(
      CodeOwnerScorings codeOwnerScorings, CodeOwnerScoring codeOwnerScoringToBeAdded) {
    Set<CodeOwnerScoring> codeOwnerScoringSet = new HashSet<>(codeOwnerScorings.scorings());
    codeOwnerScoringSet.add(codeOwnerScoringToBeAdded);
    return create(codeOwnerScoringSet);
  }

  /**
   * Returns a comparator to sort code owners by the collected scorings.
   *
   * <p>Code owners with higher scoring come first. The order of code owners with the same scoring
   * is undefined.
   */
  public Comparator<CodeOwner> comparingByScorings() {
    return Comparator.comparingDouble(this::sumWeightedScorings).reversed();
  }

  /** Returns the sum of all weighted scorings that available for the given code owner. */
  private double sumWeightedScorings(CodeOwner codeOwner) {
    double sum =
        scorings().stream()
            .map(scoring -> scoring.weightedScoring(codeOwner))
            .collect(Collectors.summingDouble(Double::doubleValue));
    return sum;
  }
}
