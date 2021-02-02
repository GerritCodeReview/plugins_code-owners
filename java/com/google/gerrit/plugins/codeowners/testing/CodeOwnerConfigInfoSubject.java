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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerSetInfoSubject.codeOwnerSetInfos;
import static com.google.gerrit.truth.ListSubject.elements;

import com.google.common.truth.BooleanSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerConfigInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerSetInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.truth.ListSubject;
import com.google.gerrit.truth.NullAwareCorrespondence;
import com.google.gerrit.truth.OptionalSubject;
import java.util.List;
import java.util.Optional;

/** {@link Subject} for doing assertions on {@link CodeOwnerConfigInfo}s. */
public class CodeOwnerConfigInfoSubject extends Subject {
  /**
   * Starts fluent chain to do assertions on a {@link CodeOwnerConfigInfo}.
   *
   * @param codeOwnerConfigInfo the {@link CodeOwnerConfigInfo} on which assertions should be done
   * @return the created {@link CodeOwnerConfigInfoSubject}
   */
  public static CodeOwnerConfigInfoSubject assertThat(CodeOwnerConfigInfo codeOwnerConfigInfo) {
    return assertAbout(codeOwnerConfigInfos()).that(codeOwnerConfigInfo);
  }

  /**
   * Starts fluent chain to do assertions on an {@link Optional} {@link CodeOwnerConfigInfo}.
   *
   * @param codeOwnerConfigInfo the {@link CodeOwnerConfigInfo} {@link Optional} on which assertions
   *     should be done
   * @return the created {@link OptionalSubject}
   */
  public static OptionalSubject<CodeOwnerConfigInfoSubject, CodeOwnerConfigInfo> assertThatOptional(
      Optional<CodeOwnerConfigInfo> codeOwnerConfigInfo) {
    return OptionalSubject.assertThat(codeOwnerConfigInfo, codeOwnerConfigInfos());
  }

  private static Factory<CodeOwnerConfigInfoSubject, CodeOwnerConfigInfo> codeOwnerConfigInfos() {
    return CodeOwnerConfigInfoSubject::new;
  }

  private final CodeOwnerConfigInfo codeOwnerConfigInfo;

  private CodeOwnerConfigInfoSubject(
      FailureMetadata metadata, CodeOwnerConfigInfo codeOwnerConfigInfo) {
    super(metadata, codeOwnerConfigInfo);
    this.codeOwnerConfigInfo = codeOwnerConfigInfo;
  }

  /**
   * Returns an {@link ListSubject} for the code owners in the {@link CodeOwnerConfigInfo}.
   *
   * @return {@link ListSubject} for the code owners in the {@link CodeOwnerConfigInfo}
   */
  public ListSubject<CodeOwnerSetInfoSubject, CodeOwnerSetInfo> hasCodeOwnerSetsThat() {
    return check("codeOwnerSets()")
        .about(elements())
        .thatCustom(codeOwnerConfigInfo().codeOwnerSets, codeOwnerSetInfos());
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
    hasIgnoreParentCodeOwnersThat().isEqualTo(codeOwnerConfig.ignoreParentCodeOwners());
    hasCodeOwnerSetsThat()
        .comparingElementsUsing(
            NullAwareCorrespondence.<CodeOwnerSetInfo, List<String>>transforming(
                codeOwnerSetInfo ->
                    codeOwnerSetInfo.codeOwners.stream()
                        .map(codeOwnerReferenceInfo -> codeOwnerReferenceInfo.email)
                        .collect(toImmutableList()),
                "has code owners"))
        .containsExactly(
            codeOwnerConfig.codeOwnerSets().stream()
                .flatMap(
                    codeOwnerSet ->
                        codeOwnerSet.codeOwners().stream().map(CodeOwnerReference::email))
                .collect(toImmutableList()));
  }

  private CodeOwnerConfigInfo codeOwnerConfigInfo() {
    isNotNull();
    return codeOwnerConfigInfo;
  }
}
