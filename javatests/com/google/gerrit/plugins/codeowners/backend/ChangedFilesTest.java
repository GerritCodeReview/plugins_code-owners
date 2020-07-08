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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.plugins.codeowners.JgitPath;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.restapi.change.ChangesCollection;
import com.google.gerrit.server.restapi.change.Revisions;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link ChangedFiles}. */
public class ChangedFilesTest extends AbstractCodeOwnersTest {
  private ChangedFiles changedFiles;
  private ChangesCollection changesCollection;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    changedFiles = plugin.getSysInjector().getInstance(ChangedFiles.class);
    changesCollection = plugin.getSysInjector().getInstance(ChangesCollection.class);
    revisions = plugin.getSysInjector().getInstance(Revisions.class);
  }

  @Test
  public void cannotComputeForNullRevisionResource() throws Exception {
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> changedFiles.compute(null));
    assertThat(npe).hasMessageThat().isEqualTo("revisionResource");
  }

  @Test
  public void computeForChangeThatAddedAFile() throws Exception {
    String path = "/foo/bar/baz.txt";
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    ImmutableSet<ChangedFile> changedFilesSet = changedFiles.compute(getRevisionResource(changeId));
    assertThat(changedFilesSet).hasSize(1);
    ChangedFile changedFile = Iterables.getOnlyElement(changedFilesSet);
    assertThat(changedFile.newPath()).value().isEqualTo(Paths.get(path));
    assertThat(changedFile.oldPath()).isEmpty();
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isFalse();
  }

  @Test
  public void computeForChangeThatModifiedAFile() throws Exception {
    String path = "/foo/bar/baz.txt";
    createChange("Test Change", JgitPath.of(path).get(), "file content").getChangeId();

    String changeId =
        createChange("Change Modifying A File", JgitPath.of(path).get(), "new file content")
            .getChangeId();

    ImmutableSet<ChangedFile> changedFilesSet = changedFiles.compute(getRevisionResource(changeId));
    assertThat(changedFilesSet).hasSize(1);
    ChangedFile changedFile = Iterables.getOnlyElement(changedFilesSet);
    assertThat(changedFile.newPath()).value().isEqualTo(Paths.get(path));
    assertThat(changedFile.oldPath()).value().isEqualTo(Paths.get(path));
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isFalse();
  }

  @Test
  public void computeForChangeThatDeletedAFile() throws Exception {
    String path = "/foo/bar/baz.txt";
    createChange("Test Change", JgitPath.of(path).get(), "file content").getChangeId();

    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Change Deleting A File",
            JgitPath.of(path).get(),
            "file content");
    Result r = push.rm("refs/for/master");
    r.assertOkStatus();
    String changeId = r.getChangeId();

    ImmutableSet<ChangedFile> changedFilesSet = changedFiles.compute(getRevisionResource(changeId));
    assertThat(changedFilesSet).hasSize(1);
    ChangedFile changedFile = Iterables.getOnlyElement(changedFilesSet);
    assertThat(changedFile.newPath()).isEmpty();
    assertThat(changedFile.oldPath()).value().isEqualTo(Paths.get(path));
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isTrue();
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void computeForInitialChangeThatAddedAFile() throws Exception {
    String path = "/foo/bar/baz.txt";
    Result r = createChange("Change Adding A File", JgitPath.of(path).get(), "file content");
    assertThat(r.getCommit().getParents()).isEmpty();
    String changeId = r.getChangeId();

    ImmutableSet<ChangedFile> changedFilesSet = changedFiles.compute(getRevisionResource(changeId));
    assertThat(changedFilesSet).hasSize(1);
    ChangedFile changedFile = Iterables.getOnlyElement(changedFilesSet);
    assertThat(changedFile.newPath()).value().isEqualTo(Paths.get(path));
    assertThat(changedFile.oldPath()).isEmpty();
    assertThat(changedFile.isRename()).isFalse();
    assertThat(changedFile.isDeletion()).isFalse();
  }

  private RevisionResource getRevisionResource(String changeId) throws Exception {
    ChangeResource changeResource =
        changesCollection.parse(TopLevelResource.INSTANCE, IdString.fromDecoded(changeId));
    return revisions.parse(changeResource, IdString.fromDecoded("current"));
  }
}
