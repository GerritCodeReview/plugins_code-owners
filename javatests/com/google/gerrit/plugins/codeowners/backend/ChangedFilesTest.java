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
import static com.google.gerrit.plugins.codeowners.testing.ChangedFileSubject.assertThatCollection;
import static com.google.gerrit.plugins.codeowners.testing.ChangedFileSubject.hasPath;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.common.ChangedFile;
import com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy;
import com.google.gerrit.plugins.codeowners.testing.ChangedFileSubject;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.restapi.change.ChangesCollection;
import com.google.inject.Inject;
import java.nio.file.Paths;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
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
  public void cannotGetFromDiffCacheForNullRevisionResource() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> changedFiles.getFromDiffCache(/* revisionResource= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("revisionResource");
  }

  @Test
  public void cannotGetFromDiffCacheForNullProject_v1() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> changedFiles.getFromDiffCache(/* project= */ null, ObjectId.zeroId()));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void cannotGetFromDiffCacheForNullRevision_v1() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> changedFiles.getFromDiffCache(project, /* revision= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("revision");
  }

  @Test
  public void cannotGetFromDiffCacheForNullProject_v2() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                changedFiles.getFromDiffCache(
                    /* project= */ null, ObjectId.zeroId(), MergeCommitStrategy.ALL_CHANGED_FILES));
    assertThat(npe).hasMessageThat().isEqualTo("project");
  }

  @Test
  public void cannotGetFromDiffCacheForNullRevision_v2() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                changedFiles.getFromDiffCache(
                    project, /* revision= */ null, MergeCommitStrategy.ALL_CHANGED_FILES));
    assertThat(npe).hasMessageThat().isEqualTo("revision");
  }

  @Test
  public void cannotGetFromDiffCacheForNullMergeCommitStrategy() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                changedFiles.getFromDiffCache(
                    project, ObjectId.zeroId(), /* mergeCommitStrategy= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("mergeCommitStrategy");
  }

  @Test
  public void getFromDiffCacheForChangeThatAddedAFile() throws Exception {
    String path = "/foo/bar/baz.txt";
    RevCommit commit =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getCommit();

    ImmutableList<ChangedFile> changedFilesSet =
        changedFiles.getFromDiffCache(project, commit, MergeCommitStrategy.ALL_CHANGED_FILES);
    assertThat(changedFilesSet).hasSize(1);
    ChangedFile changedFile = Iterables.getOnlyElement(changedFilesSet);
    assertThat(changedFile).hasNewPath().value().isEqualTo(Paths.get(path));
    assertThat(changedFile).hasOldPath().isEmpty();
    assertThat(changedFile).isNoRename();
    assertThat(changedFile).isNoDeletion();
  }

  @Test
  public void getFromDiffCacheForChangeThatModifiedAFile() throws Exception {
    String path = "/foo/bar/baz.txt";
    createChange("Test Change", JgitPath.of(path).get(), "file content").getChangeId();

    RevCommit commit =
        createChange("Change Modifying A File", JgitPath.of(path).get(), "new file content")
            .getCommit();

    ImmutableList<ChangedFile> changedFilesSet =
        changedFiles.getFromDiffCache(project, commit, MergeCommitStrategy.ALL_CHANGED_FILES);
    assertThat(changedFilesSet).hasSize(1);
    ChangedFile changedFile = Iterables.getOnlyElement(changedFilesSet);
    assertThat(changedFile).hasNewPath().value().isEqualTo(Paths.get(path));
    assertThat(changedFile).hasOldPath().value().isEqualTo(Paths.get(path));
    assertThat(changedFile).isNoRename();
    assertThat(changedFile).isNoDeletion();
  }

  @Test
  public void getFromDiffCacheForChangeThatDeletedAFile() throws Exception {
    String path = "/foo/bar/baz.txt";
    String changeId = createChangeWithFileDeletion(path);

    ImmutableList<ChangedFile> changedFilesSet =
        changedFiles.getFromDiffCache(
            project,
            getRevisionResource(changeId).getPatchSet().commitId(),
            MergeCommitStrategy.ALL_CHANGED_FILES);
    assertThat(changedFilesSet).hasSize(1);
    ChangedFile changedFile = Iterables.getOnlyElement(changedFilesSet);
    assertThat(changedFile).hasNewPath().isEmpty();
    assertThat(changedFile).hasOldPath().value().isEqualTo(Paths.get(path));
    assertThat(changedFile).isNoRename();
    assertThat(changedFile).isDeletion();
  }

  @Test
  public void getFromDiffCacheForChangeThatRenamedAFile() throws Exception {
    String oldPath = "/foo/bar/old.txt";
    String newPath = "/foo/bar/new.txt";
    String changeId = createChangeWithFileRename(oldPath, newPath);

    gApi.changes().id(changeId).current().files();

    ImmutableList<ChangedFile> changedFilesSet =
        changedFiles.getFromDiffCache(
            project,
            getRevisionResource(changeId).getPatchSet().commitId(),
            MergeCommitStrategy.ALL_CHANGED_FILES);
    ChangedFileSubject changedFile = assertThatCollection(changedFilesSet).onlyElement();
    changedFile.hasNewPath().value().isEqualTo(Paths.get(newPath));
    changedFile.hasOldPath().value().isEqualTo(Paths.get(oldPath));
    changedFile.isRename();
    changedFile.isNoDeletion();
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void getFromDiffCacheForInitialChangeThatAddedAFile() throws Exception {
    String path = "/foo/bar/baz.txt";
    RevCommit commit =
        createChange("Change Adding A File", JgitPath.of(path).get(), "file content").getCommit();
    assertThat(commit.getParents()).isEmpty();

    ImmutableList<ChangedFile> changedFilesSet =
        changedFiles.getFromDiffCache(project, commit, MergeCommitStrategy.ALL_CHANGED_FILES);
    assertThat(changedFilesSet).hasSize(1);
    ChangedFile changedFile = Iterables.getOnlyElement(changedFilesSet);
    assertThat(changedFile).hasNewPath().value().isEqualTo(Paths.get(path));
    assertThat(changedFile).hasOldPath().isEmpty();
    assertThat(changedFile).isNoRename();
    assertThat(changedFile).isNoDeletion();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.mergeCommitStrategy", value = "ALL_CHANGED_FILES")
  public void getFromFileDiffCacheForMergeChange_allChangedFiles() throws Exception {
    testGetFromFileDiffCacheForMergeChange(MergeCommitStrategy.ALL_CHANGED_FILES);
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.mergeCommitStrategy",
      value = "FILES_WITH_CONFLICT_RESOLUTION")
  public void getFromFileDiffCacheForMergeChange_filesWithConflictResolution() throws Exception {
    testGetFromFileDiffCacheForMergeChange(MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION);
  }

  private void testGetFromFileDiffCacheForMergeChange(MergeCommitStrategy mergeCommitStrategy)
      throws Exception {
    setAsRootCodeOwners(admin);

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

    ImmutableList<ChangedFile> changedFilesSet =
        changedFiles.getFromDiffCache(
            project,
            getRevisionResource(Integer.toString(mergeChange.get())).getPatchSet().commitId(),
            mergeCommitStrategy);

    if (MergeCommitStrategy.ALL_CHANGED_FILES.equals(mergeCommitStrategy)) {
      assertThat(changedFilesSet).comparingElementsUsing(hasPath()).containsExactly(file1, file2);
    } else if (MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION.equals(mergeCommitStrategy)) {
      assertThat(changedFilesSet).comparingElementsUsing(hasPath()).containsExactly(file1);
    } else {
      fail("expected merge commit strategy: " + mergeCommitStrategy);
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.mergeCommitStrategy", value = "ALL_CHANGED_FILES")
  public void
      getFromFileDiffCacheForMergeChangeThatContainsADeletedFileAsConflictResolution_allChangedFiles()
          throws Exception {
    testGetFromFileDiffCacheForMergeChangeThatContainsADeletedFileAsConflictResolution(
        MergeCommitStrategy.ALL_CHANGED_FILES);
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.mergeCommitStrategy",
      value = "FILES_WITH_CONFLICT_RESOLUTION")
  public void
      getFromFileDiffCacheForMergeChangeThatContainsADeletedFileAsConflictResolution_filesWithConflictResolution()
          throws Exception {
    testGetFromFileDiffCacheForMergeChangeThatContainsADeletedFileAsConflictResolution(
        MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION);
  }

  private void testGetFromFileDiffCacheForMergeChangeThatContainsADeletedFileAsConflictResolution(
      MergeCommitStrategy mergeCommitStrategy) throws Exception {
    setAsRootCodeOwners(admin);

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

    ImmutableList<ChangedFile> changedFilesSet =
        changedFiles.getFromDiffCache(
            project,
            getRevisionResource(Integer.toString(mergeChange.get())).getPatchSet().commitId(),
            mergeCommitStrategy);
    ImmutableSet<String> oldPaths =
        changedFilesSet.stream()
            .map(changedFile -> JgitPath.of(changedFile.oldPath().get()).get())
            .collect(toImmutableSet());
    assertThat(oldPaths).containsExactly(file);
  }

  @Test
  public void getFromDiffCacheReturnsChangedFilesSortedByPath() throws Exception {
    String file1 = "foo/bar.baz";
    String file2 = "foo/baz.bar";
    String file3 = "bar/foo.baz";
    String file4 = "bar/baz.foo";
    String file5 = "baz/foo.bar";
    RevCommit commit =
        createChange(
                "Test Change",
                ImmutableMap.of(
                    file1,
                    "file content",
                    file2,
                    "file content",
                    file3,
                    "file content",
                    file4,
                    "file content",
                    file5,
                    "file content"))
            .getCommit();

    ImmutableList<ChangedFile> changedFilesSet =
        changedFiles.getFromDiffCache(project, commit, MergeCommitStrategy.ALL_CHANGED_FILES);
    assertThat(changedFilesSet)
        .comparingElementsUsing(hasPath())
        .containsExactly(file4, file3, file5, file1, file2)
        .inOrder();
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.mergeCommitStrategy", value = "ALL_CHANGED_FILES")
  public void getFromDiffCacheReturnsChangedFilesSortedByPath_mergeCommitAgainstFirstParent()
      throws Exception {
    testGetFromDiffCacheReturnsChangedFilesSortedByPathForMerge(
        MergeCommitStrategy.ALL_CHANGED_FILES);
  }

  @Test
  @GerritConfig(
      name = "plugin.code-owners.mergeCommitStrategy",
      value = "FILES_WITH_CONFLICT_RESOLUTION")
  public void getFromDiffCacheReturnsChangedFilesSortedByPath_mergeCommitAgainstAutoMerge()
      throws Exception {
    testGetFromDiffCacheReturnsChangedFilesSortedByPathForMerge(
        MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION);
  }

  private void testGetFromDiffCacheReturnsChangedFilesSortedByPathForMerge(
      MergeCommitStrategy mergeCommitStrategy) throws Exception {
    setAsRootCodeOwners(admin);

    String file1 = "foo/bar.baz";
    String file2 = "foo/baz.bar";
    String file3 = "bar/foo.baz";
    String file4 = "bar/baz.foo";
    String file5 = "baz/foo.bar";

    // Create a base change.
    Change.Id baseChange =
        changeOperations
            .newChange()
            .branch("master")
            .file(file1)
            .content("base content")
            .file(file3)
            .content("base content")
            .file(file5)
            .content("base content")
            .create();
    approveAndSubmit(baseChange);

    // Create another branch
    String branchName = "foo";
    BranchInput branchInput = new BranchInput();
    branchInput.ref = branchName;
    branchInput.revision = projectOperations.project(project).getHead("master").name();
    gApi.projects().name(project.get()).branch(branchInput.ref).create(branchInput);

    // Create a change in master that touches file1, file3 and file5
    Change.Id changeInMaster =
        changeOperations
            .newChange()
            .branch("master")
            .file(file1)
            .content("master content")
            .file(file3)
            .content("master content")
            .file(file5)
            .content("master content")
            .create();
    approveAndSubmit(changeInMaster);

    // Create a change in the other branch and that touches file1, file3, file5 and creates file2,
    // file4.
    Change.Id changeInOtherBranch =
        changeOperations
            .newChange()
            .branch(branchName)
            .file(file1)
            .content("other content")
            .file(file2)
            .content("content")
            .file(file3)
            .content("other content")
            .file(file4)
            .content("content")
            .file(file5)
            .content("other content")
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
            .file(file3)
            .content("merged content")
            .file(file4)
            .content("content")
            .file(file5)
            .content("merged content")
            .create();

    ImmutableList<ChangedFile> changedFilesSet =
        changedFiles.getFromDiffCache(
            project,
            getRevisionResource(Integer.toString(mergeChange.get())).getPatchSet().commitId(),
            mergeCommitStrategy);

    if (MergeCommitStrategy.ALL_CHANGED_FILES.equals(mergeCommitStrategy)) {
      assertThat(changedFilesSet)
          .comparingElementsUsing(hasPath())
          .containsExactly(file4, file3, file5, file1, file2)
          .inOrder();
    } else if (MergeCommitStrategy.FILES_WITH_CONFLICT_RESOLUTION.equals(mergeCommitStrategy)) {
      assertThat(changedFilesSet)
          .comparingElementsUsing(hasPath())
          .containsExactly(file3, file5, file1);
    } else {
      fail("expected merge commit strategy: " + mergeCommitStrategy);
    }
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
