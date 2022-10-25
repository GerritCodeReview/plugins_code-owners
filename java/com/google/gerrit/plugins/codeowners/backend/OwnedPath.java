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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import java.nio.file.Path;

/** Representation of a file path and its code owners. */
@AutoValue
public abstract class OwnedPath {
  /** The path of the file that may be owned. */
  public abstract Path path();

  /** Whether the specified user owns this path. */
  public abstract boolean owned();

  /** The owners for this path. */
  public abstract ImmutableSet<Account.Id> owners();

  public static OwnedPath create(Path path, boolean owned, ImmutableSet<Account.Id> owners) {
    return new AutoValue_OwnedPath(path, owned, owners);
  }
}
