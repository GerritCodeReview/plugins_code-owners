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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations.PerCodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigUpdate;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSetModification;
import com.google.gerrit.plugins.codeowners.backend.CodeOwners;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnersUpdate;
import com.google.gerrit.plugins.codeowners.config.InvalidPluginConfigurationException;
import com.google.gerrit.server.ServerInitiated;
import com.google.inject.Key;
import com.google.inject.Provider;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerConfigOperationsImpl}. */
public class CodeOwnerConfigOperationsImplTest extends AbstractCodeOwnersTest {
  // Use specific subclass instead of depending on the interface field from the base class.
  @SuppressWarnings("hiding")
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
            .getInstance(new Key<Provider<CodeOwnersUpdate>>(ServerInitiated.class) {});
  }

  @Test
  public void getNonExistingCodeOwnerConfig() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    PerCodeOwnerConfigOperations perCodeOwnerConfigOperations =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey);
    assertThat(perCodeOwnerConfigOperations.exists()).isFalse();
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, perCodeOwnerConfigOperations::get);
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
        .hasCodeOwnerSetsThat()
        .onlyElement()
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
    assertThat(codeOwnerConfigKey.shortBranchName()).isEqualTo(branchName);
    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfigKey))
        .hasCodeOwnerSetsThat()
        .onlyElement()
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
        .hasCodeOwnerSetsThat()
        .onlyElement()
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
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email());
  }

  @Test
  public void codeOwnerConfigCanBeCreatedWithoutSpecifyingIgnoreParentCodeOwners()
      throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .addCodeOwnerEmail(admin.email())
            .create();

    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfigKey))
        .hasIgnoreParentCodeOwnersThat()
        .isFalse();
  }

  @Test
  public void specifiedIgnoreParentCodeOwnersIsRespectedForCodeOwnerConfigCreation()
      throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .ignoreParentCodeOwners()
            .create();

    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfigKey))
        .hasIgnoreParentCodeOwnersThat()
        .isTrue();
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
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email(), user.email());
  }

  @Test
  public void specifiedCodeOwnerSetsAreRespectedForCodeOwnerConfigCreation() throws Exception {
    CodeOwnerSet codeOwnerSet1 =
        CodeOwnerSet.builder()
            .addPathExpression("foo")
            .addCodeOwnerEmail(admin.email())
            .addCodeOwnerEmail(user.email())
            .build();
    CodeOwnerSet codeOwnerSet2 =
        CodeOwnerSet.builder().addPathExpression("bar").addCodeOwnerEmail(admin.email()).build();

    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .addCodeOwnerSet(codeOwnerSet1)
            .addCodeOwnerSet(codeOwnerSet2)
            .create();

    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfigKey))
        .hasCodeOwnerSetsThat()
        .containsExactly(codeOwnerSet1, codeOwnerSet2)
        .inOrder();
  }

  @Test
  public void cannotCreateEmptyCodeOwnerConfig() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> codeOwnerConfigOperations.newCodeOwnerConfig().project(project).create());
    assertThat(exception).hasMessageThat().contains("code owner config must not be empty");
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
  public void setIgnoreParentCodeOwners() throws Exception {
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerConfig(false, admin.email());
    codeOwnerConfigOperations
        .codeOwnerConfig(codeOwnerConfig.key())
        .forUpdate()
        .ignoreParentCodeOwners()
        .update();
    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfig.key()))
        .hasIgnoreParentCodeOwnersThat()
        .isTrue();
  }

  @Test
  public void unsetIgnoreParentCodeOwners() throws Exception {
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerConfig(true, admin.email());
    codeOwnerConfigOperations
        .codeOwnerConfig(codeOwnerConfig.key())
        .forUpdate()
        .ignoreParentCodeOwners(false)
        .update();
    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfig.key()))
        .hasIgnoreParentCodeOwnersThat()
        .isFalse();
  }

  @Test
  public void addCodeOwner() throws Exception {
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerConfig(admin.email());
    codeOwnerConfigOperations
        .codeOwnerConfig(codeOwnerConfig.key())
        .forUpdate()
        .codeOwnerSetsModification(CodeOwnerSetModification.addToOnlySet(user.email()))
        .update();
    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfig.key()))
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email(), user.email());
  }

  @Test
  public void removeCodeOwner() throws Exception {
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerConfig(admin.email(), user.email());
    codeOwnerConfigOperations
        .codeOwnerConfig(codeOwnerConfig.key())
        .forUpdate()
        .codeOwnerSetsModification(CodeOwnerSetModification.removeFromOnlySet(user.email()))
        .update();
    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfig.key()))
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email());
  }

  @Test
  public void clearCodeOwners_emptyCodeOwnerSetIsDropped() throws Exception {
    CodeOwnerSet codeOwnerSet1 = CodeOwnerSet.builder().addCodeOwnerEmail(admin.email()).build();
    CodeOwnerSet codeOwnerSet2 =
        CodeOwnerSet.builder().addPathExpression("foo").addCodeOwnerEmail(user.email()).build();

    // Create a code owner config that contains 2 code owner sets.
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfig(
            false, CodeOwnerSetModification.set(ImmutableList.of(codeOwnerSet1, codeOwnerSet2)));

    // Remove all code owners from the code owner set that doesn't have path expressions so that it
    // becomes empty.
    codeOwnerConfigOperations
        .codeOwnerConfig(codeOwnerConfig.key())
        .forUpdate()
        .codeOwnerSetsModification(
            originalCodeOwnerSets ->
                ImmutableList.of(
                    codeOwnerSet1.toBuilder().setCodeOwners(ImmutableSet.of()).build(),
                    codeOwnerSet2))
        .update();

    // By removing all code owners from codeOwnerSet1 it became empty, and hence it was dropped.
    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfig.key()))
        .hasCodeOwnerSetsThat()
        .containsExactly(codeOwnerSet2);
  }

  @Test
  public void clearCodeOwners_emptyCodeOwnerConfigIsDeleted() throws Exception {
    CodeOwnerSet codeOwnerSet = CodeOwnerSet.builder().addCodeOwnerEmail(admin.email()).build();

    // Create a code owner config that contains only a single code owner set.
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfig(false, CodeOwnerSetModification.set(ImmutableList.of(codeOwnerSet)));

    // Remove all code owners so that the code owner set becomes empty.
    codeOwnerConfigOperations
        .codeOwnerConfig(codeOwnerConfig.key())
        .forUpdate()
        .codeOwnerSetsModification(
            originalCodeOwnerSets ->
                ImmutableList.of(codeOwnerSet.toBuilder().setCodeOwners(ImmutableSet.of()).build()))
        .update();

    // Removing all code owners from the code owner sets made the code owner set empty so that it
    // was dropped.
    // Since this made the code owner config empty it caused a deletion of the code owner config
    // file.
    assertThat(codeOwners.get(codeOwnerConfig.key())).isEmpty();
  }

  @Test
  public void removeNonExistingCodeOwner() throws Exception {
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerConfig(admin.email());
    codeOwnerConfigOperations
        .codeOwnerConfig(codeOwnerConfig.key())
        .forUpdate()
        .codeOwnerSetsModification(CodeOwnerSetModification.removeFromOnlySet(user.email()))
        .update();
    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfig.key()))
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email());
  }

  @Test
  public void addCodeOwnerSet() throws Exception {
    CodeOwnerSet oldCodeOwnerSet = CodeOwnerSet.createWithoutPathExpressions(admin.email());
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfig(
            false, CodeOwnerSetModification.set(ImmutableList.of(oldCodeOwnerSet)));
    CodeOwnerSet newCodeOwnerSet =
        CodeOwnerSet.builder().addPathExpression("foo").addCodeOwnerEmail(user.email()).build();
    codeOwnerConfigOperations
        .codeOwnerConfig(codeOwnerConfig.key())
        .forUpdate()
        .addCodeOwnerSet(newCodeOwnerSet)
        .update();
    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfig.key()))
        .hasCodeOwnerSetsThat()
        .containsExactly(oldCodeOwnerSet, newCodeOwnerSet)
        .inOrder();
  }

  @Test
  public void removeCodeOwnerSet() throws Exception {
    CodeOwnerSet codeOwnerSet1 =
        CodeOwnerSet.builder().addPathExpression("foo").addCodeOwnerEmail(admin.email()).build();
    CodeOwnerSet codeOwnerSet2 =
        CodeOwnerSet.builder().addPathExpression("bar").addCodeOwnerEmail(user.email()).build();
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfig(
            false, CodeOwnerSetModification.set(ImmutableList.of(codeOwnerSet1, codeOwnerSet2)));
    codeOwnerConfigOperations
        .codeOwnerConfig(codeOwnerConfig.key())
        .forUpdate()
        .codeOwnerSetsModification(CodeOwnerSetModification.remove(codeOwnerSet1))
        .update();
    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfig.key()))
        .hasCodeOwnerSetsThat()
        .containsExactly(codeOwnerSet2);
  }

  @Test
  public void clearCodeOwnerSets() throws Exception {
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerConfig(admin.email(), user.email());
    codeOwnerConfigOperations
        .codeOwnerConfig(codeOwnerConfig.key())
        .forUpdate()
        .clearCodeOwnerSets()
        .update();

    // Removing all code owner sets leads to a deletion of the code owner config file.
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

  private CodeOwnerConfig getCodeOwnerConfigFromServer(CodeOwnerConfig.Key codeOwnerConfigKey)
      throws InvalidPluginConfigurationException {
    return codeOwners
        .get(codeOwnerConfigKey)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format("code owner config %s does not exist", codeOwnerConfigKey)));
  }

  private CodeOwnerConfig createArbitraryCodeOwnerConfig()
      throws InvalidPluginConfigurationException {
    return createCodeOwnerConfig(admin.email());
  }

  private CodeOwnerConfig createCodeOwnerConfig(String... emails)
      throws InvalidPluginConfigurationException {
    return createCodeOwnerConfig(false, emails);
  }

  private CodeOwnerConfig createCodeOwnerConfig(boolean ignoreParentCodeOwners, String... emails)
      throws InvalidPluginConfigurationException {
    return createCodeOwnerConfig(
        ignoreParentCodeOwners,
        CodeOwnerSetModification.set(CodeOwnerSet.createWithoutPathExpressions(emails)));
  }

  private CodeOwnerConfig createCodeOwnerConfig(
      boolean ignoreParentCodeOwners, CodeOwnerSetModification codeOwnerSetsModification)
      throws InvalidPluginConfigurationException {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfigUpdate codeOwnerConfigUpdate =
        CodeOwnerConfigUpdate.builder()
            .setIgnoreParentCodeOwners(ignoreParentCodeOwners)
            .setCodeOwnerSetsModification(codeOwnerSetsModification)
            .build();
    return codeOwnersUpdate
        .get()
        .upsertCodeOwnerConfig(codeOwnerConfigKey, codeOwnerConfigUpdate)
        .orElseThrow(() -> new IllegalArgumentException("code owner config was not created."));
  }
}
