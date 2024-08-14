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

package com.google.gerrit.plugins.codeowners.restapi;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwner;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerScore;
import com.google.gerrit.plugins.codeowners.backend.Pair;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Set;

/** Collection of routines to populate {@link CodeOwnerInfo}s. */
class CodeOwnerJson {
  interface Factory {
    /**
     * Creates a {@link CodeOwnerJson} instance.
     *
     * @param accountOptions account options that control which {@link
     *     com.google.gerrit.extensions.common.AccountInfo} fields should be populated for the
     *     accounts in the {@link CodeOwnerInfo}s
     * @return the created {@link CodeOwnerJson} instance.
     */
    CodeOwnerJson create(Set<FillOptions> accountOptions);
  }

  private final AccountLoader.Factory accountLoaderFactory;
  private final Set<FillOptions> accountOptions;

  @Inject
  CodeOwnerJson(
      AccountLoader.Factory accountLoaderFactory, @Assisted Set<FillOptions> accountOptions) {
    this.accountLoaderFactory = accountLoaderFactory;

    checkState(!accountOptions.isEmpty(), "account options must not be empty");
    this.accountOptions = accountOptions;
  }

  /**
   * Formats the provided {@link CodeOwner}s as {@link CodeOwnerInfo}s.
   *
   * @param codeOwners the code owners that should be formatted as {@link CodeOwnerInfo}s
   * @param scorings provides the scorings data that should be populated in {@link CodeOwnerInfo}s
   * @return the provided code owners as {@link CodeOwnerInfo}s
   */
  ImmutableList<CodeOwnerInfo> format(
      ImmutableList<CodeOwner> codeOwners,
      ImmutableMap<CodeOwner, ImmutableMap<CodeOwnerScore, Integer>> scorings)
      throws PermissionBackendException {
    AccountLoader accountLoader = accountLoaderFactory.create(accountOptions);
    ImmutableList<CodeOwnerInfo> codeOwnerInfos =
        requireNonNull(codeOwners, "codeOwners").stream()
            .map(codeOwner -> format(accountLoader, codeOwner, scorings.get(codeOwner)))
            .collect(toImmutableList());
    accountLoader.fill();
    return codeOwnerInfos;
  }

  /**
   * Formats the provided {@link CodeOwner} as {@link CodeOwnerInfo}.
   *
   * @param accountLoader the account loader instance that should be used to create the {@link
   *     com.google.gerrit.extensions.common.AccountInfo} in the code owner info
   * @param codeOwner the code owner that should be formatted as {@link CodeOwnerInfo}
   * @param scorings the scorings data that should be populated in {@link CodeOwnerInfo}s
   * @return the provided code owner as {@link CodeOwnerInfo}
   */
  private static CodeOwnerInfo format(AccountLoader accountLoader, CodeOwner codeOwner,
      ImmutableMap<CodeOwnerScore, Integer> scorings) {
    CodeOwnerInfo info = new CodeOwnerInfo();
    info.account = accountLoader.get(requireNonNull(codeOwner, "codeOwner").accountId());
    if (scorings != null) {
      info.scorings = scorings.entrySet().stream()
          .map(e -> Pair.of(e.getKey().name(), e.getValue()))
          .collect(toImmutableMap(Pair::key, Pair::value));
    }
    return info;
  }
}
