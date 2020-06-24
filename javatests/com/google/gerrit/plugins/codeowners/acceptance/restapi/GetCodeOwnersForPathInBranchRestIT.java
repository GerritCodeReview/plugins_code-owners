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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoSubject.hasAccountId;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerInfoSubject.hasAccountName;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.extensions.client.ListAccountsOption;
import com.google.gerrit.extensions.client.ListOption;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerInfo;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.util.List;
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
public class GetCodeOwnersForPathInBranchRestIT extends AbstractCodeOwnersIT {
  @Inject private AccountOperations accountOperations;

  @Test
  public void getCodeOwnerConfigForInvalidPath() throws Exception {
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

  @Test
  public void getCodeOwnersWithAccountOptions() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(admin.email())
        .create();

    // add secondary email to admin account
    String secondaryEmail = "admin@foo.bar";
    accountOperations.account(admin.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    // Make the request with the admin user that has the 'Modify Account' global capability.
    RestResponse r =
        adminRestSession.get(
            String.format(
                "/projects/%s/branches/%s/code_owners/%s?o=%s&o=%s",
                IdString.fromDecoded(project.get()),
                IdString.fromDecoded("master"),
                IdString.fromDecoded("/foo/bar/baz.md"),
                ListAccountsOption.DETAILS.name(),
                ListAccountsOption.ALL_EMAILS.name()));
    r.assertOK();
    List<CodeOwnerInfo> codeOwnerInfos =
        newGson().fromJson(r.getReader(), new TypeToken<List<CodeOwnerInfo>>() {}.getType());
    assertThat(codeOwnerInfos).comparingElementsUsing(hasAccountId()).containsExactly(admin.id());
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountName())
        .containsExactly(admin.fullName());
    assertThat(Iterables.getOnlyElement(codeOwnerInfos).account.secondaryEmails)
        .containsExactly(secondaryEmail);
  }

  @Test
  public void getCodeOwnersWithHexAccountOptions() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(admin.email())
        .create();

    // add secondary email to admin account
    String secondaryEmail = "admin@foo.bar";
    accountOperations.account(admin.id()).forUpdate().addSecondaryEmail(secondaryEmail).update();

    // Make the request with the admin user that has the 'Modify Account' global capability.
    RestResponse r =
        adminRestSession.get(
            String.format(
                "/projects/%s/branches/%s/code_owners/%s?O=%s",
                IdString.fromDecoded(project.get()),
                IdString.fromDecoded("master"),
                IdString.fromDecoded("/foo/bar/baz.md"),
                ListOption.toHex(
                    ImmutableSet.of(ListAccountsOption.DETAILS, ListAccountsOption.ALL_EMAILS))));
    r.assertOK();
    List<CodeOwnerInfo> codeOwnerInfos =
        newGson().fromJson(r.getReader(), new TypeToken<List<CodeOwnerInfo>>() {}.getType());
    assertThat(codeOwnerInfos).comparingElementsUsing(hasAccountId()).containsExactly(admin.id());
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountName())
        .containsExactly(admin.fullName());
    assertThat(Iterables.getOnlyElement(codeOwnerInfos).account.secondaryEmails)
        .containsExactly(secondaryEmail);
  }

  @Test
  public void cannotGetCodeOwnersWithUnknownAccountOption() throws Exception {
    RestResponse r =
        adminRestSession.get(
            String.format(
                "/projects/%s/branches/%s/code_owners/%s?o=%s",
                IdString.fromDecoded(project.get()),
                IdString.fromDecoded("master"),
                IdString.fromDecoded("/foo/bar/baz.md"),
                "unknown-option"));
    r.assertBadRequest();
    assertThat(r.getEntityContent())
        .isEqualTo("\"unknown_option\" is not a valid value for \"-o\"");
  }

  @Test
  public void cannotGetCodeOwnersWithUnknownHexAccountOption() throws Exception {
    String unknownHexOption = Integer.toHexString(100);
    RestResponse r =
        adminRestSession.get(
            String.format(
                "/projects/%s/branches/%s/code_owners/%s?O=%s",
                IdString.fromDecoded(project.get()),
                IdString.fromDecoded("master"),
                IdString.fromDecoded("/foo/bar/baz.md"),
                unknownHexOption));
    r.assertBadRequest();
    assertThat(r.getEntityContent())
        .isEqualTo(String.format("\"%s\" is not a valid value for \"-O\"", unknownHexOption));
  }
}
