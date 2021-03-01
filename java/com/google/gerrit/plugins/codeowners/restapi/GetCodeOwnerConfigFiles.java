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
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigScanner;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.server.project.BranchResource;
import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import org.kohsuke.args4j.Option;

/**
 * REST endpoint that lists the code owner config files in a branch.
 *
 * <p>This REST endpoint handles {@code GET
 * /projects/<project-name>/branches/<branch-name>/code_owners.config/} requests.
 *
 * <p>The implementation of this REST endpoint iterates over all code owner config files in the
 * branch. This means the expected performance of this REST endpoint is rather low and it should not
 * be used in any critical path where performance matters.
 */
public class GetCodeOwnerConfigFiles implements RestReadView<BranchResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final CodeOwnerConfigScanner.Factory codeOwnerConfigScannerFactory;

  private boolean includeNonParsableFiles;
  private String email;
  private String pathGlob;

  @Option(
      name = "--include-non-parsable-files",
      usage = "includes non-parseable code owner config files in the response")
  public void setIncludeNonParsableFiles(boolean includeNonParsableFiles) {
    this.includeNonParsableFiles = includeNonParsableFiles;
  }

  @Option(
      name = "--email",
      metaVar = "EMAIL",
      usage = "limits the returned code owner config files to those that contain this email")
  public void setEmail(@Nullable String email) {
    this.email = email;
  }

  @Option(
      name = "--path",
      usage =
          "limits the returned code owner config files to those that have a path matching"
              + " this glob")
  public void setPath(@Nullable String pathGlob) {
    this.pathGlob = pathGlob;
  }

  @Inject
  public GetCodeOwnerConfigFiles(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerConfigScanner.Factory codeOwnerConfigScannerFactory) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.codeOwnerConfigScannerFactory = codeOwnerConfigScannerFactory;
  }

  @Override
  public Response<List<String>> apply(BranchResource branchResource) throws BadRequestException {
    validateOptions();

    CodeOwnerBackend codeOwnerBackend =
        codeOwnersPluginConfiguration
            .getProjectConfig(branchResource.getNameKey())
            .getBackend(branchResource.getBranchKey().branch());
    ImmutableList.Builder<Path> codeOwnerConfigs = ImmutableList.builder();

    if (email != null) {
      logger.atFine().log(
          "limiting the returned code owner config files to those that contain the email %s",
          email);
    }

    codeOwnerConfigScannerFactory
        .create()
        // Do not include the default code owner config file in refs/meta/config, as this config is
        // stored in another branch. If it should be listed users must list the code owner config
        // files in refs/meta/config explicitly.
        .includeDefaultCodeOwnerConfig(false)
        .visit(
            branchResource.getBranchKey(),
            codeOwnerConfig -> {
              Path codeOwnerConfigPath = codeOwnerBackend.getFilePath(codeOwnerConfig.key());
              if (email == null || containsEmail(codeOwnerConfig, codeOwnerConfigPath, email)) {
                codeOwnerConfigs.add(codeOwnerConfigPath);
              }
              return true;
            },
            includeNonParsableFiles
                ? (codeOwnerConfigFilePath, configInvalidException) -> {
                  codeOwnerConfigs.add(codeOwnerConfigFilePath);
                }
                : CodeOwnerConfigScanner.ignoreInvalidCodeOwnerConfigFiles(),
            pathGlob);
    return Response.ok(
        codeOwnerConfigs.build().stream().map(Path::toString).collect(toImmutableList()));
  }

  /**
   * Checks whether the given code owner config contains the given email.
   *
   * @param codeOwnerConfig the code owner config for which it should be checked if it contains the
   *     email
   * @param codeOwnerConfigPath the path of the code owner config
   * @param email the email
   * @return whether the given code owner config contains the given email
   */
  private boolean containsEmail(
      CodeOwnerConfig codeOwnerConfig, Path codeOwnerConfigPath, String email) {
    requireNonNull(email, "email");
    boolean containsEmail =
        codeOwnerConfig.codeOwnerSets().stream()
            .flatMap(codeOwnerSet -> codeOwnerSet.codeOwners().stream())
            .anyMatch(codeOwnerReference -> email.equals(codeOwnerReference.email()));
    if (!containsEmail) {
      logger.atFine().log(
          "Filtering out %s since it doesn't contain the email", codeOwnerConfigPath);
    }
    return containsEmail;
  }

  private void validateOptions() throws BadRequestException {
    if (email != null && includeNonParsableFiles) {
      throw new BadRequestException(
          "the options 'email' and 'include-non-parsable-files' are mutually exclusive");
    }
  }
}
