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
   * @param {string} changeId
   */
  listOwnerStatus(changeId) {
    return this.restApi.get(`/changes/${changeId}/code_owners.status`);
  }

  /**
   * Returns a promise fetching the owners for a given path.
   *
   * @param {string} project
   * @param {string} branch
   * @param {string} path
   */
  listOwnersForPath(project, branch, path) {
    return this.restApi.get(
        `/projects/${project}/branches/${branch}/code_owners/${path}?limit=5`
    );
  }

  /**
   * Returns a promise fetching the owners config for a given path.
   *
   * @param {string} project
   * @param {string} branch
   * @param {string} path
   */
  getConfigForPath(project, branch, path) {
    // TODO: may need to handle the 204 when does not exist
    return this.restApi.get(
        `/projects/${project}/branches/${branch}/code_owners.config/${path}`
    );
  }
}

// TODO(taoalpha): error flows
/**
 * Service for the data layer used in the plugin UI.
 */
export class CodeOwnerService {
  constructor(restApi, change, options = {}) {
    this.restApi = restApi;
    this.change = change;
    this.options = {maxConcurrentRequests: 10, ...options};
    this.codeOwnerApi = new CodeOwnerApi(restApi);

    this.init();
  }

  /**
   * Initial fetches.
   */
  init() {
    this.statusPromise = this.codeOwnerApi.listOwnerStatus(this.change._number);
  }

  getStatus() {
    return this.statusPromise.then(res => {
      if (!this.isOnLatestPatchset(res.patch_set_id)) {
        // status is outdated, re-init
        this.init();
        return this.statusPromise;
      }
      return res;
    });
  }

  /**
   * Gets owner suggestions.
   *
   * @param {!Object} opt
   * @property {boolean} skipApproved
   * @property {boolean} onlyApproved - this and skipApproved are mutual exclusive, will ignore skipApproved if set
   */
  getSuggestedOwners(opt = {}) {
    return this.getStatus()
        .then(({file_owner_statuses}) => {
          let filesToFetchOwners = Object.keys(file_owner_statuses);
          if (opt.skipApproved) {
            filesToFetchOwners = filesToFetchOwners.filter(
                file => file_owner_statuses[file] !== OwnerStatus.APPROVED
            );
          }
          if (opt.onlyApproved) {
            filesToFetchOwners = filesToFetchOwners.filter(
                file => file_owner_statuses[file] === OwnerStatus.APPROVED
            );
          }
          return this.batchFetchCodeOwners(filesToFetchOwners);
        })
        .then(fileOwnersMap => {
          return this._groupFilesByOwners(fileOwnersMap);
        });
  }

  _groupFilesByOwners(fileOwnersMap) {
    // TODO(taoalpha): For moved files, they need to be always in a different group
    // since they may need two owners to approve
    const ownersFilesMap = {};
    const allFiles = Object.keys(fileOwnersMap);
    for (let i = 0; i < allFiles.length; i++) {
      const filePath = allFiles[i];
      // TODO(taoalpha): handle failed fetches, fileOwnersMap[filePath]
      // will be {error}
      const ownersKey = (fileOwnersMap[filePath].owners || [])
          .map(account => account._account_id)
          .sort()
          .join(',');
      ownersFilesMap[ownersKey] = ownersFilesMap[ownersKey] || [];
      ownersFilesMap[ownersKey].push(filePath);
    }
    return Object.keys(ownersFilesMap).map(ownersKey => {
      const groupName = this.getGroupName(ownersFilesMap[ownersKey]);
      const firstFileInTheGroup = ownersFilesMap[ownersKey][0];
      return {
        groupName,
        files: ownersFilesMap[ownersKey],
        owners: fileOwnersMap[firstFileInTheGroup].owners,
      };
    });
  }

  getGroupName(files) {
    const fileName = files[0].split('/').pop();
    return `${
      files.length > 1 ? `(${files.length} files) ${fileName}, ...` : fileName
    }`;
  }

  /**
   * Returns a promise with whether status is for latest patchset or not.
   */
  isStatusOnLatestPatchset() {
    return this.statusPromise.then(({patch_set_id}) => {
      return this.isOnLatestPatchset(patch_set_id);
    });
  }

  isOnLatestPatchset(patchsetId) {
    const latestRevision = this.change.revisions[this.change.current_revision];
    return `${latestRevision._number}` === `${patchsetId}`;
  }

  getStatusForPath(path) {
    return this.getStatus().then(({file_owner_statuses}) => {
      return file_owner_statuses[path];
    });
  }

  /**
   * Recursively fetches code owners for all files until finished.
   *
   * @param {!Array<string>} files
   */
  batchFetchCodeOwners(files, ownersMap = {}) {
    const batchRequests = [];
    const maxConcurrentRequests = this.options.maxConcurrentRequests;
    for (let i = 0; i < maxConcurrentRequests; i++) {
      const filePath = files[i];
      if (filePath) {
        ownersMap[filePath] = {};
        batchRequests.push(
            this.codeOwnerApi
                .listOwnersForPath(
                    this.change.project,
                    this.change.branch,
                    filePath
                )
                .then(owners => {
                  ownersMap[filePath].owners = owners;
                })
                .catch(e => {
                  ownersMap[filePath].error = e;
                })
        );
      }
    }
    const resPromise = Promise.all(batchRequests);
    if (files.length > maxConcurrentRequests) {
      return resPromise.then(() =>
        this.batchFetchCodeOwners(files.slice(maxConcurrentRequests), ownersMap)
      );
    }
    return resPromise.then(() => ownersMap);
  }

  static getOwnerService(restApi, change) {
    if (!this.ownerService || this.ownerService.change !== change) {
      this.ownerService = new CodeOwnerService(restApi, change, {
        maxConcurrentRequests: 2,
      });
    }
    return this.ownerService;
  }
}