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

package com.google.gerrit.plugins.codeowners.common;

/** Policy that should be used to validate code owner config files. */
public enum CodeOwnerConfigValidationPolicy {
  /**
   * The code owner config file validation is enabled and invalid code owner config files are
   * rejected.
   *
   * <p>If the code owners functionality is disabled, no validation is performed.
   */
  TRUE,

  /**
   * The code owner config file validation is disabled. Invalid code owner config files are not
   * rejected.
   */
  FALSE,

  /**
   * Code owner config files are validated, but invalid code owner config files are not rejected.
   *
   * <p>If the code owners functionality is disabled, no dry-run validation is performed.
   */
  DRY_RUN,

  /**
   * Code owner config files are validated even if the code owners functionality is disabled.
   *
   * <p>This option is useful when the code owner config validation should be enabled as preparation
   * to enabling the code owners functionality.
   */
  FORCED,

  /**
   * Code owner config files are validated even if the code owners functionality is disabled, but
   * invalid code owner config files are not rejected.
   *
   * <p>This option is useful when the code owner config validation should be enabled as preparation
   * to enabling the code owners functionality.
   */
  FORCED_DRY_RUN;

  public boolean isDryRun() {
    return this == CodeOwnerConfigValidationPolicy.DRY_RUN
        || this == CodeOwnerConfigValidationPolicy.FORCED_DRY_RUN;
  }

  public boolean runValidation() {
    return this != CodeOwnerConfigValidationPolicy.FALSE;
  }

  public boolean isForced() {
    return this == CodeOwnerConfigValidationPolicy.FORCED
        || this == CodeOwnerConfigValidationPolicy.FORCED_DRY_RUN;
  }
}
