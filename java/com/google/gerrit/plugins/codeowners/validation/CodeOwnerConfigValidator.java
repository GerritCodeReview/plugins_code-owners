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

      if (validationMessages.stream()
          .anyMatch(
              validationMessage ->
                  ValidationMessage.Type.ERROR.equals(validationMessage.getType()))) {
        throw new CommitValidationException("invalid code owner config files", validationMessages);
      }
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
      throw new StorageException("Failed to validate ", e);
    }
  }

  private Stream<CommitValidationMessage> validateCodeOwnerConfig(
      CodeOwnerBackend codeOwnerBackend,
      BranchNameKey branchNameKey,
      ChangedFile changedFile,
      ObjectId revision) {
    try {
      CodeOwnerConfig.Key codeOwnerConfigKey =
          createCodeOwnerConfigKey(branchNameKey, changedFile.newPath().get());
      Optional<CodeOwnerConfig> codeOwnerConfig =
          codeOwnerBackend.getCodeOwnerConfig(codeOwnerConfigKey, revision);
      checkState(
          codeOwnerConfig.isPresent(),
          "code owner config %s not found in revision %s",
          codeOwnerConfig,
          revision.name());
      return validateCodeOwnerConfig(codeOwnerConfig.get());
    } catch (StorageException e) {
      Optional<ConfigInvalidException> configInvalidException = getInvalidConfigCause(e);
      if (!configInvalidException.isPresent()) {
        throw e;
      }

      // Using error as type means that uploads will be blocked.
      ValidationMessage.Type validationMessageType = ValidationMessage.Type.ERROR;
      if (changedFile.oldPath().isPresent()) {
        Optional<ObjectId> parentRevision = getParentRevision(branchNameKey.project(), revision);
        if (parentRevision.isPresent()) {
          try {
            // Try to load the code owner config from the parent revision to see if it was parseable
            // there. If yes, the parsing error is introduced by the new commit and we should block
            // uploading it.
            CodeOwnerConfig.Key codeOwnerConfigKey =
                createCodeOwnerConfigKey(branchNameKey, changedFile.oldPath().get());
            codeOwnerBackend.getCodeOwnerConfig(codeOwnerConfigKey, parentRevision.get());
          } catch (StorageException e2) {
            if (getInvalidConfigCause(e2).isPresent()) {
              // The code owner config was already non-parseable before, hence we do not need to
              // block the upload if the code owner config is still non-parseable.
              // Using warning as type means that uploads are not blocked.
              validationMessageType = ValidationMessage.Type.WARNING;
            } else {
              throw e2;
            }
          }
        }
      }

      return Stream.of(
          new CommitValidationMessage(
              configInvalidException.get().getMessage(), validationMessageType));
    }
  }

  private CodeOwnerConfig.Key createCodeOwnerConfigKey(BranchNameKey branchNameKey, Path filePath) {
    Path folderPath =
        filePath.getParent() != null
            ? JgitPath.of(filePath.getParent()).getAsAbsolutePath()
            : Paths.get("/");
    String fileName = filePath.getFileName().toString();
    return CodeOwnerConfig.Key.create(branchNameKey, folderPath, fileName);
  }

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

  private Optional<ConfigInvalidException> getInvalidConfigCause(Exception e) {
    // Check whether the exception was caused by a non-parseable code owner config (in this case
    // the causal chain contains a ConfigInvalidException).
    return Throwables.getCausalChain(e).stream()
        .filter(t -> t instanceof ConfigInvalidException)
        .map(t -> (ConfigInvalidException) t)
        .findFirst();
  }

  private Stream<CommitValidationMessage> validateCodeOwnerConfig(
      @SuppressWarnings("unused") CodeOwnerConfig codeOwnerConfig) {
    // TODO(ekempin): Validate the parsed code owner config.
    return Stream.of();
  }
}
