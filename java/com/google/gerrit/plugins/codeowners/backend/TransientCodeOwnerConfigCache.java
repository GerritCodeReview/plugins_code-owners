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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * Class to load and cache {@link CodeOwnerConfig}s within a request.
 *
 * <p>This cache maintains a transient request-scoped cache and consults the process-wide {@link
 * CodeOwnerConfigCache} when resolving code owner configs.
 *
 * <p><strong>Note</strong>: This class is not thread-safe.
 */
public class TransientCodeOwnerConfigCache implements CodeOwnerConfigLoader {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepositoryManager repoManager;
  private final CodeOwners codeOwners;
  private final CodeOwnerConfigCache persistentCache;
  private final Optional<Integer> maxCacheSize;
  private final Counters counters;
  private final HashMap<CacheKey, Optional<CodeOwnerConfig>> cache = new HashMap<>();

  @Inject
  TransientCodeOwnerConfigCache(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      GitRepositoryManager repoManager,
      CodeOwners codeOwners,
      CodeOwnerConfigCache persistentCache,
      CodeOwnerMetrics codeOwnerMetrics) {
    this.repoManager = repoManager;
    this.codeOwners = codeOwners;
    this.persistentCache = persistentCache;
    this.maxCacheSize =
        codeOwnersPluginConfiguration.getGlobalConfig().getMaxCodeOwnerConfigCacheSize();
    this.counters = new Counters(codeOwnerMetrics);
  }

  /**
   * Gets the specified code owner config from the cache, if it was previously retrieved. Otherwise
   * loads and returns the code owner config.
   */
  @Override
  public Optional<CodeOwnerConfig> get(
      CodeOwnerConfig.Key codeOwnerConfigKey, @Nullable ObjectId revision) {
    // 1. Check L1 (request-local HashMap) first.
    CacheKey cacheKey = CacheKey.create(codeOwnerConfigKey, revision);
    Optional<CodeOwnerConfig> cachedCodeOwnerConfig = cache.get(cacheKey);
    if (cachedCodeOwnerConfig != null) {
      counters.incrementCacheReads();
      return cachedCodeOwnerConfig;
    }

    // 2. L1 miss: resolve branch revision once if null before querying L2.
    ObjectId targetRevision = revision;
    if (targetRevision == null) {
      Optional<ObjectId> branchRevision = getRevision(codeOwnerConfigKey.branchNameKey());
      if (branchRevision.isEmpty()) {
        // branch does not exist, cache negative lookup in L1 and return
        putInL1(cacheKey, Optional.empty());
        return Optional.empty();
      }
      targetRevision = branchRevision.get();
    }

    // 3. Check L2 (process-wide cache).
    Optional<Optional<CodeOwnerConfig>> persistentConfig =
        persistentCache.getIfPresent(codeOwnerConfigKey, targetRevision);
    if (persistentConfig.isPresent()) {
      counters.incrementCacheReads();
      // Populate L1 cache so subsequent reads in this request are O(1) without touching L2
      putInL1(cacheKey, persistentConfig.get());
      return persistentConfig.get();
    }

    // 4. L2 miss: load using resolved revision to avoid duplicate ref lookup.
    counters.incrementBackendReads();
    Optional<CodeOwnerConfig> codeOwnerConfig = codeOwners.get(codeOwnerConfigKey, targetRevision);
    putInL1(cacheKey, codeOwnerConfig);

    persistentCache.put(codeOwnerConfigKey, targetRevision, codeOwnerConfig);
    return codeOwnerConfig;
  }

  private void putInL1(CacheKey cacheKey, Optional<CodeOwnerConfig> codeOwnerConfig) {
    if (maxCacheSize.isEmpty() || cache.size() < maxCacheSize.get()) {
      cache.put(cacheKey, codeOwnerConfig);
    } else {
      logger.atWarning().atMostEvery(1, TimeUnit.DAYS).log(
          "exceeded limit of %s (project = %s, limit = %s)",
          getClass().getSimpleName(), cacheKey.codeOwnerConfigKey().project(), maxCacheSize.get());
    }
  }

  /**
   * Gets the specified code owner config from the cache, if it was previously retrieved. Otherwise
   * loads and returns the code owner config.
   */
  @Override
  public Optional<CodeOwnerConfig> getFromCurrentRevision(CodeOwnerConfig.Key codeOwnerConfigKey) {
    return get(codeOwnerConfigKey, /* revision= */ null);
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
      throw new StorageException(
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

  /** Performance counters for cache reads and backend reads. */
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
