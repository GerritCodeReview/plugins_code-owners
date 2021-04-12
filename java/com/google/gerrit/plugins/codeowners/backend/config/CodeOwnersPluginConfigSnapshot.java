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

package com.google.gerrit.plugins.codeowners.backend.config;

import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnersInternalServerErrorException;
import com.google.gerrit.plugins.codeowners.backend.EnableImplicitApprovals;
import com.google.gerrit.plugins.codeowners.backend.FallbackCodeOwners;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerConfigValidationPolicy;
import com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy;
import com.google.gerrit.server.account.Emails;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;

/** Snapshot of the code-owners plugin configuration for one project. */
public class CodeOwnersPluginConfigSnapshot {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    CodeOwnersPluginConfigSnapshot create(Project.NameKey projectName);
  }

  private final ProjectCache projectCache;
  private final Emails emails;
  private final BackendConfig backendConfig;
  private final GeneralConfig generalConfig;
  private final OverrideApprovalConfig overrideApprovalConfig;
  private final RequiredApprovalConfig requiredApprovalConfig;
  private final StatusConfig statusConfig;
  private final Project.NameKey projectName;
  private final Config pluginConfig;

  @Nullable private ImmutableSet<Account.Id> exemptedAccounts;

  @Inject
  CodeOwnersPluginConfigSnapshot(
      CodeOwnersPluginConfig.Factory codeOwnersPluginConfigFactory,
      ProjectCache projectCache,
      Emails emails,
      BackendConfig backendConfig,
      GeneralConfig generalConfig,
      OverrideApprovalConfig overrideApprovalConfig,
      RequiredApprovalConfig requiredApprovalConfig,
      StatusConfig statusConfig,
      @Assisted Project.NameKey projectName) {
    this.projectCache = projectCache;
    this.emails = emails;
    this.backendConfig = backendConfig;
    this.generalConfig = generalConfig;
    this.overrideApprovalConfig = overrideApprovalConfig;
    this.requiredApprovalConfig = requiredApprovalConfig;
    this.statusConfig = statusConfig;
    this.projectName = projectName;
    this.pluginConfig = codeOwnersPluginConfigFactory.create(projectName).get();
  }

  /** Gets the file extension of code owner config files, if any configured. */
  public Optional<String> getFileExtension() {
    return generalConfig.getFileExtension(pluginConfig);
  }

  /** Checks whether code owner configs are read-only. */
  public boolean areCodeOwnerConfigsReadOnly() {
    return generalConfig.getReadOnly(projectName, pluginConfig);
  }

  /**
   * Checks whether pure revert changes are exempted from needing code owner approvals for submit.
   */
  public boolean arePureRevertsExempted() {
    return generalConfig.getExemptPureReverts(projectName, pluginConfig);
  }

  /**
   * Checks whether newly added non-resolvable code owners should be rejected on commit received and
   * submit.
   *
   * @param branchName the branch for which it should be checked whether non-resolvable code owners
   *     should be rejected
   */
  public boolean rejectNonResolvableCodeOwners(String branchName) {
    requireNonNull(branchName, "branchName");

    Optional<Boolean> branchSpecificFlag =
        generalConfig.getRejectNonResolvableCodeOwnersForBranch(
            BranchNameKey.create(projectName, branchName), pluginConfig);
    if (branchSpecificFlag.isPresent()) {
      return branchSpecificFlag.get();
    }

    return generalConfig.getRejectNonResolvableCodeOwners(projectName, pluginConfig);
  }

  /**
   * Checks whether newly added non-resolvable imports should be rejected on commit received and
   * submit.
   *
   * @param branchName the branch for which it should be checked whether non-resolvable imports
   *     should be rejected
   */
  public boolean rejectNonResolvableImports(String branchName) {
    requireNonNull(branchName, "branchName");

    Optional<Boolean> branchSpecificFlag =
        generalConfig.getRejectNonResolvableImportsForBranch(
            BranchNameKey.create(projectName, branchName), pluginConfig);
    if (branchSpecificFlag.isPresent()) {
      return branchSpecificFlag.get();
    }

    return generalConfig.getRejectNonResolvableImports(projectName, pluginConfig);
  }

  /**
   * Whether code owner configs should be validated when a commit is received.
   *
   * @param branchName the branch for which it should be checked whether code owner configs should
   *     be validated on commit received
   */
  public CodeOwnerConfigValidationPolicy getCodeOwnerConfigValidationPolicyForCommitReceived(
      String branchName) {
    requireNonNull(branchName, "branchName");

    Optional<CodeOwnerConfigValidationPolicy> branchSpecificPolicy =
        generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceivedForBranch(
            BranchNameKey.create(projectName, branchName), pluginConfig);
    if (branchSpecificPolicy.isPresent()) {
      return branchSpecificPolicy.get();
    }

    return generalConfig.getCodeOwnerConfigValidationPolicyForCommitReceived(
        projectName, pluginConfig);
  }

  /**
   * Whether code owner configs should be validated when a change is submitted.
   *
   * @param branchName the branch for which it should be checked whether code owner configs should
   *     be validated on submit
   */
  public CodeOwnerConfigValidationPolicy getCodeOwnerConfigValidationPolicyForSubmit(
      String branchName) {
    requireNonNull(branchName, "branchName");

    Optional<CodeOwnerConfigValidationPolicy> branchSpecificPolicy =
        generalConfig.getCodeOwnerConfigValidationPolicyForSubmitForBranch(
            BranchNameKey.create(projectName, branchName), pluginConfig);
    if (branchSpecificPolicy.isPresent()) {
      return branchSpecificPolicy.get();
    }

    return generalConfig.getCodeOwnerConfigValidationPolicyForSubmit(projectName, pluginConfig);
  }

  /** Gets the merge commit strategy. */
  public MergeCommitStrategy getMergeCommitStrategy() {
    return generalConfig.getMergeCommitStrategy(projectName, pluginConfig);
  }

  /** Gets the fallback code owners. */
  public FallbackCodeOwners getFallbackCodeOwners() {
    return generalConfig.getFallbackCodeOwners(projectName, pluginConfig);
  }

  /** Gets the max paths in change messages. */
  public int getMaxPathsInChangeMessages() {
    return generalConfig.getMaxPathsInChangeMessages(projectName, pluginConfig);
  }

  /** Gets the global code owners. */
  public ImmutableSet<CodeOwnerReference> getGlobalCodeOwners() {
    return generalConfig.getGlobalCodeOwners(pluginConfig);
  }

  /** Gets the accounts that are exempted from requiring code owner approvals. */
  public ImmutableSet<Account.Id> getExemptedAccounts() {
    if (exemptedAccounts == null) {
      exemptedAccounts = lookupExemptedAccounts();
    }
    return exemptedAccounts;
  }

  private ImmutableSet<Account.Id> lookupExemptedAccounts() {
    ImmutableSet<String> exemptedUsers = generalConfig.getExemptedUsers(pluginConfig);

    try {
      ImmutableSetMultimap<String, Account.Id> exemptedAccounts =
          emails.getAccountsFor(exemptedUsers.toArray(new String[0]));

      exemptedUsers.stream()
          .filter(exemptedUser -> !exemptedAccounts.containsKey(exemptedUser))
          .forEach(
              exemptedUser ->
                  logger.atWarning().log(
                      "Ignoring exempted user %s for project %s: not found",
                      exemptedUser, projectName));

      return ImmutableSet.copyOf(exemptedAccounts.values());
    } catch (IOException e) {
      throw new CodeOwnersInternalServerErrorException(
          String.format(
              "Failed to resolve exempted users %s on project %s", exemptedUsers, projectName),
          e);
    }
  }

  /** Gets the override info URL that is configured. */
  public Optional<String> getOverrideInfoUrl() {
    return generalConfig.getOverrideInfoUrl(pluginConfig);
  }

  /** Gets the invalid code owner config info URL that is configured. */
  public Optional<String> getInvalidCodeOwnerConfigInfoUrl() {
    return generalConfig.getInvalidCodeOwnerConfigInfoUrl(pluginConfig);
  }

  /**
   * Whether the code owners functionality is disabled for the given branch.
   *
   * <p>Callers must ensure that the project of the specified branch exists. If the project doesn't
   * exist the call fails with {@link IllegalStateException}.
   *
   * <p>The configuration is evaluated in the following order:
   *
   * <ul>
   *   <li>disabled configuration for the branch (with inheritance)
   *   <li>disabled configuration for the project (with inheritance)
   *   <li>hard-coded default (not disabled)
   * </ul>
   *
   * <p>The first disabled configuration that exists counts and the evaluation is stopped.
   *
   * @param branchName the branch for which it should be checked whether the code owners
   *     functionality is disabled
   * @return {@code true} if the code owners functionality is disabled for the given branch,
   *     otherwise {@code false}
   */
  public boolean isDisabled(String branchName) {
    requireNonNull(branchName, "branchName");

    boolean isDisabled =
        statusConfig.isDisabledForBranch(
            pluginConfig, BranchNameKey.create(projectName, branchName));
    if (isDisabled) {
      return true;
    }

    return isDisabled();
  }

  /**
   * Whether the code owners functionality is disabled for the given project.
   *
   * <p>The configuration is evaluated in the following order:
   *
   * <ul>
   *   <li>disabled configuration for the project (with inheritance)
   *   <li>hard-coded default (not disabled)
   * </ul>
   *
   * <p>The first disabled configuration that exists counts and the evaluation is stopped.
   *
   * @return {@code true} if the code owners functionality is disabled, otherwise {@code false}
   */
  public boolean isDisabled() {
    return statusConfig.isDisabledForProject(pluginConfig, projectName);
  }

  /**
   * Returns the configured {@link CodeOwnerBackend} for the given branch.
   *
   * <p>The code owner backend configuration is evaluated in the following order:
   *
   * <ul>
   *   <li>backend configuration for branch (with inheritance, first by full branch name, then by
   *       short branch name)
   *   <li>backend configuration for project (with inheritance)
   *   <li>default backend (first globally configured backend, then hard-coded default backend)
   * </ul>
   *
   * <p>The first code owner backend configuration that exists counts and the evaluation is stopped.
   *
   * @param branchName the branch for which the configured code owner backend should be returned
   * @return the {@link CodeOwnerBackend} that should be used for the branch
   */
  public CodeOwnerBackend getBackend(String branchName) {
    // check if a branch specific backend is configured
    Optional<CodeOwnerBackend> codeOwnerBackend =
        backendConfig.getBackendForBranch(
            pluginConfig, BranchNameKey.create(projectName, branchName));
    if (codeOwnerBackend.isPresent()) {
      return codeOwnerBackend.get();
    }

    return getBackend();
  }

  /**
   * Returns the configured {@link CodeOwnerBackend}.
   *
   * <p>The code owner backend configuration is evaluated in the following order:
   *
   * <ul>
   *   <li>backend configuration for project (with inheritance)
   *   <li>default backend (first globally configured backend, then hard-coded default backend)
   * </ul>
   *
   * <p>The first code owner backend configuration that exists counts and the evaluation is stopped.
   *
   * @return the {@link CodeOwnerBackend} that should be used
   */
  public CodeOwnerBackend getBackend() {
    // check if a project specific backend is configured
    Optional<CodeOwnerBackend> codeOwnerBackend =
        backendConfig.getBackendForProject(pluginConfig, projectName);
    if (codeOwnerBackend.isPresent()) {
      return codeOwnerBackend.get();
    }

    // fall back to the default backend
    return backendConfig.getDefaultBackend();
  }

  /** Checks whether an implicit code owner approval from the last uploader is assumed. */
  public boolean areImplicitApprovalsEnabled() {
    EnableImplicitApprovals enableImplicitApprovals =
        generalConfig.getEnableImplicitApprovals(projectName, pluginConfig);
    switch (enableImplicitApprovals) {
      case FALSE:
        logger.atFine().log("implicit approvals on project %s are disabled", projectName);
        return false;
      case TRUE:
        LabelType requiredLabel = getRequiredApproval().labelType();
        if (requiredLabel.isIgnoreSelfApproval()) {
          logger.atFine().log(
              "ignoring implicit approval configuration on project %s since the label of the required"
                  + " approval (%s) is configured to ignore self approvals",
              projectName, requiredLabel);
          return false;
        }
        return true;
      case FORCED:
        logger.atFine().log("implicit approvals on project %s are enforced", projectName);
        return true;
    }
    throw new IllegalStateException(
        String.format(
            "unknown value %s for enableImplicitApprovals configuration in project %s",
            enableImplicitApprovals, projectName));
  }

  /**
   * Returns the approval that is required from code owners to approve the files in a change.
   *
   * <p>Defines which approval counts as code owner approval.
   *
   * <p>The code owner required approval configuration is evaluated in the following order:
   *
   * <ul>
   *   <li>required approval configuration for project (with inheritance)
   *   <li>globally configured required approval
   *   <li>hard-coded default required approval
   * </ul>
   *
   * <p>The first required code owner approval configuration that exists counts and the evaluation
   * is stopped.
   *
   * <p>If the code owner configuration contains multiple required approvals values, the last value
   * is used.
   *
   * @return the required code owner approval that should be used
   */
  public RequiredApproval getRequiredApproval() {
    ImmutableList<RequiredApproval> configuredRequiredApprovalConfig =
        getConfiguredRequiredApproval(requiredApprovalConfig);
    if (!configuredRequiredApprovalConfig.isEmpty()) {
      // There can be only one required approval. If multiple ones are configured just use the last
      // one, this is also what Config#getString(String, String, String) does.
      return Iterables.getLast(configuredRequiredApprovalConfig);
    }

    // fall back to hard-coded default required approval
    ProjectState projectState =
        projectCache.get(projectName).orElseThrow(illegalState(projectName));
    return requiredApprovalConfig.createDefault(projectState);
  }

  /**
   * Returns the approvals that are required to override the code owners submit check for a change.
   *
   * <p>If multiple approvals are returned, any of them is sufficient to override the code owners
   * submit check.
   *
   * <p>The override approval configuration is evaluated in the following order:
   *
   * <ul>
   *   <li>override approval configuration for project (with inheritance)
   *   <li>globally configured override approval
   * </ul>
   *
   * <p>The first override approval configuration that exists counts and the evaluation is stopped.
   *
   * @return the override approvals that should be used, an empty set if no override approval is
   *     configured, in this case the override functionality is disabled
   */
  public ImmutableSet<RequiredApproval> getOverrideApprovals() {
    try {
      return filterOutDuplicateRequiredApprovals(
          getConfiguredRequiredApproval(overrideApprovalConfig));
    } catch (InvalidPluginConfigurationException e) {
      logger.atWarning().withCause(e).log(
          "Ignoring invalid override approval configuration for project %s."
              + " Overrides are disabled.",
          projectName.get());
    }

    return ImmutableSet.of();
  }

  /**
   * Filters out duplicate required approvals from the input list.
   *
   * <p>The following entries are considered as duplicate:
   *
   * <ul>
   *   <li>exact identical required approvals (e.g. "Code-Review+2" and "Code-Review+2")
   *   <li>required approvals with the same label name and a higher value (e.g. "Code-Review+2" is
   *       not needed if "Code-Review+1" is already contained, since "Code-Review+1" covers all
   *       "Code-Review" approvals >= 1)
   * </ul>
   */
  private ImmutableSet<RequiredApproval> filterOutDuplicateRequiredApprovals(
      ImmutableList<RequiredApproval> requiredApprovals) {
    Map<String, RequiredApproval> requiredApprovalsByLabel = new HashMap<>();
    for (RequiredApproval requiredApproval : requiredApprovals) {
      String labelName = requiredApproval.labelType().getName();
      RequiredApproval otherRequiredApproval = requiredApprovalsByLabel.get(labelName);
      if (otherRequiredApproval != null
          && otherRequiredApproval.value() <= requiredApproval.value()) {
        continue;
      }
      requiredApprovalsByLabel.put(labelName, requiredApproval);
    }
    return ImmutableSet.copyOf(requiredApprovalsByLabel.values());
  }

  /**
   * Gets the required approvals that are configured.
   *
   * @param requiredApprovalConfig the config from which the required approvals should be read
   * @return the required approvals that is configured, an empty list if no required approvals are
   *     configured
   */
  private ImmutableList<RequiredApproval> getConfiguredRequiredApproval(
      AbstractRequiredApprovalConfig requiredApprovalConfig) {
    ProjectState projectState =
        projectCache.get(projectName).orElseThrow(illegalState(projectName));
    return requiredApprovalConfig.get(projectState, pluginConfig);
  }
}
