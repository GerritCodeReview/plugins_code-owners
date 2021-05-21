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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import java.nio.file.Path;
import java.util.List;

/** The result of resolving path code owners via {@link PathCodeOwners}. */
@AutoValue
public abstract class PathCodeOwnersResult {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Gets the path for which the code owner config was resolved. */
  abstract Path path();

  /** Gets the resolved code owner config. */
  abstract CodeOwnerConfig codeOwnerConfig();

  /** Gets a list of unresolved imports. */
  public abstract ImmutableList<UnresolvedImport> unresolvedImports();

  /** Whether there are unresolved imports. */
  public boolean hasUnresolvedImports() {
    return !unresolvedImports().isEmpty();
  }

  /**
   * Gets the code owners from the code owner config that apply to the path.
   *
   * <p>Code owners from inherited code owner configs are not considered.
   *
   * @return the code owners of the path
   */
  public ImmutableSet<CodeOwnerReference> getPathCodeOwners() {
    logger.atFine().log(
        "retrieving path code owners for %s from %s", path(), codeOwnerConfig().key());
    ImmutableSet<CodeOwnerReference> pathCodeOwners =
        codeOwnerConfig().codeOwnerSets().stream()
            .flatMap(codeOwnerSet -> codeOwnerSet.codeOwners().stream())
            .collect(toImmutableSet());
    logger.atFine().log("pathCodeOwners = %s", pathCodeOwners);
    return pathCodeOwners;
  }

  /**
   * Gets the annotations for all path code owners that are returned by {@link
   * #getPathCodeOwners()}.
   *
   * @return annotations by code owner
   */
  public ImmutableMultimap<CodeOwnerReference, CodeOwnerAnnotation> getAnnotations() {
    logger.atFine().log(
        "retrieving path code owner annotations for %s from %s", path(), codeOwnerConfig().key());
    ImmutableMultimap.Builder<CodeOwnerReference, CodeOwnerAnnotation> annotationsBuilder =
        ImmutableMultimap.builder();
    codeOwnerConfig()
        .codeOwnerSets()
        .forEach(codeOwnerSet -> annotationsBuilder.putAll(codeOwnerSet.annotations()));

    ImmutableMultimap<CodeOwnerReference, CodeOwnerAnnotation> annotations =
        annotationsBuilder.build();
    logger.atFine().log("annotations = %s", annotations);
    return annotations;
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
  public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("path", path())
        .add("codeOwnerConfig", codeOwnerConfig())
        .add("unresolvedImports", unresolvedImports())
        .toString();
  }

  /** Creates a {@link PathCodeOwnersResult} instance. */
  public static PathCodeOwnersResult create(
      Path path, CodeOwnerConfig codeOwnerConfig, List<UnresolvedImport> unresolvedImports) {
    return new AutoValue_PathCodeOwnersResult(
        path, codeOwnerConfig, ImmutableList.copyOf(unresolvedImports));
  }
}
