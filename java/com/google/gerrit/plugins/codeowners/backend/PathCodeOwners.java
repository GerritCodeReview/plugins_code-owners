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
import com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfiguration;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Class to compute the code owners for a path from a {@link CodeOwnerConfig}.
 *
 * <p>Code owners from inherited code owner configs are not considered.
 */
class PathCodeOwners {
  @Singleton
  public static class Factory {
    private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;

    @Inject
    Factory(CodeOwnersPluginConfiguration codeOwnersPluginConfiguration) {
      this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    }

    public PathCodeOwners create(CodeOwnerConfig codeOwnerConfig, Path absolutePath) {
      requireNonNull(codeOwnerConfig, "codeOwnerConfig");
      return new PathCodeOwners(codeOwnerConfig, absolutePath, getMatcher(codeOwnerConfig.key()));
    }

    /**
     * Gets the {@link PathExpressionMatcher} that should be used for the specified code owner
     * config.
     *
     * <p>Checks which {@link CodeOwnerBackend} is responsible for the specified code owner config
     * and retrieves the {@link PathExpressionMatcher} from it.
     *
     * <p>If the {@link CodeOwnerBackend} doesn't support path expressions and doesn't provide a
     * {@link PathExpressionMatcher} a {@link PathExpressionMatcher} that never matches is returned.
     * This way {@link CodeOwnerSet}s that have path expressions are ignored and will not have any
     * effect.
     *
     * @param codeOwnerConfigKey the key of the code owner config for which the path expression
     *     matcher should be returned
     * @return the {@link PathExpressionMatcher} that should be used for the specified code owner
     *     config
     */
    private PathExpressionMatcher getMatcher(CodeOwnerConfig.Key codeOwnerConfigKey) {
      CodeOwnerBackend codeOwnerBackend =
          codeOwnersPluginConfiguration.getBackend(codeOwnerConfigKey.branch());
      return codeOwnerBackend
          .getPathExpressionMatcher()
          .orElse((pathExpression, relativePath) -> false);
    }
  }

  private final CodeOwnerConfig codeOwnerConfig;
  private final Path path;
  private final PathExpressionMatcher pathExpressionMatcher;

  private PathCodeOwners(
      CodeOwnerConfig codeOwnerConfig, Path path, PathExpressionMatcher pathExpressionMatcher) {
    this.codeOwnerConfig = requireNonNull(codeOwnerConfig, "codeOwnerConfig");
    this.path = requireNonNull(path, "path");
    this.pathExpressionMatcher = requireNonNull(pathExpressionMatcher, "pathExpressionMatcher");

    checkState(path.isAbsolute(), "path %s must be absolute", path);
  }

  /**
   * Gets the code owners from the code owner config that apply to the path.
   *
   * <p>Code owners from inherited code owner configs are not considered.
   *
   * @return the code owners of the path
   */
  public ImmutableSet<CodeOwnerReference> get() {
    Stream.Builder<CodeOwnerSet> matchingCodeOwnerSets = Stream.builder();

    // Add all code owner sets that have matching path expressions.
    getMatchingPerFileCodeOwnerSets().forEach(matchingCodeOwnerSets::add);

    // Add all code owner sets without path expressions if global code owners are not ignored.
    if (!ignoreGlobalCodeOwners()) {
      codeOwnerConfig.codeOwnerSets().stream()
          .filter(codeOwnerSet -> codeOwnerSet.pathExpressions().isEmpty())
          .forEach(matchingCodeOwnerSets::add);
    }

    // Resolve the matching code owner sets to code owner references.
    return matchingCodeOwnerSets
        .build()
        .flatMap(codeOwnerSet -> codeOwnerSet.codeOwners().stream())
        .collect(toImmutableSet());
  }

  /**
   * Whether parent code owners should be ignored for the path.
   *
   * @return whether parent code owners should be ignored for the path
   */
  public boolean ignoreParentCodeOwners() {
    if (codeOwnerConfig.ignoreParentCodeOwners()) {
      return true;
    }

    return ignoreGlobalCodeOwners();
  }

  private boolean ignoreGlobalCodeOwners() {
    return getMatchingPerFileCodeOwnerSets()
        .anyMatch(codeOwnerSet -> codeOwnerSet.ignoreGlobalAndParentCodeOwners());
  }

  private Stream<CodeOwnerSet> getMatchingPerFileCodeOwnerSets() {
    Path relativePath = codeOwnerConfig.relativize(path);
    return codeOwnerConfig.codeOwnerSets().stream()
        .filter(codeOwnerSet -> !codeOwnerSet.pathExpressions().isEmpty())
        .filter(codeOwnerSet -> matches(codeOwnerSet, relativePath, pathExpressionMatcher));
  }

  /**
   * Whether the given code owner set matches the given path.
   *
   * <p>A path matches the code owner set, if any of its path expressions matches the path.
   *
   * <p>The passed in code owner set must have at least one path expression.
   *
   * @param codeOwnerSet the code owner set for which it should be checked if it matches the given
   *     path, must have at least one path expression
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
    checkState(
        !codeOwnerSet.pathExpressions().isEmpty(), "code owner set must have path expressions");

    return codeOwnerSet.pathExpressions().stream()
        .anyMatch(pathExpression -> matcher.matches(pathExpression, relativePath));
  }
}
