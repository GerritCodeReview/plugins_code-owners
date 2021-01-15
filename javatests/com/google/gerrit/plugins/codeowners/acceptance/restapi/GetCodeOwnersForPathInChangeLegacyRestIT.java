// Copyright (C) 2021 The Android Open Source Project
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
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.extensions.client.ListAccountsOption;
import com.google.gerrit.extensions.client.ListOption;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerInfo;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnersForPathInChangeLegacy} REST endpoint.
 */
public class GetCodeOwnersForPathInChangeLegacyRestIT extends AbstractCodeOwnersIT {
  private static final String TEST_PATH = "/foo/bar/baz.md";

  @Inject private AccountOperations accountOperations;

  private String changeId;

  @Before
  public void createTestChange() throws Exception {
    TestAccount changeOwner =
        accountCreator.create(
            "changeOwner", "changeOwner@example.com", "ChangeOwner", /* displayName= */ null);
    // Create a change that contains the file that is used in the tests. This is necessary since
    // CodeOwnersInChangeCollection rejects requests for paths that are not present in the change.
    changeId =
        createChange(changeOwner, "Test Change", JgitPath.of(TEST_PATH).get(), "some content")
            .getChangeId();
  }

  @Test
  public void getCodeOwnerConfigNonExistingPath() throws Exception {
    RestResponse r = adminRestSession.get(getUrl("non-existing"));
    r.assertNotFound();
  }

  @Test
  public void getCodeOwnerConfigForInvalidPath() throws Exception {
    RestResponse r = adminRestSession.get(getUrl("\0"));
    r.assertBadRequest();
    assertThat(r.getEntityContent()).contains("Nul character not allowed");
  }

  @Test
  public void getCodeOwners() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(admin.email())
        .addCodeOwnerEmail(user.email())
        .create();

    RestResponse r = adminRestSession.get(getUrl(TEST_PATH));
    r.assertOK();
    List<CodeOwnerInfo> codeOwnerInfos =
        newGson().fromJson(r.getReader(), new TypeToken<List<CodeOwnerInfo>>() {}.getType());
    assertThat(codeOwnerInfos)
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id(), user.id());
  }

  @Test
  public void getCodeOwnersWithLimit() throws Exception {
    TestAccount user2 = accountCreator.user2();

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/bar/")
        .addCodeOwnerEmail(admin.email())
        .addCodeOwnerEmail(user.email())
        .addCodeOwnerEmail(user2.email())
        .create();

    RestResponse r = adminRestSession.get(getUrl(TEST_PATH));
    r.assertOK();
    List<CodeOwnerInfo> codeOwnerInfos =
        newGson().fromJson(r.getReader(), new TypeToken<List<CodeOwnerInfo>>() {}.getType());
    assertThat(codeOwnerInfos).hasSize(3);

    r = adminRestSession.get(getUrl(TEST_PATH, "limit=2"));
    r.assertOK();
    codeOwnerInfos =
        newGson().fromJson(r.getReader(), new TypeToken<List<CodeOwnerInfo>>() {}.getType());
    assertThat(codeOwnerInfos).hasSize(2);

    r = adminRestSession.get(getUrl(TEST_PATH, "limit=1"));
    r.assertOK();
    codeOwnerInfos =
        newGson().fromJson(r.getReader(), new TypeToken<List<CodeOwnerInfo>>() {}.getType());
    assertThat(codeOwnerInfos).hasSize(1);
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
            getUrl(
                TEST_PATH,
                "o=" + ListAccountsOption.DETAILS.name(),
                "o=" + ListAccountsOption.ALL_EMAILS.name()));
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
            getUrl(
                TEST_PATH,
                "O="
                    + ListOption.toHex(
                        ImmutableSet.of(
                            ListAccountsOption.DETAILS, ListAccountsOption.ALL_EMAILS))));
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

  private String getUrl(String path) {
    return String.format(
        "/changes/%s/revisions/%s/code_owners.legacy/%s",
        IdString.fromDecoded(changeId),
        IdString.fromDecoded("current"),
        IdString.fromDecoded(path));
  }

  private String getUrl(String path, String... parameters) {
    StringBuilder b = new StringBuilder();
    b.append(getUrl(path));
    String paramaterString = Arrays.stream(parameters).collect(joining("&"));
    if (!paramaterString.isEmpty()) {
      b.append('?').append(paramaterString);
    }
    return b.toString();
  }
}
