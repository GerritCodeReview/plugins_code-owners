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

/** Strategy that defines for merge commits which files require code owner approvals. */
public enum MergeCommitStrategy {
  /**
   * All files which differ between the merge commit that is being reviewed and its first parent
   * commit (which is the HEAD of the destination branch) require code owner approvals.
   *
   * <p>Using this strategy is the safest option, but requires code owners to also approve files
   * which have been merged automatically.
   *
   * <p>Using this strategy makes sense if the code owners differ between branches and the code
   * owners in one branch don't trust what the code owners in other branches have approved, or if
   * there are branches that do not require code owner approvals at all.
   */
  ALL_CHANGED_FILES,

  /**
   * Only files which differ between the merge commit that is being reviewed and the auto merge
   * commit (the result of automatically merging the 2 parent commits, may contain Git conflict
   * markers) require code owner approvals.
   *
   * <p>Using this strategy means that files that have been merged automatically and for which no
   * manual conflict resolution has been done do not require code owner approval.
   *
   * <p>Using this strategy is only recommended, if all branches require code owner approvals and if
   * the code owners in all branches are trusted. If this is not the case, it is recommended to use
   * the {@link #ALL_CHANGED_FILES} strategy instead.
   *
   * <p>Example: If this strategy is used and there is a branch that doesn't require code owner
   * approvals (e.g. a user sandbox branch or an experimental branch) the code owners check can be
   * bypassed by:
   *
   * <ul>
   *   <li>setting the branch that doesn't require code owner approvals to the same commit as the
   *       main branch that does require code owner approvals
   *   <li>making a change in the branch that doesn't require code owner approvals
   *   <li>merging this change back into the main branch that does require code owner approvals
   *   <li>since it's a clean merge, all files are merged automatically and no code owner approval
   *       is required
   * </ul>
   */
  FILES_WITH_CONFLICT_RESOLUTION;
}
