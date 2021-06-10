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
import static com.google.common.truth.TruthJUnit.assume;

import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.api.RenameEmailInput;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackendId;
import com.google.gerrit.plugins.codeowners.backend.config.BackendConfig;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;

/**
 * Acceptance test for the {@link com.google.gerrit.plugins.codeowners.restapi.RenameEmail} REST
 * endpoint. that require using via REST.
 *
 * <p>Acceptance test for the {@link com.google.gerrit.plugins.codeowners.restapi.RenameEmail} REST
 * endpoint that can use the Java API are implemented in {@link
 * com.google.gerrit.plugins.codeowners.acceptance.api.RenameEmailIT}.
 */
public class RenameEmailRestIT extends AbstractCodeOwnersIT {
  @Inject private AccountOperations accountOperations;

  @Before
  public void setup() throws Exception {
    backendConfig = plugin.getSysInjector().getInstance(BackendConfig.class);
  }

  @Test
  public void cannotRenameEmailsAnonymously() throws Exception {
    RestResponse r =
        anonymousRestSession.post(
            String.format(
                "/projects/%s/branches/%s/code_owners.rename",
                IdString.fromDecoded(project.get()), "master"));
    r.assertForbidden();
    assertThat(r.getEntityContent()).contains("Authentication required");
  }

  @Test
  public void renameEmailNotSupported() throws Exception {
    // renaming email is only unsupported for the proto backend
    assume().that(backendConfig.getDefaultBackend()).isInstanceOf(ProtoBackend.class);

    String secondaryEmail = "user-foo@example.com";
    accountOperations.account(user.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/")
        .addCodeOwnerEmail(user.email())
        .create();

    RenameEmailInput input = new RenameEmailInput();
    input.oldEmail = user.email();
    input.newEmail = secondaryEmail;
    RestResponse r =
        adminRestSession.post(
            String.format(
                "/projects/%s/branches/%s/code_owners.rename",
                IdString.fromDecoded(project.get()), "master"),
            input);
    r.assertMethodNotAllowed();
    assertThat(r.getEntityContent())
        .contains(
            String.format(
                "rename email not supported by %s backend",
                CodeOwnerBackendId.getBackendId(backendConfig.getDefaultBackend().getClass())));
  }
}
