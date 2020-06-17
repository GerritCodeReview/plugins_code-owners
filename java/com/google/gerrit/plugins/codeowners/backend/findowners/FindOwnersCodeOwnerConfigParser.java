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

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.inject.Singleton;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser and formatter for the syntax that is used to store {@link CodeOwnerConfig}s in {@code
 * OWNERS} files as they are used by the {@code find-owners} plugin.
 *
 * <p>The syntax is described at in the {@code find-owners} plugin documentation at:
 * https://gerrit.googlesource.com/plugins/find-owners/+/master/src/main/resources/Documentation/syntax.md
 *
 * <p><strong>Note:</strong> Currently this class only supports a subset of the syntax. Only the
 * following syntax elements are supported:
 *
 * <ul>
 *   <li>comment: a line can be a comment (comments must start with '#')
 *   <li>code owner emails: a line can be the email of a code owner
 * </ul>
 *
 * <p>Comment lines and invalid lines silently ignored.
 *
 * <p>Comments can appear as separate lines and as appendix for email lines (e.g. using
 * 'foo.bar@example.com # Foo Bar' would be a valid email line).
 *
 * <p>Most of the code in this class was copied from the {@code
 * com.googlesource.gerrit.plugins.findowners.Parser} class from the {@code find-owners} plugin. The
 * original parsing code is used to be as backwards-compatible as possible and to avoid spending
 * time on reimplementing a parser for a deprecated syntax. We have only done a minimal amount of
 * adaption so that the parser produces a {@link CodeOwnerConfig} as result, instead of the
 * abstraction that is used in the {@code find-owners} plugin.
 */
@Singleton
@VisibleForTesting
public class FindOwnersCodeOwnerConfigParser implements CodeOwnerConfigParser {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String BOL = "^[\\s]*"; // begin-of-line
  private static final String EOL = "[\\s]*(#.*)?$"; // end-of-line

  private static final String EMAIL = "([^\\s<>@,]+@[^\\s<>@#,]+)";
  private static final String SET_NOPARENT = "set[\\s]+noparent";

  // Simple input lines with 0 or 1 sub-pattern.
  private static final Pattern PAT_COMMENT = Pattern.compile(BOL + EOL);
  private static final Pattern PAT_EMAIL = Pattern.compile(BOL + EMAIL + EOL);
  private static final Pattern PAT_NO_PARENT = Pattern.compile(BOL + SET_NOPARENT + EOL);

  private static final String SET_NOPARENT_LINE = "set noparent\n";

  @Override
  public CodeOwnerConfig parse(
      CodeOwnerConfig.Key codeOwnerConfigKey, String codeOwnerConfigAsString) {
    return parseFile(
        requireNonNull(codeOwnerConfigKey, "codeOwnerConfigKey"),
        Strings.nullToEmpty(codeOwnerConfigAsString).split("\\R"));
  }

  @Override
  public String formatAsString(CodeOwnerConfig codeOwnerConfig) {
    requireNonNull(codeOwnerConfig, "codeOwnerConfig");

    StringBuilder b = new StringBuilder();
    if (codeOwnerConfig.ignoreParentCodeOwners()) {
      b.append(SET_NOPARENT_LINE);
    }
    if (!codeOwnerConfig.codeOwners().isEmpty()) {
      b.append(
          codeOwnerConfig.codeOwners().stream()
              .map(CodeOwnerReference::email)
              .sorted()
              .distinct()
              .collect(joining("\n", "", "\n")));
    }
    return b.toString();
  }

  private CodeOwnerConfig parseFile(CodeOwnerConfig.Key codeOwnerConfigKey, String[] lines) {
    CodeOwnerConfig.Builder codeOwnerConfigBuilder = CodeOwnerConfig.builder(codeOwnerConfigKey);
    for (String line : lines) {
      parseLine(codeOwnerConfigBuilder, line);
    }
    return codeOwnerConfigBuilder.build();
  }

  private void parseLine(CodeOwnerConfig.Builder codeOwnerConfigBuilder, String line) {
    String email;
    if (isNoParent(line)) {
      codeOwnerConfigBuilder.setIgnoreParentCodeOwners();
    } else if (isComment(line)) {
      // ignore comment lines and empty lines
    } else if ((email = parseEmail(line)) != null) {
      codeOwnerConfigBuilder.addCodeOwner(CodeOwnerReference.create(email));
    } else {
      logger.atInfo().log("Skipping unknown line: %s", line);
    }
  }

  private static boolean isComment(String line) {
    return PAT_COMMENT.matcher(line).matches();
  }

  private static boolean isNoParent(String line) {
    return PAT_NO_PARENT.matcher(line).matches();
  }

  private static String parseEmail(String line) {
    Matcher m = PAT_EMAIL.matcher(line);
    return m.matches() ? m.group(1).trim() : null;
  }
}
