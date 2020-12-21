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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/** Tests for {@link FindOwnersGlobMatcher}. */
public class FindOwnersGlobMatcherTest extends GlobMatcherTest {
  @Override
  protected PathExpressionMatcher getPathExpressionMatcher() {
    return FindOwnersGlobMatcher.INSTANCE;
  }

  @Test
  public void singleStarInGlobIsReplacedWithDoubleStar() throws Exception {
    assertThat(FindOwnersGlobMatcher.INSTANCE.replaceSingleStarWithDoubleStar("*.md"))
        .isEqualTo("**.md");
    assertThat(FindOwnersGlobMatcher.INSTANCE.replaceSingleStarWithDoubleStar("foo/*.md"))
        .isEqualTo("foo/**.md");
    assertThat(FindOwnersGlobMatcher.INSTANCE.replaceSingleStarWithDoubleStar("*/foo/*.md"))
        .isEqualTo("**/foo/**.md");
    assertThat(FindOwnersGlobMatcher.INSTANCE.replaceSingleStarWithDoubleStar("foo/*"))
        .isEqualTo("foo/**");
  }

  @Test
  public void doubleStarInGlobIsNotReplaced() throws Exception {
    assertThat(FindOwnersGlobMatcher.INSTANCE.replaceSingleStarWithDoubleStar("**.md"))
        .isEqualTo("**.md");
    assertThat(FindOwnersGlobMatcher.INSTANCE.replaceSingleStarWithDoubleStar("foo/**.md"))
        .isEqualTo("foo/**.md");
    assertThat(FindOwnersGlobMatcher.INSTANCE.replaceSingleStarWithDoubleStar("**/foo/**.md"))
        .isEqualTo("**/foo/**.md");
  }

  @Test
  public void tripleStarInGlobIsNotReplaced() throws Exception {
    assertThat(FindOwnersGlobMatcher.INSTANCE.replaceSingleStarWithDoubleStar("***.md"))
        .isEqualTo("***.md");
    assertThat(FindOwnersGlobMatcher.INSTANCE.replaceSingleStarWithDoubleStar("foo/***.md"))
        .isEqualTo("foo/***.md");
    assertThat(FindOwnersGlobMatcher.INSTANCE.replaceSingleStarWithDoubleStar("***/foo/***.md"))
        .isEqualTo("***/foo/***.md");
  }

  /**
   * This test differs from the base class ({@link GlobMatcherTest}), since for {@link
   * FindOwnersGlobMatcher} {@code *} also matches slashes and the test in the base class has an
   * assertion that checks that slashes are not matched by {@code *}.
   */
  @Test
  @Override
  public void matchFileTypeInCurrentFolder() throws Exception {
    String pathExpression = "*.md";
    assertMatch(pathExpression, "README.md", "config.md");
    assertNoMatch(pathExpression, "README", "README.md5");
  }

  @Test
  public void matchFileTypeInCurrentFolderAndAllSubfoldersBySingleStar() throws Exception {
    String pathExpression = "*.md";
    assertMatch(pathExpression, "README.md", "config.md", "foo/README.md", "foo/bar/README.md");
    assertNoMatch(pathExpression, "README", "README.md5");
  }

  @Test
  public void matchAllFilesInSubfolderBySingleStar() throws Exception {
    String pathExpression = "foo/*";
    assertMatch(
        pathExpression,
        "foo/README.md",
        "foo/config.txt",
        "foo/bar/README.md",
        "foo/bar/baz/README.md");
    assertNoMatch(pathExpression, "README", "foo2/README");
  }
}
