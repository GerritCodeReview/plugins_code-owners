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

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.plugins.codeowners.backend.ChangedFiles;
import com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectLevelConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

/** Validates modifications to the {@code code-owners.config} file in {@code refs/meta/config}. */
@Singleton
public class CodeOwnersPluginConfigValidator implements CommitValidationListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String pluginName;
  private final ProjectConfig.Factory projectConfigFactory;
  private final ProjectState.Factory projectStateFactory;
  private final ChangedFiles changedFiles;
  private final GeneralConfig generalConfig;
  private final StatusConfig statusConfig;
  private final BackendConfig backendConfig;
  private final RequiredApprovalConfig requiredApprovalConfig;
  private final OverrideApprovalConfig overrideApprovalConfig;

  @Inject
  CodeOwnersPluginConfigValidator(
      @PluginName String pluginName,
      ProjectConfig.Factory projectConfigFactory,
      ProjectState.Factory projectStateFactory,
      ChangedFiles changedFiles,
      GeneralConfig generalConfig,
      StatusConfig statusConfig,
      BackendConfig backendConfig,
      RequiredApprovalConfig requiredApprovalConfig,
      OverrideApprovalConfig overrideApprovalConfig) {
    this.pluginName = pluginName;
    this.projectConfigFactory = projectConfigFactory;
    this.projectStateFactory = projectStateFactory;
    this.changedFiles = changedFiles;
    this.generalConfig = generalConfig;
    this.statusConfig = statusConfig;
    this.backendConfig = backendConfig;
    this.requiredApprovalConfig = requiredApprovalConfig;
    this.overrideApprovalConfig = overrideApprovalConfig;
  }

  @Override
  public ImmutableList<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
      throws CommitValidationException {
    String fileName = pluginName + ".config";

    try {
      if (!receiveEvent.refName.equals(RefNames.REFS_CONFIG)
          || !isFileChanged(receiveEvent, fileName)) {
        // the code-owners.config file in refs/meta/config was not modified, hence we do not need to
        // validate it
        return ImmutableList.of();
      }

      ProjectState projectState = getProjectState(receiveEvent);
      ProjectLevelConfig.Bare cfg = loadConfig(receiveEvent, fileName);
      ImmutableList<CommitValidationMessage> validationMessages =
          validateConfig(projectState, fileName, cfg.getConfig());
      if (!validationMessages.isEmpty()) {
        throw new CommitValidationException(
            exceptionMessage(fileName, cfg.getRevision()), validationMessages);
      }
      return ImmutableList.of();
    } catch (IOException | ConfigInvalidException e) {
      String errorMessage =
          String.format(
              "failed to validate file %s for revision %s in ref %s of project %s",
              fileName,
              receiveEvent.commit.getName(),
              RefNames.REFS_CONFIG,
              receiveEvent.project.getNameKey());
      logger.atSevere().withCause(e).log(errorMessage);
      throw new CommitValidationException(errorMessage, e);
    }
  }

  private ProjectState getProjectState(CommitReceivedEvent receiveEvent)
      throws IOException, ConfigInvalidException {
    ProjectConfig projectConfig = projectConfigFactory.create(receiveEvent.project.getNameKey());
    projectConfig.load(receiveEvent.revWalk, receiveEvent.commit);
    return projectStateFactory.create(projectConfig.getCacheable());
  }

  /**
   * Whether the given file was changed in the given revision.
   *
   * @param receiveEvent the receive event
   * @param fileName the name of the file
   */
  private boolean isFileChanged(CommitReceivedEvent receiveEvent, String fileName)
      throws IOException {
    return changedFiles
        .compute(
            receiveEvent.project.getNameKey(),
            receiveEvent.commit,
            MergeCommitStrategy.ALL_CHANGED_FILES)
        .stream()
        .anyMatch(changedFile -> changedFile.hasNewPath(JgitPath.of(fileName).getAsAbsolutePath()));
  }

  /**
   * Loads the configuration from the file and revision.
   *
   * @param receiveEvent the receive event
   * @param fileName the name of the config file
   * @return the loaded configuration
   * @throws CommitValidationException thrown if the configuration is invalid and cannot be parsed
   */
  private ProjectLevelConfig.Bare loadConfig(CommitReceivedEvent receiveEvent, String fileName)
      throws CommitValidationException, IOException {
    ProjectLevelConfig.Bare cfg = new ProjectLevelConfig.Bare(fileName);
    try {
      cfg.load(receiveEvent.project.getNameKey(), receiveEvent.revWalk, receiveEvent.commit);
    } catch (ConfigInvalidException e) {
      throw new CommitValidationException(
          exceptionMessage(fileName, receiveEvent.commit),
          new CommitValidationMessage(e.getMessage(), ValidationMessage.Type.ERROR));
    }
    return cfg;
  }

  /**
   * Validates the code-owners project-level configuration.
   *
   * @param projectState the project state
   * @param fileName the name of the config file
   * @param cfg the project-level code-owners configuration that should be validated
   * @return list of messages with validation issues, empty list if there are no issues
   */
  public ImmutableList<CommitValidationMessage> validateConfig(
      ProjectState projectState, String fileName, Config cfg) {
    List<CommitValidationMessage> validationMessages = new ArrayList<>();
    validationMessages.addAll(backendConfig.validateProjectLevelConfig(fileName, cfg));
    validationMessages.addAll(generalConfig.validateProjectLevelConfig(fileName, cfg));
    validationMessages.addAll(statusConfig.validateProjectLevelConfig(fileName, cfg));
    validationMessages.addAll(
        requiredApprovalConfig.validateProjectLevelConfig(projectState, fileName, cfg));
    validationMessages.addAll(
        overrideApprovalConfig.validateProjectLevelConfig(projectState, fileName, cfg));
    return ImmutableList.copyOf(validationMessages);
  }

  /**
   * Creates the message for {@link CommitValidationException}s that are thrown for validation
   * errors in the project-level code-owners configuration.
   *
   * @param fileName the name of the config file
   * @param revision the revision in which the configuration is invalid
   * @return the created exception message
   */
  private static String exceptionMessage(String fileName, ObjectId revision) {
    return String.format("invalid %s file in revision %s", fileName, revision.getName());
  }
}
