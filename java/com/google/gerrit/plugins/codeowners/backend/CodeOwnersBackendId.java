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

import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;

/**
 * Enum of all code owner backend IDs.
 *
 * <p>This enum should contain all registered code owner backends.
 */
public enum CodeOwnersBackendId {
  FIND_OWNERS(FindOwnersBackend.ID);

  /** The ID under which the code owners backend is registered. */
  private final String backendId;

  private CodeOwnersBackendId(String backendId) {
    this.backendId = backendId;
  }

  /** Gets the ID under which the code owners backend is registered. */
  public String getBackendId() {
    return backendId;
  }
}
