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

package com.google.gerrit.plugins.codeowners.backend;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

/** Submit rule that checks that all files in a change have been approved by their code owners. */
@Singleton
class CodeOwnerSubmitRule implements SubmitRule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Module extends FactoryModule {
    @Override
    public void configure() {
      bind(SubmitRule.class)
          .annotatedWith(Exports.named("CodeOwnerSubmitRule"))
          .to(CodeOwnerSubmitRule.class);
    }
  }

  private static final SubmitRequirement NOT_READY_SUBMIT_REQUIREMENT =
      SubmitRequirement.builder()
          .setFallbackText("All files must be approved by a code owner")
          .setType("code-owners")
          .build();

  private final CodeOwnerApprovalCheck codeOwnerApprovalCheck;

  @Inject
  CodeOwnerSubmitRule(CodeOwnerApprovalCheck codeOwnerApprovalCheck) {
    this.codeOwnerApprovalCheck = codeOwnerApprovalCheck;
  }

  @Override
  public Optional<SubmitRecord> evaluate(ChangeData changeData) {
    try {
      requireNonNull(changeData, "changeData");
      return Optional.of(getSubmitRecord(changeData.notes()));
    } catch (Throwable t) {
      String errorMessage = "Failed to evaluate code owner statuses";
      if (changeData != null) {
        errorMessage +=
            String.format(
                " for patch set %d of change %d",
                changeData.currentPatchSet().id().get(), changeData.change().getId().get());
      }
      errorMessage += ".";
      logger.atSevere().withCause(t).log(errorMessage);
      return Optional.of(ruleError(errorMessage));
    }
  }

  private SubmitRecord getSubmitRecord(ChangeNotes changeNotes) throws IOException {
    requireNonNull(changeNotes, "changeNotes");
    return codeOwnerApprovalCheck.isSubmittable(changeNotes) ? ok() : notReady();
  }

  private static SubmitRecord ok() {
    SubmitRecord submitRecord = new SubmitRecord();
    submitRecord.status = SubmitRecord.Status.OK;
    return submitRecord;
  }

  private static SubmitRecord notReady() {
    SubmitRecord submitRecord = new SubmitRecord();
    submitRecord.status = SubmitRecord.Status.NOT_READY;
    submitRecord.requirements = ImmutableList.of(NOT_READY_SUBMIT_REQUIREMENT);
    return submitRecord;
  }

  private static SubmitRecord ruleError(String reason) {
    SubmitRecord submitRecord = new SubmitRecord();
    submitRecord.errorMessage = reason;
    submitRecord.status = SubmitRecord.Status.RULE_ERROR;
    return submitRecord;
  }
}
