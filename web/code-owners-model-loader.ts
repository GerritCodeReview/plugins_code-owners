/**
 * @license
 * Copyright (
 * ) 2020 The Android Open Source Project
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
  CodeOwnersModel,
  CodeOwnersState,
  SuggestionsState,
  SuggestionsType,
} from './code-owners-model.js';
import {ServerConfigurationError} from './code-owners-api.js';
import {CodeOwnerService} from './code-owners-service.js';

/**
 * ModelLoader provides a method for loading data into the model.
 * It is a bridge between an ownersModel and an ownersService.
 * When UI elements depends on a model property XXX, the element should
 * observe (or bind to) the property and call modelLoader.loadXXX method
 * to load the value.
 * It is recommended to use CodeOwnersModelMixin and call all load... methods
 * in the loadPropertiesAfterModelChanged method
 */
export class ModelLoader {
  private activeLoadSuggestionType?: SuggestionsType;

  constructor(
    private readonly ownersService: CodeOwnerService,
    private readonly ownersModel: CodeOwnersModel
  ) {}

  async _loadProperty<K extends keyof CodeOwnersState, T>(
    propertyName: K,
    propertyLoader: () => Promise<T>,
    propertySetter: (value: T) => void
  ) {
    if (this.ownersModel.state[propertyName] !== undefined) return;
    let newValue;
    try {
      newValue = await propertyLoader();
    } catch (e) {
      if (e instanceof ServerConfigurationError) {
        this.ownersModel.setServerConfigurationError(e.message);
        return;
      }
      console.error(e);
      this.ownersModel.setPluginFailed((e as Error).message);
      return;
    }
    // It is possible, that several requests is made in parallel.
    // Store only the first result and discard all other results.
    // (also, due to the CodeOwnersCacheApi all result must be identical)
    if (this.ownersModel.state[propertyName] !== undefined) return;
    propertySetter(newValue);
  }

  async loadBranchConfig() {
    await this._loadProperty(
      'branchConfig',
      () => this.ownersService.getBranchConfig(),
      value => this.ownersModel.setBranchConfig(value)
    );
  }

  async loadStatus() {
    await this._loadProperty(
      'status',
      () => this.ownersService.getStatus(),
      value => this.ownersModel.setStatus(value)
    );
  }

  async loadOwnedPaths() {
    await this._loadProperty(
      'ownedPaths',
      () => this.ownersService.getOwnedPaths(),
      paths => this.ownersModel.setOwnedPaths(paths)
    );
  }

  async loadUserRole() {
    await this._loadProperty(
      'userRole',
      () => this.ownersService.getLoggedInUserInitialRole(),
      value => this.ownersModel.setUserRole(value)
    );
  }

  async loadPluginStatus() {
    await this._loadProperty(
      'pluginStatus',
      () => this.ownersService.isCodeOwnerEnabled(),
      value => this.ownersModel.setPluginEnabled(value)
    );
  }

  async loadAreAllFilesApproved() {
    await this._loadProperty(
      'areAllFilesApproved',
      () => this.ownersService.areAllFilesApproved(),
      value => this.ownersModel.setAreAllFilesApproved(value)
    );
  }

  async loadSuggestions(suggestionsType: SuggestionsType) {
    // NOTE: This line is necessary to avoid an infinite loop with
    // SuggestOwners.onShowSuggestionsTypeChanged
    if (this.activeLoadSuggestionType === suggestionsType) return;
    this.pauseActiveSuggestedOwnersLoading();
    this.activeLoadSuggestionType = suggestionsType;
    if (
      this.ownersModel.getSuggestionsByType(suggestionsType)!.state ===
      SuggestionsState.Loading
    ) {
      this.ownersService.resumeSuggestedOwnersLoading(suggestionsType);
      return;
    }

    // If a loading has been started already, do nothing
    if (
      this.ownersModel.getSuggestionsByType(suggestionsType)!.state !==
      SuggestionsState.NotLoaded
    )
      return;

    this.ownersModel.setSuggestionsState(
      suggestionsType,
      SuggestionsState.Loading
    );
    let suggestedOwners;
    try {
      suggestedOwners = await this.ownersService.getSuggestedOwners(
        suggestionsType
      );
    } catch (e) {
      console.error(e);
      this.ownersModel.setSuggestionsState(
        suggestionsType,
        SuggestionsState.LoadFailed
      );
      // The selectedSuggestionsType can be changed while getSuggestedOwners
      // is executed. The plugin should fail only if the selectedSuggestionsType
      // is the same.
      if (this.ownersModel.state.selectedSuggestionsType === suggestionsType) {
        this.ownersModel.setPluginFailed((e as Error).message);
      }
      return;
    }
    this.ownersModel.setSuggestionsFiles(
      suggestionsType,
      suggestedOwners.files
    );
    this.ownersModel.setSuggestionsState(
      suggestionsType,
      SuggestionsState.Loaded
    );
  }

  pauseActiveSuggestedOwnersLoading() {
    if (!this.activeLoadSuggestionType) return;
    this.ownersService.pauseSuggestedOwnersLoading(
      this.activeLoadSuggestionType
    );
  }

  async updateLoadSelectedSuggestionsProgress() {
    const suggestionsType = this.ownersModel.state.selectedSuggestionsType;
    let suggestedOwners;
    try {
      suggestedOwners = await this.ownersService.getSuggestedOwnersProgress(
        suggestionsType
      );
    } catch {
      // Ignore any error, keep progress unchanged.
      return;
    }
    this.ownersModel.setSuggestionsLoadProgress(
      suggestionsType,
      suggestedOwners.progress
    );
    this.ownersModel.setSuggestionsFiles(
      suggestionsType,
      suggestedOwners.files
    );
  }
}
