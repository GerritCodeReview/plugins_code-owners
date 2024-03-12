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

package com.google.gerrit.plugins.codeowners.backend.config;

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.server.git.meta.VersionedConfigFile;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;

/**
 * Reads/writes the code-owners project configuration from/to the {@code code-owners.config} file in
 * the {@code refs/meta/config} branch.
 */
public class CodeOwnersProjectConfigFile extends VersionedConfigFile {
  public static final String FILE_NAME = "code-owners.config";

  private boolean isLoaded = false;

  public CodeOwnersProjectConfigFile() {
    super(FILE_NAME);
  }

  /**
   * Returns the loaded code owners config.
   *
   * <p>Fails if loading was not done yet.
   */
  @Override
  public Config getConfig() {
    checkLoaded();
    return cfg;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    super.onLoad();
    isLoaded = true;
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    checkLoaded();
    return super.onSave(commit);
  }

  private void checkLoaded() {
    checkState(isLoaded, "%s not loaded yet", FILE_NAME);
  }
}
