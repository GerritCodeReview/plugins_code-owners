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

import org.junit.Test;

/** Tests for {@link FindOwnersGlobMatcher}. */
public class FindOwnersGlobMatcherTest extends GlobMatcherTest {
  @Override
  protected PathExpressionMatcher getPathExpressionMatcher() {
    return FindOwnersGlobMatcher.INSTANCE;
  }

  /**
   * This test differs from the base class ({@link GlobMatcherTest}), since {@link
   * FindOwnersGlobMatcher} matches globs against any subdirectory and the test in the base class
   * checks that subdirectories are not matched.
   */
  @Test
  @Override
  public void matchFileType() throws Exception {
    String pathExpression = "*.md";
    assertMatch(pathExpression, "README.md", "config.md", "foo/README.md", "foo/bar/README.md");
    assertNoMatch(pathExpression, "README", "README.md5");
  }

  /**
   * This test differs from the base class ({@link GlobMatcherTest}), since {@link
   * FindOwnersGlobMatcher} matches globs against any subdirectory and the test in the base class
   * checks that subdirectories are not matched.
   */
  @Test
  @Override
  public void matchConcreteFile() throws Exception {
    String pathExpression = "BUILD";
    assertMatch(pathExpression, "BUILD", "foo/BUILD", "foo/bar/BUILD");
    assertNoMatch(pathExpression, "README", "BUILD2");
  }

  /**
   * This test differs from the base class ({@link GlobMatcherTest}), since {@link
   * FindOwnersGlobMatcher} matches globs against any subdirectory and the test in the base class
   * checks that subdirectories are not matched.
   */
  @Test
  @Override
  public void matchAllFilesInSubfolder() throws Exception {
    String pathExpression = "foo/**";
    assertMatch(
        pathExpression,
        "foo/README.md",
        "foo/config.txt",
        "foo/bar/README.md",
        "foo/bar/baz/README.md",
        "bar/foo/README.md",
        "bar/foo/config.txt",
        "bar/foo/bar/README.md",
        "bar/foo/bar/baz/README.md");
    assertNoMatch(pathExpression, "README", "foo2/README", "bar/README", "bar/foo2/README");
  }

  /**
   * This test differs from the base class ({@link GlobMatcherTest}), since {@link
   * FindOwnersGlobMatcher} matches globs against any subdirectory and the test in the base class
   * checks that subdirectories are not matched.
   */
  @Test
  @Override
  public void matchPattern() throws Exception {
    String pathExpression = "{**/,}foo-[1-4].txt";
    assertMatch(pathExpression, "foo-1.txt", "foo-2.txt", "sub/foo-3.txt", "sub/sub/foo-4.txt");
    assertNoMatch(pathExpression, "foo-5.txt", "foo-11.txt");

    String pathExpression2 = "foo-[1-4].txt";
    assertMatch(pathExpression2, "foo-1.txt", "foo-2.txt", "sub/foo-3.txt", "sub/sub/foo-4.txt");
    assertNoMatch(pathExpression2, "foo-5.txt", "foo-11.txt");
  }
}
