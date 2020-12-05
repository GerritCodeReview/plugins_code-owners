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

package com.google.gerrit.plugins.codeowners.common;

/** Code owner status for a path in a change. */
public enum CodeOwnerStatus {
  /**
   * The path needs a code owner approval, but none of its code owners is currently a reviewer of
   * the change.
   */
  INSUFFICIENT_REVIEWERS,

  /**
   * A code owner of this path has been added as reviewer, but no code owner approval for this path
   * has been given yet.
   */
  PENDING,

  /** The path has been approved by a code owner or a code owners override is present. */
  APPROVED;
}
