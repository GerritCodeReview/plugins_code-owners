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

package com.google.gerrit.plugins.codeowners.acceptance.api;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigInfoSubject.assertThatOptional;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerConfigInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import java.util.Optional;
import org.junit.Test;

/**
 * Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerConfigForPathInBranch} REST endpoint.
 *
 * <p>Further tests for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerConfigForPathInBranch} REST endpoint
 * that require using the REST API are implemented in {@link
 * com.google.gerrit.plugins.codeowners.acceptance.restapi.GetCodeOwnerConfigForPathInBranchRestIT}.
 */
public class GetCodeOwnerConfigForPathInBranchIT extends AbstractCodeOwnersIT {
  @Test
  @GerritConfig(name = "plugin.code-owners.enableExperimentalRestEndpoints", value = "true")
  public void getNonExistingCodeOwnerConfig() throws Exception {
    assertThatOptional(codeOwnerConfigsApiFactory.branch(project, "master").get("/")).isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableExperimentalRestEndpoints", value = "true")
  public void getCodeOwnerConfigForAbsolutePath() throws Exception {
    testGetCodeOwnerConfig(true);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableExperimentalRestEndpoints", value = "true")
  public void getCodeOwnerConfigForNonAbsolutePath() throws Exception {
    testGetCodeOwnerConfig(false);
  }

  private void testGetCodeOwnerConfig(boolean useAbsolutePath) throws Exception {
    String branch = "master";
    String path = useAbsolutePath ? "/foo/bar/" : "foo/bar/";

    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath(JgitPath.of(path).getAsAbsolutePath())
            .ignoreParentCodeOwners()
            .addCodeOwnerEmail(admin.email())
            .create();

    Optional<CodeOwnerConfigInfo> codeOwnerConfigInfo =
        codeOwnerConfigsApiFactory.branch(project, branch).get(path);
    assertThatOptional(codeOwnerConfigInfo)
        .value()
        .correspondsTo(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).get());
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableExperimentalRestEndpoints", value = "true")
  public void getCodeOwnerConfigIfCodeOwnersFunctionalityIsDisabled() throws Exception {
    disableCodeOwnersForProject(project);
    testGetCodeOwnerConfig(true);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableExperimentalRestEndpoints", value = "false")
  public void cannotGetCodeOwnerConfigIfExperimentalRestEndpointsAreNotEnabled() throws Exception {
    MethodNotAllowedException exception =
        assertThrows(
            MethodNotAllowedException.class,
            () -> codeOwnerConfigsApiFactory.branch(project, "master").get("/foo/bar/"));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("experimental code owners REST endpoints are disabled");
  }
}
