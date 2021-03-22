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

import {SuggestionsState} from './code-owners-model.js';

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
  constructor(ownersService, ownersModel) {
    this.ownersService = ownersService;
    this.ownersModel = ownersModel;
  }

  async _loadProperty(propertyName, propertyLoader, propertySetter) {
    // Load property only if it was not already loaded
    if (this.ownersModel[propertyName] !== undefined) return;
    let newValue;
    try {
      newValue = await propertyLoader();
    } catch (e) {
      this.ownersModel.setPluginFailed(e.message);
      return;
    }
    // It is possible, that several requests is made in parallel.
    // Store only the first result and discard all other results.
    // (also, due to the CodeOwnersCacheApi all result must be identical)
    if (this.ownersModel[propertyName] !== undefined) return;
    propertySetter(newValue);
  }

  async loadBranchConfig() {
    await this._loadProperty('branchConfig',
        () => this.ownersService.getBranchConfig(),
        value => this.ownersModel.setBranchConfig(value)
    );
  }

  async loadStatus() {
    await this._loadProperty('status',
        () => this.ownersService.getStatus(),
        value => this.ownersModel.setStatus(value)
    );
  }

  async loadUserRole() {
    await this._loadProperty('userRole',
        () => this.ownersService.getLoggedInUserInitialRole(),
        value => this.ownersModel.setUserRole(value)
    );
  }

  async loadPluginStatus() {
    await this._loadProperty('pluginStatus',
        () => this.ownersService.isCodeOwnerEnabled(),
        value => this.ownersModel.setPluginEnabled(value)
    );
  }

  async loadAreAllFilesApproved() {
    await this._loadProperty('areAllFilesApproved',
        () => this.ownersService.areAllFilesApproved(),
        value => this.ownersModel.setAreAllFilesApproved(value)
    );
  }

  async loadSuggestions() {
    const suggestionsType = this.ownersModel.selectedSuggestionsType;
    // If a loading has been started already, do nothing
    if (this.ownersModel.suggestionsByTypes[suggestionsType].state
        !== SuggestionsState.NotLoaded) return;

    this.ownersModel.setSuggestionsState(suggestionsType,
        SuggestionsState.Loading);
    let suggestedOwners;
    try {
      suggestedOwners =
          await this.ownersService.getSuggestedOwners(suggestionsType);
    } catch (e) {
      this.ownersModel.setSuggestionsState(suggestionsType,
          SuggestionsState.LoadFailed);
      // The selectedSuggestionsType can be changed while getSuggestedOwners
      // is executed. The plugin should fail only if the selectedSuggestionsType
      // is the same.
      if (this.ownersModel.selectedSuggestionsType === suggestionsType) {
        this.ownersModel.setPluginFailed(e.message);
      }
      return;
    }
    this.ownersModel.setSuggestionsItems(suggestionsType,
        suggestedOwners.suggestions);
    this.ownersModel.setSuggestionsState(suggestionsType,
        SuggestionsState.Loaded);
  }

  async updateLoadSuggestionsProgress() {
    const suggestionsType = this.ownersModel.selectedSuggestionsType;
    let suggestedOwners;
    try {
      suggestedOwners =
          await this.ownersService.getSuggestedOwnersProgress(suggestionsType);
    } catch {
      // Ignore any error, keep progress unchanged.
      return;
    }
    this.ownersModel.setSuggestionsLoadProgress(suggestionsType,
        suggestedOwners.progress);
    this.ownersModel.setSuggestionsItems(suggestionsType,
        suggestedOwners.suggestions);
  }
}
