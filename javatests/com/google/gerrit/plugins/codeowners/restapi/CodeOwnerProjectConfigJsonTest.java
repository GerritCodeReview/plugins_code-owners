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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.api.BackendInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerProjectConfigInfo;
import com.google.gerrit.plugins.codeowners.api.RequiredApprovalInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackendId;
import com.google.gerrit.plugins.codeowners.config.RequiredApproval;
import org.junit.Test;

/** Tests for {@link CodeOwnerProjectConfigJson}. */
public class CodeOwnerProjectConfigJsonTest extends AbstractCodeOwnersTest {
  @Test
  public void formatRequiredApproval() throws Exception {
    RequiredApproval requiredApproval =
        RequiredApproval.create(LabelType.withDefaultValues("Code-Review"), (short) 2);
    RequiredApprovalInfo requiredApprovalInfo =
        CodeOwnerProjectConfigJson.formatRequiredApprovalInfo(requiredApproval);
    assertThat(requiredApprovalInfo.label).isEqualTo("Code-Review");
    assertThat(requiredApprovalInfo.value).isEqualTo(2);
  }

  @Test
  public void cannotFormatNullRequiredApproval() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> CodeOwnerProjectConfigJson.formatRequiredApprovalInfo(null));
    assertThat(npe).hasMessageThat().isEqualTo("requiredApproval");
  }

  @Test
  public void formatBackendIds() throws Exception {
    BackendInfo backendInfo =
        CodeOwnerProjectConfigJson.formatBackendInfo(
            CodeOwnerBackendId.FIND_OWNERS.getBackendId(),
            ImmutableMap.of(
                BranchNameKey.create(project, "master"),
                CodeOwnerBackendId.FIND_OWNERS.getBackendId(),
                BranchNameKey.create(project, "stable-2.10"),
                CodeOwnerBackendId.PROTO.getBackendId()));
    assertThat(backendInfo.id).isEqualTo(CodeOwnerBackendId.FIND_OWNERS.getBackendId());
    // The entry for the master branch is omitted since its backend doesn't differ from the
    // project-level backend.
    assertThat(backendInfo.idsByBranch)
        .containsExactly("refs/heads/stable-2.10", CodeOwnerBackendId.PROTO.getBackendId());
  }

  @Test
  public void idsPerBranchNotSetIfThereIsNoBranchSpecificBackendConfiguration() throws Exception {
    BackendInfo backendInfo =
        CodeOwnerProjectConfigJson.formatBackendInfo(
            CodeOwnerBackendId.FIND_OWNERS.getBackendId(), ImmutableMap.of());
    assertThat(backendInfo.id).isEqualTo(CodeOwnerBackendId.FIND_OWNERS.getBackendId());
    assertThat(backendInfo.idsByBranch).isNull();
  }

  @Test
  public void cannotFormatBackendIdsForNullBackendId() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> CodeOwnerProjectConfigJson.formatBackendInfo(null, ImmutableMap.of()));
    assertThat(npe).hasMessageThat().isEqualTo("backendId");
  }

  @Test
  public void cannotFormatBackendIdsForNullPerBranchBackendIds() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                CodeOwnerProjectConfigJson.formatBackendInfo(
                    CodeOwnerBackendId.FIND_OWNERS.getBackendId(), null));
    assertThat(npe).hasMessageThat().isEqualTo("backendIdsPerBranch");
  }

  @Test
  public void formatCodeOwnerProjectConfig() throws Exception {
    CodeOwnerProjectConfigInfo codeOwnerProjectConfigInfo =
        CodeOwnerProjectConfigJson.format(
            true,
            ImmutableList.of(),
            CodeOwnerBackendId.FIND_OWNERS.getBackendId(),
            ImmutableMap.of(
                BranchNameKey.create(project, "master"),
                CodeOwnerBackendId.FIND_OWNERS.getBackendId(),
                BranchNameKey.create(project, "stable-2.10"),
                CodeOwnerBackendId.PROTO.getBackendId()),
            RequiredApproval.create(LabelType.withDefaultValues("Code-Review"), (short) 2));
    assertThat(codeOwnerProjectConfigInfo.status.disabled).isTrue();
    assertThat(codeOwnerProjectConfigInfo.status.disabledBranches).isNull();
    assertThat(codeOwnerProjectConfigInfo.backend.id)
        .isEqualTo(CodeOwnerBackendId.FIND_OWNERS.getBackendId());
    assertThat(codeOwnerProjectConfigInfo.backend.idsByBranch)
        .containsExactly("refs/heads/stable-2.10", CodeOwnerBackendId.PROTO.getBackendId());
    assertThat(codeOwnerProjectConfigInfo.requiredApproval.label).isEqualTo("Code-Review");
    assertThat(codeOwnerProjectConfigInfo.requiredApproval.value).isEqualTo(2);
  }

  @Test
  public void formatCodeOwnerProjectConfig_disabledBranchesNotSetIfDisabledOnProjectLevel()
      throws Exception {
    CodeOwnerProjectConfigInfo codeOwnerProjectConfigInfo =
        CodeOwnerProjectConfigJson.format(
            true,
            ImmutableList.of(BranchNameKey.create(project, "master")),
            CodeOwnerBackendId.FIND_OWNERS.getBackendId(),
            ImmutableMap.of(),
            RequiredApproval.create(LabelType.withDefaultValues("Code-Review"), (short) 2));
    assertThat(codeOwnerProjectConfigInfo.status.disabled).isTrue();
    assertThat(codeOwnerProjectConfigInfo.status.disabledBranches).isNull();
  }

  @Test
  public void formatCodeOwnerProjectConfig_statusNotSetIfEmpty() throws Exception {
    CodeOwnerProjectConfigInfo codeOwnerProjectConfigInfo =
        CodeOwnerProjectConfigJson.format(
            false,
            ImmutableList.of(),
            CodeOwnerBackendId.FIND_OWNERS.getBackendId(),
            ImmutableMap.of(),
            RequiredApproval.create(LabelType.withDefaultValues("Code-Review"), (short) 2));
    assertThat(codeOwnerProjectConfigInfo.status).isNull();
  }

  @Test
  public void formatCodeOwnerProjectConfig_withDisabledBranches() throws Exception {
    CodeOwnerProjectConfigInfo codeOwnerProjectConfigInfo =
        CodeOwnerProjectConfigJson.format(
            false,
            ImmutableList.of(BranchNameKey.create(project, "master")),
            CodeOwnerBackendId.FIND_OWNERS.getBackendId(),
            ImmutableMap.of(),
            RequiredApproval.create(LabelType.withDefaultValues("Code-Review"), (short) 2));
    assertThat(codeOwnerProjectConfigInfo.status.disabled).isNull();
    assertThat(codeOwnerProjectConfigInfo.status.disabledBranches)
        .containsExactly("refs/heads/master");
  }

  @Test
  public void cannotFormatCodeOwnerProjectConfigForNullDisabledBranches() throws Exception {
    RequiredApproval requiredApproval =
        RequiredApproval.create(LabelType.withDefaultValues("Code-Review"), (short) 2);
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                CodeOwnerProjectConfigJson.format(
                    false,
                    null,
                    CodeOwnerBackendId.FIND_OWNERS.getBackendId(),
                    ImmutableMap.of(),
                    requiredApproval));
    assertThat(npe).hasMessageThat().isEqualTo("disabledBranches");
  }

  @Test
  public void cannotFormatCodeOwnerProjectConfigForNullBackendId() throws Exception {
    RequiredApproval requiredApproval =
        RequiredApproval.create(LabelType.withDefaultValues("Code-Review"), (short) 2);
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                CodeOwnerProjectConfigJson.format(
                    false, ImmutableList.of(), null, ImmutableMap.of(), requiredApproval));
    assertThat(npe).hasMessageThat().isEqualTo("backendId");
  }

  @Test
  public void cannotFormatCodeOwnerProjectConfigForNullPerBranchBackendIds() throws Exception {
    RequiredApproval requiredApproval =
        RequiredApproval.create(LabelType.withDefaultValues("Code-Review"), (short) 2);
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                CodeOwnerProjectConfigJson.format(
                    false,
                    ImmutableList.of(),
                    CodeOwnerBackendId.FIND_OWNERS.getBackendId(),
                    null,
                    requiredApproval));
    assertThat(npe).hasMessageThat().isEqualTo("backendIdsPerBranch");
  }

  @Test
  public void cannotFormatCodeOwnerProjectConfigForNullRequiredApproval() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                CodeOwnerProjectConfigJson.format(
                    false,
                    ImmutableList.of(),
                    CodeOwnerBackendId.FIND_OWNERS.getBackendId(),
                    ImmutableMap.of(),
                    null));
    assertThat(npe).hasMessageThat().isEqualTo("requiredApproval");
  }
}
