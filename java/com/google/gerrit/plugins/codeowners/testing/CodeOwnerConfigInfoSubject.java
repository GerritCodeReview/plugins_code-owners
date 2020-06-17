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

import com.google.common.truth.BooleanSubject;
import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerConfigInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerReferenceInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.restapi.CodeOwnerConfigJson;
import java.util.Objects;
import java.util.Optional;

/** {@link Subject} for doing assertions on {@link CodeOwnerConfigInfo}s. */
public class CodeOwnerConfigInfoSubject extends Subject {
  /** {@link Correspondence} that maps {@link CodeOwnerReference}s to emails. */
  private static final Correspondence<CodeOwnerReferenceInfo, String>
      CODE_OWNER_REFERENCE_INFO_TO_EMAIL =
          Correspondence.from(
              (actualCodeOwnerReferenceInfo, expectedEmail) -> {
                String email =
                    Optional.ofNullable(actualCodeOwnerReferenceInfo)
                        .map(codeOwnerReference -> codeOwnerReference.email)
                        .orElse(null);
                return Objects.equals(email, expectedEmail);
              },
              "has email");

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
   * Returns an {@link IterableSubject} for the code owners in the {@link CodeOwnerConfigInfo}.
   *
   * @return {@link IterableSubject} for the code owners in the {@link CodeOwnerConfigInfo}
   */
  public IterableSubject hasCodeOwnersThat() {
    return check("codeOwners()").that(codeOwnerConfigInfo().codeOwners);
  }

  /**
   * Returns an {@link IterableSubject} for the code owner emails in the {@link
   * CodeOwnerConfigInfo}.
   *
   * @return {@link IterableSubject} for the code owner emails in the {@link CodeOwnerConfigInfo}
   */
  public IterableSubject.UsingCorrespondence<CodeOwnerReferenceInfo, String>
      hasCodeOwnersEmailsThat() {
    return hasCodeOwnersThat().comparingElementsUsing(CODE_OWNER_REFERENCE_INFO_TO_EMAIL);
  }

  /**
   * Returns a subject for the ignore parent code owners flag in the {@link CodeOwnerConfigInfo}.
   *
   * @return a subject for the ignore parent code owners flag in the {@link CodeOwnerConfigInfo}
   */
  public BooleanSubject hasIgnoreParentCodeOwnersThat() {
    return check("ignoreParentCodeOwners()").that(codeOwnerConfigInfo().ignoreParentCodeOwners);
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
