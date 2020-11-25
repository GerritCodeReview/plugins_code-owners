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
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth8;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations.PerCodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportMode;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportModification;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigUpdate;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSetModification;
import com.google.gerrit.plugins.codeowners.backend.CodeOwners;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnersUpdate;
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
  public void specifiedBranchIsRespectedForCodeOwnerConfigCreation() throws Exception {
    String branchName = "foo";
    createBranch(BranchNameKey.create(project, branchName));

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
  public void specifiedFolderPathIsRespectedForCodeOwnerConfigCreation() throws Exception {
    String folderPath = "/foo/bar";
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .folderPath(folderPath)
            .addCodeOwnerEmail(admin.email())
            .create();
    Truth8.assertThat(codeOwnerConfigKey.folderPath()).isEqualTo(Paths.get(folderPath));
    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfigKey))
        .hasCodeOwnerSetsThat()
        .onlyElement()
        .hasCodeOwnersEmailsThat()
        .containsExactly(admin.email());
  }

  @Test
  public void specifiedFileNameIsRespectedForCodeOwnerConfigCreation() throws Exception {
    String folderPath = "/foo/bar";
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .folderPath(folderPath)
            .fileName("OWNERS_foo")
            .addCodeOwnerEmail(admin.email())
            .create();
    assertThat(codeOwnerConfigKey.fileName()).value().isEqualTo("OWNERS_foo");
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
  public void specifiedImportsAreRespectedForCodeOwnerConfigCreation() throws Exception {
    CodeOwnerConfigReference import1 =
        CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/foo/OWNERS");
    CodeOwnerConfigReference import2 =
        CodeOwnerConfigReference.builder(
                CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/bar/OWNERS")
            .setProject(allProjects)
            .build();

    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .addImport(import1)
            .addImport(import2)
            .create();

    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfigKey))
        .hasImportsThat()
        .containsExactly(import1, import2)
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
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfig(/* ignoreParentCodeOwners= */ false, admin.email());
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
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfig(/* ignoreParentCodeOwners= */ true, admin.email());
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
        createCodeOwnerConfig(
            /* ignoreParentCodeOwners= */ false,
            CodeOwnerSetModification.set(ImmutableList.of(codeOwnerSet)));

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
    assertThat(codeOwners.getFromCurrentRevision(codeOwnerConfig.key())).isEmpty();
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
    assertThat(codeOwners.getFromCurrentRevision(codeOwnerConfig.key())).isEmpty();
  }

  @Test
  public void addImport() throws Exception {
    CodeOwnerConfigReference oldImport =
        CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/foo/OWNERS");
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfig(
            false,
            CodeOwnerSetModification.keep(),
            CodeOwnerConfigImportModification.set(ImmutableList.of(oldImport)));
    CodeOwnerConfigReference newImport =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/bar/OWNERS");
    codeOwnerConfigOperations
        .codeOwnerConfig(codeOwnerConfig.key())
        .forUpdate()
        .addImport(newImport)
        .update();
    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfig.key()))
        .hasImportsThat()
        .containsExactly(oldImport, newImport)
        .inOrder();
  }

  @Test
  public void removeImport() throws Exception {
    CodeOwnerConfigReference import1 =
        CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/foo/OWNERS");
    CodeOwnerConfigReference import2 =
        CodeOwnerConfigReference.create(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, "/bar/OWNERS");
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfig(
            false,
            CodeOwnerSetModification.keep(),
            CodeOwnerConfigImportModification.set(ImmutableList.of(import1, import2)));
    codeOwnerConfigOperations
        .codeOwnerConfig(codeOwnerConfig.key())
        .forUpdate()
        .importsModification(CodeOwnerConfigImportModification.remove(import1))
        .update();
    assertThat(getCodeOwnerConfigFromServer(codeOwnerConfig.key()))
        .hasImportsThat()
        .containsExactly(import2);
  }

  @Test
  public void clearImports() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerConfig(
            false,
            CodeOwnerSetModification.keep(),
            CodeOwnerConfigImportModification.set(
                CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/foo/OWNERS")));
    codeOwnerConfigOperations
        .codeOwnerConfig(codeOwnerConfig.key())
        .forUpdate()
        .clearImports()
        .update();

    // Removing all code owner sets leads to a deletion of the code owner config file.
    assertThat(codeOwners.getFromCurrentRevision(codeOwnerConfig.key())).isEmpty();
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

  @Test
  public void getJGitFilePath() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addCodeOwnerEmail(admin.email())
            .create();
    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath())
        .isEqualTo("foo/OWNERS");
  }

  @Test
  public void getFilePath() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addCodeOwnerEmail(admin.email())
            .create();
    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath())
        .isEqualTo("/foo/OWNERS");
  }

  @Test
  public void getJGitFilePath_nonExistingCodeOwnerConfig() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/foo/");
    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath())
        .isEqualTo("foo/OWNERS");
  }

  @Test
  public void getFilePath_nonExistingCodeOwnerConfig() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/foo/");
    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath())
        .isEqualTo("/foo/OWNERS");
  }

  @Test
  public void getJGitFilePath_nonExistingProject() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(Project.nameKey("non-existing"), "master", "/foo/");
    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getJGitFilePath())
        .isEqualTo("foo/OWNERS");
  }

  @Test
  public void getFilePath_nonExistingProject() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(Project.nameKey("non-existing"), "master", "/foo/");
    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath())
        .isEqualTo("/foo/OWNERS");
  }

  @Test
  public void getContent() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .addCodeOwnerEmail(admin.email())
            .create();
    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getContent())
        .isEqualTo(admin.email() + "\n");
  }

  @Test
  public void getContent_nonExistingCodeOwnerConfig() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/foo/");
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getContent());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("code owner config %s does not exist", codeOwnerConfigKey));
  }

  @Test
  public void getContent_nonExistingBranch() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "non-existing", "/foo/");
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getContent());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("code owner config %s does not exist", codeOwnerConfigKey));
  }

  @Test
  public void getContent_nonExistingProject() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(Project.nameKey("non-existing"), "master", "/foo/");
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getContent());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("code owner config %s does not exist", codeOwnerConfigKey));
  }

  private CodeOwnerConfig getCodeOwnerConfigFromServer(CodeOwnerConfig.Key codeOwnerConfigKey) {
    return codeOwners
        .getFromCurrentRevision(codeOwnerConfigKey)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format("code owner config %s does not exist", codeOwnerConfigKey)));
  }

  private CodeOwnerConfig createArbitraryCodeOwnerConfig() {
    return createCodeOwnerConfig(admin.email());
  }

  private CodeOwnerConfig createCodeOwnerConfig(String... emails) {
    return createCodeOwnerConfig(/* ignoreParentCodeOwners= */ false, emails);
  }

  private CodeOwnerConfig createCodeOwnerConfig(boolean ignoreParentCodeOwners, String... emails) {
    return createCodeOwnerConfig(
        ignoreParentCodeOwners,
        CodeOwnerSetModification.set(CodeOwnerSet.createWithoutPathExpressions(emails)));
  }

  private CodeOwnerConfig createCodeOwnerConfig(
      boolean ignoreParentCodeOwners, CodeOwnerSetModification codeOwnerSetsModification) {
    return createCodeOwnerConfig(
        ignoreParentCodeOwners,
        codeOwnerSetsModification,
        CodeOwnerConfigImportModification.keep());
  }

  private CodeOwnerConfig createCodeOwnerConfig(
      boolean ignoreParentCodeOwners,
      CodeOwnerSetModification codeOwnerSetsModification,
      CodeOwnerConfigImportModification codeOwnerImportModification) {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfigUpdate codeOwnerConfigUpdate =
        CodeOwnerConfigUpdate.builder()
            .setIgnoreParentCodeOwners(ignoreParentCodeOwners)
            .setCodeOwnerSetsModification(codeOwnerSetsModification)
            .setImportsModification(codeOwnerImportModification)
            .build();
    return codeOwnersUpdate
        .get()
        .upsertCodeOwnerConfig(codeOwnerConfigKey, codeOwnerConfigUpdate)
        .orElseThrow(() -> new IllegalArgumentException("code owner config was not created."));
  }
}
