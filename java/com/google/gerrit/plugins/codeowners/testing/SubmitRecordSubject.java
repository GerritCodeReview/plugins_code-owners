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

import static com.google.gerrit.plugins.codeowners.testing.LegacySubmitRequirementSubject.submitRecordRequirements;
import static com.google.gerrit.truth.ListSubject.elements;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.entities.LegacySubmitRequirement;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.truth.ListSubject;
import com.google.gerrit.truth.OptionalSubject;
import java.util.Optional;

/** {@link Subject} for doing assertions on {@link SubmitRecord}s. */
public class SubmitRecordSubject extends Subject {
  /**
   * Starts fluent chain to do assertions on an {@link Optional} {@link SubmitRecord}.
   *
   * @param submitRecord the {@link SubmitRecord} {@link Optional} on which assertions should be
   *     done
   * @return the created {@link OptionalSubject}
   */
  public static OptionalSubject<SubmitRecordSubject, SubmitRecord> assertThatOptional(
      Optional<SubmitRecord> submitRecord) {
    return OptionalSubject.assertThat(submitRecord, submitRecords());
  }

  private static Factory<SubmitRecordSubject, SubmitRecord> submitRecords() {
    return SubmitRecordSubject::new;
  }

  private final SubmitRecord submitRecord;

  private SubmitRecordSubject(FailureMetadata metadata, SubmitRecord submitRecord) {
    super(metadata, submitRecord);
    this.submitRecord = submitRecord;
  }

  /** Returns a subject for the status. */
  public StatusSubject hasStatusThat() {
    return check("status()")
        .about(StatusSubject.submitRecordStatuses())
        .that(submitRecord().status);
  }

  /** Returns a subject for the error message. */
  public StringSubject hasErrorMessageThat() {
    return check("errorMessage()").that(submitRecord().errorMessage);
  }

  /** Returns a {@link ListSubject} for the submit requirements. */
  public ListSubject<LegacySubmitRequirementSubject, LegacySubmitRequirement>
      hasSubmitRequirementsThat() {
    return check("submitRequirements()")
        .about(elements())
        .thatCustom(submitRecord().requirements, submitRecordRequirements());
  }

  private SubmitRecord submitRecord() {
    isNotNull();
    return submitRecord;
  }

  /**
   * {@link Subject} for doing assertions on {@link
   * com.google.gerrit.entities.SubmitRecord.Status}es.
   */
  public static class StatusSubject extends Subject {
    static Factory<StatusSubject, SubmitRecord.Status> submitRecordStatuses() {
      return StatusSubject::new;
    }

    private final SubmitRecord.Status status;

    private StatusSubject(FailureMetadata metadata, SubmitRecord.Status status) {
      super(metadata, status);
      this.status = status;
    }

    /** Checks whether the status is {@link com.google.gerrit.entities.SubmitRecord.Status#OK}. */
    public void isOk() {
      check("isOk()").that(status()).isEqualTo(SubmitRecord.Status.OK);
    }

    /**
     * Checks whether the status is {@link
     * com.google.gerrit.entities.SubmitRecord.Status#NOT_READY}.
     */
    public void isNotReady() {
      check("isNotReady()").that(status()).isEqualTo(SubmitRecord.Status.NOT_READY);
    }

    /**
     * Checks whether the status is {@link
     * com.google.gerrit.entities.SubmitRecord.Status#RULE_ERROR}.
     */
    public void isRuleError() {
      check("isRuleError()").that(status()).isEqualTo(SubmitRecord.Status.RULE_ERROR);
    }

    private SubmitRecord.Status status() {
      isNotNull();
      return status;
    }
  }
}
