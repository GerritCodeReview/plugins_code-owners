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
 * <p>The tests in this class do not depend on the used code owners backend, hence we do not need to
 * extend {@link AbstractCodeOwnersIT}.
 */
public class CodeOwnersRestApiBindingsIT extends AbstractCodeOwnersTest {
  private static final ImmutableList<RestCall> BRANCH_CODE_OWNER_CONFIGS_ENDPOINTS =
      ImmutableList.of(RestCall.get("/projects/%s/branches/%s/code-owners~code_owners.config/%s"));

  private static final ImmutableList<RestCall> BRANCH_CODE_OWNERS_ENDPOINTS =
      ImmutableList.of(RestCall.get("/projects/%s/branches/%s/code-owners~code_owners/%s"));

  @Test
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

  private static String urlEncode(String decoded) {
    return IdString.fromDecoded(decoded).encoded();
  }
}
