// Copyright (C) 2024 The Android Open Source Project
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

import com.google.common.truth.BooleanSubject;
import com.google.common.truth.FailureMetadata;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerConfigFileWithCheckResultsInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig.Key;

public class CodeOwnerConfigFileWithCheckResultsInfoSubject extends CodeOwnerConfigFileInfoSubject {
  /**
   * Starts fluent chain to do assertions on a {@link CodeOwnerConfigFileWithCheckResultsInfo}.
   *
   * @param codeOwnerConfigFileWithCheckResultsInfo the code owner config file info on which
   *     assertions should be done
   * @return the created {@link CodeOwnerConfigFileWithCheckResultsInfoSubject}
   */
  public static CodeOwnerConfigFileWithCheckResultsInfoSubject assertThat(
      CodeOwnerConfigFileWithCheckResultsInfo codeOwnerConfigFileWithCheckResultsInfo) {
    return assertAbout(codeOwnerConfigFileWithCheckResultsInfos())
        .that(codeOwnerConfigFileWithCheckResultsInfo);
  }

  public static Factory<
          CodeOwnerConfigFileWithCheckResultsInfoSubject, CodeOwnerConfigFileWithCheckResultsInfo>
      codeOwnerConfigFileWithCheckResultsInfos() {
    return CodeOwnerConfigFileWithCheckResultsInfoSubject::new;
  }

  private final CodeOwnerConfigFileWithCheckResultsInfo codeOwnerConfigFileWithCheckResultsInfo;

  private CodeOwnerConfigFileWithCheckResultsInfoSubject(
      FailureMetadata metadata,
      CodeOwnerConfigFileWithCheckResultsInfo codeOwnerConfigFileWithCheckResultsInfo) {
    super(metadata, codeOwnerConfigFileWithCheckResultsInfo);
    this.codeOwnerConfigFileWithCheckResultsInfo = codeOwnerConfigFileWithCheckResultsInfo;
  }

  @Override
  public CodeOwnerConfigFileWithCheckResultsInfoSubject assertKey(
      CodeOwnerBackend codeOwnerBackend, Key codeOwnerConfigKey) {
    super.assertKey(codeOwnerBackend, codeOwnerConfigKey);
    return this;
  }

  public BooleanSubject hasAssignsCodeOwnershipToUserThat() {
    return check("assignsCodeOwnershipToUser()")
        .that(codeOwnerConfigFileInfoWithCheckInfo().assignsCodeOwnershipToUser);
  }

  public CodeOwnerConfigFileWithCheckResultsInfoSubject assertAssignsCodeOwnershipToUser() {
    hasAssignsCodeOwnershipToUserThat().isTrue();
    return this;
  }

  public CodeOwnerConfigFileWithCheckResultsInfoSubject assertDoesNotAssignCodeOwnershipToUser() {
    hasAssignsCodeOwnershipToUserThat().isFalse();
    return this;
  }

  private CodeOwnerConfigFileWithCheckResultsInfo codeOwnerConfigFileInfoWithCheckInfo() {
    isNotNull();
    return codeOwnerConfigFileWithCheckResultsInfo;
  }
}
