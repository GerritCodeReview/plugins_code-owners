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
 * Representation of a configured required approval in the REST API.
 *
 * <p>This class determines the JSON format of a configured required approval in the REST API.
 *
 * <p>Represents an approval that is required for an action.
 *
 * <p>A required approval is used to represent:
 *
 * <ul>
 *   <li>the approval that is required from code owners to approve the files in a change (see {@link
 *       CodeOwnerProjectConfigInfo#requiredApproval})
 *   <li>the approval that is required to override the code owners submit check (see {@link
 *       CodeOwnerProjectConfigInfo#overrideApproval})
 * </ul>
 */
public class RequiredApprovalInfo {
  /** The name of the label on which an approval is required. */
  public String label;

  /** The voting value that is required on the label. */
  public short value;
}
