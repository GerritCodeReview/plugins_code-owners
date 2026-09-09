// Copyright (C) 2026 The Android Open Source Project
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
import com.google.common.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Process-wide, thread-safe in-memory cache for {@link CodeOwnerConfig}s across requests.
 *
 * <p>Entries are strictly keyed by (codeOwnerConfigKey, revision ObjectId). Since Git commit
 * ObjectIds are immutable, entries cached here will never become stale even when branches are
 * updated. Eviction is governed purely by the configured maximum cache size (LRU).
 */
@Singleton
public class CodeOwnerConfigCache {
  public static final String CACHE_NAME = "code_owner_configs";
  public static final int DEFAULT_MAX_CACHE_SIZE = 10_000;

  private final Cache<Key, Optional<CodeOwnerConfig>> cache;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        // CacheBinding only exposes maximumWeight; with no custom weigher, Gerrit's
        // DefaultMemoryCacheFactory uses singletonWeigher(), so weight corresponds to entry count.
        cache(CACHE_NAME, Key.class, new TypeLiteral<Optional<CodeOwnerConfig>>() {})
            .maximumWeight(DEFAULT_MAX_CACHE_SIZE);
        bind(CodeOwnerConfigCache.class);
      }
    };
  }

  @AutoValue
  public abstract static class Key {
    static Key create(CodeOwnerConfig.Key configKey, ObjectId revision) {
      return new AutoValue_CodeOwnerConfigCache_Key(configKey, revision.toObjectId());
    }

    abstract CodeOwnerConfig.Key configKey();

    abstract ObjectId revision();
  }

  @Inject
  public CodeOwnerConfigCache(@Named(CACHE_NAME) Cache<Key, Optional<CodeOwnerConfig>> cache) {
    this.cache = cache;
  }

  public Optional<Optional<CodeOwnerConfig>> getIfPresent(
      CodeOwnerConfig.Key key, ObjectId revision) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(revision, "revision");
    return Optional.ofNullable(cache.getIfPresent(Key.create(key, revision)));
  }

  public void put(CodeOwnerConfig.Key key, ObjectId revision, Optional<CodeOwnerConfig> config) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(revision, "revision");
    Objects.requireNonNull(config, "config");
    cache.put(Key.create(key, revision), config);
  }

  public long size() {
    return cache.size();
  }
}
