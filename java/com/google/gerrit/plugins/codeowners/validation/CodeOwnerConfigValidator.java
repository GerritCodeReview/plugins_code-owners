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

/** Validates modifications to the code owner config files. */
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

      // There are no errors, just return the messages. This means that they will be send to the
      // client, but the upload will not fail.
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
    } catch (StorageException e) {
      // Loading the code owner config has failed.
      Optional<ConfigInvalidException> configInvalidException = getInvalidConfigCause(e);
      if (!configInvalidException.isPresent()) {
        // Propagate any failure that is not related to the contents of the code owner config.
        throw e;
      }

      // The exception was caused by a ConfigInvalidException. This means loading the code owner
      // config failed because it is not parseable.

      // The validation message type that we will use to report issues. Using error as type means
      // that the upload will be blocked, using warning as type means that the upload can succeed
      // and the issues will only be shown as warnings.
      ValidationMessage.Type validationMessageType;

      // If a previous version of the code owner config exists and the previous version was also
      // non-parseable we want to allow the upload even if the new version is still non-parseable,
      // as it is not making anything worse.
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
          validationMessageType = ValidationMessage.Type.ERROR;
        } catch (StorageException e2) {
          // Loading the base code owner config has failed.
          if (getInvalidConfigCause(e2).isPresent()) {
            // The code owner config was already non-parseable before, hence we do not need to
            // block the upload if the code owner config is still non-parseable.
            // Using warning as type means that uploads are not blocked.
            validationMessageType = ValidationMessage.Type.WARNING;
          } else {
            // Propagate any failure that is not related to the contents of the code owner config.
            throw e2;
          }
        }
      } else {
        // The code owner config is newly created. Hence the parsing error comes from the commit
        // that is being pushed and we want to block it from uploading. To do this we set the
        // validation message type to error.
        validationMessageType = ValidationMessage.Type.ERROR;
      }

      // Convert the message from the InvalidConfigException into a validation message and return
      // it.
      return Stream.of(
          new CommitValidationMessage(
              configInvalidException.get().getMessage(), validationMessageType));
    }

    // The code owner config was successfully loaded and parsed.

    // Validate the parsed code owner config.
    return validateCodeOwnerConfig(codeOwnerConfig);
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
