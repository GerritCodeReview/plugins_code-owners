// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.plugins.codeowners.backend.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.entities.RefNames;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.FallbackCodeOwners;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link CodeOwnersPluginConfigValidator}.
 *
 * <p>Integration tests for {@link CodeOwnersPluginConfigValidator} are contained in {@code
 * com.google.gerrit.plugins.codeowners.acceptance.api.CodeOwnersPluginConfigValidatorIT}.
 */
public class CodeOwnersPluginConfigValidatorTest extends AbstractCodeOwnersTest {
  private CodeOwnersPluginConfigValidator codeOwnersPluginConfigValidator;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnersPluginConfigValidator =
        plugin.getSysInjector().getInstance(CodeOwnersPluginConfigValidator.class);
  }

  @Test
  public void failsOnInvalidProjectConfig() throws Exception {
    Config cfg = new Config();
    cfg.setEnum(
        CodeOwnersPluginConfiguration.SECTION_CODE_OWNERS,
        /* subsection= */ null,
        GeneralConfig.KEY_FALLBACK_CODE_OWNERS,
        FallbackCodeOwners.ALL_USERS);
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      RevCommit commit =
          testRepo
              .commit()
              .add("code-owners.config", cfg.toText())
              .add("project.config", "INVALID")
              .create();

      CommitReceivedEvent receiveEvent = new CommitReceivedEvent();
      receiveEvent.project =
          projectCache.get(project).orElseThrow(illegalState(project)).getProject();
      receiveEvent.refName = RefNames.REFS_CONFIG;
      receiveEvent.commit = commit;
      receiveEvent.revWalk = testRepo.getRevWalk();
      receiveEvent.repoConfig = new Config();
      CommitValidationException exception =
          assertThrows(
              CommitValidationException.class,
              () -> codeOwnersPluginConfigValidator.onCommitReceived(receiveEvent));
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo(
              String.format(
                  "failed to validate file code-owners.config for revision %s in ref %s of project %s",
                  commit.getName(), RefNames.REFS_CONFIG, project));
      assertThat(exception).hasCauseThat().isInstanceOf(ConfigInvalidException.class);
    }
  }
}
