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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerSetSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import java.util.NoSuchElementException;
import org.junit.Test;

/** Tests for {@link CodeOwnerSetModification}. */
public class CodeOwnerSetModificationTest extends AbstractCodeOwnersTest {
  @Test
  public void keep() throws Exception {
    ImmutableList<CodeOwnerSet> codeOwnerSets =
        ImmutableList.of(
            CodeOwnerSet.createWithoutPathExpressions(admin.email()),
            CodeOwnerSet.createWithoutPathExpressions(user.email()));
    assertThat(CodeOwnerSetModification.keep().apply(codeOwnerSets)).isEqualTo(codeOwnerSets);
  }

  @Test
  public void clear() throws Exception {
    assertThat(
            CodeOwnerSetModification.clear()
                .apply(
                    ImmutableList.of(
                        CodeOwnerSet.createWithoutPathExpressions(admin.email()),
                        CodeOwnerSet.createWithoutPathExpressions(user.email()))))
        .isEmpty();
  }

  @Test
  public void append() throws Exception {
    CodeOwnerSet codeOwnerSet1 = CodeOwnerSet.createWithoutPathExpressions(admin.email());
    CodeOwnerSet codeOwnerSet2 = CodeOwnerSet.createWithoutPathExpressions(user.email());
    assertThat(
            CodeOwnerSetModification.append(codeOwnerSet2).apply(ImmutableList.of(codeOwnerSet1)))
        .containsExactly(codeOwnerSet1, codeOwnerSet2)
        .inOrder();
  }

  @Test
  public void setOne() throws Exception {
    CodeOwnerSet codeOwnerSet1 = CodeOwnerSet.createWithoutPathExpressions(admin.email());
    CodeOwnerSet codeOwnerSet2 = CodeOwnerSet.createWithoutPathExpressions(user.email());
    assertThat(CodeOwnerSetModification.set(codeOwnerSet2).apply(ImmutableList.of(codeOwnerSet1)))
        .containsExactly(codeOwnerSet2);
  }

  @Test
  public void setList() throws Exception {
    CodeOwnerSet codeOwnerSet1 = CodeOwnerSet.createWithoutPathExpressions(admin.email());
    CodeOwnerSet codeOwnerSet2 = CodeOwnerSet.createWithoutPathExpressions(user.email());
    CodeOwnerSet codeOwnerSet3 = CodeOwnerSet.createWithoutPathExpressions("user2@test.com");
    assertThat(
            CodeOwnerSetModification.set(ImmutableList.of(codeOwnerSet2, codeOwnerSet3))
                .apply(ImmutableList.of(codeOwnerSet1)))
        .containsExactly(codeOwnerSet2, codeOwnerSet3)
        .inOrder();
  }

  @Test
  public void addToOnlySet() throws Exception {
    assertThat(
            CodeOwnerSetModification.addToOnlySet(CodeOwnerReference.create(user.email()))
                .apply(ImmutableList.of(CodeOwnerSet.createWithoutPathExpressions(admin.email()))))
        .containsExactly(CodeOwnerSet.createWithoutPathExpressions(admin.email(), user.email()));
  }

  @Test
  public void addToOnlySetFailsIfThereIsNoSet() throws Exception {
    assertThrows(
        NoSuchElementException.class,
        () ->
            CodeOwnerSetModification.addToOnlySet(CodeOwnerReference.create(user.email()))
                .apply(ImmutableList.of()));
  }

  @Test
  public void addToOnlySetFailsIfThereAreMultipleSets() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CodeOwnerSetModification.addToOnlySet(CodeOwnerReference.create(user.email()))
                .apply(
                    ImmutableList.of(
                        CodeOwnerSet.createWithoutPathExpressions(admin.email()),
                        CodeOwnerSet.createWithoutPathExpressions(user.email()))));
  }

  @Test
  public void addToOnlySetByEmail() throws Exception {
    assertThat(
            CodeOwnerSetModification.addToOnlySet(user.email())
                .apply(ImmutableList.of(CodeOwnerSet.createWithoutPathExpressions(admin.email()))))
        .containsExactly(CodeOwnerSet.createWithoutPathExpressions(admin.email(), user.email()));
  }

  @Test
  public void addToOnlySetByEmailFailsIfThereIsNoSet() throws Exception {
    assertThrows(
        NoSuchElementException.class,
        () -> CodeOwnerSetModification.addToOnlySet(user.email()).apply(ImmutableList.of()));
  }

  @Test
  public void addToOnlySetByEmailFailsIfThereAreMultipleSets() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CodeOwnerSetModification.addToOnlySet(user.email())
                .apply(
                    ImmutableList.of(
                        CodeOwnerSet.createWithoutPathExpressions(admin.email()),
                        CodeOwnerSet.createWithoutPathExpressions(user.email()))));
  }

  @Test
  public void addToCodeOwnerSet() throws Exception {
    CodeOwnerSet codeOwnerSet = CodeOwnerSet.createWithoutPathExpressions(admin.email());
    assertThat(
            CodeOwnerSetModification.addToCodeOwnerSet(
                codeOwnerSet, CodeOwnerReference.create(user.email())))
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email(), user.email());
  }

  @Test
  public void addToCodeOwnerSetThatAlreadyContainsTheCodeOwner() throws Exception {
    CodeOwnerSet codeOwnerSet = CodeOwnerSet.createWithoutPathExpressions(admin.email());
    assertThat(
            CodeOwnerSetModification.addToCodeOwnerSet(
                codeOwnerSet, CodeOwnerReference.create(admin.email())))
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email());
  }

  @Test
  public void removeFromOnlySet() throws Exception {
    assertThat(
            CodeOwnerSetModification.removeFromOnlySet(CodeOwnerReference.create(user.email()))
                .apply(
                    ImmutableList.of(
                        CodeOwnerSet.createWithoutPathExpressions(admin.email(), user.email()))))
        .containsExactly(CodeOwnerSet.createWithoutPathExpressions(admin.email()));
  }

  @Test
  public void removeFromOnlySetFailsIfThereIsNoSet() throws Exception {
    assertThrows(
        NoSuchElementException.class,
        () ->
            CodeOwnerSetModification.removeFromOnlySet(CodeOwnerReference.create(user.email()))
                .apply(ImmutableList.of()));
  }

  @Test
  public void removeFromOnlySetFailsIfThereAreMultipleSets() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CodeOwnerSetModification.removeFromOnlySet(CodeOwnerReference.create(user.email()))
                .apply(
                    ImmutableList.of(
                        CodeOwnerSet.createWithoutPathExpressions(admin.email()),
                        CodeOwnerSet.createWithoutPathExpressions(user.email()))));
  }

  @Test
  public void removeFromOnlySetByEmail() throws Exception {
    assertThat(
            CodeOwnerSetModification.removeFromOnlySet(user.email())
                .apply(
                    ImmutableList.of(
                        CodeOwnerSet.createWithoutPathExpressions(admin.email(), user.email()))))
        .containsExactly(CodeOwnerSet.createWithoutPathExpressions(admin.email()));
  }

  @Test
  public void removeFromOnlySetByEmailFailsIfThereIsNoSet() throws Exception {
    assertThrows(
        NoSuchElementException.class,
        () -> CodeOwnerSetModification.removeFromOnlySet(user.email()).apply(ImmutableList.of()));
  }

  @Test
  public void removeFromOnlySetByEmailFailsIfThereAreMultipleSets() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CodeOwnerSetModification.removeFromOnlySet(user.email())
                .apply(
                    ImmutableList.of(
                        CodeOwnerSet.createWithoutPathExpressions(admin.email()),
                        CodeOwnerSet.createWithoutPathExpressions(user.email()))));
  }

  @Test
  public void removeFromCodeOwnerSet() throws Exception {
    CodeOwnerSet codeOwnerSet =
        CodeOwnerSet.createWithoutPathExpressions(admin.email(), user.email());
    assertThat(
            CodeOwnerSetModification.removeFromCodeOwnerSet(
                codeOwnerSet, CodeOwnerReference.create(user.email())))
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email());
  }

  @Test
  public void removeFromCodeOwnerSetThatDoesntTheCodeOwner() throws Exception {
    CodeOwnerSet codeOwnerSet = CodeOwnerSet.createWithoutPathExpressions(admin.email());
    assertThat(
            CodeOwnerSetModification.removeFromCodeOwnerSet(
                codeOwnerSet, CodeOwnerReference.create(user.email())))
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email());
  }
}
