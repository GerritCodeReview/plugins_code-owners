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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerProjectConfigInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackendId;
import com.google.gerrit.plugins.codeowners.config.BackendConfig;
import com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.config.RequiredApproval;
import com.google.inject.Inject;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerProjectConfig} REST endpoint.
 *
 * <p>Further tests for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerProjectConfig} REST endpoint that
 * require using the REST API are implemented in {@link
 * com.google.gerrit.plugins.codeowners.acceptance.restapi.GetCodeOwnerProjectConfigRestIT}.
 */
public class GetCodeOwnerProjectConfigIT extends AbstractCodeOwnersIT {
  @Inject ProjectOperations projectOperations;

  private BackendConfig backendConfig;

  @Before
  public void setup() throws Exception {
    backendConfig = plugin.getSysInjector().getInstance(BackendConfig.class);
  }

  @Test
  public void cannotGetConfigForHiddenProject() throws Exception {
    ConfigInput configInput = new ConfigInput();
    configInput.state = ProjectState.HIDDEN;
    gApi.projects().name(project.get()).config(configInput);

    ResourceConflictException exception =
        assertThrows(
            ResourceConflictException.class,
            () -> projectCodeOwnersApiFactory.project(project).getConfig());
    assertThat(exception).hasMessageThat().isEqualTo("project state HIDDEN does not permit read");
  }

  @Test
  public void getDefaultConfig() throws Exception {
    CodeOwnerProjectConfigInfo codeOwnerProjectConfigInfo =
        projectCodeOwnersApiFactory.project(project).getConfig();
    assertThat(codeOwnerProjectConfigInfo.backend.id)
        .isEqualTo(CodeOwnerBackendId.getBackendId(backendConfig.getDefaultBackend().getClass()));
    assertThat(codeOwnerProjectConfigInfo.backend.idsByBranch).isNull();
    assertThat(codeOwnerProjectConfigInfo.requiredApproval.label)
        .isEqualTo(RequiredApproval.DEFAULT_LABEL);
    assertThat(codeOwnerProjectConfigInfo.requiredApproval.value)
        .isEqualTo(RequiredApproval.DEFAULT_VALUE);
  }

  @Test
  public void getConfigWithConfiguredBackend() throws Exception {
    String otherBackendId = getOtherCodeOwnerBackend(backendConfig.getDefaultBackend());
    configureBackend(project, otherBackendId);
    CodeOwnerProjectConfigInfo codeOwnerProjectConfigInfo =
        projectCodeOwnersApiFactory.project(project).getConfig();
    assertThat(codeOwnerProjectConfigInfo.backend.id).isEqualTo(otherBackendId);
  }

  @Test
  public void getConfigWithConfiguredBranchSpecificBackend() throws Exception {
    String otherBackendId = getOtherCodeOwnerBackend(backendConfig.getDefaultBackend());
    configureBackend(project, "master", otherBackendId);
    CodeOwnerProjectConfigInfo codeOwnerProjectConfigInfo =
        projectCodeOwnersApiFactory.project(project).getConfig();
    assertThat(codeOwnerProjectConfigInfo.backend.id).isNotEqualTo(otherBackendId);
    assertThat(codeOwnerProjectConfigInfo.backend.idsByBranch)
        .containsExactly("refs/heads/master", otherBackendId);
  }

  @Test
  public void branchSpecificBackendIsOmittedIfItMatchesTheRepositoryBackend() throws Exception {
    String backendId =
        CodeOwnerBackendId.getBackendId(backendConfig.getDefaultBackend().getClass());
    configureBackend(project, "master", backendId);
    CodeOwnerProjectConfigInfo codeOwnerProjectConfigInfo =
        projectCodeOwnersApiFactory.project(project).getConfig();
    assertThat(codeOwnerProjectConfigInfo.backend.id).isEqualTo(backendId);
    assertThat(codeOwnerProjectConfigInfo.backend.idsByBranch).isNull();
  }

  @Test
  public void branchSpecificBackendIsOmittedForNonExistingBranch() throws Exception {
    String otherBackendId = getOtherCodeOwnerBackend(backendConfig.getDefaultBackend());
    configureBackend(project, "non-existing", otherBackendId);
    CodeOwnerProjectConfigInfo codeOwnerProjectConfigInfo =
        projectCodeOwnersApiFactory.project(project).getConfig();
    assertThat(codeOwnerProjectConfigInfo.backend.id).isNotEqualTo(otherBackendId);
    assertThat(codeOwnerProjectConfigInfo.backend.idsByBranch).isNull();
  }

  @Test
  public void branchSpecificBackendIsOmittedForNonVisibleBranch() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/heads/master").group(REGISTERED_USERS))
        .update();

    String otherBackendId = getOtherCodeOwnerBackend(backendConfig.getDefaultBackend());
    configureBackend(project, "master", otherBackendId);
    CodeOwnerProjectConfigInfo codeOwnerProjectConfigInfo =
        projectCodeOwnersApiFactory.project(project).getConfig();
    assertThat(codeOwnerProjectConfigInfo.backend.id).isNotEqualTo(otherBackendId);
    assertThat(codeOwnerProjectConfigInfo.backend.idsByBranch).isNull();
  }

  @Test
  public void getConfigWithConfiguredRequiredApproval() throws Exception {
    configureRequiredApproval(project, "Code-Review+2");
    CodeOwnerProjectConfigInfo codeOwnerProjectConfigInfo =
        projectCodeOwnersApiFactory.project(project).getConfig();
    assertThat(codeOwnerProjectConfigInfo.requiredApproval.label).isEqualTo("Code-Review");
    assertThat(codeOwnerProjectConfigInfo.requiredApproval.value).isEqualTo(2);
  }

  private void configureBackend(Project.NameKey project, String backendName) throws Exception {
    configureBackend(project, null, backendName);
  }

  private void configureBackend(
      Project.NameKey project, @Nullable String branch, String backendName) throws Exception {
    setConfig(project, branch, BackendConfig.KEY_BACKEND, backendName);
  }

  private void configureRequiredApproval(Project.NameKey project, String requiredApproval)
      throws Exception {
    setConfig(project, null, RequiredApproval.KEY_REQUIRED_APPROVAL, requiredApproval);
  }

  private void setConfig(Project.NameKey project, String subsection, String key, String value)
      throws Exception {
    Config codeOwnersConfig = new Config();
    codeOwnersConfig.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS, subsection, key, value);
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      Ref ref = testRepo.getRepository().exactRef(RefNames.REFS_CONFIG);
      RevCommit head = testRepo.getRevWalk().parseCommit(ref.getObjectId());
      testRepo.update(
          RefNames.REFS_CONFIG,
          testRepo
              .commit()
              .parent(head)
              .message("Configure code owner backend")
              .add("code-owners.config", codeOwnersConfig.toText()));
    }
    projectCache.evict(project);
  }

  /** Returns the ID of a code owner backend that is not the given backend. */
  private String getOtherCodeOwnerBackend(CodeOwnerBackend codeOwnerBackend) {
    for (CodeOwnerBackendId codeOwnerBackendId : CodeOwnerBackendId.values()) {
      if (!codeOwnerBackendId.getCodeOwnerBackendClass().equals(codeOwnerBackend.getClass())) {
        return codeOwnerBackendId.getBackendId();
      }
    }
    throw new IllegalStateException(
        String.format("couldn't find other backend than %s", codeOwnerBackend.getClass()));
  }
}
