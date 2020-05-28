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

import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Key;
import com.google.inject.util.Providers;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwners}. */
public class CodeOwnersTest extends AbstractCodeOwnersTest {
  private CodeOwners codeOwners;
  private DynamicMap<CodeOwnersBackend> codeOwnersBackends;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwners = plugin.getSysInjector().getInstance(CodeOwners.class);
    codeOwnersBackends =
        plugin.getSysInjector().getInstance(new Key<DynamicMap<CodeOwnersBackend>>() {});
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = TestCodeOwnersBackend.ID)
  public void codeOwnerConfigIsRetrievedFromConfiguredBackend() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig expectedCodeOwnersConfig =
        CodeOwnerConfig.builder(codeOwnerConfigKey).addCodeOwnerEmail(admin.email()).build();
    try (AutoCloseable registration =
        registerTestBackend(new TestCodeOwnersBackend(expectedCodeOwnersConfig))) {
      Optional<CodeOwnerConfig> codeOwnerConfig = codeOwners.get(codeOwnerConfigKey);
      assertThat(codeOwnerConfig).isPresent();
      assertThat(codeOwnerConfig.get()).isEqualTo(expectedCodeOwnersConfig);
    }
  }

  private AutoCloseable registerTestBackend(CodeOwnersBackend codeOwnersBackend) {
    RegistrationHandle registrationHandle =
        ((PrivateInternals_DynamicMapImpl<CodeOwnersBackend>) codeOwnersBackends)
            .put("gerrit", TestCodeOwnersBackend.ID, Providers.of(codeOwnersBackend));
    return () -> registrationHandle.remove();
  }

  private static class TestCodeOwnersBackend implements CodeOwnersBackend {
    static final String ID = "test-backend";

    private final CodeOwnerConfig codeOwnerConfig;

    TestCodeOwnersBackend(CodeOwnerConfig codeOwnerConfig) {
      this.codeOwnerConfig = codeOwnerConfig;
    }

    @Override
    public Optional<CodeOwnerConfig> getCodeOwnerConfig(CodeOwnerConfig.Key codeOwnerConfigKey) {
      return Optional.of(codeOwnerConfig);
    }

    @Override
    public Optional<CodeOwnerConfig> upsertCodeOwnerConfig(
        CodeOwnerConfig.Key codeOwnerConfigKey,
        CodeOwnerConfigUpdate codeOwnerConfigUpdate,
        @Nullable IdentifiedUser currentUser) {
      throw new UnsupportedOperationException("not implemented");
    }
  }
}
