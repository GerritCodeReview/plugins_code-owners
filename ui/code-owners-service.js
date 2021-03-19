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

/**
 * All statuses returned for owner status.
 *
 * @enum
 */
export const OwnerStatus = {
  INSUFFICIENT_REVIEWERS: 'INSUFFICIENT_REVIEWERS',
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
};

/**
 * @enum
 */
const FetchStatus = {
  NOT_STARTED: 0,
  FETCHING: 1,
  FINISHED: 2,
  ABORT: 3,
};

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

// TODO: Try to remove it. The ResponseError and getErrorMessage duplicates
// code from the gr-plugin-rest-api.ts. This code is required because
// we want custom error processing in some functions. For details see
// the original gr-plugin-rest-api.ts file/

class ResponseError extends Error {
  constructor(response) {
    super();
    this.response = response;
  }
}

async function getErrorMessage(response) {
  const text = await response.text();
  return text ?
    `${response.status}: ${text}` :
    `${response.status}`;
}

/**
 * Responsible for communicating with the rest-api
 *
 * @see resources/Documentation/rest-api.md
 */
class CodeOwnerApi {
  constructor(restApi) {
    this.restApi = restApi;
  }

  /**
   * Returns a promise fetching the owner statuses for all files within the change.
   *
   * @doc https://gerrit.googlesource.com/plugins/code-owners/+/refs/heads/master/resources/Documentation/rest-api.md#change-endpoints
   * @param {string} changeId
   */
  listOwnerStatus(changeId) {
    return this.restApi.get(`/changes/${changeId}/code_owners.status`);
  }

  /**
   * Returns a promise fetching the owners for a given path.
   *
   * @doc https://gerrit.googlesource.com/plugins/code-owners/+/refs/heads/master/resources/Documentation/rest-api.md#list-code-owners-for-path-in-branch
   * @param {string} changeId
   * @param {string} path
   */
  listOwnersForPath(changeId, path) {
    return this.restApi.get(
        `/changes/${changeId}/revisions/current/code_owners` +
        `/${encodeURIComponent(path)}?limit=5&o=DETAILS`
    );
  }

  /**
   * Returns a promise fetching the owners config for a given path.
   *
   * @doc https://gerrit.googlesource.com/plugins/code-owners/+/refs/heads/master/resources/Documentation/rest-api.md#branch-endpoints
   * @param {string} project
   * @param {string} branch
   * @param {string} path
   */
  getConfigForPath(project, branch, path) {
    return this.restApi.get(
        `/projects/${encodeURIComponent(project)}/` +
        `branches/${encodeURIComponent(branch)}/` +
        `code_owners.config/${encodeURIComponent(path)}`
    );
  }

  /**
   * Returns a promise fetching the owners config for a given branch.
   *
   * @doc https://gerrit.googlesource.com/plugins/code-owners/+/refs/heads/master/resources/Documentation/rest-api.md#branch-endpoints
   * @param {string} project
   * @param {string} branch
   */
  async getBranchConfig(project, branch) {
    const errFn = (response, error) => {
      if (error) throw error;
      if (response) throw new ResponseError(response);
      throw new Error('Generic REST API error');
    }
    try {
      const config = await this.restApi.send(
          'GET',
          `/projects/${encodeURIComponent(project)}/` +
          `branches/${encodeURIComponent(branch)}/` +
          `code_owners.branch_config`,
          undefined,
          errFn
      );
      if (config.override_approval && !(config.override_approval
          instanceof Array)) {
        // In the upcoming backend changes, the override_approval will be changed
        // to array with (possible) multiple items.
        // While this transition is in progress, the frontend supports both API -
        // the old one and the new one.
        return {...config, override_approval: [config.override_approval]};
      }
      return config;
    } catch(err) {
      if (err instanceof ResponseError) {
        if (err.response.status === 404) {
          // The 404 error means that the branch doesn't exist and
          // the plugin should be disabled.
          return {disabled: true};
        }
        return getErrorMessage(err.response).then(msg => {
          throw new Error(msg);
        });
      }
      throw err;
    }
  }
}

/**
 * Wrapper around codeOwnerApi, sends each requests only once and then cache
 * the response. A new CodeOwnersCacheApi instance is created every time when a
 * new change object is assigned.
 * Gerrit never updates existing change object, but instead always assigns a new
 * change object. Particularly, a new change object is assigned when a change
 * is updated and user clicks reload toasts to see the updated change.
 * As a result, the lifetime of a cache is the same as a lifetime of an assigned
 * change object.
 * Periodical cache invalidation can lead to inconsistency in UI, i.e.
 * user can see the old reviewers list (reflects a state when a change was
 * loaded) and code-owners status for the current reviewer list. To avoid
 * this inconsistency, the cache doesn't invalidate.
 */
export class CodeOwnersCacheApi {
  constructor(codeOwnerApi, change) {
    this.codeOwnerApi = codeOwnerApi;
    this.change = change;
    this.promises = {};
  }

  _fetchOnce(cacheKey, asyncFn) {
    if (!this.promises[cacheKey]) {
      this.promises[cacheKey] = asyncFn();
    }
    return this.promises[cacheKey];
  }

  getAccount() {
    return this._fetchOnce('getAccount', () => this._getAccount());
  }

  async _getAccount() {
    const loggedIn = await this.codeOwnerApi.restApi.getLoggedIn();
    if (!loggedIn) return undefined;
    return await this.codeOwnerApi.restApi.getAccount();
  }

  listOwnerStatus() {
    return this._fetchOnce('listOwnerStatus',
        () => this.codeOwnerApi.listOwnerStatus(this.change._number));
  }

  getBranchConfig() {
    return this._fetchOnce('getBranchConfig',
        () => this.codeOwnerApi.getBranchConfig(this.change.project,
            this.change.branch));
  }

  listOwnersForPath(path) {
    return this._fetchOnce(`listOwnersForPath:${path}`,
        () => this.codeOwnerApi.listOwnersForPath(this.change.id, path));
  }
}

export class OwnersFetcher {
  constructor(restApi, change, options) {
    // fetched files and fetching status
    this._fetchedOwners = new Map();
    this._fetchStatus = FetchStatus.NOT_STARTED;
    this._totalFetchCount = 0;
    this.change = change;
    this.options = options;
    this.codeOwnerApi = new CodeOwnerApi(restApi);
  }

  getStatus() {
    return this._fetchStatus;
  }

  getProgressString() {
    return this._totalFetchCount === 0 ?
      `Loading suggested owners ...` :
      `${this._fetchedOwners.size} out of ${this._totalFetchCount} files have returned suggested owners.`;
  }

  getFiles() {
    const result = [];
    for (const [path, info] of this._fetchedOwners.entries()) {
      result.push({path, info});
    }
    return result;
  }

  async fetchSuggestedOwners(codeOwnerStatusMap) {
    // reset existing temporary storage
    this._fetchedOwners = new Map();
    this._fetchStatus = FetchStatus.FETCHING;
    this._totalFetchCount = 0;

    // only fetch those not approved yet
    const filesGroupByStatus = [...codeOwnerStatusMap.keys()].reduce(
        (list, file) => {
          const status = codeOwnerStatusMap
              .get(file).status;
          if (status === OwnerStatus.INSUFFICIENT_REVIEWERS) {
            list.missing.push(file);
          } else if (status === OwnerStatus.PENDING) {
            list.pending.push(file);
          }
          return list;
        }
        , {pending: [], missing: []});
    // always fetch INSUFFICIENT_REVIEWERS first and then pending
    const filesToFetch = filesGroupByStatus.missing
        .concat(filesGroupByStatus.pending);
    this._totalFetchCount = filesToFetch.length;
    await this._batchFetchCodeOwners(filesToFetch);
    this._fetchStatus = FetchStatus.FINISHED;
  }

  /**
   * Recursively fetches code owners for all files until finished.
   *
   * @param {!Array<string>} files
   */
  async _batchFetchCodeOwners(files) {
    if (this._fetchStatus === FetchStatus.ABORT) {
      return this._fetchedOwners;
    }

    const batchRequests = [];
    const maxConcurrentRequests = this.options.maxConcurrentRequests;
    for (let i = 0; i < maxConcurrentRequests; i++) {
      const filePath = files[i];
      if (filePath) {
        this._fetchedOwners.set(filePath, {});
        batchRequests.push(this._fetchOwnersForPath(this.change.id, filePath));
      }
    }
    const resPromise = Promise.all(batchRequests);
    await resPromise;
    if (files.length > maxConcurrentRequests) {
      return await this._batchFetchCodeOwners(
          files.slice(maxConcurrentRequests));
    }
    return this._fetchedOwners;
  }

  async _fetchOwnersForPath(changeId, filePath) {
    try {
      const owners = await this.codeOwnerApi.listOwnersForPath(changeId,
          filePath);
      this._fetchedOwners.get(filePath).owners = owners;
    } catch (e) {
      this._fetchedOwners.get(filePath).error = e;
    }
  }

  abort() {
    this._fetchStatus = FetchStatus.ABORT;
    this._fetchedOwners = new Map();
    this._totalFetchCount = 0;
  }
}

/**
 * Service for the data layer used in the plugin UI.
 */
export class CodeOwnerService {
  constructor(restApi, change, options = {}) {
    this.restApi = restApi;
    this.change = change;
    const codeOwnerApi = new CodeOwnerApi(restApi);
    this.codeOwnerCacheApi = new CodeOwnersCacheApi(codeOwnerApi, change);

    const fetcherOptions = {
      maxConcurrentRequests: options.maxConcurrentRequests || 10,
    };
    this.ownersFetcher = new OwnersFetcher(restApi, change, fetcherOptions);
  }

  /**
   * Prefetch data
   */
  async prefetch() {
    try {
      await Promise.all([
        this.codeOwnerCacheApi.getAccount(),
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
    const account = await this.codeOwnerCacheApi.getAccount();
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
    if (status.enabled && !this.isOnLatestPatchset(status.patchsetNumber)) {
      // status is outdated, abort and re-init
      this.abort();
      this.prefetch();
      return await this.codeOwnerCacheApi.getStatus();
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
      };
    }

    const onwerStatus = await this.codeOwnerCacheApi.listOwnerStatus();

    return {
      enabled: true,
      patchsetNumber: onwerStatus.patch_set_number,
      codeOwnerStatusMap: this._formatStatuses(
          onwerStatus.file_code_owner_statuses
      ),
      rawStatuses: onwerStatus.file_code_owner_statuses,
    };
  }

  async areAllFilesApproved() {
    const {rawStatuses} = await this.getStatus();
    return !rawStatuses.some(status => {
      const oldPathStatus = status.old_path_status;
      const newPathStatus = status.new_path_status;
      // For deleted files, no new_path_status exists
      return (newPathStatus && newPathStatus.status !== OwnerStatus.APPROVED)
        || (oldPathStatus && oldPathStatus.status !== OwnerStatus.APPROVED);
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
  async getSuggestedOwners() {
    const {codeOwnerStatusMap} = await this.getStatus();

    // In case its aborted due to outdated patches
    // should kick start the fetching again
    // Note: we currently are not reusing the instance when switching changes,
    // so if its `abort` due to different changes, the whole instance will be
    // outdated and not used.
    if (this.ownersFetcher.getStatus() === FetchStatus.NOT_STARTED
      || this.ownersFetcher.getStatus() === FetchStatus.ABORT) {
      await this.ownersFetcher.fetchSuggestedOwners(codeOwnerStatusMap);
    }

    return {
      finished: this.ownersFetcher.getStatus() === FetchStatus.FINISHED,
      status: this.ownersFetcher.getStatus(),
      progress: this.ownersFetcher.getProgressString(),
      suggestions: this._groupFilesByOwners(codeOwnerStatusMap,
          this.ownersFetcher.getFiles()),
    };
  }

  async getSuggestedOwnersProgress() {
    const {codeOwnerStatusMap} = await this.getStatus();
    return {
      finished: this.ownersFetcher.getStatus() === FetchStatus.FINISHED,
      status: this.ownersFetcher.getStatus(),
      progress: this.ownersFetcher.getProgressString(),
      suggestions: this._groupFilesByOwners(codeOwnerStatusMap,
          this.ownersFetcher.getFiles()),
    };
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

  _groupFilesByOwners(codeOwnerStatusMap, files) {
    // Note: for renamed or moved files, they will have two entries in the map
    // we will treat them as two entries when group as well
    const ownersFilesMap = new Map();
    const failedToFetchFiles = new Set();
    for (const file of files) {
      const fileInfo = {
        path: file.path,
        status: this._computeFileStatus(codeOwnerStatusMap, file.path),
      };
      // for files failed to fetch, add them to the special group
      if (file.info.error) {
        failedToFetchFiles.add(fileInfo);
        continue;
      }

      // do not include files still in fetching
      if (!file.info.owners) {
        continue;
      }

      const ownersKey = this._getOwnersGroupKey(file.info.owners);
      ownersFilesMap.set(
          ownersKey,
          ownersFilesMap.get(ownersKey) || {files: [], owners: file.info.owners}
      );
      ownersFilesMap.get(ownersKey).files.push(fileInfo);
    }
    const groupedItems = [];
    for (const ownersKey of ownersFilesMap.keys()) {
      const groupName = this.getGroupName(ownersFilesMap.get(ownersKey).files);
      groupedItems.push({
        groupName,
        files: ownersFilesMap.get(ownersKey).files,
        owners: ownersFilesMap.get(ownersKey).owners,
      });
    }

    if (failedToFetchFiles.size > 0) {
      const failedFiles = [...failedToFetchFiles];
      groupedItems.push({
        groupName: this.getGroupName(failedFiles),
        files: failedFiles,
        error: new Error(
            'Failed to fetch code owner info. Try to refresh the page.'),
      });
    }

    return groupedItems;
  }

  _getOwnersGroupKey(owners) {
    if (owners.owned_by_all_users) {
      return '__owned_by_all_users__';
    }
    const code_owners = owners.code_owners;
    return code_owners
        .map(owner => owner.account._account_id)
        .sort()
        .join(',');
  }

  getGroupName(files) {
    const fileName = files[0].path.split('/').pop();
    return {
      name: fileName,
      prefix: files.length > 1 ? `+ ${files.length - 1} more` : '',
    };
  }

  isOnLatestPatchset(patchsetId) {
    const latestRevision = this.change.revisions[this.change.current_revision];
    return `${latestRevision._number}` === `${patchsetId}`;
  }

  abort() {
    this.ownersFetcher.abort();
    const codeOwnerApi = new CodeOwnerApi(this.restApi);
    this.codeOwnerCacheApi = new CodeOwnersCacheApi(codeOwnerApi, change);
  }

  async getBranchConfig() {
    return this.codeOwnerCacheApi.getBranchConfig();
  }

  async isCodeOwnerEnabled() {
    if (this.change.status === ChangeStatus.ABANDONED ||
        this.change.status === ChangeStatus.MERGED) {
      return false;
    }
    const config = await this.codeOwnerCacheApi.getBranchConfig();
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

