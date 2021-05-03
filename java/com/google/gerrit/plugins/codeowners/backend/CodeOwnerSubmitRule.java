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
import com.google.gerrit.entities.LegacySubmitRequirement;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.Optional;

/** Submit rule that checks that all files in a change have been approved by their code owners. */
@Singleton
class CodeOwnerSubmitRule implements SubmitRule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Module extends AbstractModule {
    @Override
    public void configure() {
      bind(SubmitRule.class)
          .annotatedWith(Exports.named("CodeOwnerSubmitRule"))
          .to(CodeOwnerSubmitRule.class);
    }
  }

  private static final LegacySubmitRequirement SUBMIT_REQUIREMENT =
      LegacySubmitRequirement.builder()
          .setFallbackText("Code Owners")
          .setType("code-owners")
          .build();

  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final CodeOwnerApprovalCheck codeOwnerApprovalCheck;
  private final CodeOwnerMetrics codeOwnerMetrics;

  @Inject
  CodeOwnerSubmitRule(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerApprovalCheck codeOwnerApprovalCheck,
      CodeOwnerMetrics codeOwnerMetrics) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.codeOwnerApprovalCheck = codeOwnerApprovalCheck;
    this.codeOwnerMetrics = codeOwnerMetrics;
  }

  @Override
  public Optional<SubmitRecord> evaluate(ChangeData changeData) {
    try {
      requireNonNull(changeData, "changeData");

      if (changeData.change().isClosed()) {
        return Optional.empty();
      }

      try (Timer0.Context ctx = codeOwnerMetrics.runCodeOwnerSubmitRule.start()) {
        codeOwnerMetrics.countCodeOwnerSubmitRuleRuns.increment();
        logger.atFine().log(
            "run code owner submit rule (project = %s, change = %d)",
            changeData.project().get(), changeData.getId().get());

        if (codeOwnersPluginConfiguration
            .getProjectConfig(changeData.project())
            .isDisabled(changeData.change().getDest().branch())) {
          logger.atFine().log(
              "code owners functionality is disabled for branch %s", changeData.change().getDest());
          return Optional.empty();
        }

        return Optional.of(getSubmitRecord(changeData.notes()));
      }
    } catch (RestApiException e) {
      logger.atFine().withCause(e).log(
          String.format(
              "Couldn't evaluate code owner statuses for patch set %d of change %d.",
              changeData.currentPatchSet().id().get(), changeData.change().getId().get()));
      return Optional.of(notReady());
    } catch (Throwable t) {
      // Whether the exception should be treated as RULE_ERROR.
      // RULE_ERROR must only be returned if the exception is caused by user misconfiguration (e.g.
      // an invalid OWNERS file), but not for internal server errors.
      boolean isRuleError = false;

      String cause = t.getClass().getSimpleName();
      String errorMessage = "Failed to evaluate code owner statuses";
      if (changeData != null) {
        errorMessage +=
            String.format(
                " for patch set %d of change %d",
                changeData.currentPatchSet().id().get(), changeData.change().getId().get());
      }
      Optional<InvalidPathException> invalidPathException =
          CodeOwnersExceptionHook.getInvalidPathException(t);
      Optional<InvalidCodeOwnerConfigException> invalidCodeOwnerConfigException =
          CodeOwners.getInvalidCodeOwnerConfigCause(t);
      if (invalidPathException.isPresent()) {
        isRuleError = true;
        cause = "invalid_path";
        errorMessage += String.format(" (cause: %s)", invalidPathException.get().getMessage());
      } else if (invalidCodeOwnerConfigException.isPresent()) {
        isRuleError = true;
        codeOwnerMetrics.countInvalidCodeOwnerConfigFiles.increment(
            invalidCodeOwnerConfigException.get().getProjectName().get(),
            invalidCodeOwnerConfigException.get().getRef(),
            invalidCodeOwnerConfigException.get().getCodeOwnerConfigFilePath());

        cause = "invalid_code_owner_config_file";
        errorMessage +=
            String.format(" (cause: %s)", invalidCodeOwnerConfigException.get().getMessage());

        Optional<String> invalidCodeOwnerConfigInfoUrl =
            codeOwnersPluginConfiguration
                .getProjectConfig(invalidCodeOwnerConfigException.get().getProjectName())
                .getInvalidCodeOwnerConfigInfoUrl();
        if (invalidCodeOwnerConfigInfoUrl.isPresent()) {
          errorMessage +=
              String.format(".\nFor help check %s", invalidCodeOwnerConfigInfoUrl.get());
        }
      }
      errorMessage += ".";
      codeOwnerMetrics.countCodeOwnerSubmitRuleErrors.increment(cause);

      if (isRuleError) {
        logger.atWarning().log(errorMessage);
        return Optional.of(ruleError(errorMessage));
      }
      throw new CodeOwnersInternalServerErrorException(errorMessage, t);
    }
  }

  private SubmitRecord getSubmitRecord(ChangeNotes changeNotes)
      throws ResourceConflictException, IOException, PatchListNotAvailableException,
          DiffNotAvailableException {
    requireNonNull(changeNotes, "changeNotes");
    return codeOwnerApprovalCheck.isSubmittable(changeNotes) ? ok() : notReady();
  }

  private static SubmitRecord ok() {
    SubmitRecord submitRecord = new SubmitRecord();
    submitRecord.status = SubmitRecord.Status.OK;
    submitRecord.requirements = ImmutableList.of(SUBMIT_REQUIREMENT);
    return submitRecord;
  }

  private static SubmitRecord notReady() {
    SubmitRecord submitRecord = new SubmitRecord();
    submitRecord.status = SubmitRecord.Status.NOT_READY;
    submitRecord.requirements = ImmutableList.of(SUBMIT_REQUIREMENT);
    return submitRecord;
  }

  private static SubmitRecord ruleError(String reason) {
    SubmitRecord submitRecord = new SubmitRecord();
    submitRecord.errorMessage = reason;
    submitRecord.status = SubmitRecord.Status.RULE_ERROR;
    return submitRecord;
  }
}
