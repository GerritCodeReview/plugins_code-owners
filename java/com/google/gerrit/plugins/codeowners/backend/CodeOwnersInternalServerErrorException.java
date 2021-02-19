// Copyright (C) 2021 The Android Open Source Project
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

/** Exception signaling an internal server error in the code-owners plugin. */
public class CodeOwnersInternalServerErrorException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private static final String USER_MESSAGE = "Internal server in code-owners plugin";

  public CodeOwnersInternalServerErrorException(String message) {
    super(message);
  }

  public CodeOwnersInternalServerErrorException(String message, Throwable cause) {
    super(message, cause);
  }

  public String getUserVisibleMessage() {
    return USER_MESSAGE;
  }
}
