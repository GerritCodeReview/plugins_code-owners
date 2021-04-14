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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.server.schema.AllProjectsInput.getDefaultCodeReviewLabel;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.api.BackendInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerBranchConfigInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerProjectConfigInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnersStatusInfo;
import com.google.gerrit.plugins.codeowners.api.RequiredApprovalInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackendId;
import com.google.gerrit.plugins.codeowners.backend.FallbackCodeOwners;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfigSnapshot;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.RequiredApproval;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.BranchResource;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.ListBranches;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

/** Tests for {@link CodeOwnerProjectConfigJson}. */
public class CodeOwnerProjectConfigJsonTest extends AbstractCodeOwnersTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Mock private CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  @Mock private CodeOwnersPluginConfigSnapshot codeOwnersPluginConfigSnapshot;

  @Inject private CurrentUser currentUser;

  private CodeOwnerConfigOperations codeOwnerConfigOperations;
  private CodeOwnerProjectConfigJson codeOwnerProjectConfigJson;
  private FindOwnersBackend findOwnersBackend;
  private ProtoBackend protoBackend;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerConfigOperations =
        plugin.getSysInjector().getInstance(CodeOwnerConfigOperations.class);
    codeOwnerProjectConfigJson =
        new CodeOwnerProjectConfigJson(
            codeOwnersPluginConfiguration,
            plugin.getSysInjector().getInstance(new Key<Provider<ListBranches>>() {}));
    findOwnersBackend = plugin.getSysInjector().getInstance(FindOwnersBackend.class);
    protoBackend = plugin.getSysInjector().getInstance(ProtoBackend.class);
  }

  @Test
  public void formatRequiredApproval() throws Exception {
    RequiredApproval requiredApproval =
        RequiredApproval.create(LabelType.withDefaultValues("Code-Review"), (short) 2);
    RequiredApprovalInfo requiredApprovalInfo =
        CodeOwnerProjectConfigJson.formatRequiredApproval(requiredApproval);
    assertThat(requiredApprovalInfo.label).isEqualTo("Code-Review");
    assertThat(requiredApprovalInfo.value).isEqualTo(2);
  }

  @Test
  public void cannotFormatNullRequiredApproval() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> CodeOwnerProjectConfigJson.formatRequiredApproval(/* requiredApproval= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("requiredApproval");
  }

  @Test
  public void formatBackendIds() throws Exception {
    createBranch(BranchNameKey.create(project, "stable-2.10"));

    when(codeOwnersPluginConfigSnapshot.getBackend()).thenReturn(findOwnersBackend);
    when(codeOwnersPluginConfigSnapshot.getBackend("refs/heads/master"))
        .thenReturn(findOwnersBackend);
    when(codeOwnersPluginConfigSnapshot.getBackend("refs/meta/config"))
        .thenReturn(findOwnersBackend);
    when(codeOwnersPluginConfigSnapshot.getBackend("refs/heads/stable-2.10"))
        .thenReturn(protoBackend);
    when(codeOwnersPluginConfiguration.getProjectConfig(project))
        .thenReturn(codeOwnersPluginConfigSnapshot);

    BackendInfo backendInfo = codeOwnerProjectConfigJson.formatBackendInfo(createProjectResource());
    assertThat(backendInfo.id).isEqualTo(CodeOwnerBackendId.FIND_OWNERS.getBackendId());

    // This project has 3 branches ("master", "stable-2.10" and "refs/meta/config"). The
    // backendInfo.idsByBranch field only contains those branches that use a backend that differs
    // from the backend that is returned by the backendInfo.id field. This means since "master" and
    // "refs/meta/config" do use the find-owners backend, the same backend that is returned in the
    // backendInfo.id field, they are omitted in the backendInfo.idsByBranch field.
    assertThat(backendInfo.idsByBranch)
        .containsExactly("refs/heads/stable-2.10", CodeOwnerBackendId.PROTO.getBackendId());
  }

  @Test
  public void idsPerBranchNotSetIfThereIsNoBranchSpecificBackendConfiguration() throws Exception {
    when(codeOwnersPluginConfigSnapshot.getBackend()).thenReturn(findOwnersBackend);
    when(codeOwnersPluginConfigSnapshot.getBackend("refs/heads/master"))
        .thenReturn(findOwnersBackend);
    when(codeOwnersPluginConfigSnapshot.getBackend("refs/meta/config"))
        .thenReturn(findOwnersBackend);
    when(codeOwnersPluginConfiguration.getProjectConfig(project))
        .thenReturn(codeOwnersPluginConfigSnapshot);

    BackendInfo backendInfo = codeOwnerProjectConfigJson.formatBackendInfo(createProjectResource());
    assertThat(backendInfo.id).isEqualTo(CodeOwnerBackendId.FIND_OWNERS.getBackendId());
    assertThat(backendInfo.idsByBranch).isNull();
  }

  @Test
  public void formatCodeOwnerProjectConfig() throws Exception {
    createOwnersOverrideLabel();
    createBranch(BranchNameKey.create(project, "stable-2.10"));

    when(codeOwnersPluginConfigSnapshot.getFileExtension()).thenReturn(Optional.of("foo"));
    when(codeOwnersPluginConfigSnapshot.getMergeCommitStrategy())
        .thenReturn(MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION);
    when(codeOwnersPluginConfigSnapshot.getFallbackCodeOwners())
        .thenReturn(FallbackCodeOwners.ALL_USERS);
    when(codeOwnersPluginConfigSnapshot.getOverrideInfoUrl())
        .thenReturn(Optional.of("http://foo.example.com"));
    when(codeOwnersPluginConfigSnapshot.getInvalidCodeOwnerConfigInfoUrl())
        .thenReturn(Optional.of("http://bar.example.com"));
    when(codeOwnersPluginConfigSnapshot.isDisabled()).thenReturn(false);
    when(codeOwnersPluginConfigSnapshot.isDisabled(any(String.class))).thenReturn(false);
    when(codeOwnersPluginConfigSnapshot.getBackend()).thenReturn(findOwnersBackend);
    when(codeOwnersPluginConfigSnapshot.getBackend("refs/heads/master"))
        .thenReturn(findOwnersBackend);
    when(codeOwnersPluginConfigSnapshot.getBackend("refs/meta/config"))
        .thenReturn(findOwnersBackend);
    when(codeOwnersPluginConfigSnapshot.getBackend("refs/heads/stable-2.10"))
        .thenReturn(protoBackend);
    when(codeOwnersPluginConfigSnapshot.areImplicitApprovalsEnabled()).thenReturn(true);
    when(codeOwnersPluginConfigSnapshot.getRequiredApproval())
        .thenReturn(RequiredApproval.create(getDefaultCodeReviewLabel(), (short) 2));
    when(codeOwnersPluginConfigSnapshot.getOverrideApprovals())
        .thenReturn(
            ImmutableSortedSet.of(
                RequiredApproval.create(
                    LabelType.withDefaultValues("Owners-Override"), (short) 1)));
    when(codeOwnersPluginConfiguration.getProjectConfig(project))
        .thenReturn(codeOwnersPluginConfigSnapshot);

    CodeOwnerProjectConfigInfo codeOwnerProjectConfigInfo =
        codeOwnerProjectConfigJson.format(createProjectResource());
    assertThat(codeOwnerProjectConfigInfo.status.disabled).isNull();
    assertThat(codeOwnerProjectConfigInfo.status.disabledBranches).isNull();
    assertThat(codeOwnerProjectConfigInfo.general.fileExtension).isEqualTo("foo");
    assertThat(codeOwnerProjectConfigInfo.general.overrideInfoUrl)
        .isEqualTo("http://foo.example.com");
    assertThat(codeOwnerProjectConfigInfo.general.invalidCodeOwnerConfigInfoUrl)
        .isEqualTo("http://bar.example.com");
    assertThat(codeOwnerProjectConfigInfo.general.mergeCommitStrategy)
        .isEqualTo(MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION);
    assertThat(codeOwnerProjectConfigInfo.general.fallbackCodeOwners)
        .isEqualTo(FallbackCodeOwners.ALL_USERS);
    assertThat(codeOwnerProjectConfigInfo.general.implicitApprovals).isTrue();
    assertThat(codeOwnerProjectConfigInfo.backend.id)
        .isEqualTo(CodeOwnerBackendId.FIND_OWNERS.getBackendId());
    assertThat(codeOwnerProjectConfigInfo.backend.idsByBranch)
        .containsExactly("refs/heads/stable-2.10", CodeOwnerBackendId.PROTO.getBackendId());
    assertThat(codeOwnerProjectConfigInfo.requiredApproval.label).isEqualTo("Code-Review");
    assertThat(codeOwnerProjectConfigInfo.requiredApproval.value).isEqualTo(2);
    assertThat(codeOwnerProjectConfigInfo.overrideApproval).hasSize(1);
    assertThat(codeOwnerProjectConfigInfo.overrideApproval.get(0).label)
        .isEqualTo("Owners-Override");
    assertThat(codeOwnerProjectConfigInfo.overrideApproval.get(0).value).isEqualTo(1);
  }

  @Test
  public void disabledBranchesNotSetIfDisabledOnProjectLevel() throws Exception {
    when(codeOwnersPluginConfigSnapshot.isDisabled()).thenReturn(true);
    when(codeOwnersPluginConfiguration.getProjectConfig(project))
        .thenReturn(codeOwnersPluginConfigSnapshot);
    CodeOwnersStatusInfo statusInfo =
        codeOwnerProjectConfigJson.formatStatusInfo(createProjectResource());
    assertThat(statusInfo.disabled).isTrue();
    assertThat(statusInfo.disabledBranches).isNull();
  }

  @Test
  public void emptyStatus() throws Exception {
    when(codeOwnersPluginConfigSnapshot.isDisabled()).thenReturn(false);
    when(codeOwnersPluginConfigSnapshot.isDisabled("refs/heads/master")).thenReturn(false);
    when(codeOwnersPluginConfigSnapshot.isDisabled("refs/meta/config")).thenReturn(false);
    when(codeOwnersPluginConfiguration.getProjectConfig(project))
        .thenReturn(codeOwnersPluginConfigSnapshot);
    CodeOwnersStatusInfo statusInfo =
        codeOwnerProjectConfigJson.formatStatusInfo(createProjectResource());
    assertThat(statusInfo.disabled).isNull();
    assertThat(statusInfo.disabledBranches).isNull();
  }

  @Test
  public void withDisabledBranches() throws Exception {
    when(codeOwnersPluginConfigSnapshot.isDisabled()).thenReturn(false);
    when(codeOwnersPluginConfigSnapshot.isDisabled("refs/heads/master")).thenReturn(true);
    when(codeOwnersPluginConfigSnapshot.isDisabled("refs/meta/config")).thenReturn(false);
    when(codeOwnersPluginConfiguration.getProjectConfig(project))
        .thenReturn(codeOwnersPluginConfigSnapshot);
    CodeOwnersStatusInfo statusInfo =
        codeOwnerProjectConfigJson.formatStatusInfo(createProjectResource());
    assertThat(statusInfo.disabled).isNull();
    assertThat(statusInfo.disabledBranches).containsExactly("refs/heads/master");
  }

  @Test
  public void withMultipleOverrides() throws Exception {
    createOwnersOverrideLabel();

    when(codeOwnersPluginConfigSnapshot.getOverrideApprovals())
        .thenReturn(
            ImmutableSortedSet.of(
                RequiredApproval.create(LabelType.withDefaultValues("Owners-Override"), (short) 1),
                RequiredApproval.create(LabelType.withDefaultValues("Code-Review"), (short) 2)));
    when(codeOwnersPluginConfiguration.getProjectConfig(project))
        .thenReturn(codeOwnersPluginConfigSnapshot);

    ImmutableList<RequiredApprovalInfo> requiredApprovalInfos =
        codeOwnerProjectConfigJson.formatOverrideApprovalInfo(project);
    assertThat(requiredApprovalInfos).hasSize(2);
    assertThat(requiredApprovalInfos.get(0).label).isEqualTo("Code-Review");
    assertThat(requiredApprovalInfos.get(0).value).isEqualTo(2);
    assertThat(requiredApprovalInfos.get(1).label).isEqualTo("Owners-Override");
    assertThat(requiredApprovalInfos.get(1).value).isEqualTo(1);
  }

  @Test
  public void formatCodeOwnerBranchConfig() throws Exception {
    createOwnersOverrideLabel();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    when(codeOwnersPluginConfigSnapshot.getFileExtension()).thenReturn(Optional.of("foo"));
    when(codeOwnersPluginConfigSnapshot.getMergeCommitStrategy())
        .thenReturn(MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION);
    when(codeOwnersPluginConfigSnapshot.getFallbackCodeOwners())
        .thenReturn(FallbackCodeOwners.ALL_USERS);
    when(codeOwnersPluginConfigSnapshot.getOverrideInfoUrl())
        .thenReturn(Optional.of("http://foo.example.com"));
    when(codeOwnersPluginConfigSnapshot.getInvalidCodeOwnerConfigInfoUrl())
        .thenReturn(Optional.of("http://bar.example.com"));
    when(codeOwnersPluginConfigSnapshot.isDisabled(any(String.class))).thenReturn(false);
    when(codeOwnersPluginConfigSnapshot.getBackend("refs/heads/master"))
        .thenReturn(findOwnersBackend);
    when(codeOwnersPluginConfigSnapshot.areImplicitApprovalsEnabled()).thenReturn(true);
    when(codeOwnersPluginConfigSnapshot.getRequiredApproval())
        .thenReturn(RequiredApproval.create(getDefaultCodeReviewLabel(), (short) 2));
    when(codeOwnersPluginConfigSnapshot.getOverrideApprovals())
        .thenReturn(
            ImmutableSortedSet.of(
                RequiredApproval.create(
                    LabelType.withDefaultValues("Owners-Override"), (short) 1)));
    when(codeOwnersPluginConfiguration.getProjectConfig(project))
        .thenReturn(codeOwnersPluginConfigSnapshot);

    CodeOwnerBranchConfigInfo codeOwnerBranchConfigInfo =
        codeOwnerProjectConfigJson.format(createBranchResource("refs/heads/master"));
    assertThat(codeOwnerBranchConfigInfo.disabled).isNull();
    assertThat(codeOwnerBranchConfigInfo.general.fileExtension).isEqualTo("foo");
    assertThat(codeOwnerBranchConfigInfo.general.overrideInfoUrl)
        .isEqualTo("http://foo.example.com");
    assertThat(codeOwnerBranchConfigInfo.general.invalidCodeOwnerConfigInfoUrl)
        .isEqualTo("http://bar.example.com");
    assertThat(codeOwnerBranchConfigInfo.general.mergeCommitStrategy)
        .isEqualTo(MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION);
    assertThat(codeOwnerBranchConfigInfo.general.fallbackCodeOwners)
        .isEqualTo(FallbackCodeOwners.ALL_USERS);
    assertThat(codeOwnerBranchConfigInfo.general.implicitApprovals).isTrue();
    assertThat(codeOwnerBranchConfigInfo.backendId)
        .isEqualTo(CodeOwnerBackendId.FIND_OWNERS.getBackendId());
    assertThat(codeOwnerBranchConfigInfo.requiredApproval.label).isEqualTo("Code-Review");
    assertThat(codeOwnerBranchConfigInfo.requiredApproval.value).isEqualTo(2);
    assertThat(codeOwnerBranchConfigInfo.overrideApproval).hasSize(1);
    assertThat(codeOwnerBranchConfigInfo.overrideApproval.get(0).label)
        .isEqualTo("Owners-Override");
    assertThat(codeOwnerBranchConfigInfo.overrideApproval.get(0).value).isEqualTo(1);
  }

  @Test
  public void formatCodeOwnerBranchConfig_disabled() throws Exception {
    when(codeOwnersPluginConfigSnapshot.isDisabled(any(String.class))).thenReturn(true);
    when(codeOwnersPluginConfiguration.getProjectConfig(project))
        .thenReturn(codeOwnersPluginConfigSnapshot);

    CodeOwnerBranchConfigInfo codeOwnerBranchConfigInfo =
        codeOwnerProjectConfigJson.format(createBranchResource("refs/heads/master"));
    assertThat(codeOwnerBranchConfigInfo.disabled).isTrue();
    assertThat(codeOwnerBranchConfigInfo.general).isNull();
    assertThat(codeOwnerBranchConfigInfo.backendId).isNull();
    assertThat(codeOwnerBranchConfigInfo.requiredApproval).isNull();
    assertThat(codeOwnerBranchConfigInfo.overrideApproval).isNull();
  }

  private ProjectResource createProjectResource() {
    return new ProjectResource(
        projectCache.get(project).orElseThrow(illegalState(project)), currentUser);
  }

  private BranchResource createBranchResource(String branch) throws IOException {
    try (Repository repository = repoManager.openRepository(project)) {
      Ref ref = repository.exactRef(branch);
      return new BranchResource(
          projectCache.get(project).orElseThrow(illegalState(project)), currentUser, ref);
    }
  }
}
