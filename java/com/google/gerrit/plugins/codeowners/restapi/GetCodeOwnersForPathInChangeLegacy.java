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

package com.google.gerrit.plugins.codeowners.restapi;

import com.google.gerrit.extensions.client.ListAccountsOption;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerInfo;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.kohsuke.args4j.Option;

/**
 * REST endpoint that gets the code owners for an arbitrary path in a revision of a change.
 *
 * <p>This REST endpoint handles {@code GET
 * /changes/<change-id>/revisions/<revision-id>/code_owners.legacy/<path>} requests.
 *
 * <p>The path may or may not exist in the revision of the change.
 *
 * <p>Legacy version of the {@link GetCodeOwnersForPathInChange} REST endpoint that supports the old
 * response format.
 */
public class GetCodeOwnersForPathInChangeLegacy
    implements RestReadView<CodeOwnersInChangeCollectionLegacy.PathResource> {
  private final Provider<GetCodeOwnersForPathInChange> getCodeOwnersForPathInChangeProvider;
  private final EnumSet<ListAccountsOption> options;
  private final Set<String> hexOptions;

  private int limit = AbstractGetCodeOwnersForPath.DEFAULT_LIMIT;
  private Optional<Long> seed = Optional.empty();

  @Option(
      name = "-o",
      usage = "Options to control which fields should be populated for the returned account")
  public void addOption(ListAccountsOption o) {
    options.add(o);
  }

  @Option(
      name = "-O",
      usage =
          "Options to control which fields should be populated for the returned account, in hex")
  void setOptionFlagsHex(String hex) {
    hexOptions.add(hex);
  }

  @Option(
      name = "--limit",
      aliases = {"-n"},
      metaVar = "CNT",
      usage =
          "maximum number of code owners to list (default = "
              + AbstractGetCodeOwnersForPath.DEFAULT_LIMIT
              + ")")
  public void setLimit(int limit) {
    this.limit = limit;
  }

  @Option(
      name = "--seed",
      usage = "seed that should be used to shuffle code owners that have the same score")
  public void setSeed(long seed) {
    this.seed = Optional.of(seed);
  }

  @Inject
  GetCodeOwnersForPathInChangeLegacy(
      Provider<GetCodeOwnersForPathInChange> getCodeOwnersForPathInChangeProvider) {
    this.getCodeOwnersForPathInChangeProvider = getCodeOwnersForPathInChangeProvider;
    this.options = EnumSet.noneOf(ListAccountsOption.class);
    this.hexOptions = new HashSet<>();
  }

  @Override
  public Response<List<CodeOwnerInfo>> apply(
      CodeOwnersInChangeCollectionLegacy.PathResource resource)
      throws RestApiException, PermissionBackendException {
    GetCodeOwnersForPathInChange getCodeOwnersForPathInChange =
        getCodeOwnersForPathInChangeProvider.get();
    getCodeOwnersForPathInChange.setLimit(limit);
    seed.ifPresent(getCodeOwnersForPathInChange::setSeed);
    options.forEach(getCodeOwnersForPathInChange::addOption);
    hexOptions.forEach(getCodeOwnersForPathInChange::setOptionFlagsHex);
    return getCodeOwnersForPathInChange.apply(
        CodeOwnersInChangeCollection.PathResource.parse(
            resource.getRevisionResource(),
            resource.getRevision(),
            IdString.fromDecoded(resource.getPath().toString())));
  }
}
