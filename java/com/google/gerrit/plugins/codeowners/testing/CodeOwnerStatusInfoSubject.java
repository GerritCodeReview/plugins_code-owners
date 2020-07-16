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
import static com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusInfoSubject.fileCodeOwnerStatusInfos;
import static com.google.gerrit.truth.ListSubject.elements;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerStatusInfo;
import com.google.gerrit.plugins.codeowners.api.FileCodeOwnerStatusInfo;
import com.google.gerrit.truth.ListSubject;

/** {@link Subject} for doing assertions on {@link CodeOwnerStatusInfo}s. */
public class CodeOwnerStatusInfoSubject extends Subject {
  /**
   * Starts fluent chain to do assertions on a {@link CodeOwnerStatusInfo}.
   *
   * @param codeOwnerStatusInfo the {@link CodeOwnerStatusInfo} on which assertions should be done
   * @return the created {@link CodeOwnerStatusInfoSubject}
   */
  public static CodeOwnerStatusInfoSubject assertThat(CodeOwnerStatusInfo codeOwnerStatusInfo) {
    return assertAbout(codeOwnerStatusInfos()).that(codeOwnerStatusInfo);
  }

  private static Factory<CodeOwnerStatusInfoSubject, CodeOwnerStatusInfo> codeOwnerStatusInfos() {
    return CodeOwnerStatusInfoSubject::new;
  }

  private final CodeOwnerStatusInfo codeOwnerStatusInfo;

  private CodeOwnerStatusInfoSubject(
      FailureMetadata metadata, CodeOwnerStatusInfo codeOwnerStatusInfo) {
    super(metadata, codeOwnerStatusInfo);
    this.codeOwnerStatusInfo = codeOwnerStatusInfo;
  }

  /** Returns a subject for the change type. */
  public IntegerSubject hasPatchSetNumberThat() {
    return check("patchSetNumber()").that(codeOwnerStatusInfo().patchSetNumber);
  }

  /** Returns a {@link ListSubject} for the file code owner statuses. */
  public ListSubject<FileCodeOwnerStatusInfoSubject, FileCodeOwnerStatusInfo>
      hasFileCodeOwnerStatusesThat() {
    return check("fileCodeOwnerStatuses")
        .about(elements())
        .thatCustom(codeOwnerStatusInfo().fileCodeOwnerStatuses, fileCodeOwnerStatusInfos());
  }

  private CodeOwnerStatusInfo codeOwnerStatusInfo() {
    isNotNull();
    return codeOwnerStatusInfo;
  }
}
