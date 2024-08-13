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

import java.util.List;

/**
 * REST API representation of the result of checking a code owner via {code
 * com.google.gerrit.plugins.codeowners.restapi.CheckCodeOwner}.
 *
 * <p>This class determines the JSON format of check result in the REST API.
 */
public class CodeOwnerCheckInfo {
  /**
   * Whether the given email owns the specified path in the branch.
   *
   * <p>True if:
   *
   * <ul>
   *   <li>the given email is resolvable (see {@link #isResolvable}) and
   *   <li>any code owner config file assigns code ownership to the email for the path (see {@link
   *       CodeOwnerConfigFileWithCheckResultsInfo#assignsCodeOwnershipToUser} field of the
   *       inspected {@link #codeOwnerConfigs}) or the email is configured as global code owner (see
   *       {@link #isGlobalCodeOwner} field) or the user is a fallback code owner (see {@link
   *       #isFallbackCodeOwner} field)
   * </ul>
   */
  public boolean isCodeOwner;

  /**
   * Whether the given email is resolvable for the specified user or the calling user if no user was
   * specified.
   */
  public boolean isResolvable;

  /**
   * The code owner config files that are relevant for computing the code ownership, i.e. all code
   * owner config files which have been inspected to compute the code ownership.
   */
  public List<CodeOwnerConfigFileWithCheckResultsInfo> codeOwnerConfigs;

  /**
   * Whether the user to which the given email was resolved has read permissions on the branch.
   *
   * <p>Not set if:
   *
   * <ul>
   *   <li>the given email is not resolvable
   *   <li>the given email is the all users wildcard (aka {@code *})
   * </ul>
   */
  public Boolean canReadRef;

  /**
   * Whether the user to which the given email was resolved can see the specified change.
   *
   * <p>Not set if:
   *
   * <ul>
   *   <li>the given email is not resolvable
   *   <li>the given email is the all users wildcard (aka {@code *})
   *   <li>no change was specified
   * </ul>
   */
  public Boolean canSeeChange;

  /**
   * Whether the user to which the given email was resolved can code-owner approve the specified
   * change.
   *
   * <p>Being able to code-owner approve the change means that the user has permissions to vote on
   * the label that is required as code owner approval. Other permissions are not considered for
   * computing this flag. In particular missing read permissions on the change don't have any effect
   * on this flag. Whether the user misses read permissions on the change (and hence cannot apply
   * the code owner approval) can be seen from the {@link #canSeeChange} flag.
   *
   * <p>Not set if:
   *
   * <ul>
   *   <li>the given email is not resolvable
   *   <li>the given email is the all users wildcard (aka {@code *})
   *   <li>no change was specified
   * </ul>
   */
  public Boolean canApproveChange;

  /**
   * Whether the given email is a fallback code owner of the specified path in the branch.
   *
   * <p>True if:
   *
   * <ul>
   *   <li>the given email is resolvable (see {@link #isResolvable}) and
   *   <li>no code owners are defined for the specified path in the branch and
   *   <li>parent code owners are not ignored and
   *   <li>the user is a fallback code owner according to the configured fallback code owner policy
   */
  public boolean isFallbackCodeOwner;

  /**
   * Whether the given email is configured as a default code owner.
   *
   * <p>If the email is configured as default code owner, but the email is not resolvable (see
   * {@link #isResolvable} field), the user is not a code owner.
   */
  public boolean isDefaultCodeOwner;

  /**
   * Whether the given email is configured as a global code owner.
   *
   * <p>If the email is configured as global code owner, but the email is not resolvable (see {@link
   * #isResolvable} field), the user is not a code owner.
   */
  public boolean isGlobalCodeOwner;

  /** Whether the the specified path in the branch is owned by all users (aka {@code *}). */
  public boolean isOwnedByAllUsers;

  /**
   * Annotations that were set for the user.
   *
   * <p>Contains only supported annotations (unsupported annotations are reported in the {@link
   * #debugLogs}).
   *
   * <p>Sorted alphabetically.
   */
  public List<String> annotations;

  /** Debug logs that may help to understand why the user is or isn't a code owner. */
  public List<String> debugLogs;
}
