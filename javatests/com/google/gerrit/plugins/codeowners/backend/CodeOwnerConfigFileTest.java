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

import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigSubject;
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
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerConfigFile}. */
public class CodeOwnerConfigFileTest extends AbstractCodeOwnersTest {
  private static final String CODE_OWNER_CONFIG_FILE_NAME = "CODE_OWNER_CONFIG";
  private static final CodeOwnerConfigParser CODE_OWNER_CONFIG_PARSER =
      new TestCodeOwnerConfigParser();

  @Inject private ProjectOperations projectOperations;
  @Inject private MetaDataUpdate.Server metaDataUpdateServer;

  private CodeOwnerConfigFile.Factory codeOwnerConfigFileFactory;
  private TestCodeOwnerConfigStorage testCodeOwnerConfigStorage;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerConfigFileFactory =
        plugin.getSysInjector().getInstance(CodeOwnerConfigFile.Factory.class);
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
    testCodeOwnerConfigStorage.writeCodeOwnerConfig(codeOwnerConfigKey, b -> {});

    CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);
    CodeOwnerConfigSubject codeOwnerConfigSubject =
        assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).value();
    codeOwnerConfigSubject.hasCodeOwnerSetsThat().isEmpty();
    codeOwnerConfigSubject
        .hasRevisionThat()
        .isEqualTo(projectOperations.project(project).getHead("master"));
  }

  @Test
  public void loadCodeOwnerConfigFile() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    testCodeOwnerConfigStorage.writeCodeOwnerConfig(
        codeOwnerConfigKey,
        b ->
            b.setIgnoreParentCodeOwners()
                .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email())));

    CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);
    CodeOwnerConfigSubject codeOwnerConfigSubject =
        assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).value();
    codeOwnerConfigSubject.hasIgnoreParentCodeOwnersThat().isTrue();
    codeOwnerConfigSubject
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email());
    codeOwnerConfigSubject
        .hasRevisionThat()
        .isEqualTo(projectOperations.project(project).getHead("master"));
  }

  @Test
  public void loadCodeOwnerConfigFileFromOldRevision() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    ObjectId revision1 =
        testCodeOwnerConfigStorage
            .writeCodeOwnerConfig(
                codeOwnerConfigKey,
                b ->
                    b.setIgnoreParentCodeOwners()
                        .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email())))
            .revision();

    // update the code owner config, which creates a new revision
    ObjectId revision2 =
        testCodeOwnerConfigStorage
            .writeCodeOwnerConfig(codeOwnerConfigKey, b -> b.setIgnoreParentCodeOwners(false))
            .revision();
    assertThat(revision1).isNotEqualTo(revision2);

    CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey, revision1);
    CodeOwnerConfigSubject codeOwnerConfigSubject =
        assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).value();
    codeOwnerConfigSubject.hasIgnoreParentCodeOwnersThat().isTrue();
    codeOwnerConfigSubject.hasRevisionThat().isEqualTo(revision1);
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
  public void loadCodeOwnerConfigFileWithCustomFileName() throws Exception {
    String customFileName = "FOO_CODE_OWNERS";
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "master", "/", customFileName);
    testCodeOwnerConfigStorage.writeCodeOwnerConfig(
        codeOwnerConfigKey,
        b ->
            b.setIgnoreParentCodeOwners()
                .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email())));

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
      RevCommit oldHead = getHead(repo, codeOwnerConfigKey.ref());

      // Create the code owner config.
      codeOwnerConfigFile.setCodeOwnerConfigUpdate(
          CodeOwnerConfigUpdate.builder()
              .setIgnoreParentCodeOwners(true)
              .setCodeOwnerSetsModification(
                  CodeOwnerSetModification.set(
                      CodeOwnerSet.createWithoutPathExpressions(admin.email())))
              .build());
      codeOwnerConfigFile.commit(metaDataUpdate);

      RevCommit newHead = getHead(repo, codeOwnerConfigKey.ref());

      // Check that the loaded code owner config was updated.
      CodeOwnerConfigSubject loadedCodeOwnerConfigSubject =
          assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).value();
      loadedCodeOwnerConfigSubject.hasIgnoreParentCodeOwnersThat().isTrue();
      loadedCodeOwnerConfigSubject
          .hasCodeOwnerSetsThat()
          .onlyElement()
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email());
      loadedCodeOwnerConfigSubject.hasRevisionThat().isEqualTo(newHead);

      // Check that the code owner config was created in the repository.
      Optional<CodeOwnerConfig> codeOwnerConfigInRepo =
          testCodeOwnerConfigStorage.readCodeOwnerConfig(codeOwnerConfigKey);
      CodeOwnerConfigSubject codeOwnerConfigInRepoSubject =
          assertThatOptional(codeOwnerConfigInRepo).value();
      codeOwnerConfigInRepoSubject.hasIgnoreParentCodeOwnersThat().isTrue();
      codeOwnerConfigInRepoSubject
          .hasCodeOwnerSetsThat()
          .onlyElement()
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email());

      // Check that the branch was changed.
      assertThat(oldHead).isNotEqualTo(newHead);
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
  public void createCodeOwnerConfigFileWithCustomName() throws Exception {
    String customFileName = "FOO_CODE_OWNERS";
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "master", "/foo/bar/", customFileName);
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
  public void updateCodeOwnerConfigFile() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    testCodeOwnerConfigStorage.writeCodeOwnerConfig(
        codeOwnerConfigKey,
        b -> b.addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email())));

    try (Repository repo = repoManager.openRepository(project);
        MetaDataUpdate metaDataUpdate = metaDataUpdateServer.create(project)) {
      CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);

      // Check that the code owner config exists.
      assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isPresent();
      assertThatOptional(testCodeOwnerConfigStorage.readCodeOwnerConfig(codeOwnerConfigKey))
          .isPresent();

      // Remember head so that we can check that the branch is changed.
      RevCommit oldHead = getHead(repo, codeOwnerConfigKey.ref());

      // Update the code owner config.
      codeOwnerConfigFile.setCodeOwnerConfigUpdate(
          CodeOwnerConfigUpdate.builder()
              .setIgnoreParentCodeOwners(true)
              .setCodeOwnerSetsModification(CodeOwnerSetModification.addToOnlySet(user.email()))
              .build());

      codeOwnerConfigFile.commit(metaDataUpdate);

      RevCommit newHead = getHead(repo, codeOwnerConfigKey.ref());

      // Check that the loaded code owner config was updated.
      CodeOwnerConfigSubject loadedCodeOwnerConfigSubject =
          assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).value();
      loadedCodeOwnerConfigSubject.hasIgnoreParentCodeOwnersThat().isTrue();
      loadedCodeOwnerConfigSubject
          .hasCodeOwnerSetsThat()
          .onlyElement()
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email(), user.email());
      loadedCodeOwnerConfigSubject.hasRevisionThat().isEqualTo(newHead);

      // Check that the code owner config was updated in the repository.
      Optional<CodeOwnerConfig> codeOwnerConfigInRepo =
          testCodeOwnerConfigStorage.readCodeOwnerConfig(codeOwnerConfigKey);
      CodeOwnerConfigSubject codeOwnerConfigInRepoSubject =
          assertThatOptional(codeOwnerConfigInRepo).value();
      codeOwnerConfigInRepoSubject.hasIgnoreParentCodeOwnersThat().isTrue();
      codeOwnerConfigInRepoSubject
          .hasCodeOwnerSetsThat()
          .onlyElement()
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email(), user.email());

      // Check that the branch was changed.
      assertThat(oldHead).isNotEqualTo(newHead);
    }
  }

  @Test
  public void codeOwnerConfigFileIsNotUpdatedIfUpdateIsANoOp() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    testCodeOwnerConfigStorage.writeCodeOwnerConfig(
        codeOwnerConfigKey,
        b -> b.addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email())));

    try (Repository repo = repoManager.openRepository(project);
        MetaDataUpdate metaDataUpdate = metaDataUpdateServer.create(project)) {
      CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);

      // Check that the code owner config exists.
      assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isPresent();
      assertThatOptional(testCodeOwnerConfigStorage.readCodeOwnerConfig(codeOwnerConfigKey))
          .isPresent();

      // Remember head so that we can check that the branch doesn't change.
      RevCommit oldHead = getHead(repo, codeOwnerConfigKey.ref());

      // Update the code owner config without changing it.
      codeOwnerConfigFile.setCodeOwnerConfigUpdate(CodeOwnerConfigUpdate.builder().build());
      codeOwnerConfigFile.commit(metaDataUpdate);

      RevCommit newHead = getHead(repo, codeOwnerConfigKey.ref());

      // Check that the loaded code owner config didn't change.
      CodeOwnerConfigSubject loadedCodeOwnerConfigSubject =
          assertThatOptional(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).value();
      loadedCodeOwnerConfigSubject
          .hasCodeOwnerSetsThat()
          .onlyElement()
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email());
      loadedCodeOwnerConfigSubject.hasRevisionThat().isEqualTo(newHead);

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
      assertThat(oldHead).isEqualTo(newHead);
    }
  }

  @Test
  public void updateCodeOwnerConfigFileSoThatItBecomesEmptyAndIsDeleted() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    testCodeOwnerConfigStorage.writeCodeOwnerConfig(
        codeOwnerConfigKey,
        b -> b.addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email())));

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
    try (Repository repository = repoManager.openRepository(codeOwnerConfigKey.project())) {
      return codeOwnerConfigFileFactory.loadCurrent(
          CODE_OWNER_CONFIG_FILE_NAME, CODE_OWNER_CONFIG_PARSER, repository, codeOwnerConfigKey);
    }
  }

  private CodeOwnerConfigFile loadCodeOwnerConfig(
      CodeOwnerConfig.Key codeOwnerConfigKey, ObjectId revision)
      throws IOException, ConfigInvalidException {
    try (Repository repository = repoManager.openRepository(codeOwnerConfigKey.project());
        RevWalk revWalk = new RevWalk(repository)) {
      return codeOwnerConfigFileFactory.load(
          CODE_OWNER_CONFIG_FILE_NAME,
          CODE_OWNER_CONFIG_PARSER,
          revWalk,
          revision,
          codeOwnerConfigKey);
    }
  }

  private static class TestCodeOwnerConfigParser implements CodeOwnerConfigParser {
    private final Map<String, CodeOwnerConfig> codeOwnerConfigCache = new HashMap<>();

    @Override
    public CodeOwnerConfig parse(
        ObjectId revision, CodeOwnerConfig.Key codeOwnerConfigKey, String codeOwnerConfigAsString)
        throws IOException {
      if (codeOwnerConfigAsString.isEmpty()) {
        return CodeOwnerConfig.builder(codeOwnerConfigKey, revision).build();
      }

      return codeOwnerConfigCache
          .get(codeOwnerConfigAsString)
          .toBuilder()
          .setRevision(revision)
          .build();
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
