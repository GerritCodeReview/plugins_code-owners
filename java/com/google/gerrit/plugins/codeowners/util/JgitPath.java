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

package com.google.gerrit.plugins.codeowners.util;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Converts a path so that it can be used for the jgit API.
 *
 * <p>The jgit API doesn't accept leading '/' for absolute paths, so this class makes sure to remove
 * it.
 */
public class JgitPath {
  private final String jgitPath;

  /**
   * Creates a {@link JgitPath} from the given path.
   *
   * @param path absolute path that should be converted to a jgit path, or a jgit path that should
   *     be converted to an absolute path
   */
  public static JgitPath of(String path) {
    return new JgitPath(path);
  }

  /**
   * Creates a {@link JgitPath} from the given path.
   *
   * @param path absolute path that should be converted to a jgit path, or a jgit path that should
   *     be converted to an absolute path
   */
  public static JgitPath of(Path path) {
    requireNonNull(path, "path");
    return of(path.toString());
  }

  private JgitPath(String path) {
    requireNonNull(path, "path");
    this.jgitPath = path.startsWith("/") ? path.substring(1) : path;
  }

  /** Returns the path as string that can be used for the jgit API. */
  public String get() {
    return jgitPath;
  }

  /** Returns the path as absolute path. */
  public Path getAsAbsolutePath() {
    return Paths.get("/" + get());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(jgitPath);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof JgitPath)) {
      return false;
    }
    JgitPath other = (JgitPath) o;
    return Objects.equals(jgitPath, other.jgitPath);
  }

  @Override
  public String toString() {
    return get();
  }
}
