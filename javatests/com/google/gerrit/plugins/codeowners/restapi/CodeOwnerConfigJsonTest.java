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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigInfoSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
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
  public void cannotFormatNullCodeOwnerReferenceInfo() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> CodeOwnerConfigJson.format((CodeOwnerReference) null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerReference");
  }

  @Test
  public void formatCodeOwnerConfig() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"))
            .addCodeOwnerEmail(admin.email())
            .build();
    CodeOwnerConfigInfo codeOwnerConfigInfo = CodeOwnerConfigJson.format(codeOwnerConfig);
    assertThat(codeOwnerConfigInfo).hasCodeOwnersEmailsThat().containsExactly(admin.email());
  }

  @Test
  public void cannotFormatNullCodeOwnerConfigInfo() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> CodeOwnerConfigJson.format((CodeOwnerConfig) null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfig");
  }
}
