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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoSubject.hasAccountId;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toMap;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.JgitPath;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwners;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnersForPathInChange} REST endpoint.
 *
 * <p>Further tests for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnersForPathInChange} REST endpoint that
 * require using the REST API are implemented in {@link
 * com.google.gerrit.plugins.codeowners.acceptance.restapi.GetCodeOwnersForPathInChangeRestIT}.
 */
public class GetCodeOwnersForPathInChangeIT extends AbstractGetCodeOwnersForPathIT {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private GroupOperations groupOperations;

  private TestAccount changeOwner;
  private String changeId;

  @Before
  public void createTestChange() throws Exception {
    changeOwner =
        accountCreator.create(
            "changeOwner", "changeOwner@example.com", "ChangeOwner", /* displayName= */ null);
    TestRepository<InMemoryRepository> testRepo = cloneProject(project, changeOwner);
    // Create a change that contains files for all paths that are used in the tests. This is
    // necessary since CodeOwnersInChangeCollection rejects requests for paths that are not present
    // in the change.
    PushOneCommit push =
        pushFactory.create(
            changeOwner.newIdent(),
            testRepo,
            "test change",
            TEST_PATHS.stream()
                .collect(
                    toMap(
                        path -> JgitPath.of(path).get(),
                        path -> String.format("content of %s", path))));
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();
    changeId = result.getChangeId();
  }

  @Override
  protected CodeOwners getCodeOwnersApi() throws RestApiException {
    return codeOwnersApiFactory.change(changeId, "current");
  }

  @Override
  protected List<CodeOwnerInfo> queryCodeOwners(CodeOwners.QueryRequest queryRequest, String path)
      throws RestApiException {
    assertWithMessage("test path %s was not registered", path)
        .that(gApi.changes().id(changeId).current().files())
        .containsKey(JgitPath.of(path).get());
    return super.queryCodeOwners(queryRequest, path);
  }

  @Test
  public void getCodeOwnersForDeletedFile() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(user.email())
        .create();

    String path = "/foo/bar/baz.txt";
    String changeId = createChangeWithFileDeletion(path);

    List<CodeOwnerInfo> codeOwnerInfos =
        codeOwnersApiFactory.change(changeId, "current").query().get(path);
    assertThat(codeOwnerInfos).comparingElementsUsing(hasAccountId()).containsExactly(user.id());
  }

  @Test
  public void getCodeOwnersForRenamedFile() throws Exception {
    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/new/")
        .addCodeOwnerEmail(user.email())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/old/")
        .addCodeOwnerEmail(user2.email())
        .create();

    String oldPath = "/foo/old/bar.txt";
    String newPath = "/foo/new/bar.txt";
    String changeId = createChangeWithFileRename(oldPath, newPath);

    List<CodeOwnerInfo> codeOwnerInfosNewPath =
        codeOwnersApiFactory.change(changeId, "current").query().get(newPath);
    assertThat(codeOwnerInfosNewPath)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id());

    List<CodeOwnerInfo> codeOwnerInfosOldPath =
        codeOwnersApiFactory.change(changeId, "current").query().get(oldPath);
    assertThat(codeOwnerInfosOldPath)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user2.id());
  }

  @Test
  public void cannotGetCodeOwnersForRevision() throws Exception {
    RevCommit revision = projectOperations.project(project).getHead("master");
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                queryCodeOwners(
                    getCodeOwnersApi().query().forRevision(revision.name()), "/foo/bar/baz.md"));
    assertThat(exception).hasMessageThat().isEqualTo("specifying revision is not supported");
  }

  @Test
  public void getCodeOwnersForPrivateChange() throws Exception {
    // Define 'user' as code owner.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();

    // Make the change private.
    gApi.changes().id(changeId).setPrivate(true, null);

    // Check that 'user' cannot set the private change.
    requestScopeOperations.setApiUser(user.id());
    assertThrows(ResourceNotFoundException.class, () -> gApi.changes().id(changeId).get());

    // Check that 'user' is anyway suggested as code owner for the file in the private change since
    // by adding 'user' as reviewer the change would get visible to 'user'.
    requestScopeOperations.setApiUser(changeOwner.id());
    List<CodeOwnerInfo> codeOwnerInfos =
        codeOwnersApiFactory.change(changeId, "current").query().get(TEST_PATHS.get(0));
    assertThat(codeOwnerInfos).comparingElementsUsing(hasAccountId()).containsExactly(user.id());
  }

  @Test
  public void codeOwnersThatAreServiceUsersAreFilteredOut() throws Exception {
    TestAccount serviceUser =
        accountCreator.create("serviceUser", "service.user@example.com", "Service User", null);

    // Create a code owner config with 2 code owners.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .addCodeOwnerEmail(serviceUser.email())
        .create();

    // Check that both code owners are suggested.
    assertThat(queryCodeOwners("/foo/bar/baz.md"))
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), serviceUser.id());

    // Make 'serviceUser' a service user.
    groupOperations
        .group(groupCache.get(AccountGroup.nameKey("Service Users")).get().getGroupUUID())
        .forUpdate()
        .addMember(serviceUser.id())
        .update();

    // Expect that 'serviceUser' is filtered out now.
    assertThat(queryCodeOwners("/foo/bar/baz.md"))
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id());
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "service.user@example.com")
  public void globalCodeOwnersThatAreServiceUsersAreFilteredOut() throws Exception {
    TestAccount serviceUser =
        accountCreator.create("serviceUser", "service.user@example.com", "Service User", null);
    groupOperations
        .group(groupCache.get(AccountGroup.nameKey("Service Users")).get().getGroupUUID())
        .forUpdate()
        .addMember(serviceUser.id())
        .update();
    assertThat(queryCodeOwners("/foo/bar/baz.md")).isEmpty();
  }

  @Test
  public void changeOwnerIsFilteredOut() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(changeOwner.email())
        .addCodeOwnerEmail(user.email())
        .create();

    assertThat(queryCodeOwners("/foo/bar/baz.md"))
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id());
  }
}
