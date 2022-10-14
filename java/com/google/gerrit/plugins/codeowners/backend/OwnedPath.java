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
import com.google.gerrit.entities.Account;
import java.lang.Iterable;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Representation of a file path the may be owned by the user. */
@AutoValue
public abstract class OwnedPath {
  /** The path of the file that may be owned by the user. */
  public abstract Path path();

  /** Whether the user owns this path. */
  public abstract boolean owned();

  /** The reviewers that are owners of this path.  */
  public abstract Iterable<Account.Id> owners();

  public static OwnedPath create(Path path, boolean owned, Iterable<Account.Id> owners) {
    return new AutoValue_OwnedPath(path, owned, owners);
  }
}
