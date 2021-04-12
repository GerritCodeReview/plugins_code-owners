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

import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerStatusInfoSubject.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusInfoSubject.isFileCodeOwnerStatus;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerStatusInfo;
import com.google.gerrit.plugins.codeowners.backend.FileCodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;
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
            FileCodeOwnerStatus.deletion(oldPath, CodeOwnerStatus.INSUFFICIENT_REVIEWERS),
            FileCodeOwnerStatus.addition(newPath, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a reviewer that is a code owner of the old path.
    gApi.changes().id(changeId).addReviewer(user.email());

    codeOwnerStatus = changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().get();
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .comparingElementsUsing(isFileCodeOwnerStatus())
        .containsExactly(
            FileCodeOwnerStatus.deletion(oldPath, CodeOwnerStatus.PENDING),
            FileCodeOwnerStatus.addition(newPath, CodeOwnerStatus.INSUFFICIENT_REVIEWERS));

    // Add a reviewer that is a code owner of the new path.
    gApi.changes().id(changeId).addReviewer(user2.email());

    codeOwnerStatus = changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus().get();
    assertThat(codeOwnerStatus)
        .hasFileCodeOwnerStatusesThat()
        .comparingElementsUsing(isFileCodeOwnerStatus())
        .containsExactly(
            FileCodeOwnerStatus.deletion(oldPath, CodeOwnerStatus.PENDING),
            FileCodeOwnerStatus.addition(newPath, CodeOwnerStatus.PENDING));
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
