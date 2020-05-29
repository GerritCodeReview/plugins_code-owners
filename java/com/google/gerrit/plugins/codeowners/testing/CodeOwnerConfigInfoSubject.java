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

package com.google.gerrit.plugins.codeowners.testing;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerConfigInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.restapi.CodeOwnerConfigJson;

/** {@link Subject} for doing assertions on {@link CodeOwnerConfigInfo}s. */
public class CodeOwnerConfigInfoSubject extends Subject {
  /**
   * Starts fluent chain to do assertions on a {@link CodeOwnerConfigInfo}.
   *
   * @param codeOwnerConfigInfo the code owner config on which assertions should be done
   * @return the created {@link CodeOwnerConfigInfoSubject}
   */
  public static CodeOwnerConfigInfoSubject assertThat(CodeOwnerConfigInfo codeOwnerConfigInfo) {
    return assertAbout(CodeOwnerConfigInfoSubject::new).that(codeOwnerConfigInfo);
  }

  private final CodeOwnerConfigInfo codeOwnerConfigInfo;

  private CodeOwnerConfigInfoSubject(
      FailureMetadata metadata, CodeOwnerConfigInfo codeOwnerConfigInfo) {
    super(metadata, codeOwnerConfigInfo);
    this.codeOwnerConfigInfo = codeOwnerConfigInfo;
  }

  /**
   * Checks whether the given {@link CodeOwnerConfig} corresponds to the {@link CodeOwnerConfigInfo}
   * of this subject.
   *
   * @param codeOwnerConfig the code owner config for which it should be checked whether it
   *     corresponds to the {@link CodeOwnerConfigInfo} of this subject
   */
  public void correspondsTo(CodeOwnerConfig codeOwnerConfig) {
    check("codeOwnerInfo()")
        .that(codeOwnerConfigInfo())
        .isEqualTo(CodeOwnerConfigJson.format(codeOwnerConfig));
  }

  private CodeOwnerConfigInfo codeOwnerConfigInfo() {
    isNotNull();
    return codeOwnerConfigInfo;
  }
}
