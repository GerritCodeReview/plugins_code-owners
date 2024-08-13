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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
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

  /** Gets whether global code owners (aka folder code owners) should be ignored for the path. */
  abstract boolean ignoreGlobalCodeOwners();

  /** Gets code owner sets that contain global code owners (aka folder code owners). */
  abstract ImmutableSet<CodeOwnerSet> globalCodeOwnerSets();

  /** Gets code owner sets that contain per-file code owners that are matching the path. */
  abstract ImmutableSet<CodeOwnerSet> perFileCodeOwnerSets();

  /** Gets a list of resolved imports. */
  public abstract ImmutableList<CodeOwnerConfigImport> resolvedImports();

  /** Gets a list of unresolved imports. */
  public abstract ImmutableList<CodeOwnerConfigImport> unresolvedImports();

  /** Whether there are unresolved imports. */
  public boolean hasUnresolvedImports() {
    return !unresolvedImports().isEmpty();
  }

  public abstract ImmutableList<DebugMessage> messages();

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
        relevantCodeOwnerSets().stream()
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
    relevantCodeOwnerSets()
        .forEach(codeOwnerSet -> annotationsBuilder.putAll(codeOwnerSet.annotations()));

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

  private ImmutableSet<CodeOwnerSet> relevantCodeOwnerSets() {
    if (ignoreGlobalCodeOwners()) {
      return perFileCodeOwnerSets();
    }

    return ImmutableSet.<CodeOwnerSet>builder()
        .addAll(globalCodeOwnerSets())
        .addAll(perFileCodeOwnerSets())
        .build();
  }

  @Override
  public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("path", path())
        .add("codeOwnerConfigKey", codeOwnerConfigKey())
        .add("ignoreParentCodeOwners", ignoreParentCodeOwners())
        .add("ignoreGlobalCodeOwners", ignoreGlobalCodeOwners())
        .add("globalCodeOwnerSets", globalCodeOwnerSets())
        .add("perFileCodeOwnerSets", perFileCodeOwnerSets())
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
        .ignoreParentCodeOwners(ignoreParentCodeOwners)
        .ignoreGlobalCodeOwners(false);
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder path(Path path);

    abstract Builder codeOwnerConfigKey(CodeOwnerConfig.Key codeOwnerConfigKey);

    abstract Builder ignoreParentCodeOwners(boolean ignoreParentCodeOwners);

    abstract Builder ignoreGlobalCodeOwners(boolean ignoreGlobalCodeOwners);

    abstract boolean ignoreGlobalCodeOwners();

    abstract ImmutableSet.Builder<CodeOwnerSet> globalCodeOwnerSetsBuilder();

    @CanIgnoreReturnValue
    Builder addGlobalCodeOwnerSet(CodeOwnerSet globalCodeOwnerSet) {
      requireNonNull(globalCodeOwnerSet, "globalCodeOwnerSet");
      globalCodeOwnerSetsBuilder().add(globalCodeOwnerSet);
      return this;
    }

    @CanIgnoreReturnValue
    Builder addAllGlobalCodeOwnerSets(ImmutableSet<CodeOwnerSet> globalCodeOwnerSets) {
      requireNonNull(globalCodeOwnerSets, "globalCodeOwnerSets");
      globalCodeOwnerSetsBuilder().addAll(globalCodeOwnerSets);
      return this;
    }

    abstract ImmutableSet.Builder<CodeOwnerSet> perFileCodeOwnerSetsBuilder();

    @CanIgnoreReturnValue
    Builder addPerFileCodeOwnerSet(CodeOwnerSet perFileCodeOwnerSet) {
      requireNonNull(perFileCodeOwnerSet, "perFileCodeOwnerSet");
      perFileCodeOwnerSetsBuilder().add(perFileCodeOwnerSet);

      if (perFileCodeOwnerSet.ignoreGlobalAndParentCodeOwners()) {
        ignoreParentCodeOwners(true);

        if (!ignoreGlobalCodeOwners()) {
          ignoreGlobalCodeOwners(true);

          addMessage(
              DebugMessage.createMessage(
                  String.format(
                      "found matching per-file code owner set (with path expressions = %s) that ignores"
                          + " parent code owners, hence ignoring the folder code owners",
                      perFileCodeOwnerSet.pathExpressions())));
        }
      }

      return this;
    }

    @CanIgnoreReturnValue
    Builder addAllPerFileCodeOwnerSets(ImmutableSet<CodeOwnerSet> perFileCodeOwnerSets) {
      requireNonNull(perFileCodeOwnerSets, "perFileCodeOwnerSets");
      perFileCodeOwnerSets.forEach(this::addPerFileCodeOwnerSet);
      return this;
    }

    abstract ImmutableSet<CodeOwnerSet> perFileCodeOwnerSets();

    abstract ImmutableList.Builder<CodeOwnerConfigImport> resolvedImportsBuilder();

    @CanIgnoreReturnValue
    Builder addResolvedImport(CodeOwnerConfigImport codeOwnerConfigImport) {
      requireNonNull(codeOwnerConfigImport, "codeOwnerConfigImport");
      resolvedImportsBuilder().add(codeOwnerConfigImport);
      return this;
    }

    @CanIgnoreReturnValue
    Builder addAllResolvedImports(ImmutableList<CodeOwnerConfigImport> codeOwnerConfigImports) {
      requireNonNull(codeOwnerConfigImports, "codeOwnerConfigImports");
      resolvedImportsBuilder().addAll(codeOwnerConfigImports);
      return this;
    }

    abstract ImmutableList.Builder<CodeOwnerConfigImport> unresolvedImportsBuilder();

    @CanIgnoreReturnValue
    Builder addUnresolvedImport(CodeOwnerConfigImport codeOwnerConfigImport) {
      requireNonNull(codeOwnerConfigImport, "codeOwnerConfigImport");
      unresolvedImportsBuilder().add(codeOwnerConfigImport);
      return this;
    }

    @CanIgnoreReturnValue
    Builder addAllUnresolvedImports(ImmutableList<CodeOwnerConfigImport> codeOwnerConfigImports) {
      requireNonNull(codeOwnerConfigImports, "codeOwnerConfigImports");
      unresolvedImportsBuilder().addAll(codeOwnerConfigImports);
      return this;
    }

    abstract ImmutableList.Builder<DebugMessage> messagesBuilder();

    @CanIgnoreReturnValue
    Builder addMessage(DebugMessage message) {
      requireNonNull(message, "message");
      messagesBuilder().add(message);
      return this;
    }

    @CanIgnoreReturnValue
    Builder addAllMessages(ImmutableList<DebugMessage> messages) {
      requireNonNull(messages, "messages");
      messagesBuilder().addAll(messages);
      return this;
    }

    abstract PathCodeOwnersResult build();
  }
}
