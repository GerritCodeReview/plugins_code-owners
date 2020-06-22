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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigInfoSubject.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerSetInfoSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerConfigInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerReferenceInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerSetInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import java.util.Optional;
import org.junit.Test;

/** Tests for {@link CodeOwnerConfigJson}. */
public class CodeOwnerConfigJsonTest extends AbstractCodeOwnersTest {
  @Test
  public void formatCodeOwnerReference() throws Exception {
    CodeOwnerReference codeOwnerReference = CodeOwnerReference.create(admin.email());
    CodeOwnerReferenceInfo codeOwnerReferenceInfo = CodeOwnerConfigJson.format(codeOwnerReference);
    assertThat(codeOwnerReferenceInfo.email).isEqualTo(admin.email());
  }

  @Test
  public void cannotFormatNullCodeOwnerReference() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> CodeOwnerConfigJson.format((CodeOwnerReference) null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerReference");
  }

  @Test
  public void formatCodeOwnerSet() throws Exception {
    CodeOwnerSet codeOwnerSet = CodeOwnerSet.createWithoutPathExpressions(admin.email());
    Optional<CodeOwnerSetInfo> codeOwnerSetInfo = CodeOwnerConfigJson.format(codeOwnerSet);
    assertThat(codeOwnerSetInfo).isPresent();
    assertThat(codeOwnerSetInfo.get()).hasCodeOwnersEmailsThat().containsExactly(admin.email());
  }

  @Test
  public void formatEmptyCodeOwnerSet() throws Exception {
    assertThat(
            CodeOwnerConfigJson.format(
                CodeOwnerSet.createWithoutPathExpressions(ImmutableSet.of())))
        .isEmpty();
  }

  @Test
  public void cannotFormatNullCodeOwnerSet() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> CodeOwnerConfigJson.format((CodeOwnerSet) null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerSet");
  }

  @Test
  public void cannotFormatNullCodeOwnerConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> CodeOwnerConfigJson.format((CodeOwnerConfig) null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfig");
  }

  @Test
  public void formatCodeOwnerConfigWithCodeOwnerSet() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"))
            .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email(), user.email()))
            .build();
    CodeOwnerConfigInfo codeOwnerConfigInfo = CodeOwnerConfigJson.format(codeOwnerConfig);
    assertThat(codeOwnerConfigInfo)
        .hasExactlyOneCodeOwnerSetThat()
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email(), user.email());
  }

  @Test
  public void formatCodeOwnerConfigWithoutCodeOwnerSet() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"))
            .setIgnoreParentCodeOwners()
            .build();
    CodeOwnerConfigInfo codeOwnerConfigInfo = CodeOwnerConfigJson.format(codeOwnerConfig);
    assertThat(codeOwnerConfigInfo).hasCodeOwnerSetsThat().isNull();
  }

  @Test
  public void formatCodeOwnerConfigWithIgnoreParentCodeOwners() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"))
            .setIgnoreParentCodeOwners()
            .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
            .build();
    CodeOwnerConfigInfo codeOwnerConfigInfo = CodeOwnerConfigJson.format(codeOwnerConfig);
    assertThat(codeOwnerConfigInfo).hasIgnoreParentCodeOwnersThat().isTrue();
    assertThat(codeOwnerConfigInfo)
        .hasExactlyOneCodeOwnerSetThat()
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email());
  }

  @Test
  public void formatCodeOwnerConfigWithOnlyIgnoreParentCodeOwners() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"))
            .setIgnoreParentCodeOwners()
            .build();
    CodeOwnerConfigInfo codeOwnerConfigInfo = CodeOwnerConfigJson.format(codeOwnerConfig);
    assertThat(codeOwnerConfigInfo).hasIgnoreParentCodeOwnersThat().isTrue();
    assertThat(codeOwnerConfigInfo).hasCodeOwnerSetsThat().isNull();
  }

  @Test
  public void formatCodeOwnerConfigWithoutIgnoreParentCodeOwners() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"))
            .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
            .build();
    CodeOwnerConfigInfo codeOwnerConfigInfo = CodeOwnerConfigJson.format(codeOwnerConfig);
    assertThat(codeOwnerConfigInfo).hasIgnoreParentCodeOwnersThat().isNull();
    assertThat(codeOwnerConfigInfo)
        .hasExactlyOneCodeOwnerSetThat()
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email());
  }
}
