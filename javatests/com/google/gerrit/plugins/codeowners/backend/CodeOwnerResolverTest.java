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

package com.google.gerrit.plugins.codeowners.backend;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerIterableSubject.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.nio.file.Paths;
import java.util.Optional;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerResolver}. */
public class CodeOwnerResolverTest extends AbstractCodeOwnersTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject @ServerInitiated private Provider<AccountsUpdate> accountsUpdate;
  @Inject private ExternalIdNotes.Factory externalIdNotesFactory;

  private CodeOwnerResolver codeOwnerResolver;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerResolver = plugin.getSysInjector().getInstance(CodeOwnerResolver.class);
  }

  @Test
  public void cannotResolveNullToCodeOwner() throws Exception {
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> codeOwnerResolver.resolve(null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerReference");
  }

  @Test
  public void resolveCodeOwnerReferenceForNonExistingEmail() throws Exception {
    assertThat(codeOwnerResolver.resolve(CodeOwnerReference.create("non-existing@test.com")))
        .isEmpty();
  }

  @Test
  public void resolveCodeOwnerReferenceForEmail() throws Exception {
    Optional<CodeOwner> codeOwner =
        codeOwnerResolver.resolve(CodeOwnerReference.create(admin.email()));
    assertThat(codeOwner).isPresent();
    assertThat(codeOwner.get()).hasAccountIdThat().isEqualTo(admin.id());
  }

  @Test
  public void resolveCodeOwnerReferenceForAmbiguousEmail() throws Exception {
    // Create an external ID for 'user' account that has the same email as the 'admin' account.
    accountsUpdate
        .get()
        .update(
            "Test update",
            user.id(),
            (a, u) ->
                u.addExternalId(ExternalId.create("foo", "bar", user.id(), admin.email(), null)));

    assertThat(codeOwnerResolver.resolve(CodeOwnerReference.create(admin.email()))).isEmpty();
  }

  @Test
  public void resolveCodeOwnerReferenceForOrphanedEmail() throws Exception {
    // Create an external ID with an email for a non-existing account.
    String email = "foo.bar@test.com";
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.upsert(ExternalId.createEmail(Account.id(999999), email));
      extIdNotes.commit(md);
    }

    assertThat(codeOwnerResolver.resolve(CodeOwnerReference.create(email))).isEmpty();
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void resolveCodeOwnerReferenceForNonVisibleAccount() throws Exception {
    TestAccount user2 = accountCreator.user2();

    // Set user2 as current user.
    requestScopeOperations.setApiUser(user2.id());

    // user2 cannot see the admin account since they do not share any group and
    // "accounts.visibility" is set to "SAME_GROUP".
    assertThat(codeOwnerResolver.resolve(CodeOwnerReference.create(admin.email()))).isEmpty();
  }

  @Test
  public void resolveCodeOwnerReferenceForSecondaryEmail() throws Exception {
    // add secondary email to user account
    String secondaryEmail = "user@foo.bar";
    accountsUpdate
        .get()
        .update(
            "Add secondary email to user test account",
            user.id(),
            (a, u) -> u.addExternalId(ExternalId.createEmail(user.id(), secondaryEmail)));

    // admin has the "Modify Account" global capability and hence can see the secondary email of the
    // user account.
    Optional<CodeOwner> codeOwner =
        codeOwnerResolver.resolve(CodeOwnerReference.create(secondaryEmail));
    assertThat(codeOwner).value().hasAccountIdThat().isEqualTo(user.id());

    // user can see its own secondary email.
    requestScopeOperations.setApiUser(user.id());
    codeOwner = codeOwnerResolver.resolve(CodeOwnerReference.create(secondaryEmail));
    assertThat(codeOwner).value().hasAccountIdThat().isEqualTo(user.id());
  }

  @Test
  public void resolveCodeOwnerReferenceForNonVisibleSecondaryEmail() throws Exception {
    // add secondary email to admin account
    String secondaryEmail = "admin@foo.bar";
    accountsUpdate
        .get()
        .update(
            "Add secondary email to admin test account",
            admin.id(),
            (a, u) -> u.addExternalId(ExternalId.createEmail(admin.id(), secondaryEmail)));

    // user doesn't have the "Modify Account" global capability and hence cannot see the secondary
    // email of the admin account.
    requestScopeOperations.setApiUser(user.id());
    assertThat(codeOwnerResolver.resolve(CodeOwnerReference.create(secondaryEmail))).isEmpty();
  }

  @Test
  public void resolveLocalCodeOwnersForEmptyCodeOwnerConfig() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/")).build();
    assertThat(codeOwnerResolver.resolveLocalCodeOwners(codeOwnerConfig, Paths.get("/README.md")))
        .isEmpty();
  }

  @Test
  public void resolveLocalCodeOwners() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"))
            .addCodeOwnerEmail(admin.email())
            .addCodeOwnerEmail(user.email())
            .build();
    assertThat(codeOwnerResolver.resolveLocalCodeOwners(codeOwnerConfig, Paths.get("/README.md")))
        .hasAccountIdsThat()
        .containsExactly(admin.id(), user.id());
  }

  @Test
  public void resolveLocalCodeOwnersNonResolvableCodeOwnersAreFilteredOut() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"))
            .addCodeOwnerEmail(admin.email())
            .addCodeOwnerEmail("non-existing@test.com")
            .build();
    assertThat(codeOwnerResolver.resolveLocalCodeOwners(codeOwnerConfig, Paths.get("/README.md")))
        .hasAccountIdsThat()
        .containsExactly(admin.id());
  }

  @Test
  public void cannotResolveLocalCodeOwnersOfNullCodeOwnerConfig() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> codeOwnerResolver.resolveLocalCodeOwners(null, Paths.get("/README.md")));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfig");
  }

  @Test
  public void cannotResolveLocalCodeOwnersForNullPath() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(CodeOwnerConfig.Key.create(project, "master", "/"))
            .addCodeOwnerEmail(admin.email())
            .build();
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> codeOwnerResolver.resolveLocalCodeOwners(codeOwnerConfig, null));
    assertThat(npe).hasMessageThat().isEqualTo("path");
  }
}
