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

package com.google.gerrit.plugins.codeowners.backend;

import org.junit.Test;

/** Tests for {@link UnresolvedImport}. */
public class UnresolvedImportTest extends AbstractAutoValueTest {
  @Test
  public void toStringIncludesAllData() throws Exception {
    UnresolvedImport unresolvedImport =
        UnresolvedImport.create(
            CodeOwnerConfig.Key.create(project, "master", "/"),
            CodeOwnerConfig.Key.create(project, "master", "/bar/"),
            CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/bar/OWNERS"),
            "test message");
    assertThatToStringIncludesAllData(unresolvedImport, UnresolvedImport.class);
  }
}
