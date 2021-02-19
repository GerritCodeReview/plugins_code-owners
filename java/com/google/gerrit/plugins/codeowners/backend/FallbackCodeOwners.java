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

/** Defines who owns paths for which no code owners are defined. */
public enum FallbackCodeOwners {
  /**
   * Paths for which no code owners are defined are owned by no one. This means changes that touch
   * these files can only be submitted with a code owner override.
   */
  NONE,

  /**
   * Paths for which no code owners are defined are owned by all users. This means changes to these
   * paths can be approved by anyone. If implicit approvals are enabled, these files are always
   * automatically approved.
   *
   * <p>The {@code ALL_USERS} option should only be used with care as it means that any path that is
   * not covered by the code owner config files is automatically opened up to everyone and mistakes
   * with configuring code owners can easily happen. This is why this option is intended to be only
   * used if requiring code owner approvals should not be enforced.
   */
  ALL_USERS,

  /**
   * Paths for which no code owners are defined are owned by the project owners. This means changes
   * to these paths can be approved by the project owners.
   */
  PROJECT_OWNERS;
}
