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

import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.truth.ListSubject;
import java.util.Collection;

/** {@link Subject} for doing assertions on {@link SubmitRequirementInfo}s. */
public class SubmitRequirementInfoSubject extends Subject {
  public static ListSubject<SubmitRequirementInfoSubject, SubmitRequirementInfo>
      assertThatCollection(Collection<SubmitRequirementInfo> submitRequirementInfos) {
    return ListSubject.assertThat(
        ImmutableList.copyOf(submitRequirementInfos), submitRecordRequirementInfos());
  }

  private static Factory<SubmitRequirementInfoSubject, SubmitRequirementInfo>
      submitRecordRequirementInfos() {
    return SubmitRequirementInfoSubject::new;
  }

  private final SubmitRequirementInfo submitRequirementInfo;

  private SubmitRequirementInfoSubject(
      FailureMetadata metadata, SubmitRequirementInfo submitRequirementInfo) {
    super(metadata, submitRequirementInfo);
    this.submitRequirementInfo = submitRequirementInfo;
  }

  /** Returns a subject for the type. */
  public StringSubject hasStatusThat() {
    return check("status()").that(submitRequirementInfo().status);
  }

  /** Returns a subject for the type. */
  public StringSubject hasTypeThat() {
    return check("type()").that(submitRequirementInfo().type);
  }

  /** Returns a subject for the type. */
  public StringSubject hasFallbackTextThat() {
    return check("fallbackText()").that(submitRequirementInfo().fallbackText);
  }

  private SubmitRequirementInfo submitRequirementInfo() {
    isNotNull();
    return submitRequirementInfo;
  }
}
