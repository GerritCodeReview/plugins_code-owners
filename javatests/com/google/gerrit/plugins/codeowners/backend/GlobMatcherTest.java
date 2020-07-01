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
import org.junit.Test;

/** Tests for {@link GlobMatcher}. */
public class GlobMatcherTest extends AbstractCodeOwnersTest {
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

  @Test
  public void matchConcreteFileInCurrentFolderAndAllSubfolders() throws Exception {
    String pathExpression = "{**/,}BUILD";
    assertMatch(pathExpression, "BUILD", "foo/BUILD", "foo/bar/BUILD");
    assertNoMatch(pathExpression, "README", "BUILD2");
  }

  @Test
  public void matchFileTypeInCurrentFolderAndAllSubfolders() throws Exception {
    String pathExpression = "**.md";
    assertMatch(pathExpression, "README.md", "config.md", "foo/README.md", "foo/bar/README.md");
    assertNoMatch(pathExpression, "README", "README.md5");
  }

  @Test
  public void matchAllFilesInSubfolder() throws Exception {
    String pathExpression = "foo/**";
    assertMatch(
        pathExpression,
        "foo/README.md",
        "foo/config.txt",
        "foo/bar/README.md",
        "foo/bar/baz/README.md");
    assertNoMatch(pathExpression, "README", "foo2/README");
  }

  @Test
  public void matchPattern() throws Exception {
    String pathExpression = "{**/,}foo-[1-4].txt";
    assertMatch(pathExpression, "foo-1.txt", "foo-2.txt", "sub/foo-3.txt", "sub/sub/foo-4.txt");
    assertNoMatch(pathExpression, "foo-5.txt", "foo-11.txt");
  }

  private static void assertMatch(String pathExpression, String firstPath, String... morePaths) {
    assertMatch(true, pathExpression, firstPath, morePaths);
  }

  private static void assertNoMatch(String pathExpression, String firstPath, String... morePaths) {
    assertMatch(false, pathExpression, firstPath, morePaths);
  }

  private static void assertMatch(
      boolean expectedToMatch, String pathExpression, String firstPath, String... morePaths) {
    for (String path : toList(firstPath, morePaths)) {
      assertWithMessage("path expression %s matches path %s", pathExpression, path)
          .that(GlobMatcher.INSTANCE.matches(pathExpression, Paths.get(path)))
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
