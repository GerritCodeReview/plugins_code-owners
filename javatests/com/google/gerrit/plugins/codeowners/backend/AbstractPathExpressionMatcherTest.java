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

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import java.nio.file.Paths;
import java.util.Arrays;

/** Base class for testing {@link PathExpressionMatcher}s. */
public abstract class AbstractPathExpressionMatcherTest extends AbstractCodeOwnersTest {
  /** Must return the {@link PathExpressionMatcher} that should be tested. */
  protected abstract PathExpressionMatcher getPathExpressionMatcher();

  protected void assertMatch(String pathExpression, String firstPath, String... morePaths) {
    assertMatch(true, pathExpression, firstPath, morePaths);
  }

  protected void assertNoMatch(String pathExpression, String firstPath, String... morePaths) {
    assertMatch(false, pathExpression, firstPath, morePaths);
  }

  private void assertMatch(
      boolean expectedToMatch, String pathExpression, String firstPath, String... morePaths) {
    for (String path : toList(firstPath, morePaths)) {
      assertWithMessage("path expression %s matches path %s", pathExpression, path)
          .that(getPathExpressionMatcher().matches(pathExpression, Paths.get(path)))
          .isEqualTo(expectedToMatch);
    }
  }

  private static ImmutableList<String> toList(String firstValue, String... moreValues) {
    ImmutableList.Builder<String> pathsBuilder = ImmutableList.builder();
    pathsBuilder.add(firstValue);
    pathsBuilder.addAll(Arrays.asList(moreValues));
    return pathsBuilder.build();
  }
}
