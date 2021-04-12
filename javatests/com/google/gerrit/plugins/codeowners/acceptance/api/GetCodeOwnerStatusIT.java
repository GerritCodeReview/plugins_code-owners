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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerStatusInfoSubject.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusInfoSubject.isFileCodeOwnerStatus;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerStatusInfo;
import com.google.gerrit.plugins.codeowners.backend.FileCodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.inject.Inject;
import org.junit.Test;

/**
 * Acceptance test for the {@link com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerStatus}
 * REST endpoint.
 *
 * <p>Further tests for the {@link com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerStatus}
 * REST endpoint that require using the REST API are implemented in {@link
 * com.google.gerrit.plugins.codeowners.acceptance.restapi.GetCodeOwnerStatusRestIT}.
 */
public class GetCodeOwnerStatusIT extends AbstractCodeOwnersIT {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void getStatus() throws Exception {
    TestAccount user2 = accountCreator.user2();

    setAsCodeOwners("/foo/", user);

    String path = "foo/bar.baz";
    PushOneCommit.Result r = createChange("Change Adding A File", path, "file content");
    String changeId = r.getChangeId();

    // Add a reviewer that is a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    CodeOwnerStatusInfo codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().get();
    assertThat(codeOwnerStatus)
        .hasPatchSetNumberThat()
        .isEqualTo(r.getChange().currentPatchSet().id().get());

    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .comparingElementsUsing(isFileCodeOwnerStatus())
        .containsExactly(FileCodeOwnerStatus.addition(path, CodeOwnerStatus.PENDING));
    assertThat(codeOwnerStatus).hasMoreThat().isNull();
  }

  @Test
  public void getStatusWithStart() throws Exception {
    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    String path1 = "/foo/bar/baz.md";
    String path2 = "/foo/baz/bar.md";
    String path3 = "/bar/foo.md";
    String path4 = "/bar/baz.md";

    PushOneCommit.Result r =
        createChange(
            "Change Adding A File",
            ImmutableMap.of(
                JgitPath.of(path1).get(),
                "file content",
                JgitPath.of(path2).get(),
                "file content",
                JgitPath.of(path3).get(),
                "file content",
                JgitPath.of(path4).get(),
                "file content"));
    String changeId = r.getChangeId();

    // Add a reviewer that is a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    CodeOwnerStatusInfo codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().withStart(0).get();
    assertThat(codeOwnerStatus)
        .hasPatchSetNumberThat()
        .isEqualTo(r.getChange().currentPatchSet().id().get());
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .comparingElementsUsing(isFileCodeOwnerStatus())
        .containsExactly(
            FileCodeOwnerStatus.addition(path4, CodeOwnerStatus.INSUFFICIENT_REVIEWERS),
            FileCodeOwnerStatus.addition(path3, CodeOwnerStatus.INSUFFICIENT_REVIEWERS),
            FileCodeOwnerStatus.addition(path1, CodeOwnerStatus.PENDING),
            FileCodeOwnerStatus.addition(path2, CodeOwnerStatus.PENDING))
        .inOrder();

    codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().withStart(1).get();
    assertThat(codeOwnerStatus)
        .hasPatchSetNumberThat()
        .isEqualTo(r.getChange().currentPatchSet().id().get());
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .comparingElementsUsing(isFileCodeOwnerStatus())
        .containsExactly(
            FileCodeOwnerStatus.addition(path3, CodeOwnerStatus.INSUFFICIENT_REVIEWERS),
            FileCodeOwnerStatus.addition(path1, CodeOwnerStatus.PENDING),
            FileCodeOwnerStatus.addition(path2, CodeOwnerStatus.PENDING))
        .inOrder();

    codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().withStart(2).get();
    assertThat(codeOwnerStatus)
        .hasPatchSetNumberThat()
        .isEqualTo(r.getChange().currentPatchSet().id().get());
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .comparingElementsUsing(isFileCodeOwnerStatus())
        .containsExactly(
            FileCodeOwnerStatus.addition(path1, CodeOwnerStatus.PENDING),
            FileCodeOwnerStatus.addition(path2, CodeOwnerStatus.PENDING))
        .inOrder();

    codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().withStart(3).get();
    assertThat(codeOwnerStatus)
        .hasPatchSetNumberThat()
        .isEqualTo(r.getChange().currentPatchSet().id().get());
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .comparingElementsUsing(isFileCodeOwnerStatus())
        .containsExactly(FileCodeOwnerStatus.addition(path2, CodeOwnerStatus.PENDING));

    codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().withStart(4).get();
    assertThat(codeOwnerStatus)
        .hasPatchSetNumberThat()
        .isEqualTo(r.getChange().currentPatchSet().id().get());
    assertThat(codeOwnerStatus).hasFileCodeOwnerStatusesThat().isEmpty();
  }

  @Test
  public void getStatusWithLimit() throws Exception {
    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    String path1 = "/foo/bar/baz.md";
    String path2 = "/foo/baz/bar.md";
    String path3 = "/bar/foo.md";
    String path4 = "/bar/baz.md";

    PushOneCommit.Result r =
        createChange(
            "Change Adding A File",
            ImmutableMap.of(
                JgitPath.of(path1).get(),
                "file content",
                JgitPath.of(path2).get(),
                "file content",
                JgitPath.of(path3).get(),
                "file content",
                JgitPath.of(path4).get(),
                "file content"));
    String changeId = r.getChangeId();

    // Add a reviewer that is a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    CodeOwnerStatusInfo codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().withLimit(1).get();
    assertThat(codeOwnerStatus)
        .hasPatchSetNumberThat()
        .isEqualTo(r.getChange().currentPatchSet().id().get());
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .comparingElementsUsing(isFileCodeOwnerStatus())
        .containsExactly(
            FileCodeOwnerStatus.addition(path4, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
    assertThat(codeOwnerStatus).hasMoreThat().isTrue();

    codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().withLimit(2).get();
    assertThat(codeOwnerStatus)
        .hasPatchSetNumberThat()
        .isEqualTo(r.getChange().currentPatchSet().id().get());
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .comparingElementsUsing(isFileCodeOwnerStatus())
        .containsExactly(
            FileCodeOwnerStatus.addition(path4, CodeOwnerStatus.INSUFFICIENT_REVIEWERS),
            FileCodeOwnerStatus.addition(path3, CodeOwnerStatus.INSUFFICIENT_REVIEWERS))
        .inOrder();
    assertThat(codeOwnerStatus).hasMoreThat().isTrue();

    codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().withLimit(3).get();
    assertThat(codeOwnerStatus)
        .hasPatchSetNumberThat()
        .isEqualTo(r.getChange().currentPatchSet().id().get());
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .comparingElementsUsing(isFileCodeOwnerStatus())
        .containsExactly(
            FileCodeOwnerStatus.addition(path4, CodeOwnerStatus.INSUFFICIENT_REVIEWERS),
            FileCodeOwnerStatus.addition(path3, CodeOwnerStatus.INSUFFICIENT_REVIEWERS),
            FileCodeOwnerStatus.addition(path1, CodeOwnerStatus.PENDING))
        .inOrder();
    assertThat(codeOwnerStatus).hasMoreThat().isTrue();

    codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().withLimit(4).get();
    assertThat(codeOwnerStatus)
        .hasPatchSetNumberThat()
        .isEqualTo(r.getChange().currentPatchSet().id().get());
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .comparingElementsUsing(isFileCodeOwnerStatus())
        .containsExactly(
            FileCodeOwnerStatus.addition(path4, CodeOwnerStatus.INSUFFICIENT_REVIEWERS),
            FileCodeOwnerStatus.addition(path3, CodeOwnerStatus.INSUFFICIENT_REVIEWERS),
            FileCodeOwnerStatus.addition(path1, CodeOwnerStatus.PENDING),
            FileCodeOwnerStatus.addition(path2, CodeOwnerStatus.PENDING))
        .inOrder();
    assertThat(codeOwnerStatus).hasMoreThat().isNull();
  }

  @Test
  public void getStatusWithStartAndLimit() throws Exception {
    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    String path1 = "/foo/bar/baz.md";
    String path2 = "/foo/baz/bar.md";
    String path3 = "/bar/foo.md";
    String path4 = "/bar/baz.md";

    PushOneCommit.Result r =
        createChange(
            "Change Adding A File",
            ImmutableMap.of(
                JgitPath.of(path1).get(),
                "file content",
                JgitPath.of(path2).get(),
                "file content",
                JgitPath.of(path3).get(),
                "file content",
                JgitPath.of(path4).get(),
                "file content"));
    String changeId = r.getChangeId();

    // Add a reviewer that is a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    CodeOwnerStatusInfo codeOwnerStatus =
        changeCodeOwnersApiFactory
            .change(changeId)
            .getCodeOwnerStatus()
            .withStart(1)
            .withLimit(2)
            .get();
    assertThat(codeOwnerStatus)
        .hasPatchSetNumberThat()
        .isEqualTo(r.getChange().currentPatchSet().id().get());
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .comparingElementsUsing(isFileCodeOwnerStatus())
        .containsExactly(
            FileCodeOwnerStatus.addition(path3, CodeOwnerStatus.INSUFFICIENT_REVIEWERS),
            FileCodeOwnerStatus.addition(path1, CodeOwnerStatus.PENDING))
        .inOrder();
    assertThat(codeOwnerStatus).hasMoreThat().isTrue();
  }

  @Test
  public void startCannotBeNegative() throws Exception {
    String changeId = createChange().getChangeId();
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                changeCodeOwnersApiFactory
                    .change(changeId)
                    .getCodeOwnerStatus()
                    .withStart(-1)
                    .get());
    assertThat(exception).hasMessageThat().isEqualTo("start cannot be negative");
  }

  @Test
  public void limitCannotBeNegative() throws Exception {
    String changeId = createChange().getChangeId();
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                changeCodeOwnersApiFactory
                    .change(changeId)
                    .getCodeOwnerStatus()
                    .withLimit(-1)
                    .get());
    assertThat(exception).hasMessageThat().isEqualTo("limit cannot be negative");
  }

  @Test
  public void getStatusWithoutLimit() throws Exception {
    String changeId = createChange().getChangeId();
    CodeOwnerStatusInfo codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().withLimit(0).get();
    assertThat(codeOwnerStatus).hasFileCodeOwnerStatusesThat().isNotEmpty();
    assertThat(codeOwnerStatus).hasMoreThat().isNull();
  }

  @Test
  public void getStatusForRenamedFile() throws Exception {
    TestAccount user2 = accountCreator.user2();

    setAsCodeOwners("/foo/bar/", user);
    setAsCodeOwners("/foo/baz/", user2);

    String oldPath = "foo/bar/abc.txt";
    String newPath = "foo/baz/abc.txt";
    String changeId = createChangeWithFileRename(oldPath, newPath);

    CodeOwnerStatusInfo codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().get();
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .comparingElementsUsing(isFileCodeOwnerStatus())
        .containsExactly(
            FileCodeOwnerStatus.rename(
                oldPath,
                CodeOwnerStatus.INSUFFICIENT_REVIEWERS,
                newPath,
                CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a reviewer that is a code owner of the old path.
    gApi.changes().id(changeId).addReviewer(user.email());

    codeOwnerStatus = changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().get();
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .comparingElementsUsing(isFileCodeOwnerStatus())
        .containsExactly(
            FileCodeOwnerStatus.rename(
                oldPath, CodeOwnerStatus.PENDING, newPath, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a reviewer that is a code owner of the new path.
    gApi.changes().id(changeId).addReviewer(user2.email());

    codeOwnerStatus = changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().get();
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .comparingElementsUsing(isFileCodeOwnerStatus())
        .containsExactly(
            FileCodeOwnerStatus.rename(
                oldPath, CodeOwnerStatus.PENDING, newPath, CodeOwnerStatus.PENDING));
  }

  @Test
  public void getCodeOwnerStatusIfCodeOwnersFunctionalityIsDisabled() throws Exception {
    disableCodeOwnersForProject(project);
    String path = "foo/bar.baz";
    String changeId = createChange("Change Adding A File", path, "file content").getChangeId();
    CodeOwnerStatusInfo codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().get();
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .comparingElementsUsing(isFileCodeOwnerStatus())
        .containsExactly(
            FileCodeOwnerStatus.addition(path, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));
  }
}
