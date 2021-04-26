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

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.entities.LegacySubmitRequirement;

/** {@link Subject} for doing assertions on {@link LegacySubmitRequirement}s. */
public class LegacySubmitRequirementSubject extends Subject {
  public static Factory<LegacySubmitRequirementSubject, LegacySubmitRequirement>
      submitRecordRequirements() {
    return LegacySubmitRequirementSubject::new;
  }

  private final LegacySubmitRequirement submitRequirement;

  private LegacySubmitRequirementSubject(
      FailureMetadata metadata, LegacySubmitRequirement submitRequirement) {
    super(metadata, submitRequirement);
    this.submitRequirement = submitRequirement;
  }

  /** Returns a subject for the type. */
  public StringSubject hasTypeThat() {
    return check("type()").that(submitRequirement().type());
  }

  /** Returns a subject for the type. */
  public StringSubject hasFallbackTextThat() {
    return check("fallbackText()").that(submitRequirement().fallbackText());
  }

  private LegacySubmitRequirement submitRequirement() {
    isNotNull();
    return submitRequirement;
  }
}
