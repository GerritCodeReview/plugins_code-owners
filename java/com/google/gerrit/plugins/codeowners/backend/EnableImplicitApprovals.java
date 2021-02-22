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

/** Enum to control whether implicit code-owner approvals by the patch set uploader are enabled. */
public enum EnableImplicitApprovals {
  /** Implicit code-owner approvals of the the patch set uploader are disabled. */
  FALSE,

  /**
   * Implicit code-owner approvals of the patch set uploader are enabled, but only if the configured
   * required label allows self approvals.
   */
  TRUE,

  /**
   * Implicit code-owner approvals of the patch set uploader are enabled, even if the configured
   * required label disallows self approvals.
   */
  FORCED;
}
