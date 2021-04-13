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
import static com.google.gerrit.truth.OptionalSubject.assertThat;
import static org.mockito.Mockito.when;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.common.ChangedFile;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import java.nio.file.Paths;
import java.util.Optional;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

/** Tests for {@link ChangedFile}. */
public class ChangedFileTest extends AbstractCodeOwnersTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Mock private DiffEntry diffEntry;
  @Mock private PatchListEntry patchListEntry;
  @Mock private FileDiffOutput fileDiffOutput;

  @Test
  public void getNewPath_diffEntry() throws Exception {
    String newPath = "foo/bar/baz.txt";
    setupDiffEntry(newPath, null, ChangeType.ADD);
    assertThat(ChangedFile.create(diffEntry).newPath()).value().isEqualTo(Paths.get("/" + newPath));
  }

  @Test
  public void getNewPath_patchListEntry() throws Exception {
    String newPath = "foo/bar/baz.txt";
    setupPatchListEntry(newPath, null, Patch.ChangeType.ADDED);
    assertThat(ChangedFile.create(patchListEntry).newPath())
        .value()
        .isEqualTo(Paths.get("/" + newPath));
  }

  @Test
  public void getNewPath_fileDiffOutput() throws Exception {
    String newPath = "foo/bar/baz.txt";
    setupFileDiffOutput(newPath, null, Patch.ChangeType.ADDED);
    assertThat(ChangedFile.create(fileDiffOutput).newPath())
        .value()
        .isEqualTo(Paths.get("/" + newPath));
  }

  @Test
  public void getNewPathWhenNewPathIsNotSet_diffEntry() throws Exception {
    setupDiffEntry(null, null, ChangeType.ADD);
    assertThat(ChangedFile.create(diffEntry).newPath()).isEmpty();
  }

  @Test
  public void getNewPathWhenNewPathIsNotSet_patchListEntry() throws Exception {
    setupPatchListEntry(null, null, Patch.ChangeType.ADDED);
    assertThat(ChangedFile.create(patchListEntry).newPath()).isEmpty();
  }

  @Test
  public void getNewPathWhenNewPathIsNotSet_fileDiffOutput() throws Exception {
    setupFileDiffOutput(null, null, Patch.ChangeType.ADDED);
    assertThat(ChangedFile.create(fileDiffOutput).newPath()).isEmpty();
  }

  @Test
  public void hasNewPath_diffEntry() throws Exception {
    String newPath = "foo/bar/baz.txt";
    setupDiffEntry(newPath, null, ChangeType.ADD);

    ChangedFile changedFile = ChangedFile.create(diffEntry);
    assertThat(changedFile.hasNewPath(Paths.get("/" + newPath))).isTrue();
    assertThat(changedFile.hasNewPath(Paths.get("/otherPath"))).isFalse();
  }

  @Test
  public void hasNewPath_patchListEntry() throws Exception {
    String newPath = "foo/bar/baz.txt";
    setupPatchListEntry(newPath, null, Patch.ChangeType.ADDED);

    ChangedFile changedFile = ChangedFile.create(patchListEntry);
    assertThat(changedFile.hasNewPath(Paths.get("/" + newPath))).isTrue();
    assertThat(changedFile.hasNewPath(Paths.get("/otherPath"))).isFalse();
  }

  @Test
  public void hasNewPath_fileDiffOutput() throws Exception {
    String newPath = "foo/bar/baz.txt";
    setupFileDiffOutput(newPath, null, Patch.ChangeType.ADDED);

    ChangedFile changedFile = ChangedFile.create(fileDiffOutput);
    assertThat(changedFile.hasNewPath(Paths.get("/" + newPath))).isTrue();
    assertThat(changedFile.hasNewPath(Paths.get("/otherPath"))).isFalse();
  }

  @Test
  public void cannotCheckHasNewPathWithNull_diffEntry() throws Exception {
    setupDiffEntry(null, null, ChangeType.ADD);

    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> ChangedFile.create(diffEntry).hasNewPath(null));
    assertThat(npe).hasMessageThat().isEqualTo("absolutePath");
  }

  @Test
  public void cannotCheckHasNewPathWithNull_patchListEntry() throws Exception {
    setupPatchListEntry(null, null, Patch.ChangeType.ADDED);

    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> ChangedFile.create(patchListEntry).hasNewPath(null));
    assertThat(npe).hasMessageThat().isEqualTo("absolutePath");
  }

  @Test
  public void cannotCheckHasNewPathWithNull_fileDiffOutput() throws Exception {
    setupFileDiffOutput(null, null, Patch.ChangeType.ADDED);

    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> ChangedFile.create(fileDiffOutput).hasNewPath(null));
    assertThat(npe).hasMessageThat().isEqualTo("absolutePath");
  }

  @Test
  public void cannotCheckHasNewPathWithRelativePath_diffEntry() throws Exception {
    String relativePath = "foo/bar/baz.txt";
    setupDiffEntry(null, null, ChangeType.ADD);
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> ChangedFile.create(diffEntry).hasNewPath(Paths.get(relativePath)));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("path %s must be absolute", relativePath));
  }

  @Test
  public void cannotCheckHasNewPathWithRelativePath_patchListEntry() throws Exception {
    String relativePath = "foo/bar/baz.txt";
    setupPatchListEntry(null, null, Patch.ChangeType.ADDED);
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> ChangedFile.create(patchListEntry).hasNewPath(Paths.get(relativePath)));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("path %s must be absolute", relativePath));
  }

  @Test
  public void cannotCheckHasNewPathWithRelativePath_fileDiffOutput() throws Exception {
    String relativePath = "foo/bar/baz.txt";
    setupFileDiffOutput(null, null, Patch.ChangeType.ADDED);
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> ChangedFile.create(fileDiffOutput).hasNewPath(Paths.get(relativePath)));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("path %s must be absolute", relativePath));
  }

  @Test
  public void getOldPath_diffEntry() throws Exception {
    String oldPath = "foo/bar/baz.txt";
    setupDiffEntry(null, oldPath, ChangeType.DELETE);
    assertThat(ChangedFile.create(diffEntry).oldPath()).value().isEqualTo(Paths.get("/" + oldPath));
  }

  @Test
  public void getOldPath_patchListEntry() throws Exception {
    String oldPath = "foo/bar/baz.txt";
    setupPatchListEntry(null, oldPath, Patch.ChangeType.DELETED);
    assertThat(ChangedFile.create(patchListEntry).oldPath())
        .value()
        .isEqualTo(Paths.get("/" + oldPath));
  }

  @Test
  public void getOldPath_fileDiffOutput() throws Exception {
    String oldPath = "foo/bar/baz.txt";
    setupFileDiffOutput(null, oldPath, Patch.ChangeType.DELETED);
    assertThat(ChangedFile.create(fileDiffOutput).oldPath())
        .value()
        .isEqualTo(Paths.get("/" + oldPath));
  }

  @Test
  public void getOldPathWhenOldPathIsNotSet_diffEntry() throws Exception {
    setupDiffEntry(null, null, ChangeType.DELETE);
    when(diffEntry.getOldPath()).thenReturn(DiffEntry.DEV_NULL);
    assertThat(ChangedFile.create(diffEntry).oldPath()).isEmpty();
  }

  @Test
  public void getOldPathWhenOldPathIsNotSet_patchListEntry() throws Exception {
    setupPatchListEntry(null, null, Patch.ChangeType.DELETED);
    assertThat(ChangedFile.create(patchListEntry).oldPath()).isEmpty();
  }

  @Test
  public void getOldPathWhenOldPathIsNotSet_fileDiffOutput() throws Exception {
    setupFileDiffOutput(null, null, Patch.ChangeType.DELETED);
    assertThat(ChangedFile.create(fileDiffOutput).oldPath()).isEmpty();
  }

  @Test
  public void hasOldPath_diffEntry() throws Exception {
    String oldPath = "foo/bar/baz.txt";
    setupDiffEntry(null, oldPath, ChangeType.DELETE);

    ChangedFile changedFile = ChangedFile.create(diffEntry);
    assertThat(changedFile.hasOldPath(Paths.get("/" + oldPath))).isTrue();
    assertThat(changedFile.hasOldPath(Paths.get("/otherPath"))).isFalse();
  }

  @Test
  public void hasOldPath_patchListEntry() throws Exception {
    String oldPath = "foo/bar/baz.txt";
    setupPatchListEntry(null, oldPath, Patch.ChangeType.DELETED);

    ChangedFile changedFile = ChangedFile.create(patchListEntry);
    assertThat(changedFile.hasOldPath(Paths.get("/" + oldPath))).isTrue();
    assertThat(changedFile.hasOldPath(Paths.get("/otherPath"))).isFalse();
  }

  @Test
  public void hasOldPath_fileDiffOutput() throws Exception {
    String oldPath = "foo/bar/baz.txt";
    setupFileDiffOutput(null, oldPath, Patch.ChangeType.DELETED);

    ChangedFile changedFile = ChangedFile.create(fileDiffOutput);
    assertThat(changedFile.hasOldPath(Paths.get("/" + oldPath))).isTrue();
    assertThat(changedFile.hasOldPath(Paths.get("/otherPath"))).isFalse();
  }

  @Test
  public void cannotCheckHasOldPathWithNull_diffEntry() throws Exception {
    setupDiffEntry(null, null, ChangeType.DELETE);
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> ChangedFile.create(diffEntry).hasOldPath(null));
    assertThat(npe).hasMessageThat().isEqualTo("absolutePath");
  }

  @Test
  public void cannotCheckHasOldPathWithNull_patchListEntry() throws Exception {
    setupPatchListEntry(null, null, Patch.ChangeType.DELETED);
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> ChangedFile.create(patchListEntry).hasOldPath(null));
    assertThat(npe).hasMessageThat().isEqualTo("absolutePath");
  }

  @Test
  public void cannotCheckHasOldPathWithNull_fileDiffOutput() throws Exception {
    setupFileDiffOutput(null, null, Patch.ChangeType.DELETED);
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> ChangedFile.create(fileDiffOutput).hasOldPath(null));
    assertThat(npe).hasMessageThat().isEqualTo("absolutePath");
  }

  @Test
  public void cannotCheckHasOldPathWithRelativePath_diffEntry() throws Exception {
    setupDiffEntry(null, null, ChangeType.DELETE);
    String relativePath = "foo/bar/baz.txt";
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> ChangedFile.create(diffEntry).hasOldPath(Paths.get(relativePath)));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("path %s must be absolute", relativePath));
  }

  @Test
  public void cannotCheckHasOldPathWithRelativePath_patchListEntry() throws Exception {
    setupPatchListEntry(null, null, Patch.ChangeType.DELETED);
    String relativePath = "foo/bar/baz.txt";
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> ChangedFile.create(patchListEntry).hasOldPath(Paths.get(relativePath)));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("path %s must be absolute", relativePath));
  }

  @Test
  public void cannotCheckHasOldPathWithRelativePath_fileDiffOutput() throws Exception {
    setupFileDiffOutput(null, null, Patch.ChangeType.DELETED);
    String relativePath = "foo/bar/baz.txt";
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> ChangedFile.create(fileDiffOutput).hasOldPath(Paths.get(relativePath)));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("path %s must be absolute", relativePath));
  }

  @Test
  public void isRename_diffEntry() throws Exception {
    setupDiffEntry(null, null, ChangeType.RENAME);
    ChangedFile changedFile = ChangedFile.create(diffEntry);
    assertThat(changedFile.isRename()).isTrue();
    assertThat(changedFile.isDeletion()).isFalse();
  }

  @Test
  public void isRename_patchListEntry() throws Exception {
    setupPatchListEntry(null, null, Patch.ChangeType.RENAMED);
    ChangedFile changedFile = ChangedFile.create(patchListEntry);
    assertThat(changedFile.isRename()).isTrue();
    assertThat(changedFile.isDeletion()).isFalse();
  }

  @Test
  public void isRename_fileDiffOutput() throws Exception {
    setupFileDiffOutput(null, null, Patch.ChangeType.RENAMED);
    ChangedFile changedFile = ChangedFile.create(fileDiffOutput);
    assertThat(changedFile.isRename()).isTrue();
    assertThat(changedFile.isDeletion()).isFalse();
  }

  @Test
  public void isDeletion_diffEntry() throws Exception {
    setupDiffEntry(null, null, ChangeType.DELETE);
    ChangedFile changedFile = ChangedFile.create(diffEntry);
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isTrue();
  }

  @Test
  public void isDeletion_patchListEntry() throws Exception {
    setupPatchListEntry(null, null, Patch.ChangeType.DELETED);
    ChangedFile changedFile = ChangedFile.create(patchListEntry);
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isTrue();
  }

  @Test
  public void isDeletion_fileDiffOutput() throws Exception {
    setupFileDiffOutput(null, null, Patch.ChangeType.DELETED);
    ChangedFile changedFile = ChangedFile.create(fileDiffOutput);
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isTrue();
  }

  @Test
  public void isAddition_diffEntry() throws Exception {
    setupDiffEntry(null, null, ChangeType.ADD);
    ChangedFile changedFile = ChangedFile.create(diffEntry);
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isFalse();
  }

  @Test
  public void isAddition_patchListEntry() throws Exception {
    setupPatchListEntry(null, null, Patch.ChangeType.ADDED);
    ChangedFile changedFile = ChangedFile.create(patchListEntry);
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isFalse();
  }

  @Test
  public void isAddition_fileDiffOutput() throws Exception {
    setupFileDiffOutput(null, null, Patch.ChangeType.ADDED);
    ChangedFile changedFile = ChangedFile.create(fileDiffOutput);
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isFalse();
  }

  @Test
  public void isModify_diffEntry() throws Exception {
    setupDiffEntry(null, null, ChangeType.MODIFY);
    ChangedFile changedFile = ChangedFile.create(diffEntry);
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isFalse();
  }

  @Test
  public void isModify_patchListEntry() throws Exception {
    setupPatchListEntry(null, null, Patch.ChangeType.MODIFIED);
    ChangedFile changedFile = ChangedFile.create(patchListEntry);
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isFalse();
  }

  @Test
  public void isModify_fileDiffOutput() throws Exception {
    setupFileDiffOutput(null, null, Patch.ChangeType.MODIFIED);
    ChangedFile changedFile = ChangedFile.create(fileDiffOutput);
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isFalse();
  }

  @Test
  public void isCopy_diffEntry() throws Exception {
    setupDiffEntry(null, null, ChangeType.COPY);
    ChangedFile changedFile = ChangedFile.create(diffEntry);
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isFalse();
  }

  @Test
  public void isCopy_patchListEntry() throws Exception {
    setupPatchListEntry(null, null, Patch.ChangeType.COPIED);
    ChangedFile changedFile = ChangedFile.create(patchListEntry);
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isFalse();
  }

  @Test
  public void isCopy_fileDiffOutput() throws Exception {
    setupFileDiffOutput(null, null, Patch.ChangeType.COPIED);
    ChangedFile changedFile = ChangedFile.create(fileDiffOutput);
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isFalse();
  }

  private void setupDiffEntry(
      @Nullable String newPath, @Nullable String oldPath, ChangeType changeType) {
    when(diffEntry.getNewPath()).thenReturn(newPath != null ? newPath : DiffEntry.DEV_NULL);
    when(diffEntry.getOldPath()).thenReturn(oldPath != null ? oldPath : DiffEntry.DEV_NULL);
    when(diffEntry.getChangeType()).thenReturn(changeType);
  }

  private void setupPatchListEntry(
      @Nullable String newPath, @Nullable String oldPath, Patch.ChangeType changeType) {
    if (Patch.ChangeType.DELETED == changeType) {
      // for deletions PatchListEntry sets the oldPath as new name
      when(patchListEntry.getNewName()).thenReturn(oldPath);
      when(patchListEntry.getChangeType()).thenReturn(changeType);
    } else {
      when(patchListEntry.getNewName()).thenReturn(newPath);
      when(patchListEntry.getOldName()).thenReturn(oldPath);
      when(patchListEntry.getChangeType()).thenReturn(changeType);
    }
  }

  private void setupFileDiffOutput(
      @Nullable String newPath, @Nullable String oldPath, Patch.ChangeType changeType) {
    when(fileDiffOutput.newPath()).thenReturn(Optional.ofNullable(newPath));
    when(fileDiffOutput.oldPath()).thenReturn(Optional.ofNullable(oldPath));
    when(fileDiffOutput.changeType()).thenReturn(changeType);
  }
}
