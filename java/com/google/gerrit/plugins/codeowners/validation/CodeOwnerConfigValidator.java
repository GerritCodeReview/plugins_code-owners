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

package com.google.gerrit.plugins.codeowners.validation;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.plugins.codeowners.backend.CodeOwners.getInvalidConfigCause;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.plugins.codeowners.backend.ChangedFiles;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportType;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnersInternalServerErrorException;
import com.google.gerrit.plugins.codeowners.backend.PathCodeOwners;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfigSnapshot;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.InvalidPluginConfigurationException;
import com.google.gerrit.plugins.codeowners.common.ChangedFile;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerConfigValidationPolicy;
import com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.MergeValidationException;
import com.google.gerrit.server.git.validators.MergeValidationListener;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Validates modifications to the code owner config files.
 *
 * <p>The validations are best effort to prevent invalid code owner configs from entering the
 * repository, but we cannot prevent it in all cases. Still the validation is useful since it
 * prevents most issues and also gives quick feedback to uploaders about typos (e.g. if an email is
 * misspelled it's not breaking anything, but the intended change of the uploader is not working).
 *
 * <p>Code owner configs are not validated when:
 *
 * <ul>
 *   <li>the {@code code-owners} plugin is not installed (this means when the {@code code-owners}
 *       plugin gets installed it is possible that invalid code owner configs already exist in the
 *       repository)
 *   <li>the code owners functionality is disabled for the repository or branch (this means when the
 *       code owners functionality gets enabled it is possible that invalid code owner configs
 *       already exist in the repository)
 *   <li>the {@code code-owners} plugin configuration is invalid (in this case we don't know which
 *       files are code owner config files, so we allow all uploads rather than blocking all
 *       uploads, to reduce the risk of breaking the plugin configuration {@code
 *       com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfigValidator} takes care to
 *       validate modifications to the plugin configuration)
 *   <li>updates happen behind Gerrit's back (e.g. pushes that bypass Gerrit)
 * </ul>
 *
 * <p>In addition it is possible that code owner config files get invalid after they have been
 * submitted:
 *
 * <ul>
 *   <li>configuration parameters that are relevant for the validation are changed (e.g. the account
 *       visibility is changed, another code owners backend is configured which now uses a different
 *       syntax or different names for code owner config files, or the file extension for code owner
 *       config file is set/changed)
 *   <li>emails of user may change so that emails in code owner configs can no longer be resolved
 *   <li>imported code owner config files may get deleted or renamed so that the import references
 *       can no longer be resolved
 * </ul>
 */
@Singleton
public class CodeOwnerConfigValidator implements CommitValidationListener, MergeValidationListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String pluginName;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final GitRepositoryManager repoManager;
  private final ChangedFiles changedFiles;
  private final Provider<CodeOwnerResolver> codeOwnerResolverProvider;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final ChangeNotes.Factory changeNotesFactory;
  private final PatchSetUtil patchSetUtil;
  private final IdentifiedUser.GenericFactory userFactory;

  @Inject
  CodeOwnerConfigValidator(
      @PluginName String pluginName,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      GitRepositoryManager repoManager,
      ChangedFiles changedFiles,
      Provider<CodeOwnerResolver> codeOwnerResolver,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      ChangeNotes.Factory changeNotesFactory,
      PatchSetUtil patchSetUtil,
      IdentifiedUser.GenericFactory userFactory) {
    this.pluginName = pluginName;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.repoManager = repoManager;
    this.changedFiles = changedFiles;
    this.codeOwnerResolverProvider = codeOwnerResolver;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.changeNotesFactory = changeNotesFactory;
    this.patchSetUtil = patchSetUtil;
    this.userFactory = userFactory;
  }

  @Override
  public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
      throws CommitValidationException {
    try (TraceTimer traceTimer =
        TraceContext.newTimer(
            "Validate code owner config files on commit received",
            Metadata.builder()
                .projectName(receiveEvent.project.getName())
                .commit(receiveEvent.commit.name())
                .branchName(receiveEvent.refName)
                .username(receiveEvent.user.getLoggableName())
                .build())) {
      CodeOwnerConfigValidationPolicy codeOwnerConfigValidationPolicy =
          codeOwnersPluginConfiguration
              .getProjectConfig(receiveEvent.getProjectNameKey())
              .getCodeOwnerConfigValidationPolicyForCommitReceived(receiveEvent.refName);
      logger.atFine().log("codeOwnerConfigValidationPolicy = %s", codeOwnerConfigValidationPolicy);
      Optional<ValidationResult> validationResult;
      if (!codeOwnerConfigValidationPolicy.runValidation()) {
        validationResult =
            Optional.of(
                ValidationResult.create(
                    pluginName,
                    "skipping validation of code owner config files",
                    new CommitValidationMessage(
                        "code owners config validation is disabled", ValidationMessage.Type.HINT)));
      } else {
        try {
          validationResult =
              validateCodeOwnerConfig(
                  receiveEvent.getBranchNameKey(),
                  receiveEvent.repoConfig,
                  receiveEvent.revWalk,
                  receiveEvent.commit,
                  receiveEvent.user,
                  codeOwnerConfigValidationPolicy.isForced());
        } catch (RuntimeException e) {
          if (!codeOwnerConfigValidationPolicy.isDryRun()) {
            throw e;
          }

          // The validation was executed as dry-run and failures during the validation should not
          // cause an error. Hence we swallow the exception here.
          logger.atWarning().withCause(e).log(
              "ignoring failure during validation of code owner config files in revision %s"
                  + " (project = %s, branch = %s) because the validation was performed as dry-run",
              receiveEvent.commit.getName(),
              receiveEvent.getBranchNameKey().project(),
              receiveEvent.getBranchNameKey().branch());
          validationResult = Optional.empty();
        }
      }
      if (!validationResult.isPresent()) {
        return ImmutableList.of();
      }

      logger.atFine().log("validation result = %s", validationResult.get());
      return validationResult
          .get()
          .processForOnCommitReceived(codeOwnerConfigValidationPolicy.isDryRun());
    }
  }

  @Override
  public void onPreMerge(
      Repository repository,
      CodeReviewRevWalk revWalk,
      CodeReviewCommit commit,
      ProjectState projectState,
      BranchNameKey branchNameKey,
      PatchSet.Id patchSetId,
      IdentifiedUser caller)
      throws MergeValidationException {
    try (TraceTimer traceTimer =
        TraceContext.newTimer(
            "Validate code owner config files on pre merge",
            Metadata.builder()
                .projectName(branchNameKey.project().get())
                .commit(commit.name())
                .branchName(branchNameKey.branch())
                .username(caller.getLoggableName())
                .patchSetId(patchSetId.get())
                .build())) {
      CodeOwnerConfigValidationPolicy codeOwnerConfigValidationPolicy =
          codeOwnersPluginConfiguration
              .getProjectConfig(branchNameKey.project())
              .getCodeOwnerConfigValidationPolicyForSubmit(branchNameKey.branch());
      logger.atFine().log("codeOwnerConfigValidationPolicy = %s", codeOwnerConfigValidationPolicy);
      Optional<ValidationResult> validationResult;
      if (!codeOwnerConfigValidationPolicy.runValidation()) {
        validationResult =
            Optional.of(
                ValidationResult.create(
                    pluginName,
                    "skipping validation of code owner config files",
                    new CommitValidationMessage(
                        "code owners config validation is disabled", ValidationMessage.Type.HINT)));
      } else {
        try {
          ChangeNotes changeNotes =
              changeNotesFactory.create(projectState.getNameKey(), commit.change().getId());
          PatchSet patchSet = patchSetUtil.get(changeNotes, patchSetId);
          IdentifiedUser patchSetUploader = userFactory.create(patchSet.uploader());
          validationResult =
              validateCodeOwnerConfig(
                  branchNameKey,
                  repository.getConfig(),
                  revWalk,
                  commit,
                  patchSetUploader,
                  codeOwnerConfigValidationPolicy.isForced());
        } catch (RuntimeException e) {
          if (!codeOwnerConfigValidationPolicy.isDryRun()) {
            throw e;
          }

          // The validation was executed as dry-run and failures during the validation should not
          // cause an error. Hence we swallow the exception here.
          logger.atFine().withCause(e).log(
              "ignoring failure during validation of code owner config files in revision %s"
                  + " (project = %s, branch = %s) because the validation was performed as dry-run",
              commit.name(), branchNameKey.project(), branchNameKey.branch());
          validationResult = Optional.empty();
        }
      }
      if (validationResult.isPresent()) {
        logger.atFine().log("validation result = %s", validationResult.get());
        validationResult.get().processForOnPreMerge(codeOwnerConfigValidationPolicy.isDryRun());
      }
    }
  }

  /**
   * Validates the code owner config files which are newly added or modified in the given commit.
   *
   * @param branchNameKey the project and branch that contains the provided commit or for which the
   *     commit is being pushed
   * @param revWalk the rev walk that should be used to load revCommit
   * @param revCommit the commit for which newly added and modified code owner configs should be
   *     validated
   * @param user user for which the code owner visibility checks should be performed
   * @param force whether the validation should be done even if the code owners functionality is
   *     disabled for the branch
   * @return the validation result, {@link Optional#empty()} if no validation is performed because
   *     the given commit doesn't contain newly added or modified code owner configs
   */
  private Optional<ValidationResult> validateCodeOwnerConfig(
      BranchNameKey branchNameKey,
      Config repoConfig,
      RevWalk revWalk,
      RevCommit revCommit,
      IdentifiedUser user,
      boolean force) {
    CodeOwnersPluginConfigSnapshot codeOwnersConfig =
        codeOwnersPluginConfiguration.getProjectConfig(branchNameKey.project());
    logger.atFine().log("force = %s", force);
    if (!force && codeOwnersConfig.isDisabled(branchNameKey.branch())) {
      return Optional.of(
          ValidationResult.create(
              pluginName,
              "skipping validation of code owner config files",
              new CommitValidationMessage(
                  "code-owners functionality is disabled", ValidationMessage.Type.HINT)));
    }
    if (codeOwnersConfig.areCodeOwnerConfigsReadOnly()) {
      return Optional.of(
          ValidationResult.create(
              pluginName,
              "modifying code owner config files not allowed",
              new CommitValidationMessage(
                  "code owner config files are configured to be read-only",
                  ValidationMessage.Type.ERROR)));
    }

    try {
      CodeOwnerBackend codeOwnerBackend = codeOwnersConfig.getBackend(branchNameKey.branch());

      // For merge commits, always do the comparison against the destination branch
      // (MergeCommitStrategy.ALL_CHANGED_FILES). Doing the comparison against the auto-merge
      // (MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION) is not possible because the auto-merge
      // is loaded via the PatchListCache to which we cannot pass the rev walk which should be used
      // to load the newly created merge commit and hence trying to load it from PatchListCache
      // would fail with a missing object exception. This is why we use
      // MergeCommitStrategy.ALL_CHANGED_FILES here even if
      // MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION is configured.
      ImmutableList<ChangedFile> modifiedCodeOwnerConfigFiles =
          changedFiles
              .compute(
                  branchNameKey.project(),
                  repoConfig,
                  revWalk,
                  revCommit,
                  MergeCommitStrategy.ALL_CHANGED_FILES)
              .stream()
              // filter out deletions (files without new path)
              .filter(changedFile -> changedFile.newPath().isPresent())
              // filter out non code owner config files
              .filter(
                  changedFile ->
                      codeOwnerBackend.isCodeOwnerConfigFile(
                          branchNameKey.project(),
                          Paths.get(changedFile.newPath().get().toString())
                              .getFileName()
                              .toString()))
              .collect(toImmutableList());

      if (modifiedCodeOwnerConfigFiles.isEmpty()) {
        return Optional.empty();
      }

      // validate the code owner config files
      return Optional.of(
          ValidationResult.create(
              pluginName,
              modifiedCodeOwnerConfigFiles.stream()
                  .flatMap(
                      changedFile ->
                          validateCodeOwnerConfig(
                              user,
                              codeOwnerBackend,
                              branchNameKey,
                              changedFile,
                              revWalk,
                              revCommit))));
    } catch (InvalidPluginConfigurationException e) {
      // If the code-owners plugin configuration is invalid we cannot get the code owners backend
      // and hence we are not able to detect and validate code owner config files. Instead of
      // failing in this case (which would block all change uploads) we only log a warning and
      // accept that it's possible to add invalid code owner configs while the plugin configuration
      // is invalid.
      logger.atWarning().log(
          String.format(
              "cannot validate code owner config files due to invalid code-owners plugin"
                  + " configuration: %s",
              e.getMessage()));
      return Optional.of(
          ValidationResult.create(
              pluginName,
              "skipping validation of code owner config files",
              new CommitValidationMessage(
                  "code-owners plugin configuration is invalid,"
                      + " cannot validate code owner config files",
                  ValidationMessage.Type.WARNING)));
    } catch (IOException | PatchListNotAvailableException e) {
      String errorMessage =
          String.format(
              "failed to validate code owner config files in revision %s"
                  + " (project = %s, branch = %s)",
              revCommit.getName(), branchNameKey.project(), branchNameKey.branch());
      logger.atSevere().withCause(e).log(errorMessage);
      throw new CodeOwnersInternalServerErrorException(errorMessage, e);
    }
  }

  /**
   * Validates the specified code owner config and returns a stream of validation messages.
   *
   * @param user user for which the code owner visibility checks should be performed
   * @param codeOwnerBackend the code owner backend from which the code owner config can be loaded
   * @param branchNameKey the project and branch of the code owner config
   * @param changedFile the changed file that represents the code owner config
   * @param revWalk the rev walk that should be used to load revCommit
   * @param revCommit the commit from which the code owner config should be loaded
   * @return a stream of validation messages that describe issues with the code owner config, an
   *     empty stream if there are no issues
   */
  public Stream<CommitValidationMessage> validateCodeOwnerConfig(
      IdentifiedUser user,
      CodeOwnerBackend codeOwnerBackend,
      BranchNameKey branchNameKey,
      ChangedFile changedFile,
      RevWalk revWalk,
      RevCommit revCommit) {
    requireNonNull(user, "user");
    requireNonNull(codeOwnerBackend, "codeOwnerBackend");
    requireNonNull(branchNameKey, "branchNameKey");
    requireNonNull(changedFile, "changedFile");
    requireNonNull(revWalk, "revWalk");
    requireNonNull(revCommit, "revCommit");

    if (!changedFile.newPath().isPresent()) {
      // The code owner config file was deleted. Hence we do not need to do any validation.
      return Stream.of();
    }

    CodeOwnerConfig codeOwnerConfig;
    try {
      // Load the code owner config. If the code owner config is not parsable this will fail with a
      // InvalidConfigException (wrapped in a CodeOwnersInternalServerErrorException) that we handle
      // below.
      CodeOwnerConfig.Key codeOwnerConfigKey =
          createCodeOwnerConfigKey(branchNameKey, changedFile.newPath().get());
      codeOwnerConfig =
          codeOwnerBackend
              .getCodeOwnerConfig(codeOwnerConfigKey, revWalk, revCommit)
              // We already know that the path exists, so either the code owner config is
              // successfully loaded (this case) or the loading fails with an exception because the
              // code owner config is not parseable (catch block below), but it cannot happen that
              // the code owner config is not found and an empty Optional is returned.
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          String.format(
                              "code owner config %s not found in revision %s",
                              codeOwnerConfigKey, revCommit.name())));
    } catch (CodeOwnersInternalServerErrorException codeOwnersInternalServerErrorException) {
      // Loading the code owner config has failed.
      Optional<ConfigInvalidException> configInvalidException =
          getInvalidConfigCause(codeOwnersInternalServerErrorException);
      if (!configInvalidException.isPresent()) {
        // Propagate any failure that is not related to the contents of the code owner config.
        throw codeOwnersInternalServerErrorException;
      }

      // The exception was caused by a ConfigInvalidException. This means loading the code owner
      // config failed because it is not parseable.

      // Convert the message from the InvalidConfigException into a validation message and return
      // it.
      return Stream.of(
          new CommitValidationMessage(
              configInvalidException.get().getMessage(),
              getValidationMessageTypeForParsingError(
                  codeOwnerBackend, branchNameKey, changedFile, revWalk, revCommit)));
    }

    // The code owner config was successfully loaded and parsed.

    // We only report new issues as errors. If the same issues already existed in the base version
    // we just report them as warnings. To know which issues already existed in the base version
    // we must load it, what we do here, and then run the validation on it.
    Optional<CodeOwnerConfig> baseCodeOwnerConfig;
    try {
      baseCodeOwnerConfig =
          getBaseCodeOwnerConfig(codeOwnerBackend, branchNameKey, changedFile, revWalk, revCommit);
    } catch (CodeOwnersInternalServerErrorException codeOwnersInternalServerErrorException) {
      if (getInvalidConfigCause(codeOwnersInternalServerErrorException).isPresent()) {
        // The base code owner config is non-parseable. Since the update makes the code owner
        // config parseable, it is a good update even if the code owner config still contains
        // issues. Hence in this case we downgrade all validation errors in the new version to
        // warnings so that the update is not blocked.
        return validateCodeOwnerConfig(
                branchNameKey, revWalk, user, codeOwnerBackend, codeOwnerConfig)
            .map(CodeOwnerConfigValidator::downgradeErrorToWarning);
      }

      // Propagate any exception that was not caused by the content of the code owner config.
      throw codeOwnersInternalServerErrorException;
    }

    // Validate the parsed code owner config.
    if (baseCodeOwnerConfig.isPresent()) {
      return validateCodeOwnerConfig(
          branchNameKey,
          revWalk,
          user,
          codeOwnerBackend,
          codeOwnerConfig,
          baseCodeOwnerConfig.get());
    }
    return validateCodeOwnerConfig(branchNameKey, revWalk, user, codeOwnerBackend, codeOwnerConfig);
  }

  /**
   * Create the key for a code owner config from a given file path.
   *
   * @param branchNameKey the project and branch of the code owner config
   * @param filePath the file path of the code owner config
   * @return the key of the code owner config
   */
  private CodeOwnerConfig.Key createCodeOwnerConfigKey(BranchNameKey branchNameKey, Path filePath) {
    Path folderPath =
        filePath.getParent() != null
            ? JgitPath.of(filePath.getParent()).getAsAbsolutePath()
            : Paths.get("/");
    String fileName = filePath.getFileName().toString();
    return CodeOwnerConfig.Key.create(branchNameKey, folderPath, fileName);
  }

  /**
   * Loads and returns the base code owner config if it exists.
   *
   * <p>Throws a {@link ConfigInvalidException} (wrapped in a {@link
   * CodeOwnersInternalServerErrorException} if the base code owner config exists, but is not
   * parseable.
   *
   * @param codeOwnerBackend the code owner backend from which the base code owner config can be
   *     loaded
   * @param branchNameKey the project and branch of the base code owner config
   * @param changedFile the changed file of the code owner config that contains the path of the base
   *     code owner config as old path
   * @param revWalk rev walk that should be used to load the base code owner config
   * @param revCommit the commit of the code owner config for which the base code owner config
   *     should be loaded
   * @return the loaded base code owner config, {@link Optional#empty()} if no base code owner
   *     config exists (e.g. if the code owner config is newly created)
   */
  private Optional<CodeOwnerConfig> getBaseCodeOwnerConfig(
      CodeOwnerBackend codeOwnerBackend,
      BranchNameKey branchNameKey,
      ChangedFile changedFile,
      RevWalk revWalk,
      RevCommit revCommit) {
    if (changedFile.oldPath().isPresent()) {
      Optional<ObjectId> parentRevision = getParentRevision(branchNameKey.project(), revCommit);
      if (parentRevision.isPresent()) {
        CodeOwnerConfig.Key baseCodeOwnerConfigKey =
            createCodeOwnerConfigKey(branchNameKey, changedFile.oldPath().get());
        return codeOwnerBackend.getCodeOwnerConfig(
            baseCodeOwnerConfigKey, revWalk, parentRevision.get());
      }
    }
    return Optional.empty();
  }

  /**
   * Returns the {@link com.google.gerrit.server.git.validators.ValidationMessage.Type} (FATAL or
   * WARNING) that should be used for a parsing error of the code owner config file (specified as
   * {@code changedFile}).
   *
   * <p>If {@link com.google.gerrit.server.git.validators.ValidationMessage.Type#FATAL} is returned
   * the upload will be blocked, if {@link
   * com.google.gerrit.server.git.validators.ValidationMessage.Type#WARNING} is returned the upload
   * can succeed and the parsing error will only be shown as warning.
   *
   * <p>If a previous version of the code owner config exists and the previous version was also
   * non-parseable, we want to allow the upload even if the new version is still non-parseable, as
   * it is not making anything worse. Hence in this case the parsing error should be returned as
   * {@link com.google.gerrit.server.git.validators.ValidationMessage.Type#WARNING}, whereas a new
   * parsing error should be returned as {@link
   * com.google.gerrit.server.git.validators.ValidationMessage.Type#FATAL}.
   *
   * @param codeOwnerBackend the code owner backend from which the code owner config can be loaded
   * @param branchNameKey the project and branch of the code owner config
   * @param changedFile the changed file that represents the code owner config
   * @param revWalk rev walk that should be used to load the code owner config
   * @param revCommit the commit from which the code owner config should be loaded
   * @return the {@link com.google.gerrit.server.git.validators.ValidationMessage.Type} (ERROR or
   *     WARNING) that should be used for parsing error of a code owner config file
   */
  private ValidationMessage.Type getValidationMessageTypeForParsingError(
      CodeOwnerBackend codeOwnerBackend,
      BranchNameKey branchNameKey,
      ChangedFile changedFile,
      RevWalk revWalk,
      RevCommit revCommit) {
    //
    if (changedFile.oldPath().isPresent()) {
      // A previous version of the code owner config exists.
      ObjectId parentRevision =
          getParentRevision(branchNameKey.project(), revCommit)
              // Since there is an old path a parent revision must exist.
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          String.format(
                              "parent revision for revision %s in project %s not found",
                              revCommit.name(), branchNameKey.project().get())));
      try {
        // Try to load the code owner config from the parent revision to see if it was parseable
        // there.
        CodeOwnerConfig.Key baseCodeOwnerConfigKey =
            createCodeOwnerConfigKey(branchNameKey, changedFile.oldPath().get());
        codeOwnerBackend.getCodeOwnerConfig(baseCodeOwnerConfigKey, revWalk, parentRevision);
        // The code owner config at the parent revision is parseable. This means the parsing error
        // is introduced by the new commit and we should block uploading it, which we achieve by
        // setting the validation message type to fatal.
        return ValidationMessage.Type.FATAL;
      } catch (CodeOwnersInternalServerErrorException codeOwnersInternalServerErrorException) {
        // Loading the base code owner config has failed.
        if (getInvalidConfigCause(codeOwnersInternalServerErrorException).isPresent()) {
          // The code owner config was already non-parseable before, hence we do not need to
          // block the upload if the code owner config is still non-parseable.
          // Using warning as type means that uploads are not blocked.
          return ValidationMessage.Type.WARNING;
        }
        // Propagate any failure that is not related to the contents of the code owner config.
        throw codeOwnersInternalServerErrorException;
      }
    }

    // The code owner config is newly created. Hence the parsing error comes from the commit
    // that is being pushed and we want to block it from uploading. To do this we set the
    // validation message type to fatal.
    return ValidationMessage.Type.FATAL;
  }

  /**
   * Returns the first parent of the given revision.
   *
   * @param project the project that contains the revision
   * @param revCommit the commit for which the first parent should be returned
   * @return the first parent of the given revision, {@link Optional#empty()} if the given revision
   *     has no parent
   */
  private Optional<ObjectId> getParentRevision(Project.NameKey project, RevCommit revCommit) {
    if (revCommit.getParentCount() == 0) {
      return Optional.empty();
    }
    RevCommit firstParent = revCommit.getParent(0);
    logger.atFine().log(
        "first parent of %s in %s is %s", revCommit.name(), project.get(), firstParent.name());
    return Optional.of(firstParent);
  }

  /**
   * Returns a copy of the given commit validation message with type warning if the type the given
   * commit validation message is fatal or error. Otherwise it returns the given commit validation
   * message unchanged.
   */
  private static CommitValidationMessage downgradeErrorToWarning(
      CommitValidationMessage commitValidationMessage) {
    if (CommitValidationMessage.Type.FATAL.equals(commitValidationMessage.getType())
        || CommitValidationMessage.Type.ERROR.equals(commitValidationMessage.getType())) {
      return new CommitValidationMessage(
          commitValidationMessage.getMessage(), ValidationMessage.Type.WARNING);
    }
    return commitValidationMessage;
  }

  /**
   * Validates the given code owner config and returns validation issues as stream.
   *
   * <p>Validation errors that exist in both code owner configs are returned as warning (because
   * they are not newly introduced by the given code owner config).
   *
   * @param branchNameKey the branch and the project
   * @param revWalk rev walk that should be used to load the code owner configs
   * @param user user for which the code owner visibility checks should be performed
   * @param codeOwnerBackend the code owner backend from which the code owner configs were loaded
   * @param codeOwnerConfig the code owner config that should be validated
   * @param baseCodeOwnerConfig the base code owner config
   * @return a stream of validation messages that describe issues with the code owner config, an
   *     empty stream if there are no issues
   */
  private Stream<CommitValidationMessage> validateCodeOwnerConfig(
      BranchNameKey branchNameKey,
      RevWalk revWalk,
      IdentifiedUser user,
      CodeOwnerBackend codeOwnerBackend,
      CodeOwnerConfig codeOwnerConfig,
      CodeOwnerConfig baseCodeOwnerConfig) {
    requireNonNull(codeOwnerConfig, "codeOwnerConfig");
    requireNonNull(baseCodeOwnerConfig, "baseCodeOwnerConfig");

    ImmutableSet<CommitValidationMessage> issuesInBaseVersion =
        validateCodeOwnerConfig(branchNameKey, revWalk, user, codeOwnerBackend, baseCodeOwnerConfig)
            .collect(toImmutableSet());
    return validateCodeOwnerConfig(branchNameKey, revWalk, user, codeOwnerBackend, codeOwnerConfig)
        .map(
            commitValidationMessage ->
                issuesInBaseVersion.contains(commitValidationMessage)
                    ? downgradeErrorToWarning(commitValidationMessage)
                    : commitValidationMessage);
  }

  /**
   * Validates the given code owner config and returns validation issues as stream.
   *
   * @param branchNameKey the branch and the project
   * @param revWalk rev walk that should be used to load the code owner configs from {@code project}
   * @param user user for which the code owner visibility checks should be performed
   * @param codeOwnerBackend the code owner backend from which the code owner config was loaded
   * @param codeOwnerConfig the code owner config that should be validated
   * @return a stream of validation messages that describe issues with the code owner config, an
   *     empty stream if there are no issues
   */
  public Stream<CommitValidationMessage> validateCodeOwnerConfig(
      BranchNameKey branchNameKey,
      RevWalk revWalk,
      IdentifiedUser user,
      CodeOwnerBackend codeOwnerBackend,
      CodeOwnerConfig codeOwnerConfig) {
    requireNonNull(codeOwnerConfig, "codeOwnerConfig");
    return Streams.concat(
        validateCodeOwnerReferences(
            branchNameKey,
            user,
            codeOwnerBackend.getFilePath(codeOwnerConfig.key()),
            codeOwnerConfig),
        validateImports(
            branchNameKey,
            revWalk,
            codeOwnerBackend.getFilePath(codeOwnerConfig.key()),
            codeOwnerConfig));
  }

  /**
   * Validates the code owner references of the given code owner config.
   *
   * @param branchNameKey the branch and the project
   * @param user user for which the code owner visibility checks should be performed
   * @param codeOwnerConfigFilePath the path of the code owner config file which contains the code
   *     owner references
   * @param codeOwnerConfig the code owner config for which the code owner references should be
   *     validated
   * @return a stream of validation messages that describe issues with the code owner references, an
   *     empty stream if there are no issues
   */
  private Stream<CommitValidationMessage> validateCodeOwnerReferences(
      BranchNameKey branchNameKey,
      IdentifiedUser user,
      Path codeOwnerConfigFilePath,
      CodeOwnerConfig codeOwnerConfig) {
    return codeOwnerConfig.codeOwnerSets().stream()
        .flatMap(codeOwnerSet -> codeOwnerSet.codeOwners().stream())
        .map(
            codeOwnerReference ->
                validateCodeOwnerReference(
                    branchNameKey, user, codeOwnerConfigFilePath, codeOwnerReference))
        .filter(Optional::isPresent)
        .map(Optional::get);
  }

  /**
   * Validates a code owner reference.
   *
   * @param branchNameKey the branch and the project
   * @param user user for which the code owner visibility checks should be performed
   * @param codeOwnerConfigFilePath the path of the code owner config file which contains the code
   *     owner reference
   * @param codeOwnerReference the code owner reference that should be validated.
   * @return a validation message describing the issue with the code owner reference, {@link
   *     Optional#empty()} if there is no issue
   */
  private Optional<CommitValidationMessage> validateCodeOwnerReference(
      BranchNameKey branchNameKey,
      IdentifiedUser user,
      Path codeOwnerConfigFilePath,
      CodeOwnerReference codeOwnerReference) {
    CodeOwnerResolver codeOwnerResolver = codeOwnerResolverProvider.get().forUser(user);
    if (!codeOwnerResolver.isEmailDomainAllowed(codeOwnerReference.email()).get()) {
      return nonResolvableCodeOwner(
          branchNameKey,
          String.format(
              "the domain of the code owner email '%s' in '%s' is not allowed for code owners",
              codeOwnerReference.email(), codeOwnerConfigFilePath));
    }

    // Check if the code owner reference is resolvable.
    if (codeOwnerResolver.isResolvable(codeOwnerReference)) {
      // The code owner reference was successfully resolved to at least one code owner.
      return Optional.empty();
    }

    // It was not possible to resolve the code owner reference. Possible reasons: no such account
    // exists, the code owner is not visible, the email of the code owner is not visible (see
    // CodeOwerResolver for details). We intentionally return the same generic message in all these
    // cases so that uploaders cannot probe emails for existence (e.g. they cannot add an email and
    // conclude from the error message whether the email exists).
    return nonResolvableCodeOwner(
        branchNameKey,
        String.format(
            "code owner email '%s' in '%s' cannot be resolved for %s",
            codeOwnerReference.email(), codeOwnerConfigFilePath, user.getLoggableName()));
  }

  /**
   * Validates the imports of the given code owner config.
   *
   * @param branchNameKey the branch and the project
   * @param revWalk rev walk that should be used to load the code owner configs from {@code project}
   * @param codeOwnerConfigFilePath the path of the code owner config file which contains the code
   *     owner config
   * @param codeOwnerConfig the code owner config for which the imports should be validated
   * @return a stream of validation messages that describe issues with the imports, an empty stream
   *     if there are no issues
   */
  private Stream<CommitValidationMessage> validateImports(
      BranchNameKey branchNameKey,
      RevWalk revWalk,
      Path codeOwnerConfigFilePath,
      CodeOwnerConfig codeOwnerConfig) {
    return Streams.concat(
            codeOwnerConfig.imports().stream()
                .map(
                    codeOwnerConfigReference ->
                        validateCodeOwnerConfigReference(
                            branchNameKey,
                            revWalk,
                            codeOwnerConfigFilePath,
                            codeOwnerConfig.key(),
                            codeOwnerConfig.revision(),
                            CodeOwnerConfigImportType.GLOBAL,
                            codeOwnerConfigReference)),
            codeOwnerConfig.codeOwnerSets().stream()
                .flatMap(codeOwnerSet -> codeOwnerSet.imports().stream())
                .map(
                    codeOwnerConfigReference ->
                        validateCodeOwnerConfigReference(
                            branchNameKey,
                            revWalk,
                            codeOwnerConfigFilePath,
                            codeOwnerConfig.key(),
                            codeOwnerConfig.revision(),
                            CodeOwnerConfigImportType.PER_FILE,
                            codeOwnerConfigReference)))
        .filter(Optional::isPresent)
        .map(Optional::get);
  }

  /**
   * Validates a code owner config reference.
   *
   * @param branchNameKey the branch and the project
   * @param revWalk rev walk that should be used to load the code owner configs from {@code project}
   * @param codeOwnerConfigFilePath the path of the code owner config file which contains the code
   *     owner config reference
   * @param keyOfImportingCodeOwnerConfig key of the importing code owner config
   * @param codeOwnerConfigRevision the commit from which the code owner config which contains the
   *     code owner config reference was loaded
   * @param importType the type of the import
   * @param codeOwnerConfigReference the code owner config reference that should be validated.
   * @return a validation message describing the issue with the code owner config reference, {@link
   *     Optional#empty()} if there is no issue
   */
  private Optional<CommitValidationMessage> validateCodeOwnerConfigReference(
      BranchNameKey branchNameKey,
      RevWalk revWalk,
      Path codeOwnerConfigFilePath,
      CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig,
      ObjectId codeOwnerConfigRevision,
      CodeOwnerConfigImportType importType,
      CodeOwnerConfigReference codeOwnerConfigReference) {
    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        PathCodeOwners.createKeyForImportedCodeOwnerConfig(
            keyOfImportingCodeOwnerConfig, codeOwnerConfigReference);

    if (isSelfImport(keyOfImportingCodeOwnerConfig, keyOfImportedCodeOwnerConfig)) {
      return nonResolvableImport(
          importType,
          codeOwnerConfigFilePath,
          "code owner config imports itself",
          ValidationMessage.Type.WARNING);
    }

    Optional<ProjectState> projectState = projectCache.get(keyOfImportedCodeOwnerConfig.project());
    if (!projectState.isPresent() || !isProjectReadable(keyOfImportedCodeOwnerConfig)) {
      // we intentionally use the same error message for non-existing and non-readable projects so
      // that uploaders cannot probe for the existence of projects (e.g. deduce from the error
      // message whether a project exists)
      return nonResolvableImport(
          branchNameKey,
          importType,
          codeOwnerConfigFilePath,
          String.format("project '%s' not found", keyOfImportedCodeOwnerConfig.project().get()));
    }

    if (!projectState.get().statePermitsRead()) {
      return nonResolvableImport(
          branchNameKey,
          importType,
          codeOwnerConfigFilePath,
          String.format(
              "project '%s' has state '%s' that doesn't permit read",
              keyOfImportedCodeOwnerConfig.project().get(),
              projectState.get().getProject().getState().name()));
    }

    Optional<ObjectId> revision =
        getRevision(
            keyOfImportingCodeOwnerConfig, codeOwnerConfigRevision, keyOfImportedCodeOwnerConfig);
    if (!revision.isPresent() || !isBranchReadable(keyOfImportedCodeOwnerConfig)) {
      // we intentionally use the same error message for non-existing and non-readable branches so
      // that uploaders cannot probe for the existence of branches (e.g. deduce from the error
      // message whether a branch exists)
      return nonResolvableImport(
          branchNameKey,
          importType,
          codeOwnerConfigFilePath,
          String.format(
              "branch '%s' not found in project '%s'",
              keyOfImportedCodeOwnerConfig.shortBranchName(),
              keyOfImportedCodeOwnerConfig.project().get()));
    }

    CodeOwnerBackend codeOwnerBackend =
        codeOwnersPluginConfiguration
            .getProjectConfig(keyOfImportedCodeOwnerConfig.project())
            .getBackend(keyOfImportedCodeOwnerConfig.branchNameKey().branch());
    if (!codeOwnerBackend.isCodeOwnerConfigFile(
        keyOfImportedCodeOwnerConfig.project(), codeOwnerConfigReference.fileName())) {
      return nonResolvableImport(
          branchNameKey,
          importType,
          codeOwnerConfigFilePath,
          String.format(
              "'%s' is not a code owner config file", codeOwnerConfigReference.filePath()));
    }

    try {
      // If a code owner config is imported from the same project, we must use the provided rev
      // walk, otherwise the revision may not be visible yet and trying to load a code owner config
      // from it could fail with MissingObjectException.
      Optional<CodeOwnerConfig> importedCodeOwnerConfig =
          keyOfImportedCodeOwnerConfig.project().equals(branchNameKey.project())
              ? codeOwnerBackend.getCodeOwnerConfig(
                  keyOfImportedCodeOwnerConfig, revWalk, revision.get())
              : codeOwnerBackend.getCodeOwnerConfig(keyOfImportedCodeOwnerConfig, revision.get());
      if (!importedCodeOwnerConfig.isPresent()) {
        return nonResolvableImport(
            branchNameKey,
            importType,
            codeOwnerConfigFilePath,
            String.format(
                "'%s' does not exist (project = %s, branch = %s, revision = %s)",
                codeOwnerConfigReference.filePath(),
                keyOfImportedCodeOwnerConfig.branchNameKey().project().get(),
                keyOfImportedCodeOwnerConfig.branchNameKey().shortName(),
                revision.get().name()));
      }
    } catch (CodeOwnersInternalServerErrorException codeOwnersInternalServerErrorException) {
      if (getInvalidConfigCause(codeOwnersInternalServerErrorException).isPresent()) {
        // The imported code owner config is non-parseable.
        return nonResolvableImport(
            branchNameKey,
            importType,
            codeOwnerConfigFilePath,
            String.format(
                "'%s' is not parseable (project = %s, branch = %s)",
                codeOwnerConfigReference.filePath(),
                keyOfImportedCodeOwnerConfig.branchNameKey().project().get(),
                keyOfImportedCodeOwnerConfig.branchNameKey().shortName()));
      }

      // Propagate any exception that was not caused by the content of the code owner config.
      throw codeOwnersInternalServerErrorException;
    }

    // no issue found
    return Optional.empty();
  }

  /** Whether the importing code owner config is the same as the imported code owner config. */
  private boolean isSelfImport(
      CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig,
      CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig) {
    return keyOfImportingCodeOwnerConfig.project().equals(keyOfImportedCodeOwnerConfig.project())
        && keyOfImportingCodeOwnerConfig.ref().equals(keyOfImportedCodeOwnerConfig.ref())
        && codeOwnersPluginConfiguration
            .getProjectConfig(keyOfImportingCodeOwnerConfig.project())
            .getBackend(keyOfImportingCodeOwnerConfig.branchNameKey().branch())
            .getFilePath(keyOfImportingCodeOwnerConfig)
            .equals(
                codeOwnersPluginConfiguration
                    .getProjectConfig(keyOfImportedCodeOwnerConfig.project())
                    .getBackend(keyOfImportedCodeOwnerConfig.branchNameKey().branch())
                    .getFilePath(keyOfImportedCodeOwnerConfig));
  }

  private boolean isProjectReadable(CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig) {
    try {
      return permissionBackend
          .currentUser()
          .project(keyOfImportedCodeOwnerConfig.project())
          .test(ProjectPermission.ACCESS);
    } catch (PermissionBackendException e) {
      throw new CodeOwnersInternalServerErrorException(
          "failed to check read permission for project of imported code owner config", e);
    }
  }

  private boolean isBranchReadable(CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig) {
    try {
      return permissionBackend
          .currentUser()
          .project(keyOfImportedCodeOwnerConfig.project())
          .ref(keyOfImportedCodeOwnerConfig.ref())
          .test(RefPermission.READ);
    } catch (PermissionBackendException e) {
      throw new CodeOwnersInternalServerErrorException(
          "failed to check read permission for branch of imported code owner config", e);
    }
  }

  private Optional<ObjectId> getRevision(
      CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig,
      ObjectId codeOwnerConfigRevision,
      CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig) {
    if (keyOfImportingCodeOwnerConfig
        .branchNameKey()
        .equals(keyOfImportedCodeOwnerConfig.branchNameKey())) {
      // load the imported code owner config from the same revision from which the importing code
      // owner config was loaded
      return Optional.of(codeOwnerConfigRevision);
    }

    try (Repository repo = repoManager.openRepository(keyOfImportedCodeOwnerConfig.project())) {
      return Optional.ofNullable(repo.exactRef(keyOfImportedCodeOwnerConfig.ref()))
          .map(Ref::getObjectId);
    } catch (IOException e) {
      throw new CodeOwnersInternalServerErrorException(
          "failed to read revision of import code owner config", e);
    }
  }

  private Optional<CommitValidationMessage> nonResolvableImport(
      BranchNameKey branchNameKey,
      CodeOwnerConfigImportType importType,
      Path codeOwnerConfigFilePath,
      String message) {
    return nonResolvableImport(
        importType,
        codeOwnerConfigFilePath,
        message,
        codeOwnersPluginConfiguration
                .getProjectConfig(branchNameKey.project())
                .rejectNonResolvableImports(branchNameKey.branch())
            ? ValidationMessage.Type.ERROR
            : ValidationMessage.Type.WARNING);
  }

  private Optional<CommitValidationMessage> nonResolvableImport(
      CodeOwnerConfigImportType importType,
      Path codeOwnerConfigFilePath,
      String message,
      ValidationMessage.Type validationMessageType) {
    return Optional.of(
        new CommitValidationMessage(
            String.format(
                "invalid %s import in '%s': %s",
                importType.getType(), codeOwnerConfigFilePath, message),
            validationMessageType));
  }

  private Optional<CommitValidationMessage> nonResolvableCodeOwner(
      BranchNameKey branchNameKey, String message) {
    return Optional.of(
        new CommitValidationMessage(
            message,
            codeOwnersPluginConfiguration
                    .getProjectConfig(branchNameKey.project())
                    .rejectNonResolvableCodeOwners(branchNameKey.branch())
                ? ValidationMessage.Type.ERROR
                : ValidationMessage.Type.WARNING));
  }

  /** The result of validating code owner config files. */
  @AutoValue
  public abstract static class ValidationResult {
    private static final String NO_ISSUES_MSG =
        "code owner config files validated, no issues found";
    private static final String INVALID_MSG = "invalid code owner config files";

    abstract String pluginName();

    abstract String summaryMessage();

    abstract ImmutableList<CommitValidationMessage> validationMessages();

    static ValidationResult create(
        String pluginName, String summaryMessage, CommitValidationMessage commitValidationMessage) {
      return new AutoValue_CodeOwnerConfigValidator_ValidationResult(
          pluginName, summaryMessage, ImmutableList.of(commitValidationMessage));
    }

    static ValidationResult create(
        String pluginName, Stream<CommitValidationMessage> validationMessagesStream) {
      ImmutableList<CommitValidationMessage> validationMessages =
          validationMessagesStream.collect(toImmutableList());
      return new AutoValue_CodeOwnerConfigValidator_ValidationResult(
          pluginName,
          validationMessages.isEmpty() ? NO_ISSUES_MSG : INVALID_MSG,
          validationMessages);
    }

    /**
     * Processes the validation messages for a validation that is done when a commit is received
     * (e.g. on push).
     *
     * <p>Throws a {@link CommitValidationException} if there are errors to make the upload fail.
     *
     * <p>If there are no errors the validation messages are returned so that they can be sent to
     * the client without causing the upload to fail.
     */
    List<CommitValidationMessage> processForOnCommitReceived(boolean dryRun)
        throws CommitValidationException {
      if (!dryRun && hasError()) {
        throw new CommitValidationException(
            withPluginName(summaryMessage()), withPluginName(validationMessages()));
      }

      return validationMessagesWithIncludedSummaryMessage();
    }

    /**
     * Processes the validation messages for a validation that is done on pre-merge (aka on submit).
     *
     * <p>Throws a {@link MergeValidationException} if there are errors to make the submit fail.
     *
     * <p>If there are no errors the validation messages are logged on fine level so that they show
     * up in a trace. Returning the message to the user without failing the submit is not possible.
     */
    void processForOnPreMerge(boolean dryRun) throws MergeValidationException {
      if (!dryRun && hasError()) {
        throw new MergeValidationException(getMessage(validationMessages()));
      }

      if (!validationMessages().isEmpty()) {
        logger.atFine().log(
            "submitting changes to code owner config files with the following messages: %s",
            validationMessagesWithIncludedSummaryMessage());
      } else {
        logger.atFine().log("submitting changes to code owner config files, no issues found");
      }
    }

    /** Checks whether any of the validation messages is an error. */
    private boolean hasError() {
      return validationMessages().stream()
          .anyMatch(
              validationMessage ->
                  ValidationMessage.Type.FATAL.equals(validationMessage.getType())
                      || ValidationMessage.Type.ERROR.equals(validationMessage.getType()));
    }

    private ImmutableList<CommitValidationMessage> validationMessagesWithIncludedSummaryMessage() {
      return ImmutableList.<CommitValidationMessage>builder()
          .add(
              new CommitValidationMessage(
                  withPluginName(summaryMessage()), getValidationMessageTypeForSummaryMessage()))
          .addAll(withPluginName(validationMessages()))
          .build();
    }

    /**
     * Gets the validation message type that should be used for the summary message.
     *
     * <p>The following validation message type will be returned:
     *
     * <ul>
     *   <li>FATAL: if any of the validation message has type fatal
     *   <li>ERROR: if any of the validation message has type error
     *   <li>WARNING: if any of the validation message has type warning and none has type error
     *   <li>HINT: otherwise
     * </ul>
     */
    private ValidationMessage.Type getValidationMessageTypeForSummaryMessage() {
      ValidationMessage.Type validationMessageType = ValidationMessage.Type.HINT;

      if (!validationMessages().isEmpty()) {
        for (CommitValidationMessage validationMessage : validationMessages()) {
          if (ValidationMessage.Type.FATAL.equals(validationMessage.getType())) {
            validationMessageType = ValidationMessage.Type.FATAL;
            break;
          }
          if (ValidationMessage.Type.ERROR.equals(validationMessage.getType())) {
            validationMessageType = ValidationMessage.Type.ERROR;
            break;
          }
          if (ValidationMessage.Type.WARNING.equals(validationMessage.getType())) {
            validationMessageType = ValidationMessage.Type.WARNING;
          }
        }
      }

      return validationMessageType;
    }

    /**
     * Composes a single message out of the given validation messages.
     *
     * <p>Expects that at least 1 validation message is provided.
     *
     * @param validationMessages the validation messages
     * @return the composed message
     */
    private String getMessage(List<CommitValidationMessage> validationMessages) {
      checkState(!validationMessages.isEmpty(), "expected at least 1 validation message");
      StringBuilder msgBuilder = new StringBuilder(withPluginName(summaryMessage())).append(":");
      for (CommitValidationMessage msg : validationMessages) {
        msgBuilder
            .append("\n  ")
            .append(msg.getType().name())
            .append(": ")
            .append(msg.getMessage());
      }
      return msgBuilder.toString();
    }

    private String withPluginName(String message) {
      return "[" + pluginName() + "] " + message;
    }

    private ImmutableList<CommitValidationMessage> withPluginName(
        ImmutableList<CommitValidationMessage> validationMessages) {
      return validationMessages.stream()
          .map(msg -> new CommitValidationMessage(withPluginName(msg.getMessage()), msg.getType()))
          .collect(toImmutableList());
    }
  }
}
