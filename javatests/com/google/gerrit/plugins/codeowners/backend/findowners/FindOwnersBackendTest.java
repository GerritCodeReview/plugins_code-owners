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

package com.google.gerrit.plugins.codeowners.backend.findowners;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigUpdate;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.testing.backend.TestCodeOwnerConfigStorage;
import com.google.gerrit.server.IdentifiedUser;
import java.util.Optional;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link FindOwnersBackend}. */
public class FindOwnersBackendTest extends AbstractCodeOwnersTest {
  private TestCodeOwnerConfigStorage backendTestUtil;
  private FindOwnersBackend findOwnersBackend;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    backendTestUtil =
        plugin
            .getSysInjector()
            .getInstance(TestCodeOwnerConfigStorage.Factory.class)
            .create(
                FindOwnersBackend.CODE_OWNER_CONFIG_FILE_NAME,
                plugin.getSysInjector().getInstance(FindOwnersCodeOwnerConfigParser.class));
    findOwnersBackend = plugin.getSysInjector().getInstance(FindOwnersBackend.class);
  }

  @Test
  public void getNonExistingCodeOwnerConfig() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "master", "/non-existing/");
    assertThat(findOwnersBackend.getCodeOwnerConfig(codeOwnerConfigKey)).isEmpty();
  }

  @Test
  public void getCodeOwnerConfigFromNonExistingBranch() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "non-existing", "/");
    assertThat(findOwnersBackend.getCodeOwnerConfig(codeOwnerConfigKey)).isEmpty();
  }

  @Test
  public void getCodeOwnerConfig() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfigInRepository =
        CodeOwnerConfig.builder(codeOwnerConfigKey).addCodeOwnerEmail(admin.email()).build();
    backendTestUtil.writeCodeOwnerConfig(codeOwnerConfigInRepository);

    Optional<CodeOwnerConfig> codeOwnerConfig =
        findOwnersBackend.getCodeOwnerConfig(codeOwnerConfigKey);
    assertThat(codeOwnerConfig).isPresent();
    assertThat(codeOwnerConfig.get()).isEqualTo(codeOwnerConfigInRepository);
  }

  @Test
  public void createCodeOwnerConfigInitiatedByServer() throws Exception {
    testCreateCodeOwnerConfig(null);
  }

  @Test
  public void createCodeOwnerConfigInitiatedByUser() throws Exception {
    testCreateCodeOwnerConfig(identifiedUserFactory.create(user.id()));
  }

  private void testCreateCodeOwnerConfig(@Nullable IdentifiedUser currentUser) throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");

    try (Repository repo = repoManager.openRepository(project)) {
      // Remember head for later assertions.
      RevCommit origHead = getHead(repo, codeOwnerConfigKey.ref());

      // Create the code owner config.
      Optional<CodeOwnerConfig> codeOwnerConfig =
          findOwnersBackend.upsertCodeOwnerConfig(
              codeOwnerConfigKey,
              CodeOwnerConfigUpdate.builder()
                  .setCodeOwnerModification(
                      codeOwners ->
                          Sets.union(
                              codeOwners,
                              ImmutableSet.of(CodeOwnerReference.create(admin.email()))))
                  .build(),
              currentUser);
      assertThat(codeOwnerConfig).isPresent();
      assertThat(codeOwnerConfig.get()).hasCodeOwnersEmailsThat().containsExactly(admin.email());

      // Check the metadata of the created commit.
      RevCommit newHead = getHead(repo, codeOwnerConfigKey.ref());
      assertThat(newHead.getParent(0)).isEqualTo(origHead);
      assertThat(newHead.getShortMessage()).isEqualTo("Create code owner config");

      // Check author identity.
      if (currentUser != null) {
        assertThat(newHead.getAuthorIdent().getEmailAddress())
            .isEqualTo(currentUser.getAccount().preferredEmail());
      } else {
        assertThat(newHead.getAuthorIdent().getEmailAddress())
            .isEqualTo(serverIdent.get().getEmailAddress());
        assertThat(newHead.getCommitterIdent()).isEqualTo(newHead.getAuthorIdent());
      }

      // Check committer identity.
      assertThat(newHead.getCommitterIdent().getEmailAddress())
          .isEqualTo(serverIdent.get().getEmailAddress());
      assertThat(newHead.getCommitterIdent().getTimeZone())
          .isEqualTo(newHead.getAuthorIdent().getTimeZone());
      assertThat(newHead.getCommitterIdent().getWhen().getTime())
          .isEqualTo(newHead.getAuthorIdent().getWhen().getTime());
    }
  }

  @Test
  public void updateCodeOwnerConfigInitiatedByServer() throws Exception {
    testUpdateCodeOwnerConfig(null);
  }

  @Test
  public void updateCodeOwnerConfigInitiatedByUser() throws Exception {
    testUpdateCodeOwnerConfig(identifiedUserFactory.create(user.id()));
  }

  private void testUpdateCodeOwnerConfig(@Nullable IdentifiedUser currentUser) throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfigInRepository =
        CodeOwnerConfig.builder(codeOwnerConfigKey).addCodeOwnerEmail(admin.email()).build();
    backendTestUtil.writeCodeOwnerConfig(codeOwnerConfigInRepository);

    try (Repository repo = repoManager.openRepository(project)) {
      // Remember head for later assertions.
      RevCommit origHead = getHead(repo, codeOwnerConfigKey.ref());

      // Update the code owner config.
      Optional<CodeOwnerConfig> codeOwnerConfig =
          findOwnersBackend.upsertCodeOwnerConfig(
              codeOwnerConfigKey,
              CodeOwnerConfigUpdate.builder()
                  .setCodeOwnerModification(
                      codeOwners ->
                          Sets.union(
                              codeOwners, ImmutableSet.of(CodeOwnerReference.create(user.email()))))
                  .build(),
              currentUser);
      assertThat(codeOwnerConfig).isPresent();
      assertThat(codeOwnerConfig.get())
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email(), user.email());

      // Check the metadata of the created commit.
      RevCommit newHead = getHead(repo, codeOwnerConfigKey.ref());
      assertThat(newHead.getParent(0)).isEqualTo(origHead);
      assertThat(newHead.getShortMessage()).isEqualTo("Update code owner config");

      // Check author identity.
      if (currentUser != null) {
        assertThat(newHead.getAuthorIdent().getEmailAddress())
            .isEqualTo(currentUser.getAccount().preferredEmail());
      } else {
        assertThat(newHead.getAuthorIdent().getEmailAddress())
            .isEqualTo(serverIdent.get().getEmailAddress());
        assertThat(newHead.getCommitterIdent()).isEqualTo(newHead.getAuthorIdent());
      }

      // Check committer identity.
      assertThat(newHead.getCommitterIdent().getEmailAddress())
          .isEqualTo(serverIdent.get().getEmailAddress());
      assertThat(newHead.getCommitterIdent().getTimeZone())
          .isEqualTo(newHead.getAuthorIdent().getTimeZone());
      assertThat(newHead.getCommitterIdent().getWhen().getTime())
          .isEqualTo(newHead.getAuthorIdent().getWhen().getTime());
    }
  }

  @Test
  public void deleteCodeOwnerConfigInitiatedByServer() throws Exception {
    testDeleteCodeOwnerConfigInitiatedByServer(null);
  }

  @Test
  public void deleteCodeOwnerConfigInitiatedByUser() throws Exception {
    testDeleteCodeOwnerConfigInitiatedByServer(identifiedUserFactory.create(user.id()));
  }

  private void testDeleteCodeOwnerConfigInitiatedByServer(@Nullable IdentifiedUser currentUser)
      throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfigInRepository =
        CodeOwnerConfig.builder(codeOwnerConfigKey).addCodeOwnerEmail(admin.email()).build();
    backendTestUtil.writeCodeOwnerConfig(codeOwnerConfigInRepository);

    try (Repository repo = repoManager.openRepository(project)) {
      // Remember head for later assertions.
      RevCommit origHead = getHead(repo, codeOwnerConfigKey.ref());

      // Update the code owner config.
      Optional<CodeOwnerConfig> codeOwnerConfig =
          findOwnersBackend.upsertCodeOwnerConfig(
              codeOwnerConfigKey,
              CodeOwnerConfigUpdate.builder()
                  .setCodeOwnerModification(codeOwners -> ImmutableSet.of())
                  .build(),
              currentUser);
      assertThat(codeOwnerConfig).isEmpty();

      // Check the metadata of the created commit.
      RevCommit newHead = getHead(repo, codeOwnerConfigKey.ref());
      assertThat(newHead.getParent(0)).isEqualTo(origHead);
      assertThat(newHead.getShortMessage()).isEqualTo("Delete code owner config");

      // Check author identity.
      if (currentUser != null) {
        assertThat(newHead.getAuthorIdent().getEmailAddress())
            .isEqualTo(currentUser.getAccount().preferredEmail());
      } else {
        assertThat(newHead.getAuthorIdent().getEmailAddress())
            .isEqualTo(serverIdent.get().getEmailAddress());
        assertThat(newHead.getCommitterIdent()).isEqualTo(newHead.getAuthorIdent());
      }

      // Check committer identity.
      assertThat(newHead.getCommitterIdent().getEmailAddress())
          .isEqualTo(serverIdent.get().getEmailAddress());
      assertThat(newHead.getCommitterIdent().getTimeZone())
          .isEqualTo(newHead.getAuthorIdent().getTimeZone());
      assertThat(newHead.getCommitterIdent().getWhen().getTime())
          .isEqualTo(newHead.getAuthorIdent().getWhen().getTime());
    }
  }

  @Test
  public void noOpCodeOwnerConfigUpdate() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfigInRepository =
        CodeOwnerConfig.builder(codeOwnerConfigKey).addCodeOwnerEmail(admin.email()).build();
    backendTestUtil.writeCodeOwnerConfig(codeOwnerConfigInRepository);

    try (Repository repo = repoManager.openRepository(project)) {
      // Remember head for later assertions.
      RevCommit origHead = getHead(repo, codeOwnerConfigKey.ref());

      // Update the code owner config.
      Optional<CodeOwnerConfig> codeOwnerConfig =
          findOwnersBackend.upsertCodeOwnerConfig(
              codeOwnerConfigKey, CodeOwnerConfigUpdate.builder().build(), null);
      assertThat(codeOwnerConfig).isPresent();
      assertThat(codeOwnerConfig.get()).hasCodeOwnersEmailsThat().containsExactly(admin.email());

      // Check that no commit was created.
      assertThat(getHead(repo, codeOwnerConfigKey.ref())).isEqualTo(origHead);
    }
  }

  @Test
  public void cannotUpdateCodeOwnerConfigInNonExistingBranch() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "non-existing", "/");

    try (Repository repo = repoManager.openRepository(project)) {
      // Update the code owner config.
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  findOwnersBackend.upsertCodeOwnerConfig(
                      codeOwnerConfigKey, CodeOwnerConfigUpdate.builder().build(), null));
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo(String.format("branch %s does not exist", codeOwnerConfigKey.ref()));

      // Check that the branch was not created.
      assertThat(repo.exactRef(codeOwnerConfigKey.ref())).isNull();
    }
  }
}
