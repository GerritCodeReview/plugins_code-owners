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

import com.google.common.truth.Correspondence;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.inject.Key;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnersBackends}. */
public class CodeOwnersBackendsTest extends AbstractCodeOwnersTest {
  private DynamicMap<CodeOwnersBackend> codeOwnersBackends;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnersBackends =
        plugin.getSysInjector().getInstance(new Key<DynamicMap<CodeOwnersBackend>>() {});
  }

  @Test
  public void codeOwnersBackendsEnumMatchesRegisteredBackends() throws Exception {
    assertThat(CodeOwnersBackends.values())
        .asList()
        .comparingElementsUsing(
            Correspondence.<CodeOwnersBackends, String>from(
                (actualCodeOwnersBackend, expectedCodeOwnersBackendId) ->
                    Objects.equals(
                        actualCodeOwnersBackend.getBackendId(), expectedCodeOwnersBackendId),
                "has backend ID"))
        .containsExactlyElementsIn(codeOwnersBackends.byPlugin("gerrit").keySet());
  }
}
