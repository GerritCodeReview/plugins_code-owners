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

import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerReferenceInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerSetInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.truth.NullAwareCorrespondence;

/** {@link Subject} for doing assertions on {@link CodeOwnerSetInfo}s. */
public class CodeOwnerSetInfoSubject extends Subject {
  /** {@link Correspondence} that maps {@link CodeOwnerReference}s to emails. */
  private static final Correspondence<CodeOwnerReferenceInfo, String> hasEmail() {
    return NullAwareCorrespondence.transforming(
        codeOwnerReference -> codeOwnerReference.email, "has email");
  }

  /**
   * Starts fluent chain to do assertions on a {@link CodeOwnerSetInfo}.
   *
   * @param codeOwnerSetInfo the code owner set on which assertions should be done
   * @return the created {@link CodeOwnerSetInfoSubject}
   */
  public static CodeOwnerSetInfoSubject assertThat(CodeOwnerSetInfo codeOwnerSetInfo) {
    return assertAbout(CodeOwnerSetInfoSubject::new).that(codeOwnerSetInfo);
  }

  private final CodeOwnerSetInfo codeOwnerSetInfo;

  private CodeOwnerSetInfoSubject(FailureMetadata metadata, CodeOwnerSetInfo codeOwnerSetInfo) {
    super(metadata, codeOwnerSetInfo);
    this.codeOwnerSetInfo = codeOwnerSetInfo;
  }

  /**
   * Returns an {@link IterableSubject} for the code owners in the {@link CodeOwnerSetInfo}.
   *
   * @return {@link IterableSubject} for the code owners in the {@link CodeOwnerSetInfo}
   */
  public IterableSubject hasCodeOwnersThat() {
    return check("codeOwners()").that(codeOwnerSetInfo().codeOwners);
  }

  /**
   * Returns an {@link IterableSubject} for the code owner emails in the {@link CodeOwnerSetInfo}.
   *
   * @return {@link IterableSubject} for the code owner emails in the {@link CodeOwnerSetInfo}
   */
  public IterableSubject.UsingCorrespondence<CodeOwnerReferenceInfo, String>
      hasCodeOwnersEmailsThat() {
    return hasCodeOwnersThat().comparingElementsUsing(hasEmail());
  }

  private CodeOwnerSetInfo codeOwnerSetInfo() {
    isNotNull();
    return codeOwnerSetInfo;
  }
}
