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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.rest.util.RestApiCallHelper;
import com.google.gerrit.acceptance.rest.util.RestCall;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import org.junit.Test;

/**
 * Test that verifies that the code owners REST endpoints are correctly bound in Guice.
 *
 * <p>To verify this, this test sends a REST request to each REST endpoint and checks that a
 * non-'404 Not Found' response is returned.
 *
 * <p>This test is necessary since all other integration tests for code owners use the Java
 * extension API and hence would not detect issues with the Guice bindings of the code owners REST
 * endpoints.
 *
 * <p>The tests in this class do not depend on the used code owner backend, hence we do not need to
 * extend {@link AbstractCodeOwnersIT}.
 */
public class CodeOwnersRestApiBindingsIT extends AbstractCodeOwnersTest {
  private static final ImmutableList<RestCall> CHANGE_ENDPOINTS =
      ImmutableList.of(RestCall.get("/changes/%s/code-owners~code_owners.status"));

  private static final ImmutableList<RestCall> REVISION_ENDPOINTS =
      ImmutableList.of(
          RestCall.post("/changes/%s/revisions/current/code-owners~code_owners.check_config"),
          RestCall.get("/changes/%s/revisions/current/code-owners~owned_paths"));

  private static final ImmutableList<RestCall> PROJECT_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/projects/%s/code-owners~code_owners.project_config"),
          RestCall.post("/projects/%s/code_owners.check_config"));

  private static final ImmutableList<RestCall> BRANCH_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/projects/%s/branches/%s/code-owners~code_owners.config_files"),
          RestCall.get("/projects/%s/branches/%s/code-owners~code_owners.branch_config"),
          RestCall.post("/projects/%s/branches/%s/code-owners~code_owners.rename"),
          RestCall.get("/projects/%s/branches/%s/code-owners~code_owners.check"));

  private static final ImmutableList<RestCall> BRANCH_CODE_OWNER_CONFIGS_ENDPOINTS =
      ImmutableList.of(RestCall.get("/projects/%s/branches/%s/code-owners~code_owners.config/%s"));

  private static final ImmutableList<RestCall> BRANCH_CODE_OWNERS_ENDPOINTS =
      ImmutableList.of(RestCall.get("/projects/%s/branches/%s/code-owners~code_owners/%s"));

  private static final ImmutableList<RestCall> REVISION_CODE_OWNERS_ENDPOINTS =
      ImmutableList.of(RestCall.get("/changes/%s/revisions/%s/code-owners~code_owners/%s"));

  @Test
  public void changeEndpoints() throws Exception {
    String changeId = createChange().getChangeId();
    RestApiCallHelper.execute(adminRestSession, CHANGE_ENDPOINTS, urlEncode(changeId));
  }

  @Test
  public void revisionEndpoints() throws Exception {
    String changeId = createChange().getChangeId();
    RestApiCallHelper.execute(adminRestSession, REVISION_ENDPOINTS, urlEncode(changeId));
  }

  @Test
  public void projectEndpoints() throws Exception {
    RestApiCallHelper.execute(adminRestSession, PROJECT_ENDPOINTS, urlEncode(project.get()));
  }

  @Test
  public void branchEndpoints() throws Exception {
    RestApiCallHelper.execute(
        adminRestSession, BRANCH_ENDPOINTS, urlEncode(project.get()), "master");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.enableExperimentalRestEndpoints", value = "true")
  public void branchCodeOwnerConfigsEndpoints() throws Exception {
    RestApiCallHelper.execute(
        adminRestSession,
        BRANCH_CODE_OWNER_CONFIGS_ENDPOINTS,
        urlEncode(project.get()),
        urlEncode("master"),
        urlEncode("foo/bar.baz"));
  }

  @Test
  public void branchCodeOwnersEndpoints() throws Exception {
    RestApiCallHelper.execute(
        adminRestSession,
        BRANCH_CODE_OWNERS_ENDPOINTS,
        urlEncode(project.get()),
        urlEncode("master"),
        urlEncode("foo/bar.baz"));
  }

  @Test
  public void revisionCodeOwnersEndpoints() throws Exception {
    String filePath = "foo/bar.baz";
    String changeId = createChange("Test Change", filePath, "file content").getChangeId();
    RestApiCallHelper.execute(
        adminRestSession,
        REVISION_CODE_OWNERS_ENDPOINTS,
        urlEncode(changeId),
        urlEncode("current"),
        urlEncode(filePath));
  }

  private static String urlEncode(String decoded) {
    return IdString.fromDecoded(decoded).encoded();
  }
}
