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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.RequiredApprovalSubject.assertThat;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.server.project.ProjectState;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link com.google.gerrit.plugins.codeowners.backend.config.RequiredApprovalConfig}. */
public class RequiredApprovalConfigTest extends AbstractRequiredApprovalConfigTest {
  private RequiredApprovalConfig requiredApprovalConfig;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    requiredApprovalConfig = plugin.getSysInjector().getInstance(RequiredApprovalConfig.class);
  }

  @Override
  protected AbstractRequiredApprovalConfig getRequiredApprovalConfig() {
    return requiredApprovalConfig;
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "Code-Review+2")
  public void getFromGlobalPluginConfig() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    ImmutableList<RequiredApproval> requiredApproval =
        getRequiredApprovalConfig().get(projectState, new Config());
    assertThat(requiredApproval).hasSize(1);
    assertThat(requiredApproval).element(0).hasLabelNameThat().isEqualTo("Code-Review");
    assertThat(requiredApproval).element(0).hasValueThat().isEqualTo(2);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.requiredApproval", value = "INVALID")
  public void cannotIfGlobalConfigIsInvalid() throws Exception {
    testCannotGetIfGlobalConfigIsInvalid("INVALID");
  }

  @Test
  public void createDefaultRequiresProjectState() throws Exception {
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> requiredApprovalConfig.createDefault(null));
    assertThat(npe).hasMessageThat().isEqualTo("projectState");
  }

  @Test
  public void createDefault() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    RequiredApproval requiredApproval = requiredApprovalConfig.createDefault(projectState);
    assertThat(requiredApproval.labelType().getName())
        .isEqualTo(RequiredApprovalConfig.DEFAULT_LABEL);
    assertThat(requiredApproval.value()).isEqualTo(RequiredApprovalConfig.DEFAULT_VALUE);
  }
}
