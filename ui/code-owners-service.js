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
        `/projects/${project}/branches/${branch}/code_owners/${path}`
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
              patch_set_id: '7',
              file_owner_statuses: {
                /* eslint-disable */
                'java/com/google/gerrit/acceptance/StandaloneSiteTest.java':
                OwnerStatus.APPROVED,
                'java/com/google/gerrit/server/git/HookUtil.java':
                OwnerStatus.INSUFFICIENT_REVIEWERS,
                'java/com/google/gerrit/server/git/receive/AllRefsWatcher.java':
                OwnerStatus.PENDING,
                'java/com/google/gerrit/server/git/receive/HackPushNegotiateHook.java':
                OwnerStatus.INSUFFICIENT_REVIEWERS,
                'java/com/google/gerrit/server/git/receive/ReceiveCommitsAdvertiseRefsHook.java':
                OwnerStatus.APPROVED,
                'java/com/google/gerrit/server/util/time/TimeUtil.java':
                OwnerStatus.PENDING,
                'javatests/com/google/gerrit/server/BUILD': OwnerStatus.APPROVED,
              // "lib/jgit/jgit.bzl": OwnerStatus.INSUFFICIENT_REVIEWERS,
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

    const accountsPool = new Array(6).fill(0).map((_, idx) => Object.assign({}, baseFakeAccount, {display_name: 'Tester ' + idx, _account_id: baseFakeAccount._account_id + idx}));

    function pickAccounts() {
      const res = accountsPool.slice();
      delete res[Math.floor(Math.random() * res.length)];
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
      const groupName = this.getLongestCommonPrefix(ownersFilesMap[ownersKey]);
      return {
        groupName,
        files: ownersFilesMap[ownersKey],
        owners: fileOwnersMap[ownersFilesMap[ownersKey][0]],
      };
    });
  }

  getLongestCommonPrefix(files) {
    const splitFileNames = files.map(file => file.split('/'));
    const longestCommonPrefix = [];
    const firstFile = splitFileNames[0];
    for (let i = 0; i < firstFile.length; i++) {
      const iThPartInFirstFile = firstFile[i];
      for (let j = 1; j < splitFileNames.length; j++) {
        const iThPartInThisFile = splitFileNames[j][i];
        if (iThPartInFirstFile !== iThPartInThisFile) {
          break;
        }
      }
      longestCommonPrefix.push(iThPartInFirstFile);
    }

    if (!longestCommonPrefix.length) {
      return '/';
    }
    return (
      `${files.length > 1 ? `(${files.length} files) ` : ''}` +
      longestCommonPrefix.join('/')
    );
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
