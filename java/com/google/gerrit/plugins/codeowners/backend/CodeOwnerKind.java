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

/** Code owner kind, describing how the code ownership was assigned to a user. */
public enum CodeOwnerKind {
  /**
   * The user is a global code owner (defined by {@code plugin.code-owners.globalCodeOwner} in
   * {@code gerrit.config} or {@code codeOwners.globalCodeOwner} in {@code code-owners.config}).
   */
  GLOBAL_CODE_OWNER("global code owner"),

  /**
   * The user is a default code owner (defined in the code owner config file in {@code
   * refs/meta/config}).
   */
  DEFAULT_CODE_OWNER("default code owner"),

  /**
   * The user is a fallback code owner (according the the fallback code owner policy that is defined
   * by {@code plugin.code-owners.fallbackCodeOwners} in {@code gerrit.config} or {@code
   * codeOwners.fallbackCodeOwners} in {@code code-owners.config} if no code owner is defined).
   */
  FALLBACK_CODE_OWNER("fallback code owner"),

  /**
   * Regular folder or per-file code owner (define in a code owner config file in the repository).
   */
  REGULAR_CODE_OWNER("code owner");

  private final String displayName;

  private CodeOwnerKind(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
