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

export class ServerConfigurationError extends Error {
  constructor(msg) {
    super(msg);
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
export class CodeOwnersApi {
  constructor(restApi) {
    this.restApi = restApi;
  }

  /**
   * Send a get request and provides custom response-code handling
   */
  async _get(url) {
    const errFn = (response, error) => {
      if (error) throw error;
      if (response) throw new ResponseError(response);
      throw new Error('Generic REST API error');
    };
    try {
      return await this.restApi.send(
          'GET',
          url,
          undefined,
          errFn
      );
    } catch (err) {
      if (err instanceof ResponseError && err.response.status === 409) {
        return getErrorMessage(err.response).then(msg => {
          throw new ServerConfigurationError(msg);
        });
      }
      throw err;
    }
  }

  /**
   * Returns a promise fetching the owner statuses for all files within the change.
   *
   * @doc https://gerrit.googlesource.com/plugins/code-owners/+/refs/heads/master/resources/Documentation/rest-api.md#change-endpoints
   * @param {string} changeId
   */
  listOwnerStatus(changeId) {
    return this._get(`/changes/${changeId}/code_owners.status`);
  }

  /**
   * Returns a promise fetching the owners for a given path.
   *
   * @doc https://gerrit.googlesource.com/plugins/code-owners/+/refs/heads/master/resources/Documentation/rest-api.md#list-code-owners-for-path-in-branch
   * @param {string} changeId
   * @param {string} path
   */
  listOwnersForPath(changeId, path, limit) {
    return this._get(
        `/changes/${changeId}/revisions/current/code_owners` +
        `/${encodeURIComponent(path)}?limit=${limit}&o=DETAILS`
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
    return this._get(
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
    try {
      const config = await this._get(
          `/projects/${encodeURIComponent(project)}/` +
          `branches/${encodeURIComponent(branch)}/` +
          `code_owners.branch_config`
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
    } catch (err) {
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
}
