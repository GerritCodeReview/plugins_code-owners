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

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.plugins.codeowners.JgitPath;

/**
 * Base class for code owner integration tests.
 *
 * <p>We have this base class because hard-coding the {@link TestPlugin} annotation for each test
 * class would be too much overhead.
 *
 * <p>Integration/acceptance tests should extend {@link AbstractCodeOwnersIT} instead of exting this
 * class directly.
 */
@TestPlugin(
    name = "code-owners",
    sysModule = "com.google.gerrit.plugins.codeowners.acceptance.TestModule")
public class AbstractCodeOwnersTest extends LightweightPluginDaemonTest {
  protected PushOneCommit.Result createChangeWithFileDeletion(String filePath) throws Exception {
    createChange("Change Adding A File", JgitPath.of(filePath).get(), "file content").getChangeId();

    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Change Deleting A File",
            JgitPath.of(filePath).get(),
            "file content");
    Result r = push.rm("refs/for/master");
    r.assertOkStatus();
    return r;
  }
}
