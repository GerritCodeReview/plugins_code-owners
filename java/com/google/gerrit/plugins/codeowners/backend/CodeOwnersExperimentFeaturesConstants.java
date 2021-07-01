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

/**
 * Constants for {@link com.google.gerrit.server.experiments.ExperimentFeatures} in the code-owners
 * plugin.
 */
public final class CodeOwnersExperimentFeaturesConstants {
  /**
   * Whether {@link com.google.gerrit.server.patch.DiffOperations}, and thus the diff cache, should
   * be used to get changed files, instead of computing the changed files on our own.
   *
   * @see ChangedFiles#getOrCompute(com.google.gerrit.entities.Project.NameKey,
   *     org.eclipse.jgit.lib.ObjectId,
   *     com.google.gerrit.plugins.codeowners.common.MergeCommitStrategy)
   */
  public static final String USE_DIFF_CACHE =
      "GerritBackendRequestFeature__code_owners_use_diff_cache";

  /**
   * Private constructor to prevent instantiation of this class.
   *
   * <p>The class only contains static fields, hence the class never needs to be instantiated.
   */
  private CodeOwnersExperimentFeaturesConstants() {}
}
