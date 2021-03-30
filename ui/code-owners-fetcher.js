/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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

import {CodeOwnersApi} from './code-owners-api.js';

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
export const FetchStatus = {
  /** Fetch hasn't been started */
  NOT_STARTED: 0,
  /**
   * Fetch has been started, but not all files has been finished.
   * Pausing during fetching doesn't change state.
   */
  FETCHING: 1,
  /**
   * All owners has been loaded. resume/pause call doesn't change state.
   */
  FINISHED: 2,
};

/**
 * Fetch owners for files. The class fetches owners in parallel and allows to
 * pause/resume fetch.
 */
class OwnersFetcher {
  /**
   * Creates a fetcher in paused state. Actual fetching starts after resume()
   * is called.
   *
   * @param {Array<string>} filesToFetch - Files paths for loading owners.
   * @param {number} ownersLimit - number of requested owners per file.
   * @param {number} maxConcurrentRequest - max number of concurrent requests to server.
   */
  constructor(codeOwnerApi, changeId, filesToFetch, ownersLimit,
      maxConcurrentRequest) {
    this._fetchedOwners = new Map();
    this._ownersLimit = ownersLimit;
    this._paused = true;
    this._pausedFilesFetcher = [];
    this._filesToFetch = filesToFetch;
    this._fetchFilesPromises = [];
    this._codeOwnerApi = codeOwnerApi;
    this._changeId = changeId;

    for (let i = 0; i < maxConcurrentRequest; i++) {
      this._fetchFilesPromises.push(this._fetchFiles());
    }
  }

  async _fetchFiles() {
    for (;;) {
      const filePath = await this._getNextFilePath();
      if (!filePath) return;
      try {
        this._fetchedOwners.set(filePath, {
          owners: await this._codeOwnerApi.listOwnersForPath(this._changeId,
              filePath, this._ownersLimit),
        });
      } catch (error) {
        this._fetchedOwners.set(filePath, {error});
      }
    }
  }

  async _getNextFilePath() {
    if (this._paused) {
      await new Promise(resolve => this._pausedFilesFetcher.push(resolve));
    }
    if (this._filesToFetch.length === 0) return null;
    return this._filesToFetch.splice(0, 1)[0];
  }

  async waitFetchComplete() {
    await Promise.allSettled(this._fetchFilesPromises);
  }

  resume() {
    if (!this._paused) return;
    this._paused = false;
    for (const fetcher of this._pausedFilesFetcher.splice(0,
        this._pausedFilesFetcher.length)) {
      fetcher();
    }
  }

  pause() {
    this._paused = true;
  }

  getFetchedOwners() {
    return this._fetchedOwners;
  }

  getFiles() {
    const result = [];
    for (const [path, info] of this._fetchedOwners.entries()) {
      result.push({path, info});
    }
    return result;
  }
}

export class OwnersProvider {
  constructor(restApi, change, options) {
    this.change = change;
    this.options = options;
    this._totalFetchCount = 0;
    this._status = FetchStatus.NOT_STARTED;
    this._codeOwnerApi = new CodeOwnersApi(restApi);
  }

  getStatus() {
    return this._status;
  }

  getProgressString() {
    return !this._ownersFetcher || this._totalFetchCount === 0 ?
      `Loading suggested owners ...` :
      `${this._ownersFetcher.getFetchedOwners().size} out of ` +
      `${this._totalFetchCount} files have returned suggested owners.`;
  }

  getFiles() {
    if (!this._ownersFetcher) return [];
    return this._ownersFetcher.getFiles();
  }

  async fetchSuggestedOwners(codeOwnerStatusMap) {
    if (this._status !== FetchStatus.NOT_STARTED) {
      await this._ownersFetcher.waitFetchComplete();
      return;
    }
    const filesToFetch = this._getFilesToFetch(codeOwnerStatusMap);
    this._totalFetchCount = filesToFetch.length;
    this._ownersFetcher = new OwnersFetcher(this._codeOwnerApi, this.change.id,
        filesToFetch,
        this.options.ownersLimit, this.options.maxConcurrentRequests);
    this._status = FetchStatus.FETCHING;
    this._ownersFetcher.resume();
    await this._ownersFetcher.waitFetchComplete(filesToFetch);
    this._status = FetchStatus.FINISHED;
  }

  _getFilesToFetch(codeOwnerStatusMap) {
    // only fetch those not approved yet
    const filesGroupByStatus = [...codeOwnerStatusMap.entries()].reduce(
        (list, [file, fileInfo]) => {
          if (list[fileInfo.status]) list[fileInfo.status].push(file);
          return list;
        }
        , {
          [OwnerStatus.PENDING]: [],
          [OwnerStatus.INSUFFICIENT_REVIEWERS]: [],
          [OwnerStatus.APPROVED]: [],
        }
    );
    // always fetch INSUFFICIENT_REVIEWERS first, then pending and then approved
    return filesGroupByStatus[OwnerStatus.INSUFFICIENT_REVIEWERS]
        .concat(filesGroupByStatus[OwnerStatus.PENDING])
        .concat(filesGroupByStatus[OwnerStatus.APPROVED]);
  }

  pause() {
    if (!this._ownersFetcher) return;
    this._ownersFetcher.pause();
  }

  resume() {
    if (!this._ownersFetcher) return;
    this._ownersFetcher.resume();
  }

  reset() {
    this._totalFetchCount = 0;
    this.ownersFetcher = null;
    this._status = FetchStatus.NOT_STARTED;
  }
}
