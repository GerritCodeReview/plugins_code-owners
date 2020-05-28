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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.UserInitiated;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnersUpdate}. */
public class CodeOwnersUpdateTest extends AbstractCodeOwnersTest {
  @Inject private Provider<IdentifiedUser> currentUserProvider;

  private Provider<CodeOwnersUpdate> userInitiatedCodeOwnersUpdate;
  private Provider<CodeOwnersUpdate> serverInitiatedCodeOwnersUpdate;
  private DynamicMap<CodeOwnersBackend> codeOwnersBackends;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    userInitiatedCodeOwnersUpdate =
        plugin
            .getSysInjector()
            .getInstance(
                Key.get(new TypeLiteral<Provider<CodeOwnersUpdate>>() {}, UserInitiated.class));
    serverInitiatedCodeOwnersUpdate =
        plugin
            .getSysInjector()
            .getInstance(
                Key.get(new TypeLiteral<Provider<CodeOwnersUpdate>>() {}, ServerInitiated.class));
    codeOwnersBackends =
        plugin.getSysInjector().getInstance(new Key<DynamicMap<CodeOwnersBackend>>() {});
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = "test-backend")
  public void codeOwnerUpdateIsForwardedToConfiguredBackendUserInitiated() throws Exception {
    testCodeOwnerUpdateIsForwardedToConfiguredBackend(
        userInitiatedCodeOwnersUpdate.get(), currentUserProvider.get());
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.backend", value = "test-backend")
  public void codeOwnerUpdateIsForwardedToConfiguredBackendServerInitiated() throws Exception {
    testCodeOwnerUpdateIsForwardedToConfiguredBackend(serverInitiatedCodeOwnersUpdate.get(), null);
  }

  private void testCodeOwnerUpdateIsForwardedToConfiguredBackend(
      CodeOwnersUpdate codeOwnersUpdate, @Nullable IdentifiedUser currentUser) throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfigUpdate codeOwnerConfigUpdate =
        CodeOwnerConfigUpdate.builder()
            .setCodeOwnerModification(
                codeOwners ->
                    Sets.union(
                        codeOwners, ImmutableSet.of(CodeOwnerReference.create(admin.email()))))
            .build();
    CodeOwnersBackend codeOwnersBackendMock = mock(CodeOwnersBackend.class);
    try (AutoCloseable registration = registerTestBackend("test-backend", codeOwnersBackendMock)) {
      codeOwnersUpdate.upsertCodeOwnerConfig(codeOwnerConfigKey, codeOwnerConfigUpdate);
      verify(codeOwnersBackendMock)
          .upsertCodeOwnerConfig(codeOwnerConfigKey, codeOwnerConfigUpdate, currentUser);
    }
  }

  private AutoCloseable registerTestBackend(String id, CodeOwnersBackend codeOwnersBackend) {
    RegistrationHandle registrationHandle =
        ((PrivateInternals_DynamicMapImpl<CodeOwnersBackend>) codeOwnersBackends)
            .put("gerrit", id, Providers.of(codeOwnersBackend));
    return () -> registrationHandle.remove();
  }
}
