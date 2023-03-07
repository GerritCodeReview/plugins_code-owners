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

import com.google.common.collect.ImmutableList;
import java.nio.file.Paths;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

/** Tests for {@link PathCodeOwnersResult}. */
public class PathCodeOwnersResultTest extends AbstractAutoValueTest {
  private static final ObjectId TEST_REVISION =
      ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

  @Test
  public void toStringIncludesAllData() throws Exception {
    CodeOwnerConfig.Key codeOwnerConfigKey = CodeOwnerConfig.Key.create(project, "master", "/");
    CodeOwnerConfigReference resolvableCodeOwnerConfigReference =
        CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/bar/OWNERS");
    CodeOwnerConfigReference unresolvableCodeOwnerConfigReference =
        CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/baz/OWNERS");
    PathCodeOwnersResult pathCodeOwnersResult =
        PathCodeOwnersResult.create(
            Paths.get("/foo/bar/baz.md"),
            CodeOwnerConfig.builder(codeOwnerConfigKey, TEST_REVISION)
                .addImport(resolvableCodeOwnerConfigReference)
                .addImport(unresolvableCodeOwnerConfigReference)
                .build(),
            ImmutableList.of(
                ImportedCodeOwnerConfig.createResolvedImport(
                    codeOwnerConfigKey,
                    CodeOwnerConfig.Key.create(project, "master", "/bar/"),
                    resolvableCodeOwnerConfigReference)),
            ImmutableList.of(
                ImportedCodeOwnerConfig.createUnresolvedImport(
                    codeOwnerConfigKey,
                    CodeOwnerConfig.Key.create(project, "master", "/baz/"),
                    unresolvableCodeOwnerConfigReference,
                    "test message")));
    assertThatToStringIncludesAllData(pathCodeOwnersResult, PathCodeOwnersResult.class);
  }
}
