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

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.common.ChangeType;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerStatusInfo;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusInfoSubject;
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

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    String path = "foo/bar.baz";
    PushOneCommit.Result r = createChange("Change Adding A File", path, "file content");
    String changeId = r.getChangeId();

    // Add a reviewer that is a code owner.
    gApi.changes().id(changeId).addReviewer(user.email());

    // Add a Code-Review+1 (= code owner approval) from a user that is not a code owner.
    requestScopeOperations.setApiUser(user2.id());
    recommend(changeId);

    CodeOwnerStatusInfo codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus();
    assertThat(codeOwnerStatus)
        .hasPatchSetNumberThat()
        .isEqualTo(r.getChange().currentPatchSet().id().get());
    FileCodeOwnerStatusInfoSubject fileCodeOwnerStatusInfoSubject =
        assertThat(codeOwnerStatus).hasFileCodeOwnerStatusesThat().onlyElement();
    fileCodeOwnerStatusInfoSubject.hasChangeTypeThat().isEqualTo(ChangeType.ADDED);
    fileCodeOwnerStatusInfoSubject.hasNewPathStatusThat().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusInfoSubject
        .hasNewPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.PENDING);
  }

  @Test
  public void getStatusForRenamedFile() throws Exception {
    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar")
        .addCodeOwnerEmail(user.email())
        .create();
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/baz")
        .addCodeOwnerEmail(user2.email())
        .create();

    String oldPath = "foo/bar/abc.txt";
    String newPath = "foo/baz/abc.txt";
    String changeId = createChangeWithFileRename(oldPath, newPath);

    CodeOwnerStatusInfo codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus();
    assertThat(codeOwnerStatus).hasFileCodeOwnerStatusesThat().hasSize(2);
    FileCodeOwnerStatusInfoSubject fileCodeOwnerStatusInfoSubject1 =
        assertThat(codeOwnerStatus).hasFileCodeOwnerStatusesThat().element(0);
    fileCodeOwnerStatusInfoSubject1.hasChangeTypeThat().isEqualTo(ChangeType.DELETED);
    fileCodeOwnerStatusInfoSubject1.hasOldPathStatusThat().value().hasPathThat().isEqualTo(oldPath);
    fileCodeOwnerStatusInfoSubject1
        .hasOldPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
    FileCodeOwnerStatusInfoSubject fileCodeOwnerStatusInfoSubject2 =
        assertThat(codeOwnerStatus).hasFileCodeOwnerStatusesThat().element(1);
    fileCodeOwnerStatusInfoSubject2.hasChangeTypeThat().isEqualTo(ChangeType.ADDED);
    fileCodeOwnerStatusInfoSubject2.hasNewPathStatusThat().value().hasPathThat().isEqualTo(newPath);
    fileCodeOwnerStatusInfoSubject2
        .hasNewPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);

    // Add a reviewer that is a code owner of the old path.
    gApi.changes().id(changeId).addReviewer(user.email());

    codeOwnerStatus = changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus();
    assertThat(codeOwnerStatus).hasFileCodeOwnerStatusesThat().hasSize(2);
    fileCodeOwnerStatusInfoSubject1 =
        assertThat(codeOwnerStatus).hasFileCodeOwnerStatusesThat().element(0);
    fileCodeOwnerStatusInfoSubject1.hasChangeTypeThat().isEqualTo(ChangeType.DELETED);
    fileCodeOwnerStatusInfoSubject1.hasOldPathStatusThat().value().hasPathThat().isEqualTo(oldPath);
    fileCodeOwnerStatusInfoSubject1
        .hasOldPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.PENDING);
    fileCodeOwnerStatusInfoSubject2 =
        assertThat(codeOwnerStatus).hasFileCodeOwnerStatusesThat().element(1);
    fileCodeOwnerStatusInfoSubject2.hasChangeTypeThat().isEqualTo(ChangeType.ADDED);
    fileCodeOwnerStatusInfoSubject2.hasNewPathStatusThat().value().hasPathThat().isEqualTo(newPath);
    fileCodeOwnerStatusInfoSubject2
        .hasNewPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);

    // Add a reviewer that is a code owner of the new path.
    gApi.changes().id(changeId).addReviewer(user2.email());

    codeOwnerStatus = changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus();
    assertThat(codeOwnerStatus).hasFileCodeOwnerStatusesThat().hasSize(2);
    fileCodeOwnerStatusInfoSubject1 =
        assertThat(codeOwnerStatus).hasFileCodeOwnerStatusesThat().element(0);
    fileCodeOwnerStatusInfoSubject1.hasChangeTypeThat().isEqualTo(ChangeType.DELETED);
    fileCodeOwnerStatusInfoSubject1.hasOldPathStatusThat().value().hasPathThat().isEqualTo(oldPath);
    fileCodeOwnerStatusInfoSubject1
        .hasOldPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.PENDING);
    fileCodeOwnerStatusInfoSubject2 =
        assertThat(codeOwnerStatus).hasFileCodeOwnerStatusesThat().element(1);
    fileCodeOwnerStatusInfoSubject2.hasChangeTypeThat().isEqualTo(ChangeType.ADDED);
    fileCodeOwnerStatusInfoSubject2.hasNewPathStatusThat().value().hasPathThat().isEqualTo(newPath);
    fileCodeOwnerStatusInfoSubject2
        .hasNewPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.PENDING);
  }

  @Test
  public void getCodeOwnerStatusIfCodeOwnersFunctionalityIsDisabled() throws Exception {
    disableCodeOwnersForProject(project);
    String path = "foo/bar.baz";
    String changeId = createChange("Change Adding A File", path, "file content").getChangeId();
    CodeOwnerStatusInfo codeOwnerStatus =
        changeCodeOwnersApiFactory.change(changeId).getCodeOwnerStatus();
    FileCodeOwnerStatusInfoSubject fileCodeOwnerStatusInfoSubject =
        assertThat(codeOwnerStatus).hasFileCodeOwnerStatusesThat().onlyElement();
    fileCodeOwnerStatusInfoSubject.hasChangeTypeThat().isEqualTo(ChangeType.ADDED);
    fileCodeOwnerStatusInfoSubject.hasNewPathStatusThat().value().hasPathThat().isEqualTo(path);
    fileCodeOwnerStatusInfoSubject
        .hasNewPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.INSUFFICIENT_REVIEWERS);
  }
}
