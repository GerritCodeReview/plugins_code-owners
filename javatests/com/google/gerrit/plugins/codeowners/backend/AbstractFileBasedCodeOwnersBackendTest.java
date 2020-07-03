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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.testing.backend.TestCodeOwnerConfigStorage;
import com.google.gerrit.server.IdentifiedUser;
import java.util.Optional;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Base class for testing {@link AbstractFileBasedCodeOwnersBackend}s.
 *
 * <p>Implements the tests that are common for all backends once instead of once per backend.
 */
public abstract class AbstractFileBasedCodeOwnersBackendTest extends AbstractCodeOwnersTest {
  protected TestCodeOwnerConfigStorage testCodeOwnerConfigStorage;
  protected CodeOwnersBackend codeOwnersBackend;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    testCodeOwnerConfigStorage =
        plugin
            .getSysInjector()
            .getInstance(TestCodeOwnerConfigStorage.Factory.class)
            .create(getFileName(), plugin.getSysInjector().getInstance(getParserClass()));
    codeOwnersBackend = plugin.getSysInjector().getInstance(getBackendClass());
  }

  /**
   * Must return the class of the {@link AbstractFileBasedCodeOwnersBackend} that should be tested.
   */
  protected abstract Class<? extends AbstractFileBasedCodeOwnersBackend> getBackendClass();

  /** Must return the name of the files in which {@link CodeOwnerConfig}s are stored. */
  protected abstract String getFileName();

  /**
   * Must return the class of the {@link CodeOwnerConfigParser} that is used by the code owners
   * backend.
   */
  protected abstract Class<? extends CodeOwnerConfigParser> getParserClass();

  @Test
  public void getNonExistingCodeOwnerConfig() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "master", "/non-existing/");
    assertThat(codeOwnersBackend.getCodeOwnerConfig(codeOwnerConfigKey)).isEmpty();
  }

  @Test
  public void getCodeOwnerConfigFromNonExistingBranch() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "non-existing", "/");
    assertThat(codeOwnersBackend.getCodeOwnerConfig(codeOwnerConfigKey)).isEmpty();
  }

  @Test
  public void getCodeOwnerConfig() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfigInRepository =
        CodeOwnerConfig.builder(codeOwnerConfigKey)
            .addCodeOwnerSet(CodeOwnerSet.createForEmails((admin.email())))
            .build();
    testCodeOwnerConfigStorage.writeCodeOwnerConfig(codeOwnerConfigInRepository);

    Optional<CodeOwnerConfig> codeOwnerConfig =
        codeOwnersBackend.getCodeOwnerConfig(codeOwnerConfigKey);
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
          codeOwnersBackend.upsertCodeOwnerConfig(
              codeOwnerConfigKey,
              CodeOwnerConfigUpdate.builder()
                  .setCodeOwnerSetsModification(
                      CodeOwnerSetModification.set(CodeOwnerSet.createForEmails(admin.email())))
                  .build(),
              currentUser);
      assertThat(codeOwnerConfig).isPresent();
      assertThat(codeOwnerConfig.get())
          .hasCodeOwnerSetsThat()
          .onlyElement()
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email());

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
      assertThat(newHead.getCommitterIdent().getWhen())
          .isEqualTo(newHead.getAuthorIdent().getWhen());
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
        CodeOwnerConfig.builder(codeOwnerConfigKey)
            .addCodeOwnerSet(CodeOwnerSet.createForEmails(admin.email()))
            .build();
    testCodeOwnerConfigStorage.writeCodeOwnerConfig(codeOwnerConfigInRepository);

    try (Repository repo = repoManager.openRepository(project)) {
      // Remember head for later assertions.
      RevCommit origHead = getHead(repo, codeOwnerConfigKey.ref());

      // Update the code owner config.
      Optional<CodeOwnerConfig> codeOwnerConfig =
          codeOwnersBackend.upsertCodeOwnerConfig(
              codeOwnerConfigKey,
              CodeOwnerConfigUpdate.builder()
                  .setCodeOwnerSetsModification(CodeOwnerSetModification.addToOnlySet(user.email()))
                  .build(),
              currentUser);
      assertThat(codeOwnerConfig).isPresent();
      assertThat(codeOwnerConfig.get())
          .hasCodeOwnerSetsThat()
          .onlyElement()
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
      assertThat(newHead.getCommitterIdent().getWhen())
          .isEqualTo(newHead.getAuthorIdent().getWhen());
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
        CodeOwnerConfig.builder(codeOwnerConfigKey)
            .addCodeOwnerSet(CodeOwnerSet.createForEmails(admin.email()))
            .build();
    testCodeOwnerConfigStorage.writeCodeOwnerConfig(codeOwnerConfigInRepository);

    try (Repository repo = repoManager.openRepository(project)) {
      // Remember head for later assertions.
      RevCommit origHead = getHead(repo, codeOwnerConfigKey.ref());

      // Update the code owner config.
      Optional<CodeOwnerConfig> codeOwnerConfig =
          codeOwnersBackend.upsertCodeOwnerConfig(
              codeOwnerConfigKey,
              CodeOwnerConfigUpdate.builder()
                  .setCodeOwnerSetsModification(CodeOwnerSetModification.clear())
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
      assertThat(newHead.getCommitterIdent().getWhen())
          .isEqualTo(newHead.getAuthorIdent().getWhen());
    }
  }

  @Test
  public void noOpCodeOwnerConfigUpdate() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfigInRepository =
        CodeOwnerConfig.builder(codeOwnerConfigKey)
            .addCodeOwnerSet(CodeOwnerSet.createForEmails(admin.email()))
            .build();
    testCodeOwnerConfigStorage.writeCodeOwnerConfig(codeOwnerConfigInRepository);

    try (Repository repo = repoManager.openRepository(project)) {
      // Remember head for later assertions.
      RevCommit origHead = getHead(repo, codeOwnerConfigKey.ref());

      // Update the code owner config.
      Optional<CodeOwnerConfig> codeOwnerConfig =
          codeOwnersBackend.upsertCodeOwnerConfig(
              codeOwnerConfigKey, CodeOwnerConfigUpdate.builder().build(), null);
      assertThat(codeOwnerConfig).isPresent();
      assertThat(codeOwnerConfig.get())
          .hasCodeOwnerSetsThat()
          .onlyElement()
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email());

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
                  codeOwnersBackend.upsertCodeOwnerConfig(
                      codeOwnerConfigKey, CodeOwnerConfigUpdate.builder().build(), null));
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo(String.format("branch %s does not exist", codeOwnerConfigKey.ref()));

      // Check that the branch was not created.
      assertThat(repo.exactRef(codeOwnerConfigKey.ref())).isNull();
    }
  }
}
