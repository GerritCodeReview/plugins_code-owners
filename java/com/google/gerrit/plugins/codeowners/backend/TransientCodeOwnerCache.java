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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Class to cache resolved {@link CodeOwner}s within a request.
 *
 * <p>This cache is transient, which means the code owners stay cached only for the lifetime of the
 * {@code TransientCodeOwnerCache} instance.
 *
 * <p><strong>Note</strong>: This class is not thread-safe.
 */
public class TransientCodeOwnerCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Optional<Integer> maxCacheSize;
  private final Counters counters;
  private final HashMap<String, Optional<CodeOwner>> cache = new HashMap<>();

  @Inject
  TransientCodeOwnerCache(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerMetrics codeOwnerMetrics) {
    this.maxCacheSize =
        codeOwnersPluginConfiguration.getGlobalConfig().getMaxCodeOwnerConfigCacheSize();
    this.counters = new Counters(codeOwnerMetrics);
  }

  public ImmutableMap<String, Optional<CodeOwner>> get(Set<String> emails) {
    ImmutableMap<String, Optional<CodeOwner>> cachedCodeOwnersByEmail =
        cache.entrySet().stream()
            .filter(e -> emails.contains(e.getKey()))
            .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    counters.incrementCacheReads(cachedCodeOwnersByEmail.size());
    return cachedCodeOwnersByEmail;
  }

  public void clear() {
    cache.clear();
  }

  public void cacheNonResolvable(String email) {
    cache(email, Optional.empty());
  }

  public void cache(String email, CodeOwner codeOwner) {
    cache(email, Optional.of(codeOwner));
  }

  private void cache(String email, Optional<CodeOwner> codeOwner) {
    counters.incrementResolutions();
    if (!maxCacheSize.isPresent() || cache.size() < maxCacheSize.get()) {
      cache.put(email, codeOwner);
    } else if (maxCacheSize.isPresent()) {
      logger.atWarning().atMostEvery(1, TimeUnit.DAYS).log(
          "exceeded limit of %s", getClass().getSimpleName());
    }
  }

  public Counters getCounters() {
    return counters;
  }

  public static class Counters {
    private final CodeOwnerMetrics codeOwnerMetrics;

    private int resolutionCount;
    private int cacheReadCount;

    private Counters(CodeOwnerMetrics codeOwnerMetrics) {
      this.codeOwnerMetrics = codeOwnerMetrics;
    }

    private void incrementCacheReads(long value) {
      codeOwnerMetrics.countCodeOwnerCacheReads.incrementBy(value);
      cacheReadCount++;
    }

    private void incrementResolutions() {
      codeOwnerMetrics.countCodeOwnerResolutions.increment();
      resolutionCount++;
    }

    public int getResolutionCount() {
      return resolutionCount;
    }

    public int getCacheReadCount() {
      return cacheReadCount;
    }
  }
}
