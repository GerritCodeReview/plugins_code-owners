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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Representation of a file that was changed in the revision for which the user owns the new path,
 * the old path or both paths.
 */
@AutoValue
public abstract class OwnedChangedFile {
  /**
   * Owner information for the old path.
   *
   * <p>{@link Optional#empty()} for deletions.
   */
  public abstract Optional<OwnedPath> newPath();

  /**
   * Owner information for the old path.
   *
   * <p>Present only for deletions and renames.
   */
  public abstract Optional<OwnedPath> oldPath();

  public static OwnedChangedFile create(@Nullable OwnedPath newPath, @Nullable OwnedPath oldPath) {
    return new AutoValue_OwnedChangedFile(
        Optional.ofNullable(newPath), Optional.ofNullable(oldPath));
  }

  /**
   * Returns the owned paths that are contained in the given {@link OwnedChangedFile}s as new or old
   * path, as a sorted list.
   *
   * <p>New or old paths that are not owned by the user are filtered out.
   */
  public static ImmutableList<Path> getOwnedPaths(
      ImmutableList<OwnedChangedFile> ownedChangedFiles) {
    return asPathStream(ownedChangedFiles.stream()).collect(toImmutableList());
  }

  /**
   * Returns the owned paths that are contained in the given {@link OwnedChangedFile}s as new or old
   * path, as a sorted stream.
   *
   * <p>New or old paths that are not owned by the user are filtered out.
   */
  public static Stream<Path> asPathStream(Stream<OwnedChangedFile> ownedChangedFiles) {
    return ownedChangedFiles
        .flatMap(
            ownedChangedFile -> Stream.of(ownedChangedFile.newPath(), ownedChangedFile.oldPath()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(OwnedPath::owned)
        .map(ownedPath -> ownedPath.path())
        .sorted(Comparator.comparing(path -> path.toString()));
  }
}
