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

import {HttpMethod, RestPluginApi} from '@gerritcodereview/typescript-api/rest';
import {
  AccountDetailInfo,
  AccountInfo,
  ChangeInfo,
  ChangeInfoId,
  NumericChangeId,
} from '@gerritcodereview/typescript-api/rest-api';

// TODO: Try to remove it. The ResponseError and getErrorMessage duplicates
// code from the gr-plugin-rest-api.ts. This code is required because
// we want custom error processing in some functions. For details see
// the original gr-plugin-rest-api.ts file/
class ResponseError extends Error {
  constructor(readonly response: Response) {
    super();
  }
}

export class ServerConfigurationError extends Error {
  constructor(msg: string) {
    super(msg);
  }
}

async function getErrorMessage(response: Response) {
  const text = await response.text();
  return text ? `${response.status}: ${text}` : `${response.status}`;
}

export enum MerCommitStrategy {
  ALL_CHANGED_FILES = 'ALL_CHANGED_FILES',
  FILES_WITH_CONFLICT_RESOLUTION = 'FILES_WITH_CONFLICT_RESOLUTION',
}

export enum FallbackCodeOwners {
  NONE = 'NONE',
  ALL_USERS = 'ALL_USERS',
}

export interface GeneralInfo {
  file_extension?: string;
  merge_commit_strategy: MerCommitStrategy;
  implicit_approvals?: boolean;
  override_info_url?: string;
  invalid_code_owner_config_info_url?: string;
  fallback_code_owners: FallbackCodeOwners;
}

export interface CodeOwnerBranchConfigInfo {
  general?: GeneralInfo;
  disabled?: boolean;
  backend_id?: string;
  no_code_owners_defined?: boolean;
  required_approval?: Array<RequiredApprovalInfo>;
  override_approval?: Array<RequiredApprovalInfo>;
}
export interface RequiredApprovalInfo {
  label: string;
  value: number;
}

export enum ChangeType {
  ADDED = 'ADDED',
  MODIFIED = 'MODIFIED',
  DELETED = 'DELETED',
  RENAMED = 'RENAMED',
  COPIED = 'COPIED',
}

export enum OwnerStatus {
  INSUFFICIENT_REVIEWERS = 'INSUFFICIENT_REVIEWERS',
  PENDING = 'PENDING',
  APPROVED = 'APPROVED',
}
export interface PathCodeOwnerStatusInfo {
  path: string;
  status?: OwnerStatus;
  reasons?: Array<string>;
}

export interface FileCodeOwnerStatusInfo {
  change_type: ChangeType;
  old_path_status?: PathCodeOwnerStatusInfo;
  new_path_status?: PathCodeOwnerStatusInfo;
}

export interface CodeOwnerStatusInfo {
  patch_set_number: number;
  file_code_owner_statuses: Array<FileCodeOwnerStatusInfo>;
  more?: boolean;
  accounts?: {[account_id: number]: AccountInfo};
}

export interface CodeOwnersStatusInfo {
  disabled?: boolean;
  disabled_branches?: Array<string>;
}

export interface CodeOwnerInfo {
  account?: AccountInfo;
  selected?: boolean;
}
export interface CodeOwnersInfo {
  code_owners: Array<CodeOwnerInfo>;
  owned_by_all_users?: boolean;
}

export interface FetchedOwner {
  owners?: CodeOwnersInfo;
  error?: unknown;
}

export interface FetchedFile {
  path: string;
  info: FetchedOwner;
  status?: string;
}
/**
 * Responsible for communicating with the rest-api
 *
 * @see resources/Documentation/rest-api.md
 */
export class CodeOwnersApi {
  constructor(readonly restApi: RestPluginApi) {}

  /**
   * Send a get request and provides custom response-code handling
   */
  private async get(url: string): Promise<unknown> {
    const errFn = (response?: Response | null, error?: Error) => {
      if (error) throw error;
      if (response) throw new ResponseError(response);
      throw new Error('Generic REST API error');
    };
    try {
      return await this.restApi.send(HttpMethod.GET, url, undefined, errFn);
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
   * @doc https://gerrit.googlesource.com/plugins/code-owners/+/HEAD/resources/Documentation/rest-api.md#change-endpoints
   */
  listOwnerStatus(changeId: NumericChangeId): Promise<CodeOwnerStatusInfo> {
    return this.get(
      `/changes/${changeId}/code_owners.status?limit=100000`
    ) as Promise<CodeOwnerStatusInfo>;
  }

  /**
   * Returns a promise fetching the owners for a given path.
   *
   * @doc https://gerrit.googlesource.com/plugins/code-owners/+/HEAD/resources/Documentation/rest-api.md#list-code-owners-for-path-in-branch
   */
  listOwnersForPath(
    changeId: ChangeInfoId,
    path: string,
    limit: number
  ): Promise<CodeOwnersInfo> {
    return this.get(
      `/changes/${changeId}/revisions/current/code_owners` +
        `/${encodeURIComponent(path)}?limit=${limit}&o=DETAILS`
    ) as Promise<CodeOwnersInfo>;
  }

  /**
   * Returns a promise fetching the owners config for a given path.
   *
   * @doc https://gerrit.googlesource.com/plugins/code-owners/+/HEAD/resources/Documentation/rest-api.md#branch-endpoints
   */
  getConfigForPath(project: string, branch: string, path: string) {
    return this.get(
      `/projects/${encodeURIComponent(project)}/` +
        `branches/${encodeURIComponent(branch)}/` +
        `code_owners.config/${encodeURIComponent(path)}`
    );
  }

  /**
   * Returns a promise fetching the owners config for a given branch.
   *
   * @doc https://gerrit.googlesource.com/plugins/code-owners/+/HEAD/resources/Documentation/rest-api.md#branch-endpoints
   */
  async getBranchConfig(project: string, branch: string) {
    try {
      const config = (await this.get(
        `/projects/${encodeURIComponent(project)}/` +
          `branches/${encodeURIComponent(branch)}/` +
          `code_owners.branch_config`
      )) as CodeOwnerBranchConfigInfo;
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
  private promises = new Map<string, Promise<unknown>>();

  constructor(
    private readonly codeOwnerApi: CodeOwnersApi,
    private readonly change: ChangeInfo
  ) {}

  private fetchOnce(
    cacheKey: string,
    asyncFn: () => Promise<unknown>
  ): Promise<unknown> {
    let promise = this.promises.get(cacheKey);
    if (promise) return promise;
    promise = asyncFn();
    this.promises.set(cacheKey, promise);
    return promise;
  }

  getAccount(): Promise<AccountDetailInfo | undefined> {
    return this.fetchOnce('getAccount', () => this.getAccountImpl()) as Promise<
      AccountDetailInfo | undefined
    >;
  }

  private async getAccountImpl() {
    const loggedIn = await this.codeOwnerApi.restApi.getLoggedIn();
    if (!loggedIn) return undefined;
    return await this.codeOwnerApi.restApi.getAccount();
  }

  listOwnerStatus(): Promise<CodeOwnerStatusInfo> {
    return this.fetchOnce('listOwnerStatus', () =>
      this.codeOwnerApi.listOwnerStatus(this.change._number)
    ) as Promise<CodeOwnerStatusInfo>;
  }

  getBranchConfig(): Promise<CodeOwnerBranchConfigInfo> {
    return this.fetchOnce('getBranchConfig', () =>
      this.codeOwnerApi.getBranchConfig(this.change.project, this.change.branch)
    ) as Promise<CodeOwnerBranchConfigInfo>;
  }
}
