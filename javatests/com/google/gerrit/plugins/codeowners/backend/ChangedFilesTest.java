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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.ChangedFileSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.plugins.codeowners.JgitPath;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.api.MergeCommitStrategy;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.restapi.change.ChangesCollection;
import com.google.inject.Inject;
import java.nio.file.Paths;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link ChangedFiles}. */
public class ChangedFilesTest extends AbstractCodeOwnersTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private ChangeOperations changeOperations;
  @Inject private ChangesCollection changesCollection;

  private ChangedFiles changedFiles;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    changedFiles = plugin.getSysInjector().getInstance(ChangedFiles.class);
  }

  @Test
  public void cannotComputeForNullRevisionResource() throws Exception {
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> changedFiles.compute(null));
    assertThat(npe).hasMessageThat().isEqualTo("revisionResource");
  }

  @Test
  public void cannotComputeForNullProject() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> changedFiles.compute(null, ObjectId.zeroId()));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void cannotComputeForNullRevision() throws Exception {
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> changedFiles.compute(project, null));
    assertThat(npe).hasMessageThat().isEqualTo("revision");
  }

  @Test
  public void computeForChangeThatAddedAFile() throws Exception {
    String path = "/foo/bar/baz.txt";
    String changeId =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getChangeId();

    ImmutableSet<ChangedFile> changedFilesSet = changedFiles.compute(getRevisionResource(changeId));
    assertThat(changedFilesSet).hasSize(1);
    ChangedFile changedFile = Iterables.getOnlyElement(changedFilesSet);
    assertThat(changedFile).hasNewPath().value().isEqualTo(Paths.get(path));
    assertThat(changedFile).hasOldPath().isEmpty();
    assertThat(changedFile).isNoRename();
    assertThat(changedFile).isNoDeletion();
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
    assertThat(changedFile).hasNewPath().value().isEqualTo(Paths.get(path));
    assertThat(changedFile).hasOldPath().value().isEqualTo(Paths.get(path));
    assertThat(changedFile).isNoRename();
    assertThat(changedFile).isNoDeletion();
  }

  @Test
  public void computeForChangeThatDeletedAFile() throws Exception {
    String path = "/foo/bar/baz.txt";
    String changeId = createChangeWithFileDeletion(path);

    ImmutableSet<ChangedFile> changedFilesSet = changedFiles.compute(getRevisionResource(changeId));
    assertThat(changedFilesSet).hasSize(1);
    ChangedFile changedFile = Iterables.getOnlyElement(changedFilesSet);
    assertThat(changedFile).hasNewPath().isEmpty();
    assertThat(changedFile).hasOldPath().value().isEqualTo(Paths.get(path));
    assertThat(changedFile).isNoRename();
    assertThat(changedFile).isDeletion();
  }

  @Test
  public void computeForChangeThatRenamedAFile() throws Exception {
    String oldPath = "/foo/bar/old.txt";
    String newPath = "/foo/bar/new.txt";
    String changeId = createChangeWithFileRename(oldPath, newPath);

    gApi.changes().id(changeId).current().files();

    ImmutableSet<ChangedFile> changedFilesSet = changedFiles.compute(getRevisionResource(changeId));
    assertThat(changedFilesSet).hasSize(1);
    ChangedFile changedFile = Iterables.getOnlyElement(changedFilesSet);
    assertThat(changedFile).hasNewPath().value().isEqualTo(Paths.get(newPath));
    assertThat(changedFile).hasOldPath().value().isEqualTo(Paths.get(oldPath));
    assertThat(changedFile).isRename();
    assertThat(changedFile).isNoDeletion();
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
    assertThat(changedFile).hasNewPath().value().isEqualTo(Paths.get(path));
    assertThat(changedFile).hasOldPath().isEmpty();
    assertThat(changedFile).isNoRename();
    assertThat(changedFile).isNoDeletion();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.mergeCommitStrategy", value = "ALL_CHANGED_FILES")
  public void computeForMergeChange_allChangedFiles() throws Exception {
    testComputeForMergeChange(MergeCommitStrategy.ALL_CHANGED_FILES);
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.mergeCommitStrategy",
      value = "FILES_WITH_CONFLICT_RESOLUTION")
  public void computeForMergeChange_filesWithConflictResolution() throws Exception {
    testComputeForMergeChange(MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION);
  }

  private void testComputeForMergeChange(MergeCommitStrategy mergeCommitStrategy) throws Exception {
    String file1 = "foo/a.txt";
    String file2 = "bar/b.txt";

    // Create a base change.
    Change.Id baseChange =
        changeOperations.newChange().branch("master").file(file1).content("base content").create();
    approveAndSubmit(baseChange);

    // Create another branch
    String branchName = "foo";
    BranchInput branchInput = new BranchInput();
    branchInput.ref = branchName;
    branchInput.revision = projectOperations.project(project).getHead("master").name();
    gApi.projects().name(project.get()).branch(branchInput.ref).create(branchInput);

    // Create a change in master that touches file1.
    Change.Id changeInMaster =
        changeOperations
            .newChange()
            .branch("master")
            .file(file1)
            .content("master content")
            .create();
    approveAndSubmit(changeInMaster);

    // Create a change in the other branch and that touches file1 and creates file2.
    Change.Id changeInOtherBranch =
        changeOperations
            .newChange()
            .branch(branchName)
            .file(file1)
            .content("other content")
            .file(file2)
            .content("content")
            .create();
    approveAndSubmit(changeInOtherBranch);

    // Create a merge change with a conflict resolution for file1 and file2 with the same content as
    // in the other branch (no conflict on file2).
    Change.Id mergeChange =
        changeOperations
            .newChange()
            .branch("master")
            .mergeOfButBaseOnFirst()
            .tipOfBranch("master")
            .and()
            .tipOfBranch(branchName)
            .file(file1)
            .content("merged content")
            .file(file2)
            .content("content")
            .create();

    ImmutableSet<ChangedFile> changedFilesSet =
        changedFiles.compute(getRevisionResource(Integer.toString(mergeChange.get())));
    ImmutableSet<String> paths =
        changedFilesSet.stream()
            .map(changedFile -> JgitPath.of(changedFile.newPath().get()).get())
            .collect(toImmutableSet());

    if (MergeCommitStrategy.ALL_CHANGED_FILES.equals(mergeCommitStrategy)) {
      assertThat(paths).containsExactly(file1, file2);
    } else if (MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION.equals(mergeCommitStrategy)) {
      assertThat(paths).containsExactly(file1);
    } else {
      fail("expected merge commit strategy: " + mergeCommitStrategy);
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.mergeCommitStrategy", value = "ALL_CHANGED_FILES")
  public void computeForMergeChangeThatContainsADeletedFileAsConflictResolution_allChangedFiles()
      throws Exception {
    testComputeForMergeChangeThatContainsADeletedFileAsConflictResolution();
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.mergeCommitStrategy",
      value = "FILES_WITH_CONFLICT_RESOLUTION")
  public void
      computeForMergeChangeThatContainsADeletedFileAsConflictResolution_filesWithConflictResolution()
          throws Exception {
    testComputeForMergeChangeThatContainsADeletedFileAsConflictResolution();
  }

  private void testComputeForMergeChangeThatContainsADeletedFileAsConflictResolution()
      throws Exception {
    String file = "foo/a.txt";

    // Create a base change.
    Change.Id baseChange =
        changeOperations.newChange().branch("master").file(file).content("base content").create();
    approveAndSubmit(baseChange);

    // Create another branch
    String branchName = "foo";
    BranchInput branchInput = new BranchInput();
    branchInput.ref = branchName;
    branchInput.revision = projectOperations.project(project).getHead("master").name();
    gApi.projects().name(project.get()).branch(branchInput.ref).create(branchInput);

    // Create a change in master that touches file1.
    Change.Id changeInMaster =
        changeOperations.newChange().branch("master").file(file).content("master content").create();
    approveAndSubmit(changeInMaster);

    // Create a change in the other branch and that deleted file1.
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "Change Deleting A File", file, "");
    Result r = push.rm("refs/for/master");
    r.assertOkStatus();
    approveAndSubmit(r.getChange().getId());

    // Create a merge change with resolving the conflict on file between the edit in master and the
    // deletion in the other branch by deleting the file.
    Change.Id mergeChange =
        changeOperations
            .newChange()
            .branch("master")
            .mergeOf()
            .tipOfBranch("master")
            .and()
            .tipOfBranch(branchName)
            .file(file)
            .delete()
            .create();

    ImmutableSet<ChangedFile> changedFilesSet =
        changedFiles.compute(getRevisionResource(Integer.toString(mergeChange.get())));
    ImmutableSet<String> oldPaths =
        changedFilesSet.stream()
            .map(changedFile -> JgitPath.of(changedFile.oldPath().get()).get())
            .collect(toImmutableSet());
    assertThat(oldPaths).containsExactly(file);
  }

  private void approveAndSubmit(Change.Id changeId) throws Exception {
    approve(Integer.toString(changeId.get()));
    gApi.changes().id(changeId.get()).current().submit();
  }

  private RevisionResource getRevisionResource(String changeId) throws Exception {
    ChangeResource changeResource =
        changesCollection.parse(TopLevelResource.INSTANCE, IdString.fromDecoded(changeId));
    return revisions.parse(changeResource, IdString.fromDecoded("current"));
  }
}
