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
import java.util.Optional;
import org.junit.Test;

/** Tests for {@link CodeOwnerScoring}. */
public class CodeOwnerScoringTest extends AbstractCodeOwnersTest {
  @Test
  public void cannotAddValueForNullCodeOwner() throws Exception {
    CodeOwnerScoring.Builder builder = CodeOwnerScoring.builder(CodeOwnerScore.DISTANCE, 100);
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> builder.putValueForCodeOwner(/* codeOwner= */ null, 50));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwner");
  }

  @Test
  public void cannotAddNegativeValue() throws Exception {
    CodeOwner codeOwner = CodeOwner.create(admin.id());
    CodeOwnerScoring.Builder builder = CodeOwnerScoring.builder(CodeOwnerScore.DISTANCE, 100);
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> builder.putValueForCodeOwner(codeOwner, -1));
    assertThat(exception).hasMessageThat().isEqualTo("value cannot be negative: -1");
  }

  @Test
  public void cannotAddValueGreaterThanMaxValue() throws Exception {
    CodeOwner codeOwner = CodeOwner.create(admin.id());
    CodeOwnerScoring.Builder builder = CodeOwnerScoring.builder(CodeOwnerScore.DISTANCE, 100);
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> builder.putValueForCodeOwner(codeOwner, 101));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("value cannot be greater than max value 100: 101");
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

    // for the distance score a lower value is better
    assertThat(bestValue).hasValue(25);
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

    double scoring = builder.build().scoring(codeOwner);
    assertThat(scoring).isWithin(1.0e-10).of(0.75);
  }

  @Test
  public void getScoringForCodeOwnerThatWasNotScored() throws Exception {
    assertThat(
            CodeOwnerScoring.builder(CodeOwnerScore.DISTANCE, 100)
                .build()
                .scoring(CodeOwner.create(admin.id())))
        .isZero();
  }
}
