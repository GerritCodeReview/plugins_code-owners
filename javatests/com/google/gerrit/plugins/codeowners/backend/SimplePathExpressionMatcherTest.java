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

/** Tests for {@link com.google.gerrit.plugins.codeowners.backend.SimplePathExpressionMatcher}. */
public class SimplePathExpressionMatcherTest extends AbstractPathExpressionMatcherTest {
  @Override
  protected PathExpressionMatcher getPathExpressionMatcher() {
    return SimplePathExpressionMatcher.INSTANCE;
  }

  @Test
  public void matchConcreteFileInCurrentFolder() throws Exception {
    String pathExpression = "BUILD";
    assertMatch(pathExpression, "BUILD");
    assertNoMatch(pathExpression, "README", "BUILD2", "foo/BUILD");
  }

  @Test
  public void matchFileTypeInCurrentFolder() throws Exception {
    String pathExpression = "*.md";
    assertMatch(pathExpression, "README.md", "config.md");
    assertNoMatch(pathExpression, "README", "README.md5", "foo/README.md");
  }

  // There is no matchConcreteFileInCurrentFolderAndAllSubfolders() test since it's not possible to
  // express this with a single simple path expression (you would need 2 path expressions: e.g.
  // 'BUILD' + '.../BUILD').

  @Test
  public void matchConcreteFileInAllSubfolders() throws Exception {
    String pathExpression = ".../BUILD";
    assertMatch(pathExpression, "foo/BUILD", "foo/bar/BUILD");
    assertNoMatch(pathExpression, "BUILD");
  }

  @Test
  public void matchFileTypeInCurrentFolderAndAllSubfolders() throws Exception {
    String pathExpression = "....md";
    assertMatch(pathExpression, "README.md", "config.md", "foo/README.md", "foo/bar/README.md");
    assertNoMatch(pathExpression, "README", "README.md5");
  }

  @Test
  public void matchAllFilesInSubfolder() throws Exception {
    String pathExpression = "foo/...";
    assertMatch(
        pathExpression,
        "foo/README.md",
        "foo/config.txt",
        "foo/bar/README.md",
        "foo/bar/baz/README.md");
    assertNoMatch(pathExpression, "README", "foo2/README");
  }

  @Test
  public void patternIsNotMatched() throws Exception {
    String pathExpression = "{**/,}foo-[1-4].txt";
    assertNoMatch(pathExpression, "foo-1.txt", "foo-2.txt", "sub/foo-3.txt", "sub/sub/foo-4.txt");
    assertMatch(pathExpression, pathExpression);
  }
}
