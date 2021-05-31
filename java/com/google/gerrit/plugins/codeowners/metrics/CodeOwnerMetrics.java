// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.plugins.codeowners.metrics;

import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Counter3;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.Histogram0;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.server.logging.Metadata;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Metrics of the code-owners plugin. */
@Singleton
public class CodeOwnerMetrics {
  // latency metrics
  public final Timer0 addChangeMessageOnAddReviewer;
  public final Timer0 computeChangedFiles;
  public final Timer0 computeFileStatus;
  public final Timer0 computeFileStatuses;
  public final Timer0 computeOwnedPaths;
  public final Timer0 computePatchSetApprovals;
  public final Timer0 extendChangeMessageOnPostReview;
  public final Timer0 getAutoMerge;
  public final Timer0 getChangedFiles;
  public final Timer0 prepareFileStatusComputation;
  public final Timer0 prepareFileStatusComputationForAccount;
  public final Timer0 resolveCodeOwnerConfig;
  public final Timer0 resolveCodeOwnerConfigImport;
  public final Timer0 resolveCodeOwnerConfigImports;
  public final Timer0 resolveCodeOwnerReferences;
  public final Timer0 resolvePathCodeOwners;
  public final Timer0 runCodeOwnerSubmitRule;

  // code owner config metrics
  public final Histogram0 codeOwnerCacheReadsPerChange;
  public final Histogram0 codeOwnerConfigBackendReadsPerChange;
  public final Histogram0 codeOwnerConfigCacheReadsPerChange;
  public final Histogram0 codeOwnerResolutionsPerChange;
  public final Timer1<String> loadCodeOwnerConfig;
  public final Timer0 readCodeOwnerConfig;
  public final Timer1<String> parseCodeOwnerConfig;

  // counter metrics
  public final Counter0 countCodeOwnerCacheReads;
  public final Counter0 countCodeOwnerConfigReads;
  public final Counter0 countCodeOwnerConfigCacheReads;
  public final Counter3<ValidationTrigger, ValidationResult, Boolean>
      countCodeOwnerConfigValidations;
  public final Counter0 countCodeOwnerResolutions;
  public final Counter1<String> countCodeOwnerSubmitRuleErrors;
  public final Counter0 countCodeOwnerSubmitRuleRuns;
  public final Counter1<Boolean> countCodeOwnerSuggestions;
  public final Counter3<String, String, String> countInvalidCodeOwnerConfigFiles;

  private final MetricMaker metricMaker;

  @Inject
  CodeOwnerMetrics(MetricMaker metricMaker) {
    this.metricMaker = metricMaker;

    // latency metrics
    this.addChangeMessageOnAddReviewer =
        createLatencyTimer(
            "add_change_message_on_add_reviewer",
            "Latency for adding a change message with the owned path when a code owner is added as"
                + " a reviewer");
    this.computeChangedFiles =
        createLatencyTimer("compute_changed_files", "Latency for computing changed files");
    this.computeFileStatus =
        createLatencyTimer(
            "compute_file_status", "Latency for computing the file status of one file");
    this.computeFileStatuses =
        createLatencyTimer(
            "compute_file_statuses",
            "Latency for computing file statuses for all files in a change");
    this.computeOwnedPaths =
        createLatencyTimer(
            "compute_owned_paths",
            "Latency for computing the files in a change that are owned by a user");
    this.computePatchSetApprovals =
        createLatencyTimer(
            "compute_patch_set_approvals",
            "Latency for computing the approvals of the current patch set");
    this.extendChangeMessageOnPostReview =
        createLatencyTimer(
            "extend_change_message_on_post_review",
            "Latency for extending the change message with the owned path when a code owner"
                + " approval is applied");
    this.getAutoMerge =
        createLatencyTimer(
            "get_auto_merge", "Latency for getting the auto merge commit of a merge commit");
    this.getChangedFiles =
        createLatencyTimer(
            "get_changed_files", "Latency for getting changed files from diff cache");
    this.prepareFileStatusComputation =
        createLatencyTimer(
            "prepare_file_status_computation", "Latency for preparing the file status computation");
    this.prepareFileStatusComputationForAccount =
        createLatencyTimer(
            "compute_file_statuses_for_account",
            "Latency for computing file statuses for an account");
    this.resolveCodeOwnerConfig =
        createLatencyTimer(
            "resolve_code_owner_config", "Latency for resolving a code owner config file");
    this.resolveCodeOwnerConfigImport =
        createLatencyTimer(
            "resolve_code_owner_config_import",
            "Latency for resolving an import of a code owner config file");
    this.resolveCodeOwnerConfigImports =
        createLatencyTimer(
            "resolve_code_owner_config_imports",
            "Latency for resolving all imports of a code owner config file");
    this.resolveCodeOwnerReferences =
        createLatencyTimer(
            "resolve_code_owner_references", "Latency for resolving the code owner references");
    this.resolvePathCodeOwners =
        createLatencyTimer(
            "resolve_path_code_owners", "Latency for resolving the code owners of a path");
    this.runCodeOwnerSubmitRule =
        createLatencyTimer(
            "run_code_owner_submit_rule", "Latency for running the code owner submit rule");

    // code owner config metrics
    this.codeOwnerCacheReadsPerChange =
        createHistogram(
            "code_owner_cache_reads_per_change", "Number of code owner cache reads per change");
    this.codeOwnerConfigBackendReadsPerChange =
        createHistogram(
            "code_owner_config_backend_reads_per_change",
            "Number of code owner config backend reads per change");
    this.codeOwnerConfigCacheReadsPerChange =
        createHistogram(
            "code_owner_config_cache_reads_per_change",
            "Number of code owner config cache reads per change");
    this.codeOwnerResolutionsPerChange =
        createHistogram(
            "code_owner_resolutions_per_change", "Number of code owner resolutions per change");
    this.loadCodeOwnerConfig =
        createTimerWithClassField(
            "load_code_owner_config",
            "Latency for loading a code owner config file (read + parse)",
            "backend");
    this.parseCodeOwnerConfig =
        createTimerWithClassField(
            "parse_code_owner_config", "Latency for parsing a code owner config file", "parser");
    this.readCodeOwnerConfig =
        createLatencyTimer(
            "read_code_owner_config", "Latency for reading a code owner config file");

    // counter metrics
    this.countCodeOwnerCacheReads =
        createCounter(
            "count_code_owner_cache_reads", "Total number of code owner reads from cache");
    this.countCodeOwnerConfigReads =
        createCounter(
            "count_code_owner_config_reads",
            "Total number of code owner config reads from backend");
    this.countCodeOwnerConfigCacheReads =
        createCounter(
            "count_code_owner_config_cache_reads",
            "Total number of code owner config reads from cache");
    this.countCodeOwnerConfigValidations =
        createCounter3(
            "count_code_owner_config_validations",
            "Total number of code owner config file validations",
            Field.ofEnum(
                    ValidationTrigger.class, "trigger", (metadataBuilder, resolveAllUsers) -> {})
                .description("The trigger of the validation.")
                .build(),
            Field.ofEnum(ValidationResult.class, "result", (metadataBuilder, resolveAllUsers) -> {})
                .description("The result of the validation.")
                .build(),
            Field.ofBoolean("dry_run", (metadataBuilder, resolveAllUsers) -> {})
                .description("Whether the validation was a dry run.")
                .build());
    this.countCodeOwnerResolutions =
        createCounter("count_code_owner_resolutions", "Total number of code owner resolutions");
    this.countCodeOwnerSubmitRuleErrors =
        createCounter1(
            "count_code_owner_submit_rule_errors",
            "Total number of code owner submit rule errors",
            Field.ofString("cause", Metadata.Builder::cause)
                .description("The cause of the submit rule error.")
                .build());
    this.countCodeOwnerSubmitRuleRuns =
        createCounter(
            "count_code_owner_submit_rule_runs", "Total number of code owner submit rule runs");
    this.countCodeOwnerSuggestions =
        createCounter1(
            "count_code_owner_suggestions",
            "Total number of code owner suggestions",
            Field.ofBoolean("resolve_all_users", (metadataBuilder, resolveAllUsers) -> {})
                .description(
                    "Whether code ownerships that are assigned to all users are resolved to random"
                        + " users.")
                .build());
    this.countInvalidCodeOwnerConfigFiles =
        createCounter3(
            "count_invalid_code_owner_config_files",
            "Total number of failed requests caused by an invalid / non-parsable code owner config"
                + " file",
            Field.ofString("project", Metadata.Builder::projectName)
                .description(
                    "The name of the project that contains the invalid code owner config file.")
                .build(),
            Field.ofString("branch", Metadata.Builder::branchName)
                .description(
                    "The name of the branch that contains the invalid code owner config file.")
                .build(),
            Field.ofString("path", Metadata.Builder::filePath)
                .description("The path of the invalid code owner config file.")
                .build());
  }

  private Timer0 createLatencyTimer(String name, String description) {
    return metricMaker.newTimer(
        name, new Description(description).setCumulative().setUnit(Units.MILLISECONDS));
  }

  private Timer1<String> createTimerWithClassField(
      String name, String description, String fieldName) {
    Field<String> CODE_OWNER_BACKEND_FIELD =
        Field.ofString(
                fieldName, (metadataBuilder, fieldValue) -> metadataBuilder.className(fieldValue))
            .build();

    return metricMaker.newTimer(
        name,
        new Description(description).setCumulative().setUnit(Description.Units.MILLISECONDS),
        CODE_OWNER_BACKEND_FIELD);
  }

  private Counter0 createCounter(String name, String description) {
    return metricMaker.newCounter(name, new Description(description).setRate());
  }

  private <F1> Counter1<F1> createCounter1(String name, String description, Field<F1> field1) {
    return metricMaker.newCounter(name, new Description(description).setRate(), field1);
  }

  private <F1, F2, F3> Counter3<F1, F2, F3> createCounter3(
      String name, String description, Field<F1> field1, Field<F2> field2, Field<F3> field3) {
    return metricMaker.newCounter(
        name, new Description(description).setRate(), field1, field2, field3);
  }

  private Histogram0 createHistogram(String name, String description) {
    return metricMaker.newHistogram(name, new Description(description).setCumulative());
  }
}
