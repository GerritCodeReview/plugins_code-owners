// Copyright (C) 2024 The Android Open Source Project
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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.restapi.CheckCodeOwnerConfigFilesCapability;
import com.google.gerrit.server.restapi.config.ListCapabilities.CapabilityInfo;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.util.Map;
import org.junit.Test;

/**
 * Acceptance test for {@link
 * com.google.gerrit.plugins.codeowners.restapi.CheckCodeOwnerConfigFilesCapability}.
 */
public class CheckCodeOwnerFilesCapabilityRestIT extends AbstractCodeOwnersIT {
  @Inject private ProjectOperations projectOperations;

  @Test
  public void listCapabilities() throws Exception {
    RestResponse r = adminRestSession.get("/config/server/capabilities");
    r.assertOK();
    Map<String, CapabilityInfo> capabilities =
        newGson()
            .fromJson(r.getReader(), new TypeToken<Map<String, CapabilityInfo>>() {}.getType());
    CapabilityInfo capabilityInfo =
        capabilities.get("code-owners-" + CheckCodeOwnerConfigFilesCapability.ID);
    assertThat(capabilityInfo.id)
        .isEqualTo("code-owners-" + CheckCodeOwnerConfigFilesCapability.ID);
    assertThat(capabilityInfo.name).isEqualTo("Check Code Owner Config Files");
  }

  @Test
  public void getAccountCapabilities() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(
            allowCapability("code-owners-" + CheckCodeOwnerConfigFilesCapability.ID)
                .group(REGISTERED_USERS))
        .update();

    RestResponse r = adminRestSession.get("/accounts/self/capabilities");
    r.assertOK();
    Map<String, Object> capabilities =
        newGson().fromJson(r.getReader(), new TypeToken<Map<String, Object>>() {}.getType());
    assertThat(capabilities)
        .containsEntry("code-owners-" + CheckCodeOwnerConfigFilesCapability.ID, true);
  }
}
