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

import {type ChangeInfo} from '@gerritcodereview/typescript-api/rest-api';
import {
  ChangeType,
  CodeOwnerBranchConfigInfo,
  FetchedFile,
  FileCodeOwnerStatusInfo,
  OwnerStatus,
} from './code-owners-api';

export enum SuggestionsState {
  NotLoaded = 'NotLoaded',
  Loaded = 'Loaded',
  Loading = 'Loading',
  LoadFailed = 'LoadFailed',
}

export enum PluginState {
  Enabled = 'Enabled',
  Disabled = 'Disabled',
  ServerConfigurationError = 'ServerConfigurationError',
  Failed = 'Failed',
}

export interface PluginStatus {
  state: PluginState;
  failedMessage?: string;
}

export function isPluginErrorState(state: PluginState) {
  return (
    state === PluginState.ServerConfigurationError ||
    state === PluginState.Failed
  );
}

export enum SuggestionsType {
  BEST_SUGGESTIONS = 'BEST_SUGGESTIONS',
  ALL_SUGGESTIONS = 'ALL_SUGGESTIONS',
}

/**
 * @enum
 */
export enum UserRole {
  ANONYMOUS = 'ANONYMOUS',
  AUTHOR = 'AUTHOR',
  CHANGE_OWNER = 'CHANGE_OWNER',
  REVIEWER = 'REVIEWER',
  CC = 'CC',
  REMOVED_REVIEWER = 'REMOVED_REVIEWER',
  OTHER = 'OTHER',
}

export interface Suggestion {
  files?: Array<FetchedFile>;
  state: SuggestionsState;
  loadProgress?: string;
}

export interface Status {
  patchsetNumber: number;
  enabled: boolean;
  codeOwnerStatusMap: Map<string, FileStatus>;
  rawStatuses: Array<FileCodeOwnerStatusInfo>;
  newerPatchsetUploaded: boolean;
}

export interface FileStatus {
  changeType: ChangeType;
  status: OwnerStatus;
  newPath?: string | null;
  oldPath?: string | null;
}

export const BestSuggestionsLimit = 5;
export const AllSuggestionsLimit = 1000;

let codeOwnersModel: CodeOwnersModel | undefined;

/**
 * Maintain the state of code-owners.
 * Raises 'model-property-changed' event when a property is changed.
 * The plugin shares the same model between all UI elements (if it is not,
 * the plugin can't maintain showSuggestions state across different UI elements).
 * UI elements use values from this model to display information
 * and listens for the model-property-changed event. To do so, UI elements
 * add CodeOwnersModelMixin, which is doing the listening and the translation
 * from model-property-changed event to Polymer property-changed-event. The
 * translation allows to use model properties in observables, bindings,
 * computed properties, etc...
 * The CodeOwnersModelLoader updates the model.
 *
 * It would be good to use RxJs Observable for implementing model properties.
 * However, RxJs library is imported by Gerrit and there is no
 * good way to reuse the same library in the plugin.
 */
export class CodeOwnersModel extends EventTarget {
  showSuggestions = false;

  selectedSuggestionsType = SuggestionsType.BEST_SUGGESTIONS;

  branchConfig?: CodeOwnerBranchConfigInfo;

  suggestionsByTypes: Map<SuggestionsType, Suggestion>;

  pluginStatus?: PluginStatus;

  userRole?: UserRole;

  areAllFilesApproved?: boolean;

  status?: Status;

  constructor(readonly change: ChangeInfo) {
    super();
    this.suggestionsByTypes = new Map();
    for (const suggestionType of Object.values(SuggestionsType)) {
      this.suggestionsByTypes.set(suggestionType, {
        files: undefined,
        state: SuggestionsState.NotLoaded,
        loadProgress: undefined,
      });
    }
  }

  get selectedSuggestions() {
    return this.suggestionsByTypes.get(this.selectedSuggestionsType);
  }

  setBranchConfig(config: CodeOwnerBranchConfigInfo) {
    if (this.branchConfig === config) return;
    this.branchConfig = config;
    this.firePropertyChanged('branchConfig');
  }

  setStatus(status: Status) {
    if (this.status === status) return;
    this.status = status;
    this.firePropertyChanged('status');
  }

  setUserRole(userRole: UserRole) {
    if (this.userRole === userRole) return;
    this.userRole = userRole;
    this.firePropertyChanged('userRole');
  }

  setAreAllFilesApproved(approved: boolean) {
    if (this.areAllFilesApproved === approved) return;
    this.areAllFilesApproved = approved;
    this.firePropertyChanged('areAllFilesApproved');
  }

  setSuggestionsFiles(
    suggestionsType: SuggestionsType,
    files: Array<FetchedFile>
  ) {
    const suggestions = this.suggestionsByTypes.get(suggestionsType);
    if (!suggestions) return;
    if (suggestions.files === files) return;
    suggestions.files = files;
    this._fireSuggestionsChanged(suggestionsType, 'files');
  }

  setSuggestionsState(
    suggestionsType: SuggestionsType,
    state: SuggestionsState
  ) {
    const suggestions = this.suggestionsByTypes.get(suggestionsType);
    if (!suggestions) return;
    if (suggestions.state === state) return;
    suggestions.state = state;
    this._fireSuggestionsChanged(suggestionsType, 'state');
  }

  setSuggestionsLoadProgress(
    suggestionsType: SuggestionsType,
    progress: string
  ) {
    const suggestions = this.suggestionsByTypes.get(suggestionsType);
    if (!suggestions) return;
    if (suggestions.loadProgress === progress) return;
    suggestions.loadProgress = progress;
    this._fireSuggestionsChanged(suggestionsType, 'loadProgress');
  }

  setSelectedSuggestionType(suggestionsType: SuggestionsType) {
    if (this.selectedSuggestionsType === suggestionsType) return;
    this.selectedSuggestionsType = suggestionsType;
    this.firePropertyChanged('selectedSuggestionsType');
    this.firePropertyChanged('selectedSuggestions');
  }

  setShowSuggestions(show: boolean) {
    if (this.showSuggestions === show) return;
    this.showSuggestions = show;
    this.firePropertyChanged('showSuggestions');
  }

  setPluginEnabled(enabled: boolean) {
    this._setPluginStatus({
      state: enabled ? PluginState.Enabled : PluginState.Disabled,
    });
  }

  setServerConfigurationError(failedMessage: string) {
    this._setPluginStatus({
      state: PluginState.ServerConfigurationError,
      failedMessage,
    });
  }

  setPluginFailed(failedMessage: string) {
    this._setPluginStatus({state: PluginState.Failed, failedMessage});
  }

  _setPluginStatus(status: PluginStatus) {
    if (this._arePluginStatusesEqual(this.pluginStatus, status)) return;
    this.pluginStatus = status;
    this.firePropertyChanged('pluginStatus');
  }

  _arePluginStatusesEqual(
    status1: PluginStatus | undefined,
    status2: PluginStatus | undefined
  ) {
    if (status1 === undefined || status2 === undefined) {
      return status1 === status2;
    }
    if (status1.state !== status2.state) return false;
    return isPluginErrorState(status1.state)
      ? status1.failedMessage === status2.failedMessage
      : true;
  }

  private firePropertyChanged(propertyName: string) {
    this.dispatchEvent(
      new CustomEvent('model-property-changed', {
        detail: {
          propertyName,
        },
      })
    );
  }

  _fireSuggestionsChanged(
    suggestionsType: SuggestionsType,
    propertyName: string
  ) {
    this.firePropertyChanged(
      `suggestionsByTypes.${suggestionsType}.${propertyName}`
    );
    if (suggestionsType === this.selectedSuggestionsType) {
      this.firePropertyChanged(`selectedSuggestions.${propertyName}`);
    }
  }

  static getModel(change: ChangeInfo) {
    if (!codeOwnersModel || codeOwnersModel.change !== change) {
      codeOwnersModel = new CodeOwnersModel(change);
    }
    return codeOwnersModel;
  }
}
