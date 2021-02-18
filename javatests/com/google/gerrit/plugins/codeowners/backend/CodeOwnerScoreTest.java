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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import org.junit.Test;

/** Tests for {@link CodeOwnerScore}. */
public class CodeOwnerScoreTest extends AbstractCodeOwnersTest {
  @Test
  public void createScoringForScoreThatDefinesAMaxValue() throws Exception {
    assertThat(CodeOwnerScore.IS_REVIEWER.createScoring().build().maxValue())
        .isEqualTo(CodeOwnerScore.IS_REVIEWER.maxValue().get());
  }

  @Test
  public void cannotCreateScoringWithMaxValueForScoreThatDefinesAMaxValue() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> CodeOwnerScore.IS_REVIEWER.createScoring(5));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("score IS_REVIEWER has defined a maxValue, setting maxValue not allowed");
  }

  @Test
  public void createScoringWithMaxValueForScoreThatDosntDefineAMaxValue() throws Exception {
    assertThat(CodeOwnerScore.DISTANCE.createScoring(5).build().maxValue()).isEqualTo(5);
  }

  @Test
  public void cannotCreateScoringWithoutMaxValueForScoreThatDoesntDefinesAMaxValue()
      throws Exception {
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> CodeOwnerScore.DISTANCE.createScoring());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("score DISTANCE doesn't have a maxValue defined, setting maxValue is required");
  }
}
