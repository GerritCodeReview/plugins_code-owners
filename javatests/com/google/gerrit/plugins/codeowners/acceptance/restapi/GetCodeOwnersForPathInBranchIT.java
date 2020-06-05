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

package com.google.gerrit.plugins.codeowners.acceptance.restapi;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoIterableSubject.assertThat;

import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.restapi.CodeOwnerInfo;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import org.junit.Test;

/**
 * Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnersForPathInBranch} REST endpoint.
 */
public class GetCodeOwnersForPathInBranchIT extends AbstractCodeOwnersIT {
  @Test
  public void getCodeOwnersWhenNoCodeOwnerConfigsExist() throws Exception {
    assertThat(getCodeOwnersViaRest(project, "master", "/foo/bar/baz.md")).isEmpty();
  }

  @Test
  public void getCodeOwnersUnrelatedCodeOwnerConfigHasNoEffect() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/abc/def/")
        .addCodeOwnerEmail(admin.email())
        .addCodeOwnerEmail(user.email())
        .create();
    assertThat(getCodeOwnersViaRest(project, "master", "/foo/bar/baz.md")).isEmpty();
  }

  @Test
  public void getCodeOwnersForAbsolutePath() throws Exception {
    testGetCodeOwners(true);
  }

  @Test
  public void getCodeOwnersForNonAbsolutePath() throws Exception {
    testGetCodeOwners(false);
  }

  private void testGetCodeOwners(boolean useAbsolutePath) throws Exception {
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

    assertThat(
            getCodeOwnersViaRest(
                project, "master", useAbsolutePath ? "/foo/bar/baz.md" : "foo/bar/baz.md"))
        .hasAccountIdsThat()
        .containsExactly(admin.id(), user.id(), user2.id())
        .inOrder();
  }

  @Test
  public void getCodeOwnersForInvalidPath() throws Exception {
    RestResponse r =
        adminRestSession.get(
            String.format(
                "/projects/%s/branches/%s/code_owners/%s",
                IdString.fromDecoded(project.get()),
                IdString.fromDecoded("master"),
                IdString.fromDecoded("\0")));
    r.assertBadRequest();
    assertThat(r.getEntityContent()).contains("Nul character not allowed");
  }

  private List<CodeOwnerInfo> getCodeOwnersViaRest(
      Project.NameKey project, String branchName, String path) throws Exception {
    RestResponse r =
        adminRestSession.get(
            String.format(
                "/projects/%s/branches/%s/code_owners/%s",
                IdString.fromDecoded(project.get()),
                IdString.fromDecoded(branchName),
                IdString.fromDecoded(path)));
    r.assertOK();
    return newGson().fromJson(r.getReader(), new TypeToken<List<CodeOwnerInfo>>() {}.getType());
  }
}
