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
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwners}. */
public class CodeOwnersTest extends AbstractCodeOwnersTest {
  private static final ObjectId TEST_REVISION =
      ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

  private CodeOwners codeOwners;
  private DynamicMap<CodeOwnerBackend> codeOwnerBackends;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwners = plugin.getSysInjector().getInstance(CodeOwners.class);
    codeOwnerBackends =
        plugin.getSysInjector().getInstance(new Key<DynamicMap<CodeOwnerBackend>>() {});
  }

  @Test
  public void cannotGetCodeOwnerConfigForNullCodeOwnerConfigKey() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> codeOwners.get(/* codeOwnerConfigKey= */ null, TEST_REVISION));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfigKey");
  }

  @Test
  public void cannotGetCodeOwnerConfigForNullRevision() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () ->
                codeOwners.get(
                    CodeOwnerConfig.Key.create(project, "master", "/"), /* folderPath= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("revision");
  }

  @Test
  public void cannotGetCodeOwnerConfigFromCurrentRevisionForNullCodeOwnerConfigKey()
      throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> codeOwners.getFromCurrentRevision(/* codeOwnerConfigKey= */ null));
    assertThat(npe).hasMessageThat().isEqualTo("codeOwnerConfigKey");
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = "test-backend")
  public void codeOwnerConfigIsRetrievedFromConfiguredBackend() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig expectedCodeOwnersConfig =
        CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
            .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
            .build();
    CodeOwnerBackend codeOwnerBackendMock = mock(CodeOwnerBackend.class);
    when(codeOwnerBackendMock.getCodeOwnerConfig(codeOwnerConfigKey, /* revision= */ null))
        .thenReturn(Optional.of(expectedCodeOwnersConfig));
    try (AutoCloseable registration = registerTestBackend("test-backend", codeOwnerBackendMock)) {
      Optional<CodeOwnerConfig> codeOwnerConfig =
          codeOwners.getFromCurrentRevision(codeOwnerConfigKey);
      assertThat(codeOwnerConfig).value().isEqualTo(expectedCodeOwnersConfig);
      verify(codeOwnerBackendMock).getCodeOwnerConfig(codeOwnerConfigKey, /* revision= */ null);
    }
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = "test-backend")
  public void revisionIsPassedToConfiguredBackend() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfig expectedCodeOwnersConfig =
        CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
            .addCodeOwnerSet(CodeOwnerSet.createWithoutPathExpressions(admin.email()))
            .build();
    CodeOwnerBackend codeOwnerBackendMock = mock(CodeOwnerBackend.class);
    when(codeOwnerBackendMock.getCodeOwnerConfig(codeOwnerConfigKey, TEST_REVISION))
        .thenReturn(Optional.of(expectedCodeOwnersConfig));
    try (AutoCloseable registration = registerTestBackend("test-backend", codeOwnerBackendMock)) {
      Optional<CodeOwnerConfig> codeOwnerConfig = codeOwners.get(codeOwnerConfigKey, TEST_REVISION);
      assertThat(codeOwnerConfig).value().isEqualTo(expectedCodeOwnersConfig);
      verify(codeOwnerBackendMock).getCodeOwnerConfig(codeOwnerConfigKey, TEST_REVISION);
    }
  }

  private AutoCloseable registerTestBackend(String id, CodeOwnerBackend codeOwnerBackend) {
    RegistrationHandle registrationHandle =
        ((PrivateInternals_DynamicMapImpl<CodeOwnerBackend>) codeOwnerBackends)
            .put("gerrit", id, Providers.of(codeOwnerBackend));
    return registrationHandle::remove;
  }
}
