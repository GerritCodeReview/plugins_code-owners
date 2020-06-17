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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.testing.CodeOwnerSetSubject;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

/** Tests for {@link CodeOwnerConfig}. */
public class CodeOwnerConfigTest extends AbstractCodeOwnersTest {
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
  public void getShortBranchName() throws Exception {
    String branch = "master";
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(Project.nameKey("project"), branch, "/foo/bar/");
    assertThat(codeOwnerConfigKey.shortBranchName()).isEqualTo(branch);
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
  public void cannotAddNullAsCodeOwnerSet() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> createCodeOwnerBuilder().addCodeOwnerSet(null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerSet");
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
    CodeOwnerConfig codeOwnerConfig =
        createCodeOwnerBuilder()
            .addCodeOwnerSet(CodeOwnerSet.createForEmails(codeOwnerEmail1, codeOwnerEmail2))
            .build();
    assertThat(codeOwnerConfig.localCodeOwners(Paths.get("/foo/bar/baz.md")))
        .comparingElementsUsing(CodeOwnerSetSubject.CODE_OWNER_REFERENCE_TO_EMAIL)
        .containsExactly(codeOwnerEmail1, codeOwnerEmail2);
  }

  @Test
  public void cannotGetLocalCodeOwnersForNullPath() throws Exception {
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerBuilder().build();
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> codeOwnerConfig.localCodeOwners(null));
    assertThat(npe).hasMessageThat().isEqualTo("path");
  }

  @Test
  public void cannotGetLocalCodeOwnersForRelativePath() throws Exception {
    String relativePath = "foo/bar/baz.md";
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerBuilder().build();
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> codeOwnerConfig.localCodeOwners(Paths.get(relativePath)));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("path %s must be absolute", relativePath));
  }

  @Test
  public void cannotRelativizeNullPath() throws Exception {
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerBuilder().build();
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> codeOwnerConfig.relativize(null));
    assertThat(npe).hasMessageThat().isEqualTo("path");
  }

  @Test
  public void relativizePath() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(
                CodeOwnerConfig.Key.create(
                    BranchNameKey.create(Project.nameKey("project"), "master"),
                    Paths.get("/foo/bar/")))
            .build();
    Path relativizedPath = codeOwnerConfig.relativize(Paths.get("/foo/bar/baz.md"));
    assertThat(relativizedPath).isEqualTo(Paths.get("baz.md"));
  }

  private static CodeOwnerConfig.Builder createCodeOwnerBuilder() {
    return CodeOwnerConfig.builder(
        CodeOwnerConfig.Key.create(
            BranchNameKey.create(Project.nameKey("project"), "master"), Paths.get("/")));
  }
}
