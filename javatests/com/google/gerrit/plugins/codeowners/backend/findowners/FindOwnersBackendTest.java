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

package com.google.gerrit.plugins.codeowners.backend.findowners;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.plugins.codeowners.backend.AbstractFileBasedCodeOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.AbstractFileBasedCodeOwnersBackendTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigUpdate;
import com.google.gerrit.server.IdentifiedUser;
import java.util.Optional;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/** Tests for {@link FindOwnersBackend}. */
public class FindOwnersBackendTest extends AbstractFileBasedCodeOwnersBackendTest {
  @Override
  protected Class<? extends AbstractFileBasedCodeOwnersBackend> getBackendClass() {
    return FindOwnersBackend.class;
  }

  @Override
  protected String getFileName() {
    return FindOwnersBackend.CODE_OWNER_CONFIG_FILE_NAME;
  }

  @Override
  protected Class<? extends CodeOwnerConfigParser> getParserClass() {
    return FindOwnersCodeOwnerConfigParser.class;
  }

  @Test
  public void deleteCodeOwnerConfigInitiatedByServer() throws Exception {
    testDeleteCodeOwnerConfigInitiatedByServer(null);
  }

  @Test
  public void deleteCodeOwnerConfigInitiatedByUser() throws Exception {
    testDeleteCodeOwnerConfigInitiatedByServer(identifiedUserFactory.create(user.id()));
  }

  private void testDeleteCodeOwnerConfigInitiatedByServer(@Nullable IdentifiedUser currentUser)
      throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig codeOwnerConfigInRepository =
        CodeOwnerConfig.builder(codeOwnerConfigKey).addCodeOwnerEmail(admin.email()).build();
    backendTestUtil.writeCodeOwnerConfig(codeOwnerConfigInRepository);

    try (Repository repo = repoManager.openRepository(project)) {
      // Remember head for later assertions.
      RevCommit origHead = getHead(repo, codeOwnerConfigKey.ref());

      // Update the code owner config.
      Optional<CodeOwnerConfig> codeOwnerConfig =
          codeOwnersBackend.upsertCodeOwnerConfig(
              codeOwnerConfigKey,
              CodeOwnerConfigUpdate.builder()
                  .setCodeOwnerModification(codeOwners -> ImmutableSet.of())
                  .build(),
              currentUser);
      assertThat(codeOwnerConfig).isEmpty();

      // Check the metadata of the created commit.
      RevCommit newHead = getHead(repo, codeOwnerConfigKey.ref());
      assertThat(newHead.getParent(0)).isEqualTo(origHead);
      assertThat(newHead.getShortMessage()).isEqualTo("Delete code owner config");

      // Check author identity.
      if (currentUser != null) {
        assertThat(newHead.getAuthorIdent().getEmailAddress())
            .isEqualTo(currentUser.getAccount().preferredEmail());
      } else {
        assertThat(newHead.getAuthorIdent().getEmailAddress())
            .isEqualTo(serverIdent.get().getEmailAddress());
        assertThat(newHead.getCommitterIdent()).isEqualTo(newHead.getAuthorIdent());
      }

      // Check committer identity.
      assertThat(newHead.getCommitterIdent().getEmailAddress())
          .isEqualTo(serverIdent.get().getEmailAddress());
      assertThat(newHead.getCommitterIdent().getTimeZone())
          .isEqualTo(newHead.getAuthorIdent().getTimeZone());
      assertThat(newHead.getCommitterIdent().getWhen().getTime())
          .isEqualTo(newHead.getAuthorIdent().getWhen().getTime());
    }
  }
}
