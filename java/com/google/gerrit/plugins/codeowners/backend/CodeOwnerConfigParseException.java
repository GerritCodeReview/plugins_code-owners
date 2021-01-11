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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.git.ValidationError;
import com.google.gerrit.server.validators.ValidationException;
import java.util.List;

/** Exception that is thrown if a code owner config cannot be parsed. */
public class CodeOwnerConfigParseException extends ValidationException {
  private static final long serialVersionUID = 1L;

  private final CodeOwnerConfig.Key codeOwnerConfigKey;
  private final ImmutableList<ValidationError> messages;

  public CodeOwnerConfigParseException(
      CodeOwnerConfig.Key codeOwnerConfigKey, List<ValidationError> messages) {
    super("invalid code owner config file");
    this.codeOwnerConfigKey = codeOwnerConfigKey;
    this.messages = ImmutableList.copyOf(messages);
  }

  public CodeOwnerConfigParseException(CodeOwnerConfig.Key codeOwnerConfigKey, String message) {
    this(codeOwnerConfigKey, ImmutableList.of(ValidationError.create(message)));
  }

  /** Returns all validation as a single, formatted string. */
  public String getFullMessage(String defaultCodeOwnerConfigFileName) {
    StringBuilder sb = new StringBuilder(getMessage());
    sb.append(
        String.format(
            " '%s' (project = %s, branch = %s)",
            codeOwnerConfigKey.filePath(defaultCodeOwnerConfigFileName),
            codeOwnerConfigKey.project(),
            codeOwnerConfigKey.shortBranchName()));
    if (!messages.isEmpty()) {
      sb.append(':');
      for (ValidationError msg : messages) {
        sb.append("\n  ").append(msg.getMessage());
      }
    }
    return sb.toString();
  }
}
