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
import com.google.gerrit.entities.Account;

/**
 * Representation of a code owner.
 *
 * <p>{@link CodeOwnerConfig}s contain {@link CodeOwnerReference}s that need to be resolved to
 * accounts. After a code owner reference has been resolved the code owner is represented by this
 * class.
 *
 * <p>At the moment we only support accounts as code owners, but in the future we may also support
 * groups as code owners. This is why we have a dedicated class to represent a code owner rather
 * than using {@link com.google.gerrit.entities.Account.Id} directly.
 */
@AutoValue
public abstract class CodeOwner {
  /** Returns the account ID of the code owner. */
  public abstract Account.Id accountId();

  /**
   * Creates a code owner for an account.
   *
   * @param accountId the ID of the account for which an code owner key should be created
   * @return the code owner for the given account
   */
  public static CodeOwner create(Account.Id accountId) {
    return new AutoValue_CodeOwner.Builder().setAccountId(accountId).build();
  }

  @AutoValue.Builder
  abstract static class Builder {
    /**
     * Sets the account ID of the code owner.
     *
     * @param accountId the account ID of the code owner
     * @return the Builder instance for chaining calls
     */
    public abstract Builder setAccountId(Account.Id accountId);

    /**
     * Builds the {@link CodeOwner} instance.
     *
     * @return the {@link CodeOwner} instance
     */
    abstract CodeOwner build();
  }
}
