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

package com.google.gerrit.plugins.codeowners.restapi;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.codeowners.api.CheckCodeOwnerConfigFilesInput;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigScanner;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.validation.CodeOwnerConfigValidator;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.ListBranches;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST endpoint that checks/validates the code owner config files in a project.
 *
 * <p>This REST endpoint handles {@code POST
 * /projects/<project-name>/branches/<branch-name>/code_owners.check_config} requests.
 *
 * <p>The implementation of this REST endpoint iterates over all code owner config files in the
 * project. This means the expected performance of this REST endpoint is rather low and it should
 * not be used in any critical path where performance matters.
 */
@Singleton
public class CheckCodeOwnerConfigFiles
    implements RestModifyView<ProjectResource, CheckCodeOwnerConfigFilesInput> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<CurrentUser> currentUser;
  private final PermissionBackend permissionBackend;
  private final Provider<ListBranches> listBranches;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final CodeOwnerConfigScanner.Factory codeOwnerConfigScannerFactory;
  private final CodeOwnerConfigValidator codeOwnerConfigValidator;

  @Inject
  public CheckCodeOwnerConfigFiles(
      Provider<CurrentUser> currentUser,
      PermissionBackend permissionBackend,
      Provider<ListBranches> listBranches,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerConfigScanner.Factory codeOwnerConfigScannerFactory,
      CodeOwnerConfigValidator codeOwnerConfigValidator) {
    this.currentUser = currentUser;
    this.permissionBackend = permissionBackend;
    this.listBranches = listBranches;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.codeOwnerConfigScannerFactory = codeOwnerConfigScannerFactory;
    this.codeOwnerConfigValidator = codeOwnerConfigValidator;
  }

  @Override
  public Response<Map<String, Map<String, List<ConsistencyProblemInfo>>>> apply(
      ProjectResource projectResource, CheckCodeOwnerConfigFilesInput input)
      throws RestApiException, PermissionBackendException, IOException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    // This REST endpoint requires the caller to be a project owner.
    permissionBackend
        .currentUser()
        .project(projectResource.getNameKey())
        .check(ProjectPermission.WRITE_CONFIG);

    logger.atFine().log(
        "checking code owner config files for project %s"
            + " (validateDisabledBranches = %s, branches = %s, path = %s, verbosity = %s)",
        projectResource.getNameKey(),
        input.validateDisabledBranches,
        input.branches,
        input.path,
        input.verbosity);

    ImmutableSet<BranchNameKey> branches = branches(projectResource);

    validateInput(projectResource.getNameKey(), branches, input);

    ImmutableMap.Builder<String, Map<String, List<ConsistencyProblemInfo>>> resultsByBranchBuilder =
        ImmutableMap.builder();
    branches.stream()
        .filter(branchNameKey -> shouldValidateBranch(input, branchNameKey))
        .filter(
            branchNameKey ->
                validateDisabledBranches(input)
                    || !codeOwnersPluginConfiguration
                        .getProjectConfig(branchNameKey.project())
                        .isDisabled(branchNameKey.branch()))
        .forEach(
            branchNameKey ->
                resultsByBranchBuilder.put(
                    branchNameKey.branch(),
                    checkBranch(input.path, branchNameKey, input.verbosity)));
    return Response.ok(resultsByBranchBuilder.build());
  }

  private ImmutableSet<BranchNameKey> branches(ProjectResource projectResource)
      throws RestApiException, IOException, PermissionBackendException {
    return listBranches.get().apply(projectResource).value().stream()
        .filter(branchInfo -> !"HEAD".equals(branchInfo.ref))
        .map(branchInfo -> BranchNameKey.create(projectResource.getNameKey(), branchInfo.ref))
        .collect(toImmutableSet());
  }

  private Map<String, List<ConsistencyProblemInfo>> checkBranch(
      String pathGlob,
      BranchNameKey branchNameKey,
      @Nullable ConsistencyProblemInfo.Status verbosity) {
    ListMultimap<String, ConsistencyProblemInfo> problemsByPath = LinkedListMultimap.create();
    CodeOwnerBackend codeOwnerBackend =
        codeOwnersPluginConfiguration
            .getProjectConfig(branchNameKey.project())
            .getBackend(branchNameKey.branch());
    codeOwnerConfigScannerFactory
        .create()
        // Do not check the default code owner config file in refs/meta/config, as this config is
        // stored in another branch. If it should be checked users must check the code owner config
        // files in refs/meta/config explicitly.
        .includeDefaultCodeOwnerConfig(false)
        .visit(
            branchNameKey,
            codeOwnerConfig -> {
              problemsByPath.putAll(
                  codeOwnerBackend.getFilePath(codeOwnerConfig.key()).toString(),
                  checkCodeOwnerConfig(
                      branchNameKey, codeOwnerBackend, codeOwnerConfig, verbosity));
              return true;
            },
            (codeOwnerConfigFilePath, configInvalidException) -> {
              problemsByPath.put(
                  codeOwnerConfigFilePath.toString(),
                  new ConsistencyProblemInfo(
                      ConsistencyProblemInfo.Status.FATAL, configInvalidException.getMessage()));
            },
            pathGlob);

    return Multimaps.asMap(problemsByPath);
  }

  private ImmutableList<ConsistencyProblemInfo> checkCodeOwnerConfig(
      BranchNameKey branchNameKey,
      CodeOwnerBackend codeOwnerBackend,
      CodeOwnerConfig codeOwnerConfig,
      @Nullable ConsistencyProblemInfo.Status verbosity) {
    return codeOwnerConfigValidator
        .validateCodeOwnerConfig(
            branchNameKey, currentUser.get().asIdentifiedUser(), codeOwnerBackend, codeOwnerConfig)
        .map(
            commitValidationMessage ->
                createConsistencyProblemInfo(commitValidationMessage, verbosity))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toImmutableList());
  }

  public static Optional<ConsistencyProblemInfo> createConsistencyProblemInfo(
      CommitValidationMessage commitValidationMessage,
      @Nullable ConsistencyProblemInfo.Status verbosity) {
    switch (commitValidationMessage.getType()) {
      case FATAL:
        return Optional.of(
            new ConsistencyProblemInfo(
                ConsistencyProblemInfo.Status.FATAL, commitValidationMessage.getMessage()));
      case ERROR:
        if (ConsistencyProblemInfo.Status.FATAL.equals(verbosity)) {
          // errors should not be reported
          return Optional.empty();
        }
        return Optional.of(
            new ConsistencyProblemInfo(
                ConsistencyProblemInfo.Status.ERROR, commitValidationMessage.getMessage()));
      case WARNING:
        if (ConsistencyProblemInfo.Status.FATAL.equals(verbosity)
            || ConsistencyProblemInfo.Status.ERROR.equals(verbosity)) {
          // warnings should not be reported
          return Optional.empty();
        }
        return Optional.of(
            new ConsistencyProblemInfo(
                ConsistencyProblemInfo.Status.WARNING, commitValidationMessage.getMessage()));
      case HINT:
      case OTHER:
        return Optional.empty();
    }

    throw new IllegalStateException(
        String.format(
            "unknown message type %s for message %s",
            commitValidationMessage.getType(), commitValidationMessage.getMessage()));
  }

  private void validateInput(
      Project.NameKey projectName,
      ImmutableSet<BranchNameKey> branches,
      CheckCodeOwnerConfigFilesInput input)
      throws RestApiException {
    if (input.branches != null) {
      for (String branchName : input.branches) {
        BranchNameKey branchNameKey = BranchNameKey.create(projectName, branchName);
        if (!branches.contains(branchNameKey)) {
          throw new UnprocessableEntityException(String.format("branch %s not found", branchName));
        }

        if ((input.validateDisabledBranches == null || !input.validateDisabledBranches)
            && codeOwnersPluginConfiguration.getProjectConfig(projectName).isDisabled(branchName)) {
          throw new BadRequestException(
              String.format(
                  "code owners functionality for branch %s is disabled,"
                      + " set 'validate_disabled_braches' in the input to 'true' if code owner"
                      + " config files in this branch should be validated",
                  branchName));
        }
      }
    }
  }

  private static boolean shouldValidateBranch(
      CheckCodeOwnerConfigFilesInput input, BranchNameKey branchNameKey) {
    boolean shouldValidateBranch =
        input.branches == null
            || input.branches.contains(branchNameKey.branch())
            || input.branches.contains(branchNameKey.shortName());
    if (!shouldValidateBranch) {
      logger.atFine().log("skip validation for branch %s", branchNameKey.branch());
    }
    return shouldValidateBranch;
  }

  private static boolean validateDisabledBranches(CheckCodeOwnerConfigFilesInput input) {
    return input.validateDisabledBranches != null && input.validateDisabledBranches;
  }
}
