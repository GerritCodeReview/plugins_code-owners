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

// The mock is for change: https://gerrit-review.googlesource.com/c/plugins/code-owners/+/271996
// testing purpose only
class MockCodeOwnerApi extends CodeOwnerApi {
  /**
   * Returns a promise fetching the owner statuses for all files within the change.
   *
   * @param {string} changeId
   */
  listOwnerStatus(changeId) {
    return new Promise(resolve => {
      setTimeout(
          () =>
            resolve({
              patch_set_id: '1',
              file_owner_statuses: {
                /* eslint-disable */
                'package.json':
                OwnerStatus.APPROVED,
                '.eslintrc.json':
                OwnerStatus.INSUFFICIENT_REVIEWERS,
                'BUILD':
                OwnerStatus.PENDING,
                'ui/code-owners-service.js':
                OwnerStatus.INSUFFICIENT_REVIEWERS,
                'ui/owner-status-column.js':
                OwnerStatus.APPROVED,
                'ui/plugin.js':
                OwnerStatus.PENDING,
                'ui/suggest-owners.js': OwnerStatus.APPROVED,
                /* eslint-disable */
              },
            }),
          1000
      );
    });
  }

  /**
   * Returns a promise fetching the owners for a given path.
   *
   * @param {string} project
   * @param {string} branch
   * @param {string} path
   */
  listOwnersForPath(project, branch, path) {
    const baseFakeAccount = {
      avatars: [
        {
          height: 32,
          url:
            'https://lh3.googleusercontent.com/-XdUIqdMkCWA/AAAAAAAAAAI/AAAAAAAAAAA/4252rscbv5M/s32/photo.jpg',
        },
      ],
      display_name: 'Tester A',
      email: 'taoalpha@google.com',
      name: 'Tao Zhou',
      _account_id: 1086906,
    };

    const accountsPool = new Array(5).fill(0).map((_, idx) => Object.assign({}, baseFakeAccount, {display_name: 'Tester ' + idx, _account_id: baseFakeAccount._account_id + idx}));

    function pickAccounts() {
      const res = accountsPool.slice();
      delete res[Math.floor(Math.random() * res.length)];
      return res.filter(d => !!d);
    }

    return new Promise(resolve => {
      setTimeout(
          () =>
            resolve(pickAccounts()),
          1000
      );
    });
  }
}

/**
 * Service for the data layer used in the plugin UI.
 */
export class CodeOwnerService {
  constructor(restApi, change, options = {}) {
    this.restApi = restApi;
    this.change = change;
    this.options = Object.assign({maxConcurrentRequests: 10}, options);
    this.codeOwnerApi = new MockCodeOwnerApi(restApi);

    this.init();
  }

  /**
   * Initial fetches.
   */
  init() {
    this.statusPromise = this.codeOwnerApi.listOwnerStatus(this.change._number);
    this.codeOwnersPromise = this.statusPromise.then(
        ({patch_set_id, file_owner_statuses}) => {
          if (this.isOnLatestPatchset(patch_set_id)) {
            return this.batchFetchCodeOwners(Object.keys(file_owner_statuses));
          } else {
            throw new Error('Owner status is outdated!');
          }
        }
    );
  }

  getSuggestedOwners() {
    return this.codeOwnersPromise.then(fileOwnersMap => {
      return this.groupFilesByOwners(fileOwnersMap);
    });
  }

  groupFilesByOwners(fileOwnersMap) {
    const ownersFilesMap = {};
    const allFiles = Object.keys(fileOwnersMap);
    for (let i = 0; i < allFiles.length; i++) {
      const filePath = allFiles[i];
      const ownersKey = (fileOwnersMap[filePath] || [])
          .map(account => account._account_id)
          .sort()
          .join(',');
      ownersFilesMap[ownersKey] = ownersFilesMap[ownersKey] || [];
      ownersFilesMap[ownersKey].push(filePath);
    }
    return Object.keys(ownersFilesMap).map(ownersKey => {
      const groupName = this.getGroupName(ownersFilesMap[ownersKey]);
      return {
        groupName,
        files: ownersFilesMap[ownersKey],
        owners: fileOwnersMap[ownersFilesMap[ownersKey][0]],
      };
    });
  }

  getGroupName(files) {
    const fileName = files[0].split("/").pop();
    return `${files.length > 1 ? `(${files.length} files) ${fileName}, ...` : fileName}`;
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

  /**
   * Returns a promise with the map of file path and owner status.
   */
  getStatuses() {
    return this.statusPromise.then(({file_owner_statuses}) => {
      return file_owner_statuses;
    });
  }

  getStatusForPath(path) {
    return this.getStatuses().then(statuses => statuses[path]);
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
        batchRequests.push(
            this.codeOwnerApi
                .listOwnersForPath(
                    this.change.project,
                    this.change.branch,
                    filePath
                )
                .then(owners => {
                  ownersMap[filePath] = owners;
                })
                .catch(e => {
                  ownersMap[filePath] = e;
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

/**
 * @enum
 */
export const OwnerStatus = {
  INSUFFICIENT_REVIEWERS: 'INSUFFICIENT_REVIEWERS',
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
};
