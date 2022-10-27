// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.plugins.codeowners.backend;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginProjectConfigSnapshot;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerApprovalCheckInput}. */
public class CodeOwnerApprovalCheckInputTest extends AbstractCodeOwnersTest {
  @Inject private ChangeNotes.Factory changeNotesFactory;
  @Inject private RequestScopeOperations requestScopeOperations;

  private CodeOwnerApprovalCheckInput.Loader.Factory inputLoaderFactory;
  private CodeOwnerResolver codeOwnerResolver;
  private CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig;
  private TestAccount user2;
  private Change.Id changeId;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    inputLoaderFactory =
        plugin.getSysInjector().getInstance(CodeOwnerApprovalCheckInput.Loader.Factory.class);
    codeOwnerResolver = plugin.getSysInjector().getInstance(CodeOwnerResolver.class);
    CodeOwnersPluginConfiguration codeOwnersPluginConfiguration =
        plugin.getSysInjector().getInstance(CodeOwnersPluginConfiguration.class);
    codeOwnersConfig = codeOwnersPluginConfiguration.getProjectConfig(project);
  }

  @Before
  public void setUp() throws Exception {
    user2 = accountCreator.user2();
    changeId = createChange().getChange().getId();
  }

  @Test
  public void noReviewers() {
    assertThat(loadInput().reviewers()).isEmpty();
  }

  @Test
  public void withReviewers() throws Exception {
    changeApi().addReviewer(user.email());
    changeApi().addReviewer(user2.email());

    // make the change owner a reviewer:
    // the change owner cannot be added as a reviewer, but the change owner becomes a reviewer when
    // they vote on the change
    recommend(changeId.toString());

    assertThat(loadInput().reviewers()).containsExactly(user.id(), user2.id(), admin.id());
  }

  @Test
  public void withReviewers_selfApprovalsIgnored() throws Exception {
    disableSelfCodeReviewApprovals();

    changeApi().addReviewer(user.email());
    changeApi().addReviewer(user2.email());

    // make the change owner a reviewer:
    // the change owner cannot be added as a reviewer, but the change owner becomes a reviewer when
    // they vote on the change
    recommend(changeId.toString());

    assertThat(loadInput().reviewers()).containsExactly(user.id(), user2.id());
  }

  @Test
  public void noApprovers() {
    assertThat(loadInput().approvers()).isEmpty();
  }

  @Test
  public void withApprovers() throws Exception {
    // self approve
    recommend(changeId.toString());

    // approve as user
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // approve as user2
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId.toString());

    assertThat(loadInput().reviewers()).containsExactly(admin.id(), user.id(), user2.id());
  }

  @Test
  public void withApprovers_selfApprovalsIgnored() throws Exception {
    disableSelfCodeReviewApprovals();

    // self approve
    recommend(changeId.toString());

    // approve as user
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId.toString());

    // approve as user2
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId.toString());

    assertThat(loadInput().reviewers()).containsExactly(user.id(), user2.id());
  }

  @Test
  public void noImplicitApprover() {
    assertThat(loadInput().implicitApprover()).isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableImplicitApprovals", value = "true")
  public void withImplicitApprover() {
    assertThat(loadInput().implicitApprover()).hasValue(admin.id());
  }

  @Test
  public void noOverrides() {
    assertThat(loadInput().overrides()).isEmpty();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void withOverrides_selfApprovalsIgnored() throws Exception {
    createOwnersOverrideLabel();
    disableSelfOwnersOverrideApprovals();

    ReviewInput reviewInput = new ReviewInput().label("Owners-Override", 1);

    // self override
    gApi.changes().id(changeId.toString()).current().review(reviewInput);

    // override as user
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(changeId.toString()).current().review(reviewInput);

    // override as user2
    requestScopeOperations.setApiUser(user2.id());
    gApi.changes().id(changeId.toString()).current().review(reviewInput);

    assertThat(
            loadInput().overrides().stream()
                .map(PatchSetApproval::accountId)
                .collect(toImmutableSet()))
        .containsExactly(user.id(), user2.id());
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.overrideApproval", value = "Owners-Override+1")
  public void withOverrides() throws Exception {
    createOwnersOverrideLabel();

    ReviewInput reviewInput = new ReviewInput().label("Owners-Override", 1);

    // self override
    gApi.changes().id(changeId.toString()).current().review(reviewInput);

    // override as user
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(changeId.toString()).current().review(reviewInput);

    // override as user2
    requestScopeOperations.setApiUser(user2.id());
    gApi.changes().id(changeId.toString()).current().review(reviewInput);

    assertThat(
            loadInput().overrides().stream()
                .map(PatchSetApproval::accountId)
                .collect(toImmutableSet()))
        .containsExactly(admin.id(), user.id(), user2.id());
  }

  @Test
  public void noGlobalCodeOwners() {
    CodeOwnerResolverResult globalCodeOwners = loadInput().globalCodeOwners();
    assertThat(globalCodeOwners.codeOwnersAccountIds()).isEmpty();
    assertThat(globalCodeOwners.ownedByAllUsers()).isFalse();
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.globalCodeOwner",
      values = {"user1@example.com", "user2@example.com"})
  public void withGlobalCodeOwners() {
    CodeOwnerResolverResult globalCodeOwners = loadInput().globalCodeOwners();
    assertThat(globalCodeOwners.codeOwnersAccountIds()).containsExactly(user.id(), user2.id());
    assertThat(globalCodeOwners.ownedByAllUsers()).isFalse();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "*")
  public void withAllUsersAsGlobalCodeOwners() {
    CodeOwnerResolverResult globalCodeOwners = loadInput().globalCodeOwners();
    assertThat(globalCodeOwners.codeOwnersAccountIds()).isEmpty();
    assertThat(globalCodeOwners.ownedByAllUsers()).isTrue();
  }

  @Test
  public void withFallbackCodeOwnersNone() {
    assertThat(loadInput().fallbackCodeOwners()).isEqualTo(FallbackCodeOwners.NONE);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "ALL_USERS")
  public void withFallbackCodeOwnersAllUser() {
    assertThat(loadInput().fallbackCodeOwners()).isEqualTo(FallbackCodeOwners.ALL_USERS);
  }

  private void disableSelfCodeReviewApprovals() throws Exception {
    disableSelfApprovals(allProjects, "Code-Review");
  }

  private void disableSelfOwnersOverrideApprovals() throws Exception {
    disableSelfApprovals(project, "Owners-Override");
  }

  private void disableSelfApprovals(Project.NameKey project, String labelName) throws Exception {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.ignoreSelfApproval = true;
    gApi.projects().name(project.get()).label(labelName).update(input);
  }

  private ChangeApi changeApi() throws RestApiException {
    return gApi.changes().id(changeId.get());
  }

  private CodeOwnerApprovalCheckInput loadInput() {
    ChangeNotes changeNotes = changeNotesFactory.create(project, changeId);
    return inputLoaderFactory.create(codeOwnersConfig, codeOwnerResolver, changeNotes).load();
  }
}
