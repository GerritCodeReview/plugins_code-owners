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

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerStatus;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import java.nio.file.Path;

/** Code owner status for a particular path that has been modified in a change. */
@AutoValue
public abstract class PathCodeOwnerStatus {
  /**
   * Path to which the {@link #status()} belongs.
   *
   * <p>Always an absolute path.
   */
  public abstract Path path();

  /** The code owner status of the {@link #path()}. */
  public abstract CodeOwnerStatus status();

  /**
   * Message explaining the reason for {@link #status()}.
   *
   * <p>A reason may contain one or several placeholders for accounts (see {@link
   * com.google.gerrit.server.util.AccountTemplateUtil#ACCOUNT_TEMPLATE}).
   */
  public abstract ImmutableList<String> reasons();

  /**
   * List of code owners for this {@link #path()}.
   * This field is only set if `checkOwnerIds` is passed as true to
   * {@link com.google.gerrit.plugins.codeowners.backend.CodeOwnerApprovalCheck#getPathCodeOwnerStatus}.
   */
  public abstract ImmutableSet<Account.Id> owners();

  /** Creates a builder for a {@link PathCodeOwnerStatus}. */
  public static PathCodeOwnerStatus.Builder builder(Path path, CodeOwnerStatus codeOwnerStatus) {
    return new AutoValue_PathCodeOwnerStatus.Builder().path(path).status(codeOwnerStatus);
  }

  /** Creates a builder for a {@link PathCodeOwnerStatus}. */
  public static PathCodeOwnerStatus.Builder builder(String path, CodeOwnerStatus codeOwnerStatus) {
    return builder(JgitPath.of(path).getAsAbsolutePath(), codeOwnerStatus);
  }

  /**
   * Creates a {@link PathCodeOwnerStatus} instance.
   *
   * @param path the path to which the code owner status belongs
   * @param codeOwnerStatus the code owner status
   * @return the created {@link PathCodeOwnerStatus} instance
   */
  public static PathCodeOwnerStatus create(Path path, CodeOwnerStatus codeOwnerStatus) {
    return builder(path, codeOwnerStatus).build();
  }

  /**
   * Creates a {@link PathCodeOwnerStatus} instance.
   *
   * @param path the path to which the code owner status belongs
   * @param codeOwnerStatus the code owner status
   * @param reason for the status
   * @return the created {@link PathCodeOwnerStatus} instance
   */
  public static PathCodeOwnerStatus create(
      Path path, CodeOwnerStatus codeOwnerStatus, @Nullable String reason) {
    Builder builder = builder(path, codeOwnerStatus);
    if (reason != null) {
      builder.addReason(reason);
    }
    return builder.build();
  }

  /**
   * Creates a {@link PathCodeOwnerStatus} instance.
   *
   * @param path the path to which the code owner status belongs
   * @param codeOwnerStatus the code owner status
   * @param reason for the status
   * @param owners owners for this path.
   * @return the created {@link PathCodeOwnerStatus} instance
   */
  public static PathCodeOwnerStatus create(
      Path path,
      CodeOwnerStatus codeOwnerStatus,
      @Nullable String reason,
      ImmutableSet<Account.Id> owners) {
    Builder builder = builder(path, codeOwnerStatus);
    if (reason != null) {
      builder.addReason(reason);
    }
    builder.addOwners(owners);
    return builder.build();
  }

  /**
   * Creates a {@link PathCodeOwnerStatus} instance.
   *
   * @param path the path to which the code owner status belongs
   * @param codeOwnerStatus the code owner status
   * @return the created {@link PathCodeOwnerStatus} instance
   */
  public static PathCodeOwnerStatus create(String path, CodeOwnerStatus codeOwnerStatus) {
    requireNonNull(path, "path");
    requireNonNull(codeOwnerStatus, "codeOwnerStatus");

    return builder(path, codeOwnerStatus).build();
  }

  /** Builder for a {@link PathCodeOwnerStatus}. */
  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Sets the path to which the status belongs.
     *
     * @param path absolute path to which the status belongs
     */
    public abstract Builder path(Path path);

    /** Sets the code owner status of the path. */
    public abstract Builder status(CodeOwnerStatus codeOwnerStatus);

    /** Gets a builder for adding reasons for this status. */
    abstract ImmutableList.Builder<String> reasonsBuilder();

    /** Gets a builder for adding owners for this status. */
    abstract ImmutableSet.Builder<Account.Id> ownersBuilder();

    /** Adds a reason for this status. */
    public Builder addReason(String reason) {
      reasonsBuilder().add(reason);
      return this;
    }

    /** Adds owners for this status */
    public Builder addOwners(ImmutableSet<Account.Id> owners) {
      ownersBuilder().addAll(owners);
      return this;
    }

    /** Builds the {@link PathCodeOwnerStatus} instance. */
    public abstract PathCodeOwnerStatus build();
  }
}
