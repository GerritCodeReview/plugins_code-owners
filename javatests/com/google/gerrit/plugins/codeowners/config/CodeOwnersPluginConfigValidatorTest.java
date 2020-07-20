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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.acceptance.GitUtil.pushHead;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackendId;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnersPluginConfigValidator}. */
public class CodeOwnersPluginConfigValidatorTest extends AbstractCodeOwnersTest {
  private CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnersPluginConfiguration =
        plugin.getSysInjector().getInstance(CodeOwnersPluginConfiguration.class);
  }

  @Test
  public void cannotUploadNonParseableConfig() throws Exception {
    fetchRefsMetaConfig();

    setCodeOwnersConfig("INVALID");

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus())
        .isEqualTo(Status.REJECTED_OTHER_REASON);
    assertThat(r.getMessages())
        .contains(
            String.format(
                "Invalid config file code-owners.config in project %s in branch %s",
                project, RefNames.REFS_CONFIG));
    assertThat(r.getMessages()).contains("Invalid line in config file");
  }

  @Test
  public void configureBackendForProject() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        null,
        BackendConfig.KEY_BACKEND,
        CodeOwnerBackendId.PROTO.getBackendId());
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus()).isEqualTo(Status.OK);
    assertThat(codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")))
        .isInstanceOf(ProtoBackend.class);
  }

  @Test
  public void configureBackendForBranch() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        "master",
        BackendConfig.KEY_BACKEND,
        CodeOwnerBackendId.PROTO.getBackendId());
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus()).isEqualTo(Status.OK);
    assertThat(codeOwnersPluginConfiguration.getBackend(BranchNameKey.create(project, "master")))
        .isInstanceOf(ProtoBackend.class);
  }

  @Test
  public void cannotConfigureInvalidBackendForProject() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        null,
        BackendConfig.KEY_BACKEND,
        "INVALID");
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus())
        .isEqualTo(Status.REJECTED_OTHER_REASON);
    assertThat(r.getMessages())
        .contains(
            "Code owner backend 'INVALID' that is configured in code-owners.config"
                + " (parameter codeOwners.backend) not found.");
  }

  @Test
  public void cannotConfigureInvalidBackendForBranch() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        "master",
        BackendConfig.KEY_BACKEND,
        "INVALID");
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus())
        .isEqualTo(Status.REJECTED_OTHER_REASON);
    assertThat(r.getMessages())
        .contains(
            "Code owner backend 'INVALID' that is configured in code-owners.config"
                + " (parameter codeOwners.master.backend) not found.");
  }

  @Test
  public void configureRequiredApproval() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        null,
        RequiredApproval.KEY_REQUIRED_APPROVAL,
        "Code-Review+2");
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus()).isEqualTo(Status.OK);
    RequiredApproval requiredApproval =
        codeOwnersPluginConfiguration.getRequiredApproval(BranchNameKey.create(project, "master"));
    assertThat(requiredApproval.labelType().getName()).isEqualTo("Code-Review");
    assertThat(requiredApproval.value()).isEqualTo(2);
  }

  @Test
  public void cannotConfigureInvalidRequiredApproval() throws Exception {
    fetchRefsMetaConfig();

    Config cfg = new Config();
    cfg.setString(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        null,
        RequiredApproval.KEY_REQUIRED_APPROVAL,
        "INVALID");
    setCodeOwnersConfig(cfg);

    PushResult r = pushRefsMetaConfig();
    assertThat(r.getRemoteUpdate(RefNames.REFS_CONFIG).getStatus())
        .isEqualTo(Status.REJECTED_OTHER_REASON);
    assertThat(r.getMessages())
        .contains(
            "Required approval 'INVALID' that is configured in code-owners.config (parameter"
                + " codeOwners.requiredApproval) is invalid: Invalid format, expected"
                + " '<label-name>+<label-value>'.");
  }

  private void fetchRefsMetaConfig() throws Exception {
    fetch(testRepo, RefNames.REFS_CONFIG + ":" + RefNames.REFS_CONFIG);
    testRepo.reset(RefNames.REFS_CONFIG);
  }

  private PushResult pushRefsMetaConfig() throws Exception {
    return pushHead(testRepo, RefNames.REFS_CONFIG);
  }

  private void setCodeOwnersConfig(Config codeOwnersConfig) throws Exception {
    setCodeOwnersConfig(codeOwnersConfig.toText());
  }

  private void setCodeOwnersConfig(String codeOwnersConfig) throws Exception {
    RevCommit head = getHead(testRepo.getRepository(), RefNames.REFS_CONFIG);
    RevCommit commit =
        testRepo.update(
            RefNames.REFS_CONFIG,
            testRepo
                .commit()
                .parent(head)
                .message("Add test code owner config")
                .author(admin.newIdent())
                .committer(admin.newIdent())
                .add("code-owners.config", codeOwnersConfig));

    testRepo.reset(commit);
  }
}
