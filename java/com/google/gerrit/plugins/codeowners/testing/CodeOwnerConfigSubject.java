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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigReferenceSubject.codeOwnerConfigReferences;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerSetSubject.codeOwnerSets;
import static com.google.gerrit.truth.ListSubject.elements;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.BooleanSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.truth.ListSubject;
import com.google.gerrit.truth.OptionalSubject;
import java.util.Optional;

/** {@link Subject} for doing assertions on {@link CodeOwnerConfig}s. */
public class CodeOwnerConfigSubject extends Subject {
  /**
   * Starts fluent chain to do assertions on a {@link CodeOwnerConfig}.
   *
   * @param codeOwnerConfig the code owner config on which assertions should be done
   * @return the created {@link CodeOwnerConfigSubject}
   */
  public static CodeOwnerConfigSubject assertThat(CodeOwnerConfig codeOwnerConfig) {
    return assertAbout(codeOwnerConfigs()).that(codeOwnerConfig);
  }

  /**
   * Starts fluent chain to do assertions on an {@link Optional} {@link CodeOwnerConfig}.
   *
   * @param codeOwnerConfig the code owner config {@link Optional} on which assertions should be
   *     done
   * @return the created {@link OptionalSubject}
   */
  public static OptionalSubject<CodeOwnerConfigSubject, CodeOwnerConfig> assertThatOptional(
      Optional<CodeOwnerConfig> codeOwnerConfig) {
    return OptionalSubject.assertThat(codeOwnerConfig, codeOwnerConfigs());
  }

  private static Factory<CodeOwnerConfigSubject, CodeOwnerConfig> codeOwnerConfigs() {
    return CodeOwnerConfigSubject::new;
  }

  private final CodeOwnerConfig codeOwnerConfig;

  private CodeOwnerConfigSubject(FailureMetadata metadata, CodeOwnerConfig codeOwnerConfig) {
    super(metadata, codeOwnerConfig);
    this.codeOwnerConfig = codeOwnerConfig;
  }

  /** Returns a subject for the revision. */
  public Subject hasRevisionThat() {
    return check("revision()").that(codeOwnerConfig().revision());
  }

  /**
   * Returns an {@link ListSubject} for the code owners in the code owner config.
   *
   * @return {@link ListSubject} for the code owners in the code owner config
   */
  public ListSubject<CodeOwnerSetSubject, CodeOwnerSet> hasCodeOwnerSetsThat() {
    return check("codeOwnerSets()")
        .about(elements())
        .thatCustom(codeOwnerConfig().codeOwnerSetsAsList(), codeOwnerSets());
  }

  /**
   * Returns an {@link ListSubject} for the code owner config references in the code owner config.
   *
   * @return {@link ListSubject} for the code owner config references in the code owner config
   */
  public ListSubject<CodeOwnerConfigReferenceSubject, CodeOwnerConfigReference> hasImportsThat() {
    return check("imports()")
        .about(elements())
        .thatCustom(ImmutableList.copyOf(codeOwnerConfig().imports()), codeOwnerConfigReferences());
  }

  /**
   * Returns a subject for the ignore parent code owners flag in the code owner config.
   *
   * @return a subject for the ignore parent code owners flag in the code owner config
   */
  public BooleanSubject hasIgnoreParentCodeOwnersThat() {
    return check("ignoreParentCodeOwners()").that(codeOwnerConfig().ignoreParentCodeOwners());
  }

  private CodeOwnerConfig codeOwnerConfig() {
    isNotNull();
    return codeOwnerConfig;
  }
}
