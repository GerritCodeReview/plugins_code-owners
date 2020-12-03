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

package com.google.gerrit.plugins.codeowners.config;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.plugins.codeowners.JgitPath;
import com.google.gerrit.plugins.codeowners.backend.ChangedFiles;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectLevelConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

/** Validates modifications to the {@code code-owners.config} file in {@code refs/meta/config}. */
@Singleton
class CodeOwnersPluginConfigValidator implements CommitValidationListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String pluginName;
  private final GitRepositoryManager repoManager;
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
      GitRepositoryManager repoManager,
      ProjectConfig.Factory projectConfigFactory,
      ProjectState.Factory projectStateFactory,
      ChangedFiles changedFiles,
      GeneralConfig generalConfig,
      StatusConfig statusConfig,
      BackendConfig backendConfig,
      RequiredApprovalConfig requiredApprovalConfig,
      OverrideApprovalConfig overrideApprovalConfig) {
    this.pluginName = pluginName;
    this.repoManager = repoManager;
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
    Project.NameKey project = receiveEvent.project.getNameKey();

    try {
      if (!receiveEvent.refName.equals(RefNames.REFS_CONFIG)
          || !isFileChanged(project, receiveEvent.commit, fileName)) {
        // the code-owners.config file in refs/meta/config was not modified, hence we do not need to
        // validate it
        return ImmutableList.of();
      }

      ProjectState projectState = getProjectState(project, receiveEvent.commit);
      ProjectLevelConfig.Bare cfg = loadConfig(project, fileName, receiveEvent.commit);
      validateConfig(projectState, fileName, cfg);
      return ImmutableList.of();
    } catch (IOException | ConfigInvalidException | PatchListNotAvailableException e) {
      String errorMessage =
          String.format(
              "failed to validate file %s for revision %s in ref %s of project %s",
              fileName, receiveEvent.commit.getName(), RefNames.REFS_CONFIG, project);
      logger.atSevere().log(errorMessage);
      throw new CommitValidationException(errorMessage, e);
    }
  }

  private ProjectState getProjectState(Project.NameKey projectName, RevCommit commit)
      throws IOException, ConfigInvalidException {
    try (Repository repo = repoManager.openRepository(projectName)) {
      ProjectConfig projectConfig = projectConfigFactory.create(projectName);
      projectConfig.load(repo, commit);
      return projectStateFactory.create(projectConfig.getCacheable());
    }
  }

  /**
   * Whether the given file was changed in the given revision.
   *
   * @param project the name of the project
   * @param revision the revision
   * @param fileName the name of the file
   */
  private boolean isFileChanged(Project.NameKey project, ObjectId revision, String fileName)
      throws IOException, PatchListNotAvailableException {
    return changedFiles.compute(project, revision).stream()
        .anyMatch(changedFile -> changedFile.hasNewPath(JgitPath.of(fileName).getAsAbsolutePath()));
  }

  /**
   * Loads the configuration from the file and revision.
   *
   * @param project the project name
   * @param fileName the name of the config file
   * @param revision the revision from which the configuration should be loaded
   * @return the loaded configuration
   * @throws CommitValidationException thrown if the configuration is invalid and cannot be parsed
   */
  private ProjectLevelConfig.Bare loadConfig(
      Project.NameKey project, String fileName, ObjectId revision)
      throws CommitValidationException, IOException {
    ProjectLevelConfig.Bare cfg = new ProjectLevelConfig.Bare(fileName);
    try (Repository git = repoManager.openRepository(project)) {
      cfg.load(project, git, revision);
    } catch (ConfigInvalidException e) {
      throw new CommitValidationException(
          exceptionMessage(fileName, revision),
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
   * @throws CommitValidationException throw if there are any validation errors
   */
  private void validateConfig(
      ProjectState projectState, String fileName, ProjectLevelConfig.Bare cfg)
      throws CommitValidationException {
    List<CommitValidationMessage> validationMessages = new ArrayList<>();
    validationMessages.addAll(backendConfig.validateProjectLevelConfig(fileName, cfg));
    validationMessages.addAll(generalConfig.validateProjectLevelConfig(fileName, cfg));
    validationMessages.addAll(statusConfig.validateProjectLevelConfig(fileName, cfg));
    validationMessages.addAll(
        requiredApprovalConfig.validateProjectLevelConfig(projectState, fileName, cfg));
    validationMessages.addAll(
        overrideApprovalConfig.validateProjectLevelConfig(projectState, fileName, cfg));
    if (!validationMessages.isEmpty()) {
      throw new CommitValidationException(
          exceptionMessage(fileName, cfg.getRevision()), validationMessages);
    }
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
