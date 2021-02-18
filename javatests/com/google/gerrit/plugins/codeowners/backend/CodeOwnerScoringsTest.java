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
import static com.google.gerrit.plugins.codeowners.backend.CodeOwnerScore.IS_REVIEWER_SCORING_VALUE;
import static com.google.gerrit.plugins.codeowners.backend.CodeOwnerScore.NO_REVIEWER_SCORING_VALUE;

import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import java.util.ArrayList;
import org.junit.Test;

/** Tests for {@link CodeOwnerScorings}. */
public class CodeOwnerScoringsTest extends AbstractCodeOwnersTest {
  @Test
  public void sortCodeOwnersByLowerValueIsBetterScore() throws Exception {
    CodeOwner codeOwner1 = CodeOwner.create(admin.id());
    CodeOwner codeOwner2 = CodeOwner.create(user.id());
    CodeOwner codeOwner3 = CodeOwner.create(accountCreator.user2().id());

    ArrayList<CodeOwner> codeOwners = new ArrayList<>();
    codeOwners.add(codeOwner1);
    codeOwners.add(codeOwner2);
    codeOwners.add(codeOwner3);

    // lower distance is better
    CodeOwnerScoring distanceScoring =
        CodeOwnerScore.DISTANCE
            .createScoring(100)
            .putValueForCodeOwner(codeOwner1, 50)
            .putValueForCodeOwner(codeOwner2, 100)
            .putValueForCodeOwner(codeOwner3, 0)
            .build();
    codeOwners.sort(CodeOwnerScorings.create(distanceScoring).comparingByScorings());
    assertThat(codeOwners).containsExactly(codeOwner3, codeOwner1, codeOwner2).inOrder();
  }

  @Test
  public void sortCodeOwnersByGreaterValueIsBetterScore() throws Exception {
    CodeOwner codeOwner1 = CodeOwner.create(admin.id());
    CodeOwner codeOwner2 = CodeOwner.create(user.id());

    ArrayList<CodeOwner> codeOwners = new ArrayList<>();
    codeOwners.add(codeOwner1);
    codeOwners.add(codeOwner2);

    // for the IS_REVIEWER score a greater score is better
    CodeOwnerScoring isReviewerScoring =
        CodeOwnerScore.IS_REVIEWER
            .createScoring()
            .putValueForCodeOwner(codeOwner1, 0)
            .putValueForCodeOwner(codeOwner2, 1)
            .build();
    codeOwners.sort(CodeOwnerScorings.create(isReviewerScoring).comparingByScorings());
    assertThat(codeOwners).containsExactly(codeOwner2, codeOwner1).inOrder();
  }

  @Test
  public void sortCodeOwnersByMultipleScore() throws Exception {
    CodeOwner codeOwner1 = CodeOwner.create(admin.id());
    CodeOwner codeOwner2 = CodeOwner.create(user.id());
    CodeOwner codeOwner3 = CodeOwner.create(accountCreator.user2().id());

    ArrayList<CodeOwner> codeOwners = new ArrayList<>();
    codeOwners.add(codeOwner1);
    codeOwners.add(codeOwner2);
    codeOwners.add(codeOwner3);

    // lower distance is better
    CodeOwnerScoring distanceScoring =
        CodeOwnerScoring.builder(CodeOwnerScore.DISTANCE, 100)
            .putValueForCodeOwner(codeOwner1, 50)
            .putValueForCodeOwner(codeOwner2, 75)
            .putValueForCodeOwner(codeOwner3, 0)
            .build();

    // for the IS_REVIEWER score a greater score is better
    CodeOwnerScoring isReviewerScoring =
        CodeOwnerScore.IS_REVIEWER
            .createScoring()
            .putValueForCodeOwner(codeOwner1, NO_REVIEWER_SCORING_VALUE)
            .putValueForCodeOwner(codeOwner2, IS_REVIEWER_SCORING_VALUE)
            .putValueForCodeOwner(codeOwner3, NO_REVIEWER_SCORING_VALUE)
            .build();

    codeOwners.sort(
        CodeOwnerScorings.create(distanceScoring, isReviewerScoring).comparingByScorings());

    // Expected scorings:
    // codeOwner1: DISTANCE(weight=1)=0.5, IS_REVIEWER(weight=2)=0.0
    //             -> total scoring: 0.5 * 1 + 0.0 * 2 = 0.5
    // codeOwner2: DISTANCE(weight=1)=0.25, IS_REVIEWER(weight=2)=1.0
    //            -> total scoring: 0.25 * 1 + 1.0 * 2 = 2.25
    // codeOwner3: DISTANCE(weight=1)=1.0, IS_REVIEWER(weight=2)=0.0
    //            -> total scoring: 1.0 * 1 + 0.0 * 2 = 1.0
    // => expected order (highest total score first): codeOwner2, codeOwner3, codeOwner1
    assertThat(codeOwners).containsExactly(codeOwner2, codeOwner3, codeOwner1).inOrder();
  }

  @Test
  public void sortCodeOwnersByScoringsIfAnyCodeOwnerHasNoScoring() throws Exception {
    CodeOwner codeOwner1 = CodeOwner.create(admin.id());
    CodeOwner codeOwner2 = CodeOwner.create(user.id());

    ArrayList<CodeOwner> codeOwners = new ArrayList<>();
    codeOwners.add(codeOwner1);
    codeOwners.add(codeOwner2);

    CodeOwnerScoring distanceScoring =
        CodeOwnerScoring.builder(CodeOwnerScore.DISTANCE, 100)
            .putValueForCodeOwner(codeOwner2, 50)
            .build();
    codeOwners.sort(CodeOwnerScorings.create(distanceScoring).comparingByScorings());
    assertThat(codeOwners).containsExactly(codeOwner2, codeOwner1).inOrder();
  }
}
