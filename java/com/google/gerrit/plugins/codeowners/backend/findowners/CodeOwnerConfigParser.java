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

package com.google.gerrit.plugins.codeowners.backend.findowners;

import com.google.common.base.Splitter;
import com.google.common.collect.Streams;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;

/**
 * Parser for {@code OWNERS} files as they are used by the {@code find-owners} plugin.
 *
 * <p>The syntax is described at in the {@code find-owners} plugin documentation at:
 * https://gerrit.googlesource.com/plugins/find-owners/+/master/src/main/resources/Documentation/syntax.md
 *
 * <p><strong>Note:</strong> Currently this class only supports a subset of the syntax. Only the
 * following syntax elements are supported:
 *
 * <ul>
 *   <li>code owner emails: a line can be the email of a code owner
 * </ul>
 *
 * <p>Invalid lines are silently ignored.
 */
final class CodeOwnerConfigParser {
  /**
   * Parses a {@link CodeOwnerConfig} from a string that represents the content of an {@code OWNERS}
   * file.
   *
   * <p>Invalid lines are silently ignored.
   *
   * @param codeOwnerConfigKey the key of the code owner config that should be parsed
   * @param codeOwnerConfigFileContent string that represents the content of an {@code OWNERS} file
   * @return the parsed {@link CodeOwnerConfig}
   */
  static CodeOwnerConfig parse(
      CodeOwnerConfig.Key codeOwnerConfigKey, String codeOwnerConfigFileContent) {
    CodeOwnerConfig.Builder codeOwnerConfig = CodeOwnerConfig.builder(codeOwnerConfigKey);

    Streams.stream(Splitter.on('\n').split(codeOwnerConfigFileContent))
        .map(String::trim)
        .filter(CodeOwnerConfigParser::isEmail)
        .distinct()
        .forEach(codeOwnerConfig::addCodeOwnerEmail);

    return codeOwnerConfig.build();
  }

  private static boolean isEmail(String trimmedLine) {
    // TODO(ekempin): Make this check smarter.
    return trimmedLine.contains("@");
  }

  /**
   * Private constructor to prevent instantiation of this class.
   *
   * <p>The class only contains static methods, hence the class never needs to be instantiated.
   */
  private CodeOwnerConfigParser() {}
}
