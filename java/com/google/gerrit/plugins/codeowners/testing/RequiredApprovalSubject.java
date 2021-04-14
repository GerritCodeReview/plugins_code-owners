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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.backend.config.RequiredApproval;
import com.google.gerrit.truth.ListSubject;

/** {@link Subject} for doing assertions on {@link RequiredApproval}s. */
public class RequiredApprovalSubject extends Subject {
  /**
   * Starts a fluent chain to do assertions on a {@link RequiredApproval}.
   *
   * @param requiredApproval the required approval on which assertions should be done
   * @return the created {@link RequiredApprovalSubject}
   */
  public static RequiredApprovalSubject assertThat(RequiredApproval requiredApproval) {
    return assertAbout(requiredApprovals()).that(requiredApproval);
  }

  /**
   * Starts a fluent chain to do assertions on a list of {@link RequiredApproval}s.
   *
   * @param requiredApprovals list of required approvals on which assertions should be done
   * @return the created {@link ListSubject}
   */
  public static ListSubject<RequiredApprovalSubject, RequiredApproval> assertThat(
      ImmutableList<RequiredApproval> requiredApprovals) {
    return ListSubject.assertThat(requiredApprovals, requiredApprovals());
  }

  /**
   * Starts a fluent chain to do assertions on a sorted set of {@link RequiredApproval}s.
   *
   * @param requiredApprovals sorted set of required approvals on which assertions should be done
   * @return the created {@link ListSubject}
   */
  public static ListSubject<RequiredApprovalSubject, RequiredApproval> assertThat(
      ImmutableSortedSet<RequiredApproval> requiredApprovals) {
    return assertThat(requiredApprovals.asList());
  }

  /**
   * Creates a subject factory for mapping {@link RequiredApproval}s to {@link
   * RequiredApprovalSubject}s.
   */
  private static Subject.Factory<RequiredApprovalSubject, RequiredApproval> requiredApprovals() {
    return RequiredApprovalSubject::new;
  }

  private final RequiredApproval requiredApproval;

  private RequiredApprovalSubject(FailureMetadata metadata, RequiredApproval requiredApproval) {
    super(metadata, requiredApproval);
    this.requiredApproval = requiredApproval;
  }

  /** Returns a subject for the label name. */
  public StringSubject hasLabelNameThat() {
    return check("labelName()").that(requiredApproval().labelType().getName());
  }

  /** Returns a subject for the value. */
  public IntegerSubject hasValueThat() {
    return check("value()").that((int) requiredApproval().value());
  }

  private RequiredApproval requiredApproval() {
    isNotNull();
    return requiredApproval;
  }
}
