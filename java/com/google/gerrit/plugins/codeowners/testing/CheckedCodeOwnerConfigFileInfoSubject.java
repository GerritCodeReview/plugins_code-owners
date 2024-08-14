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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigFileInfoSubject.codeOwnerConfigFileInfos;

import com.google.common.truth.BooleanSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.api.CheckedCodeOwnerConfigFileInfo;

public class CheckedCodeOwnerConfigFileInfoSubject extends Subject {
  /**
   * Starts fluent chain to do assertions on a {@link CheckedCodeOwnerConfigFileInfo}.
   *
   * @param checkedCodeOwnerConfigFileInfo the checked code owner config file info on which
   *     assertions should be done
   * @return the created {@link CheckedCodeOwnerConfigFileInfoSubject}
   */
  public static CheckedCodeOwnerConfigFileInfoSubject assertThat(
      CheckedCodeOwnerConfigFileInfo checkedCodeOwnerConfigFileInfo) {
    return assertAbout(checkedCodeOwnerConfigFileInfos()).that(checkedCodeOwnerConfigFileInfo);
  }

  public static Factory<CheckedCodeOwnerConfigFileInfoSubject, CheckedCodeOwnerConfigFileInfo>
      checkedCodeOwnerConfigFileInfos() {
    return CheckedCodeOwnerConfigFileInfoSubject::new;
  }

  private final CheckedCodeOwnerConfigFileInfo checkedCodeOwnerConfigFileInfo;

  private CheckedCodeOwnerConfigFileInfoSubject(
      FailureMetadata metadata, CheckedCodeOwnerConfigFileInfo checkedCodeOwnerConfigFileInfo) {
    super(metadata, checkedCodeOwnerConfigFileInfo);
    this.checkedCodeOwnerConfigFileInfo = checkedCodeOwnerConfigFileInfo;
  }

  public CodeOwnerConfigFileInfoSubject hasCodeOwnerConfigFileThat() {
    return check("codeOwnerConfig")
        .about(codeOwnerConfigFileInfos())
        .that(checkedCodeOwnerConfigFileInfo().codeOwnerConfigFileInfo);
  }

  public BooleanSubject hasAssignsCodeOwnershipToUserThat() {
    return check("assignsCodeOwnershipToUser()")
        .that(checkedCodeOwnerConfigFileInfo().assignsCodeOwnershipToUser);
  }

  public CheckedCodeOwnerConfigFileInfoSubject assignsCodeOwnershipToUser() {
    hasAssignsCodeOwnershipToUserThat().isTrue();
    return this;
  }

  public CheckedCodeOwnerConfigFileInfoSubject doesNotAssignCodeOwnershipToUser() {
    hasAssignsCodeOwnershipToUserThat().isFalse();
    return this;
  }

  private CheckedCodeOwnerConfigFileInfo checkedCodeOwnerConfigFileInfo() {
    isNotNull();
    return checkedCodeOwnerConfigFileInfo;
  }
}
