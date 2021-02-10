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

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;

/** Information about an unresolved import. */
@AutoValue
public abstract class UnresolvedImport {
  /** Key of the importing code owner config. */
  public abstract CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig();

  /** Key of the imported code owner config. */
  public abstract CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig();

  /** The code owner config reference that was attempted to be resolved. */
  public abstract CodeOwnerConfigReference codeOwnerConfigReference();

  /** Message explaining why the code owner config reference couldn't be resolved. */
  public abstract String message();

  @Override
  public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("keyOfImportingCodeOwnerConfig", keyOfImportingCodeOwnerConfig())
        .add("keyOfImportedCodeOwnerConfig", keyOfImportedCodeOwnerConfig())
        .add("codeOwnerConfigReference", codeOwnerConfigReference())
        .add("message", message())
        .toString();
  }

  /** Creates a {@link UnresolvedImport} instance. */
  static UnresolvedImport create(
      CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig,
      CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig,
      CodeOwnerConfigReference codeOwnerConfigReference,
      String message) {
    return new AutoValue_UnresolvedImport(
        keyOfImportingCodeOwnerConfig,
        keyOfImportedCodeOwnerConfig,
        codeOwnerConfigReference,
        message);
  }
}
