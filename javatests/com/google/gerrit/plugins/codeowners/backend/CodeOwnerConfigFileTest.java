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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.testing.backend.BackendTestUtil;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerConfigFile}. */
public class CodeOwnerConfigFileTest extends AbstractCodeOwnersTest {
  private static String CODE_OWNER_CONFIG_FILE_NAME = "CODE_OWNER_CONFIG";
  private static CodeOwnerConfigParser CODE_OWNER_CONFIG_PARSER = new TestCodeOwnerConfigParser();

  @Inject private MetaDataUpdate.Server metaDataUpdateServer;

  private BackendTestUtil backendTestUtil;
  private CodeOwnerConfigFile.Factory codeOwnerConfigFileFactory;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    backendTestUtil =
        plugin
            .getSysInjector()
            .getInstance(BackendTestUtil.Factory.class)
            .create(CODE_OWNER_CONFIG_FILE_NAME, CODE_OWNER_CONFIG_PARSER);
    codeOwnerConfigFileFactory =
        plugin.getSysInjector().getInstance(CodeOwnerConfigFile.Factory.class);
  }

  @Test
  public void loadNonExistingCodeOwnerConfigFile() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "master", "/non-existing/");
    CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);
    assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();
  }

  @Test
  public void loadCodeOwnerConfigFileFromNonExistingBranch() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "non-existing", "/");
    CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);
    assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();
  }

  @Test
  public void loadEmptyCodeOwnerConfigFile() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfig = CodeOwnerConfig.builder(codeOwnerConfigKey).build();
    backendTestUtil.writeCodeOwnerConfig(codeOwnerConfig);

    CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);
    assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isPresent();
    assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig().get()).hasCodeOwnersThat().isEmpty();
  }

  @Test
  public void loadCodeOwnerConfigFile() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(codeOwnerConfigKey).addCodeOwnerEmail(admin.email()).build();
    backendTestUtil.writeCodeOwnerConfig(codeOwnerConfig);

    CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);
    assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isPresent();
    assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig().get())
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
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();
      assertThat(backendTestUtil.readCodeOwnerConfig(codeOwnerConfigKey)).isEmpty();

      // Remember head so that we can check that the branch is changed.
      RevCommit head = getHead(repo, codeOwnerConfigKey.ref());

      // Create the code owner config.
      codeOwnerConfigFile.setCodeOwnerConfigUpdate(
          CodeOwnerConfigUpdate.builder()
              .setCodeOwnerModification(
                  codeOwners ->
                      Sets.union(
                          codeOwners, ImmutableSet.of(CodeOwnerReference.create(admin.email()))))
              .build());
      codeOwnerConfigFile.commit(metaDataUpdate);

      // Check that the loaded code owner config was updated.
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isPresent();
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig().get())
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email());

      // Check that the code owner config was created in the repository.
      Optional<CodeOwnerConfig> codeOwnerConfigInRepo =
          backendTestUtil.readCodeOwnerConfig(codeOwnerConfigKey);
      assertThat(codeOwnerConfigInRepo).isPresent();
      assertThat(codeOwnerConfigInRepo.get())
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
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();
      assertThat(backendTestUtil.readCodeOwnerConfig(codeOwnerConfigKey)).isEmpty();

      // Remember head so that we can check that the branch doesn't change.
      RevCommit head = getHead(repo, codeOwnerConfigKey.ref());

      // Try to create the code owner config without setting a code owner config update.
      codeOwnerConfigFile.commit(metaDataUpdate);

      // Check that the loaded code owner config was not created.
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();

      // Check that no code owner config was created in the repository.
      Optional<CodeOwnerConfig> codeOwnerConfigInRepo =
          backendTestUtil.readCodeOwnerConfig(codeOwnerConfigKey);
      assertThat(codeOwnerConfigInRepo).isEmpty();

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
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();
      assertThat(backendTestUtil.readCodeOwnerConfig(codeOwnerConfigKey)).isEmpty();

      // Remember head so that we can check that the branch doesn't change.
      RevCommit head = getHead(repo, codeOwnerConfigKey.ref());

      // Try to create the code owner config without specifying code owners.
      codeOwnerConfigFile.setCodeOwnerConfigUpdate(CodeOwnerConfigUpdate.builder().build());
      codeOwnerConfigFile.commit(metaDataUpdate);

      // Check that the loaded code owner config was not created.
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();

      // Check that no code owner config was created in the repository.
      Optional<CodeOwnerConfig> codeOwnerConfigInRepo =
          backendTestUtil.readCodeOwnerConfig(codeOwnerConfigKey);
      assertThat(codeOwnerConfigInRepo).isEmpty();

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
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();
      assertThat(backendTestUtil.readCodeOwnerConfig(codeOwnerConfigKey)).isEmpty();

      // Create the code owner config.
      String email = "admin@example.com";
      codeOwnerConfigFile.setCodeOwnerConfigUpdate(
          CodeOwnerConfigUpdate.builder()
              .setCodeOwnerModification(
                  codeOwners ->
                      Sets.union(codeOwners, ImmutableSet.of(CodeOwnerReference.create(email))))
              .build());
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class, () -> codeOwnerConfigFile.commit(metaDataUpdate));
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo(String.format("branch %s does not exist", codeOwnerConfigKey.ref()));

      // Check that the loaded code owner config is still empty.
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();

      // Check that the code owner config was not created in the repository.
      assertThat(backendTestUtil.readCodeOwnerConfig(codeOwnerConfigKey)).isEmpty();

      // Check that the branch was not created.
      assertThat(repo.exactRef(codeOwnerConfigKey.ref())).isNull();
    }
  }

  @Test
  public void updateCodeOwnerConfigFile() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(codeOwnerConfigKey).addCodeOwnerEmail(admin.email()).build();
    backendTestUtil.writeCodeOwnerConfig(codeOwnerConfig);

    try (Repository repo = repoManager.openRepository(project);
        MetaDataUpdate metaDataUpdate = metaDataUpdateServer.create(project)) {
      CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);

      // Check that the code owner config exists.
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isPresent();
      assertThat(backendTestUtil.readCodeOwnerConfig(codeOwnerConfigKey)).isPresent();

      // Remember head so that we can check that the branch is changed.
      RevCommit head = getHead(repo, codeOwnerConfigKey.ref());

      // Update the code owner config.
      codeOwnerConfigFile.setCodeOwnerConfigUpdate(
          CodeOwnerConfigUpdate.builder()
              .setCodeOwnerModification(
                  codeOwners ->
                      Sets.union(
                          codeOwners, ImmutableSet.of(CodeOwnerReference.create(user.email()))))
              .build());
      codeOwnerConfigFile.commit(metaDataUpdate);

      // Check that the loaded code owner config was updated.
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isPresent();
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig().get())
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email(), user.email());

      // Check that the code owner config was updated in the repository.
      Optional<CodeOwnerConfig> codeOwnerConfigInRepo =
          backendTestUtil.readCodeOwnerConfig(codeOwnerConfigKey);
      assertThat(codeOwnerConfigInRepo).isPresent();
      assertThat(codeOwnerConfigInRepo.get())
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
        CodeOwnerConfig.builder(codeOwnerConfigKey).addCodeOwnerEmail(admin.email()).build();
    backendTestUtil.writeCodeOwnerConfig(codeOwnerConfig);

    try (Repository repo = repoManager.openRepository(project);
        MetaDataUpdate metaDataUpdate = metaDataUpdateServer.create(project)) {
      CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);

      // Check that the code owner config exists.
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isPresent();
      assertThat(backendTestUtil.readCodeOwnerConfig(codeOwnerConfigKey)).isPresent();

      // Remember head so that we can check that the branch doesn't change.
      RevCommit head = getHead(repo, codeOwnerConfigKey.ref());

      // Update the code owner config without changing it.
      codeOwnerConfigFile.setCodeOwnerConfigUpdate(CodeOwnerConfigUpdate.builder().build());
      codeOwnerConfigFile.commit(metaDataUpdate);

      // Check that the loaded code owner config didn't change.
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isPresent();
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig().get())
          .hasCodeOwnersEmailsThat()
          .containsExactly(admin.email());

      // Check that the code owner config in the repository didn't change.
      Optional<CodeOwnerConfig> codeOwnerConfigInRepo =
          backendTestUtil.readCodeOwnerConfig(codeOwnerConfigKey);
      assertThat(codeOwnerConfigInRepo).isPresent();
      assertThat(codeOwnerConfigInRepo.get())
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
        CodeOwnerConfig.builder(codeOwnerConfigKey).addCodeOwnerEmail(admin.email()).build();
    backendTestUtil.writeCodeOwnerConfig(codeOwnerConfig);

    try (Repository repo = repoManager.openRepository(project);
        MetaDataUpdate metaDataUpdate = metaDataUpdateServer.create(project)) {
      CodeOwnerConfigFile codeOwnerConfigFile = loadCodeOwnerConfig(codeOwnerConfigKey);

      // Check that the code owner config exists.
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isPresent();
      assertThat(backendTestUtil.readCodeOwnerConfig(codeOwnerConfigKey)).isPresent();

      // Remember head so that we can check that the branch is changed.
      RevCommit head = getHead(repo, codeOwnerConfigKey.ref());

      // Update the code owner config so that it becomes empty.
      codeOwnerConfigFile.setCodeOwnerConfigUpdate(
          CodeOwnerConfigUpdate.builder()
              .setCodeOwnerModification(codeOwners -> ImmutableSet.of())
              .build());
      codeOwnerConfigFile.commit(metaDataUpdate);

      // Check that the loaded code owner config was unset.
      assertThat(codeOwnerConfigFile.getLoadedCodeOwnerConfig()).isEmpty();

      // Check that the code owner config was deleted in the repository.
      assertThat(backendTestUtil.readCodeOwnerConfig(codeOwnerConfigKey)).isEmpty();

      // Check that the branch was changed.
      assertThat(head).isNotEqualTo(getHead(repo, codeOwnerConfigKey.ref()));
    }
  }

  private CodeOwnerConfigFile loadCodeOwnerConfig(CodeOwnerConfig.Key codeOwnerConfigKey)
      throws IOException, ConfigInvalidException {
    return codeOwnerConfigFileFactory.load(
        CODE_OWNER_CONFIG_FILE_NAME, CODE_OWNER_CONFIG_PARSER, codeOwnerConfigKey);
  }

  private static class TestCodeOwnerConfigParser implements CodeOwnerConfigParser {
    @Override
    public CodeOwnerConfig parse(
        CodeOwnerConfig.Key codeOwnerConfigKey, String codeOwnerConfigAsString) throws IOException {
      if (codeOwnerConfigAsString.isEmpty()) {
        return CodeOwnerConfig.builder(codeOwnerConfigKey).build();
      }

      try (ByteArrayInputStream byteArrayInputStream =
              new ByteArrayInputStream(Base64.getDecoder().decode(codeOwnerConfigAsString));
          ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)) {
        @SuppressWarnings("unchecked")
        ImmutableList<String> codeOwnerEmails =
            (ImmutableList<String>) objectInputStream.readObject();
        return CodeOwnerConfig.builder(codeOwnerConfigKey)
            .setCodeOwners(
                codeOwnerEmails.stream().map(CodeOwnerReference::create).collect(toImmutableSet()))
            .build();
      } catch (ClassNotFoundException e) {
        throw new IOException(e);
      }
    }

    @Override
    public String formatAsString(CodeOwnerConfig codeOwnerConfig) throws IOException {
      if (codeOwnerConfig.codeOwners().isEmpty()) {
        return "";
      }

      try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
          ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
        objectOutputStream.writeObject(
            codeOwnerConfig.codeOwners().stream()
                .map(CodeOwnerReference::email)
                .sorted()
                .collect(toImmutableList()));
        objectOutputStream.flush();
        byteArrayOutputStream.flush();
        return Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
      }
    }
  }
}
