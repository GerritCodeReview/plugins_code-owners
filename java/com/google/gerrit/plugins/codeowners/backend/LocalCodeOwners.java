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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.plugins.codeowners.CodeOwnersPluginConfiguration;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;

/**
 * Class to compute the local code owners for a path from a {@link CodeOwnerConfig}.
 *
 * <p>The <strong>local</strong> code owners are the code owners that are directly mentioned in a
 * code owner config. Code owners in inherited and included code owner configs are not considered.
 */
@Singleton
class LocalCodeOwners {
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;

  @Inject
  LocalCodeOwners(CodeOwnersPluginConfiguration codeOwnersPluginConfiguration) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
  }

  /**
   * Gets the local code owners from the given code owner config that apply the given path.
   *
   * <p>This method computes the <strong>local</strong> code owners which means that only code
   * owners that are directly mentioned in the code owner config are considered. Code owners in
   * inherited and included code owner configs are not considered.
   *
   * @param codeOwnerConfig the code owner config from which the local code owners should be
   *     returned
   * @param absolutePath path for which the local code owners should be returned; the path must be
   *     absolute; can be the path of a file or folder; the path may or may not exist
   * @return the local code owners for the given path
   */
  public ImmutableSet<CodeOwnerReference> get(CodeOwnerConfig codeOwnerConfig, Path absolutePath) {
    requireNonNull(codeOwnerConfig, "codeOwnerConfig");
    requireNonNull(absolutePath, "absolutePath");
    checkState(absolutePath.isAbsolute(), "path %s must be absolute", absolutePath);

    Path relativePath = codeOwnerConfig.relativize(absolutePath);
    return codeOwnerConfig.codeOwnerSets().stream()
        .filter(
            codeOwnerSet -> matches(codeOwnerSet, relativePath, getMatcher(codeOwnerConfig.key())))
        .flatMap(codeOwnerSet -> codeOwnerSet.codeOwners().stream())
        .collect(toImmutableSet());
  }

  /**
   * Gets the {@link PathExpressionMatcher} that should be used for the specified code owner config.
   *
   * <p>Checks which {@link CodeOwnersBackend} is responsible for the specified code owner config
   * and retrieves the {@link PathExpressionMatcher} from it.
   *
   * @param codeOwnerConfigKey the key of the code owner config for which the path expression
   *     matcher should be returned
   * @return the {@link PathExpressionMatcher} that should be used for the specified code owner
   *     config
   */
  private PathExpressionMatcher getMatcher(CodeOwnerConfig.Key codeOwnerConfigKey) {
    CodeOwnersBackend codeOwnersBackend =
        codeOwnersPluginConfiguration.getBackend(codeOwnerConfigKey.branch());
    return codeOwnersBackend.getPathExpressionMatcher();
  }

  /**
   * Whether the given owner set matches the given path.
   *
   * <p>A path matches the owner set, if any of its path expressions matches the path, or if the
   * code owner set doesn't contain any path expressions.
   *
   * @param codeOwnerSet the code owner set for which it should be checked if it matches the given
   *     path
   * @param relativePath path for which it should be checked whether it matches the given owner set;
   *     the path must be relative to the path in which the {@link CodeOwnerConfig} is stored that
   *     contains the code owner set; can be the path of a file or folder; the path may or may not
   *     exist
   * @param matcher the {@link PathExpressionMatcher} that should be used to match path expressions
   *     against the given path
   * @return whether this owner set matches the given path
   */
  @VisibleForTesting
  static boolean matches(
      CodeOwnerSet codeOwnerSet, Path relativePath, PathExpressionMatcher matcher) {
    requireNonNull(codeOwnerSet, "codeOwnerSet");
    requireNonNull(relativePath, "relativePath");
    requireNonNull(matcher, "matcher");
    checkState(!relativePath.isAbsolute(), "path %s must be relative", relativePath);

    // If no path expressions are set, the code owners apply for all files.
    if (codeOwnerSet.pathExpressions().isEmpty()) {
      return true;
    }

    return codeOwnerSet.pathExpressions().stream()
        .anyMatch(pathExpression -> matcher.matches(pathExpression, relativePath));
  }
}
