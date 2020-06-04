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

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.plugins.codeowners.testing.CodeOwnerConfigSubject;
import java.nio.file.Paths;
import org.junit.Test;

/** Tests for {@link CodeOwnerConfig}. */
public class CodeOwnerConfigTest {
  @Test
  public void createKey() throws Exception {
    Project.NameKey project = Project.nameKey("project");
    String branch = "master";
    String folderPath = "/foo/bar/";
    CodeOwnerConfig.Key codeOwnerConfigKeyCreatedByCustomConstructor =
        CodeOwnerConfig.Key.create(project, branch, folderPath);
    CodeOwnerConfig.Key codeOwnerConfigKeyCreatedByAutoValueConstructor =
        CodeOwnerConfig.Key.create(BranchNameKey.create(project, branch), Paths.get(folderPath));
    assertThat(codeOwnerConfigKeyCreatedByCustomConstructor)
        .isEqualTo(codeOwnerConfigKeyCreatedByAutoValueConstructor);
  }

  @Test
  public void getProject() throws Exception {
    Project.NameKey project = Project.nameKey("project");
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(project, "master", "/foo/bar/");
    assertThat(codeOwnerConfigKey.project()).isEqualTo(project);
  }

  @Test
  public void getBranchName() throws Exception {
    String branch = "master";
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(Project.nameKey("project"), branch, "/foo/bar/");
    assertThat(codeOwnerConfigKey.branchName()).isEqualTo(branch);
  }

  @Test
  public void getRef() throws Exception {
    String branch = "master";
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(Project.nameKey("project"), branch, "/foo/bar/");
    assertThat(codeOwnerConfigKey.ref()).isEqualTo(RefNames.REFS_HEADS + branch);
  }

  @Test
  public void getFilePath() throws Exception {
    String folderPath = "/foo/bar/";
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(Project.nameKey("project"), "master", folderPath);
    assertThat(codeOwnerConfigKey.filePath("OWNERS")).isEqualTo(Paths.get(folderPath, "OWNERS"));
  }

  @Test
  public void cannotGetFilePathForNullFileName() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(Project.nameKey("project"), "master", "/foo/bar/");
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> codeOwnerConfigKey.filePath(null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfigFileName");
  }

  @Test
  public void getFilePathForJgit() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(Project.nameKey("project"), "master", "/foo/bar/");
    assertThat(codeOwnerConfigKey.filePathForJgit("OWNERS")).isEqualTo("foo/bar/OWNERS");
  }

  @Test
  public void cannotGetFilePathForJgitForNullFileName() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(Project.nameKey("project"), "master", "/foo/bar/");
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> codeOwnerConfigKey.filePathForJgit(null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfigFileName");
  }

  @Test
  public void addCodeOwners() throws Exception {
    CodeOwnerReference codeOwner1 = CodeOwnerReference.create("jdoe@example.com");
    CodeOwnerReference codeOwner2 = CodeOwnerReference.create("jroe@example.com");
    CodeOwnerConfig.Builder codeOwnerConfigBuilder = createCodeOwnerBuilder();
    codeOwnerConfigBuilder.addCodeOwner(codeOwner1);
    codeOwnerConfigBuilder.addCodeOwner(codeOwner2);
    CodeOwnerConfig codeOwnerConfig = codeOwnerConfigBuilder.build();
    assertThat(codeOwnerConfig).hasCodeOwnersThat().containsExactly(codeOwner1, codeOwner2);
  }

  @Test
  public void addDuplicateCodeOwners() throws Exception {
    CodeOwnerReference codeOwner = CodeOwnerReference.create("jdoe@example.com");
    CodeOwnerConfig.Builder codeOwnerConfigBuilder = createCodeOwnerBuilder();
    codeOwnerConfigBuilder.addCodeOwner(codeOwner);
    codeOwnerConfigBuilder.addCodeOwner(codeOwner);
    CodeOwnerConfig codeOwnerConfig = codeOwnerConfigBuilder.build();
    assertThat(codeOwnerConfig).hasCodeOwnersThat().containsExactly(codeOwner);
  }

  @Test
  public void cannotAddNullAsCodeOwner() throws Exception {
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> createCodeOwnerBuilder().addCodeOwner(null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerReference");
  }

  @Test
  public void addCodeOwnersByEmail() throws Exception {
    String codeOwnerEmail1 = "jdoe@example.com";
    String codeOwnerEmail2 = "jroe@example.com";
    CodeOwnerConfig.Builder codeOwnerConfigBuilder = createCodeOwnerBuilder();
    codeOwnerConfigBuilder.addCodeOwnerEmail(codeOwnerEmail1);
    codeOwnerConfigBuilder.addCodeOwnerEmail(codeOwnerEmail2);
    CodeOwnerConfig codeOwnerConfig = codeOwnerConfigBuilder.build();
    assertThat(codeOwnerConfig)
        .hasCodeOwnersEmailsThat()
        .containsExactly(codeOwnerEmail1, codeOwnerEmail2);
  }

  @Test
  public void addDuplicateCodeOwnersByEmail() throws Exception {
    String codeOwnerEmail = "jdoe@example.com";
    CodeOwnerConfig.Builder codeOwnerConfigBuilder = createCodeOwnerBuilder();
    codeOwnerConfigBuilder.addCodeOwnerEmail(codeOwnerEmail);
    codeOwnerConfigBuilder.addCodeOwnerEmail(codeOwnerEmail);
    CodeOwnerConfig codeOwnerConfig = codeOwnerConfigBuilder.build();
    assertThat(codeOwnerConfig).hasCodeOwnersEmailsThat().containsExactly(codeOwnerEmail);
  }

  @Test
  public void cannotAddNullAsCodeOwnerEmail() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> createCodeOwnerBuilder().addCodeOwnerEmail(null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerEmail");
  }

  @Test
  public void getEmptyLocalCodeOwners() throws Exception {
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerBuilder().build();
    assertThat(codeOwnerConfig.localCodeOwners(Paths.get("/foo/bar/baz.md"))).isEmpty();
  }

  @Test
  public void getLocalCodeOwners() throws Exception {
    String codeOwnerEmail1 = "jdoe@example.com";
    String codeOwnerEmail2 = "jroe@example.com";
    CodeOwnerConfig.Builder codeOwnerConfigBuilder = createCodeOwnerBuilder();
    codeOwnerConfigBuilder.addCodeOwnerEmail(codeOwnerEmail1);
    codeOwnerConfigBuilder.addCodeOwnerEmail(codeOwnerEmail2);
    CodeOwnerConfig codeOwnerConfig = codeOwnerConfigBuilder.build();
    assertThat(codeOwnerConfig.localCodeOwners(Paths.get("/foo/bar/baz.md")))
        .comparingElementsUsing(CodeOwnerConfigSubject.CODE_OWNER_REFERENCE_TO_EMAIL)
        .containsExactly(codeOwnerEmail1, codeOwnerEmail2);
  }

  @Test
  public void getLocalCodeOwnersForNullPath() throws Exception {
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerBuilder().build();
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> codeOwnerConfig.localCodeOwners(null));
    assertThat(npe).hasMessageThat().isEqualTo("relativePath");
  }

  private static CodeOwnerConfig.Builder createCodeOwnerBuilder() {
    return CodeOwnerConfig.builder(
        CodeOwnerConfig.Key.create(
            BranchNameKey.create(Project.nameKey("project"), "master"), Paths.get("/")));
  }
}
