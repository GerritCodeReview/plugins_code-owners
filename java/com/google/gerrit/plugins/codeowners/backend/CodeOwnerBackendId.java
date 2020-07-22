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

import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import java.util.Arrays;

/**
 * Enum of all code owner backend IDs.
 *
 * <p>This enum should contain all registered code owner backends.
 *
 * <p>This enum is used to execute the integration tests for all code owner backends (see {@link
 * com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT}.
 */
public enum CodeOwnerBackendId {
  FIND_OWNERS(FindOwnersBackend.ID, FindOwnersBackend.class),
  PROTO(ProtoBackend.ID, ProtoBackend.class);

  /** The ID under which the code owner backend is registered. */
  private final String backendId;

  /** The class that implements the code owner backend. */
  private final Class<? extends CodeOwnerBackend> codeOwnerBackendClass;

  private CodeOwnerBackendId(
      String backendId, Class<? extends CodeOwnerBackend> codeOwnerBackendClass) {
    this.backendId = backendId;
    this.codeOwnerBackendClass = codeOwnerBackendClass;
  }

  /** Gets the ID under which the code owner backend is registered. */
  public String getBackendId() {
    return backendId;
  }

  /** Gets the class that implements the code owner backend. */
  public Class<? extends CodeOwnerBackend> getCodeOwnerBackendClass() {
    return codeOwnerBackendClass;
  }

  /**
   * Returns the ID for the given code owner backend.
   *
   * <p>Throws {@link IllegalStateException} if the given code owner backend is not known.
   *
   * @param codeOwnerBackendClass the code owner backend class for which the ID should be returned.
   */
  public static String getBackendId(Class<? extends CodeOwnerBackend> codeOwnerBackendClass) {
    return Arrays.stream(values())
        .filter(
            codeOwnerBackendId ->
                codeOwnerBackendId.getCodeOwnerBackendClass().equals(codeOwnerBackendClass))
        .map(CodeOwnerBackendId::getBackendId)
        .findAny()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "unknown code owner backend: %s", codeOwnerBackendClass.getName())));
  }
}
