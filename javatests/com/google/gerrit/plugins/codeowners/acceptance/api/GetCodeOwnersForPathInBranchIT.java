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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoSubject.hasAccountId;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnersInfoSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.fail;

import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.api.CodeOwners;
import com.google.gerrit.plugins.codeowners.api.CodeOwnersInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerAnnotations;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSetModification;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/**
 * Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnersForPathInBranch} REST endpoint.
 *
 * <p>Further tests for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnersForPathInBranch} REST endpoint that
 * require using the REST API are implemented in {@link
 * com.google.gerrit.plugins.codeowners.acceptance.restapi.GetCodeOwnersForPathInBranchRestIT}.
 */
public class GetCodeOwnersForPathInBranchIT extends AbstractGetCodeOwnersForPathIT {
  @Inject private ProjectOperations projectOperations;
  @Inject private GroupOperations groupOperations;

  @Override
  protected CodeOwners getCodeOwnersApi() throws RestApiException {
    return codeOwnersApiFactory.branch(project, "master");
  }

  @Test
  public void getCodeOwnersOrderNotDefinedIfCodeOwnersHaveTheSameScoring() throws Exception {
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
    // Check that the order of the code owners with the same score is different for further requests
    // at least once.
    List<Account.Id> accountIdsInRetrievedOrder1 =
        codeOwnersInfo.codeOwners.stream()
            .map(info -> Account.id(info.account._accountId))
            .collect(toList());
    boolean foundOtherOrder = false;
    for (int i = 0; i < 10; i++) {
      codeOwnersInfo = queryCodeOwners("/foo/bar.md");
      List<Account.Id> accountIdsInRetrievedOrder2 =
          codeOwnersInfo.codeOwners.stream()
              .map(info -> Account.id(info.account._accountId))
              .collect(toList());
      if (!accountIdsInRetrievedOrder1.equals(accountIdsInRetrievedOrder2)) {
        foundOtherOrder = true;
        break;
      }
    }
    if (!foundOtherOrder) {
      fail(
          String.format(
              "expected different order, but order was always %s", accountIdsInRetrievedOrder1));
    }
  }

  @Test
  public void getCodeOwnersForRevision() throws Exception {
    // Create an initial code owner config that only has 'admin' as code owner.
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail(admin.email())
            .create();
    RevCommit revision1 = projectOperations.project(project).getHead("master");

    // Update the code owner config so that also 'user' is a code owner now.
    codeOwnerConfigOperations
        .codeOwnerConfig(codeOwnerConfigKey)
        .forUpdate()
        .codeOwnerSetsModification(CodeOwnerSetModification.addToOnlySet(user.email()))
        .update();
    RevCommit revision2 = projectOperations.project(project).getHead("master");
    assertThat(revision1).isNotEqualTo(revision2);

    // For the first revision we expect that only 'admin' is returned as code owner.
    CodeOwnersInfo codeOwnersInfo =
        queryCodeOwners(
            getCodeOwnersApi().query().forRevision(revision1.name()), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id());

    // For the second revision we expect that 'admin' and 'user' are returned as code owners.
    codeOwnersInfo =
        queryCodeOwners(
            getCodeOwnersApi().query().forRevision(revision2.name()), "/foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id());
  }

  @Test
  public void cannotGetCodeOwnersForInvalidRevision() throws Exception {
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                queryCodeOwners(
                    getCodeOwnersApi().query().forRevision("INVALID"), "/foo/bar/baz.md"));
    assertThat(exception).hasMessageThat().isEqualTo("invalid revision");
  }

  @Test
  public void cannotGetCodeOwnersForUnknownRevision() throws Exception {
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                queryCodeOwners(
                    getCodeOwnersApi()
                        .query()
                        .forRevision("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"),
                    "/foo/bar/baz.md"));
    assertThat(exception).hasMessageThat().isEqualTo("unknown revision");
  }

  @Test
  public void cannotGetCodeOwnersForRevisionOfOtherBranch() throws Exception {
    RevCommit revision = projectOperations.project(project).getHead(RefNames.REFS_CONFIG);
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                queryCodeOwners(
                    getCodeOwnersApi().query().forRevision(revision.name()), "/foo/bar/baz.md"));
    assertThat(exception).hasMessageThat().isEqualTo("unknown revision");
  }

  @Test
  public void codeOwnersThatAreServiceUsersAreIncluded() throws Exception {
    TestAccount serviceUser =
        accountCreator.create("serviceUser", "service.user@example.com", "Service User", null);

    // Make 'serviceUser' a service user.
    groupOperations
        .group(groupCache.get(AccountGroup.nameKey("Service Users")).get().getGroupUUID())
        .forUpdate()
        .addMember(serviceUser.id())
        .update();

    // Create a code owner config with 2 code owners.
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .addCodeOwnerEmail(serviceUser.email())
        .create();

    // Expect that 'serviceUser' is included.
    assertThat(queryCodeOwners("/foo/bar/baz.md"))
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), serviceUser.id());
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.globalCodeOwner", value = "service.user@example.com")
  public void globalCodeOwnersThatAreServiceUsersAreIncluded() throws Exception {
    TestAccount serviceUser =
        accountCreator.create("serviceUser", "service.user@example.com", "Service User", null);
    groupOperations
        .group(groupCache.get(AccountGroup.nameKey("Service Users")).get().getGroupUUID())
        .forUpdate()
        .addMember(serviceUser.id())
        .update();
    assertThat(queryCodeOwners("/foo/bar/baz.md"))
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(serviceUser.id());
  }

  @Test
  public void codeOwnersWithNeverSuggestAnnotationAreIncluded() throws Exception {
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

    // Expectation: admin is included because GetCodeOwnersForPathInBranch ignores the NEVER_SUGGEST
    // suggestion.
    CodeOwnersInfo codeOwnersInfo = queryCodeOwners("foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id());
  }

  @Test
  public void perFileCodeOwnersWithNeverSuggestAnnotationAreIncluded() throws Exception {
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

    // Expectation: admin is included because GetCodeOwnersForPathInBranch ignores the NEVER_SUGGEST
    // suggestion.
    CodeOwnersInfo codeOwnersInfo = queryCodeOwners("foo/bar/baz.md");
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id(), user2.id());
  }
}
