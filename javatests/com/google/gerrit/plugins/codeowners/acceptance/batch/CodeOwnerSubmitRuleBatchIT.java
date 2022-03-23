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

package com.google.gerrit.plugins.codeowners.acceptance.batch;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.plugins.codeowners.testing.LegacySubmitRequirementInfoSubject.assertThatCollection;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.testing.LegacySubmitRequirementInfoSubject;
import com.google.inject.Inject;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Before;
import org.junit.Test;

/**
 * Test that verifies that the {@link com.google.gerrit.plugins.codeowners.module.BatchModule} has
 * bound all classes that are needed to run {@code
 * com.google.gerrit.plugins.codeowners.backend.CodeOwnerSubmitRule}.
 */
@TestPlugin(
    name = "code-owners",
    sysModule = "com.google.gerrit.plugins.codeowners.acceptance.TestBatchModule")
public class CodeOwnerSubmitRuleBatchIT extends LightweightPluginDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  private CodeOwnerConfigOperations codeOwnerConfigOperations;

  @Before
  public void testSetup() throws Exception {
    codeOwnerConfigOperations =
        plugin.getSysInjector().getInstance(CodeOwnerConfigOperations.class);
  }

  @Test
  public void invokeCodeOwnerSubmitRule() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    // Upload a change as a non-code owner.
    TestRepository<InMemoryRepository> testRepo = cloneProject(project, user);
    PushOneCommit push =
        pushFactory.create(user.newIdent(), testRepo, "Test Change", "foo/bar.baz", "file content");
    String changeId = push.to("refs/for/master").getChangeId();

    // Approve by a non-code-owner.
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-2, +2))
        .update();
    requestScopeOperations.setApiUser(user.id());
    approve(changeId);

    // Verify that the change is not submittable.
    ChangeInfo changeInfo = gApi.changes().id(changeId).get(ListChangesOption.SUBMITTABLE);
    assertThat(changeInfo.submittable).isFalse();

    // Check the submit requirement.
    LegacySubmitRequirementInfoSubject submitRequirementInfoSubject =
        assertThatCollection(changeInfo.requirements).onlyElement();
    submitRequirementInfoSubject.hasStatusThat().isEqualTo("NOT_READY");
    submitRequirementInfoSubject.hasFallbackTextThat().isEqualTo("Code-Owners");
    submitRequirementInfoSubject.hasTypeThat().isEqualTo("code-owners");

    // Approve by a code owner.
    requestScopeOperations.setApiUser(admin.id());
    approve(changeId);

    // Verify that the change is submittable now.
    changeInfo = gApi.changes().id(changeId).get(ListChangesOption.SUBMITTABLE);
    assertThat(changeInfo.submittable).isTrue();

    // Check the submit requirement.
    submitRequirementInfoSubject = assertThatCollection(changeInfo.requirements).onlyElement();
    submitRequirementInfoSubject.hasStatusThat().isEqualTo("OK");
    submitRequirementInfoSubject.hasFallbackTextThat().isEqualTo("Code-Owners");
    submitRequirementInfoSubject.hasTypeThat().isEqualTo("code-owners");
  }
}
