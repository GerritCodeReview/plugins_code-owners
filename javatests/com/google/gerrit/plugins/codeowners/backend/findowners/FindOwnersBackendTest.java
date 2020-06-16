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

package com.google.gerrit.plugins.codeowners.backend.findowners;

import com.google.gerrit.plugins.codeowners.backend.AbstractFileBasedCodeOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.AbstractFileBasedCodeOwnersBackendTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParser;

/** Tests for {@link FindOwnersBackend}. */
public class FindOwnersBackendTest extends AbstractFileBasedCodeOwnersBackendTest {
  @Override
  protected Class<? extends AbstractFileBasedCodeOwnersBackend> getBackendClass() {
    return FindOwnersBackend.class;
  }

  @Override
  protected String getFileName() {
    return FindOwnersBackend.CODE_OWNER_CONFIG_FILE_NAME;
  }

  @Override
  protected Class<? extends CodeOwnerConfigParser> getParserClass() {
    return FindOwnersCodeOwnerConfigParser.class;
  }
}
