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

import java.io.IOException;

/**
 * Parser and formatter for {@link CodeOwnerConfig}s.
 *
 * <p>To be implemented by {@link CodeOwnersBackend}s, as the syntax that is used to represent a
 * {@link CodeOwnerConfig} as string depends on the {@link CodeOwnersBackend}.
 *
 * <p>Most {@link CodeOwnersBackend}s store the string representations of {@link CodeOwnerConfig}s
 * in files, but other storages are also possible.
 */
public interface CodeOwnerConfigParser {
  /**
   * Parses a {@link CodeOwnerConfig} from a string.
   *
   * <p>Most code owners backends store code owner configs in files. In this case the provided
   * string is the file content.
   *
   * <p><strong>Note:</strong> Parsing a code owner config by using the {@link
   * #parse(CodeOwnerConfig.Key, String)} and then formatting the parsed code owner config back to a
   * string by using {@link #formatAsString(CodeOwnerConfig)} is not guaranteed to result in the
   * exact same code owner config file (e.g. comment lines, invalid lines and invalid emails may be
   * dropped or emails may be reordered).
   *
   * @param codeOwnerConfigKey the key of the code owner config that should be parsed
   * @param codeOwnerConfigAsString the code owner configuration as string, e.g. the content of the
   *     code owner config file
   * @return the parsed {@link CodeOwnerConfig}
   * @throws IOException throw is there is an IO error during the parsing
   */
  CodeOwnerConfig parse(CodeOwnerConfig.Key codeOwnerConfigKey, String codeOwnerConfigAsString)
      throws IOException;

  /**
   * Formats the given code owner config as string.
   *
   * <p>Most code owners backends store the string representation of the code owner config in a
   * file.
   *
   * @param codeOwnerConfig the code owner config that should be formatted
   * @return the code owner config as string
   * @throws IOException throw is there is an IO error during the formatting
   */
  String formatAsString(CodeOwnerConfig codeOwnerConfig) throws IOException;
}
