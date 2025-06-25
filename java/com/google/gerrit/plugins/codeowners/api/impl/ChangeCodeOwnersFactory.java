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

package com.google.gerrit.plugins.codeowners.api.impl;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.plugins.codeowners.api.ChangeCodeOwners;
import com.google.gerrit.plugins.codeowners.api.CodeOwners;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.restapi.change.ChangesCollection;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Factory to instantiate the {@link ChangeCodeOwners} API.
 *
 * <p>Allows to instantiate the {@link CodeOwners} API for a branch and maybe later for a change.
 */
@Singleton
public class ChangeCodeOwnersFactory {
  private final ChangeCodeOwnersImpl.Factory changeCodeOwnersFactory;
  private final ChangesCollection changesCollection;

  @Inject
  ChangeCodeOwnersFactory(
      ChangeCodeOwnersImpl.Factory changeCodeOwnersFactory, ChangesCollection changesCollection) {
    this.changeCodeOwnersFactory = changeCodeOwnersFactory;
    this.changesCollection = changesCollection;
  }

  /**
   * Returns the {@link ChangeCodeOwners} API for the given change.
   *
   * @param changeId the ID of the change for which the {@link ChangeCodeOwners} API should be
   *     returned
   * @return the {@link ChangeCodeOwners} API for the given change
   */
  public ChangeCodeOwners change(String changeId) throws RestApiException {
    return changeCodeOwnersFactory.create(getChangeResource(changeId));
  }

  /**
   * Creates a {@link ChangeResource} for the given change.
   *
   * @param changeId ID of the change for which a {@link ChangeResource} should be created
   * @return the {@link ChangeResource} for the given change
   */
  private ChangeResource getChangeResource(String changeId) throws RestApiException {
    try {
      return changesCollection.parse(TopLevelResource.INSTANCE, IdString.fromUrl(changeId));
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve change", e);
    }
  }
}
