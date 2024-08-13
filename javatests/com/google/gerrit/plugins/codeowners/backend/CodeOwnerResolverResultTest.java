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
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

/** Tests for {@link CodeOwnerResolverResult}. */
public class CodeOwnerResolverResultTest extends AbstractAutoValueTest {
  private static final ObjectId TEST_REVISION =
      ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

  @Test
  public void toStringIncludesAllData() throws Exception {
    CodeOwnerResolverResult codeOwnerResolverResult =
        CodeOwnerResolverResult.create(
            ImmutableSet.of(CodeOwner.create(admin.id())),
            /* annotations= */ ImmutableMultimap.of(),
            /* ownedByAllUsers= */ false,
            /* hasUnresolvedCodeOwners= */ false,
            ImmutableList.of(
                CodeOwnerConfigImport.createResolvedImport(
                    CodeOwnerConfig.builder(
                            CodeOwnerConfig.Key.create(project, "master", "/"), TEST_REVISION)
                        .build(),
                    CodeOwnerConfig.builder(
                            CodeOwnerConfig.Key.create(project, "master", "/bar/"), TEST_REVISION)
                        .build(),
                    CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/bar/OWNERS"))),
            ImmutableList.of(
                CodeOwnerConfigImport.createUnresolvedImport(
                    CodeOwnerConfig.builder(
                            CodeOwnerConfig.Key.create(project, "master", "/"), TEST_REVISION)
                        .build(),
                    CodeOwnerConfig.Key.create(project, "master", "/bar/"),
                    CodeOwnerConfigReference.create(CodeOwnerConfigImportMode.ALL, "/bar/OWNERS"),
                    "test message")),
            ImmutableList.of(DebugMessage.createMessage("test message")));
    assertThatToStringIncludesAllData(codeOwnerResolverResult, CodeOwnerResolverResult.class);
  }
}
