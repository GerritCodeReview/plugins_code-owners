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

import com.google.common.annotations.VisibleForTesting;
import java.nio.file.Path;

/**
 * Matcher that checks for a given path expression if it matches a given path.
 *
 * <p>This interface allows {@link CodeOwnersBackend}s to support different kinds of path
 * expressions (e.g. globs or regular expressions).
 */
public interface PathExpressionMatcher {
  /**
   * Creates a {@link PathExpressionMatcher} for a {@link CodeOwnersBackend} that doesn't support
   * path expressions.
   *
   * <p>If the {@link PathExpressionMatcher} is invoked an {@link IllegalStateException} is thrown
   * saying that the {@link CodeOwnersBackend} doesn't support path expressions.
   *
   * @param backendId ID of the {@link CodeOwnersBackend} for which the created {@link
   *     PathExpressionMatcher} will be used
   * @return the created {@link PathExpressionMatcher}
   */
  static PathExpressionMatcher notSupportedBy(CodeOwnersBackendId backendId) {
    return notSupportedBy(backendId.getBackendId());
  }

  /**
   * Creates a {@link PathExpressionMatcher} for a {@link CodeOwnersBackend} that doesn't support
   * path expressions.
   *
   * <p>If the {@link PathExpressionMatcher} is invoked an {@link UnsupportedOperationException} is
   * thrown saying that the {@link CodeOwnersBackend} doesn't support path expressions.
   *
   * <p>Use this method only from tests. Non-test code should use {@link
   * #notSupportedBy(CodeOwnersBackendId)}.
   *
   * @param backendId ID of the {@link CodeOwnersBackend} for which the created {@link
   *     PathExpressionMatcher} will be used
   * @return the created {@link PathExpressionMatcher}
   */
  @VisibleForTesting
  static PathExpressionMatcher notSupportedBy(String backendId) {
    return (String pathExpression, Path relativePath) -> {
      throw new UnsupportedOperationException(
          String.format("path expressions not supported by %s backend", backendId));
    };
  }

  /**
   * Whether the given path expression matches the given path
   *
   * @param pathExpression path expression relative to the code owner config
   * @param relativePath path relative to the code owner config
   * @return {@code true} if the given path expression matches the given path, otherwise {@code
   *     false}
   */
  boolean matches(String pathExpression, Path relativePath);
}
