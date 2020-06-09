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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import org.junit.Test;

/** Tests for {@link CodeOwnerScoring}. */
public class CodeOwnerScoringTest extends AbstractCodeOwnersTest {
  @Test
  public void cannotAtValueForNullCodeOwner() throws Exception {
    CodeOwnerScoring.Builder builder = CodeOwnerScoring.builder(CodeOwnerScore.DISTANCE, 100);
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> builder.putValueForCodeOwner(null, 50));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwner");
  }

  @Test
  public void canAddMultipleValuesForACodeOwner() throws Exception {
    CodeOwner codeOwner = CodeOwner.create(admin.id());
    CodeOwnerScoring.Builder builder = CodeOwnerScoring.builder(CodeOwnerScore.DISTANCE, 100);
    builder.putValueForCodeOwner(codeOwner, 50);
    builder.putValueForCodeOwner(codeOwner, 25);
    assertThat(builder.build().values()).containsExactly(codeOwner, 25, codeOwner, 50);
  }

  @Test
  public void getBestValueForDistanceScore() throws Exception {
    CodeOwner codeOwner = CodeOwner.create(admin.id());
    CodeOwnerScoring.Builder builder = CodeOwnerScoring.builder(CodeOwnerScore.DISTANCE, 100);
    builder.putValueForCodeOwner(codeOwner, 50);
    builder.putValueForCodeOwner(codeOwner, 25);

    Optional<Integer> bestValue = builder.build().bestValue(codeOwner);
    assertThat(bestValue).isPresent();

    // for the distance score a lower value is better
    assertThat(bestValue.get()).isEqualTo(25);
  }

  @Test
  public void getBestValueForCodeOwnerThatWasNotScored() throws Exception {
    assertThat(
            CodeOwnerScoring.builder(CodeOwnerScore.DISTANCE, 100)
                .build()
                .bestValue(CodeOwner.create(admin.id())))
        .isEmpty();
  }

  @Test
  public void getScoringForDistanceScore() throws Exception {
    CodeOwner codeOwner = CodeOwner.create(admin.id());
    CodeOwnerScoring.Builder builder = CodeOwnerScoring.builder(CodeOwnerScore.DISTANCE, 100);
    builder.putValueForCodeOwner(codeOwner, 50);
    builder.putValueForCodeOwner(codeOwner, 25);

    Optional<Double> scoring = builder.build().scoring(codeOwner);
    assertThat(scoring).isPresent();
    assertThat(scoring.get()).isEqualTo(0.75);
  }

  @Test
  public void getScoringForCodeOwnerThatWasNotScored() throws Exception {
    assertThat(
            CodeOwnerScoring.builder(CodeOwnerScore.DISTANCE, 100)
                .build()
                .scoring(CodeOwner.create(admin.id())))
        .isEmpty();
  }

  @Test
  public void sortCodeOwnersByScorings() throws Exception {
    CodeOwner codeOwner1 = CodeOwner.create(admin.id());
    CodeOwner codeOwner2 = CodeOwner.create(user.id());
    CodeOwner codeOwner3 = CodeOwner.create(accountCreator.user2().id());

    ArrayList<CodeOwner> codeOwners = new ArrayList<>();
    codeOwners.add(codeOwner1);
    codeOwners.add(codeOwner2);
    codeOwners.add(codeOwner3);

    // lower distance is better
    Comparator<CodeOwner> comparator =
        CodeOwnerScoring.builder(CodeOwnerScore.DISTANCE, 100)
            .putValueForCodeOwner(codeOwner1, 50)
            .putValueForCodeOwner(codeOwner2, 100)
            .putValueForCodeOwner(codeOwner3, 0)
            .build()
            .comparingByScoring();
    Collections.sort(codeOwners, comparator);
    assertThat(codeOwners).containsExactly(codeOwner3, codeOwner1, codeOwner2).inOrder();
  }

  @Test
  public void cannotSortCodeOwnersByScoringsIfAnyCodeOwnerHasNoScoring() throws Exception {
    CodeOwner codeOwner1 = CodeOwner.create(admin.id());
    CodeOwner codeOwner2 = CodeOwner.create(user.id());

    ArrayList<CodeOwner> codeOwners = new ArrayList<>();
    codeOwners.add(codeOwner1);
    codeOwners.add(codeOwner2);

    Comparator<CodeOwner> comparator =
        CodeOwnerScoring.builder(CodeOwnerScore.DISTANCE, 100)
            .putValueForCodeOwner(codeOwner1, 50)
            .build()
            .comparingByScoring();
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> Collections.sort(codeOwners, comparator));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format("No %s scoring for code owner %s", CodeOwnerScore.DISTANCE, codeOwner2));
  }
}
