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

import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import org.junit.Test;

/**
 * Acceptance test for the {@link com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerStatus}
 * REST endpoint. that require using via REST.
 *
 * <p>Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerStatus} REST endpoint that can use the
 * Java API are implemented in {@link
 * com.google.gerrit.plugins.codeowners.acceptance.api.GetCodeOwnerStatusIT}.
 *
 * <p>The tests in this class do not depend on the used code owner backend, hence we do not need to
 * extend {@link AbstractCodeOwnersIT}.
 */
public class GetCodeOwnerStatusRestIT extends AbstractCodeOwnersTest {
  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = "non-existing-backend")
  public void cannotGetStatusIfPluginConfigurationIsInvalid() throws Exception {
    String changeId = createChange().getChangeId();

    // Add a reviewer to avoid short-cut.
    gApi.changes().id(changeId).addReviewer(user.email());

    RestResponse r =
        adminRestSession.get(
            String.format("/changes/%s/code_owners.status", IdString.fromDecoded(changeId)));
    r.assertConflict();
    assertThat(r.getEntityContent())
        .contains(
            "Invalid configuration of the code-owners plugin. Code owner backend"
                + " 'non-existing-backend' that is configured in gerrit.config (parameter"
                + " plugin.code-owners.backend) not found.");
  }
}
