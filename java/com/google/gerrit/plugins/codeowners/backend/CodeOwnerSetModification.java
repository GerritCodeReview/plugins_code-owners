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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/**
 * Representation of a code owner set modification as defined by {@link #apply(ImmutableList)}.
 *
 * <p>Used by {@link CodeOwnerConfigUpdate} to describe how the {@link CodeOwnerSet}s in a {@link
 * CodeOwnerConfig} should be changed on updated or be populated on creation.
 *
 * <p>This class provides a couple of static helper methods to modify {@link CodeOwnerSet}s that
 * make changes to code owners sets easier for callers.
 */
@FunctionalInterface
public interface CodeOwnerSetModification {
  /**
   * Create a {@link CodeOwnerSetModification} instance that keeps the {@link CodeOwnerSet}s as they
   * are.
   *
   * @return the created {@link CodeOwnerSetModification} instance
   */
  public static CodeOwnerSetModification keep() {
    return originalCodeOwnerSets -> originalCodeOwnerSets;
  }

  /**
   * Create a {@link CodeOwnerSetModification} instance that clears the {@link CodeOwnerSet}s.
   *
   * <p>All {@link CodeOwnerSet}s are removed.
   *
   * @return the created {@link CodeOwnerSetModification} instance
   */
  public static CodeOwnerSetModification clear() {
    return originalCodeOwnerSets -> ImmutableList.of();
  }

  /**
   * Create a {@link CodeOwnerSetModification} instance that appends the given {@link CodeOwnerSet}.
   *
   * @param newCodeOwnerSet the code owner set that should be appended
   * @return the created {@link CodeOwnerSetModification} instance
   */
  public static CodeOwnerSetModification append(CodeOwnerSet newCodeOwnerSet) {
    return originalCodeOwnerSets ->
        new ImmutableList.Builder<CodeOwnerSet>()
            .addAll(originalCodeOwnerSets)
            .add(newCodeOwnerSet)
            .build();
  }

  /**
   * Create a {@link CodeOwnerSetModification} instance that sets the given {@link CodeOwnerSet}.
   *
   * <p>This overrides all code owner sets which have been set before.
   *
   * @param newCodeOwnerSet the code owner set that should be set
   * @return the created {@link CodeOwnerSetModification} instance
   */
  public static CodeOwnerSetModification set(CodeOwnerSet newCodeOwnerSet) {
    return set(ImmutableList.of(newCodeOwnerSet));
  }

  /**
   * Create a {@link CodeOwnerSetModification} instance that sets the given {@link CodeOwnerSet}s.
   *
   * <p>This overrides all code owner sets which have been set before.
   *
   * @param newCodeOwnerSets the code owner sets that should be set
   * @return the created {@link CodeOwnerSetModification} instance
   */
  public static CodeOwnerSetModification set(ImmutableList<CodeOwnerSet> newCodeOwnerSets) {
    return originalCodeOwnerSets -> newCodeOwnerSets;
  }

  /**
   * Create a {@link CodeOwnerSetModification} instance that adds the given email to the only {@link
   * CodeOwnerSet}.
   *
   * <p>Fails if there the original list of code owners sets is empty or if it has more than 1
   * entry.
   *
   * @param email the email that should be added to the only code owner set
   * @return the created {@link CodeOwnerSetModification} instance
   */
  public static CodeOwnerSetModification addToOnlySet(String email) {
    return addToOnlySet(CodeOwnerReference.create(email));
  }

  /**
   * Create a {@link CodeOwnerSetModification} instance that adds the given {@link
   * CodeOwnerReference} to the only {@link CodeOwnerSet}.
   *
   * <p>Fails if there the original list of code owners sets is empty or if it has more than 1
   * entry.
   *
   * @param codeOwnerReference the code owner reference that should be added to the only code owner
   *     set
   * @return the created {@link CodeOwnerSetModification} instance
   */
  public static CodeOwnerSetModification addToOnlySet(CodeOwnerReference codeOwnerReference) {
    return originalCodeOwnerSets ->
        ImmutableList.of(
            addToCodeOwnerSet(Iterables.getOnlyElement(originalCodeOwnerSets), codeOwnerReference));
  }

  /**
   * Adds the given {@link CodeOwnerReference} to the given {@link CodeOwnerSet}.
   *
   * @param codeOwnerSet the code owner set to which the given code owner reference should be added
   * @param codeOwnerReference the code owner reference that should be added to the given code owner
   *     set
   * @return the updated code owner set
   */
  public static CodeOwnerSet addToCodeOwnerSet(
      CodeOwnerSet codeOwnerSet, CodeOwnerReference codeOwnerReference) {
    if (codeOwnerSet.codeOwners().contains(codeOwnerReference)) {
      return codeOwnerSet;
    }

    return codeOwnerSet
        .toBuilder()
        .setCodeOwners(
            ImmutableSet.copyOf(
                Sets.union(codeOwnerSet.codeOwners(), ImmutableSet.of(codeOwnerReference))))
        .build();
  }

  /**
   * Create a {@link CodeOwnerSetModification} instance that removes the given email from the only
   * {@link CodeOwnerSet}s.
   *
   * <p>Fails if there the original list of code owners sets is empty or if it has more than 1
   * entry.
   *
   * @param email the email that should be removed from all owner sets
   * @return the created {@link CodeOwnerSetModification} instance
   */
  public static CodeOwnerSetModification removeFromOnlySet(String email) {
    return removeFromOnlySet(CodeOwnerReference.create(email));
  }

  /**
   * Create a {@link CodeOwnerSetModification} instance that removes the given {@link
   * CodeOwnerReference} from the only {@link CodeOwnerSet}s.
   *
   * <p>Fails if there the original list of code owners sets is empty or if it has more than 1
   * entry.
   *
   * @param codeOwnerReference the code owner reference that should be removed from all owner sets
   * @return the created {@link CodeOwnerSetModification} instance
   */
  public static CodeOwnerSetModification removeFromOnlySet(CodeOwnerReference codeOwnerReference) {
    return originalCodeOwnerSets ->
        ImmutableList.of(
            removeFromCodeOwnerSet(
                Iterables.getOnlyElement(originalCodeOwnerSets), codeOwnerReference));
  }

  /**
   * Removes the given {@link CodeOwnerReference} from the given {@link CodeOwnerSet}.
   *
   * @param codeOwnerSet the code owner set from which the given code owner reference should be
   *     removed
   * @param codeOwnerReference the code owner reference that should be removed from the given code
   *     owner set
   * @return the updated code owner set
   */
  public static CodeOwnerSet removeFromCodeOwnerSet(
      CodeOwnerSet codeOwnerSet, CodeOwnerReference codeOwnerReference) {
    if (!codeOwnerSet.codeOwners().contains(codeOwnerReference)) {
      return codeOwnerSet;
    }

    return codeOwnerSet
        .toBuilder()
        .setCodeOwners(
            ImmutableSet.copyOf(
                Sets.difference(codeOwnerSet.codeOwners(), ImmutableSet.of(codeOwnerReference))))
        .build();
  }

  /**
   * Applies the modification to the given code owner sets.
   *
   * @param originalCodeOwnerSets the current code owner sets of the code owner config that is being
   *     updated. If used for a code owner config creation, this set is empty.
   * @return the desired resulting code owner sets (not the diff of the code owner sets!)
   */
  ImmutableList<CodeOwnerSet> apply(ImmutableList<CodeOwnerSet> originalCodeOwnerSets);
}
