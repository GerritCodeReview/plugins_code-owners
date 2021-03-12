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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

/** Tests for {@link CodeOwnerConfigFileUpdateScanner}. */
public class CodeOwnerConfigFileUpdateScannerTest extends AbstractCodeOwnersTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Mock private CodeOwnerConfigFileUpdater updater;

  @Inject private ProjectOperations projectOperations;

  private CodeOwnerConfigOperations codeOwnerConfigOperations;
  private CodeOwnerConfigFileUpdateScanner codeOwnerConfigFileUpdateScanner;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerConfigOperations =
        plugin.getSysInjector().getInstance(CodeOwnerConfigOperations.class);
    codeOwnerConfigFileUpdateScanner =
        plugin.getSysInjector().getInstance(CodeOwnerConfigFileUpdateScanner.class);
  }

  @Test
  public void cannotUpdateCodeOwnerConfigsForNullBranch() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwnerConfigFileUpdateScanner.update(
                    /* branchNameKey= */ null,
                    "Update code owner configs",
                    (codeOwnerConfigFilePath, codeOwnerConfigFileContent) -> Optional.empty()));
    assertThat(npe).hasMessageThat().isEqualTo("branchNameKey");
  }

  @Test
  public void cannotUpdateCodeOwnerConfigsWithNullCommitMessage() throws Exception {
    BranchNameKey branchNameKey = BranchNameKey.create(project, "master");
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwnerConfigFileUpdateScanner.update(
                    branchNameKey,
                    /* commitMessage= */ null,
                    (codeOwnerConfigFilePath, codeOwnerConfigFileContent) -> Optional.empty()));
    assertThat(npe).hasMessageThat().isEqualTo("commitMessage");
  }

  @Test
  public void cannotUpdateCodeOwnerConfigsWithNullUpdater() throws Exception {
    BranchNameKey branchNameKey = BranchNameKey.create(project, "master");
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwnerConfigFileUpdateScanner.update(
                    branchNameKey,
                    "Update code owner configs",
                    /* codeOwnerConfigFileUpdater= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfigFileUpdater");
  }

  @Test
  public void cannotUpdateCodeOwnerConfigsForNonExistingBranch() throws Exception {
    BranchNameKey branchNameKey = BranchNameKey.create(project, "non-existing");
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                codeOwnerConfigFileUpdateScanner.update(
                    branchNameKey,
                    "Update code owner configs",
                    (codeOwnerConfigFilePath, codeOwnerConfigFileContent) -> Optional.empty()));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "branch %s of project %s not found", branchNameKey.branch(), project.get()));
  }

  @Test
  public void noUpdateIfNoCodeOwnerConfigFilesExists() throws Exception {
    Optional<RevCommit> commit =
        codeOwnerConfigFileUpdateScanner.update(
            BranchNameKey.create(project, "master"), "Update code owner configs", updater);
    assertThat(commit).isEmpty();
    verifyNoInteractions(updater);
  }

  @Test
  public void noUpdateForNonCodeOwnerConfigFiles() throws Exception {
    // Create some non code owner config files.
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      Ref ref = testRepo.getRepository().exactRef("refs/heads/master");
      RevCommit head = testRepo.getRevWalk().parseCommit(ref.getObjectId());
      testRepo.update(
          "refs/heads/master",
          testRepo
              .commit()
              .parent(head)
              .message("Add some non code owner config files")
              .add("owners.txt", "some content")
              .add("owners", "some content")
              .add("foo/bar/owners.txt", "some content")
              .add("foo/bar/owners", "some content"));
    }

    Optional<RevCommit> commit =
        codeOwnerConfigFileUpdateScanner.update(
            BranchNameKey.create(project, "master"), "Update code owner configs", updater);
    assertThat(commit).isEmpty();
    verifyNoInteractions(updater);
  }

  @Test
  public void noUpdateIfCallbackDoesntReturnNewFileContent() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(admin.email())
            .create();
    Path path =
        Paths.get(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getFilePath());
    String content = codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getContent();

    RevCommit oldHead = projectOperations.project(project).getHead("master");

    when(updater.update(path, content)).thenReturn(Optional.empty());
    Optional<RevCommit> commit =
        codeOwnerConfigFileUpdateScanner.update(
            BranchNameKey.create(project, "master"), "Update code owner configs", updater);
    assertThat(commit).isEmpty();

    // Verify the code owner config file was not updated.
    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getContent())
        .isEqualTo(content);

    // Check that no commit was created.
    RevCommit newHead = projectOperations.project(project).getHead("master");
    assertThat(newHead).isEqualTo(oldHead);
  }

  @Test
  public void updateCodeOwnerConfigFiles() throws Exception {
    TestAccount user2 = accountCreator.user2();

    CodeOwnerConfig.Key codeOwnerConfigKey1 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(admin.email())
            .create();
    Path path1 =
        Paths.get(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey1).getFilePath());
    String oldContent1 =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey1).getContent();
    String newContent1 = user.email() + "\n";
    when(updater.update(path1, oldContent1)).thenReturn(Optional.of(newContent1));

    CodeOwnerConfig.Key codeOwnerConfigKey2 =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch("master")
            .folderPath("/foo/")
            .fileName("OWNERS")
            .addCodeOwnerEmail(user.email())
            .create();
    Path path2 =
        Paths.get(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey2).getFilePath());
    String oldContent2 =
        codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey2).getContent();
    String newContent2 = user2.email() + "\n";
    when(updater.update(path2, oldContent2)).thenReturn(Optional.of(newContent2));

    RevCommit oldHead = projectOperations.project(project).getHead("master");

    String commitMessage = "Update code owner configs";
    Optional<RevCommit> commit =
        codeOwnerConfigFileUpdateScanner.update(
            BranchNameKey.create(project, "master"), commitMessage, updater);
    assertThat(commit).isPresent();

    // Verify that we received the expected callbacks for the invalid code onwer config.
    Mockito.verify(updater).update(path1, oldContent1);
    Mockito.verify(updater).update(path2, oldContent2);
    verifyNoMoreInteractions(updater);

    // Verify the code owner config files were updated.
    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey1).getContent())
        .isEqualTo(newContent1);
    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey2).getContent())
        .isEqualTo(newContent2);

    // Check that exactly 1 commit was created.
    RevCommit newHead = projectOperations.project(project).getHead("master");
    assertThat(commit.get()).isEqualTo(newHead);
    assertThat(newHead).isNotEqualTo(oldHead);
    assertThat(newHead.getShortMessage()).isEqualTo(commitMessage);
    assertThat(newHead.getParent(0)).isEqualTo(oldHead);
  }

  @Test
  public void updateInvalidCodeOwnerConfigFile() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = createInvalidCodeOwnerConfig("/OWNERS", "INVALID");

    when(updater.update(any(Path.class), any(String.class)))
        .thenReturn(Optional.of("STILL INVALID"));
    Optional<RevCommit> update =
        codeOwnerConfigFileUpdateScanner.update(
            BranchNameKey.create(project, "master"), "Update code owner configs", updater);
    assertThat(update).isPresent();

    // Verify that we received the expected callbacks for the invalid code onwer config.
    Mockito.verify(updater).update(Paths.get("/OWNERS"), "INVALID");
    verifyNoMoreInteractions(updater);

    // Verify the code owner config file was updated.
    assertThat(codeOwnerConfigOperations.codeOwnerConfig(codeOwnerConfigKey).getContent())
        .isEqualTo("STILL INVALID");
  }

  private CodeOwnerConfig.Key createInvalidCodeOwnerConfig(String filePath, String content)
      throws Exception {
    disableCodeOwnersForProject(project);
    String changeId =
        createChange("Add invalid code owners file", JgitPath.of(filePath).get(), content)
            .getChangeId();
    approve(changeId);
    gApi.changes().id(changeId).current().submit();
    enableCodeOwnersForProject(project);
    Path path = Paths.get(filePath);
    return CodeOwnerConfig.Key.create(
        project, "master", path.getParent().toString(), path.getFileName().toString());
  }
}
