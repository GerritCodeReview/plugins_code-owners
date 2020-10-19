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

package com.google.gerrit.plugins.codeowners.api;

/**
 * The input for the {@link com.google.gerrit.plugins.codeowners.restapi.RenameEmail} REST endpoint.
 */
public class RenameEmailInput {
  /**
   * Optional commit message that should be used for the commit that renames the email in the code
   * owner config files.
   */
  public String message;

  /** The old email that should be replaced with the new email. */
  public String oldEmail;

  /** The new email that should be used to replace the old email. */
  public String newEmail;
}
