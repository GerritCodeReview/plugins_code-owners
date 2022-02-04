// Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.codeowners.backend.CodeOwnersInternalServerErrorException.newInternalServerError;

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import org.junit.Test;

/** Tests for {@link CodeOwnersInternalServerErrorException}. */
public class CodeOwnersInternalServerErrorExceptionTest extends AbstractCodeOwnersTest {
  @Test
  public void codeOwnersInternalServerErrorExceptionIsCreatedByDefault() {
    assertThat(newInternalServerError("foo", new NullPointerException("bar")))
        .isInstanceOf(CodeOwnersInternalServerErrorException.class);
    assertThat(
            newInternalServerError("foo", newExceptionWithCause(new NullPointerException("bar"))))
        .isInstanceOf(CodeOwnersInternalServerErrorException.class);
  }

  @Test
  public void storageExceptionIsCreatedForNonCodeOwnerErrors() {
    assertThat(newInternalServerError("foo", new DiffNotAvailableException("bar")))
        .isInstanceOf(StorageException.class);
    assertThat(
            newInternalServerError(
                "foo", newExceptionWithCause(new DiffNotAvailableException("bar"))))
        .isInstanceOf(StorageException.class);
  }

  private Exception newExceptionWithCause(Exception cause) {
    return new Exception("exception1", new Exception("exception2", cause));
  }
}
