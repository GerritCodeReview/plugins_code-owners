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

/**
 * A reference that defines a code owner in code owner configurations.
 *
 * <p>At the moment we only support emails to specify code owners in code owner configurations, but
 * in the future we may support other identifiers to reference code owners, which is why we have
 * this class as an abstraction.
 */
@AutoValue
public abstract class CodeOwnerReference {
  /** Gets the email of the code owner. */
  public abstract String email();

  /**
   * Creates a reference to a code owner by email.
   *
   * @param email the email of the code owner
   * @return a reference to a code owner by email
   */
  public static CodeOwnerReference create(String email) {
    return new AutoValue_CodeOwnerReference.Builder().setEmail(email).build();
  }

  @AutoValue.Builder
  abstract static class Builder {
    /**
     * Sets the email of the code owner for the code owner reference.
     *
     * @param email the email of the code owner
     * @return the Builder instance for chaining calls
     */
    public abstract Builder setEmail(String email);

    /**
     * Builds the {@link CodeOwnerReference} instance.
     *
     * @return the {@link CodeOwnerReference} instance
     */
    abstract CodeOwnerReference build();
  }
}
