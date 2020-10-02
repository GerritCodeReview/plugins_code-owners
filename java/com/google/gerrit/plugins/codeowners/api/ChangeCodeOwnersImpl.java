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

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnerStatus;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.restapi.change.Revisions;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/** Implementation of the {@link ChangeCodeOwners} API. */
public class ChangeCodeOwnersImpl implements ChangeCodeOwners {
  interface Factory {
    ChangeCodeOwnersImpl create(ChangeResource changeResource);
  }

  private final Revisions revisions;
  private final RevisionCodeOwnersImpl.Factory revisionCodeOwnersApi;
  private final ChangeResource changeResource;
  private final GetCodeOwnerStatus getCodeOwnerStatus;

  @Inject
  public ChangeCodeOwnersImpl(
      Revisions revisions,
      RevisionCodeOwnersImpl.Factory revisionCodeOwnersApi,
      GetCodeOwnerStatus getCodeOwnerStatus,
      @Assisted ChangeResource changeResource) {
    this.revisions = revisions;
    this.revisionCodeOwnersApi = revisionCodeOwnersApi;
    this.getCodeOwnerStatus = getCodeOwnerStatus;
    this.changeResource = changeResource;
  }

  @Override
  public CodeOwnerStatusInfo getCodeOwnerStatus() throws RestApiException {
    try {
      return getCodeOwnerStatus.apply(changeResource).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get code owner status", e);
    }
  }

  @Override
  public RevisionCodeOwners revision(String id) throws RestApiException {
    try {
      RevisionResource revisionResource = revisions.parse(changeResource, IdString.fromDecoded(id));
      return revisionCodeOwnersApi.create(revisionResource);
    } catch (Exception e) {
      throw asRestApiException("Cannot get revision code owners API", e);
    }
  }
}
