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

import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import java.nio.file.Paths;
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

  @Test
  public void getNewPath() throws Exception {
    String newPath = "foo/bar/baz.txt";
    when(diffEntry.getNewPath()).thenReturn(newPath);
    assertThat(ChangedFile.create(diffEntry).newPath()).value().isEqualTo(Paths.get("/" + newPath));
  }

  @Test
  public void getNewPathWhenNewPathIsNotSet() throws Exception {
    when(diffEntry.getNewPath()).thenReturn(DiffEntry.DEV_NULL);
    assertThat(ChangedFile.create(diffEntry).newPath()).isEmpty();
  }

  @Test
  public void hasNewPath() throws Exception {
    String newPath = "foo/bar/baz.txt";
    when(diffEntry.getNewPath()).thenReturn(newPath);

    ChangedFile changedFile = ChangedFile.create(diffEntry);
    assertThat(changedFile.hasNewPath(Paths.get("/" + newPath))).isTrue();
    assertThat(changedFile.hasNewPath(Paths.get("/otherPath"))).isFalse();
  }

  @Test
  public void cannotCheckHasNewPathWithNull() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> ChangedFile.create(diffEntry).hasNewPath(null));
    assertThat(npe).hasMessageThat().isEqualTo("absolutePath");
  }

  @Test
  public void cannotCheckHasNewPathWithRelativePath() throws Exception {
    String relativePath = "foo/bar/baz.txt";
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> ChangedFile.create(diffEntry).hasNewPath(Paths.get(relativePath)));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("path %s must be absolute", relativePath));
  }

  @Test
  public void getOldPath() throws Exception {
    String oldPath = "foo/bar/baz.txt";
    when(diffEntry.getOldPath()).thenReturn(oldPath);
    assertThat(ChangedFile.create(diffEntry).oldPath()).value().isEqualTo(Paths.get("/" + oldPath));
  }

  @Test
  public void getOldPathWhenOldPathIsNotSet() throws Exception {
    when(diffEntry.getOldPath()).thenReturn(DiffEntry.DEV_NULL);
    assertThat(ChangedFile.create(diffEntry).oldPath()).isEmpty();
  }

  @Test
  public void hasOldPath() throws Exception {
    String oldPath = "foo/bar/baz.txt";
    when(diffEntry.getOldPath()).thenReturn(oldPath);

    ChangedFile changedFile = ChangedFile.create(diffEntry);
    assertThat(changedFile.hasOldPath(Paths.get("/" + oldPath))).isTrue();
    assertThat(changedFile.hasOldPath(Paths.get("/otherPath"))).isFalse();
  }

  @Test
  public void cannotCheckHasOldPathWithNull() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> ChangedFile.create(diffEntry).hasOldPath(null));
    assertThat(npe).hasMessageThat().isEqualTo("absolutePath");
  }

  @Test
  public void cannotCheckHasOldPathWithRelativePath() throws Exception {
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
  public void isRename() throws Exception {
    when(diffEntry.getChangeType()).thenReturn(ChangeType.RENAME);
    ChangedFile changedFile = ChangedFile.create(diffEntry);
    assertThat(changedFile.isRename()).isTrue();
    assertThat(changedFile.isDeletion()).isFalse();
  }

  @Test
  public void isDeletion() throws Exception {
    when(diffEntry.getChangeType()).thenReturn(ChangeType.DELETE);
    ChangedFile changedFile = ChangedFile.create(diffEntry);
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isTrue();
  }

  @Test
  public void isAddition() throws Exception {
    when(diffEntry.getChangeType()).thenReturn(ChangeType.ADD);
    ChangedFile changedFile = ChangedFile.create(diffEntry);
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isFalse();
  }

  @Test
  public void isModify() throws Exception {
    when(diffEntry.getChangeType()).thenReturn(ChangeType.MODIFY);
    ChangedFile changedFile = ChangedFile.create(diffEntry);
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isFalse();
  }

  @Test
  public void isCopy() throws Exception {
    when(diffEntry.getChangeType()).thenReturn(ChangeType.COPY);
    ChangedFile changedFile = ChangedFile.create(diffEntry);
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isFalse();
  }
}
