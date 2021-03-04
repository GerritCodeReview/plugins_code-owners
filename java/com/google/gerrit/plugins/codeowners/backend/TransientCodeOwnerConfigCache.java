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

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * Class to load and cache {@link CodeOwnerConfig}s within a request.
 *
 * <p>This cache is transient, which means the code owner configs stay cached only for the lifetime
 * of the {@code TransientCodeOwnerConfigCache} instance.
 *
 * <p><strong>Note</strong>: This class is not thread-safe.
 */
public class TransientCodeOwnerConfigCache implements CodeOwnerConfigLoader {
  private final GitRepositoryManager repoManager;
  private final CodeOwners codeOwners;
  private final Counters counters;
  private final HashMap<CacheKey, Optional<CodeOwnerConfig>> cache = new HashMap<>();

  @Inject
  TransientCodeOwnerConfigCache(
      GitRepositoryManager repoManager, CodeOwners codeOwners, CodeOwnerMetrics codeOwnerMetrics) {
    this.repoManager = repoManager;
    this.codeOwners = codeOwners;
    this.counters = new Counters(codeOwnerMetrics);
  }

  /**
   * Gets the specified code owner config from the cache, if it was previously retrieved. Otherwise
   * loads and returns the code owner config.
   */
  @Override
  public Optional<CodeOwnerConfig> get(
      CodeOwnerConfig.Key codeOwnerConfigKey, @Nullable ObjectId revision) {
    CacheKey cacheKey = CacheKey.create(codeOwnerConfigKey, revision);
    Optional<CodeOwnerConfig> cachedCodeOwnerConfig = cache.get(cacheKey);
    if (cachedCodeOwnerConfig != null) {
      counters.incrementCacheReads();
      return cachedCodeOwnerConfig;
    }
    return loadAndCache(cacheKey);
  }

  /**
   * Gets the specified code owner config from the cache, if it was previously retrieved. Otherwise
   * loads and returns the code owner config.
   */
  @Override
  public Optional<CodeOwnerConfig> getFromCurrentRevision(CodeOwnerConfig.Key codeOwnerConfigKey) {
    return get(codeOwnerConfigKey, /* revision= */ null);
  }

  /** Load a code owner config and puts it into the cache. */
  private Optional<CodeOwnerConfig> loadAndCache(CacheKey cacheKey) {
    counters.incrementBackendReads();
    Optional<CodeOwnerConfig> codeOwnerConfig;
    if (cacheKey.revision().isPresent()) {
      codeOwnerConfig = codeOwners.get(cacheKey.codeOwnerConfigKey(), cacheKey.revision().get());
    } else {
      Optional<ObjectId> revision = getRevision(cacheKey.codeOwnerConfigKey().branchNameKey());
      if (revision.isPresent()) {
        codeOwnerConfig = codeOwners.get(cacheKey.codeOwnerConfigKey(), revision.get());
      } else {
        // branch does not exists, hence the code owner config also doesn't exist
        codeOwnerConfig = Optional.empty();
      }
    }
    cache.put(cacheKey, codeOwnerConfig);
    return codeOwnerConfig;
  }

  /**
   * Gets the revision for the given branch.
   *
   * <p>Returns {@link Optional#empty()} if the branch doesn't exist.
   */
  private Optional<ObjectId> getRevision(BranchNameKey branchNameKey) {
    try (Repository repo = repoManager.openRepository(branchNameKey.project())) {
      Ref ref = repo.exactRef(branchNameKey.branch());
      if (ref == null) {
        // branch does not exist
        return Optional.empty();
      }
      return Optional.of(ref.getObjectId());
    } catch (IOException e) {
      throw new CodeOwnersInternalServerErrorException(
          String.format(
              "failed to get revision of branch %s in project %s",
              branchNameKey.shortName(), branchNameKey.project()),
          e);
    }
  }

  @AutoValue
  abstract static class CacheKey {
    /** The key of the code owner config. */
    public abstract CodeOwnerConfig.Key codeOwnerConfigKey();

    /** The revision from which the code owner config was loaded. */
    public abstract Optional<ObjectId> revision();

    public static CacheKey create(
        CodeOwnerConfig.Key codeOwnerConfigKey, @Nullable ObjectId revision) {
      return new AutoValue_TransientCodeOwnerConfigCache_CacheKey(
          codeOwnerConfigKey, Optional.ofNullable(revision));
    }
  }

  public Counters getCounters() {
    return counters;
  }

  public static class Counters {
    private final CodeOwnerMetrics codeOwnerMetrics;

    private int cacheReadCount;
    private int backendReadCount;

    private Counters(CodeOwnerMetrics codeOwnerMetrics) {
      this.codeOwnerMetrics = codeOwnerMetrics;
    }

    private void incrementCacheReads() {
      codeOwnerMetrics.countCodeOwnerConfigCacheReads.increment();
      cacheReadCount++;
    }

    private void incrementBackendReads() {
      // we do not increase the countCodeOwnerConfigReads metric here, since this is already done in
      // CodeOwners
      backendReadCount++;
    }

    public int getBackendReadCount() {
      return backendReadCount;
    }

    public int getCacheReadCount() {
      return cacheReadCount;
    }
  }
}
