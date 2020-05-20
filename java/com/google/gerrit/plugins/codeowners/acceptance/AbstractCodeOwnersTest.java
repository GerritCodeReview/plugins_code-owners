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
import com.google.gerrit.acceptance.TestPlugin;

/**
 * Base class for code owner integration tests.
 *
 * <p>There are 2 reasons why we have this base class:
 *
 * <ul>
 *   <li>Hard-coding the {@link TestPlugin} annotation for each test class would be too much
 *       overhead.
 *   <li>The code owner API classes cannot be injected into the test classes because the plugin is
 *       not loaded yet when injections are resolved. Instantiating them will require a bit of
 *       boilerplate code. We prefer to have this boilerplate code only once here in this base
 *       class, as having this code in every test class would be too much overhead.
 * </ul>
 */
@TestPlugin(name = "code-owners", sysModule = "com.google.gerrit.plugins.codeowners.Module")
public class AbstractCodeOwnersTest extends LightweightPluginDaemonTest {
  // TODO(ekempin): Add code to instantiate the code owner API classes, once they exist.
}
