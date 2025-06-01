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

import {
  SuggestionsType,
  BestSuggestionsLimit,
  AllSuggestionsLimit,
  UserRole,
  Status,
  FileStatus,
} from './code-owners-model';
import {OwnersProvider, FetchStatus} from './code-owners-fetcher';
import {
  CodeOwnersApi,
  CodeOwnersCacheApi,
  FetchedFile,
  FetchedOwner,
  FileCodeOwnerStatusInfo,
  OwnerStatus,
} from './code-owners-api';
import {RestPluginApi} from '@gerritcodereview/typescript-api/rest';
import {
  AccountDetailInfo,
  AccountInfo,
  ChangeInfo,
} from '@gerritcodereview/typescript-api/rest-api';

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

let ownerService: CodeOwnerService | undefined;

interface CodeOwnerServiceOptions {
  maxConcurrentRequests?: number;
}

/**
 * Service for the data layer used in the plugin UI.
 */
export class CodeOwnerService {
  private codeOwnersCacheApi: CodeOwnersCacheApi;

  private ownersProviders: Map<SuggestionsType, OwnersProvider>;

  constructor(
    readonly restApi: RestPluginApi,
    readonly change: ChangeInfo,
    options: CodeOwnerServiceOptions = {}
  ) {
    const codeOwnersApi = new CodeOwnersApi(restApi);
    this.codeOwnersCacheApi = new CodeOwnersCacheApi(codeOwnersApi, change);

    const providerOptions = {
      maxConcurrentRequests: options.maxConcurrentRequests || 10,
    };
    this.ownersProviders = new Map();
    this.ownersProviders.set(
      SuggestionsType.BEST_SUGGESTIONS,
      new OwnersProvider(restApi, change, {
        ...providerOptions,
        ownersLimit: BestSuggestionsLimit,
      })
    );
    this.ownersProviders.set(
      SuggestionsType.ALL_SUGGESTIONS,
      new OwnersProvider(restApi, change, {
        ...providerOptions,
        ownersLimit: AllSuggestionsLimit,
      })
    );
  }

  /**
   * Fetch the account.
   */
  getAccount(): Promise<AccountDetailInfo | undefined> {
    return this.codeOwnersCacheApi.getAccount();
  }

  /**
   * Prefetch data
   */
  async prefetch() {
    try {
      await Promise.all([this.getAccount(), this.getStatus()]);
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
  async getLoggedInUserInitialRole(): Promise<UserRole> {
    const account = await this.getAccount();
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
      if (this.accountInReviewers(change.reviewers.REVIEWER, account)) {
        return UserRole.REVIEWER;
      } else if (this.accountInReviewers(change.reviewers.CC, account)) {
        return UserRole.CC;
      } else if (this.accountInReviewers(change.reviewers.REMOVED, account)) {
        return UserRole.REMOVED_REVIEWER;
      }
    }
    return UserRole.OTHER;
  }

  private accountInReviewers(
    reviewers: AccountInfo[] | undefined,
    account: AccountDetailInfo
  ) {
    if (!reviewers) {
      return false;
    }
    return reviewers.some(
      reviewer => reviewer._account_id === account._account_id
    );
  }

  async getStatus(): Promise<Status> {
    const status = await this.getStatusImpl();
    if (status.enabled && this.isOnOlderPatchset(status.patchsetNumber)) {
      // status is returned for an older patchset. Abort, re-init and refetch
      // new status - it is expected, that after several retry a status
      // for the newest patchset is returned
      this.reset();
      void this.prefetch()
      return await this.getStatus();
    }
    return status;
  }

  private async getStatusImpl() {
    const enabled = await this.isCodeOwnerEnabled();
    if (!enabled) {
      return {
        patchsetNumber: 0,
        enabled: false,
        codeOwnerStatusMap: new Map<string, FileStatus>(),
        rawStatuses: [],
        newerPatchsetUploaded: false,
      };
    }

    const ownerStatus = await this.codeOwnersCacheApi.listOwnerStatus();

    return {
      enabled: true,
      patchsetNumber: ownerStatus.patch_set_number,
      codeOwnerStatusMap: this.formatStatuses(
        ownerStatus.file_code_owner_statuses
      ),
      rawStatuses: ownerStatus.file_code_owner_statuses,
      newerPatchsetUploaded: this.isOnNewerPatchset(
        ownerStatus.patch_set_number
      ),
      accounts: ownerStatus.accounts,
    };
  }

  async areAllFilesApproved() {
    const {rawStatuses} = await this.getStatus();
    return !rawStatuses.some(status => {
      const oldPathStatus = status.old_path_status;
      const newPathStatus = status.new_path_status;
      // For deleted files, no new_path_status exists
      return (
        (newPathStatus && newPathStatus.status !== OwnerStatus.APPROVED) ||
        (oldPathStatus && oldPathStatus.status !== OwnerStatus.APPROVED)
      );
    });
  }

  private ownersProvider(suggestionsType: SuggestionsType) {
    return this.ownersProviders.get(suggestionsType)!;
  }

  /**
   * Gets which files are owned by the given user.
   */
  async getOwnedPaths() {
    const enabled = await this.isCodeOwnerEnabled();
    if (!enabled) {
      return Promise.resolve(undefined);
    }
    return this.codeOwnersCacheApi.listOwnedPaths();
  }

  /**
   * Gets owner suggestions.
   */
  async getSuggestedOwners(suggestionsType: SuggestionsType) {
    const {codeOwnerStatusMap} = await this.getStatus();
    const ownersProvider = this.ownersProvider(suggestionsType);

    await ownersProvider!.fetchSuggestedOwners(codeOwnerStatusMap);

    return {
      finished: ownersProvider.getStatus() === FetchStatus.FINISHED,
      status: ownersProvider.getStatus(),
      progress: ownersProvider.getProgressString(),
      files: this.getFilesWithStatuses(
        codeOwnerStatusMap,
        ownersProvider.getFiles()
      ),
    };
  }

  async getSuggestedOwnersProgress(suggestionsType: SuggestionsType) {
    const {codeOwnerStatusMap} = await this.getStatus();
    const ownersProvider = this.ownersProvider(suggestionsType);
    return {
      finished: ownersProvider.getStatus() === FetchStatus.FINISHED,
      status: ownersProvider.getStatus(),
      progress: ownersProvider.getProgressString(),
      files: this.getFilesWithStatuses(
        codeOwnerStatusMap,
        ownersProvider.getFiles()
      ),
    };
  }

  pauseSuggestedOwnersLoading(suggestionsType: SuggestionsType) {
    this.ownersProvider(suggestionsType).pause();
  }

  resumeSuggestedOwnersLoading(suggestionsType: SuggestionsType) {
    this.ownersProvider(suggestionsType).resume();
  }

  private formatStatuses(statuses?: Array<FileCodeOwnerStatusInfo>) {
    // convert the array of statuses to map between file path -> status
    return (statuses ?? []).reduce((prev, cur) => {
      const newPathStatus = cur.new_path_status;
      const oldPathStatus = cur.old_path_status;
      if (oldPathStatus) {
        prev.set(oldPathStatus.path, {
          changeType: cur.change_type,
          status: oldPathStatus.status,
          newPath: newPathStatus ? newPathStatus.path : null,
          reasons: oldPathStatus.reasons,
        });
      }
      if (newPathStatus) {
        prev.set(newPathStatus.path, {
          changeType: cur.change_type,
          status: newPathStatus.status,
          oldPath: oldPathStatus ? oldPathStatus.path : null,
          reasons: newPathStatus.reasons,
        });
      }
      return prev;
    }, new Map<string, FileStatus>());
  }

  private computeFileStatus(
    fileStatusMap: Map<string, FileStatus>,
    path: string
  ) {
    // empty for modified files and old-name files
    // Show `Renamed` for renamed file
    const status = fileStatusMap.get(path);
    if (status && status.oldPath) {
      return 'Renamed';
    }
    return;
  }

  private getFilesWithStatuses(
    codeOwnerStatusMap: Map<string, FileStatus>,
    files: Array<{
      path: string;
      info: FetchedOwner;
    }>
  ): Array<FetchedFile> {
    return files
      .map(file => {
        return {
          path: file.path,
          info: file.info,
          status: this.computeFileStatus(codeOwnerStatusMap, file.path),
        };
      })
      .sort((a, b) => CodeOwnerService.specialFilePathCompare(a.path, b.path));
  }

  private isOnNewerPatchset(patchsetId: number) {
    if (this.change.current_revision === undefined) return false;
    const latestRevision = this.change.revisions![this.change.current_revision];
    if (latestRevision._number === 'edit') {
      return false;
    }
    return patchsetId > latestRevision._number;
  }

  private isOnOlderPatchset(patchsetId: number) {
    if (this.change.current_revision === undefined) return false;
    const latestRevision = this.change.revisions![this.change.current_revision];
    if (latestRevision._number === 'edit') {
      return false;
    }
    return patchsetId < latestRevision._number;
  }

  reset() {
    for (const provider of Object.values(this.ownersProviders)) {
      provider.reset();
    }
    const codeOwnersApi = new CodeOwnersApi(this.restApi);
    this.codeOwnersCacheApi = new CodeOwnersCacheApi(
      codeOwnersApi,
      this.change
    );
  }

  async getBranchConfig() {
    return this.codeOwnersCacheApi.getBranchConfig();
  }

  async isCodeOwnerEnabled() {
    if (
      this.change.status === ChangeStatus.ABANDONED ||
      this.change.status === ChangeStatus.MERGED
    ) {
      return false;
    }
    const config = await this.getBranchConfig();
    return config && !config.disabled;
  }

  static getOwnerService(restApi: RestPluginApi, change: ChangeInfo) {
    if (!ownerService || ownerService.change !== change) {
      ownerService = new CodeOwnerService(restApi, change, {
        // Chrome has a limit of 6 connections per host name, and a max of 10 connections.
        maxConcurrentRequests: 6,
      });
      ownerService.prefetch();
    }
    return ownerService;
  }

  // Only used for tests
  static reset() {
    if (!ownerService) return;
    ownerService.reset();
    ownerService = undefined;
  }

  // Copied from https://cs.opensource.google/gerrit/gerrit/gerrit/+/master:polygerrit-ui/app/utils/path-list-util.ts;l=10;drc=57014d5ba3e0b48e3372e3aa3c67463cb6e56bca
  static specialFilePathCompare(a: string, b: string) {
    const aLastDotIndex = a.lastIndexOf('.');
    const aExt = a.substr(aLastDotIndex + 1);
    const aFile = a.substr(0, aLastDotIndex) || a;
  
    const bLastDotIndex = b.lastIndexOf('.');
    const bExt = b.substr(bLastDotIndex + 1);
    const bFile = b.substr(0, bLastDotIndex) || b;
  
    // Sort header files above others with the same base name.
    const headerExts = ['h', 'hh', 'hxx', 'hpp'];
    if (aFile.length > 0 && aFile === bFile) {
      if (headerExts.includes(aExt) && headerExts.includes(bExt)) {
        return a.localeCompare(b);
      }
      if (headerExts.includes(aExt)) {
        return -1;
      }
      if (headerExts.includes(bExt)) {
        return 1;
      }
    }
    return aFile.localeCompare(bFile) || a.localeCompare(b);
  }
}
