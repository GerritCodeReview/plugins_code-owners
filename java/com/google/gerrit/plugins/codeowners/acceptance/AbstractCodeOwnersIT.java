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

package com.google.gerrit.plugins.codeowners.acceptance;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.api.impl.ChangeCodeOwnersFactory;
import com.google.gerrit.plugins.codeowners.api.impl.CodeOwnerConfigsFactory;
import com.google.gerrit.plugins.codeowners.api.impl.CodeOwnersFactory;
import com.google.gerrit.plugins.codeowners.api.impl.ProjectCodeOwnersFactory;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackendId;
import com.google.gerrit.plugins.codeowners.backend.config.BackendConfig;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import com.google.gerrit.testing.ConfigSuite;
import java.util.Arrays;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;

/**
 * Base class for code owner integration/acceptance tests.
 *
 * <p>The code owner API and test API classes cannot be injected into the test classes because the
 * plugin is not loaded yet when injections are resolved. Instantiating them will require a bit of
 * boilerplate code. We prefer to have this boilerplate code only once here in this base class, as
 * having this code in every test class would be too much overhead.
 *
 * <p>This class also defines {@link ConfigSuite}s to execute each integration/acceptance test for
 * all code owner backends.
 */
public class AbstractCodeOwnersIT extends AbstractCodeOwnersTest {
  /**
   * Returns a {@code gerrit.config} without code owner backend configuration to test the default
   * setup.
   */
  @ConfigSuite.Default
  public static Config defaultConfig() {
    return new Config();
  }

  /**
   * Returns a {@code gerrit.config} for every code owner backend so that all code owner backends
   * are tested.
   */
  @ConfigSuite.Configs
  public static ImmutableMap<String, Config> againstCodeOwnerBackends() {
    return Arrays.stream(CodeOwnerBackendId.values())
        .collect(
            toImmutableMap(
                CodeOwnerBackendId::getBackendId,
                AbstractCodeOwnersIT::createConfigWithCodeOwnerBackend));
  }

  private static Config createConfigWithCodeOwnerBackend(CodeOwnerBackendId codeOwnerBackendId) {
    Config cfg = new Config(defaultConfig());
    cfg.setString(
        "plugin", "code-owners", BackendConfig.KEY_BACKEND, codeOwnerBackendId.getBackendId());
    return cfg;
  }

  protected CodeOwnerConfigOperations codeOwnerConfigOperations;
  protected CodeOwnerConfigsFactory codeOwnerConfigsApiFactory;
  protected CodeOwnersFactory codeOwnersApiFactory;
  protected ChangeCodeOwnersFactory changeCodeOwnersApiFactory;
  protected ProjectCodeOwnersFactory projectCodeOwnersApiFactory;
  protected BackendConfig backendConfig;

  @Before
  public void baseSetup() throws Exception {
    codeOwnerConfigOperations =
        plugin.getSysInjector().getInstance(CodeOwnerConfigOperations.class);
    codeOwnerConfigsApiFactory = plugin.getSysInjector().getInstance(CodeOwnerConfigsFactory.class);
    codeOwnersApiFactory = plugin.getSysInjector().getInstance(CodeOwnersFactory.class);
    changeCodeOwnersApiFactory = plugin.getSysInjector().getInstance(ChangeCodeOwnersFactory.class);
    projectCodeOwnersApiFactory =
        plugin.getSysInjector().getInstance(ProjectCodeOwnersFactory.class);
    backendConfig = plugin.getSysInjector().getInstance(BackendConfig.class);
  }

  protected void skipTestIfImportsNotSupportedByCodeOwnersBackend() {
    // the proto backend doesn't support imports
    assumeThatCodeOwnersBackendIsNotProtoBackend();
  }

  protected void skipTestIfIgnoreParentCodeOwnersNotSupportedByCodeOwnersBackend() {
    // the proto backend doesn't support ignoring parent code owners
    assumeThatCodeOwnersBackendIsNotProtoBackend();
  }

  protected void skipTestIfAnnotationsNotSupportedByCodeOwnersBackend() {
    // the proto backend doesn't support annotations on code owners
    assumeThatCodeOwnersBackendIsNotProtoBackend();
  }

  protected void assumeThatCodeOwnersBackendIsNotProtoBackend() {
    assume().that(backendConfig.getDefaultBackend()).isNotInstanceOf(ProtoBackend.class);
  }
}
