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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigSubject.assertThatOptional;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.testing.backend.TestCodeOwnerConfigStorage;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.IdentifiedUser;
import java.nio.file.Paths;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Base class for testing {@link AbstractFileBasedCodeOwnerBackend}s.
 *
 * <p>Implements the tests that are common for all backends once instead of once per backend.
 */
public abstract class AbstractFileBasedCodeOwnerBackendTest extends AbstractCodeOwnersTest {
  protected TestCodeOwnerConfigStorage testCodeOwnerConfigStorage;
  protected CodeOwnerBackend codeOwnerBackend;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    testCodeOwnerConfigStorage =
        plugin
            .getSysInjector()
            .getInstance(TestCodeOwnerConfigStorage.Factory.class)
            .create(getFileName(), plugin.getSysInjector().getInstance(getParserClass()));
    codeOwnerBackend = plugin.getSysInjector().getInstance(getBackendClass());
  }

  /**
   * Must return the class of the {@link AbstractFileBasedCodeOwnerBackend} that should be tested.
   */
  protected abstract Class<? extends AbstractFileBasedCodeOwnerBackend> getBackendClass();

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
    assertThatOptional(codeOwnerBackend.getCodeOwnerConfig(codeOwnerConfigKey, null)).isEmpty();
  }

  @Test
  public void getCodeOwnerConfigFromNonExistingBranch() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "non-existing", "/");
    assertThatOptional(codeOwnerBackend.getCodeOwnerConfig(codeOwnerConfigKey, null)).isEmpty();
  }

  @Test
  public void getCodeOwnerConfig() throws Exception {
    testGetCodeOwnerConfig(getFileName());
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fileExtension", value = "foo")
  public void getCodeOwnerConfigWithFileExtension() throws Exception {
    testGetCodeOwnerConfig(getFileName() + ".foo");
  }

  @Test
  public void getCodeOwnerConfigWithPostFix() throws Exception {
    testGetCodeOwnerConfig(getFileName() + "_post_fix");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fileExtension", value = "foo")
  public void getCodeOwnerConfigWithPostFixAndFileExtension() throws Exception {
    testGetCodeOwnerConfig(getFileName() + "_post_fix.foo");
  }

  @Test
  public void getCodeOwnerConfigWithPreFix() throws Exception {
    testGetCodeOwnerConfig("pre_fix_" + getFileName());
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fileExtension", value = "foo")
  public void getCodeOwnerConfigWithPreFixAndFileExtension() throws Exception {
    testGetCodeOwnerConfig("pre_fix_" + getFileName() + ".foo");
  }

  private void testGetCodeOwnerConfig(String fileName) throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "master", "/", fileName);
    CodeOwnerConfig codeOwnerConfigInRepository =
        testCodeOwnerConfigStorage.writeCodeOwnerConfig(
            codeOwnerConfigKey,
            b -> b.addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email())));

    Optional<CodeOwnerConfig> codeOwnerConfig =
        codeOwnerBackend.getCodeOwnerConfig(codeOwnerConfigKey, null);
    assertThatOptional(codeOwnerConfig).value().isEqualTo(codeOwnerConfigInRepository);
  }

  @Test
  public void getCodeOwnerConfig_noCodeOwnerConfigFoundForUnsupportedFileName() throws Exception {
    testGetCodeOwnerConfig_noCodeOwnerConfigFound("UNSUPPORTED_CODE_OWNERS");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.fileExtension", value = "foo")
  public void getCodeOwnerConfig_noCodeOwnerConfigFoundWhenUsingWrongFileExtension()
      throws Exception {
    testGetCodeOwnerConfig_noCodeOwnerConfigFound(getFileName() + ".bar");
  }

  @Test
  public void getCodeOwnerConfig_noCodeOwnerConfigFoundWhenUsingFileExtensionButNoneWasConfigured()
      throws Exception {
    testGetCodeOwnerConfig_noCodeOwnerConfigFound(getFileName() + ".foo");
  }

  @Test
  public void getCodeOwnerConfig_noCodeOwnerConfigFoundWhenUsingPreAndPostFix() throws Exception {
    testGetCodeOwnerConfig_noCodeOwnerConfigFound("pre_fix_" + getFileName() + "_post_fix");
  }

  @Test
  public void getCodeOwnerConfig_noCodeOwnerConfigFoundWhenUsingInvalidPostFix() throws Exception {
    testGetCodeOwnerConfig_noCodeOwnerConfigFound(getFileName() + "_i n v a l i d");
  }

  @Test
  public void getCodeOwnerConfig_noCodeOwnerConfigFoundWhenUsingInvalidPreFix() throws Exception {
    testGetCodeOwnerConfig_noCodeOwnerConfigFound("i n v a l i d_" + getFileName());
  }

  private void testGetCodeOwnerConfig_noCodeOwnerConfigFound(String fileName) throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "master", "/", fileName);
    assertThatOptional(codeOwnerBackend.getCodeOwnerConfig(codeOwnerConfigKey, null)).isEmpty();
  }

  @Test
  public void getCodeOwnerConfigFromOldRevision() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfigInRepository =
        testCodeOwnerConfigStorage.writeCodeOwnerConfig(
            codeOwnerConfigKey,
            b -> b.addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email())));
    ObjectId revision1 = codeOwnerConfigInRepository.revision();

    // update the code owner config, which creates a new revision
    ObjectId revision2 =
        testCodeOwnerConfigStorage
            .writeCodeOwnerConfig(codeOwnerConfigKey, b -> b.setIgnoreParentCodeOwners())
            .revision();
    assertThat(revision1).isNotEqualTo(revision2);

    Optional<CodeOwnerConfig> codeOwnerConfig =
        codeOwnerBackend.getCodeOwnerConfig(codeOwnerConfigKey, revision1);
    assertThatOptional(codeOwnerConfig).value().isEqualTo(codeOwnerConfigInRepository);
  }

  @Test
  public void cannotGetCodeOwnerConfigFromNonExistingRevision() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnersInternalServerErrorException exception =
        assertThrows(
            CodeOwnersInternalServerErrorException.class,
            () ->
                codeOwnerBackend.getCodeOwnerConfig(
                    codeOwnerConfigKey,
                    ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef")));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("failed to load code owner config %s", codeOwnerConfigKey));
    assertThat(exception).hasCauseThat().isInstanceOf(MissingObjectException.class);
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
          codeOwnerBackend.upsertCodeOwnerConfig(
              codeOwnerConfigKey,
              CodeOwnerConfigUpdate.builder()
                  .setCodeOwnerSetsModification(
                      CodeOwnerSetModification.set(
                          CodeOwnerSet.createWithoutPathExpressions(admin.email())))
                  .build(),
              currentUser);
      assertThatOptional(codeOwnerConfig)
          .value()
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
    testCodeOwnerConfigStorage.writeCodeOwnerConfig(
        codeOwnerConfigKey,
        b -> b.addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email())));

    try (Repository repo = repoManager.openRepository(project)) {
      // Remember head for later assertions.
      RevCommit origHead = getHead(repo, codeOwnerConfigKey.ref());

      // Update the code owner config.
      Optional<CodeOwnerConfig> codeOwnerConfig =
          codeOwnerBackend.upsertCodeOwnerConfig(
              codeOwnerConfigKey,
              CodeOwnerConfigUpdate.builder()
                  .setCodeOwnerSetsModification(CodeOwnerSetModification.addToOnlySet(user.email()))
                  .build(),
              currentUser);
      assertThatOptional(codeOwnerConfig)
          .value()
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
    testCodeOwnerConfigStorage.writeCodeOwnerConfig(
        codeOwnerConfigKey,
        b -> b.addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email())));

    try (Repository repo = repoManager.openRepository(project)) {
      // Remember head for later assertions.
      RevCommit origHead = getHead(repo, codeOwnerConfigKey.ref());

      // Update the code owner config.
      Optional<CodeOwnerConfig> codeOwnerConfig =
          codeOwnerBackend.upsertCodeOwnerConfig(
              codeOwnerConfigKey,
              CodeOwnerConfigUpdate.builder()
                  .setCodeOwnerSetsModification(CodeOwnerSetModification.clear())
                  .build(),
              currentUser);
      assertThatOptional(codeOwnerConfig).isEmpty();

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
    testCodeOwnerConfigStorage.writeCodeOwnerConfig(
        codeOwnerConfigKey,
        b -> b.addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email())));

    try (Repository repo = repoManager.openRepository(project)) {
      // Remember head for later assertions.
      RevCommit origHead = getHead(repo, codeOwnerConfigKey.ref());

      // Update the code owner config.
      Optional<CodeOwnerConfig> codeOwnerConfig =
          codeOwnerBackend.upsertCodeOwnerConfig(
              codeOwnerConfigKey, CodeOwnerConfigUpdate.builder().build(), null);
      assertThatOptional(codeOwnerConfig)
          .value()
          .hasCodeOwnerSetsThat()
          .onlyElement()
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email());

      // Check that no commit was created.
      assertThat(getHead(repo, codeOwnerConfigKey.ref())).isEqualTo(origHead);
    }
  }

  @Test
  public void cannotUpdateInvalidCodeOwnerConfig() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");

    // create an invalid code owner config
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(codeOwnerConfigKey.project()))) {
      Ref ref = testRepo.getRepository().exactRef(codeOwnerConfigKey.ref());
      RevCommit head = testRepo.getRevWalk().parseCommit(ref.getObjectId());
      testRepo.update(
          codeOwnerConfigKey.ref(),
          testRepo
              .commit()
              .parent(head)
              .message("Add invalid test code owner config")
              .add(JgitPath.of(codeOwnerConfigKey.filePath(getFileName())).get(), "INVALID"));
    }

    // Try to update the code owner config.
    CodeOwnersInternalServerErrorException exception =
        assertThrows(
            CodeOwnersInternalServerErrorException.class,
            () ->
                codeOwnerBackend.upsertCodeOwnerConfig(
                    codeOwnerConfigKey,
                    CodeOwnerConfigUpdate.builder()
                        .setCodeOwnerSetsModification(
                            CodeOwnerSetModification.addToOnlySet(user.email()))
                        .build(),
                    /* currentUser= */ null));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("failed to upsert code owner config %s", codeOwnerConfigKey));
    assertThat(exception).hasCauseThat().isInstanceOf(ConfigInvalidException.class);
    assertThat(exception.getCause())
        .hasMessageThat()
        .contains(
            String.format(
                "invalid code owner config file '/%s' (project = %s, branch = master)",
                getFileName(), project));
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
                  codeOwnerBackend.upsertCodeOwnerConfig(
                      codeOwnerConfigKey, CodeOwnerConfigUpdate.builder().build(), null));
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo(String.format("branch %s does not exist", codeOwnerConfigKey.ref()));

      // Check that the branch was not created.
      assertThat(repo.exactRef(codeOwnerConfigKey.ref())).isNull();
    }
  }

  @Test
  public void getFilePathForCodeOwnerConfigKeyWithoutFileName() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    assertThat(codeOwnerBackend.getFilePath(codeOwnerConfigKey))
        .isEqualTo(Paths.get(codeOwnerConfigKey.folderPath() + getFileName()));
  }

  @Test
  public void getFilePathForCodeOwnerConfigKeyWithFileName() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "master", "/", getFileName() + "_foo_bar");
    assertThat(codeOwnerBackend.getFilePath(codeOwnerConfigKey))
        .isEqualTo(
            Paths.get(codeOwnerConfigKey.folderPath() + codeOwnerConfigKey.fileName().get()));
  }
}
