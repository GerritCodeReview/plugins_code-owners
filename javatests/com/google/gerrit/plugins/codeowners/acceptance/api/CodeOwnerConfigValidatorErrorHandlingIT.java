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

package com.google.gerrit.plugins.codeowners.acceptance.api;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigUpdate;
import com.google.gerrit.plugins.codeowners.backend.PathExpressionMatcher;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Key;
import com.google.inject.util.Providers;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@code com.google.gerrit.plugins.codeowners.validation.CodeOwnerConfigValidator} that
 * verify the error handling during the validation.
 */
public class CodeOwnerConfigValidatorErrorHandlingIT extends AbstractCodeOwnersIT {
  private DynamicMap<CodeOwnerBackend> codeOwnerBackends;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerBackends =
        plugin.getSysInjector().getInstance(new Key<DynamicMap<CodeOwnerBackend>>() {});
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = FailingCodeOwnerBackend.ID)
  public void pushFailsOnInternalError() throws Exception {
    try (AutoCloseable registration = registerTestBackend(new FailingCodeOwnerBackend())) {
      PushOneCommit.Result r = createChange("Add code owners", "OWNERS", "content");
      r.assertErrorStatus("internal error");
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = FailingCodeOwnerBackend.ID)
  @GerritConfig(name = "plugin.code-owners.enableValidationOnCommitReceived", value = "DRY_RUN")
  public void pushSucceedsOnInternalErrorIfValidationIsDoneAsDryRun() throws Exception {
    try (AutoCloseable registration = registerTestBackend(new FailingCodeOwnerBackend())) {
      PushOneCommit.Result r = createChange("Add code owners", "OWNERS", "content");
      r.assertOkStatus();
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = FailingCodeOwnerBackend.ID)
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "PROJECT_OWNERS")
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "true")
  public void submitFailsOnInternalError() throws Exception {
    try (AutoCloseable registration = registerTestBackend(new FailingCodeOwnerBackend())) {
      disableCodeOwnersForProject(project);
      PushOneCommit.Result r = createChange("Add code owners", "OWNERS", "content");
      r.assertOkStatus();
      enableCodeOwnersForProject(project);
      approve(r.getChangeId());
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> gApi.changes().id(r.getChangeId()).current().submit());
      assertThat(exception).hasMessageThat().isEqualTo(FailingCodeOwnerBackend.EXCEPTION_MESSAGE);
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = FailingCodeOwnerBackend.ID)
  @GerritConfig(name = "plugin.code-owners.enableValidationOnSubmit", value = "DRY_RUN")
  @GerritConfig(name = "plugin.code-owners.fallbackCodeOwners", value = "PROJECT_OWNERS")
  public void submitSucceedsOnInternalErrorIfValidationIsDoneAsDryRun() throws Exception {
    try (AutoCloseable registration = registerTestBackend(new FailingCodeOwnerBackend())) {
      disableCodeOwnersForProject(project);
      PushOneCommit.Result r = createChange("Add code owners", "OWNERS", "content");
      r.assertOkStatus();
      enableCodeOwnersForProject(project);
      approve(r.getChangeId());
      gApi.changes().id(r.getChangeId()).current().submit();
      assertThat(gApi.changes().id(r.getChangeId()).get().status).isEqualTo(ChangeStatus.MERGED);
    }
  }

  private AutoCloseable registerTestBackend(CodeOwnerBackend codeOwnerBackend) {
    RegistrationHandle registrationHandle =
        ((PrivateInternals_DynamicMapImpl<CodeOwnerBackend>) codeOwnerBackends)
            .put("gerrit", FailingCodeOwnerBackend.ID, Providers.of(codeOwnerBackend));
    return registrationHandle::remove;
  }

  private static class FailingCodeOwnerBackend implements CodeOwnerBackend {
    static final String ID = "test-backend";
    static final String EXCEPTION_MESSAGE = "failure from test";

    @Override
    public boolean isCodeOwnerConfigFile(NameKey project, String fileName) {
      throw new IllegalStateException(EXCEPTION_MESSAGE);
    }

    @Override
    public Optional<CodeOwnerConfig> getCodeOwnerConfig(
        CodeOwnerConfig.Key codeOwnerConfigKey, RevWalk revWalk, ObjectId revision) {
      return Optional.empty();
    }

    @Override
    public Path getFilePath(CodeOwnerConfig.Key codeOwnerConfigKey) {
      return codeOwnerConfigKey.filePath("OWNERS");
    }

    @Override
    public Optional<CodeOwnerConfig> upsertCodeOwnerConfig(
        CodeOwnerConfig.Key codeOwnerConfigKey,
        CodeOwnerConfigUpdate codeOwnerConfigUpdate,
        IdentifiedUser currentUser) {
      return Optional.empty();
    }

    @Override
    public Optional<PathExpressionMatcher> getPathExpressionMatcher() {
      return Optional.empty();
    }
  }
}
