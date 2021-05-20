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

package com.google.gerrit.plugins.codeowners.restapi;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.CodeOwnerStatusInfoSubject.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusInfoSubject.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.PathCodeOwnerStatusInfoSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.common.ChangeType;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerStatusInfo;
import com.google.gerrit.plugins.codeowners.api.FileCodeOwnerStatusInfo;
import com.google.gerrit.plugins.codeowners.api.PathCodeOwnerStatusInfo;
import com.google.gerrit.plugins.codeowners.backend.FileCodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.backend.PathCodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.common.ChangedFile;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.testing.FileCodeOwnerStatusInfoSubject;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.truth.ListSubject;
import java.nio.file.Paths;
import java.util.Optional;
import org.eclipse.jgit.diff.DiffEntry;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerStatusInfoJson}. */
public class CodeOwnerStatusInfoJsonTest extends AbstractCodeOwnersTest {
  private CodeOwnerStatusInfoJson codeOwnerStatusInfoJson;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerStatusInfoJson = plugin.getSysInjector().getInstance(CodeOwnerStatusInfoJson.class);
  }

  @Test
  public void cannotFormatNullPathCodeOwnerStatus() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                CodeOwnerStatusInfoJson.format(
                    /* pathCodeOwnerStatus= */ (PathCodeOwnerStatus) null));
    assertThat(npe).hasMessageThat().isEqualTo("pathCodeOwnerStatus");
  }

  @Test
  public void formatPathCodeOwnerStatus() throws Exception {
    PathCodeOwnerStatus pathCodeOwnerStatus =
        PathCodeOwnerStatus.create(Paths.get("/foo/bar.baz"), CodeOwnerStatus.APPROVED);
    PathCodeOwnerStatusInfo pathCodeOwnerStatusInfo =
        CodeOwnerStatusInfoJson.format(pathCodeOwnerStatus);
    assertThat(pathCodeOwnerStatusInfo).hasPathThat().isEqualTo("foo/bar.baz");
    assertThat(pathCodeOwnerStatusInfo).hasStatusThat().isEqualTo(CodeOwnerStatus.APPROVED);
    assertThat(pathCodeOwnerStatusInfo).hasReasonsThat().isNull();
  }

  @Test
  public void formatPathCodeOwnerStatusWithReasons() throws Exception {
    PathCodeOwnerStatus pathCodeOwnerStatus =
        PathCodeOwnerStatus.builder(Paths.get("/foo/bar.baz"), CodeOwnerStatus.APPROVED)
            .addReason("one reason")
            .addReason("another reason")
            .build();
    PathCodeOwnerStatusInfo pathCodeOwnerStatusInfo =
        CodeOwnerStatusInfoJson.format(pathCodeOwnerStatus);
    assertThat(pathCodeOwnerStatusInfo).hasPathThat().isEqualTo("foo/bar.baz");
    assertThat(pathCodeOwnerStatusInfo).hasStatusThat().isEqualTo(CodeOwnerStatus.APPROVED);
    assertThat(pathCodeOwnerStatusInfo)
        .hasReasonsThat()
        .containsExactly("one reason", "another reason");
  }

  @Test
  public void cannotFormatNullFileCodeOwnerStatus() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                CodeOwnerStatusInfoJson.format(
                    /* fileCodeOwnerStatus= */ (FileCodeOwnerStatus) null));
    assertThat(npe).hasMessageThat().isEqualTo("fileCodeOwnerStatus");
  }

  @Test
  public void formatFileCodeOwnerStatusForNewFile() throws Exception {
    ChangedFile changedFile = mock(ChangedFile.class);
    when(changedFile.changeType()).thenReturn(DiffEntry.ChangeType.ADD);
    PathCodeOwnerStatus pathCodeOwnerStatus =
        PathCodeOwnerStatus.create(Paths.get("/foo/bar.baz"), CodeOwnerStatus.APPROVED);
    FileCodeOwnerStatus fileCodeOwnerStatus =
        FileCodeOwnerStatus.create(changedFile, Optional.of(pathCodeOwnerStatus), Optional.empty());
    FileCodeOwnerStatusInfo fileCodeOwnerStatusInfo =
        CodeOwnerStatusInfoJson.format(fileCodeOwnerStatus);
    assertThat(fileCodeOwnerStatusInfo).hasChangeTypeThat().isEqualTo(ChangeType.ADDED);
    assertThat(fileCodeOwnerStatusInfo)
        .hasNewPathStatusThat()
        .value()
        .hasPathThat()
        .isEqualTo("foo/bar.baz");
    assertThat(fileCodeOwnerStatusInfo)
        .hasNewPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
    assertThat(fileCodeOwnerStatusInfo).hasOldPathStatusThat().isEmpty();
  }

  @Test
  public void formatFileCodeOwnerStatusForModifiedFile() throws Exception {
    ChangedFile changedFile = mock(ChangedFile.class);
    when(changedFile.changeType()).thenReturn(DiffEntry.ChangeType.MODIFY);
    PathCodeOwnerStatus pathCodeOwnerStatus =
        PathCodeOwnerStatus.create(Paths.get("/foo/bar.baz"), CodeOwnerStatus.APPROVED);
    FileCodeOwnerStatus fileCodeOwnerStatus =
        FileCodeOwnerStatus.create(changedFile, Optional.of(pathCodeOwnerStatus), Optional.empty());
    FileCodeOwnerStatusInfo fileCodeOwnerStatusInfo =
        CodeOwnerStatusInfoJson.format(fileCodeOwnerStatus);
    assertThat(fileCodeOwnerStatusInfo).hasChangeTypeThat().isNull();
    assertThat(fileCodeOwnerStatusInfo)
        .hasNewPathStatusThat()
        .value()
        .hasPathThat()
        .isEqualTo("foo/bar.baz");
    assertThat(fileCodeOwnerStatusInfo)
        .hasNewPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
    assertThat(fileCodeOwnerStatusInfo).hasOldPathStatusThat().isEmpty();
  }

  @Test
  public void formatFileCodeOwnerStatusForDeletedFile() throws Exception {
    ChangedFile changedFile = mock(ChangedFile.class);
    when(changedFile.changeType()).thenReturn(DiffEntry.ChangeType.DELETE);
    PathCodeOwnerStatus pathCodeOwnerStatus =
        PathCodeOwnerStatus.create(Paths.get("/foo/bar.baz"), CodeOwnerStatus.APPROVED);
    FileCodeOwnerStatus fileCodeOwnerStatus =
        FileCodeOwnerStatus.create(changedFile, Optional.empty(), Optional.of(pathCodeOwnerStatus));
    FileCodeOwnerStatusInfo fileCodeOwnerStatusInfo =
        CodeOwnerStatusInfoJson.format(fileCodeOwnerStatus);
    assertThat(fileCodeOwnerStatusInfo).hasChangeTypeThat().isEqualTo(ChangeType.DELETED);
    assertThat(fileCodeOwnerStatusInfo).hasNewPathStatusThat().isEmpty();
    assertThat(fileCodeOwnerStatusInfo)
        .hasOldPathStatusThat()
        .value()
        .hasPathThat()
        .isEqualTo("foo/bar.baz");
    assertThat(fileCodeOwnerStatusInfo)
        .hasOldPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
  }

  @Test
  public void formatFileCodeOwnerStatusForRenamedFile() throws Exception {
    ChangedFile changedFile = mock(ChangedFile.class);
    when(changedFile.changeType()).thenReturn(DiffEntry.ChangeType.RENAME);
    PathCodeOwnerStatus newPathCodeOwnerStatus =
        PathCodeOwnerStatus.create(Paths.get("/foo/new.baz"), CodeOwnerStatus.PENDING);
    PathCodeOwnerStatus oldPathCodeOwnerStatus =
        PathCodeOwnerStatus.create(Paths.get("/foo/old.baz"), CodeOwnerStatus.APPROVED);
    FileCodeOwnerStatus fileCodeOwnerStatus =
        FileCodeOwnerStatus.create(
            changedFile, Optional.of(newPathCodeOwnerStatus), Optional.of(oldPathCodeOwnerStatus));
    FileCodeOwnerStatusInfo fileCodeOwnerStatusInfo =
        CodeOwnerStatusInfoJson.format(fileCodeOwnerStatus);
    assertThat(fileCodeOwnerStatusInfo).hasChangeTypeThat().isEqualTo(ChangeType.RENAMED);
    assertThat(fileCodeOwnerStatusInfo)
        .hasNewPathStatusThat()
        .value()
        .hasPathThat()
        .isEqualTo("foo/new.baz");
    assertThat(fileCodeOwnerStatusInfo)
        .hasNewPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.PENDING);
    assertThat(fileCodeOwnerStatusInfo)
        .hasOldPathStatusThat()
        .value()
        .hasPathThat()
        .isEqualTo("foo/old.baz");
    assertThat(fileCodeOwnerStatusInfo)
        .hasOldPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
  }

  @Test
  public void cannotFormatCodeOwnerStatusInfoForNullFileCodeOwnerStatuses() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwnerStatusInfoJson.format(
                    PatchSet.id(Change.id(1), 1), /* fileCodeOwnerStatuses= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("fileCodeOwnerStatuses");
  }

  @Test
  public void cannotCodeOwnerStatusInfoForNullPathSetId() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> codeOwnerStatusInfoJson.format(/* patchSetId= */ null, ImmutableSet.of()));
    assertThat(npe).hasMessageThat().isEqualTo("patchSetId");
  }

  @Test
  public void formatCodeOwnerStatusInfo() throws Exception {
    ChangedFile changedFile = mock(ChangedFile.class);
    when(changedFile.changeType()).thenReturn(DiffEntry.ChangeType.ADD);
    PathCodeOwnerStatus pathCodeOwnerStatus =
        PathCodeOwnerStatus.create(Paths.get("/foo/bar.baz"), CodeOwnerStatus.APPROVED);
    FileCodeOwnerStatus fileCodeOwnerStatus =
        FileCodeOwnerStatus.create(changedFile, Optional.of(pathCodeOwnerStatus), Optional.empty());
    CodeOwnerStatusInfo codeOwnerStatusInfo =
        codeOwnerStatusInfoJson.format(
            PatchSet.id(Change.id(1), 1), ImmutableSet.of(fileCodeOwnerStatus));
    assertThat(codeOwnerStatusInfo).hasPatchSetNumberThat().isEqualTo(1);
    FileCodeOwnerStatusInfoSubject fileCodeOwnerStatusInfoSubject =
        assertThat(codeOwnerStatusInfo).hasFileCodeOwnerStatusesThat().onlyElement();
    fileCodeOwnerStatusInfoSubject.hasChangeTypeThat().isEqualTo(ChangeType.ADDED);
    fileCodeOwnerStatusInfoSubject
        .hasNewPathStatusThat()
        .value()
        .hasPathThat()
        .isEqualTo("foo/bar.baz");
    fileCodeOwnerStatusInfoSubject
        .hasNewPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
    fileCodeOwnerStatusInfoSubject.hasNewPathStatusThat().value().hasReasonsThat().isNull();
    fileCodeOwnerStatusInfoSubject.hasOldPathStatusThat().isEmpty();
    assertThat(codeOwnerStatusInfo).hasAccountsThat().isNull();
  }

  @Test
  public void formatCodeOwnerStatusInfoWithReasons() throws Exception {
    ChangedFile changedFile = mock(ChangedFile.class);
    when(changedFile.changeType()).thenReturn(DiffEntry.ChangeType.ADD);
    PathCodeOwnerStatus pathCodeOwnerStatus =
        PathCodeOwnerStatus.builder(Paths.get("/foo/bar.baz"), CodeOwnerStatus.APPROVED)
            .addReason("one reason")
            .addReason("another reason")
            .build();
    FileCodeOwnerStatus fileCodeOwnerStatus =
        FileCodeOwnerStatus.create(changedFile, Optional.of(pathCodeOwnerStatus), Optional.empty());
    CodeOwnerStatusInfo codeOwnerStatusInfo =
        codeOwnerStatusInfoJson.format(
            PatchSet.id(Change.id(1), 1), ImmutableSet.of(fileCodeOwnerStatus));
    assertThat(codeOwnerStatusInfo).hasPatchSetNumberThat().isEqualTo(1);
    FileCodeOwnerStatusInfoSubject fileCodeOwnerStatusInfoSubject =
        assertThat(codeOwnerStatusInfo).hasFileCodeOwnerStatusesThat().onlyElement();
    fileCodeOwnerStatusInfoSubject.hasChangeTypeThat().isEqualTo(ChangeType.ADDED);
    fileCodeOwnerStatusInfoSubject
        .hasNewPathStatusThat()
        .value()
        .hasPathThat()
        .isEqualTo("foo/bar.baz");
    fileCodeOwnerStatusInfoSubject
        .hasNewPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
    fileCodeOwnerStatusInfoSubject
        .hasNewPathStatusThat()
        .value()
        .hasReasonsThat()
        .containsExactly("one reason", "another reason");
    fileCodeOwnerStatusInfoSubject.hasOldPathStatusThat().isEmpty();
    assertThat(codeOwnerStatusInfo).hasAccountsThat().isNull();
  }

  @Test
  public void formatCodeOwnerStatusInfoWithReasonsThatReferenceAccounts() throws Exception {
    TestAccount user2 = accountCreator.user2();
    String reason1 =
        String.format("because %s did something", ChangeMessagesUtil.getAccountTemplate(user.id()));
    String reason2 =
        String.format(
            "because %s, %s and %s did something else",
            ChangeMessagesUtil.getAccountTemplate(admin.id()),
            ChangeMessagesUtil.getAccountTemplate(user.id()),
            ChangeMessagesUtil.getAccountTemplate(user2.id()));
    ChangedFile changedFile = mock(ChangedFile.class);
    when(changedFile.changeType()).thenReturn(DiffEntry.ChangeType.ADD);
    PathCodeOwnerStatus pathCodeOwnerStatus =
        PathCodeOwnerStatus.builder(Paths.get("/foo/bar.baz"), CodeOwnerStatus.APPROVED)
            .addReason(reason1)
            .addReason(reason2)
            .build();
    FileCodeOwnerStatus fileCodeOwnerStatus =
        FileCodeOwnerStatus.create(changedFile, Optional.of(pathCodeOwnerStatus), Optional.empty());
    CodeOwnerStatusInfo codeOwnerStatusInfo =
        codeOwnerStatusInfoJson.format(
            PatchSet.id(Change.id(1), 1), ImmutableSet.of(fileCodeOwnerStatus));
    assertThat(codeOwnerStatusInfo).hasPatchSetNumberThat().isEqualTo(1);
    FileCodeOwnerStatusInfoSubject fileCodeOwnerStatusInfoSubject =
        assertThat(codeOwnerStatusInfo).hasFileCodeOwnerStatusesThat().onlyElement();
    fileCodeOwnerStatusInfoSubject.hasChangeTypeThat().isEqualTo(ChangeType.ADDED);
    fileCodeOwnerStatusInfoSubject
        .hasNewPathStatusThat()
        .value()
        .hasPathThat()
        .isEqualTo("foo/bar.baz");
    fileCodeOwnerStatusInfoSubject
        .hasNewPathStatusThat()
        .value()
        .hasStatusThat()
        .isEqualTo(CodeOwnerStatus.APPROVED);
    fileCodeOwnerStatusInfoSubject
        .hasNewPathStatusThat()
        .value()
        .hasReasonsThat()
        .containsExactly(reason1, reason2);
    fileCodeOwnerStatusInfoSubject.hasOldPathStatusThat().isEmpty();
    assertThat(codeOwnerStatusInfo).hasAccounts(admin, user, user2);
  }

  @Test
  public void fileCodeOwnerStatusInfosInCodeOwnerStatusInfoAreSortedByPath() throws Exception {
    ChangedFile changedFile1 = mock(ChangedFile.class);
    when(changedFile1.changeType()).thenReturn(DiffEntry.ChangeType.ADD);
    PathCodeOwnerStatus pathCodeOwnerStatus1 =
        PathCodeOwnerStatus.create(Paths.get("/foo/a/bar.baz"), CodeOwnerStatus.APPROVED);
    FileCodeOwnerStatus fileCodeOwnerStatus1 =
        FileCodeOwnerStatus.create(
            changedFile1, Optional.of(pathCodeOwnerStatus1), Optional.empty());

    ChangedFile changedFile2 = mock(ChangedFile.class);
    when(changedFile2.changeType()).thenReturn(DiffEntry.ChangeType.DELETE);
    PathCodeOwnerStatus pathCodeOwnerStatus2 =
        PathCodeOwnerStatus.create(Paths.get("/foo/b/bar.baz"), CodeOwnerStatus.APPROVED);
    FileCodeOwnerStatus fileCodeOwnerStatus2 =
        FileCodeOwnerStatus.create(
            changedFile2, Optional.empty(), Optional.of(pathCodeOwnerStatus2));

    ChangedFile changedFile3 = mock(ChangedFile.class);
    when(changedFile3.changeType()).thenReturn(DiffEntry.ChangeType.DELETE);
    PathCodeOwnerStatus newPathCodeOwnerStatus3 =
        PathCodeOwnerStatus.create(Paths.get("/foo/c/new.baz"), CodeOwnerStatus.APPROVED);
    PathCodeOwnerStatus oldPathCodeOwnerStatus3 =
        PathCodeOwnerStatus.create(Paths.get("/foo/c/old.baz"), CodeOwnerStatus.APPROVED);
    FileCodeOwnerStatus fileCodeOwnerStatus3 =
        FileCodeOwnerStatus.create(
            changedFile3,
            Optional.of(newPathCodeOwnerStatus3),
            Optional.of(oldPathCodeOwnerStatus3));

    CodeOwnerStatusInfo codeOwnerStatusInfo =
        codeOwnerStatusInfoJson.format(
            PatchSet.id(Change.id(1), 1),
            ImmutableSet.of(fileCodeOwnerStatus3, fileCodeOwnerStatus2, fileCodeOwnerStatus1));
    ListSubject<FileCodeOwnerStatusInfoSubject, FileCodeOwnerStatusInfo> listSubject =
        assertThat(codeOwnerStatusInfo).hasFileCodeOwnerStatusesThat();
    listSubject.hasSize(3);
    listSubject.element(0).hasNewPathStatusThat().value().hasPathThat().isEqualTo("foo/a/bar.baz");
    listSubject.element(1).hasOldPathStatusThat().value().hasPathThat().isEqualTo("foo/b/bar.baz");
    listSubject.element(2).hasNewPathStatusThat().value().hasPathThat().isEqualTo("foo/c/new.baz");
  }
}
