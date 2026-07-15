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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.query.change.AgePredicate;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangePredicates;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.lib.Config;

/**
 * Computes how recently accounts worked on a path.
 *
 * <p>The activity of an account on a path is computed from the open and merged changes in the
 * project that touched the path and that were updated within {@code suggest.touchedFilesMaxAgeDays}
 * (default 90). For each matching change the owner and the reviewers of the change get activity
 * points, where newer changes are worth more points than older changes.
 */
@Singleton
public class RecentFileActivities {
  /** Maximum number of changes that are taken into account. */
  public static final int MAX_COUNTED_CHANGES = 50;

  private final Provider<InternalChangeQuery> queryProvider;
  private final Config cfg;

  @Inject
  RecentFileActivities(
      Provider<InternalChangeQuery> queryProvider, @GerritServerConfig Config cfg) {
    this.queryProvider = queryProvider;
    this.cfg = cfg;
  }

  /**
   * Returns the activity points of the given accounts for the given path.
   *
   * @param project the project that contains the path
   * @param changeToIgnore the change for which code owners are suggested, activity on this change
   *     is not counted
   * @param path the path as JGit path (without leading '/')
   * @param accounts the accounts for which the activity should be computed
   */
  public ImmutableMap<Account.Id, Integer> compute(
      Project.NameKey project,
      Change.Id changeToIgnore,
      String path,
      ImmutableSet<Account.Id> accounts) {
    int maxAgeDays = cfg.getInt("suggest", "touchedFilesMaxAgeDays", 90);
    if (maxAgeDays <= 0 || accounts.isEmpty()) {
      return ImmutableMap.of();
    }

    Map<Account.Id, Integer> activities = new HashMap<>();
    Instant now = TimeUtil.now();
    for (ChangeData cd :
        queryProvider
            .get()
            .setLimit(MAX_COUNTED_CHANGES)
            .setRequestedFields(ChangeField.CHANGE_SPEC, ChangeField.REVIEWER_SPEC)
            .query(
                Predicate.and(
                    ChangePredicates.project(project),
                    ChangePredicates.path(path),
                    Predicate.or(
                        ChangeStatusPredicate.open(),
                        ChangeStatusPredicate.forStatus(Change.Status.MERGED)),
                    Predicate.not(new AgePredicate(maxAgeDays + "d"))))) {
      if (cd.getId().equals(changeToIgnore)) {
        continue;
      }
      int points = recencyPoints(cd.change().getLastUpdatedOn(), now, maxAgeDays);
      addPoints(activities, accounts, cd.change().getOwner(), points);
      for (Account.Id reviewerId : cd.reviewers().all()) {
        addPoints(activities, accounts, reviewerId, points);
      }
    }
    return ImmutableMap.copyOf(activities);
  }

  private static void addPoints(
      Map<Account.Id, Integer> activities,
      ImmutableSet<Account.Id> accounts,
      Account.Id accountId,
      int points) {
    if (accounts.contains(accountId)) {
      activities.merge(accountId, points, Integer::sum);
    }
  }

  /**
   * Returns the points that a change is worth, linearly decreasing from {@code maxAgeDays} for
   * changes that were updated now to 1 for changes at the maximum age.
   */
  private static int recencyPoints(Instant lastUpdated, Instant now, int maxAgeDays) {
    long ageDays = Math.max(0, Duration.between(lastUpdated, now).toDays());
    return (int) Math.max(1, maxAgeDays - ageDays);
  }
}
