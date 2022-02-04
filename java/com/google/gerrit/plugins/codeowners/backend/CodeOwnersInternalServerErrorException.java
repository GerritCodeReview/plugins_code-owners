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

import com.google.common.base.Throwables;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.patch.DiffNotAvailableException;

/** Exception signaling an internal server error in the code-owners plugin. */
public class CodeOwnersInternalServerErrorException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private static final String USER_MESSAGE = "Internal server in code-owners plugin";

  /**
   * Creates a {@link CodeOwnersInternalServerErrorException} to signal an internal server error
   * caused by an issue in the code-owners plugin..
   *
   * @param message the exception message
   * @return the created exception
   */
  public static CodeOwnersInternalServerErrorException newInternalServerError(String message) {
    return new CodeOwnersInternalServerErrorException(message);
  }

  /**
   * Creates a {@link RuntimeException} to signal an internal server error.
   *
   * <p>By default it is assumed that the internal server error is caused by an issue in the
   * code-owners plugin and a {@link CodeOwnersInternalServerErrorException} is returned.
   *
   * <p>However for some known causes that are unrelated to code owners a {@link StorageException}
   * is thrown. This is to avoid that the code-owners plugin is mistakenly assumed to be the cause
   * of these errors.
   *
   * @param message the exception message
   * @param cause the exception cause
   * @return the created exception
   */
  public static RuntimeException newInternalServerError(String message, Throwable cause) {
    if (isNonCodeOwnersCause(cause)) {
      return new StorageException(message, cause);
    }
    return new CodeOwnersInternalServerErrorException(message, cause);
  }

  private CodeOwnersInternalServerErrorException(String message) {
    super(message);
  }

  private CodeOwnersInternalServerErrorException(String message, Throwable cause) {
    super(message, cause);
  }

  public String getUserVisibleMessage() {
    return USER_MESSAGE;
  }

  private static boolean isNonCodeOwnersCause(Throwable throwable) {
    return hasCause(DiffNotAvailableException.class, throwable);
  }

  private static boolean hasCause(Class<?> exceptionClass, Throwable throwable) {
    return Throwables.getCausalChain(throwable).stream().anyMatch(exceptionClass::isInstance);
  }
}
