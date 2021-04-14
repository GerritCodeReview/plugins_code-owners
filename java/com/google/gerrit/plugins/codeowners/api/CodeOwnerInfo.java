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
import com.google.gerrit.extensions.common.AccountInfo;

/**
 * Representation of a code owner in the REST API.
 *
 * <p>This class determines the JSON format of code owners in the REST API.
 */
public class CodeOwnerInfo {
  /** The account of the code owner. */
  public AccountInfo account;

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("account", account).toString();
  }
}
