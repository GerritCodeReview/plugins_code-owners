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

import static java.util.Objects.requireNonNull;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Exception that is thrown if there is an invalid code owner config file. */
public class InvalidCodeOwnerConfigException extends ConfigInvalidException {
  private static final long serialVersionUID = 1L;

  private final Project.NameKey projectName;
  private final String ref;
  private final String codeOwnerConfigFilePath;

  public InvalidCodeOwnerConfigException(
      String message, Project.NameKey projectName, String ref, String codeOwnerConfigFilePath) {
    this(message, projectName, ref, codeOwnerConfigFilePath, /* cause= */ null);
  }

  public InvalidCodeOwnerConfigException(
      String message,
      Project.NameKey projectName,
      String ref,
      String codeOwnerConfigFilePath,
      @Nullable Throwable cause) {
    super(message, cause);

    this.projectName = requireNonNull(projectName, "projectName");
    this.ref = requireNonNull(ref, "ref");
    this.codeOwnerConfigFilePath =
        requireNonNull(codeOwnerConfigFilePath, "codeOwnerConfigFilePath");
  }

  public Project.NameKey getProjectName() {
    return projectName;
  }

  public String getRef() {
    return ref;
  }

  public String getCodeOwnerConfigFilePath() {
    return codeOwnerConfigFilePath;
  }
}
