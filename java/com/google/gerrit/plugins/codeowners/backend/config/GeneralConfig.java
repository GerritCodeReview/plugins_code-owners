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

package com.google.gerrit.plugins.codeowners.backend.config;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.EnableImplicitApprovals;
import com.google.gerrit.plugins.codeowners.backend.FallbackCodeOwners;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerConfigValidationPolicy;
import com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.project.RefPatternMatcher;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.PatternSyntaxException;
import org.eclipse.jgit.lib.Config;

/**
 * Class to read the general code owners configuration from {@code gerrit.config} and from {@code
 * code-owners.config} in {@code refs/meta/config}.
 *
 * <p>Default values are configured in {@code gerrit.config}.
 *
 * <p>The default values can be overridden on project-level in {@code code-owners.config} in {@code
 * refs/meta/config}.
 *
 * <p>Projects that have no configuration inherit the configuration from their parent projects.
 */
@Singleton
public class GeneralConfig {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String SECTION_VALIDATION = "validation";

  public static final String KEY_FILE_EXTENSION = "fileExtension";
  public static final String KEY_READ_ONLY = "readOnly";
  public static final String KEY_EXEMPT_PURE_REVERTS = "exemptPureReverts";
  public static final String KEY_FALLBACK_CODE_OWNERS = "fallbackCodeOwners";
  public static final String KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED =
      "enableValidationOnCommitReceived";
  public static final String KEY_ENABLE_VALIDATION_ON_SUBMIT = "enableValidationOnSubmit";
  public static final String KEY_MAX_PATHS_IN_CHANGE_MESSAGES = "maxPathsInChangeMessages";
  public static final String KEY_MERGE_COMMIT_STRATEGY = "mergeCommitStrategy";
  public static final String KEY_GLOBAL_CODE_OWNER = "globalCodeOwner";
  public static final String KEY_EXEMPTED_USER = "exemptedUser";
  public static final String KEY_ENABLE_IMPLICIT_APPROVALS = "enableImplicitApprovals";
  public static final String KEY_OVERRIDE_INFO_URL = "overrideInfoUrl";
  public static final String KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS =
      "rejectNonResolvableCodeOwners";
  public static final String KEY_REJECT_NON_RESOLVABLE_IMPORTS = "rejectNonResolvableImports";

  public static final int DEFAULT_MAX_PATHS_IN_CHANGE_MESSAGES = 50;

  private static final String KEY_ALLOWED_EMAIL_DOMAIN = "allowedEmailDomain";

  private final String pluginName;
  private final PluginConfig pluginConfigFromGerritConfig;

  @Inject
  GeneralConfig(@PluginName String pluginName, PluginConfigFactory pluginConfigFactory) {
    this.pluginName = pluginName;
    this.pluginConfigFromGerritConfig = pluginConfigFactory.getFromGerritConfig(pluginName);
  }

  /**
   * Validates the backend configuration in the given project level configuration.
   *
   * @param fileName the name of the config file
   * @param projectLevelConfig the project level plugin configuration
   * @return list of validation messages for validation errors, empty list if there are no
   *     validation errors
   */
  ImmutableList<CommitValidationMessage> validateProjectLevelConfig(
      String fileName, Config projectLevelConfig) {
    requireNonNull(fileName, "fileName");
    requireNonNull(projectLevelConfig, "projectLevelConfig");

    List<CommitValidationMessage> validationMessages = new ArrayList<>();

    try {
      projectLevelConfig.getEnum(
          SECTION_CODE_OWNERS,
          /* subsection= */ null,
          KEY_MERGE_COMMIT_STRATEGY,
          MergeCommitStrategy.ALL_CHANGED_FILES);
    } catch (IllegalArgumentException e) {
      validationMessages.add(
          new CommitValidationMessage(
              String.format(
                  "Merge commit strategy '%s' that is configured in %s (parameter %s.%s) is invalid.",
                  projectLevelConfig.getString(
                      SECTION_CODE_OWNERS, /* subsection= */ null, KEY_MERGE_COMMIT_STRATEGY),
                  fileName,
                  SECTION_CODE_OWNERS,
                  KEY_MERGE_COMMIT_STRATEGY),
              ValidationMessage.Type.ERROR));
    }

    try {
      projectLevelConfig.getEnum(
          SECTION_CODE_OWNERS,
          /* subsection= */ null,
          KEY_FALLBACK_CODE_OWNERS,
          FallbackCodeOwners.NONE);
    } catch (IllegalArgumentException e) {
      validationMessages.add(
          new CommitValidationMessage(
              String.format(
                  "The value for fallback code owners '%s' that is configured in %s (parameter %s.%s) is invalid.",
                  projectLevelConfig.getString(
                      SECTION_CODE_OWNERS, /* subsection= */ null, KEY_FALLBACK_CODE_OWNERS),
                  fileName,
                  SECTION_CODE_OWNERS,
                  KEY_FALLBACK_CODE_OWNERS),
              ValidationMessage.Type.ERROR));
    }

    try {
      projectLevelConfig.getInt(
          SECTION_CODE_OWNERS,
          /* subsection= */ null,
          KEY_MAX_PATHS_IN_CHANGE_MESSAGES,
          DEFAULT_MAX_PATHS_IN_CHANGE_MESSAGES);
    } catch (IllegalArgumentException e) {
      validationMessages.add(
          new CommitValidationMessage(
              String.format(
                  "The value for max paths in change messages '%s' that is configured in %s"
                      + " (parameter %s.%s) is invalid.",
                  projectLevelConfig.getString(
                      SECTION_CODE_OWNERS,
                      /* subsection= */ null,
                      KEY_MAX_PATHS_IN_CHANGE_MESSAGES),
                  fileName,
                  SECTION_CODE_OWNERS,
                  KEY_MAX_PATHS_IN_CHANGE_MESSAGES),
              ValidationMessage.Type.ERROR));
    }

    return ImmutableList.copyOf(validationMessages);
  }

  /**
   * Gets the file extension that should be used for code owner config files in the given project.
   *
   * @param pluginConfig the plugin config from which the file extension should be read.
   * @return the file extension that should be used for code owner config files in the given
   *     project, {@link Optional#empty()} if no file extension should be used
   */
  Optional<String> getFileExtension(Config pluginConfig) {
    requireNonNull(pluginConfig, "pluginConfig");

    String fileExtension =
        pluginConfig.getString(SECTION_CODE_OWNERS, /* subsection= */ null, KEY_FILE_EXTENSION);
    if (fileExtension != null) {
      return Optional.of(fileExtension);
    }

    return Optional.ofNullable(pluginConfigFromGerritConfig.getString(KEY_FILE_EXTENSION));
  }

  /**
   * Returns the email domains that are allowed to be used for code owners.
   *
   * @return the email domains that are allowed to be used for code owners, an empty set if all
   *     email domains are allowed (if {@code plugin.code-owners.allowedEmailDomain} is not set or
   *     set to an empty value)
   */
  ImmutableSet<String> getAllowedEmailDomains() {
    return Arrays.stream(pluginConfigFromGerritConfig.getStringList(KEY_ALLOWED_EMAIL_DOMAIN))
        .filter(emailDomain -> !Strings.isNullOrEmpty(emailDomain))
        .distinct()
        .collect(toImmutableSet());
  }

  /**
   * Gets the read-only configuration from the given plugin config with fallback to {@code
   * gerrit.config}.
   *
   * <p>The read-only configuration controls whether code owner config files are read-only and all
   * modifications of code owner config files should be rejected.
   *
   * @param project the project for which the read-only configuration should be read
   * @param pluginConfig the plugin config from which the read-only configuration should be read.
   * @return whether code owner config files are read-only
   */
  boolean getReadOnly(Project.NameKey project, Config pluginConfig) {
    return getBooleanConfig(project, pluginConfig, KEY_READ_ONLY, /* defaultValue= */ false);
  }

  /**
   * Gets the exempt-pure-reverts configuration from the given plugin config with fallback to {@code
   * gerrit.config}.
   *
   * <p>The exempt-pure-reverts configuration controls whether pure revert changes are exempted from
   * needing code owner approvals for submit.
   *
   * @param project the project for which the exempt-pure-revert configuration should be read
   * @param pluginConfig the plugin config from which the read-only configuration should be read.
   * @return whether pure reverts are exempted from needing code owner approvals for submit
   */
  boolean getExemptPureReverts(Project.NameKey project, Config pluginConfig) {
    return getBooleanConfig(
        project, pluginConfig, KEY_EXEMPT_PURE_REVERTS, /* defaultValue= */ false);
  }

  /**
   * Gets the reject-non-resolvable-code-owners configuration from the given plugin config for the
   * specified project with fallback to {@code gerrit.config}.
   *
   * <p>The reject-non-resolvable-code-owners configuration controls whether code owner config files
   * with newly added non-resolvable code owners should be rejected on commit received and on
   * submit.
   *
   * @param project the project for which the reject-non-resolvable-code-owners configuration should
   *     be read
   * @param pluginConfig the plugin config from which the reject-non-resolvable-code-owners
   *     configuration should be read.
   * @return whether code owner config files with newly added non-resolvable code owners should be
   *     rejected on commit received and on submit
   */
  boolean getRejectNonResolvableCodeOwners(Project.NameKey project, Config pluginConfig) {
    return getBooleanConfig(
        project, pluginConfig, KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS, /* defaultValue= */ true);
  }

  /**
   * Gets the reject-non-resolvable-code-owners configuration from the given plugin config for the
   * specified branch with fallback to {@code gerrit.config}.
   *
   * <p>If multiple branch-specific configurations match the specified branch, it is undefined which
   * of the matching branch configurations takes precedence.
   *
   * <p>The reject-non-resolvable-code-owners configuration controls whether code owner config files
   * with newly added non-resolvable code owners should be rejected on commit received and on
   * submit.
   *
   * @param branchNameKey the branch and project for which the reject-non-resolvable-code-owners
   *     configuration should be read
   * @param pluginConfig the plugin config from which the reject-non-resolvable-code-owners
   *     configuration should be read.
   * @return whether code owner config files with newly added non-resolvable code owners should be
   *     rejected on commit received and on submit
   */
  Optional<Boolean> getRejectNonResolvableCodeOwnersForBranch(
      BranchNameKey branchNameKey, Config pluginConfig) {
    return getCodeOwnerConfigValidationFlagForBranch(
        KEY_REJECT_NON_RESOLVABLE_CODE_OWNERS, branchNameKey, pluginConfig);
  }

  /**
   * Gets the reject-non-resolvable-imports configuration from the given plugin config for the
   * specified project with fallback to {@code gerrit.config}.
   *
   * <p>The reject-non-resolvable-imports configuration controls whether code owner config files
   * with newly added non-resolvable imports should be rejected on commit received and on submit.
   *
   * @param project the project for which the reject-non-resolvable-imports configuration should be
   *     read
   * @param pluginConfig the plugin config from which the reject-non-resolvable-imports
   *     configuration should be read.
   * @return whether code owner config files with newly added non-resolvable imports should be
   *     rejected on commit received and on submit
   */
  boolean getRejectNonResolvableImports(Project.NameKey project, Config pluginConfig) {
    return getBooleanConfig(
        project, pluginConfig, KEY_REJECT_NON_RESOLVABLE_IMPORTS, /* defaultValue= */ true);
  }

  /**
   * Gets the reject-non-resolvable-imports configuration from the given plugin config for the
   * specified branch with fallback to {@code gerrit.config}.
   *
   * <p>The reject-non-resolvable-imports configuration controls whether code owner config files
   * with newly added non-resolvable imports should be rejected on commit received and on submit.
   *
   * @param branchNameKey the branch and project for which the reject-non-resolvable-imports
   *     configuration should be read
   * @param pluginConfig the plugin config from which the reject-non-resolvable-imports
   *     configuration should be read.
   * @return whether code owner config files with newly added non-resolvable imports should be
   *     rejected on commit received and on submit
   */
  Optional<Boolean> getRejectNonResolvableImportsForBranch(
      BranchNameKey branchNameKey, Config pluginConfig) {
    return getCodeOwnerConfigValidationFlagForBranch(
        KEY_REJECT_NON_RESOLVABLE_IMPORTS, branchNameKey, pluginConfig);
  }

  private boolean getBooleanConfig(
      Project.NameKey project, Config pluginConfig, String key, boolean defaultValue) {
    requireNonNull(project, "project");
    requireNonNull(pluginConfig, "pluginConfig");
    requireNonNull(key, "key");

    String value = pluginConfig.getString(SECTION_CODE_OWNERS, /* subsection= */ null, key);
    if (value != null) {
      try {
        return pluginConfig.getBoolean(
            SECTION_CODE_OWNERS, /* subsection= */ null, key, defaultValue);
      } catch (IllegalArgumentException e) {
        logger.atWarning().withCause(e).log(
            "Ignoring invalid value %s for %s in '%s.config' of project %s."
                + " Falling back to global config.",
            value, key, pluginName, project.get());
      }
    }

    try {
      return pluginConfigFromGerritConfig.getBoolean(key, defaultValue);
    } catch (IllegalArgumentException e) {
      logger.atWarning().withCause(e).log(
          "Ignoring invalid value %s for %s in gerrit.config (parameter"
              + " plugin.%s.%s). Falling back to default value %s.",
          pluginConfigFromGerritConfig.getString(key), key, pluginName, key, defaultValue);
      return defaultValue;
    }
  }

  /**
   * Gets the fallback code owners that own paths that have no defined code owners.
   *
   * @param project the project for which the fallback code owners should be read
   * @param pluginConfig the plugin config from which the fallback code owners should be read
   * @return the fallback code owners that own paths that have no defined code owners
   */
  FallbackCodeOwners getFallbackCodeOwners(Project.NameKey project, Config pluginConfig) {
    requireNonNull(project, "project");
    requireNonNull(pluginConfig, "pluginConfig");

    String fallbackCodeOwnersString =
        pluginConfig.getString(
            SECTION_CODE_OWNERS, /* subsection= */ null, KEY_FALLBACK_CODE_OWNERS);
    if (fallbackCodeOwnersString != null) {
      try {
        return pluginConfig.getEnum(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            KEY_FALLBACK_CODE_OWNERS,
            FallbackCodeOwners.NONE);
      } catch (IllegalArgumentException e) {
        logger.atWarning().withCause(e).log(
            "Ignoring invalid value %s for fallback code owners in '%s.config' of project %s."
                + " Falling back to global config.",
            fallbackCodeOwnersString, pluginName, project.get());
      }
    }

    try {
      return pluginConfigFromGerritConfig.getEnum(
          KEY_FALLBACK_CODE_OWNERS, FallbackCodeOwners.NONE);
    } catch (IllegalArgumentException e) {
      logger.atWarning().withCause(e).log(
          "Ignoring invalid value %s for fallback code owners in gerrit.config (parameter"
              + " plugin.%s.%s). Falling back to default value %s.",
          pluginConfigFromGerritConfig.getString(KEY_FALLBACK_CODE_OWNERS),
          pluginName,
          KEY_FALLBACK_CODE_OWNERS,
          FallbackCodeOwners.NONE);
      return FallbackCodeOwners.NONE;
    }
  }

  /**
   * Gets the maximum number of paths that should be incuded in change messages.
   *
   * @param project the project for which the maximum number of paths in change messages should be
   *     read
   * @param pluginConfig the plugin config from which the maximum number of paths in change messages
   *     should be read
   * @return the maximum number of paths in change messages
   */
  int getMaxPathsInChangeMessages(Project.NameKey project, Config pluginConfig) {
    requireNonNull(project, "project");
    requireNonNull(pluginConfig, "pluginConfig");

    String maxPathInChangeMessagesString =
        pluginConfig.getString(
            SECTION_CODE_OWNERS, /* subsection= */ null, KEY_MAX_PATHS_IN_CHANGE_MESSAGES);
    if (maxPathInChangeMessagesString != null) {
      try {
        return pluginConfig.getInt(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            KEY_MAX_PATHS_IN_CHANGE_MESSAGES,
            DEFAULT_MAX_PATHS_IN_CHANGE_MESSAGES);
      } catch (IllegalArgumentException e) {
        logger.atWarning().withCause(e).log(
            "Ignoring invalid value %s for max paths in change messages in '%s.config' of"
                + " project %s. Falling back to global config.",
            maxPathInChangeMessagesString, pluginName, project.get());
      }
    }

    try {
      return pluginConfigFromGerritConfig.getInt(
          KEY_MAX_PATHS_IN_CHANGE_MESSAGES, DEFAULT_MAX_PATHS_IN_CHANGE_MESSAGES);
    } catch (IllegalArgumentException e) {
      logger.atWarning().withCause(e).log(
          "Ignoring invalid value %s for max paths in change messages in gerrit.config (parameter"
              + " plugin.%s.%s). Falling back to default value %s.",
          pluginConfigFromGerritConfig.getString(KEY_MAX_PATHS_IN_CHANGE_MESSAGES),
          pluginName,
          KEY_MAX_PATHS_IN_CHANGE_MESSAGES,
          DEFAULT_MAX_PATHS_IN_CHANGE_MESSAGES);
      return DEFAULT_MAX_PATHS_IN_CHANGE_MESSAGES;
    }
  }

  /**
   * Gets the enable validation on commit received configuration from the given plugin config for
   * the specified project with fallback to {@code gerrit.config} and default to {@code true}.
   *
   * <p>The enable validation on commit received controls whether code owner config files should be
   * validated when a commit is received.
   *
   * @param project the project for which the enable validation on commit received configuration
   *     should be read
   * @param pluginConfig the plugin config from which the enable validation on commit received
   *     configuration should be read
   * @return whether code owner config files should be validated when a commit is received
   */
  CodeOwnerConfigValidationPolicy getCodeOwnerConfigValidationPolicyForCommitReceived(
      Project.NameKey project, Config pluginConfig) {
    return getCodeOwnerConfigValidationPolicy(
        KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED, project, pluginConfig);
  }

  /**
   * Gets the enable validation on commit received configuration from the given plugin config for
   * the specified branch.
   *
   * <p>If multiple branch-specific configurations match the specified branch, it is undefined which
   * of the matching branch configurations takes precedence.
   *
   * <p>The enable validation on commit received controls whether code owner config files should be
   * validated when a commit is received.
   *
   * @param branchNameKey the branch and project for which the enable validation on commit received
   *     configuration should be read
   * @param pluginConfig the plugin config from which the enable validation on commit received
   *     configuration should be read
   * @return the enable validation on commit received configuration that is configured for the
   *     branch, {@link Optional#empty()} if no branch specific configuration exists
   */
  Optional<CodeOwnerConfigValidationPolicy>
      getCodeOwnerConfigValidationPolicyForCommitReceivedForBranch(
          BranchNameKey branchNameKey, Config pluginConfig) {
    return getCodeOwnerConfigValidationPolicyForBranch(
        KEY_ENABLE_VALIDATION_ON_COMMIT_RECEIVED, branchNameKey, pluginConfig);
  }

  /**
   * Gets the enable validation on submit configuration from the given plugin config for the
   * specified project with fallback to {@code gerrit.config} and default to {@code true}.
   *
   * <p>The enable validation on submit controls whether code owner config files should be validated
   * when a change is submitted.
   *
   * @param project the project for which the enable validation on submit configuration should be
   *     read
   * @param pluginConfig the plugin config from which the enable validation on submit configuration
   *     should be read
   * @return whether code owner config files should be validated when a change is submitted
   */
  CodeOwnerConfigValidationPolicy getCodeOwnerConfigValidationPolicyForSubmit(
      Project.NameKey project, Config pluginConfig) {
    return getCodeOwnerConfigValidationPolicy(
        KEY_ENABLE_VALIDATION_ON_SUBMIT, project, pluginConfig);
  }

  /**
   * Gets the enable validation on submit configuration from the given plugin config for the
   * specified branch.
   *
   * <p>If multiple branch-specific configurations match the specified branch, it is undefined which
   * of the matching branch configurations takes precedence.
   *
   * <p>The enable validation on submit controls whether code owner config files should be validated
   * when a change is submitted.
   *
   * @param branchNameKey the branch and project for which the enable validation on submit
   *     configuration should be read
   * @param pluginConfig the plugin config from which the enable validation on submit configuration
   *     should be read
   * @return the enable validation on submit configuration that is configured for the branch, {@link
   *     Optional#empty()} if no branch specific configuration exists
   */
  Optional<CodeOwnerConfigValidationPolicy> getCodeOwnerConfigValidationPolicyForSubmitForBranch(
      BranchNameKey branchNameKey, Config pluginConfig) {
    return getCodeOwnerConfigValidationPolicyForBranch(
        KEY_ENABLE_VALIDATION_ON_SUBMIT, branchNameKey, pluginConfig);
  }

  private Optional<CodeOwnerConfigValidationPolicy> getCodeOwnerConfigValidationPolicyForBranch(
      String key, BranchNameKey branchNameKey, Config pluginConfig) {
    requireNonNull(key, "key");
    requireNonNull(branchNameKey, "branchNameKey");
    requireNonNull(pluginConfig, "pluginConfig");

    Optional<String> validationSectionForBranch =
        getValidationSectionForBranch(branchNameKey, pluginConfig);
    if (!validationSectionForBranch.isPresent()) {
      return Optional.empty();
    }

    return getCodeOwnerConfigValidationPolicyForBranch(
        validationSectionForBranch.get(), key, branchNameKey.project(), pluginConfig);
  }

  private Optional<Boolean> getCodeOwnerConfigValidationFlagForBranch(
      String key, BranchNameKey branchNameKey, Config pluginConfig) {
    requireNonNull(key, "key");
    requireNonNull(branchNameKey, "branchNameKey");
    requireNonNull(pluginConfig, "pluginConfig");

    Optional<String> validationSectionForBranch =
        getValidationSectionForBranch(branchNameKey, pluginConfig);
    if (!validationSectionForBranch.isPresent()) {
      return Optional.empty();
    }

    return getCodeOwnerConfigValidationFlagForBranch(
        validationSectionForBranch.get(), key, branchNameKey.project(), pluginConfig);
  }

  private CodeOwnerConfigValidationPolicy getCodeOwnerConfigValidationPolicy(
      String key, Project.NameKey project, Config pluginConfig) {
    requireNonNull(key, "key");
    requireNonNull(project, "project");
    requireNonNull(pluginConfig, "pluginConfig");

    String codeOwnerConfigValidationPolicyString =
        pluginConfig.getString(SECTION_CODE_OWNERS, /* subsection= */ null, key);
    if (codeOwnerConfigValidationPolicyString != null) {
      try {
        return pluginConfig.getEnum(
            SECTION_CODE_OWNERS, /* subsection= */ null, key, CodeOwnerConfigValidationPolicy.TRUE);
      } catch (IllegalArgumentException e) {
        logger.atWarning().withCause(e).log(
            "Ignoring invalid value %s for the code owner config validation policy in '%s.config'"
                + " of project %s (parameter %s.%s). Falling back to global config.",
            codeOwnerConfigValidationPolicyString,
            pluginName,
            project.get(),
            SECTION_CODE_OWNERS,
            key);
      }
    }

    try {
      return pluginConfigFromGerritConfig.getEnum(key, CodeOwnerConfigValidationPolicy.TRUE);
    } catch (IllegalArgumentException e) {
      logger.atWarning().withCause(e).log(
          "Ignoring invalid value %s for the code owner config validation policy in gerrit.config"
              + " (parameter plugin.%s.%s). Falling back to default value %s.",
          pluginConfigFromGerritConfig.getString(key),
          pluginName,
          key,
          CodeOwnerConfigValidationPolicy.TRUE);
      return CodeOwnerConfigValidationPolicy.TRUE;
    }
  }

  private Optional<String> getValidationSectionForBranch(
      BranchNameKey branchNameKey, Config pluginConfig) {
    ImmutableSet<String> matchingValidationSubsections =
        pluginConfig.getSubsections(SECTION_VALIDATION).stream()
            .filter(
                refPattern -> {
                  try {
                    return RefPatternMatcher.getMatcher(refPattern)
                        .match(branchNameKey.branch(), /* user= */ null);
                  } catch (PatternSyntaxException e) {
                    logger.atWarning().withCause(e).log(
                        "invalid ref pattern %s for subsection %s.%s in %s.config of project %s",
                        refPattern,
                        SECTION_VALIDATION,
                        refPattern,
                        pluginName,
                        branchNameKey.project());
                    return false;
                  }
                })
            .collect(toImmutableSet());

    if (matchingValidationSubsections.isEmpty()) {
      return Optional.empty();
    }

    String matchingValidationSubsection = matchingValidationSubsections.asList().get(0);
    if (matchingValidationSubsections.size() > 1) {
      logger.atWarning().log(
          "branch %s matches multiple %s subsections in %.config of project %s: %s,"
              + " subsection %s takes precedence",
          branchNameKey.branch(),
          SECTION_VALIDATION,
          pluginName,
          branchNameKey.project(),
          matchingValidationSubsections,
          matchingValidationSubsection);
    }
    return Optional.of(matchingValidationSubsection);
  }

  private Optional<CodeOwnerConfigValidationPolicy> getCodeOwnerConfigValidationPolicyForBranch(
      String subsection, String key, Project.NameKey project, Config pluginConfig) {
    requireNonNull(subsection, "subsection");
    requireNonNull(key, "key");
    requireNonNull(project, "project");
    requireNonNull(pluginConfig, "pluginConfig");

    String codeOwnerConfigValidationPolicyString =
        pluginConfig.getString(SECTION_VALIDATION, subsection, key);
    if (codeOwnerConfigValidationPolicyString != null) {
      try {
        return Optional.of(
            pluginConfig.getEnum(
                SECTION_VALIDATION, subsection, key, CodeOwnerConfigValidationPolicy.TRUE));
      } catch (IllegalArgumentException e) {
        logger.atWarning().withCause(e).log(
            "Ignoring invalid value %s for the code owner config validation policy in '%s.config'"
                + " of project %s (parameter %s.%s.%s). Falling back to project-level setting.",
            codeOwnerConfigValidationPolicyString,
            pluginName,
            project.get(),
            SECTION_VALIDATION,
            subsection,
            key);
      }
    }
    return Optional.empty();
  }

  private Optional<Boolean> getCodeOwnerConfigValidationFlagForBranch(
      String subsection, String key, Project.NameKey project, Config pluginConfig) {
    requireNonNull(subsection, "subsection");
    requireNonNull(key, "key");
    requireNonNull(project, "project");
    requireNonNull(pluginConfig, "pluginConfig");

    String codeOwnerConfigValidationFlagString =
        pluginConfig.getString(SECTION_VALIDATION, subsection, key);
    if (codeOwnerConfigValidationFlagString != null) {
      try {
        return Optional.of(pluginConfig.getBoolean(SECTION_VALIDATION, subsection, key, true));
      } catch (IllegalArgumentException e) {
        logger.atWarning().withCause(e).log(
            "Ignoring invalid value %s for %s.%s.%s in '%s.config' of project %s."
                + " Falling back to project-level setting.",
            codeOwnerConfigValidationFlagString,
            SECTION_VALIDATION,
            subsection,
            key,
            pluginName,
            project.get());
      }
    }
    return Optional.empty();
  }

  /**
   * Gets the merge commit strategy from the given plugin config with fallback to {@code
   * gerrit.config}.
   *
   * <p>The merge commit strategy defines for merge commits which files require code owner
   * approvals.
   *
   * @param project the name of the project for which the merge commit strategy should be read
   * @param pluginConfig the plugin config from which the merge commit strategy should be read
   * @return the merge commit strategy that should be used
   */
  MergeCommitStrategy getMergeCommitStrategy(Project.NameKey project, Config pluginConfig) {
    requireNonNull(project, "project");
    requireNonNull(pluginConfig, "pluginConfig");

    String mergeCommitStrategyString =
        pluginConfig.getString(
            SECTION_CODE_OWNERS, /* subsection= */ null, KEY_MERGE_COMMIT_STRATEGY);
    if (mergeCommitStrategyString != null) {
      try {
        return pluginConfig.getEnum(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            KEY_MERGE_COMMIT_STRATEGY,
            MergeCommitStrategy.ALL_CHANGED_FILES);
      } catch (IllegalArgumentException e) {
        logger.atWarning().withCause(e).log(
            "Ignoring invalid value %s for merge commit stategy in '%s.config' of project %s."
                + " Falling back to global config or default value.",
            mergeCommitStrategyString, pluginName, project.get());
      }
    }

    try {
      return pluginConfigFromGerritConfig.getEnum(
          KEY_MERGE_COMMIT_STRATEGY, MergeCommitStrategy.ALL_CHANGED_FILES);
    } catch (IllegalArgumentException e) {
      logger.atWarning().withCause(e).log(
          "Ignoring invalid value %s for merge commit stategy in gerrit.config (parameter plugin.%s.%s)."
              + " Falling back to default value %s.",
          pluginConfigFromGerritConfig.getString(KEY_MERGE_COMMIT_STRATEGY),
          pluginName,
          KEY_MERGE_COMMIT_STRATEGY,
          MergeCommitStrategy.ALL_CHANGED_FILES);
      return MergeCommitStrategy.ALL_CHANGED_FILES;
    }
  }

  /**
   * Gets whether an implicit code owner approval from the last uploader is assumed from the given
   * plugin config with fallback to {@code gerrit.config}.
   *
   * @param project the name of the project for which the configuration should be read
   * @param pluginConfig the plugin config from which the configuration should be read.
   * @return whether an implicit code owner approval from the last uploader is assumed
   */
  EnableImplicitApprovals getEnableImplicitApprovals(Project.NameKey project, Config pluginConfig) {
    requireNonNull(project, "project");
    requireNonNull(pluginConfig, "pluginConfig");

    String enableImplicitApprovalsString =
        pluginConfig.getString(
            SECTION_CODE_OWNERS, /* subsection= */ null, KEY_ENABLE_IMPLICIT_APPROVALS);
    if (enableImplicitApprovalsString != null) {
      try {
        return pluginConfig.getEnum(
            SECTION_CODE_OWNERS,
            /* subsection= */ null,
            KEY_ENABLE_IMPLICIT_APPROVALS,
            EnableImplicitApprovals.FALSE);
      } catch (IllegalArgumentException e) {
        logger.atWarning().withCause(e).log(
            "Ignoring invalid value %s for enabling implicit approvals in '%s.config' of project"
                + " %s. Falling back to global config or default value.",
            enableImplicitApprovalsString, pluginName, project.get());
      }
    }

    try {
      return pluginConfigFromGerritConfig.getEnum(
          KEY_ENABLE_IMPLICIT_APPROVALS, EnableImplicitApprovals.FALSE);
    } catch (IllegalArgumentException e) {
      logger.atWarning().withCause(e).log(
          "Ignoring invalid value %s for enabling implict approvals in gerrit.config (parameter"
              + " plugin.%s.%s). Falling back to default value %s.",
          pluginConfigFromGerritConfig.getString(KEY_ENABLE_IMPLICIT_APPROVALS),
          pluginName,
          KEY_ENABLE_IMPLICIT_APPROVALS,
          EnableImplicitApprovals.FALSE);
      return EnableImplicitApprovals.FALSE;
    }
  }

  /**
   * Gets the users which are configured as global code owners from the given plugin config with
   * fallback to {@code gerrit.config}.
   *
   * @param pluginConfig the plugin config from which the global code owners should be read.
   * @return the users which are configured as global code owners
   */
  ImmutableSet<CodeOwnerReference> getGlobalCodeOwners(Config pluginConfig) {
    requireNonNull(pluginConfig, "pluginConfig");

    if (pluginConfig.getString(SECTION_CODE_OWNERS, /* subsection= */ null, KEY_GLOBAL_CODE_OWNER)
        != null) {
      return Arrays.stream(
              pluginConfig.getStringList(
                  SECTION_CODE_OWNERS, /* subsection= */ null, KEY_GLOBAL_CODE_OWNER))
          .filter(value -> !value.trim().isEmpty())
          .map(CodeOwnerReference::create)
          .collect(toImmutableSet());
    }

    return Arrays.stream(pluginConfigFromGerritConfig.getStringList(KEY_GLOBAL_CODE_OWNER))
        .filter(value -> !value.trim().isEmpty())
        .map(CodeOwnerReference::create)
        .collect(toImmutableSet());
  }

  /**
   * Gets the users which are exempted from requiring code owner approvals.
   *
   * <p>If a user is exempted from requiring code owner approvals changes that are uploaded by this
   * user are automatically code-owner approved.
   *
   * @param pluginConfig the plugin config from which the exempted users should be read.
   * @return the users which are exempted from requiring code owner approvals
   */
  ImmutableSet<String> getExemptedUsers(Config pluginConfig) {
    requireNonNull(pluginConfig, "pluginConfig");

    if (pluginConfig.getString(SECTION_CODE_OWNERS, /* subsection= */ null, KEY_EXEMPTED_USER)
        != null) {
      return Arrays.stream(
              pluginConfig.getStringList(
                  SECTION_CODE_OWNERS, /* subsection= */ null, KEY_EXEMPTED_USER))
          .filter(value -> !value.trim().isEmpty())
          .collect(toImmutableSet());
    }

    return Arrays.stream(pluginConfigFromGerritConfig.getStringList(KEY_EXEMPTED_USER))
        .filter(value -> !value.trim().isEmpty())
        .collect(toImmutableSet());
  }

  /**
   * Gets an URL that leads to an information page about overrides.
   *
   * <p>The URL is retrieved from the given plugin config, with fallback to the {@code
   * gerrit.config}.
   *
   * @param pluginConfig the plugin config from which the override info URL should be read.
   * @return URL that leads to an information page about overrides, {@link Optional#empty()} if no
   *     such URL is configured
   */
  Optional<String> getOverrideInfoUrl(Config pluginConfig) {
    requireNonNull(pluginConfig, "pluginConfig");

    String fileExtension =
        pluginConfig.getString(SECTION_CODE_OWNERS, /* subsection= */ null, KEY_OVERRIDE_INFO_URL);
    if (fileExtension != null) {
      return Optional.of(fileExtension);
    }

    return Optional.ofNullable(pluginConfigFromGerritConfig.getString(KEY_OVERRIDE_INFO_URL));
  }
}
