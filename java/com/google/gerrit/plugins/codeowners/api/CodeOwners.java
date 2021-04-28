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

package com.google.gerrit.plugins.codeowners.api;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.client.ListAccountsOption;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Java API for code owners in a branch or change revision.
 *
 * <p>To create an instance for a branch or change revision use {@code CodeOwnersFactory}.
 */
public interface CodeOwners {
  /** Query code owners for a path. */
  QueryRequest query();

  /**
   * Request to query code owners for a path.
   *
   * <p>Allows to set parameters on the request before executing it by calling {@link #get(Path)} or
   * {@link #get(String)}.
   */
  abstract class QueryRequest {
    private Set<ListAccountsOption> options = EnumSet.noneOf(ListAccountsOption.class);
    private Integer limit;
    private String revision;
    private Long seed;
    private Boolean resolveAllUsers;
    private Boolean highestScoreOnly;
    private Boolean debug;

    /**
     * Lists the code owners for the given path.
     *
     * @param path the path for which the code owners should be returned, the path may or may not
     *     exist
     * @return the code owners for the given path
     */
    public abstract CodeOwnersInfo get(Path path) throws RestApiException;

    /**
     * Lists the code owners for the given path.
     *
     * @param path the path for which the code owners should be returned, the path may or may not
     *     exist
     * @return the code owners for the given path
     */
    public CodeOwnersInfo get(String path) throws RestApiException {
      return get(Paths.get(path));
    }

    /**
     * Adds {@link ListAccountsOption} options on the request to control which account fields should
     * be populated in the {@link CodeOwnerInfo#account} field of the returned {@link
     * CodeOwnerInfo}s.
     *
     * <p>Appends to the options which have been set so far.
     */
    public QueryRequest withOptions(
        ListAccountsOption option, ListAccountsOption... furtherOptions) {
      this.options.add(requireNonNull(option, "option"));
      this.options.addAll(Arrays.asList(requireNonNull(furtherOptions, "furtherOptions")));
      return this;
    }

    /**
     * Sets a limit on the number of code owners that should be returned.
     *
     * @param limit the limit
     */
    public QueryRequest withLimit(int limit) {
      this.limit = limit;
      return this;
    }

    /**
     * Sets the seed that should be used to shuffle code owners that have the same score.
     *
     * @param seed seed that should be used to shuffle code owners that have the same score
     */
    public QueryRequest withSeed(long seed) {
      this.seed = seed;
      return this;
    }

    /**
     * Sets whether code ownerships that are assigned to all users should be resolved to random
     * users.
     *
     * @param resolveAllUsers whether code ownerships that are assigned to all users should be
     *     resolved to random users
     */
    public QueryRequest setResolveAllUsers(boolean resolveAllUsers) {
      this.resolveAllUsers = resolveAllUsers;
      return this;
    }

    /**
     * Sets whether only the code owners with the highest score should be returned.
     *
     * @param highestScoreOnly whether only the code owners with the highest score should be
     *     returned
     */
    public QueryRequest withHighestScoreOnly(boolean highestScoreOnly) {
      this.highestScoreOnly = highestScoreOnly;
      return this;
    }

    /**
     * Sets whether debug logs should be included into the response.
     *
     * <p>Requires the 'Check Code Owner' global capability.
     *
     * @param debug whether debug logs should be included into the response
     */
    public QueryRequest withDebug(boolean debug) {
      this.debug = debug;
      return this;
    }

    /**
     * Sets the branch revision from which the code owner configs should be read.
     *
     * <p>Not supported for querying code owners for a path in a change.
     *
     * @param revision the revision from which the code owner configs should be read
     */
    public QueryRequest forRevision(String revision) {
      this.revision = revision;
      return this;
    }

    /** Returns the {@link ListAccountsOption} options which have been set on the request. */
    public ImmutableSet<ListAccountsOption> getOptions() {
      return ImmutableSet.copyOf(options);
    }

    /** Returns the limit which has been set on the request. */
    public Optional<Integer> getLimit() {
      return Optional.ofNullable(limit);
    }

    /** Returns the seed that should be used to shuffle code owners that have the same score. */
    public Optional<Long> getSeed() {
      return Optional.ofNullable(seed);
    }

    /**
     * Whether code ownerships that are assigned to all users should be resolved to random users.
     */
    public Optional<Boolean> getResolveAllUsers() {
      return Optional.ofNullable(resolveAllUsers);
    }

    /** Whether only the code owners with the highest score should be returned. */
    public Optional<Boolean> getHighestScoreOnly() {
      return Optional.ofNullable(highestScoreOnly);
    }

    /** Whether debug logs should be included into the response. */
    public Optional<Boolean> getDebug() {
      return Optional.ofNullable(debug);
    }

    /** Returns the branch revision from which the code owner configs should be read. */
    public Optional<String> getRevision() {
      return Optional.ofNullable(revision);
    }
  }

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements CodeOwners {
    @Override
    public QueryRequest query() {
      throw new NotImplementedException();
    }
  }
}
