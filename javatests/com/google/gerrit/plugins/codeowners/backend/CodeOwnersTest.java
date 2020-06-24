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
import static com.google.gerrit.truth.OptionalSubject.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
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
  @GerritConfig(name = "plugin.code-owners.backend", value = "test-backend")
  public void codeOwnerConfigIsRetrievedFromConfiguredBackend() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig expectedCodeOwnersConfig =
        CodeOwnerConfig.builder(codeOwnerConfigKey)
            .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
            .build();
    CodeOwnersBackend codeOwnersBackendMock = mock(CodeOwnersBackend.class);
    when(codeOwnersBackendMock.getCodeOwnerConfig(codeOwnerConfigKey))
        .thenReturn(Optional.of(expectedCodeOwnersConfig));
    try (AutoCloseable registration = registerTestBackend("test-backend", codeOwnersBackendMock)) {
      Optional<CodeOwnerConfig> codeOwnerConfig = codeOwners.get(codeOwnerConfigKey);
      assertThat(codeOwnerConfig).value().isEqualTo(expectedCodeOwnersConfig);
      verify(codeOwnersBackendMock).getCodeOwnerConfig(codeOwnerConfigKey);
    }
  }

  private AutoCloseable registerTestBackend(String id, CodeOwnersBackend codeOwnersBackend) {
    RegistrationHandle registrationHandle =
        ((PrivateInternals_DynamicMapImpl<CodeOwnersBackend>) codeOwnersBackends)
            .put("gerrit", id, Providers.of(codeOwnersBackend));
    return () -> registrationHandle.remove();
  }
}
