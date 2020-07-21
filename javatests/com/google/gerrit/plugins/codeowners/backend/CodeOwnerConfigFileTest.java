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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigSubject.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigSubject.assertThatOptional;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.testing.backend.TestCodeOwnerConfigStorage;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerConfigFile}. */
public class CodeOwnerConfigFileTest extends AbstractCodeOwnersTest {
  private static final String CODE_OWNER_CONFIG_FILE_NAME = "CODE_OWNER_CONFIG";
  private static final CodeOwnerConfigParser CODE_OWNER_CONFIG_PARSER =
      new TestCodeOwnerConfigParser();

  @Inject private MetaDataUpdate.Server metaDataUpdateServer;

  private TestCodeOwnerConfigStorage testCodeOwnerConfigStorage;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    testCodeOwnerConfigStorage =
        plugin
            .getSysInjector()
            .getInstance(TestCodeOwnerConfigStorage.Factory.class)
            .create(CODE_OWNER_CONFIG_FILE_NAME, CODE_OWNER_CONFIG_PARSER);
  }

  @Test
  public void loadNonExistingCodeOwnerConfigFile() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "master", "/non-existing/");
    CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);
    assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();
  }

  @Test
  public void loadCodeOwnerConfigFileFromNonExistingBranch() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "non-existing", "/");
    CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);
    assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();
  }

  @Test
  public void loadEmptyCodeOwnerConfigFile() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfig = CodeOwnerConfig.builder(codeOwnerConfigKey).build();
    testCodeOwnerConfigStorage.writeCodeOwnerConfig(codeOwnerConfig);

    CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);
    assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig())
        .value()
        .hasCodeOwnerSetsThat()
        .isEmpty();
  }

  @Test
  public void loadCodeOwnerConfigFile() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(codeOwnerConfigKey)
            .setIgnoreParentCodeOwners()
            .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
            .build();
    testCodeOwnerConfigStorage.writeCodeOwnerConfig(codeOwnerConfig);

    CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);
    assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig())
        .value()
        .hasIgnoreParentCodeOwnersThat()
        .isTrue();
    assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig().get())
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email());
  }

  @Test
  public void loadCodeOwnerConfigFileFromOldRevision() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(codeOwnerConfigKey)
            .setIgnoreParentCodeOwners()
            .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
            .build();
    ObjectId revision1 = testCodeOwnerConfigStorage.writeCodeOwnerConfig(codeOwnerConfig);

    // update the code owner config, which creates a new revision
    ObjectId revision2 =
        testCodeOwnerConfigStorage.writeCodeOwnerConfig(
            codeOwnerConfig.toBuilder().setIgnoreParentCodeOwners(false).build());
    assertThat(revision1).isNotEqualTo(revision2);

    CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey, revision1);
    assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig())
        .value()
        .hasIgnoreParentCodeOwnersThat()
        .isTrue();
    assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig().get())
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email());
  }

  @Test
  public void cannotLoadCodeOwnerConfigFileFromNonExistingRevision() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    assertThrows(
        MissingObjectException.class,
        () ->
            loadCodeOwnerConfig(
                codeOwnerConfigKey,
                ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef")));
  }

  @Test
  public void createCodeOwnerConfigFile() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "master", "/foo/bar/");
    try (Repository repo = repoManager.openRepository(project);
        MetaDataUpdate metaDataUpdate = metaDataUpdateServer.create(project)) {
      CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);

      // Check that the code owner config doesn't exist yet.
      assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();
      assertThatOptional(testCodeOwnerConfigStorage.readCodeOwnerConfig(codeOwnerConfigKey))
          .isEmpty();

      // Remember head so that we can check that the branch is changed.
      RevCommit head = getHead(repo, codeOwnerConfigKey.ref());

      // Create the code owner config.
      codeOwnerConfigFile.setCodeOwnerConfigUpdate(
          CodeOwnerConfigUpdate.builder()
              .setIgnoreParentCodeOwners(true)
              .setCodeOwnerSetsModification(
                  CodeOwnerSetModification.set(
                      CodeOwnerSet.createWithoutPathExpressions(admin.email())))
              .build());
      codeOwnerConfigFile.commit(metaDataUpdate);

      // Check that the loaded code owner config was updated.
      assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig())
          .value()
          .hasIgnoreParentCodeOwnersThat()
          .isTrue();
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig().get())
          .hasCodeOwnerSetsThat()
          .onlyElement()
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email());

      // Check that the code owner config was created in the repository.
      Optional<CodeOwnerConfig> codeOwnerConfigInRepo =
          testCodeOwnerConfigStorage.readCodeOwnerConfig(codeOwnerConfigKey);
      assertThatOptional(codeOwnerConfigInRepo).value().hasIgnoreParentCodeOwnersThat().isTrue();
      assertThatOptional(codeOwnerConfigInRepo)
          .value()
          .hasCodeOwnerSetsThat()
          .onlyElement()
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email());

      // Check that the branch was changed.
      assertThat(head).isNotEqualTo(getHead(repo, codeOwnerConfigKey.ref()));
    }
  }

  @Test
  public void codeOwnerConfigFileIsNotCreatedIfNoCodeOwnerConfigUpdateSpecified() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "master", "/foo/bar/");
    try (Repository repo = repoManager.openRepository(project);
        MetaDataUpdate metaDataUpdate = metaDataUpdateServer.create(project)) {
      CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);

      // Check that the code owner config doesn't exist yet.
      assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();
      assertThatOptional(testCodeOwnerConfigStorage.readCodeOwnerConfig(codeOwnerConfigKey))
          .isEmpty();

      // Remember head so that we can check that the branch doesn't change.
      RevCommit head = getHead(repo, codeOwnerConfigKey.ref());

      // Try to create the code owner config without setting a code owner config update.
      codeOwnerConfigFile.commit(metaDataUpdate);

      // Check that the loaded code owner config was not created.
      assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();

      // Check that no code owner config was created in the repository.
      Optional<CodeOwnerConfig> codeOwnerConfigInRepo =
          testCodeOwnerConfigStorage.readCodeOwnerConfig(codeOwnerConfigKey);
      assertThatOptional(codeOwnerConfigInRepo).isEmpty();

      // Check that the branch didn't change.
      assertThat(head).isEqualTo(getHead(repo, codeOwnerConfigKey.ref()));
    }
  }

  @Test
  public void codeOwnerConfigFileIsNotCreatedIfEmpty() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "master", "/foo/bar/");
    try (Repository repo = repoManager.openRepository(project);
        MetaDataUpdate metaDataUpdate = metaDataUpdateServer.create(project)) {
      CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);

      // Check that the code owner config doesn't exist yet.
      assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();
      assertThatOptional(testCodeOwnerConfigStorage.readCodeOwnerConfig(codeOwnerConfigKey))
          .isEmpty();

      // Remember head so that we can check that the branch doesn't change.
      RevCommit head = getHead(repo, codeOwnerConfigKey.ref());

      // Try to create the code owner config without specifying code owners.
      codeOwnerConfigFile.setCodeOwnerConfigUpdate(CodeOwnerConfigUpdate.builder().build());
      codeOwnerConfigFile.commit(metaDataUpdate);

      // Check that the loaded code owner config was not created.
      assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();

      // Check that no code owner config was created in the repository.
      Optional<CodeOwnerConfig> codeOwnerConfigInRepo =
          testCodeOwnerConfigStorage.readCodeOwnerConfig(codeOwnerConfigKey);
      assertThatOptional(codeOwnerConfigInRepo).isEmpty();

      // Check that the branch didn't change.
      assertThat(head).isEqualTo(getHead(repo, codeOwnerConfigKey.ref()));
    }
  }

  @Test
  public void cannotCreateCodeOwnerConfigFileInNonExistingBranch() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "non-existing", "/foo/bar/");
    try (Repository repo = repoManager.openRepository(project);
        MetaDataUpdate metaDataUpdate = metaDataUpdateServer.create(project)) {
      CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);

      // Check that the code owner config doesn't exist yet.
      assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();
      assertThatOptional(testCodeOwnerConfigStorage.readCodeOwnerConfig(codeOwnerConfigKey))
          .isEmpty();

      // Create the code owner config.
      String email = "admin@example.com";
      codeOwnerConfigFile.setCodeOwnerConfigUpdate(
          CodeOwnerConfigUpdate.builder()
              .setCodeOwnerSetsModification(
                  CodeOwnerSetModification.set(CodeOwnerSet.createWithoutPathExpressions(email)))
              .build());
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class, () -> codeOwnerConfigFile.commit(metaDataUpdate));
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo(String.format("branch %s does not exist", codeOwnerConfigKey.ref()));

      // Check that the loaded code owner config is still empty.
      assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();

      // Check that the code owner config was not created in the repository.
      assertThatOptional(testCodeOwnerConfigStorage.readCodeOwnerConfig(codeOwnerConfigKey))
          .isEmpty();

      // Check that the branch was not created.
      assertThat(repo.exactRef(codeOwnerConfigKey.ref())).isNull();
    }
  }

  @Test
  public void updateCodeOwnerConfigFile() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(codeOwnerConfigKey)
            .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
            .build();
    testCodeOwnerConfigStorage.writeCodeOwnerConfig(codeOwnerConfig);

    try (Repository repo = repoManager.openRepository(project);
        MetaDataUpdate metaDataUpdate = metaDataUpdateServer.create(project)) {
      CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);

      // Check that the code owner config exists.
      assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isPresent();
      assertThatOptional(testCodeOwnerConfigStorage.readCodeOwnerConfig(codeOwnerConfigKey))
          .isPresent();

      // Remember head so that we can check that the branch is changed.
      RevCommit head = getHead(repo, codeOwnerConfigKey.ref());

      // Update the code owner config.
      codeOwnerConfigFile.setCodeOwnerConfigUpdate(
          CodeOwnerConfigUpdate.builder()
              .setIgnoreParentCodeOwners(true)
              .setCodeOwnerSetsModification(CodeOwnerSetModification.addToOnlySet(user.email()))
              .build());

      codeOwnerConfigFile.commit(metaDataUpdate);

      // Check that the loaded code owner config was updated.
      assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig())
          .value()
          .hasIgnoreParentCodeOwnersThat()
          .isTrue();
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig().get())
          .hasCodeOwnerSetsThat()
          .onlyElement()
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email(), user.email());

      // Check that the code owner config was updated in the repository.
      Optional<CodeOwnerConfig> codeOwnerConfigInRepo =
          testCodeOwnerConfigStorage.readCodeOwnerConfig(codeOwnerConfigKey);
      assertThatOptional(codeOwnerConfigInRepo).value().hasIgnoreParentCodeOwnersThat().isTrue();
      assertThatOptional(codeOwnerConfigInRepo)
          .value()
          .hasCodeOwnerSetsThat()
          .onlyElement()
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email(), user.email());

      // Check that the branch was changed.
      assertThat(head).isNotEqualTo(getHead(repo, codeOwnerConfigKey.ref()));
    }
  }

  @Test
  public void codeOwnerConfigFileIsNotUpdatedIfUpdateIsANoOp() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(codeOwnerConfigKey)
            .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
            .build();
    testCodeOwnerConfigStorage.writeCodeOwnerConfig(codeOwnerConfig);

    try (Repository repo = repoManager.openRepository(project);
        MetaDataUpdate metaDataUpdate = metaDataUpdateServer.create(project)) {
      CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);

      // Check that the code owner config exists.
      assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isPresent();
      assertThatOptional(testCodeOwnerConfigStorage.readCodeOwnerConfig(codeOwnerConfigKey))
          .isPresent();

      // Remember head so that we can check that the branch doesn't change.
      RevCommit head = getHead(repo, codeOwnerConfigKey.ref());

      // Update the code owner config without changing it.
      codeOwnerConfigFile.setCodeOwnerConfigUpdate(CodeOwnerConfigUpdate.builder().build());
      codeOwnerConfigFile.commit(metaDataUpdate);

      // Check that the loaded code owner config didn't change.
      assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig())
          .value()
          .hasCodeOwnerSetsThat()
          .onlyElement()
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email());

      // Check that the code owner config in the repository didn't change.
      Optional<CodeOwnerConfig> codeOwnerConfigInRepo =
          testCodeOwnerConfigStorage.readCodeOwnerConfig(codeOwnerConfigKey);
      assertThatOptional(codeOwnerConfigInRepo)
          .value()
          .hasCodeOwnerSetsThat()
          .onlyElement()
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email());

      // Check that the branch didn't change.
      assertThat(head).isEqualTo(getHead(repo, codeOwnerConfigKey.ref()));
    }
  }

  @Test
  public void updateCodeOwnerConfigFileSoThatItBecomesEmptyAndIsDeleted() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(codeOwnerConfigKey)
            .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
            .build();
    testCodeOwnerConfigStorage.writeCodeOwnerConfig(codeOwnerConfig);

    try (Repository repo = repoManager.openRepository(project);
        MetaDataUpdate metaDataUpdate = metaDataUpdateServer.create(project)) {
      CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);

      // Check that the code owner config exists.
      assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isPresent();
      assertThatOptional(testCodeOwnerConfigStorage.readCodeOwnerConfig(codeOwnerConfigKey))
          .isPresent();

      // Remember head so that we can check that the branch is changed.
      RevCommit head = getHead(repo, codeOwnerConfigKey.ref());

      // Update the code owner config so that it becomes empty.
      codeOwnerConfigFile.setCodeOwnerConfigUpdate(
          CodeOwnerConfigUpdate.builder()
              .setCodeOwnerSetsModification(CodeOwnerSetModification.clear())
              .build());
      codeOwnerConfigFile.commit(metaDataUpdate);

      // Check that the loaded code owner config was unset.
      assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();

      // Check that the code owner config was deleted in the repository.
      assertThatOptional(testCodeOwnerConfigStorage.readCodeOwnerConfig(codeOwnerConfigKey))
          .isEmpty();

      // Check that the branch was changed.
      assertThat(head).isNotEqualTo(getHead(repo, codeOwnerConfigKey.ref()));
    }
  }

  private CodeOwnerConfigFile loadCodeOwnerConfig(CodeOwnerConfig.Key codeOwnerConfigKey)
      throws IOException, ConfigInvalidException {
    return loadCodeOwnerConfig(codeOwnerConfigKey, null);
  }

  private CodeOwnerConfigFile loadCodeOwnerConfig(
      CodeOwnerConfig.Key codeOwnerConfigKey, @Nullable ObjectId revision)
      throws IOException, ConfigInvalidException {
    try (Repository repository = repoManager.openRepository(codeOwnerConfigKey.project())) {
      return CodeOwnerConfigFile.load(
          CODE_OWNER_CONFIG_FILE_NAME,
          CODE_OWNER_CONFIG_PARSER,
          repository,
          revision,
          codeOwnerConfigKey);
    }
  }

  private static class TestCodeOwnerConfigParser implements CodeOwnerConfigParser {
    private final Map<String, CodeOwnerConfig> codeOwnerConfigCache = new HashMap<>();

    @Override
    public CodeOwnerConfig parse(
        CodeOwnerConfig.Key codeOwnerConfigKey, String codeOwnerConfigAsString) throws IOException {
      if (codeOwnerConfigAsString.isEmpty()) {
        return CodeOwnerConfig.builder(codeOwnerConfigKey).build();
      }

      return codeOwnerConfigCache.get(codeOwnerConfigAsString);
    }

    @Override
    public String formatAsString(CodeOwnerConfig codeOwnerConfig) throws IOException {
      if (codeOwnerConfig.codeOwnerSets().isEmpty()) {
        return "";
      }

      String key = codeOwnerConfig.toString();
      codeOwnerConfigCache.put(key, codeOwnerConfig);
      return key;
    }
  }
}
