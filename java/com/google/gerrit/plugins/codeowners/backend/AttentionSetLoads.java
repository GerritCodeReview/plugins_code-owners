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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangePredicates;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.AttentionSetUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.Config;

/**
 * Computes the review load of accounts from the attention set.
 *
 * <p>The load of an account is the number of open changes in which the account is in the attention
 * set, capped at {@link #MAX_COUNTED_CHANGES}. Attention set entries that are older than {@code
 * suggest.attentionSetStaleAgeDays} (default 7) count an additional {@code
 * suggest.attentionSetStaleFactor} (default 1.0) on top, since unattended entries indicate that the
 * account is not actively reviewing at the moment.
 *
 * <p>Since the code owner suggestion sends one request per file, the loads are cached for a short
 * time so that the index queries are not repeated for every file.
 */
@Singleton
public class AttentionSetLoads {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Maximum number of open changes with an attention set entry that are counted per account. */
  public static final int MAX_COUNTED_CHANGES = 25;

  private final Provider<InternalChangeQuery> queryProvider;
  private final Config cfg;
  private final Cache<Account.Id, Integer> cache;

  @Inject
  AttentionSetLoads(Provider<InternalChangeQuery> queryProvider, @GerritServerConfig Config cfg) {
    this.queryProvider = queryProvider;
    this.cfg = cfg;
    this.cache =
        CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(Duration.ofMinutes(1))
            .build();
  }

  /**
   * Returns the load of the given account, reading it from the cache if it was computed recently.
   */
  public int get(Account.Id accountId) {
    try {
      return cache.get(accountId, () -> compute(accountId));
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot compute attention set load for %s", accountId);
      return 0;
    }
  }

  private int compute(Account.Id accountId) {
    int staleAgeDays = cfg.getInt("suggest", "attentionSetStaleAgeDays", 7);
    double staleFactor = getStaleFactor();
    Instant staleCut =
        staleAgeDays > 0 && staleFactor > 0
            ? TimeUtil.now().minus(Duration.ofDays(staleAgeDays))
            : null;

    double load = 0;
    for (ChangeData cd :
        queryProvider
            .get()
            .setLimit(MAX_COUNTED_CHANGES)
            .setRequestedFields(
                ChangeField.NUMERIC_ID_STR_SPEC, ChangeField.ATTENTION_SET_FULL_SPEC)
            .query(
                Predicate.and(
                    ChangeStatusPredicate.open(), ChangePredicates.attentionSet(accountId)))) {
      load += 1;
      if (staleCut != null && isStaleAttentionSetEntry(cd, accountId, staleCut)) {
        load += staleFactor;
      }
    }
    return (int) Math.min(Math.round(load), MAX_COUNTED_CHANGES);
  }

  private double getStaleFactor() {
    String value = cfg.getString("suggest", null, "attentionSetStaleFactor");
    if (value == null || value.isEmpty()) {
      return 1.0;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      logger.atSevere().withCause(e).log(
          "Invalid value for suggest.attentionSetStaleFactor: %s", value);
      return 1.0;
    }
  }

  private static boolean isStaleAttentionSetEntry(
      ChangeData cd, Account.Id accountId, Instant staleCut) {
    return AttentionSetUtil.additionsOnly(cd.attentionSet()).stream()
        .filter(update -> update.account().equals(accountId))
        .anyMatch(update -> update.timestamp().isBefore(staleCut));
  }
}
