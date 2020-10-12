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

package com.google.gerrit.plugins.codeowners.acceptance;

import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.PublishChangeEditInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.JgitPath;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.config.StatusConfig;
import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.Map;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;

/**
 * Base class for code owner integration tests.
 *
 * <p>We have this base class because hard-coding the {@link TestPlugin} annotation for each test
 * class would be too much overhead.
 *
 * <p>Integration/acceptance tests should extend {@link AbstractCodeOwnersIT} instead of exting this
 * class directly.
 */
@TestPlugin(
    name = "code-owners",
    sysModule = "com.google.gerrit.plugins.codeowners.acceptance.TestModule")
public class AbstractCodeOwnersTest extends LightweightPluginDaemonTest {
  @Inject private ProjectOperations projectOperations;

  private CodeOwnerConfigOperations codeOwnerConfigOperations;

  @Before
  public void testSetup() throws Exception {
    codeOwnerConfigOperations =
        plugin.getSysInjector().getInstance(CodeOwnerConfigOperations.class);
  }

  protected String createChangeWithFileDeletion(Path filePath) throws Exception {
    return createChangeWithFileDeletion(filePath.toString());
  }

  protected String createChangeWithFileDeletion(String filePath) throws Exception {
    createChange("Change Adding A File", JgitPath.of(filePath).get(), "file content").getChangeId();

    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Change Deleting A File",
            JgitPath.of(filePath).get(),
            "file content");
    Result r = push.rm("refs/for/master");
    r.assertOkStatus();
    return r.getChangeId();
  }

  protected String createChangeWithFileRename(Path oldFilePath, Path newFilePath) throws Exception {
    return createChangeWithFileRename(oldFilePath.toString(), newFilePath.toString());
  }

  protected String createChangeWithFileRename(String oldFilePath, String newFilePath)
      throws Exception {
    String changeId1 =
        createChange("Change Adding A File", JgitPath.of(oldFilePath).get(), "file content")
            .getChangeId();

    // The PushOneCommit test API doesn't support renaming files in a change. Use the change edit
    // Java API instead.
    ChangeInput changeInput = new ChangeInput();
    changeInput.project = project.get();
    changeInput.branch = "master";
    changeInput.subject = "Change Renaming A File";
    changeInput.baseChange = changeId1;
    String changeId2 = gApi.changes().create(changeInput).get().changeId;
    gApi.changes().id(changeId2).edit().create();
    gApi.changes()
        .id(changeId2)
        .edit()
        .renameFile(JgitPath.of(oldFilePath).get(), JgitPath.of(newFilePath).get());
    gApi.changes().id(changeId2).edit().publish(new PublishChangeEditInput());
    return changeId2;
  }

  protected void amendChange(TestAccount testAccount, String changeId) throws Exception {
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();

    TestRepository<InMemoryRepository> testRepo = cloneProject(project, testAccount);
    GitUtil.fetch(testRepo, "refs/*:refs/*");
    testRepo.reset(changeInfo.currentRevision);

    PushOneCommit push =
        pushFactory.create(
            testAccount.newIdent(),
            testRepo,
            changeInfo.subject,
            ImmutableMap.<String, String>of(),
            changeId);
    push.to("refs/for/master").assertOkStatus();
  }

  protected void disableCodeOwnersForProject(Project.NameKey project) throws Exception {
    setCodeOwnersConfig(project, null, StatusConfig.KEY_DISABLED, "true");
  }

  protected void enableCodeOwnersForProject(Project.NameKey project) throws Exception {
    setCodeOwnersConfig(project, null, StatusConfig.KEY_DISABLED, "false");
  }

  protected void setCodeOwnersConfig(
      Project.NameKey project, String subsection, String key, String value) throws Exception {
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

  protected void createOwnersOverrideLabel() throws RestApiException {
    LabelDefinitionInput input = new LabelDefinitionInput();
    input.values = ImmutableMap.of("+1", "Override", " 0", "No Override");
    gApi.projects().name(project.get()).label("Owners-Override").create(input).get();

    // Allow to vote on the Owners-Override label.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            TestProjectUpdate.allowLabel("Owners-Override")
                .range(0, 1)
                .ref("refs/*")
                .group(REGISTERED_USERS)
                .build())
        .update();
  }

  /**
   * Creates an arbitrary code owner config file.
   *
   * <p>Can be used to create an arbitrary code owner config in order to avoid entering the
   * bootstrapping code path in {@link
   * com.google.gerrit.plugins.codeowners.backend.CodeOwnerApprovalCheck}.
   */
  protected void createArbitraryCodeOwnerConfigFile() throws Exception {
    TestAccount arbitraryUser =
        accountCreator.create(
            "arbitrary-user", "arbitrary-user@example.com", "Arbitrary User", null);

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/arbitrary/path/")
        .addCodeOwnerEmail(arbitraryUser.email())
        .create();
  }

  /**
   * Creates a new change for the given test account.
   *
   * @param testAccount the account that should own the new change
   * @param subject the subject of the new change
   * @param fileName the name of the file in the change
   * @param content the content of the file in the change
   * @return the push result
   */
  protected PushOneCommit.Result createChange(
      TestAccount testAccount, String subject, String fileName, String content) throws Exception {
    TestRepository<InMemoryRepository> testRepo = cloneProject(project, testAccount);
    PushOneCommit push =
        pushFactory.create(testAccount.newIdent(), testRepo, subject, fileName, content);
    return push.to("refs/for/master");
  }

  /**
   * Creates a new change with multiple files.
   *
   * <p>The created change is pushed for the {@code master} branch and is owned by the admin user.
   *
   * @param subject the subject of the new change
   * @param files map of the file names to file contents
   * @return the push result
   */
  protected PushOneCommit.Result createChange(String subject, Map<String, String> files)
      throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo, subject, files);
    return push.to("refs/for/master");
  }
}
