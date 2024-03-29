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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import java.nio.file.Path;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

/** Tests for {@link CodeOwnerConfig}. */
public class CodeOwnerConfigTest extends AbstractCodeOwnersTest {
  private static final ObjectId TEST_REVISION =
      ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

  @Test
  public void createKey() throws Exception {
    Project.NameKey project = Project.nameKey("project");
    String branch = "master";
    String folderPath = "/foo/bar/";
    CodeOwnerConfig.Key codeOwnerConfigKeyCreatedByCustomConstructor =
        CodeOwnerConfig.Key.create(project, branch, folderPath);
    CodeOwnerConfig.Key codeOwnerConfigKeyCreatedByAutoValueConstructor =
        CodeOwnerConfig.Key.create(BranchNameKey.create(project, branch), Path.of(folderPath));
    assertThat(codeOwnerConfigKeyCreatedByCustomConstructor)
        .isEqualTo(codeOwnerConfigKeyCreatedByAutoValueConstructor);
  }

  @Test
  public void cannotCreateKeyWithRelativePath() throws Exception {
    String relativeFolderPath = "foo/bar";
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                CodeOwnerConfig.Key.create(
                    Project.nameKey("project"), "master", relativeFolderPath));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("folder path %s must be absolute", relativeFolderPath));
  }

  @Test
  public void testGetProject() throws Exception {
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
    assertThat(codeOwnerConfigKey.filePath("OWNERS")).isEqualTo(Path.of(folderPath, "OWNERS"));
  }

  @Test
  public void getFilePathWithCustomFileName() throws Exception {
    String customFileName = "FOO_CODE_OWNERS";
    String folderPath = "/foo/bar/";
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(
            Project.nameKey("project"), "master", folderPath, customFileName);
    assertThat(codeOwnerConfigKey.filePath("OWNERS"))
        .isEqualTo(Path.of(folderPath, customFileName));
  }

  @Test
  public void cannotGetFilePathForNullFileName() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        CodeOwnerConfig.Key.create(Project.nameKey("project"), "master", "/foo/bar/");
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> codeOwnerConfigKey.filePath(/* defaultCodeOwnerConfigFileName= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfigFileName");
  }

  @Test
  public void cannotAddNullAsCodeOwnerSet() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> createCodeOwnerBuilder().addCodeOwnerSet(/* codeOwnerSet= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerSet");
  }

  @Test
  public void cannotRelativizeNullPath() throws Exception {
    CodeOwnerConfig codeOwnerConfig = createCodeOwnerBuilder().build();
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> codeOwnerConfig.relativize(/* path= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("path");
  }

  @Test
  public void relativizePath() throws Exception {
    CodeOwnerConfig codeOwnerConfig =
        CodeOwnerConfig.builder(
                CodeOwnerConfig.Key.create(
                    BranchNameKey.create(Project.nameKey("project"), "master"),
                    Path.of("/foo/bar/")),
                TEST_REVISION)
            .build();
    Path relativizedPath = codeOwnerConfig.relativize(Path.of("/foo/bar/baz.md"));
    assertThat(relativizedPath).isEqualTo(Path.of("baz.md"));
  }

  private static CodeOwnerConfig.Builder createCodeOwnerBuilder() {
    return CodeOwnerConfig.builder(
        CodeOwnerConfig.Key.create(
            BranchNameKey.create(Project.nameKey("project"), "master"), Path.of("/")),
        TEST_REVISION);
  }
}
