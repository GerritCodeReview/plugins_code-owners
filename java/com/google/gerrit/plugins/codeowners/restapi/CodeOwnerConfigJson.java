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

package com.google.gerrit.plugins.codeowners.restapi;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerConfigInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerReferenceInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;

/** Collection of routines to populate {@link CodeOwnerConfigInfo}. */
public final class CodeOwnerConfigJson {
  /**
   * Formats the provided {@link CodeOwnerConfig} as {@link CodeOwnerConfigInfo}.
   *
   * @param codeOwnerConfig the {@link CodeOwnerConfig} that should be formatted as {@link
   *     CodeOwnerConfigInfo}
   * @return the provided {@link CodeOwnerConfig} as {@link CodeOwnerConfigInfo}
   */
  public static CodeOwnerConfigInfo format(CodeOwnerConfig codeOwnerConfig) {
    requireNonNull(codeOwnerConfig, "codeOwnerConfig");
    CodeOwnerConfigInfo info = new CodeOwnerConfigInfo();
    info.codeOwners =
        codeOwnerConfig.codeOwners().stream()
            .map(CodeOwnerConfigJson::format)
            .collect(toImmutableList());
    return info;
  }

  /**
   * Formats the provided {@link CodeOwnerReference} as {@link CodeOwnerReferenceInfo}.
   *
   * @param codeOwnerReference the {@link CodeOwnerReference} that should be formatted as {@link
   *     CodeOwnerReferenceInfo}
   * @return the provided {@link CodeOwnerReference} as {@link CodeOwnerReferenceInfo}
   */
  @VisibleForTesting
  static CodeOwnerReferenceInfo format(CodeOwnerReference codeOwnerReference) {
    requireNonNull(codeOwnerReference, "codeOwnerReference");
    CodeOwnerReferenceInfo info = new CodeOwnerReferenceInfo();
    info.email = codeOwnerReference.email();
    return info;
  }

  /**
   * Private constructor to prevent instantiation of this class.
   *
   * <p>The class only contains static methods, hence the class never needs to be instantiated.
   */
  private CodeOwnerConfigJson() {}
}
