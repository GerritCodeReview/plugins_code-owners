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

package com.google.gerrit.plugins.codeowners.api;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;

/**
 * Representation of a {@link com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig} in the
 * REST API.
 *
 * <p>This class determines the JSON format of code owner configs in the REST API.
 */
public class CodeOwnerConfigInfo {
  /**
   * Whether code owners from parent code owner configs (code owner configs in parent folders)
   * should be ignored.
   *
   * <p>Not set if {@code false}.
   */
  public Boolean ignoreParentCodeOwners;

  /**
   * The code owners of this code owner config.
   *
   * <p>Not set if there are no code owners defined in this code owner config.
   */
  public List<CodeOwnerReferenceInfo> codeOwners;

  @Override
  public int hashCode() {
    return Objects.hash(codeOwners);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CodeOwnerConfigInfo)) {
      return false;
    }
    CodeOwnerConfigInfo other = (CodeOwnerConfigInfo) o;
    return Objects.equals(codeOwners, other.codeOwners);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("codeOwners", codeOwners).toString();
  }
}
