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
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigUpdate;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.testing.findowners.FindOwnersTestUtil;
import java.util.Optional;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link FindOwnersBackend}. */
public class FindOwnersBackendTest extends AbstractCodeOwnersTest {
  private FindOwnersTestUtil findOwnersTestUtil;
  private FindOwnersBackend findOwnersBackend;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    findOwnersTestUtil = plugin.getSysInjector().getInstance(FindOwnersTestUtil.class);
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
    findOwnersTestUtil.writeCodeOwnerConfig(codeOwnerConfigInRepository);

    Optional<CodeOwnerConfig> codeOwnerConfig =
        findOwnersBackend.getCodeOwnerConfig(codeOwnerConfigKey);
    assertThat(codeOwnerConfig).isPresent();
    assertThat(codeOwnerConfig.get()).isEqualTo(codeOwnerConfigInRepository);
  }

  @Test
  public void createCodeOwnerConfigInitiatedByServer() throws Exception {
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
              null);
      assertThat(codeOwnerConfig).isPresent();
      assertThat(codeOwnerConfig.get()).hasCodeOwnersEmailsThat().containsExactly(admin.email());

      // Check the metadata of the created commit.
      RevCommit newHead = getHead(repo, codeOwnerConfigKey.ref());
      assertThat(newHead.getParent(0)).isEqualTo(origHead);
      assertThat(newHead.getAuthorIdent().getEmailAddress())
          .isEqualTo(serverIdent.get().getEmailAddress());
      assertThat(newHead.getCommitterIdent()).isEqualTo(newHead.getAuthorIdent());
      assertThat(newHead.getShortMessage()).isEqualTo("Create code owner config");
    }
  }

  @Test
  public void createCodeOwnerConfigInitiatedByUser() throws Exception {
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
              identifiedUserFactory.create(user.id()));
      assertThat(codeOwnerConfig).isPresent();
      assertThat(codeOwnerConfig.get()).hasCodeOwnersEmailsThat().containsExactly(admin.email());

      // Check the metadata of the created commit.
      RevCommit newHead = getHead(repo, codeOwnerConfigKey.ref());
      assertThat(newHead.getParent(0)).isEqualTo(origHead);
      assertThat(newHead.getAuthorIdent().getEmailAddress()).isEqualTo(user.email());
      assertThat(newHead.getCommitterIdent().getEmailAddress())
          .isEqualTo(serverIdent.get().getEmailAddress());
      assertThat(newHead.getCommitterIdent().getTimeZone())
          .isEqualTo(newHead.getAuthorIdent().getTimeZone());
      assertThat(newHead.getCommitterIdent().getWhen())
          .isEqualTo(newHead.getAuthorIdent().getWhen());
      assertThat(newHead.getShortMessage()).isEqualTo("Create code owner config");
    }
  }

  @Test
  public void updateCodeOwnerConfigInitiatedByServer() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfigInRepository =
        CodeOwnerConfig.builder(codeOwnerConfigKey).addCodeOwnerEmail(admin.email()).build();
    findOwnersTestUtil.writeCodeOwnerConfig(codeOwnerConfigInRepository);

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
                              codeOwners, ImmutableSet.of(CodeOwnerReference.create(user.email()))))
                  .build(),
              null);
      assertThat(codeOwnerConfig).isPresent();
      assertThat(codeOwnerConfig.get())
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email(), user.email());

      // Check the metadata of the created commit.
      RevCommit newHead = getHead(repo, codeOwnerConfigKey.ref());
      assertThat(newHead.getParent(0)).isEqualTo(origHead);
      assertThat(newHead.getAuthorIdent().getEmailAddress())
          .isEqualTo(serverIdent.get().getEmailAddress());
      assertThat(newHead.getCommitterIdent()).isEqualTo(newHead.getAuthorIdent());
      assertThat(newHead.getShortMessage()).isEqualTo("Update code owner config");
    }
  }

  @Test
  public void updateCodeOwnerConfigInitiatedByUser() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfigInRepository =
        CodeOwnerConfig.builder(codeOwnerConfigKey).addCodeOwnerEmail(admin.email()).build();
    findOwnersTestUtil.writeCodeOwnerConfig(codeOwnerConfigInRepository);

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
                              codeOwners, ImmutableSet.of(CodeOwnerReference.create(user.email()))))
                  .build(),
              identifiedUserFactory.create(user.id()));
      assertThat(codeOwnerConfig).isPresent();
      assertThat(codeOwnerConfig.get())
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email(), user.email());

      // Check the metadata of the created commit.
      RevCommit newHead = getHead(repo, codeOwnerConfigKey.ref());
      assertThat(newHead.getParent(0)).isEqualTo(origHead);
      assertThat(newHead.getAuthorIdent().getEmailAddress()).isEqualTo(user.email());
      assertThat(newHead.getCommitterIdent().getEmailAddress())
          .isEqualTo(serverIdent.get().getEmailAddress());
      assertThat(newHead.getCommitterIdent().getTimeZone())
          .isEqualTo(newHead.getAuthorIdent().getTimeZone());
      assertThat(newHead.getCommitterIdent().getWhen())
          .isEqualTo(newHead.getAuthorIdent().getWhen());
      assertThat(newHead.getShortMessage()).isEqualTo("Update code owner config");
    }
  }

  @Test
  public void deleteCodeOwnerConfigInitiatedByServer() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfigInRepository =
        CodeOwnerConfig.builder(codeOwnerConfigKey).addCodeOwnerEmail(admin.email()).build();
    findOwnersTestUtil.writeCodeOwnerConfig(codeOwnerConfigInRepository);

    try (Repository repo = repoManager.openRepository(project)) {
      // Remember head for later assertions.
      RevCommit origHead = getHead(repo, codeOwnerConfigKey.ref());

      // Create the code owner config.
      Optional<CodeOwnerConfig> codeOwnerConfig =
          findOwnersBackend.upsertCodeOwnerConfig(
              codeOwnerConfigKey,
              CodeOwnerConfigUpdate.builder()
                  .setCodeOwnerModification(codeOwners -> ImmutableSet.of())
                  .build(),
              null);
      assertThat(codeOwnerConfig).isEmpty();

      // Check the metadata of the created commit.
      RevCommit newHead = getHead(repo, codeOwnerConfigKey.ref());
      assertThat(newHead.getParent(0)).isEqualTo(origHead);
      assertThat(newHead.getAuthorIdent().getEmailAddress())
          .isEqualTo(serverIdent.get().getEmailAddress());
      assertThat(newHead.getCommitterIdent()).isEqualTo(newHead.getAuthorIdent());
      assertThat(newHead.getShortMessage()).isEqualTo("Delete code owner config");
    }
  }

  @Test
  public void deleteCodeOwnerConfigInitiatedByUser() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfigInRepository =
        CodeOwnerConfig.builder(codeOwnerConfigKey).addCodeOwnerEmail(admin.email()).build();
    findOwnersTestUtil.writeCodeOwnerConfig(codeOwnerConfigInRepository);

    try (Repository repo = repoManager.openRepository(project)) {
      // Remember head for later assertions.
      RevCommit origHead = getHead(repo, codeOwnerConfigKey.ref());

      // Create the code owner config.
      Optional<CodeOwnerConfig> codeOwnerConfig =
          findOwnersBackend.upsertCodeOwnerConfig(
              codeOwnerConfigKey,
              CodeOwnerConfigUpdate.builder()
                  .setCodeOwnerModification(codeOwners -> ImmutableSet.of())
                  .build(),
              identifiedUserFactory.create(user.id()));
      assertThat(codeOwnerConfig).isEmpty();

      // Check the metadata of the created commit.
      RevCommit newHead = getHead(repo, codeOwnerConfigKey.ref());
      assertThat(newHead.getParent(0)).isEqualTo(origHead);
      assertThat(newHead.getAuthorIdent().getEmailAddress()).isEqualTo(user.email());
      assertThat(newHead.getCommitterIdent().getEmailAddress())
          .isEqualTo(serverIdent.get().getEmailAddress());
      assertThat(newHead.getCommitterIdent().getTimeZone())
          .isEqualTo(newHead.getAuthorIdent().getTimeZone());
      assertThat(newHead.getCommitterIdent().getWhen())
          .isEqualTo(newHead.getAuthorIdent().getWhen());
      assertThat(newHead.getShortMessage()).isEqualTo("Delete code owner config");
    }
  }

  @Test
  public void noOpCodeOwnerConfigUpdate() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfigInRepository =
        CodeOwnerConfig.builder(codeOwnerConfigKey).addCodeOwnerEmail(admin.email()).build();
    findOwnersTestUtil.writeCodeOwnerConfig(codeOwnerConfigInRepository);

    try (Repository repo = repoManager.openRepository(project)) {
      // Remember head for later assertions.
      RevCommit origHead = getHead(repo, codeOwnerConfigKey.ref());

      // Create the code owner config.
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
      // Create the code owner config.
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
