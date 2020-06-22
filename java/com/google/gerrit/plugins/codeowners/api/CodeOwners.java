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
import java.util.List;
import java.util.Set;

/**
 * Java API for code owners in a branch.
 *
 * <p>To create an instance for a branch use {@link CodeOwnersFactory}.
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

    /**
     * Lists the code owners for the given path.
     *
     * @param path the path for which the code owners should be returned, the path may or may not
     *     exist
     * @return the code owners for the given path
     */
    public abstract List<CodeOwnerInfo> get(Path path) throws RestApiException;

    /**
     * Lists the code owners for the given path.
     *
     * @param path the path for which the code owners should be returned, the path may or may not
     *     exist
     * @return the code owners for the given path
     */
    public List<CodeOwnerInfo> get(String path) throws RestApiException {
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

    /** Returns the {@link ListAccountsOption} options which have been set on the request. */
    public ImmutableSet<ListAccountsOption> getOptions() {
      return ImmutableSet.copyOf(options);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append('{');
      if (!options.isEmpty()) {
        sb.append("options=").append(options);
      }
      sb.append('}');
      return sb.toString();
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
