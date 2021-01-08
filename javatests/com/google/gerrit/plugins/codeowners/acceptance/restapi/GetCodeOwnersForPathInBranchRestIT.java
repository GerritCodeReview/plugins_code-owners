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

import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.restapi.IdString;
import org.junit.Test;

/**
 * Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnersForPathInBranch} REST endpoint. that
 * require using via REST.
 *
 * <p>Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnersForPathInBranch} REST endpoint that can
 * use the Java API are implemented in {@link
 * com.google.gerrit.plugins.codeowners.acceptance.api.GetCodeOwnersForPathInBranchIT}.
 */
public class GetCodeOwnersForPathInBranchRestIT extends AbstractGetCodeOwnersForPathRestIT {
  @Override
  protected String getUrl(String path) {
    return String.format(
        "/projects/%s/branches/%s/code_owners/%s",
        IdString.fromDecoded(project.get()),
        IdString.fromDecoded("master"),
        IdString.fromDecoded(path));
  }

  @Test
  public void cannotListCodeOwners() throws Exception {
    RestResponse r =
        adminRestSession.get(
            String.format(
                "/projects/%s/branches/%s/code_owners/",
                IdString.fromDecoded(project.get()), IdString.fromDecoded("master")));
    r.assertNotFound();
  }
}
