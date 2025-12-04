// Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginProjectConfigSnapshot;
import com.google.gerrit.plugins.codeowners.common.ChangedFile;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Computes and caches the {@link ChangedFile}s for the patch sets of a change.
 *
 * <p>The changed files for a patch set are computed lazily. This way we do not compute changed
 * files unnecessarily that are never requested.
 *
 * <p>The changed files are computed without rename detection.
 *
 * <p>This class is not thread-safe.
 */
public class ChangedFilesByPatchSetCache {
  interface Factory {
    ChangedFilesByPatchSetCache create(
        CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig, ChangeNotes changeNotes);
  }

  private final ChangedFiles changedFiles;
  private final CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig;
  private final ChangeNotes changeNotes;

  private Map<PatchSet.Id, ImmutableList<ChangedFile>> cache = new HashMap<>();

  @Inject
  public ChangedFilesByPatchSetCache(
      ChangedFiles changedFiles,
      @Assisted CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig,
      @Assisted ChangeNotes changeNotes) {
    this.changedFiles = changedFiles;
    this.codeOwnersConfig = codeOwnersConfig;
    this.changeNotes = changeNotes;
  }

  public ImmutableList<ChangedFile> get(PatchSet.Id patchSetId) {
    return cache.computeIfAbsent(patchSetId, this::compute);
  }

  private ImmutableList<ChangedFile> compute(PatchSet.Id patchSetId) {
    checkState(
        patchSetId.changeId().equals(changeNotes.getChange().getId()),
        "patch set %s belongs to other change than change %s",
        patchSetId,
        changeNotes.getChange().getId().get());
    PatchSet patchSet = getPatchSet(patchSetId);
    try {
      return changedFiles.getWithoutRenameDetection(
          changeNotes.getProjectName(),
          patchSet.commitId(),
          codeOwnersConfig.getMergeCommitStrategy());
    } catch (IOException | DiffNotAvailableException e) {
      throw new StorageException(
          String.format(
              "failed to retrieve changed files for patch set %d of change %d"
                  + " in project %s (commit=%s)",
              patchSetId.get(),
              patchSetId.changeId().get(),
              changeNotes.getProjectName(),
              patchSet.commitId()),
          e);
    }
  }

  private PatchSet getPatchSet(PatchSet.Id patchSetId) {
    PatchSet patchSet = changeNotes.getPatchSets().get(patchSetId);
    if (patchSet == null) {
      throw new StorageException(
          String.format(
              "patch set %s not found in change %d", patchSetId, changeNotes.getChangeId().get()));
    }
    return patchSet;
  }
}
