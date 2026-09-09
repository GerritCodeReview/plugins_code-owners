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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginGlobalConfigSnapshot;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Key;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link CodeOwnerConfigCache} and its integration with {@link
 * TransientCodeOwnerConfigCache}.
 */
public class CodeOwnerConfigCacheTest extends AbstractCodeOwnersTest {
  private static final ObjectId REVISION_1 =
      ObjectId.fromString("1111111111111111111111111111111111111111");
  private static final ObjectId REVISION_2 =
      ObjectId.fromString("2222222222222222222222222222222222222222");

  private CodeOwnerConfigCache codeOwnerConfigCache;

  @Before
  public void setUp() {
    codeOwnerConfigCache = plugin.getSysInjector().getInstance(CodeOwnerConfigCache.class);
  }

  @Test
  public void isBoundAsSingletonInPluginInjector() {
    CodeOwnerConfigCache secondInstance =
        plugin.getSysInjector().getInstance(CodeOwnerConfigCache.class);
    assertThat(codeOwnerConfigCache).isSameInstanceAs(secondInstance);
  }

  @Test
  public void registeredInDynamicMapOfCaches() {
    DynamicMap<Cache<?, ?>> cacheMap =
        plugin.getSysInjector().getInstance(new Key<DynamicMap<Cache<?, ?>>>() {});
    Cache<?, ?> cache = cacheMap.get("code-owners", CodeOwnerConfigCache.CACHE_NAME);
    assertThat(cache).isNotNull();
  }

  @Test
  public void returnsEmptyIfNotPresent() {
    CodeOwnerConfig.Key key =
        CodeOwnerConfig.Key.create(Project.nameKey("project"), "master", "/foo");
    assertThat(codeOwnerConfigCache.getIfPresent(key, REVISION_1)).isEmpty();
  }

  @Test
  public void cachesConfigByRevision() {
    CodeOwnerConfig.Key key =
        CodeOwnerConfig.Key.create(Project.nameKey("project"), "master", "/foo");
    CodeOwnerConfig config = CodeOwnerConfig.builder(key, REVISION_1).build();

    codeOwnerConfigCache.put(key, REVISION_1, Optional.of(config));

    assertThat(codeOwnerConfigCache.getIfPresent(key, REVISION_1)).hasValue(Optional.of(config));
  }

  @Test
  public void cachesNegativeLookup() {
    CodeOwnerConfig.Key key =
        CodeOwnerConfig.Key.create(Project.nameKey("project"), "master", "/foo");
    codeOwnerConfigCache.put(key, REVISION_1, Optional.empty());

    assertThat(codeOwnerConfigCache.getIfPresent(key, REVISION_1)).hasValue(Optional.empty());
  }

  @Test
  public void revisionMismatchDoesNotReturnStaleConfig() {
    CodeOwnerConfig.Key key =
        CodeOwnerConfig.Key.create(Project.nameKey("project"), "master", "/foo");
    CodeOwnerConfig configRev1 = CodeOwnerConfig.builder(key, REVISION_1).build();

    // Cache entry for revision 1
    codeOwnerConfigCache.put(key, REVISION_1, Optional.of(configRev1));

    // Lookup for revision 2 should miss, preventing stale data after branch update
    assertThat(codeOwnerConfigCache.getIfPresent(key, REVISION_2)).isEmpty();
  }

  @Test
  public void disabledWhenMaxCacheSizeIsZero() {
    Cache<CodeOwnerConfigCache.Key, Optional<CodeOwnerConfig>> cache =
        CacheBuilder.newBuilder().maximumSize(0).build();
    CodeOwnerConfigCache disabledCache = new CodeOwnerConfigCache(cache);
    CodeOwnerConfig.Key key =
        CodeOwnerConfig.Key.create(Project.nameKey("project"), "master", "/foo");
    CodeOwnerConfig config = CodeOwnerConfig.builder(key, REVISION_1).build();

    disabledCache.put(key, REVISION_1, Optional.of(config));
    // When size is 0, maximumSize(0) evicts immediately
    assertThat(disabledCache.getIfPresent(key, REVISION_1)).isEmpty();
  }

  @Test
  public void l2HitPopulatesL1InTransientCache() {
    Project.NameKey project = Project.nameKey("project");
    String branch = "master";
    CodeOwnerConfig.Key key = CodeOwnerConfig.Key.create(project, branch, "/foo");
    CodeOwnerConfig config = CodeOwnerConfig.builder(key, REVISION_1).build();

    // Populate L2 directly
    codeOwnerConfigCache.put(key, REVISION_1, Optional.of(config));

    TransientCodeOwnerConfigCache transientCache =
        new TransientCodeOwnerConfigCache(
            plugin.getSysInjector().getInstance(CodeOwnersPluginConfiguration.class),
            plugin.getSysInjector().getInstance(GitRepositoryManager.class),
            plugin.getSysInjector().getInstance(CodeOwners.class),
            codeOwnerConfigCache,
            plugin.getSysInjector().getInstance(CodeOwnerMetrics.class));

    // First call: L1 miss, L2 hit -> returns config and populates L1
    Optional<CodeOwnerConfig> result1 = transientCache.get(key, REVISION_1);
    assertThat(result1).hasValue(config);
    assertThat(transientCache.getCounters().getCacheReadCount()).isEqualTo(1);
    assertThat(transientCache.getCounters().getBackendReadCount()).isEqualTo(0);

    // Second call: L1 hit directly from local HashMap
    Optional<CodeOwnerConfig> result2 = transientCache.get(key, REVISION_1);
    assertThat(result2).hasValue(config);
    assertThat(transientCache.getCounters().getCacheReadCount()).isEqualTo(2);
    assertThat(transientCache.getCounters().getBackendReadCount()).isEqualTo(0);
  }

  @Test
  public void l1MaxCacheSizeRespectedOnL2Hit() {
    Project.NameKey project = Project.nameKey("project");
    String branch = "master";
    CodeOwnerConfig.Key key1 = CodeOwnerConfig.Key.create(project, branch, "/foo");
    CodeOwnerConfig.Key key2 = CodeOwnerConfig.Key.create(project, branch, "/bar");
    CodeOwnerConfig config1 = CodeOwnerConfig.builder(key1, REVISION_1).build();
    CodeOwnerConfig config2 = CodeOwnerConfig.builder(key2, REVISION_1).build();

    codeOwnerConfigCache.put(key1, REVISION_1, Optional.of(config1));
    codeOwnerConfigCache.put(key2, REVISION_1, Optional.of(config2));

    CodeOwnersPluginConfiguration mockConfig = mock(CodeOwnersPluginConfiguration.class);
    CodeOwnersPluginGlobalConfigSnapshot mockSnapshot =
        mock(CodeOwnersPluginGlobalConfigSnapshot.class);
    when(mockConfig.getGlobalConfig()).thenReturn(mockSnapshot);
    // Limit L1 to 1 entry
    when(mockSnapshot.getMaxCodeOwnerConfigCacheSize()).thenReturn(Optional.of(1));

    TransientCodeOwnerConfigCache transientCache =
        new TransientCodeOwnerConfigCache(
            mockConfig,
            plugin.getSysInjector().getInstance(GitRepositoryManager.class),
            plugin.getSysInjector().getInstance(CodeOwners.class),
            codeOwnerConfigCache,
            plugin.getSysInjector().getInstance(CodeOwnerMetrics.class));

    // First L2 hit populates L1 (size reaches 1)
    Optional<CodeOwnerConfig> result1 = transientCache.get(key1, REVISION_1);
    assertThat(result1).hasValue(config1);

    // Second L2 hit: L1 size limit is reached, so key2 must not expand L1 indefinitely
    Optional<CodeOwnerConfig> result2 = transientCache.get(key2, REVISION_1);
    assertThat(result2).hasValue(config2);
  }

  @Test
  public void l2MissLoadsFromBackendAndPopulatesBothL1AndL2() {
    Project.NameKey project = Project.nameKey("project");
    String branch = "master";
    CodeOwnerConfig.Key key = CodeOwnerConfig.Key.create(project, branch, "/foo");
    CodeOwnerConfig config = CodeOwnerConfig.builder(key, REVISION_1).build();

    CodeOwners mockCodeOwners = mock(CodeOwners.class);
    when(mockCodeOwners.get(key, REVISION_1)).thenReturn(Optional.of(config));

    TransientCodeOwnerConfigCache transientCache =
        new TransientCodeOwnerConfigCache(
            plugin.getSysInjector().getInstance(CodeOwnersPluginConfiguration.class),
            plugin.getSysInjector().getInstance(GitRepositoryManager.class),
            mockCodeOwners,
            codeOwnerConfigCache,
            plugin.getSysInjector().getInstance(CodeOwnerMetrics.class));

    // Initially L2 is empty
    assertThat(codeOwnerConfigCache.getIfPresent(key, REVISION_1)).isEmpty();

    // Call get: L1 miss, L2 miss -> loads from CodeOwners backend, populates L1 and L2
    Optional<CodeOwnerConfig> result = transientCache.get(key, REVISION_1);
    assertThat(result).hasValue(config);
    assertThat(transientCache.getCounters().getCacheReadCount()).isEqualTo(0);
    assertThat(transientCache.getCounters().getBackendReadCount()).isEqualTo(1);

    // Verify L2 is now populated
    assertThat(codeOwnerConfigCache.getIfPresent(key, REVISION_1)).hasValue(Optional.of(config));

    // Verify subsequent call to transientCache hits L1
    Optional<CodeOwnerConfig> result2 = transientCache.get(key, REVISION_1);
    assertThat(result2).hasValue(config);
    assertThat(transientCache.getCounters().getCacheReadCount()).isEqualTo(1);
    assertThat(transientCache.getCounters().getBackendReadCount()).isEqualTo(1);
  }

  @Test
  public void nonExistentBranchCachedAsEmptyInL1() {
    CodeOwnerConfig.Key key = CodeOwnerConfig.Key.create(project, "non-existent-branch", "/foo");

    TransientCodeOwnerConfigCache transientCache =
        new TransientCodeOwnerConfigCache(
            plugin.getSysInjector().getInstance(CodeOwnersPluginConfiguration.class),
            plugin.getSysInjector().getInstance(GitRepositoryManager.class),
            plugin.getSysInjector().getInstance(CodeOwners.class),
            codeOwnerConfigCache,
            plugin.getSysInjector().getInstance(CodeOwnerMetrics.class));

    // When revision is null, resolving against a non-existent branch returns Optional.empty()
    Optional<CodeOwnerConfig> result1 = transientCache.get(key, /* revision= */ null);
    assertThat(result1).isEmpty();

    // Subsequent read returns cached Optional.empty() from L1 without re-querying disk
    Optional<CodeOwnerConfig> result2 = transientCache.get(key, /* revision= */ null);
    assertThat(result2).isEmpty();
    assertThat(transientCache.getCounters().getCacheReadCount()).isEqualTo(1);
    assertThat(transientCache.getCounters().getBackendReadCount()).isEqualTo(0);
  }
}
