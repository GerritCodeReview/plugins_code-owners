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

package com.google.gerrit.plugins.codeowners.api;

import com.google.gerrit.extensions.common.AccountInfo;
import java.util.List;
import java.util.Optional;

/** JSON representation of a file path the may be owned by the user. */
public class OwnedPathInfo {
  /** The path of the file that may be owned by the user. */
  public String path;

  /**
   * Whether the user owns this path. This is computed for the given 'user' parameter.
   *
   * <p>Not set if {@code false}.
   */
  public Boolean owned;

  /**
   * The owners for the given file as Account IDs.
   *
   * <p>May not be set if the list is empty.
   */
  public Optional<List<AccountInfo>> owners;
}
