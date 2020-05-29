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

package com.google.gerrit.plugins.codeowners.acceptance.testsuite;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations.PerCodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigUpdate;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigUpdate.CodeOwnerModification;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwners;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnersUpdate;
import com.google.gerrit.server.ServerInitiated;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerConfigOperationsImpl}. */
public class CodeOwnerConfigOperationsImplTest extends AbstractCodeOwnersTest {
  private CodeOwnerConfigOperations codeOwnerConfigOperations;

  private CodeOwners codeOwners;
  private Provider<CodeOwnersUpdate> codeOwnersUpdate;

  @Before
  public void setUp() {
    codeOwnerConfigOperations =
        plugin.getSysInjector().getInstance(CodeOwnerConfigOperationsImpl.class);
    codeOwners = plugin.getSysInjector().getInstance(CodeOwners.class);
    codeOwnersUpdate =
        plugin
            .getSysInjector()
            .getInstance(
                Key.get(new TypeLiteral<Provider<CodeOwnersUpdate>>() {}, ServerInitiated.class));
  }

  @Test
  public void getNonExistingCodeOwnerConfig() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    PerCodeOwnerConfigOperations perCodeOwnerConfigOperations =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey);
    assertThat(perCodeOwnerConfigOperations.exists()).isFalse();
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> perCodeOwnerConfigOperations.get());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("code owner config %s does not exist", codeOwnerConfigKey));
  }

  @Test
  public void getExistingCodeOwnerConfig() throws Exception {
    CodeOwnerConfig codeOwnerConfig = createArbitraryCodeOwnerConfig();
    PerCodeOwnerConfigOperations perCodeOwnerConfigOperations =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfig.key());
    assertThat(perCodeOwnerConfigOperations.exists()).isTrue();
    assertThat(perCodeOwnerConfigOperations.get()).isEqualTo(codeOwnerConfig);
  }

  @Test
  public void codeOwnerConfigCreationRequiresProject() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> codeOwnerConfigOperations.newCodeOwnerConfig().create());
    assertThat(exception).hasMessageThat().contains("project not specified");
  }

  @Test
  public void codeOwnerConfigCanBeCreatedWithoutSpecifyingBranch() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .create();
    assertThat(codeOwnerConfigKey).isEqualTo(CodeOwnerConfig.Key.create(project, "master", "/"));
    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfigKey))
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email());
  }

  @Test
  public void specifiedBranchIsARespectedForCodeOwnerConfigCreation() throws Exception {
    String branchName = "foo";
    gApi.projects().name(project.get()).branch(branchName).create(new BranchInput());

    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branchName)
            .addCodeOwnerEmail(admin.email())
            .create();
    assertThat(codeOwnerConfigKey.branchName()).isEqualTo(branchName);
    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfigKey))
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email());
  }

  @Test
  public void cannotCreateCodeOwnerConfigIfBranchDoesNotExist() throws Exception {
    String branchName = "non-existing";
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                codeOwnerConfigOperations
                    .newCodeOwnerConfig()
                    .project(project)
                    .branch(branchName)
                    .addCodeOwnerEmail(admin.email())
                    .create());
    assertThat(exception)
        .hasMessageThat()
        .contains(String.format("branch %s does not exist", RefNames.REFS_HEADS + branchName));
  }

  @Test
  public void codeOwnerConfigCanBeCreatedWithoutSpecifyingFolderPath() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .addCodeOwnerEmail(admin.email())
            .create();
    assertThat(codeOwnerConfigKey).isEqualTo(CodeOwnerConfig.Key.create(project, "master", "/"));
    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfigKey))
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email());
  }

  @Test
  public void specifiedFolderPathIsARespectedForCodeOwnerConfigCreation() throws Exception {
    String folderPath = "/foo/bar";
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .folderPath(folderPath)
            .addCodeOwnerEmail(admin.email())
            .create();
    assertThat(codeOwnerConfigKey.folderPath()).isEqualTo(Paths.get(folderPath));
    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfigKey))
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email());
  }

  @Test
  public void codeOwnerConfigCreationRequiresCodeOwners() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> codeOwnerConfigOperations.newCodeOwnerConfig().project(project).create());
    assertThat(exception).hasMessageThat().contains("code owner config must not be empty");
  }

  @Test
  public void specifiedCodeOwnersAreRespectedForCodeOwnerConfigCreation() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .addCodeOwnerEmail(admin.email())
            .addCodeOwnerEmail(user.email())
            .create();

    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfigKey))
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email(), user.email());
  }

  @Test
  public void cannotCreateCodeOwnerConfigIfItAlreadyExists() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .addCodeOwnerEmail(admin.email())
            .create();

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                codeOwnerConfigOperations
                    .newCodeOwnerConfig()
                    .project(project)
                    .addCodeOwnerEmail(user.email())
                    .create());
    assertThat(exception)
        .hasMessageThat()
        .contains(String.format("code owner config %s already exists", codeOwnerConfigKey));
  }

  @Test
  public void addCodeOwner() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfig(
            codeOwners -> ImmutableSet.of(CodeOwnerReference.create(admin.email())));
    codeOwnerConfigOperations
        .codeOwnerConfig(codeOwnerConfig.key())
        .forUpdate()
        .addCodeOwnerEmail(user.email())
        .update();
    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfig.key()))
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email(), user.email());
  }

  @Test
  public void removeCodeOwner() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfig(
            codeOwners ->
                ImmutableSet.of(
                    CodeOwnerReference.create(admin.email()),
                    CodeOwnerReference.create(user.email())));
    codeOwnerConfigOperations
        .codeOwnerConfig(codeOwnerConfig.key())
        .forUpdate()
        .removeCodeOwnerEmail(user.email())
        .update();
    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfig.key()))
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email());
  }

  @Test
  public void clearCodeOwners() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfig(
            codeOwners ->
                ImmutableSet.of(
                    CodeOwnerReference.create(admin.email()),
                    CodeOwnerReference.create(user.email())));
    codeOwnerConfigOperations
        .codeOwnerConfig(codeOwnerConfig.key())
        .forUpdate()
        .clearCodeOwners()
        .update();

    // Removing all code owners leads to a deletion of the code owner config file.
    assertThat(codeOwners.get(codeOwnerConfig.key())).isEmpty();
  }

  @Test
  public void cannotUpdateNonExistingCodeOwnerConfig() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).get());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("code owner config %s does not exist", codeOwnerConfigKey));
  }

  private CodeOwnerConfig getCodeOwnerConfigFromServer(CodeOwnerConfig.Key codeOwnerConfigKey) {
    return codeOwners
        .get(codeOwnerConfigKey)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format("code owner config %s does not exist", codeOwnerConfigKey)));
  }

  private CodeOwnerConfig createArbitraryCodeOwnerConfig() {
    return createCodeOwnerConfig(
        codeOwners -> ImmutableSet.of(CodeOwnerReference.create(admin.email())));
  }

  private CodeOwnerConfig createCodeOwnerConfig(CodeOwnerModification codeOwnerModification) {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfigUpdate codeOwnerConfigUpdate =
        CodeOwnerConfigUpdate.builder().setCodeOwnerModification(codeOwnerModification).build();
    return codeOwnersUpdate
        .get()
        .upsertCodeOwnerConfig(codeOwnerConfigKey, codeOwnerConfigUpdate)
        .orElseThrow(() -> new IllegalArgumentException("code owner config was not created."));
  }
}
