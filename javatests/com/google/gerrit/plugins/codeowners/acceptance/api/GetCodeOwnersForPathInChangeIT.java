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
import static java.util.stream.Collectors.toMap;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.JgitPath;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwners;
import java.util.List;
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
  private String changeId;

  @Before
  public void createTestChange() throws Exception {
    // Create a change that contains files for all paths that are used in the tests. This is
    // necessary since CodeOwnersInChangeCollection rejects requests for paths that are not present
    // in the change.
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
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
        .addCodeOwnerEmail(admin.email())
        .create();

    String path = "/foo/bar/baz.txt";
    String changeId = createChangeWithFileDeletion(path).getChangeId();

    List<CodeOwnerInfo> codeOwnerInfos =
        codeOwnersApiFactory.change(changeId, "current").query().get(path);
    assertThat(codeOwnerInfos).comparingElementsUsing(hasAccountId()).containsExactly(admin.id());
  }
}
