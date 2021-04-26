package com.google.gerrit.plugins.codeowners.acceptance.batch;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.plugins.codeowners.testing.LegacySubmitRequirementInfoSubject.assertThatCollection;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.plugins.codeowners.testing.LegacySubmitRequirementInfoSubject;
import com.google.inject.Inject;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

/**
 * Test that verifies that the {@link com.google.gerrit.plugins.codeowners.module.BatchModule} has
 * bound all classes that are needed to run {@code
 * com.google.gerrit.plugins.codeowners.backend.CodeOwnerSubmitRule}.
 */
@TestPlugin(
    name = "code-owners",
    sysModule = "com.google.gerrit.plugins.codeowners.module.BatchModule")
public class CodeOwnerSubmitRuleBatchIT extends LightweightPluginDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "PROJECT_OWNERS")
  public void invokeCodeOwnerSubmitRule() throws Exception {
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
    submitRequirementInfoSubject.hasFallbackTextThat().isEqualTo("Code Owners");
    submitRequirementInfoSubject.hasTypeThat().isEqualTo("code-owners");

    // Approve by a project owner who is code owner since project owners are configured as fallback
    // code owners.
    requestScopeOperations.setApiUser(admin.id());
    approve(changeId);

    // Verify that the change is submittable now.
    changeInfo = gApi.changes().id(changeId).get(ListChangesOption.SUBMITTABLE);
    assertThat(changeInfo.submittable).isTrue();

    // Check the submit requirement.
    submitRequirementInfoSubject = assertThatCollection(changeInfo.requirements).onlyElement();
    submitRequirementInfoSubject.hasStatusThat().isEqualTo("OK");
    submitRequirementInfoSubject.hasFallbackTextThat().isEqualTo("Code Owners");
    submitRequirementInfoSubject.hasTypeThat().isEqualTo("code-owners");
  }
}
