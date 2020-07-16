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
    this.codeOwnerApi = new MockCodeOwnerApi(restApi);

    this.init();
  }

  /**
   * Initial fetches.
   */
  init() {
    this.statusPromise = this.codeOwnerApi
        .listOwnerStatus(this.change._number)
        .then(res => {
          return {
            patchsetNumber: res.patch_set_number,
            codeOwnerStatusMap: this._formatStatuses(
                res.file_code_owner_statuses
            ),
            rawStatuses: res.file_code_owner_statuses,
          };
        });
  }

  getStatus() {
    return this.statusPromise.then(res => {
      if (!this.isOnLatestPatchset(res.patchsetNumber)) {
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
        .then(({codeOwnerStatusMap}) => {
          let filesToFetchOwners = Object.keys(codeOwnerStatusMap);
          if (opt.skipApproved) {
            filesToFetchOwners = filesToFetchOwners.filter(
                file => codeOwnerStatusMap[file].status !== OwnerStatus.APPROVED
            );
          }
          if (opt.onlyApproved) {
            filesToFetchOwners = filesToFetchOwners.filter(
                file => codeOwnerStatusMap[file].status === OwnerStatus.APPROVED
            );
          }
          return this.batchFetchCodeOwners(filesToFetchOwners)
              .then(ownersMap =>
                this._groupFilesByOwners(ownersMap)
              );
        });
  }

  _formatStatuses(statuses) {
    // convert the array of statuses to map between file path -> status
    return statuses.reduce((prev, cur) => {
      const newPathStatus = cur.new_path_status;
      const oldPathStatus = cur.old_path_status;
      if (oldPathStatus) {
        prev[oldPathStatus.path] = {
          changeType: cur.change_type,
          status: oldPathStatus.code_owner_status,
          newPath: newPathStatus.path,
        };
      }
      if (newPathStatus) {
        prev[newPathStatus.path] = {
          changeType: cur.change_type,
          status: newPathStatus.code_owner_status,
          oldPath: oldPathStatus ? oldPathStatus.path : null,
        };
      }
      return prev;
    }, {});
  }

  _groupFilesByOwners(fileOwnersMap) {
    // TODO(taoalpha): For moved files, we are treating them as two separate items
    // when suggesting here, may change later for UX
    const allFiles = Object.keys(fileOwnersMap);
    // for moved files, group by both sets (new path and old path)
    // for the rest, group by owner set
    const ownersFilesMap = {};
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
              patch_set_number: '2',
              file_code_owner_statuses: [
              /* eslint-disable */
              {
                change_type: "ADDED",
                'new_path_status': {
                  path: "package.json",
                  code_owner_status: OwnerStatus.APPROVED,
                }
              },
              {
                change_type: "ADDED",
                'new_path_status': {
                  path: ".eslintrc.json",
                  code_owner_status: OwnerStatus.APPROVED,
                }
              },
              {
                change_type: "ADDED",
                'new_path_status': {
                  path: "BUILD",
                  code_owner_status: OwnerStatus.PENDING,
                }
              },
              {
                change_type: "ADDED",
                'new_path_status': {
                  path: "ui/owner-status-column.js",
                  code_owner_status: OwnerStatus.PENDING,
                }
              },
              {
                change_type: "ADDED",
                'new_path_status': {
                  path: "ui/plugin.js",
                  code_owner_status: OwnerStatus.APPROVED,
                }
              },
              {
                change_type: "ADDED",
                'new_path_status': {
                  path: "ui/suggest-owners.js",
                  code_owner_status: OwnerStatus.APPROVED,
                }
              },
              {
                change_type: "ADDED",
                'new_path_status': {
                  path: "ui/code-owners-service.js",
                  code_owner_status: OwnerStatus.APPROVED,
                },
                'old_path_status': {
                  path: "java/code-owners-service1.js",
                  code_owner_status: OwnerStatus.INSUFFICIENT_REVIEWERS,
                }
              }
              /* eslint-disable */
            ],
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
            "https://lh3.googleusercontent.com/-XdUIqdMkCWA/AAAAAAAAAAI/AAAAAAAAAAA/4252rscbv5M/s32/photo.jpg",
        },
      ],
      display_name: "Ben",
      email: "brohlfs@google.com",
      name: "Ben Rohlfs",
      _account_id: 1013302,
    };

    const accountsPool = new Array(5)
      .fill(0)
      .map((_, idx) =>
        Object.assign({}, baseFakeAccount, {
          display_name: idx === 0 ? "Ben": "Tester " + idx,
          _account_id: baseFakeAccount._account_id + idx,
        })
      );

    function pickAccounts() {
      const res = accountsPool.slice();
      delete res[Math.floor(Math.random() * res.length)];
      return res.filter((d) => !!d);
    }

    return new Promise((resolve) => {
      setTimeout(() => resolve(pickAccounts()), 1000);
    });
  }

  isOnLatestPatchset() {
    return true;
  }
}
