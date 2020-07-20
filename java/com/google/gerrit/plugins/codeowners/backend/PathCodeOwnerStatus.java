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

package com.google.gerrit.plugins.codeowners.backend;

import java.nio.file.Path;

import com.google.auto.value.AutoValue;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerStatus;

/** Code owner status for a particular path that has been modified in a change. */
@AutoValue
public abstract class PathCodeOwnerStatus {
	/**
   * Path to which the {@link #status()} belongs.
   *
   * <p>Always an absolute path.
   */
  public abstract Path path();

  /** The code owner status of the {@link #path()}. */
  public abstract CodeOwnerStatus status();

  /**
   * Creates a {@link PathCodeOwnerStatus} instance.
   *
   * @param path the path to which the code owner status belongs
   * @param codeOwnerStatus the code owner status
   * @return the created {@link PathCodeOwnerStatus} instance
   */
  public static PathCodeOwnerStatus create(Path path, CodeOwnerStatus codeOwnerStatus) {
    return new AutoValue_PathCodeOwnerStatus(path, codeOwnerStatus);
  }
}
