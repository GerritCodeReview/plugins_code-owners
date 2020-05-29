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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigInfoSubject.assertThat;

import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.restapi.CodeOwnerConfigInfo;
import java.io.IOException;
import org.junit.Test;

/**
 * Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerConfigForPathInBranch} REST endpoint.
 */
// TODO(ekempin): Use the Java API for this test when it is available (instead of the REST API).
public class GetCodeOwnerConfigForPathInBranchIT extends AbstractCodeOwnersTest {
  @Test
  public void getNonExistingCodeOwnerConfig() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    getCodeOwnerConfigViaRest(codeOwnerConfigKey).assertNoContent();
  }

  @Test
  public void getCodeOwnerConfigForAbsolutePath() throws Exception {
    testGetCodeOwnerConfig(true);
  }

  @Test
  public void getCodeOwnerConfigForNonAbsolutePath() throws Exception {
    testGetCodeOwnerConfig(false);
  }

  private void testGetCodeOwnerConfig(boolean useAbsolutePath) throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .folderPath(useAbsolutePath ? "/foo/bar/" : "foo/bar/")
            .addCodeOwnerEmail(admin.email())
            .create();
    RestResponse r = getCodeOwnerConfigViaRest(codeOwnerConfigKey);
    r.assertOK();
    CodeOwnerConfigInfo codeOwnerConfigInfo =
        newGson().fromJson(r.getReader(), CodeOwnerConfigInfo.class);
    assertThat(codeOwnerConfigInfo)
        .correspondsTo(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).get());
  }

  @Test
  public void getCodeOwnerConfigForInvalidPath() throws Exception {
    RestResponse r =
        adminRestSession.get(
            String.format(
                "/projects/%s/branches/%s/code_owners.config/%s",
                IdString.fromDecoded(project.get()),
                IdString.fromDecoded("master"),
                IdString.fromDecoded("\0")));
    r.assertBadRequest();
    assertThat(r.getEntityContent()).contains("Nul character not allowed");
  }

  private RestResponse getCodeOwnerConfigViaRest(CodeOwnerConfig.Key codeOwnerConfigKey)
      throws IOException {
    return adminRestSession.get(
        String.format(
            "/projects/%s/branches/%s/code_owners.config/%s",
            IdString.fromDecoded(codeOwnerConfigKey.project().get()),
            IdString.fromDecoded(codeOwnerConfigKey.shortBranchName()),
            IdString.fromDecoded(codeOwnerConfigKey.folderPath().toString())));
  }
}
