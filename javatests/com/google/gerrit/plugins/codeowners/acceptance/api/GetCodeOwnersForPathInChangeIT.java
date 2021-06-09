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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnersInfoSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.fail;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.api.CodeOwners;
import com.google.gerrit.plugins.codeowners.api.CodeOwnersInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwner;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerAnnotations;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
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
  protected CodeOwnersInfo queryCodeOwners(CodeOwners.QueryRequest queryRequest, String path)
      throws RestApiException {
    assertWithMessage("test path %s was not registered", path)
        .that(gApi.changes().id(changeId).current().files())
        .containsKey(JgitPath.of(path).get());
    return super.queryCodeOwners(queryRequest, path);
  }

  @Test
  public void getCodeOwnersOrderIsAlwaysTheSameIfCodeOwnersHaveTheSameScoring() throws Exception {
    TestAccount user2 = accountCreator.user2();
    TestAccount user3 = accountCreator.create("user3", "user3@example.com", "User3", null);
    TestAccount user4 = accountCreator.create("user4", "user4@example.com", "User4", null);

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .addCodeOwnerEmail(user2.email())
        .addCodeOwnerEmail(user3.email())
        .addCodeOwnerEmail(user4.email())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    CodeOwnersInfo codeOwnersInfo = queryCodeOwners("/foo/bar.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id(), user3.id(), user4.id());

    // The first code owner in the result should be user as user has the best distance score.
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .element(0)
        .hasAccountIdThat()
        .isEqualTo(user.id());

    // The order of the other code owners is random since they have the same score.
    // Check that the order of the code owners with the same score is the same for further requests.
    List<Account.Id> accountIdsInRetrievedOrder1 =
        codeOwnersInfo.codeOwners.stream()
            .map(info -> Account.id(info.account._accountId))
            .collect(toList());
    for (int i = 0; i < 10; i++) {
      codeOwnersInfo = queryCodeOwners("/foo/bar.md");
      List<Account.Id> accountIdsInRetrievedOrder2 =
          codeOwnersInfo.codeOwners.stream()
              .map(info -> Account.id(info.account._accountId))
              .collect(toList());
      if (!accountIdsInRetrievedOrder1.equals(accountIdsInRetrievedOrder2)) {
        fail(
            String.format(
                "expected always the same order %s, but order was different %s",
                accountIdsInRetrievedOrder1, accountIdsInRetrievedOrder2));
      }
    }
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

    CodeOwnersInfo codeOwnersInfo =
        codeOwnersApiFactory.change(changeId, "current").query().get(path);
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id());
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

    CodeOwnersInfo codeOwnersInfoNewPath =
        codeOwnersApiFactory.change(changeId, "current").query().get(newPath);
    assertThat(codeOwnersInfoNewPath)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id());

    CodeOwnersInfo codeOwnersInfoOldPath =
        codeOwnersApiFactory.change(changeId, "current").query().get(oldPath);
    assertThat(codeOwnersInfoOldPath)
        .hasCodeOwnersThat()
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
    CodeOwnersInfo codeOwnersInfo =
        codeOwnersApiFactory.change(changeId, "current").query().get(TEST_PATHS.get(0));
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id());
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
        .hasCodeOwnersThat()
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
        .hasCodeOwnersThat()
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
    assertThat(queryCodeOwners("/foo/bar/baz.md")).hasCodeOwnersThat().isEmpty();
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
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id());
  }

  @Test
  public void codeOwnersWithNeverSuggestAnnotationAreFilteredOut() throws Exception {
    skipTestIfAnnotationsNotSupportedByCodeOwnersBackend();

    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addCodeOwnerEmail(admin.email())
                .addAnnotation(admin.email(), CodeOwnerAnnotations.NEVER_SUGGEST_ANNOTATION)
                .addCodeOwnerEmail(user.email())
                .addCodeOwnerEmail(user2.email())
                .build())
        .create();

    // Expectation: admin is filtered out because it is annotated with NEVER_SUGGEST.
    CodeOwnersInfo codeOwnersInfo = queryCodeOwners("foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), user2.id());
  }

  @Test
  public void codeOwnersWithNeverSuggestAnnotation_annotationIgnoredIfResultWouldBeEmpty()
      throws Exception {
    skipTestIfAnnotationsNotSupportedByCodeOwnersBackend();

    TestAccount serviceUser =
        accountCreator.create("serviceUser", "service.user@example.com", "Service User", null);

    // Make 'serviceUser' a service user.
    groupOperations
        .group(groupCache.get(AccountGroup.nameKey("Service Users")).get().getGroupUUID())
        .forUpdate()
        .addMember(serviceUser.id())
        .update();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addCodeOwnerEmail(serviceUser.email())
                .addCodeOwnerEmail(changeOwner.email())
                .addCodeOwnerEmail(admin.email())
                .addAnnotation(admin.email(), CodeOwnerAnnotations.NEVER_SUGGEST_ANNOTATION)
                .addCodeOwnerEmail(user.email())
                .addAnnotation(user.email(), CodeOwnerAnnotations.NEVER_SUGGEST_ANNOTATION)
                .build())
        .create();

    // Expectation: The service user and the change owner are filtered out. admin and user get
    // suggested despite of the NEVER_SUGGEST annotation since ignoring them would make the result
    // empty. This is a special case in which the NEVER_SUGGEST annotation is ignored.
    CodeOwnersInfo codeOwnersInfo = queryCodeOwners("foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id());
  }

  @Test
  public void codeOwnersWithNeverSuggestAnnotation_annotationSetForAllUsersWildcard()
      throws Exception {
    skipTestIfAnnotationsNotSupportedByCodeOwnersBackend();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addCodeOwnerEmail(CodeOwnerResolver.ALL_USERS_WILDCARD)
                .addAnnotation(
                    CodeOwnerResolver.ALL_USERS_WILDCARD,
                    CodeOwnerAnnotations.NEVER_SUGGEST_ANNOTATION)
                .addCodeOwnerEmail(admin.email())
                .addCodeOwnerEmail(user.email())
                .build())
        .create();

    // Expectation: Since all code owners are annotated with NEVER_SUGGEST (via the annotation on
    // the all users wildcard) the result would be empty. This is a special case in which the
    // NEVER_SUGGEST annotation is ignored, hence we expect admin and user to be suggested.
    CodeOwnersInfo codeOwnersInfo = queryCodeOwners("foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id());
  }

  @Test
  public void perFileCodeOwnersWithNeverSuggestAnnotationAreFilteredOut() throws Exception {
    skipTestIfAnnotationsNotSupportedByCodeOwnersBackend();

    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression(testPathExpressions.matchFileType("md"))
                .addCodeOwnerEmail(admin.email())
                .addAnnotation(admin.email(), CodeOwnerAnnotations.NEVER_SUGGEST_ANNOTATION)
                .addCodeOwnerEmail(user.email())
                .addCodeOwnerEmail(user2.email())
                .build())
        .create();

    // Expectation: admin is filtered out because it is annotated with NEVER_SUGGEST.
    CodeOwnersInfo codeOwnersInfo = queryCodeOwners("foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), user2.id());
  }

  @Test
  public void perFileCodeOwnersWithNeverSuggestAnnotation_annotationIgnoredIfResultWouldBeEmpty()
      throws Exception {
    skipTestIfAnnotationsNotSupportedByCodeOwnersBackend();

    TestAccount serviceUser =
        accountCreator.create("serviceUser", "service.user@example.com", "Service User", null);

    // Make 'serviceUser' a service user.
    groupOperations
        .group(groupCache.get(AccountGroup.nameKey("Service Users")).get().getGroupUUID())
        .forUpdate()
        .addMember(serviceUser.id())
        .update();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression(testPathExpressions.matchFileType("md"))
                .addCodeOwnerEmail(serviceUser.email())
                .addCodeOwnerEmail(changeOwner.email())
                .addCodeOwnerEmail(admin.email())
                .addAnnotation(admin.email(), CodeOwnerAnnotations.NEVER_SUGGEST_ANNOTATION)
                .addCodeOwnerEmail(user.email())
                .addAnnotation(user.email(), CodeOwnerAnnotations.NEVER_SUGGEST_ANNOTATION)
                .build())
        .create();

    // Expectation: The service user and the change owner are filtered out. admin and user get
    // suggested despite of the NEVER_SUGGEST annotation since ignoring them would make the result
    // empty. This is a special case in which the NEVER_SUGGEST annotation is ignored.
    CodeOwnersInfo codeOwnersInfo = queryCodeOwners("foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id());
  }

  @Test
  public void perFileCodeOwnersWithNeverSuggestAnnotation_annotationSetForAllUsersWildcard()
      throws Exception {
    skipTestIfAnnotationsNotSupportedByCodeOwnersBackend();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression(testPathExpressions.matchFileType("md"))
                .addCodeOwnerEmail(CodeOwnerResolver.ALL_USERS_WILDCARD)
                .addAnnotation(
                    CodeOwnerResolver.ALL_USERS_WILDCARD,
                    CodeOwnerAnnotations.NEVER_SUGGEST_ANNOTATION)
                .addCodeOwnerEmail(admin.email())
                .addCodeOwnerEmail(user.email())
                .build())
        .create();

    // Expectation: Since all code owners are annotated with NEVER_SUGGEST (via the annotation on
    // the all users wildcard) the result would be empty. This is a special case in which the
    // NEVER_SUGGEST annotation is ignored, hence we expect admin and user to be suggested.
    CodeOwnersInfo codeOwnersInfo = queryCodeOwners("foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id());
  }

  @Test
  public void neverSuggestTakesEffectEvenIfCodeOwnerIsAlsoSpecifiedWithoutThisAnnotation()
      throws Exception {
    skipTestIfAnnotationsNotSupportedByCodeOwnersBackend();

    TestAccount user2 = accountCreator.user2();

    // Code owner config with admin as code owner, but without annotation.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    // Code owner config that specifies admin multiple times as code owner, but only once with the
    // NEVER_SUGGEST annotation.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addCodeOwnerEmail(admin.email())
                .addAnnotation(admin.email(), CodeOwnerAnnotations.NEVER_SUGGEST_ANNOTATION)
                .addCodeOwnerEmail(user.email())
                .addCodeOwnerEmail(user2.email())
                .build())
        // Another code owner set with admin as folder code owner, but without annotation.
        .addCodeOwnerSet(CodeOwnerSet.builder().addCodeOwnerEmail(admin.email()).build())
        // A per-file code owner with admin as file code owner, but without annotation.
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression(testPathExpressions.matchFileType("md"))
                .addCodeOwnerEmail(admin.email())
                .build())
        .create();

    // Another code owner config with admin as code owner, but without annotation.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(admin.email())
        .create();

    // Expectation: admin is filtered out because at once place it is annotated with NEVER_SUGGEST.
    CodeOwnersInfo codeOwnersInfo = queryCodeOwners("foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), user2.id());
  }

  @Test
  public void neverSuggestOnNonMatchingPerFileRuleDoesntHaveAnyEffect() throws Exception {
    skipTestIfAnnotationsNotSupportedByCodeOwnersBackend();

    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addCodeOwnerEmail(admin.email())
                .addCodeOwnerEmail(user.email())
                .addCodeOwnerEmail(user2.email())
                .build())
        // Non-matching per-file code owner with NEVER_SUGGEST annotation.
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addPathExpression(testPathExpressions.matchFileType("txt"))
                .addCodeOwnerEmail(admin.email())
                .addAnnotation(admin.email(), CodeOwnerAnnotations.NEVER_SUGGEST_ANNOTATION)
                .build())
        .create();

    // Expectation: admin is suggested since the NEVER_SUGGEST annotation is set on the per-file
    // rule which doesn't match.
    CodeOwnersInfo codeOwnersInfo = queryCodeOwners("foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id());
  }

  @Test
  public void codeOwnersThatAreReviewersAreReturnedFirst() throws Exception {
    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(user2.email())
        .create();

    // None of the code owners is a reviewer, hence the sorting is done only by distance.
    CodeOwnersInfo codeOwnersInfo = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user2.id(), user.id(), admin.id())
        .inOrder();

    // Add admin as reviewer, now admin should be returned first.
    gApi.changes().id(changeId).addReviewer(admin.email());
    codeOwnersInfo = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user2.id(), user.id())
        .inOrder();

    // Add user as reviewer, now user and admin should be returned first (user before admin, because
    // user has a better distance score).
    gApi.changes().id(changeId).addReviewer(user.email());
    codeOwnersInfo = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id(), admin.id(), user2.id())
        .inOrder();

    // If all code owners are reviewers, only the distance score matters for the sorting.
    gApi.changes().id(changeId).addReviewer(user2.email());
    codeOwnersInfo = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user2.id(), user.id(), admin.id())
        .inOrder();
  }

  @Test
  public void codeOwnersSortedByDistance_fileOwnedByAllUsers() throws Exception {
    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(CodeOwnerResolver.ALL_USERS_WILDCARD)
        .addCodeOwnerEmail(user.email())
        .create();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(user2.email())
        .create();

    // None of the code owners is a reviewer, hence the sorting is done only by distance.
    CodeOwnersInfo codeOwnersInfo = queryCodeOwners("/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user2.id(), user.id(), admin.id())
        .inOrder();
    assertThat(codeOwnersInfo).hasOwnedByAllUsersThat().isTrue();
  }

  @Test
  public void filteredOutCodeOwnersAreMentionedInDebugLogs() throws Exception {
    skipTestIfAnnotationsNotSupportedByCodeOwnersBackend();

    // Create a service user.
    TestAccount serviceUser =
        accountCreator.create("serviceUser", "service.user@example.com", "Service User", null);
    groupOperations
        .group(groupCache.get(AccountGroup.nameKey("Service Users")).get().getGroupUUID())
        .forUpdate()
        .addMember(serviceUser.id())
        .update();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerSet(
            CodeOwnerSet.builder()
                .addCodeOwnerEmail(changeOwner.email())
                .addCodeOwnerEmail(serviceUser.email())
                .addCodeOwnerEmail(admin.email())
                .addAnnotation(admin.email(), CodeOwnerAnnotations.NEVER_SUGGEST_ANNOTATION)
                .addCodeOwnerEmail(user.email())
                .build())
        .create();

    String path = "/foo/bar/baz.md";
    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(getCodeOwnersApi().query().withDebug(/* debug= */ true), path);
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(user.id());
    assertThat(codeOwnersInfo)
        .hasDebugLogsThatContainAllOf(
            String.format(
                "filtering out %s because this code owner is the change owner",
                CodeOwner.create(changeOwner.id())),
            String.format(
                "filtering out %s because this code owner is a service user",
                CodeOwner.create(serviceUser.id())),
            String.format(
                "filtering out %s because this code owner is annotated with %s",
                CodeOwner.create(admin.id()), CodeOwnerAnnotations.NEVER_SUGGEST_ANNOTATION.key()));
  }
}
