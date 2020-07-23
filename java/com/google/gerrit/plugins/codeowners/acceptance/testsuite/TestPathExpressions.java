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

package com.google.gerrit.plugins.codeowners.acceptance.testsuite;

import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.GlobMatcher;
import com.google.gerrit.plugins.codeowners.backend.PathExpressionMatcher;
import com.google.gerrit.plugins.codeowners.backend.SimplePathExpressionMatcher;
import com.google.gerrit.plugins.codeowners.config.BackendConfig;
import com.google.inject.Inject;

/**
 * Class to create path expressions in integration tests.
 *
 * <p>How a path expression should be formatted depends on the {@link PathExpressionMatcher} that is
 * used by the {@link CodeOwnerBackend}. E.g. if files of a certain file type should be matched in
 * the current folder, as Java NIO glob the path expression would be written as {@code *.md} while
 * as regular expression it would be written as {@code ^[^/]*\.md}. By using this class tests can
 * avoid hard-coding a concrete path expression syntax so that they work against all {@link
 * CodeOwnerBackend}s.
 */
public class TestPathExpressions {
  private final BackendConfig backendConfig;

  @Inject
  TestPathExpressions(BackendConfig backendConfig) {
    this.backendConfig = backendConfig;
  }

  /**
   * Creates a path expression that matches all files of the given file type in the current folder.
   *
   * @param fileType the file type
   */
  public String matchFileTypeInCurrentFolder(String fileType) {
    CodeOwnerBackend defaultBackend = backendConfig.getDefaultBackend();
    PathExpressionMatcher pathExpressionMatcher =
        defaultBackend
            .getPathExpressionMatcher()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format(
                            "code owner backend %s doesn't support path expressions",
                            defaultBackend.getClass().getName())));
    if (pathExpressionMatcher instanceof GlobMatcher
        || pathExpressionMatcher instanceof SimplePathExpressionMatcher) {
      return "*." + fileType;
    }
    throw new IllegalStateException(
        String.format(
            "path expression matcher %s not supported",
            pathExpressionMatcher.getClass().getName()));
  }
}
