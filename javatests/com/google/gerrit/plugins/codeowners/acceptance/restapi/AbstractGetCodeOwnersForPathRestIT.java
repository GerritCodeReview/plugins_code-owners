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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnersInfoSubject.assertThat;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.extensions.client.ListAccountsOption;
import com.google.gerrit.extensions.client.ListOption;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.api.CodeOwnersInfo;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.util.Arrays;
import org.junit.Test;

/**
 * Base class of acceptance REST tests for REST endpoints that extend {@link
 * com.google.gerrit.plugins.codeowners.restapi.AbstractGetCodeOwnersForPath}.
 */
public abstract class AbstractGetCodeOwnersForPathRestIT extends AbstractCodeOwnersIT {
  /**
   * File path that is used by the tests. Subclasses can use create this file in the test setup in
   * case they test functionality that requires the file to exist.
   */
  protected static final String TEST_PATH = "/foo/bar/baz.md";

  @Inject private AccountOperations accountOperations;

  private String getUrl(String path, String... parameters) {
    StringBuilder b = new StringBuilder();
    b.append(getUrl(path));
    String paramaterString = Arrays.stream(parameters).collect(joining("&"));
    if (!paramaterString.isEmpty()) {
      b.append('?').append(paramaterString);
    }
    return b.toString();
  }

  /**
   * Must return the URL of the get code owners REST endpoint against which the tests should be run.
   *
   * @param path the for which code owners should be retrieved
   */
  protected abstract String getUrl(String path);

  @Test
  public void getCodeOwnerConfigForInvalidPath() throws Exception {
    RestResponse r = adminRestSession.get(getUrl("\0"));
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

    // Make the request with the admin user that has the 'View Secondary Emails' global capability.
    RestResponse r =
        adminRestSession.get(
            getUrl(
                TEST_PATH,
                "o=" + ListAccountsOption.DETAILS.name(),
                "o=" + ListAccountsOption.ALL_EMAILS.name()));
    r.assertOK();
    CodeOwnersInfo codeOwnersInfo =
        newGson().fromJson(r.getReader(), new TypeToken<CodeOwnersInfo>() {}.getType());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountName())
        .containsExactly(admin.fullName());
    assertThat(Iterables.getOnlyElement(codeOwnersInfo.codeOwners).account.secondaryEmails)
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

    // Make the request with the admin user that has the 'View Secondary Emails' global capability.
    RestResponse r =
        adminRestSession.get(
            getUrl(
                TEST_PATH,
                "O="
                    + ListOption.toHex(
                        ImmutableSet.of(
                            ListAccountsOption.DETAILS, ListAccountsOption.ALL_EMAILS))));
    r.assertOK();
    CodeOwnersInfo codeOwnersInfo =
        newGson().fromJson(r.getReader(), new TypeToken<CodeOwnersInfo>() {}.getType());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountId())
        .containsExactly(admin.id());
    assertThat(codeOwnersInfo)
        .hasCodeOwnersThat()
        .comparingElementsUsing(hasAccountName())
        .containsExactly(admin.fullName());
    assertThat(Iterables.getOnlyElement(codeOwnersInfo.codeOwners).account.secondaryEmails)
        .containsExactly(secondaryEmail);
  }

  @Test
  public void cannotGetCodeOwnersWithUnknownAccountOption() throws Exception {
    RestResponse r = adminRestSession.get(getUrl(TEST_PATH, "o=unknown-option"));
    r.assertBadRequest();
    assertThat(r.getEntityContent())
        .isEqualTo("\"unknown_option\" is not a valid value for \"-o\"");
  }

  @Test
  public void cannotGetCodeOwnersWithUnknownHexAccountOption() throws Exception {
    String unknownHexOption = Integer.toHexString(100);
    RestResponse r = adminRestSession.get(getUrl(TEST_PATH, "O=" + unknownHexOption));
    r.assertBadRequest();
    assertThat(r.getEntityContent())
        .isEqualTo(String.format("\"%s\" is not a valid value for \"-O\"", unknownHexOption));
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = "non-existing-backend")
  public void cannotGetCodeOwnersIfPluginConfigurationIsInvalid() throws Exception {
    RestResponse r = adminRestSession.get(getUrl(TEST_PATH));
    r.assertConflict();
    assertThat(r.getEntityContent())
        .contains(
            "Invalid configuration of the code-owners plugin. Code owner backend"
                + " 'non-existing-backend' that is configured in gerrit.config (parameter"
                + " plugin.code-owners.backend) not found.");
  }

  @Test
  public void cannotGetCodeOwnersWithInvalidLimit() throws Exception {
    RestResponse r = adminRestSession.get(getUrl(TEST_PATH, "limit=invalid"));
    r.assertBadRequest();
    assertThat(r.getEntityContent()).contains("\"invalid\" is not a valid value for \"--limit\"");
  }

  @Test
  public void cannotGetCodeOwnersWithInvalidSeed() throws Exception {
    RestResponse r = adminRestSession.get(getUrl(TEST_PATH, "seed=invalid"));
    r.assertBadRequest();
    assertThat(r.getEntityContent()).contains("\"invalid\" is not a valid value for \"--seed\"");
  }
}
