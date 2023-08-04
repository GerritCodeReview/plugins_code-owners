// Copyright (C) 2023 The Android Open Source Project
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

import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Utility functions to write code owners {@link ChangeMessage}s. */
public class CodeOwnersChangeMessageUtil {
  /**
   * Appends the given paths to the given message builder.
   *
   * @param message message builder to which the paths should be appended
   * @param pathsToAppend the paths to append to the message builder
   */
  public static void appendPaths(StringBuilder message, Stream<Path> pathsToAppend) {
    pathsToAppend.forEach(
        path -> message.append(String.format("* `%s`\n", JgitPath.of(path).get())));
  }

  /**
   * Private constructor to prevent instantiation of this class.
   *
   * <p>The class only contains static methods, hence the class never needs to be instantiated.
   */
  private CodeOwnersChangeMessageUtil() {}
}
