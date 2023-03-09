// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.plugins.codeowners.restapi;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerConfigFileInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImport;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportMode;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerConfigFileJson}. */
public class CodeOwnerConfigFileJsonIT extends AbstractCodeOwnersIT {
  private CodeOwnerConfigFileJson CodeOwnerConfigFileJson;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    CodeOwnerConfigFileJson = plugin.getSysInjector().getInstance(CodeOwnerConfigFileJson.class);
  }

  @Test
  public void cannotFormatWithNullCodeOwnerConfigKey() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                CodeOwnerConfigFileJson.format(
                    /* codeOwnerConfigKey= */ null,
                    /* resolvedImports= */ ImmutableList.of(),
                    /* unresolvedImports= */ ImmutableList.of()));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfigKey");
  }

  @Test
  public void cannotFormatWithNullResolvedImports() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(Project.nameKey("project"), "master", "/");
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                CodeOwnerConfigFileJson.format(
                    codeOwnerConfigKey,
                    /* resolvedImports= */ null,
                    /* unresolvedImports= */ ImmutableList.of()));
    assertThat(npe).hasMessageThat().isEqualTo("resolvedImports");
  }

  @Test
  public void cannotFormatWithNullUnresolvedImports() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(Project.nameKey("project"), "master", "/");
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                CodeOwnerConfigFileJson.format(
                    codeOwnerConfigKey,
                    /* resolvedImports= */ ImmutableList.of(),
                    /* unresolvedImports= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("unresolvedImports");
  }

  @Test
  public void formatWithoutImports() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/baz/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfigFileInfo codeOwnerConfigFileInfo =
        CodeOwnerConfigFileJson.format(
            codeOwnerConfigKey,
            /* resolvedImports= */ ImmutableList.of(),
            /* unresolvedImports= */ ImmutableList.of());
    assertThat(codeOwnerConfigFileInfo.project).isEqualTo(codeOwnerConfigKey.project().get());
    assertThat(codeOwnerConfigFileInfo.branch)
        .isEqualTo(codeOwnerConfigKey.branchNameKey().branch());
    assertThat(codeOwnerConfigFileInfo.path)
        .isEqualTo(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath());
    assertThat(codeOwnerConfigFileInfo.importMode).isNull();
    assertThat(codeOwnerConfigFileInfo.imports).isNull();
    assertThat(codeOwnerConfigFileInfo.unresolvedImports).isNull();
    assertThat(codeOwnerConfigFileInfo.unresolvedErrorMessage).isNull();
  }

  @Test
  public void formatWithUnresolvedImports() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        CodeOwnerConfig.Key.create(project, "stable", "/foo/baz/");
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig);

    CodeOwnerConfigFileInfo codeOwnerConfigFileInfo =
        CodeOwnerConfigFileJson.format(
            codeOwnerConfigKey,
            /* resolvedImports= */ ImmutableList.of(),
            /* unresolvedImports= */ ImmutableList.of(
                CodeOwnerConfigImport.createUnresolvedImport(
                    codeOwnerConfigKey,
                    keyOfImportedCodeOwnerConfig,
                    codeOwnerConfigReference,
                    "error message")));
    assertThat(codeOwnerConfigFileInfo.project).isEqualTo(codeOwnerConfigKey.project().get());
    assertThat(codeOwnerConfigFileInfo.branch)
        .isEqualTo(codeOwnerConfigKey.branchNameKey().branch());
    assertThat(codeOwnerConfigFileInfo.path)
        .isEqualTo(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath());
    assertThat(codeOwnerConfigFileInfo.importMode).isNull();
    assertThat(codeOwnerConfigFileInfo.imports).isNull();
    assertThat(codeOwnerConfigFileInfo.unresolvedErrorMessage).isNull();

    assertThat(codeOwnerConfigFileInfo.unresolvedImports).hasSize(1);
    CodeOwnerConfigFileInfo unresolvedImportInfo =
        Iterables.getOnlyElement(codeOwnerConfigFileInfo.unresolvedImports);
    assertThat(unresolvedImportInfo.project)
        .isEqualTo(keyOfImportedCodeOwnerConfig.project().get());
    assertThat(unresolvedImportInfo.branch)
        .isEqualTo(keyOfImportedCodeOwnerConfig.branchNameKey().branch());
    assertThat(unresolvedImportInfo.path)
        .isEqualTo(
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).getFilePath());
    assertThat(unresolvedImportInfo.importMode).isEqualTo(codeOwnerConfigReference.importMode());
    assertThat(unresolvedImportInfo.imports).isNull();
    assertThat(unresolvedImportInfo.unresolvedImports).isNull();
    assertThat(unresolvedImportInfo.unresolvedErrorMessage).isEqualTo("error message");
  }

  @Test
  public void formatWithImports() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        CodeOwnerConfig.Key.create(project, "stable", "/foo/baz/");
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig);

    CodeOwnerConfigFileInfo codeOwnerConfigFileInfo =
        CodeOwnerConfigFileJson.format(
            codeOwnerConfigKey,
            /* resolvedImports= */ ImmutableList.of(
                CodeOwnerConfigImport.createResolvedImport(
                    codeOwnerConfigKey, keyOfImportedCodeOwnerConfig, codeOwnerConfigReference)),
            /* unresolvedImports= */ ImmutableList.of());
    assertThat(codeOwnerConfigFileInfo.project).isEqualTo(codeOwnerConfigKey.project().get());
    assertThat(codeOwnerConfigFileInfo.branch)
        .isEqualTo(codeOwnerConfigKey.branchNameKey().branch());
    assertThat(codeOwnerConfigFileInfo.path)
        .isEqualTo(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath());
    assertThat(codeOwnerConfigFileInfo.importMode).isNull();
    assertThat(codeOwnerConfigFileInfo.unresolvedImports).isNull();
    assertThat(codeOwnerConfigFileInfo.unresolvedErrorMessage).isNull();

    assertThat(codeOwnerConfigFileInfo.imports).hasSize(1);
    CodeOwnerConfigFileInfo resolvedImportInfo =
        Iterables.getOnlyElement(codeOwnerConfigFileInfo.imports);
    assertThat(resolvedImportInfo.project).isEqualTo(keyOfImportedCodeOwnerConfig.project().get());
    assertThat(resolvedImportInfo.branch)
        .isEqualTo(keyOfImportedCodeOwnerConfig.branchNameKey().branch());
    assertThat(resolvedImportInfo.path)
        .isEqualTo(
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).getFilePath());
    assertThat(resolvedImportInfo.importMode).isEqualTo(codeOwnerConfigReference.importMode());
    assertThat(resolvedImportInfo.imports).isNull();
    assertThat(resolvedImportInfo.unresolvedImports).isNull();
    assertThat(resolvedImportInfo.unresolvedErrorMessage).isNull();
  }

  @Test
  public void formatWithNestedImports() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/baz")
            .addCodeOwnerEmail(admin.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY, keyOfImportedCodeOwnerConfig);

    CodeOwnerConfig.Key keyOfNestedImportedCodeOwnerConfig1 =
        CodeOwnerConfig.Key.create(project, "stable", "/foo/baz1/");
    CodeOwnerConfigReference nestedCodeOwnerConfigReference1 =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY,
            keyOfNestedImportedCodeOwnerConfig1);

    CodeOwnerConfig.Key keyOfNestedImportedCodeOwnerConfig2 =
        CodeOwnerConfig.Key.create(project, "stable", "/foo/baz2/");
    CodeOwnerConfigReference nestedCodeOwnerConfigReference2 =
        createCodeOwnerConfigReference(
            CodeOwnerConfigImportMode.ALL, keyOfNestedImportedCodeOwnerConfig2);

    CodeOwnerConfigFileInfo codeOwnerConfigFileInfo =
        CodeOwnerConfigFileJson.format(
            codeOwnerConfigKey,
            /* resolvedImports= */ ImmutableList.of(
                CodeOwnerConfigImport.createResolvedImport(
                    codeOwnerConfigKey, keyOfImportedCodeOwnerConfig, codeOwnerConfigReference),
                CodeOwnerConfigImport.createResolvedImport(
                    keyOfImportedCodeOwnerConfig,
                    keyOfNestedImportedCodeOwnerConfig1,
                    nestedCodeOwnerConfigReference1)),
            /* unresolvedImports= */ ImmutableList.of(
                CodeOwnerConfigImport.createUnresolvedImport(
                    keyOfImportedCodeOwnerConfig,
                    keyOfNestedImportedCodeOwnerConfig2,
                    nestedCodeOwnerConfigReference2,
                    "error message")));
    assertThat(codeOwnerConfigFileInfo.project).isEqualTo(codeOwnerConfigKey.project().get());
    assertThat(codeOwnerConfigFileInfo.branch)
        .isEqualTo(codeOwnerConfigKey.branchNameKey().branch());
    assertThat(codeOwnerConfigFileInfo.path)
        .isEqualTo(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath());
    assertThat(codeOwnerConfigFileInfo.importMode).isNull();
    assertThat(codeOwnerConfigFileInfo.unresolvedImports).isNull();
    assertThat(codeOwnerConfigFileInfo.unresolvedErrorMessage).isNull();

    assertThat(codeOwnerConfigFileInfo.imports).hasSize(1);
    CodeOwnerConfigFileInfo resolvedImportInfo =
        Iterables.getOnlyElement(codeOwnerConfigFileInfo.imports);
    assertThat(resolvedImportInfo.project).isEqualTo(keyOfImportedCodeOwnerConfig.project().get());
    assertThat(resolvedImportInfo.branch)
        .isEqualTo(keyOfImportedCodeOwnerConfig.branchNameKey().branch());
    assertThat(resolvedImportInfo.path)
        .isEqualTo(
            codeOwnerConfigOperations.codeOwnerConfig(keyOfImportedCodeOwnerConfig).getFilePath());
    assertThat(resolvedImportInfo.importMode).isEqualTo(codeOwnerConfigReference.importMode());
    assertThat(resolvedImportInfo.unresolvedErrorMessage).isNull();

    assertThat(resolvedImportInfo.imports).hasSize(1);
    CodeOwnerConfigFileInfo nestedResolvedImportInfo =
        Iterables.getOnlyElement(resolvedImportInfo.imports);
    assertThat(nestedResolvedImportInfo.project)
        .isEqualTo(keyOfNestedImportedCodeOwnerConfig1.project().get());
    assertThat(nestedResolvedImportInfo.branch)
        .isEqualTo(keyOfNestedImportedCodeOwnerConfig1.branchNameKey().branch());
    assertThat(nestedResolvedImportInfo.path)
        .isEqualTo(
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfNestedImportedCodeOwnerConfig1)
                .getFilePath());
    assertThat(nestedResolvedImportInfo.importMode)
        .isEqualTo(nestedCodeOwnerConfigReference1.importMode());
    assertThat(nestedResolvedImportInfo.imports).isNull();
    assertThat(nestedResolvedImportInfo.unresolvedImports).isNull();
    assertThat(nestedResolvedImportInfo.unresolvedErrorMessage).isNull();

    assertThat(resolvedImportInfo.unresolvedImports).hasSize(1);
    CodeOwnerConfigFileInfo nestedUnresolvedImportInfo1 =
        Iterables.getOnlyElement(resolvedImportInfo.unresolvedImports);
    assertThat(nestedUnresolvedImportInfo1.project)
        .isEqualTo(keyOfNestedImportedCodeOwnerConfig2.project().get());
    assertThat(nestedUnresolvedImportInfo1.branch)
        .isEqualTo(keyOfNestedImportedCodeOwnerConfig2.branchNameKey().branch());
    assertThat(nestedUnresolvedImportInfo1.path)
        .isEqualTo(
            codeOwnerConfigOperations
                .codeOwnerConfig(keyOfNestedImportedCodeOwnerConfig2)
                .getFilePath());
    assertThat(nestedUnresolvedImportInfo1.importMode)
        .isEqualTo(nestedCodeOwnerConfigReference2.importMode());
    assertThat(nestedUnresolvedImportInfo1.imports).isNull();
    assertThat(nestedUnresolvedImportInfo1.unresolvedImports).isNull();
    assertThat(nestedUnresolvedImportInfo1.unresolvedErrorMessage).isEqualTo("error message");
  }

  @Test
  public void formatWithCyclicImports() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail(admin.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference1 =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, codeOwnerConfigKey1);

    CodeOwnerConfig.Key codeOwnerConfigKey2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/bar/baz")
            .addCodeOwnerEmail(admin.email())
            .create();
    CodeOwnerConfigReference codeOwnerConfigReference2 =
        createCodeOwnerConfigReference(CodeOwnerConfigImportMode.ALL, codeOwnerConfigKey2);

    CodeOwnerConfigFileInfo codeOwnerConfigFileInfo =
        CodeOwnerConfigFileJson.format(
            codeOwnerConfigKey1,
            /* resolvedImports= */ ImmutableList.of(
                CodeOwnerConfigImport.createResolvedImport(
                    codeOwnerConfigKey1, codeOwnerConfigKey2, codeOwnerConfigReference1),
                CodeOwnerConfigImport.createResolvedImport(
                    codeOwnerConfigKey2, codeOwnerConfigKey1, codeOwnerConfigReference2)),
            /* unresolvedImports= */ ImmutableList.of());

    assertThat(codeOwnerConfigFileInfo.project).isEqualTo(codeOwnerConfigKey1.project().get());
    assertThat(codeOwnerConfigFileInfo.branch)
        .isEqualTo(codeOwnerConfigKey1.branchNameKey().branch());
    assertThat(codeOwnerConfigFileInfo.path)
        .isEqualTo(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey1).getFilePath());
    assertThat(codeOwnerConfigFileInfo.importMode).isNull();
    assertThat(codeOwnerConfigFileInfo.unresolvedImports).isNull();
    assertThat(codeOwnerConfigFileInfo.unresolvedErrorMessage).isNull();

    assertThat(codeOwnerConfigFileInfo.imports).hasSize(1);
    CodeOwnerConfigFileInfo resolvedImportInfo =
        Iterables.getOnlyElement(codeOwnerConfigFileInfo.imports);
    assertThat(resolvedImportInfo.project).isEqualTo(codeOwnerConfigKey2.project().get());
    assertThat(resolvedImportInfo.branch).isEqualTo(codeOwnerConfigKey2.branchNameKey().branch());
    assertThat(resolvedImportInfo.path)
        .isEqualTo(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey2).getFilePath());
    assertThat(resolvedImportInfo.importMode).isEqualTo(codeOwnerConfigReference1.importMode());
    assertThat(resolvedImportInfo.unresolvedErrorMessage).isNull();

    assertThat(resolvedImportInfo.imports).hasSize(1);
    CodeOwnerConfigFileInfo nestedResolvedImportInfo =
        Iterables.getOnlyElement(resolvedImportInfo.imports);
    assertThat(nestedResolvedImportInfo.project).isEqualTo(codeOwnerConfigKey1.project().get());
    assertThat(nestedResolvedImportInfo.branch)
        .isEqualTo(codeOwnerConfigKey1.branchNameKey().branch());
    assertThat(nestedResolvedImportInfo.path)
        .isEqualTo(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey1).getFilePath());
    assertThat(nestedResolvedImportInfo.importMode)
        .isEqualTo(codeOwnerConfigReference1.importMode());
    assertThat(nestedResolvedImportInfo.imports).isNull();
    assertThat(nestedResolvedImportInfo.unresolvedImports).isNull();
    assertThat(nestedResolvedImportInfo.unresolvedErrorMessage).isNull();
  }
}
