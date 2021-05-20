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

/**
 * An annotation on a {@link CodeOwnerReference} in a {@link CodeOwnerConfig}.
 *
 * <p>This is a class rather than string so that we can easily support values on annotations later.
 */
@AutoValue
public abstract class CodeOwnerAnnotation {
  public abstract String key();

  public static CodeOwnerAnnotation create(String key) {
    return new AutoValue_CodeOwnerAnnotation(key);
  }
}
