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

import {BehaviorSubject, Observable} from 'rxjs';
import {
  AccountInfo,
  type ChangeInfo,
} from '@gerritcodereview/typescript-api/rest-api';
import {
  ChangeType,
  CodeOwnerBranchConfigInfo,
  FetchedFile,
  FileCodeOwnerStatusInfo,
  OwnerStatus,
  OwnedPathsInfo,
} from './code-owners-api';
import {deepEqual} from './deep-util';
import {select} from './observable-util';

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

export interface OwnedPathsInfoOpt {
  oldPaths: Set<string>;
  newPaths: Set<string>;
  oldPathOwners: Map<string, Array<AccountInfo>>;
  newPathOwners: Map<string, Array<AccountInfo>>;
}

export interface CodeOwnersState {
  showSuggestions: boolean;
  selectedSuggestionsType: SuggestionsType;
  suggestionsByTypes: Map<SuggestionsType, Suggestion>;
  pluginStatus?: PluginStatus;
  branchConfig?: CodeOwnerBranchConfigInfo;
  ownedPaths?: OwnedPathsInfoOpt;
  userRole?: UserRole;
  areAllFilesApproved?: boolean;
  status?: Status;
}

/**
 * Maintain the state of code-owners.
 * Raises 'model-property-changed' event when a property is changed.
 * The plugin shares the same model between all UI elements (if it is not,
 * the plugin can't maintain showSuggestions state across different UI
 * elements). UI elements use values from this model to display information and
 * listens for the model-property-changed event. To do so, UI elements add
 * CodeOwnersModelMixin, which is doing the listening and the translation from
 * model-property-changed event to Polymer property-changed-event. The
 * translation allows to use model properties in observables, bindings,
 * computed properties, etc...
 * The CodeOwnersModelLoader updates the model.
 *
 * It would be good to use RxJs Observable for implementing model properties.
 * However, RxJs library is imported by Gerrit and there is no
 * good way to reuse the same library in the plugin.
 */
export class CodeOwnersModel extends EventTarget {
  private subject$: BehaviorSubject<CodeOwnersState> = new BehaviorSubject({
    showSuggestions: false,
    selectedSuggestionsType: SuggestionsType.BEST_SUGGESTIONS,
    suggestionsByTypes: new Map(),
  } as CodeOwnersState);

  public state$: Observable<CodeOwnersState> = this.subject$.asObservable();

  public showSuggestions$ = select(this.state$, state => state.showSuggestions);

  public selectedSuggestionsType$ = select(
    this.state$,
    state => state.selectedSuggestionsType
  );

  public selectedSuggestionsFiles$ = select(
    this.state$,
    state => state.suggestionsByTypes.get(state.selectedSuggestionsType)?.files
  );

  public selectedSuggestionsState$ = select(
    this.state$,
    state => state.suggestionsByTypes.get(state.selectedSuggestionsType)!.state
  );

  public selectedSuggestionsLoadProgress$ = select(
    this.state$,
    state =>
      state.suggestionsByTypes.get(state.selectedSuggestionsType)!.loadProgress
  );

  public ownedPaths$ = select(this.state$, state => state.ownedPaths);

  constructor(readonly change: ChangeInfo) {
    super();
    // Side-effectful initialization of state$ is ok because this happens
    // during construction time.
    const current = this.subject$.getValue();
    for (const suggestionType of Object.values(SuggestionsType)) {
      current.suggestionsByTypes.set(suggestionType, {
        files: undefined,
        state: SuggestionsState.NotLoaded,
        loadProgress: undefined,
      });
    }
  }

  get state() {
    return this.subject$.getValue();
  }

  get selectedSuggestions() {
    const current = this.subject$.getValue();
    return current.suggestionsByTypes.get(current.selectedSuggestionsType);
  }

  getSuggestionsByType(suggestionsType: SuggestionsType) {
    const current = this.subject$.getValue();
    return Object.freeze(current.suggestionsByTypes.get(suggestionsType)!);
  }

  private setState(state: CodeOwnersState) {
    this.subject$.next(Object.freeze(state));
  }

  setBranchConfig(branchConfig: CodeOwnerBranchConfigInfo) {
    const current = this.subject$.getValue();
    if (current.branchConfig === branchConfig) return;
    this.setState({...current, branchConfig});
  }

  setStatus(status: Status) {
    const current = this.subject$.getValue();
    if (current.status === status) return;
    this.setState({...current, status});
  }

  setUserRole(userRole: UserRole) {
    const current = this.subject$.getValue();
    if (current.userRole === userRole) return;
    this.setState({...current, userRole});
  }

  setAreAllFilesApproved(areAllFilesApproved: boolean) {
    const current = this.subject$.getValue();
    if (current.areAllFilesApproved === areAllFilesApproved) return;
    this.setState({...current, areAllFilesApproved});
  }

  setShowSuggestions(showSuggestions: boolean) {
    const current = this.subject$.getValue();
    if (current.showSuggestions === showSuggestions) return;
    this.setState({...current, showSuggestions});
  }

  setSelectedSuggestionType(selectedSuggestionsType: SuggestionsType) {
    const current = this.subject$.getValue();
    if (current.selectedSuggestionsType === selectedSuggestionsType) return;
    this.setState({...current, selectedSuggestionsType});
  }

  _setPluginStatus(pluginStatus: PluginStatus) {
    const current = this.subject$.getValue();
    if (this._arePluginStatusesEqual(current.pluginStatus, pluginStatus))
      return;
    this.setState({...current, pluginStatus});
  }

  private updateSuggestion(
    suggestionsType: SuggestionsType,
    suggestionsUpdate: Partial<Suggestion>
  ) {
    const current = this.subject$.getValue();
    const nextState = {
      ...current,
      suggestionsByTypes: new Map(current.suggestionsByTypes),
    };
    nextState.suggestionsByTypes.set(suggestionsType, {
      ...nextState.suggestionsByTypes.get(suggestionsType)!,
      ...suggestionsUpdate,
    });
    this.setState(nextState);
  }

  setSuggestionsFiles(
    suggestionsType: SuggestionsType,
    files: Array<FetchedFile>
  ) {
    const current = this.subject$.getValue();
    const suggestions = current.suggestionsByTypes.get(suggestionsType);
    if (!suggestions) return;
    if (suggestions.files === files) return;
    if (deepEqual(suggestions.files, files)) return;
    this.updateSuggestion(suggestionsType, {files});
  }

  setSuggestionsState(
    suggestionsType: SuggestionsType,
    state: SuggestionsState
  ) {
    const current = this.subject$.getValue();
    const suggestions = current.suggestionsByTypes.get(suggestionsType);
    if (!suggestions) return;
    if (suggestions.state === state) return;
    this.updateSuggestion(suggestionsType, {state});
  }

  setSuggestionsLoadProgress(
    suggestionsType: SuggestionsType,
    loadProgress: string
  ) {
    const current = this.subject$.getValue();
    const suggestions = current.suggestionsByTypes.get(suggestionsType);
    if (!suggestions) return;
    if (suggestions.loadProgress === loadProgress) return;
    this.updateSuggestion(suggestionsType, {loadProgress});
  }

  setOwnedPaths(ownedPathsInfo: OwnedPathsInfo | undefined) {
    const current = this.subject$.getValue();
    const ownedPaths = {
      oldPaths: new Set<string>(),
      newPaths: new Set<string>(),
      oldPathOwners: new Map<string, Array<AccountInfo>>(),
      newPathOwners: new Map<string, Array<AccountInfo>>(),
    };
    for (const changed_file of ownedPathsInfo?.owned_changed_files ?? []) {
      if (changed_file.old_path?.owned)
        ownedPaths.oldPaths.add(changed_file.old_path.path);
      if (changed_file.new_path?.owned)
        ownedPaths.newPaths.add(changed_file.new_path.path);
      if (changed_file.old_path?.owners) {
        ownedPaths.oldPathOwners.set(
          changed_file.old_path.path,
          (
            ownedPaths.oldPathOwners.get(changed_file.old_path.path) ?? []
          ).concat(changed_file.old_path.owners)
        );
      }
      if (changed_file.new_path?.owners) {
        ownedPaths.newPathOwners.set(
          changed_file.new_path.path,
          (
            ownedPaths.newPathOwners.get(changed_file.new_path.path) ?? []
          ).concat(changed_file.new_path.owners)
        );
      }
    }
    const nextState = {
      ...current,
      ownedPaths,
    };
    this.setState(nextState);
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

  static getModel(change: ChangeInfo) {
    if (!codeOwnersModel || codeOwnersModel.change !== change) {
      codeOwnersModel = new CodeOwnersModel(change);
    }
    return codeOwnersModel;
  }
}
