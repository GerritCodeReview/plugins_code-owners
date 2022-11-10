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

import static com.google.gerrit.plugins.codeowners.backend.CodeOwnersInternalServerErrorException.newInternalServerError;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.EnableImplicitApprovals;
import com.google.gerrit.plugins.codeowners.backend.FallbackCodeOwners;
import com.google.gerrit.plugins.codeowners.backend.PathExpressions;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerConfigValidationPolicy;
import com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy;
import com.google.gerrit.server.account.Emails;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;

/** Snapshot of the project-specific code-owners plugin configuration. */
public class CodeOwnersPluginProjectConfigSnapshot {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    CodeOwnersPluginProjectConfigSnapshot create(Project.NameKey projectName);
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

  @Nullable private Optional<String> fileExtension;
  @Nullable private Boolean enableCodeOwnerConfigFilesWithFileExtensions;
  @Nullable private Boolean codeOwnerConfigsReadOnly;
  @Nullable private Boolean exemptPureReverts;
  @Nullable private Boolean rejectNonResolvableCodeOwners;
  @Nullable private Boolean rejectNonResolvableImports;

  @Nullable
  private CodeOwnerConfigValidationPolicy codeOwnerConfigValidationPolicyForBranchCreation;

  @Nullable
  private CodeOwnerConfigValidationPolicy codeOwnerConfigValidationPolicyForCommitReceived;

  @Nullable private CodeOwnerConfigValidationPolicy codeOwnerConfigValidationPolicyForSubmit;
  @Nullable private MergeCommitStrategy mergeCommitStrategy;
  @Nullable private FallbackCodeOwners fallbackCodeOwners;
  @Nullable private Integer maxPathsInChangeMessages;
  @Nullable private Boolean enableAsyncMessageOnAddReviewer;
  @Nullable private ImmutableSet<CodeOwnerReference> globalCodeOwners;
  @Nullable private ImmutableSet<Account.Id> exemptedAccounts;
  @Nullable private Optional<String> overrideInfoUrl;
  @Nullable private Optional<String> invalidCodeOwnerConfigInfoUrl;
  private Map<String, Boolean> disabledByBranch = new HashMap<>();
  @Nullable private Boolean isDisabled;
  private Map<String, CodeOwnerBackend> backendByBranch = new HashMap<>();
  @Nullable private CodeOwnerBackend backend;
  private Map<String, Optional<PathExpressions>> pathExpressionsByBranch = new HashMap<>();
  @Nullable private Optional<PathExpressions> pathExpressions;
  @Nullable private Boolean implicitApprovalsEnabled;
  @Nullable private Boolean stickyApprovalsEnabled;
  @Nullable private RequiredApproval requiredApproval;
  @Nullable private ImmutableSortedSet<RequiredApproval> overrideApprovals;

  @Inject
  CodeOwnersPluginProjectConfigSnapshot(
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
    if (fileExtension == null) {
      fileExtension = generalConfig.getFileExtension(pluginConfig);
    }
    return fileExtension;
  }

  /** Whether file extensions for code owner config files are enabled. */
  public boolean enableCodeOwnerConfigFilesWithFileExtensions() {
    if (enableCodeOwnerConfigFilesWithFileExtensions == null) {
      enableCodeOwnerConfigFilesWithFileExtensions =
          generalConfig.enableCodeOwnerConfigFilesWithFileExtensions(projectName, pluginConfig);
    }
    return enableCodeOwnerConfigFilesWithFileExtensions;
  }

  /** Whether code owner configs are read-only. */
  public boolean areCodeOwnerConfigsReadOnly() {
    if (codeOwnerConfigsReadOnly == null) {
      codeOwnerConfigsReadOnly = generalConfig.getReadOnly(projectName, pluginConfig);
    }
    return codeOwnerConfigsReadOnly;
  }

  /** Whether pure revert changes are exempted from needing code owner approvals for submit. */
  public boolean arePureRevertsExempted() {
    if (exemptPureReverts == null) {
      exemptPureReverts = generalConfig.getExemptPureReverts(projectName, pluginConfig);
    }
    return exemptPureReverts;
  }

  /**
   * Whether newly added non-resolvable code owners should be rejected on commit received and
   * submit.
   *
   * @param branchName the branch for which it should be checked whether non-resolvable code owners
   *     should be rejected
   */
  public boolean rejectNonResolvableCodeOwners(String branchName) {
    if (rejectNonResolvableCodeOwners == null) {
      rejectNonResolvableCodeOwners = readRejectNonResolvableCodeOwners(branchName);
    }
    return rejectNonResolvableCodeOwners;
  }

  private boolean readRejectNonResolvableCodeOwners(String branchName) {
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
   * Whether newly added non-resolvable imports should be rejected on commit received and submit.
   *
   * @param branchName the branch for which it should be checked whether non-resolvable imports
   *     should be rejected
   */
  public boolean rejectNonResolvableImports(String branchName) {
    if (rejectNonResolvableImports == null) {
      rejectNonResolvableImports = readRejectNonResolvableImports(branchName);
    }
    return rejectNonResolvableImports;
  }

  private boolean readRejectNonResolvableImports(String branchName) {
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
   * Whether code owner configs should be validated when a branch is created.
   *
   * @param branchName the branch for which it should be checked whether code owner configs should
   *     be validated on branch creation
   */
  public CodeOwnerConfigValidationPolicy getCodeOwnerConfigValidationPolicyForBranchCreation(
      String branchName) {
    if (codeOwnerConfigValidationPolicyForBranchCreation == null) {
      codeOwnerConfigValidationPolicyForBranchCreation =
          readCodeOwnerConfigValidationPolicyForBranchCreation(branchName);
    }
    return codeOwnerConfigValidationPolicyForBranchCreation;
  }

  private CodeOwnerConfigValidationPolicy readCodeOwnerConfigValidationPolicyForBranchCreation(
      String branchName) {
    requireNonNull(branchName, "branchName");

    Optional<CodeOwnerConfigValidationPolicy> branchSpecificPolicy =
        generalConfig.getCodeOwnerConfigValidationPolicyForBranchCreationForBranch(
            BranchNameKey.create(projectName, branchName), pluginConfig);
    if (branchSpecificPolicy.isPresent()) {
      return branchSpecificPolicy.get();
    }

    return generalConfig.getCodeOwnerConfigValidationPolicyForBranchCreation(
        projectName, pluginConfig);
  }

  /**
   * Whether code owner configs should be validated when a commit is received.
   *
   * @param branchName the branch for which it should be checked whether code owner configs should
   *     be validated on commit received
   */
  public CodeOwnerConfigValidationPolicy getCodeOwnerConfigValidationPolicyForCommitReceived(
      String branchName) {
    if (codeOwnerConfigValidationPolicyForCommitReceived == null) {
      codeOwnerConfigValidationPolicyForCommitReceived =
          readCodeOwnerConfigValidationPolicyForCommitReceived(branchName);
    }
    return codeOwnerConfigValidationPolicyForCommitReceived;
  }

  private CodeOwnerConfigValidationPolicy readCodeOwnerConfigValidationPolicyForCommitReceived(
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
    if (codeOwnerConfigValidationPolicyForSubmit == null) {
      codeOwnerConfigValidationPolicyForSubmit =
          readCodeOwnerConfigValidationPolicyForSubmit(branchName);
    }
    return codeOwnerConfigValidationPolicyForSubmit;
  }

  private CodeOwnerConfigValidationPolicy readCodeOwnerConfigValidationPolicyForSubmit(
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
    if (mergeCommitStrategy == null) {
      mergeCommitStrategy = generalConfig.getMergeCommitStrategy(projectName, pluginConfig);
    }
    return mergeCommitStrategy;
  }

  /** Gets the fallback code owners. */
  public FallbackCodeOwners getFallbackCodeOwners() {
    if (fallbackCodeOwners == null) {
      fallbackCodeOwners = generalConfig.getFallbackCodeOwners(projectName, pluginConfig);
    }
    return fallbackCodeOwners;
  }

  /** Gets the max paths in change messages. */
  public int getMaxPathsInChangeMessages() {
    if (maxPathsInChangeMessages == null) {
      maxPathsInChangeMessages =
          generalConfig.getMaxPathsInChangeMessages(projectName, pluginConfig);
    }
    return maxPathsInChangeMessages;
  }

  /**
   * Gets whether code owner change messages that are added when a code owner is added as a reviewer
   * should be posted asynchronously.
   */
  public boolean enableAsyncMessageOnAddReviewer() {
    if (enableAsyncMessageOnAddReviewer == null) {
      enableAsyncMessageOnAddReviewer =
          generalConfig.enableAsyncMessageOnAddReviewer(projectName, pluginConfig);
    }
    return enableAsyncMessageOnAddReviewer;
  }

  /** Gets the global code owners. */
  public ImmutableSet<CodeOwnerReference> getGlobalCodeOwners() {
    if (globalCodeOwners == null) {
      globalCodeOwners = generalConfig.getGlobalCodeOwners(pluginConfig);
    }
    return globalCodeOwners;
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
      throw newInternalServerError(
          String.format(
              "Failed to resolve exempted users %s on project %s", exemptedUsers, projectName),
          e);
    }
  }

  /** Gets the override info URL that is configured. */
  public Optional<String> getOverrideInfoUrl() {
    if (overrideInfoUrl == null) {
      overrideInfoUrl = generalConfig.getOverrideInfoUrl(pluginConfig);
    }
    return overrideInfoUrl;
  }

  /** Gets the invalid code owner config info URL that is configured. */
  public Optional<String> getInvalidCodeOwnerConfigInfoUrl() {
    if (invalidCodeOwnerConfigInfoUrl == null) {
      invalidCodeOwnerConfigInfoUrl = generalConfig.getInvalidCodeOwnerConfigInfoUrl(pluginConfig);
    }
    return invalidCodeOwnerConfigInfoUrl;
  }

  /**
   * Whether the code owners functionality is disabled for the given branch.
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

    BranchNameKey branchNameKey = BranchNameKey.create(projectName, branchName);
    return disabledByBranch.computeIfAbsent(
        branchNameKey.branch(),
        b -> {
          boolean isDisabled = statusConfig.isDisabledForBranch(pluginConfig, branchNameKey);
          if (isDisabled) {
            return true;
          }
          return isDisabled();
        });
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
    if (isDisabled == null) {
      isDisabled = statusConfig.isDisabledForProject(pluginConfig, projectName);
    }
    return isDisabled;
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
    requireNonNull(branchName, "branchName");

    BranchNameKey branchNameKey = BranchNameKey.create(projectName, branchName);
    return backendByBranch.computeIfAbsent(
        branchNameKey.branch(),
        b -> {
          Optional<CodeOwnerBackend> codeOwnerBackend =
              backendConfig.getBackendForBranch(
                  pluginConfig, BranchNameKey.create(projectName, branchName));
          if (codeOwnerBackend.isPresent()) {
            return codeOwnerBackend.get();
          }
          return getBackend();
        });
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
    if (backend == null) {
      backend = readBackend();
    }
    return backend;
  }

  private CodeOwnerBackend readBackend() {
    // check if a project specific backend is configured
    Optional<CodeOwnerBackend> codeOwnerBackend =
        backendConfig.getBackendForProject(pluginConfig, projectName);
    if (codeOwnerBackend.isPresent()) {
      return codeOwnerBackend.get();
    }

    // fall back to the default backend
    return backendConfig.getDefaultBackend();
  }

  /**
   * Returns the configured {@link PathExpressions} for the given branch.
   *
   * <p>The path expression configuration is evaluated in the following order:
   *
   * <ul>
   *   <li>path expression configuration for branch (with inheritance, first by full branch name,
   *       then by short branch name)
   *   <li>path expressions configuration for project (with inheritance)
   * </ul>
   *
   * <p>The first path expressions configuration that exists counts and the evaluation is stopped.
   *
   * @param branchName the branch for which the configured path expressions should be returned
   * @return the {@link PathExpressions} that should be used for the branch, {@link
   *     Optional#empty()} if no path expressions are configured for the branch
   */
  public Optional<PathExpressions> getPathExpressions(String branchName) {
    requireNonNull(branchName, "branchName");

    BranchNameKey branchNameKey = BranchNameKey.create(projectName, branchName);
    return pathExpressionsByBranch.computeIfAbsent(
        branchNameKey.branch(),
        b -> {
          Optional<PathExpressions> pathExpressions =
              backendConfig.getPathExpressionsForBranch(
                  pluginConfig, BranchNameKey.create(projectName, branchName));
          if (pathExpressions.isPresent()) {
            return pathExpressions;
          }
          return getPathExpressions();
        });
  }

  /**
   * Returns the configured {@link PathExpressions}.
   *
   * <p>The path expression configuration is evaluated in the following order:
   *
   * <ul>
   *   <li>path expression configuration for project (with inheritance)
   *   <li>default path expressions (globally configured path expressions)
   * </ul>
   *
   * <p>The first path expression configuration that exists counts and the evaluation is stopped.
   *
   * @return the {@link PathExpressions} that should be used, {@link Optional#empty()} if no path
   *     expressions are configured
   */
  public Optional<PathExpressions> getPathExpressions() {
    if (pathExpressions == null) {
      pathExpressions = readPathExpressions();
    }
    return pathExpressions;
  }

  private Optional<PathExpressions> readPathExpressions() {
    // check if project specific path expressions are configured
    Optional<PathExpressions> pathExpressions =
        backendConfig.getPathExpressionsForProject(pluginConfig, projectName);
    if (pathExpressions.isPresent()) {
      return pathExpressions;
    }

    // fall back to the default path expressions
    return backendConfig.getDefaultPathExpressions();
  }

  /**
   * Checks whether implicit code owner approvals are enabled.
   *
   * <p>If enabled, an implicit code owner approval from the change owner is assumed if the last
   * patch set was uploaded by the change owner.
   */
  public boolean areImplicitApprovalsEnabled() {
    if (implicitApprovalsEnabled == null) {
      implicitApprovalsEnabled = readImplicitApprovalsEnabled();
    }
    return implicitApprovalsEnabled;
  }

  private boolean readImplicitApprovalsEnabled() {
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
   * Checks whether sticky code owner approvals are enabled.
   *
   * <p>If enabled, a code owner approval on a previous patch set is sticky (if the approver didn't
   * alter or remove it on a later patch set).
   */
  public boolean areStickyApprovalsEnabled() {
    if (stickyApprovalsEnabled == null) {
      stickyApprovalsEnabled = readStickyApprovalsEnabled();
    }
    return stickyApprovalsEnabled;
  }

  private boolean readStickyApprovalsEnabled() {
    boolean enableStickyApprovals = generalConfig.enableStickyApprovals(projectName, pluginConfig);
    logger.atFine().log(
        "sticky approvals on project %s are %s",
        projectName, enableStickyApprovals ? "enabled" : "disabled");
    return enableStickyApprovals;
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
    if (requiredApproval == null) {
      requiredApproval = readRequiredApproval();
    }
    return requiredApproval;
  }

  private RequiredApproval readRequiredApproval() {
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
   * <p>The override approval configuration is read from:
   *
   * <ul>
   *   <li>the override approval configuration for project (with inheritance)
   *   <li>the globally configured override approval
   * </ul>
   *
   * <p>Override approvals that are configured on project-level extend the inherited override
   * approval configuration.
   *
   * <p>The returned override approvals are sorted alphabetically by their string representation
   * (e.g. {@code Owners-Override+1}).
   *
   * @return the override approvals that should be used, an empty set if no override approval is
   *     configured, in this case the override functionality is disabled
   */
  public ImmutableSortedSet<RequiredApproval> getOverrideApprovals() {
    if (overrideApprovals == null) {
      overrideApprovals = readOverrideApprovals();
    }
    return overrideApprovals;
  }

  private ImmutableSortedSet<RequiredApproval> readOverrideApprovals() {
    try {
      return ImmutableSortedSet.copyOf(
          filterOutDuplicateRequiredApprovals(
              getConfiguredRequiredApproval(overrideApprovalConfig)));
    } catch (InvalidPluginConfigurationException e) {
      logger.atInfo().log(
          "Ignoring invalid override approval configuration for project %s."
              + " Overrides are disabled.",
          projectName.get());
    }

    return ImmutableSortedSet.of();
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
  private Collection<RequiredApproval> filterOutDuplicateRequiredApprovals(
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
    return requiredApprovalsByLabel.values();
  }

  /**
   * Gets the required approvals that are configured.
   *
   * @param requiredApprovalConfig the config from which the required approvals should be read
   * @return the required approvals that are configured, an empty list if no required approvals are
   *     configured
   */
  private ImmutableList<RequiredApproval> getConfiguredRequiredApproval(
      AbstractRequiredApprovalConfig requiredApprovalConfig) {
    ProjectState projectState =
        projectCache.get(projectName).orElseThrow(illegalState(projectName));
    return requiredApprovalConfig.get(projectState, pluginConfig);
  }
}
