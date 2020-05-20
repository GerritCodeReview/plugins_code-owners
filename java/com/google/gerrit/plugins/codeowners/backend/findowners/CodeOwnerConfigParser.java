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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.inject.Inject;
import com.google.inject.Singleton;

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
 *   <li>comment: a line can be a comment (comments must start with '#')
 *   <li>code owner emails: a line can be the email of a code owner
 * </ul>
 *
 * <p>Comment lines, invalid lines and invalid emails are silently ignored.
 *
 * <p>Comments cannot appear as part of syntax lines, but only as separate lines (e.g. using
 * 'foo.bar@example.com # Foo Bar' would be invalid).
 */
@Singleton
class CodeOwnerConfigParser {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * To validate emails we use the same validator that Gerrit core is using to validate emails for
   * Gerrit accounts.
   *
   * <p>Emails in code owner configurations must be resolveable to Gerrit accounts. This means any
   * email that is invalid for Gerrit accounts, is also invalid as code owner (if an email cannot be
   * added to a Gerrit account, it can never be resolved to a Gerrit account and hence it makes no
   * sense to allow it as code owner).
   */
  private final OutgoingEmailValidator emailValidator;

  @Inject
  CodeOwnerConfigParser(OutgoingEmailValidator emailValidator) {
    this.emailValidator = emailValidator;
  }
  /**
   * Parses a {@link CodeOwnerConfig} from a string that represents the content of an {@code OWNERS}
   * file.
   *
   * <p>Comment lines, invalid lines and invalid emails are silently ignored.
   *
   * @param codeOwnerConfigKey the key of the code owner config that should be parsed
   * @param codeOwnerConfigFileContent string that represents the content of an {@code OWNERS} file
   * @return the parsed {@link CodeOwnerConfig}
   */
  CodeOwnerConfig parse(CodeOwnerConfig.Key codeOwnerConfigKey, String codeOwnerConfigFileContent) {
    CodeOwnerConfig.Builder codeOwnerConfig = CodeOwnerConfig.builder(codeOwnerConfigKey);

    Streams.stream(Splitter.on('\n').split(codeOwnerConfigFileContent))
        .map(String::trim)
        .filter(line -> !isComment(line))
        .filter(this::isEmail)
        .distinct()
        .forEach(codeOwnerConfig::addCodeOwnerEmail);

    return codeOwnerConfig.build();
  }

  private static boolean isComment(String trimmedLine) {
    return trimmedLine.startsWith("#");
  }

  private boolean isEmail(String trimmedLine) {
    if (!emailValidator.isValid(trimmedLine)) {
      logger.atWarning().log("Skipping line that is not an email: %s", trimmedLine);
      return false;
    }
    return true;
  }
}
