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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.plugins.codeowners.JgitPath;
import com.google.gerrit.plugins.codeowners.backend.ChangedFile;
import com.google.gerrit.plugins.codeowners.backend.ChangedFiles;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.config.InvalidPluginConfigurationException;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

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
 *       syntax or different names for code owner config files)
 *   <li>emails of user may change so that emails in code owner configs can no longer be resolved
 * </ul>
 */
@Singleton
public class CodeOwnerConfigValidator implements CommitValidationListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final GitRepositoryManager repoManager;
  private final ChangedFiles changedFiles;

  @Inject
  CodeOwnerConfigValidator(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      GitRepositoryManager repoManager,
      ChangedFiles changedFiles) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.repoManager = repoManager;
    this.changedFiles = changedFiles;
  }

  @Override
  public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
      throws CommitValidationException {
    if (codeOwnersPluginConfiguration.isDisabled(receiveEvent.getBranchNameKey())) {
      return ImmutableList.of();
    }

    try {
      CodeOwnerBackend codeOwnerBackend =
          codeOwnersPluginConfiguration.getBackend(receiveEvent.getBranchNameKey());

      ImmutableList<CommitValidationMessage> validationMessages =
          // iterate over all changed files
          changedFiles.compute(receiveEvent.getProjectNameKey(), receiveEvent.commit).stream()
              // filter out deletions (files without new path)
              .filter(changedFile -> changedFile.newPath().isPresent())
              // filter out non code owner config files
              .filter(
                  changedFile ->
                      codeOwnerBackend.isCodeOwnerConfigFile(
                          receiveEvent.getProjectNameKey(),
                          Paths.get(changedFile.newPath().get().toString())
                              .getFileName()
                              .toString()))
              // validate the code owner config files
              .flatMap(
                  changedFile ->
                      validateCodeOwnerConfig(
                          codeOwnerBackend,
                          receiveEvent.getBranchNameKey(),
                          changedFile,
                          receiveEvent.commit))
              .collect(toImmutableList());

      // Throw a CommitValidationException if there are errors to make the upload fail.
      if (validationMessages.stream()
          .anyMatch(
              validationMessage ->
                  ValidationMessage.Type.ERROR.equals(validationMessage.getType()))) {
        throw new CommitValidationException("invalid code owner config files", validationMessages);
      }

      // There are no errors. The returned validation messages will be sent to the client, but they
      // are not causing the upload to fail.
      return validationMessages;
    } catch (InvalidPluginConfigurationException e) {
      // If the code-owners plugin configuration is invalid we cannot get the code owners backend
      // and hence we are not able to detect and validate code owner config files. Instead of
      // failing in this case (which would block all change uploads) we only log a warning and
      // accept that it's possible to add invalid code owner configs while the plugin configuration
      // is invalid.
      logger.atWarning().log(
          String.format(
              "cannot validate code owner config files due to invalid code-owners plugin configuration: %s",
              e.getMessage()));
      return ImmutableList.of();
    } catch (IOException e) {
      String errorMessage =
          String.format(
              "failed to validate code owner config files in revision %s (project = %s, branch = %s)",
              receiveEvent.commit.getName(),
              receiveEvent.getProjectNameKey(),
              receiveEvent.getBranchNameKey().branch());
      logger.atSevere().log(errorMessage);
      throw new StorageException(errorMessage, e);
    }
  }

  /**
   * Validates the specified code owner config and returns a stream of validation messages.
   *
   * @param codeOwnerBackend the code owner backend from which the code owner config can be loaded
   * @param branchNameKey the project and branch of the code owner config
   * @param changedFile the changed file that represents the code owner config
   * @param revision the revision from which the code owner config should be loaded
   * @return a stream of validation messages that describe issues with the code owner config, an
   *     empty stream if there are no issues
   */
  private Stream<CommitValidationMessage> validateCodeOwnerConfig(
      CodeOwnerBackend codeOwnerBackend,
      BranchNameKey branchNameKey,
      ChangedFile changedFile,
      ObjectId revision) {
    requireNonNull(codeOwnerBackend, "codeOwnerBackend");
    requireNonNull(branchNameKey, "branchNameKey");
    requireNonNull(changedFile, "changedFile");
    requireNonNull(revision, "revision");

    if (!changedFile.newPath().isPresent()) {
      // The code owner config file was deleted. Hence we do not need to do any validation.
      return Stream.of();
    }

    CodeOwnerConfig codeOwnerConfig;
    try {
      // Load the code owner config. If the code owner config is not parsable this will fail with a
      // InvalidConfigException (wrapped in a StorageException) that we handle below.
      CodeOwnerConfig.Key codeOwnerConfigKey =
          createCodeOwnerConfigKey(branchNameKey, changedFile.newPath().get());
      codeOwnerConfig =
          codeOwnerBackend
              .getCodeOwnerConfig(codeOwnerConfigKey, revision)
              // We already know that the path exists, so either the code owner config is
              // successfully loaded (this case) or the loading fails with an exception because the
              // code owner config is not parseable (catch block below), but it cannot happen that
              // the code owner config is not found and an empty Optional is returned.
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          String.format(
                              "code owner config %s not found in revision %s",
                              codeOwnerConfigKey, revision.name())));
    } catch (StorageException storageException) {
      // Loading the code owner config has failed.
      Optional<ConfigInvalidException> configInvalidException =
          getInvalidConfigCause(storageException);
      if (!configInvalidException.isPresent()) {
        // Propagate any failure that is not related to the contents of the code owner config.
        throw storageException;
      }

      // The exception was caused by a ConfigInvalidException. This means loading the code owner
      // config failed because it is not parseable.

      // Convert the message from the InvalidConfigException into a validation message and return
      // it.
      return Stream.of(
          new CommitValidationMessage(
              configInvalidException.get().getMessage(),
              getValidationMessageTypeForParsingError(
                  codeOwnerBackend, branchNameKey, changedFile, revision)));
    }

    // The code owner config was successfully loaded and parsed.

    // Validate the parsed code owner config.
    return validateCodeOwnerConfig(codeOwnerConfig);
  }

  /**
   * Returns the {@link com.google.gerrit.server.git.validators.ValidationMessage.Type} (ERROR or
   * WARNING) that should be used for a parsing error of the code owner config file (specified as
   * {@code changedFile}).
   *
   * <p>If {@link com.google.gerrit.server.git.validators.ValidationMessage.Type#ERROR} is returned
   * the upload will be blocked, if {@link
   * com.google.gerrit.server.git.validators.ValidationMessage.Type#WARNING} is returned the upload
   * can succeed and the parsing error will only be shown as warning.
   *
   * <p>If a previous version of the code owner config exists and the previous version was also
   * non-parseable, we want to allow the upload even if the new version is still non-parseable, as
   * it is not making anything worse. Hence in this case the parsing error should be returned as
   * {@link com.google.gerrit.server.git.validators.ValidationMessage.Type#WARNING}, whereas a new
   * parsing error should be returned as {@link
   * com.google.gerrit.server.git.validators.ValidationMessage.Type#ERROR}.
   *
   * @param codeOwnerBackend the code owner backend from which the code owner config can be loaded
   * @param branchNameKey the project and branch of the code owner config
   * @param changedFile the changed file that represents the code owner config
   * @param revision the revision from which the code owner config should be loaded
   * @return the {@link com.google.gerrit.server.git.validators.ValidationMessage.Type} (ERROR or
   *     WARNING) that should be used for parsing error of a code owner config file
   */
  private ValidationMessage.Type getValidationMessageTypeForParsingError(
      CodeOwnerBackend codeOwnerBackend,
      BranchNameKey branchNameKey,
      ChangedFile changedFile,
      ObjectId revision) {
    //
    if (changedFile.oldPath().isPresent()) {
      // A previous version of the code owner config exists.
      ObjectId parentRevision =
          getParentRevision(branchNameKey.project(), revision)
              // Since there is an old path a parent revision must exist.
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          String.format(
                              "parent revision for revision %s in project %s not found",
                              revision.name(), branchNameKey.project().get())));
      try {
        // Try to load the code owner config from the parent revision to see if it was parseable
        // there.
        CodeOwnerConfig.Key baseCodeOwnerConfigKey =
            createCodeOwnerConfigKey(branchNameKey, changedFile.oldPath().get());
        codeOwnerBackend.getCodeOwnerConfig(baseCodeOwnerConfigKey, parentRevision);
        // The code owner config at the parent revision is parseable. This means the parsing error
        // is introduced by the new commit and we should block uploading it, which we achieve by
        // setting the validation message type to error.
        return ValidationMessage.Type.ERROR;
      } catch (StorageException storageException) {
        // Loading the base code owner config has failed.
        if (getInvalidConfigCause(storageException).isPresent()) {
          // The code owner config was already non-parseable before, hence we do not need to
          // block the upload if the code owner config is still non-parseable.
          // Using warning as type means that uploads are not blocked.
          return ValidationMessage.Type.WARNING;
        }
        // Propagate any failure that is not related to the contents of the code owner config.
        throw storageException;
      }
    }

    // The code owner config is newly created. Hence the parsing error comes from the commit
    // that is being pushed and we want to block it from uploading. To do this we set the
    // validation message type to error.
    return ValidationMessage.Type.ERROR;
  }

  /**
   * Returns the first parent of the given revision.
   *
   * @param project the project that contains the revision
   * @param revision the revision for which the first parent should be returned
   * @return the first parent of the given revision, {@link Optional#empty()} if the given revision
   *     has no parent
   */
  private Optional<ObjectId> getParentRevision(Project.NameKey project, ObjectId revision) {
    try (Repository repository = repoManager.openRepository(project)) {
      RevCommit commit = repository.parseCommit(revision);
      if (commit.getParentCount() == 0) {
        return Optional.empty();
      }
      return Optional.of(commit.getParent(0));
    } catch (IOException e) {
      throw new StorageException(
          String.format(
              "Failed to retrieve parent commit of commit %s in project %s",
              revision.name(), project.get()),
          e);
    }
  }

  /**
   * Create the key for a code owner config from a given file path.
   *
   * @param branchNameKey the project and branch of the code owner config
   * @param filePath the file path of the code owner config
   * @return the key of the code owner config
   */
  private static CodeOwnerConfig.Key createCodeOwnerConfigKey(
      BranchNameKey branchNameKey, Path filePath) {
    Path folderPath =
        filePath.getParent() != null
            ? JgitPath.of(filePath.getParent()).getAsAbsolutePath()
            : Paths.get("/");
    String fileName = filePath.getFileName().toString();
    return CodeOwnerConfig.Key.create(branchNameKey, folderPath, fileName);
  }

  /**
   * Checks whether the given exception was caused by a non-parseable code owner config ({@link
   * ConfigInvalidException}). If yes, the {@link ConfigInvalidException} is returned. If no, {@link
   * Optional#empty()} is returned.
   */
  private static Optional<ConfigInvalidException> getInvalidConfigCause(Exception e) {
    return Throwables.getCausalChain(e).stream()
        .filter(t -> t instanceof ConfigInvalidException)
        .map(t -> (ConfigInvalidException) t)
        .findFirst();
  }

  /**
   * Validates the given code owner config and returns validation issues as stream.
   *
   * @param codeOwnerConfig the code owner config that should be validated
   * @return a stream of validation messages that describe issues with the code owner config, an
   *     empty stream if there are no issues
   */
  private Stream<CommitValidationMessage> validateCodeOwnerConfig(
      @SuppressWarnings("unused") CodeOwnerConfig codeOwnerConfig) {
    // TODO(ekempin): Validate the parsed code owner config.
    return Stream.of();
  }
}
