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

package com.google.gerrit.plugins.codeowners.restapi;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoSubject.hasAccountId;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoSubject.hasAccountName;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoSubject.hasScoring;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwner;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerScore;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import java.util.EnumSet;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerJson}. */
public class CodeOwnerJsonTest extends AbstractCodeOwnersTest {
  private CodeOwnerJson.Factory codeOwnerJsonFactory;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerJsonFactory = plugin.getSysInjector().getInstance(CodeOwnerJson.Factory.class);
  }

  @Test
  public void cannotCreateInstanceWithEmptyAccountOptions() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> codeOwnerJsonFactory.create(ImmutableSet.of()));
    assertThat(exception).hasMessageThat().isEqualTo("account options must not be empty");
  }

  @Test
  public void formatEmptyListOfCodeOwners() throws Exception {
    assertThat(
            codeOwnerJsonFactory
                .create(EnumSet.of(FillOptions.ID))
                .format(ImmutableList.of(), ImmutableMap.of()))
        .isEmpty();
  }

  @Test
  public void formatCodeOwnersWithAccountId() throws Exception {
    ImmutableList<CodeOwnerInfo> codeOwnerInfos =
        codeOwnerJsonFactory
            .create(EnumSet.of(FillOptions.ID))
            .format(
                ImmutableList.of(CodeOwner.create(admin.id()), CodeOwner.create(user.id())),
                ImmutableMap.of());
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id())
        .inOrder();
    assertThat(codeOwnerInfos).comparingElementsUsing(hasAccountName()).containsExactly(null, null);
  }

  @Test
  public void formatCodeOwnersWithAccountIdAndName() throws Exception {
    ImmutableList<CodeOwnerInfo> codeOwnerInfos =
        codeOwnerJsonFactory
            .create(EnumSet.of(FillOptions.ID, FillOptions.NAME))
            .format(
                ImmutableList.of(CodeOwner.create(admin.id()), CodeOwner.create(user.id())),
                ImmutableMap.of());
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id())
        .inOrder();
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountName())
        .containsExactly(admin.fullName(), user.fullName())
        .inOrder();
  }

  @Test
  public void formatCodeOwnersWithAccountIdAndScorings() throws Exception {
    CodeOwner adminCodeOwner = CodeOwner.create(admin.id());
    CodeOwner userCodeOwner = CodeOwner.create(user.id());
    Integer adminOwnershipDistance = 1;
    Integer userOwnershipDistance = 2;
    ImmutableMap<CodeOwner, ImmutableMap<CodeOwnerScore, Integer>> scorings =
        ImmutableMap.<CodeOwner, ImmutableMap<CodeOwnerScore, Integer>>builder()
            .put(adminCodeOwner, ImmutableMap.of(CodeOwnerScore.DISTANCE, adminOwnershipDistance))
            .put(userCodeOwner, ImmutableMap.of(CodeOwnerScore.DISTANCE, userOwnershipDistance))
            .build();

    ImmutableList<CodeOwnerInfo> codeOwnerInfos =
        codeOwnerJsonFactory
            .create(EnumSet.of(FillOptions.ID))
            .format(ImmutableList.of(adminCodeOwner, userCodeOwner), scorings);
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id())
        .inOrder();
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasScoring(CodeOwnerScore.DISTANCE))
        .containsExactly(adminOwnershipDistance, userOwnershipDistance)
        .inOrder();
  }

  @Test
  public void cannotFormatNullCodeOwners() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwnerJsonFactory
                    .create(EnumSet.of(FillOptions.ID))
                    .format(/* codeOwners= */ null, ImmutableMap.of()));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwners");
  }
}
