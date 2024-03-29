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

package com.google.gerrit.plugins.codeowners.backend.proto;

import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.plugins.codeowners.backend.AbstractFileBasedCodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.AbstractFileBasedCodeOwnerBackendTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.backend.SimplePathExpressionMatcher;
import org.junit.Test;

/** Tests for {@link ProtoBackend}. */
public class ProtoBackendTest extends AbstractFileBasedCodeOwnerBackendTest {
  @Override
  protected Class<? extends AbstractFileBasedCodeOwnerBackend> getBackendClass() {
    return ProtoBackend.class;
  }

  @Override
  protected String getFileName() {
    return ProtoBackend.CODE_OWNER_CONFIG_FILE_NAME;
  }

  @Override
  protected Class<? extends CodeOwnerConfigParser> getParserClass() {
    return ProtoCodeOwnerConfigParser.class;
  }

  @Test
  public void getPathExpressionMatcher() throws Exception {
    assertThat(codeOwnerBackend.getPathExpressionMatcher(BranchNameKey.create(project, "master")))
        .value()
        .isInstanceOf(SimplePathExpressionMatcher.class);
  }
}
