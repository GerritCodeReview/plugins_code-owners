/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {SuggestionsType, BestSuggestionsLimit, AllSuggestionsLimit} from './code-owners-model.js';
import {OwnersProvider, OwnerStatus, FetchStatus} from './code-owners-fetcher.js';
import {CodeOwnersApi, CodeOwnersCacheApi} from './code-owners-api.js';

/**
 * Specifies status for a change. The same as ChangeStatus enum in gerrit
 *
 * @enum
 */
const ChangeStatus = {
  ABANDONED: 'ABANDONED',
  MERGED: 'MERGED',
  NEW: 'NEW',
};

/**
 * @enum
 */
const UserRole = {
  ANONYMOUS: 'ANONYMOUS',
  AUTHOR: 'AUTHOR',
  CHANGE_OWNER: 'CHANGE_OWNER',
  REVIEWER: 'REVIEWER',
  CC: 'CC',
  REMOVED_REVIEWER: 'REMOVED_REVIEWER',
  OTHER: 'OTHER',
};

/**
 * Service for the data layer used in the plugin UI.
 */
export class CodeOwnerService {
  constructor(restApi, change, options = {}) {
    this.restApi = restApi;
    this.change = change;
    const codeOwnersApi = new CodeOwnersApi(restApi);
    this.codeOwnersCacheApi = new CodeOwnersCacheApi(codeOwnersApi, change);

    const providerOptions = {
      maxConcurrentRequests: options.maxConcurrentRequests || 10,
    };
    this.ownersProviders = {
      [SuggestionsType.BEST_SUGGESTIONS]: new OwnersProvider(restApi, change, {
        ...providerOptions,
        ownersLimit: BestSuggestionsLimit,
      }),
      [SuggestionsType.ALL_SUGGESTIONS]: new OwnersProvider(restApi, change, {
        ...providerOptions,
        ownersLimit: AllSuggestionsLimit,
      }),
    };
  }

  /**
   * Prefetch data
   */
  async prefetch() {
    try {
      await Promise.all([
        this.codeOwnersCacheApi.getAccount(),
        this.getStatus(),
      ]);
    } catch {
      // Ignore any errors during prefetch.
      // The same call from a different place throws the same exception
      // again. The CodeOwnerService is not responsible for error processing.
    }
  }

  /**
   * Returns the role of the current user. The returned value reflects the
   * role of the user at the time when the change is loaded.
   * For example, if a user removes themselves as a reviewer, the returned
   * role 'REVIEWER' remains unchanged until the change view is reloaded.
   */
  async getLoggedInUserInitialRole() {
    const account = await this.codeOwnersCacheApi.getAccount();
    if (!account) {
      return UserRole.ANONYMOUS;
    }
    const change = this.change;
    if (
      change.revisions &&
      change.current_revision &&
      change.revisions[change.current_revision]
    ) {
      const commit = change.revisions[change.current_revision].commit;
      if (
        commit &&
        commit.author &&
        account.email &&
        commit.author.email === account.email
      ) {
        return UserRole.AUTHOR;
      }
    }
    if (change.owner._account_id === account._account_id) {
      return UserRole.CHANGE_OWNER;
    }
    if (change.reviewers) {
      if (this._accountInReviewers(change.reviewers.REVIEWER, account)) {
        return UserRole.REVIEWER;
      } else if (this._accountInReviewers(change.reviewers.CC, account)) {
        return UserRole.CC;
      } else if (this._accountInReviewers(change.reviewers.REMOVED, account)) {
        return UserRole.REMOVED_REVIEWER;
      }
    }
    return UserRole.OTHER;
  }

  _accountInReviewers(reviewers, account) {
    if (!reviewers) {
      return false;
    }
    return reviewers.some(reviewer =>
      reviewer._account_id === account._account_id);
  }

  async getStatus() {
    const status = await this._getStatus();
    if (status.enabled && this._isOnOlderPatchset(status.patchsetNumber)) {
      // status is returned for an older patchset. Abort, re-init and refetch
      // new status - it is expected, that after several retry a status
      // for the newest patchset is returned
      this.reset();
      this.prefetch();
      return await this.getStatus();
    }
    return status;
  }

  async _getStatus() {
    const enabled = await this.isCodeOwnerEnabled();
    if (!enabled) {
      return {
        patchsetNumber: 0,
        enabled: false,
        codeOwnerStatusMap: new Map(),
        rawStatuses: [],
        newerPatchsetUploaded: false,
      };
    }

    const ownerStatus = await this.codeOwnersCacheApi.listOwnerStatus();

    return {
      enabled: true,
      patchsetNumber: ownerStatus.patch_set_number,
      codeOwnerStatusMap: this._formatStatuses(
          ownerStatus.file_code_owner_statuses
      ),
      rawStatuses: ownerStatus.file_code_owner_statuses,
      newerPatchsetUploaded:
        this._isOnNewerPatchset(ownerStatus.patch_set_number),
    };
  }

  async areAllFilesApproved() {
    const {rawStatuses} = await this.getStatus();
    return !rawStatuses.some(status => {
      const oldPathStatus = status.old_path_status;
      const newPathStatus = status.new_path_status;
      // For deleted files, no new_path_status exists
      return (newPathStatus && newPathStatus.status !== OwnerStatus.APPROVED) ||
        (oldPathStatus && oldPathStatus.status !== OwnerStatus.APPROVED);
    });
  }

  /**
   * Gets owner suggestions.
   *
   * @returns {{
   *  finished?: boolean,
   *  progress?: string,
   *  suggestions: Array<{
   *    groupName: {
   *      name: string,
   *      prefix: string
   *    },
   *    error?: Error,
   *    owners?: Array,
   *    files: Array,
   *  }>
   * }}
   */
  async getSuggestedOwners(suggestionsType) {
    const {codeOwnerStatusMap} = await this.getStatus();
    const ownersProvider = this.ownersProviders[suggestionsType];

    await ownersProvider.fetchSuggestedOwners(codeOwnerStatusMap);

    return {
      finished: ownersProvider.getStatus() === FetchStatus.FINISHED,
      status: ownersProvider.getStatus(),
      progress: ownersProvider.getProgressString(),
      files: this._getFilesWithStatuses(codeOwnerStatusMap,
          ownersProvider.getFiles()),
    };
  }

  async getSuggestedOwnersProgress(suggestionsType) {
    const {codeOwnerStatusMap} = await this.getStatus();
    const ownersProvider = this.ownersProviders[suggestionsType];
    return {
      finished: ownersProvider.getStatus() === FetchStatus.FINISHED,
      status: ownersProvider.getStatus(),
      progress: ownersProvider.getProgressString(),
      files: this._getFilesWithStatuses(codeOwnerStatusMap,
          ownersProvider.getFiles()),
    };
  }

  pauseSuggestedOwnersLoading(suggestionsType) {
    this.ownersProviders[suggestionsType].pause();
  }

  resumeSuggestedOwnersLoading(suggestionsType) {
    this.ownersProviders[suggestionsType].resume();
  }

  _formatStatuses(statuses) {
    // convert the array of statuses to map between file path -> status
    return statuses.reduce((prev, cur) => {
      const newPathStatus = cur.new_path_status;
      const oldPathStatus = cur.old_path_status;
      if (oldPathStatus) {
        prev.set(oldPathStatus.path, {
          changeType: cur.change_type,
          status: oldPathStatus.status,
          newPath: newPathStatus ? newPathStatus.path : null,
        });
      }
      if (newPathStatus) {
        prev.set(newPathStatus.path, {
          changeType: cur.change_type,
          status: newPathStatus.status,
          oldPath: oldPathStatus ? oldPathStatus.path : null,
        });
      }
      return prev;
    }, new Map());
  }

  _computeFileStatus(fileStatusMap, path) {
    // empty for modified files and old-name files
    // Show `Renamed` for renamed file
    const status = fileStatusMap.get(path);
    if (status.oldPath) {
      return 'Renamed';
    }
    return;
  }

  _getFilesWithStatuses(codeOwnerStatusMap, files) {
    return files.map(file => {
      return {
        path: file.path,
        info: file.info,
        status: this._computeFileStatus(codeOwnerStatusMap, file.path),
      };
    });
  }

  _isOnNewerPatchset(patchsetId) {
    const latestRevision = this.change.revisions[this.change.current_revision];
    return patchsetId > latestRevision._number;
  }

  _isOnOlderPatchset(patchsetId) {
    const latestRevision = this.change.revisions[this.change.current_revision];
    return patchsetId < latestRevision._number;
  }

  reset() {
    for (const provider of Object.values(this.ownersProviders)) {
      provider.reset();
    }
    const codeOwnersApi = new CodeOwnersApi(this.restApi);
    this.codeOwnersCacheApi =
        new CodeOwnersCacheApi(codeOwnersApi, this.change);
  }

  async getBranchConfig() {
    return this.codeOwnersCacheApi.getBranchConfig();
  }

  async isCodeOwnerEnabled() {
    if (this.change.status === ChangeStatus.ABANDONED ||
        this.change.status === ChangeStatus.MERGED) {
      return false;
    }
    const config = await this.codeOwnersCacheApi.getBranchConfig();
    return config && !config.disabled;
  }

  static getOwnerService(restApi, change) {
    if (!this.ownerService || this.ownerService.change !== change) {
      this.ownerService = new CodeOwnerService(restApi, change, {
        // Chrome has a limit of 6 connections per host name, and a max of 10 connections.
        maxConcurrentRequests: 6,
      });
      this.ownerService.prefetch();
    }
    return this.ownerService;
  }
}

