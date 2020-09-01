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

package com.google.gerrit.plugins.codeowners.acceptance.api;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import com.google.gerrit.plugins.codeowners.config.BackendConfig;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@code com.google.gerrit.plugins.codeowners.validation.CodeOwnerConfigValidator}. */
public class CodeOwnerConfigValidatorIT extends AbstractCodeOwnersIT {
  private BackendConfig backendConfig;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    backendConfig = plugin.getSysInjector().getInstance(BackendConfig.class);
  }

  @Test
  public void nonCodeOwnerConfigFileIsNotValidated() throws Exception {
    PushOneCommit.Result r = createChange("Add arbitrary file", "arbitrary-file.txt", "INVALID");
    r.assertOkStatus();
  }

  @Test
  public void canUploadNonParseableConfigIfCodeOwnersFunctionalityIsDisabled() throws Exception {
    disableCodeOwnersForProject(project);

    PushOneCommit.Result r =
        createChange("Add code owners", getCodeOwnerConfigFileName(), "INVALID");
    r.assertOkStatus();
  }

  @Test
  public void cannotUploadNonParseableConfig() throws Exception {
    PushOneCommit.Result r =
        createChange("Add code owners", getCodeOwnerConfigFileName(), "INVALID");
    r.assertErrorStatus("invalid code owner config files");
    r.assertMessage(
        String.format("invalid code owner config file '/%s':", getCodeOwnerConfigFileName()));

    CodeOwnerBackend codeOwnerBackend = backendConfig.getDefaultBackend();
    assertThat(codeOwnerBackend.getClass()).isAnyOf(FindOwnersBackend.class, ProtoBackend.class);
    if (codeOwnerBackend instanceof FindOwnersBackend) {
      r.assertMessage("invalid line: INVALID");
    } else if (codeOwnerBackend instanceof ProtoBackend) {
      r.assertMessage("1:8: expected \"{\"");
    }
  }

  private String getCodeOwnerConfigFileName() {
    CodeOwnerBackend codeOwnerBackend = backendConfig.getDefaultBackend();
    if (codeOwnerBackend instanceof FindOwnersBackend) {
      return FindOwnersBackend.CODE_OWNER_CONFIG_FILE_NAME;
    } else if (codeOwnerBackend instanceof ProtoBackend) {
      return ProtoBackend.CODE_OWNER_CONFIG_FILE_NAME;
    }
    throw new IllegalStateException(
        "unknown code owner backend: " + codeOwnerBackend.getClass().getName());
  }
}
