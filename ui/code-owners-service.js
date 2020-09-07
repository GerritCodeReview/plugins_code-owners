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
   * @param {string} project
   * @param {string} branch
   * @param {string} path
   */
  listOwnersForPath(project, branch, path) {
    return this.restApi.get(
        `/projects/${project}/branches/${branch}/` +
        `code_owners/${encodeURIComponent(path)}?limit=5&o=DETAILS`
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
        `/projects/${project}/branches/${branch}/code_owners.config/${path}`
    );
  }

  /**
   * Returns a promise fetching project_config for code owners.
   *
   * @doc https://gerrit.googlesource.com/plugins/code-owners/+/refs/heads/master/resources/Documentation/rest-api.md#get-code-owner-project-config
   * @param {string} project
   */
  getProjectConfig(project) {
    return this.restApi.get(
        `/projects/${project}/code_owners.project_config`
    );
  }
}

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

  areAllFilesApproved() {
    return this.getStatus().then(({rawStatuses}) => {
      return !rawStatuses.some(status => {
        const oldPathStatus = status.old_path_status;
        const newPathStatus = status.new_path_status;
        if (newPathStatus.status !== OwnerStatus.APPROVED) {
          return true;
        }
        return oldPathStatus && oldPathStatus.status !== OwnerStatus.APPROVED;
      });
    });
  }

  /**
   * Gets owner suggestions.
   *
   * @param {!Object} opt
   */
  getSuggestedOwners(opt = {}) {
    return this.getStatus()
        .then(({codeOwnerStatusMap}) => {
          // only fetch those not approved yet
          const filesToFetchOwners = [...codeOwnerStatusMap.keys()].filter(
              file => codeOwnerStatusMap
                  .get(file).status !== OwnerStatus.APPROVED
          );
          return this.batchFetchCodeOwners(filesToFetchOwners)
              .then(ownersMap =>
                this._groupFilesByOwners(ownersMap, codeOwnerStatusMap)
              );
        });
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

  _groupFilesByOwners(fileOwnersMap, codeOwnerStatusMap) {
    // Note: for renamed or moved files, they will have two entries in the map
    // we will treat them as two entries when group as well
    const allFiles = Object.keys(fileOwnersMap);
    const ownersFilesMap = new Map();
    const failedToFetchFiles = new Set();
    for (let i = 0; i < allFiles.length; i++) {
      const fileInfo = {
        path: allFiles[i],
        status: this._computeFileStatus(codeOwnerStatusMap, allFiles[i]),
      };
      // for files failed to fetch, add them to the special group
      if (fileOwnersMap[fileInfo.path].error) {
        failedToFetchFiles.add(fileInfo);
        continue;
      }

      const owners = [...fileOwnersMap[fileInfo.path].owners];
      const ownersKey = owners
          .map(account => account._account_id)
          .sort()
          .join(',');
      ownersFilesMap.set(
          ownersKey,
          ownersFilesMap.get(ownersKey) || {files: [], owners}
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
        error: new Error('Failed to fetch owner info'),
      });
    }

    return groupedItems;
  }

  getGroupName(files) {
    const fileName = files[0].path.split('/').pop();
    return {
      name: fileName,
      prefix: files.length > 1 ? `+ ${files.length - 1} more` : '',
    };
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
                  // use Set to de-dup
                  ownersMap[filePath].owners = new Set(owners);
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
        // Chrome has a limit of 6 connections per host name, and a max of 10 connections.
        maxConcurrentRequests: 6,
      });
    }
    return this.ownerService;
  }
}