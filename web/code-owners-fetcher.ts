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

import {
  ChangeInfo,
  ChangeInfoId,
} from '@gerritcodereview/typescript-api/rest-api.js';
import {RestPluginApi} from '@gerritcodereview/typescript-api/rest.js';
import {CodeOwnersApi, FetchedOwner, OwnerStatus} from './code-owners-api.js';
import {FileStatus} from './code-owners-model.js';

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
  private paused = true;

  private fetchedOwners = new Map<string, FetchedOwner>();

  private pausedFilesFetcher: Array<() => void> = [];

  private fetchFilesPromises: Array<Promise<void>> = [];

  /**
   * Creates a fetcher in paused state. Actual fetching starts after resume()
   * is called.
   */
  constructor(
    private readonly codeOwnerApi: CodeOwnersApi,
    private readonly changeId: ChangeInfoId,
    private readonly filesToFetch: Array<string>,
    private readonly ownersLimit: number,
    maxConcurrentRequest: number
  ) {
    for (let i = 0; i < maxConcurrentRequest; i++) {
      this.fetchFilesPromises.push(this.fetchFiles());
    }
  }

  private async fetchFiles() {
    for (;;) {
      const filePath = await this.getNextFilePath();
      if (!filePath) return;
      try {
        this.fetchedOwners.set(filePath, {
          owners: await this.codeOwnerApi.listOwnersForPath(
            this.changeId,
            filePath,
            this.ownersLimit
          ),
        });
      } catch (error) {
        this.fetchedOwners.set(filePath, {error});
      }
    }
  }

  private async getNextFilePath() {
    if (this.paused) {
      await new Promise<void>(resolve => this.pausedFilesFetcher.push(resolve));
    }
    if (this.filesToFetch.length === 0) return null;
    return this.filesToFetch.splice(0, 1)[0];
  }

  async waitFetchComplete() {
    // Simplified polyfill allSettled
    // TODO: Replace with allSettled once minimal node version is updated.
    await Promise.all(
      this.fetchFilesPromises.map(p =>
        p.then(() => 'succeeded').catch(_reason => 'rejected')
      )
    );
  }

  resume() {
    if (!this.paused) return;
    this.paused = false;
    for (const fetcher of this.pausedFilesFetcher.splice(
      0,
      this.pausedFilesFetcher.length
    )) {
      fetcher();
    }
  }

  pause() {
    this.paused = true;
  }

  getFetchedOwners() {
    return this.fetchedOwners;
  }

  getFiles(): Array<{path: string; info: FetchedOwner}> {
    const result = [];
    for (const [path, info] of this.fetchedOwners.entries()) {
      result.push({path, info});
    }
    return result;
  }
}

export class OwnersProvider {
  private status = FetchStatus.NOT_STARTED;

  private totalFetchCount = 0;

  private codeOwnerApi: CodeOwnersApi;

  private ownersFetcher?: OwnersFetcher;

  constructor(
    restApi: RestPluginApi,
    private readonly change: ChangeInfo,
    private readonly options: {
      maxConcurrentRequests: number;
      ownersLimit: number;
    }
  ) {
    this.codeOwnerApi = new CodeOwnersApi(restApi);
  }

  getStatus() {
    return this.status;
  }

  getProgressString() {
    return !this.ownersFetcher || this.totalFetchCount === 0
      ? 'Loading suggested owners ...'
      : `${this.ownersFetcher.getFetchedOwners().size} out of ` +
          `${this.totalFetchCount} files have returned suggested owners.`;
  }

  getFiles() {
    if (!this.ownersFetcher) return [];
    return this.ownersFetcher.getFiles();
  }

  async fetchSuggestedOwners(codeOwnerStatusMap: Map<string, FileStatus>) {
    if (this.status !== FetchStatus.NOT_STARTED) {
      await this.ownersFetcher!.waitFetchComplete();
      return;
    }
    const filesToFetch = this._getFilesToFetch(codeOwnerStatusMap);
    this.totalFetchCount = filesToFetch.length;
    this.ownersFetcher = new OwnersFetcher(
      this.codeOwnerApi,
      this.change.id,
      filesToFetch,
      this.options.ownersLimit,
      this.options.maxConcurrentRequests
    );
    this.status = FetchStatus.FETCHING;
    this.ownersFetcher.resume();
    await this.ownersFetcher.waitFetchComplete();
    this.status = FetchStatus.FINISHED;
  }

  _getFilesToFetch(codeOwnerStatusMap: Map<string, FileStatus>) {
    // only fetch those not approved yet
    const filesGroupByStatus = [...codeOwnerStatusMap.entries()].reduce(
      (list, [file, fileInfo]) => {
        if (list[fileInfo.status]) list[fileInfo.status].push(file);
        return list;
      },
      {
        [OwnerStatus.PENDING]: [] as Array<string>,
        [OwnerStatus.INSUFFICIENT_REVIEWERS]: [] as Array<string>,
        [OwnerStatus.APPROVED]: [] as Array<string>,
      }
    );
    // always fetch INSUFFICIENT_REVIEWERS first, then pending and then approved
    return filesGroupByStatus[OwnerStatus.INSUFFICIENT_REVIEWERS]
      .concat(filesGroupByStatus[OwnerStatus.PENDING])
      .concat(filesGroupByStatus[OwnerStatus.APPROVED]);
  }

  pause() {
    if (!this.ownersFetcher) return;
    this.ownersFetcher.pause();
  }

  resume() {
    if (!this.ownersFetcher) return;
    this.ownersFetcher.resume();
  }

  reset() {
    this.totalFetchCount = 0;
    this.ownersFetcher = undefined;
    this.status = FetchStatus.NOT_STARTED;
  }
}
