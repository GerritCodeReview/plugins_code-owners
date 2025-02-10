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

package com.google.gerrit.plugins.codeowners.validation;

import static com.google.gerrit.plugins.codeowners.backend.CodeOwnersInternalServerErrorException.newInternalServerError;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.plugins.codeowners.backend.ChangedFiles;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy;
import com.google.gerrit.server.PluginPushOption;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;

/** Push option that allows to skip the code owner config validation. */
@Singleton
public class SkipCodeOwnerConfigValidationPushOption implements PluginPushOption {
  public static final String NAME = "skip-validation";
  public static final String DESCRIPTION = "skips the code owner config validation";

  private final String pluginName;
  private final PermissionBackend permissionBackend;
  private final SkipCodeOwnerConfigValidationCapability skipCodeOwnerConfigValidationCapability;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final ChangedFiles changedFiles;

  @Inject
  SkipCodeOwnerConfigValidationPushOption(
      @PluginName String pluginName,
      PermissionBackend permissionBackend,
      SkipCodeOwnerConfigValidationCapability skipCodeOwnerConfigValidationCapability,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      ChangedFiles changedFiles) {
    this.pluginName = pluginName;
    this.permissionBackend = permissionBackend;
    this.skipCodeOwnerConfigValidationCapability = skipCodeOwnerConfigValidationCapability;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.changedFiles = changedFiles;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return DESCRIPTION;
  }

  @Override
  public boolean isOptionEnabled(ChangeNotes changeNotes) {
    return !codeOwnersPluginConfiguration
            .getProjectConfig(changeNotes.getProjectName())
            .isDisabled(changeNotes.getChange().getDest().branch())
        && canSkipCodeOwnerConfigValidation()
        && hasModifiedCodeOwnerConfigFiles(changeNotes);
  }

  // TODO: Extend check to conatain hasModifiedCodeOwnerConfigFiles as well
  // Callers would need to specify the list of files for that.
  @Override
  public boolean isOptionEnabled(Project.NameKey project, BranchNameKey branch) {
    return !codeOwnersPluginConfiguration.getProjectConfig(project).isDisabled(branch.branch())
        && canSkipCodeOwnerConfigValidation();
  }

  private boolean hasModifiedCodeOwnerConfigFiles(ChangeNotes changeNotes) {
    CodeOwnerBackend codeOwnerBackend =
        codeOwnersPluginConfiguration
            .getProjectConfig(changeNotes.getProjectName())
            .getBackend(changeNotes.getChange().getDest().branch());

    // For merge commits, always do the comparison against the destination branch
    // (MergeCommitStrategy.ALL_CHANGED_FILES) as this is what CodeOwnerConfigValidator does.
    try {
      return changedFiles
          .getFromDiffCache(
              changeNotes.getProjectName(),
              changeNotes.getCurrentPatchSet().commitId(),
              MergeCommitStrategy.ALL_CHANGED_FILES)
          .stream()
          // filter out deletions (files without new path)
          .filter(changedFile -> changedFile.newPath().isPresent())
          .anyMatch(
              changedFile ->
                  codeOwnerBackend.isCodeOwnerConfigFile(
                      changeNotes.getProjectName(),
                      Path.of(changedFile.newPath().get().toString()).getFileName().toString()));
    } catch (IOException | DiffNotAvailableException e) {
      throw newInternalServerError(
          String.format(
              "Failed to check changed files of change %s in project %s",
              changeNotes.getChangeId(), changeNotes.getProjectName()),
          e);
    }
  }

  /**
   * Whether the code owner config validation should be skipped.
   *
   * <p>Only returns {@code true} if the {@code --code-owners~skip-validation} push option was
   * specified and the calling user is allowed to skip the code owner config validation (requires
   * the {@link SkipCodeOwnerConfigValidationCapability}).
   *
   * @param pushOptions the push options that have been specified on the push
   * @return {@code true} if the {@code --code-owners~skip-validation} push option was specified and
   *     the calling user is allowed to skip the code owner config validation
   * @throws InvalidValueException if the {@code --code-owners~skip-validation} push option was
   *     specified with an invalid value or if the {@code --code-owners~skip-validation} push option
   *     was specified multiple times
   * @throws AuthException thrown if the {@code --code-owners~skip-validation} push option was
   *     specified, but the calling user is not allowed to skip the code owner config validation
   */
  public boolean skipValidation(ImmutableListMultimap<String, String> pushOptions)
      throws InvalidValueException, AuthException {
    String qualifiedName = pluginName + "~" + NAME;
    if (!pushOptions.containsKey(qualifiedName)) {
      return false;
    }
    ImmutableList<String> values = pushOptions.get(qualifiedName);
    if (values.size() != 1) {
      throw new InvalidValueException(values);
    }

    String value = values.get(0);
    if (Boolean.parseBoolean(value) || value.isEmpty()) {
      checkCanSkipCodeOwnerConfigValidation();
      return true;
    }

    if (value.equalsIgnoreCase(Boolean.FALSE.toString())) {
      return false;
    }

    // value was neither 'true', 'false' nor empty
    throw new InvalidValueException(values);
  }

  private void checkCanSkipCodeOwnerConfigValidation() throws AuthException {
    try {
      permissionBackend
          .currentUser()
          .check(skipCodeOwnerConfigValidationCapability.getPermission());
    } catch (PermissionBackendException e) {
      throw newInternalServerError(
          String.format(
              "Failed to check %s~%s capability",
              pluginName, SkipCodeOwnerConfigValidationCapability.ID),
          e);
    }
  }

  private boolean canSkipCodeOwnerConfigValidation() {
    try {
      return permissionBackend
          .currentUser()
          .test(skipCodeOwnerConfigValidationCapability.getPermission());
    } catch (PermissionBackendException e) {
      throw newInternalServerError(
          String.format(
              "Failed to check %s~%s capability",
              pluginName, SkipCodeOwnerConfigValidationCapability.ID),
          e);
    }
  }

  public class InvalidValueException extends Exception {
    private static final long serialVersionUID = 1L;

    InvalidValueException(ImmutableList<String> invalidValues) {
      super(
          invalidValues.size() == 1
              ? String.format(
                  "Invalid value for --%s~%s push option: %s",
                  pluginName, NAME, invalidValues.get(0))
              : String.format(
                  "--%s~%s push option can be specified only once, received multiple values: %s",
                  pluginName, NAME, invalidValues));
    }
  }
}
