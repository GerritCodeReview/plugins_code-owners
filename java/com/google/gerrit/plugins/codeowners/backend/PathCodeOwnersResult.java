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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import java.nio.file.Path;

/** The result of resolving path code owners via {@link PathCodeOwners}. */
@AutoValue
public abstract class PathCodeOwnersResult {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Gets the path for which the code owner config was resolved. */
  abstract Path path();

  /** Gets the resolved code owner config. */
  abstract CodeOwnerConfig codeOwnerConfig();

  /** Whether there are unresolved imports. */
  public abstract boolean hasUnresolvedImports();

  /**
   * Gets the code owners from the code owner config that apply to the path.
   *
   * <p>Code owners from inherited code owner configs are not considered.
   *
   * @return the code owners of the path
   */
  public ImmutableSet<CodeOwnerReference> getPathCodeOwners() {
    logger.atFine().log(
        "computing path code owners for %s from %s", path(), codeOwnerConfig().key());
    ImmutableSet<CodeOwnerReference> pathCodeOwners =
        codeOwnerConfig().codeOwnerSets().stream()
            .flatMap(codeOwnerSet -> codeOwnerSet.codeOwners().stream())
            .collect(toImmutableSet());
    logger.atFine().log("pathCodeOwners = %s", pathCodeOwners);
    return pathCodeOwners;
  }

  /**
   * Whether parent code owners should be ignored for the path.
   *
   * @return whether parent code owners should be ignored for the path
   */
  public boolean ignoreParentCodeOwners() {
    return codeOwnerConfig().ignoreParentCodeOwners();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("path", path())
        .add("codeOwnerConfig", codeOwnerConfig())
        .add("hasUnresolvedImports", hasUnresolvedImports())
        .toString();
  }

  /** Creates a {@link CodeOwnerResolverResult} instance. */
  public static PathCodeOwnersResult create(
      Path path, CodeOwnerConfig codeOwnerConfig, boolean hasUnresolvedImports) {
    return new AutoValue_PathCodeOwnersResult(path, codeOwnerConfig, hasUnresolvedImports);
  }
}
