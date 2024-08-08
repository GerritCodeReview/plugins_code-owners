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
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import java.nio.file.Path;

/** The result of resolving path code owners via {@link PathCodeOwners}. */
@AutoValue
public abstract class PathCodeOwnersResult {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Gets the path for which the code owner config was resolved. */
  abstract Path path();

  /** Gets the key of the resolved code owner config. */
  abstract CodeOwnerConfig.Key codeOwnerConfigKey();

  /** Gets whether parent code owners should be ignored for the path. */
  public abstract boolean ignoreParentCodeOwners();

  /** Gets the relevant code owner sets for the path. */
  abstract ImmutableSet<CodeOwnerSet> codeOwnerSets();

  /** Gets a list of resolved imports. */
  public abstract ImmutableList<CodeOwnerConfigImport> resolvedImports();

  /** Gets a list of unresolved imports. */
  public abstract ImmutableList<CodeOwnerConfigImport> unresolvedImports();

  /** Whether there are unresolved imports. */
  public boolean hasUnresolvedImports() {
    return !unresolvedImports().isEmpty();
  }

  public abstract ImmutableList<String> messages();

  /**
   * Gets the code owners from the code owner config that apply to the path.
   *
   * <p>Code owners from inherited code owner configs are not considered.
   *
   * @return the code owners of the path
   */
  public ImmutableSet<CodeOwnerReference> getPathCodeOwners() {
    logger.atFine().log("retrieving path code owners for %s from %s", path(), codeOwnerConfigKey());
    ImmutableSet<CodeOwnerReference> pathCodeOwners =
        codeOwnerSets().stream()
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
        "retrieving path code owner annotations for %s from %s", path(), codeOwnerConfigKey());
    ImmutableMultimap.Builder<CodeOwnerReference, CodeOwnerAnnotation> annotationsBuilder =
        ImmutableMultimap.builder();
    codeOwnerSets().forEach(codeOwnerSet -> annotationsBuilder.putAll(codeOwnerSet.annotations()));

    ImmutableMultimap<CodeOwnerReference, CodeOwnerAnnotation> annotations =
        annotationsBuilder.build();
    logger.atFine().log("annotations = %s", annotations);
    return annotations;
  }

  /** Gets the annotations for the given email. */
  public ImmutableSet<String> getAnnotationsFor(String email) {
    return getAnnotations().get(CodeOwnerReference.create(email)).stream()
        .map(CodeOwnerAnnotation::key)
        .collect(toImmutableSet());
  }

  @Override
  public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("path", path())
        .add("codeOwnerConfigKey", codeOwnerConfigKey())
        .add("ignoreParentCodeOwners", ignoreParentCodeOwners())
        .add("codeOwnerSets", codeOwnerSets())
        .add("resolvedImports", resolvedImports())
        .add("unresolvedImports", unresolvedImports())
        .add("messages", messages())
        .toString();
  }

  /** Creates a builder for a {@link PathCodeOwnersResult} instance. */
  public static Builder builder(
      Path path, CodeOwnerConfig.Key codeOwnerConfigKey, boolean ignoreParentCodeOwners) {
    return new AutoValue_PathCodeOwnersResult.Builder()
        .path(path)
        .codeOwnerConfigKey(codeOwnerConfigKey)
        .ignoreParentCodeOwners(ignoreParentCodeOwners);
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder path(Path path);

    abstract Builder codeOwnerConfigKey(CodeOwnerConfig.Key codeOwnerConfigKey);

    abstract Builder ignoreParentCodeOwners(boolean ignoreParentCodeOwners);

    Builder ignoreParentCodeOwners() {
      return ignoreParentCodeOwners(true);
    }

    abstract ImmutableSet.Builder<CodeOwnerSet> codeOwnerSetsBuilder();

    Builder addCodeOwnerSet(CodeOwnerSet codeOwnerSet) {
      requireNonNull(codeOwnerSet, "codeOwnerSet");
      codeOwnerSetsBuilder().add(codeOwnerSet);
      return this;
    }

    Builder addAllCodeOwnerSets(ImmutableSet<CodeOwnerSet> codeOwnerSets) {
      requireNonNull(codeOwnerSets, "codeOwnerSet2");
      codeOwnerSetsBuilder().addAll(codeOwnerSets);
      return this;
    }

    abstract ImmutableList.Builder<CodeOwnerConfigImport> resolvedImportsBuilder();

    Builder addResolvedImport(CodeOwnerConfigImport codeOwnerConfigImport) {
      requireNonNull(codeOwnerConfigImport, "codeOwnerConfigImport");
      resolvedImportsBuilder().add(codeOwnerConfigImport);
      return this;
    }

    Builder addAllResolvedImports(ImmutableList<CodeOwnerConfigImport> codeOwnerConfigImports) {
      requireNonNull(codeOwnerConfigImports, "codeOwnerConfigImports");
      resolvedImportsBuilder().addAll(codeOwnerConfigImports);
      return this;
    }

    abstract ImmutableList.Builder<CodeOwnerConfigImport> unresolvedImportsBuilder();

    Builder addUnresolvedImport(CodeOwnerConfigImport codeOwnerConfigImport) {
      requireNonNull(codeOwnerConfigImport, "codeOwnerConfigImport");
      unresolvedImportsBuilder().add(codeOwnerConfigImport);
      return this;
    }

    Builder addAllUnresolvedImports(ImmutableList<CodeOwnerConfigImport> codeOwnerConfigImports) {
      requireNonNull(codeOwnerConfigImports, "codeOwnerConfigImports");
      unresolvedImportsBuilder().addAll(codeOwnerConfigImports);
      return this;
    }

    abstract ImmutableList.Builder<String> messagesBuilder();

    Builder addMessage(String message) {
      requireNonNull(message, "message");
      messagesBuilder().add(message);
      return this;
    }

    Builder addAllMessages(ImmutableList<String> messages) {
      requireNonNull(messages, "messages");
      messagesBuilder().addAll(messages);
      return this;
    }

    abstract PathCodeOwnersResult build();
  }
}
