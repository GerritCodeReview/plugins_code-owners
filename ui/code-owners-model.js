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

export const SuggestionsState = {
  NotLoaded: 'NotLoaded',
  Loaded: 'Loaded',
  Loading: 'Loading',
  LoadFailed: 'LoadFailed',
};

export const PluginState = {
  Enabled: 'Enabled',
  Disabled: 'Disabled',
  ServerConfigurationError: 'ServerConfigurationError',
  Failed: 'Failed',
};

export const SuggestionsType = {
  BEST_SUGGESTIONS: 'BEST_SUGGESTIONS',
  ALL_SUGGESTIONS: 'ALL_SUGGESTIONS',
};

export const BestSuggestionsLimit = 5;
export const AllSuggestionsLimit = 1000;

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
  constructor(change) {
    super();
    this.change = change;
    this.branchConfig = undefined;
    this.status = undefined;
    this.userRole = undefined;
    this.isCodeOwnerEnabled = undefined;
    this.areAllFilesApproved = undefined;
    this.suggestionsByTypes = {};
    for (const suggestionType of Object.values(SuggestionsType)) {
      this.suggestionsByTypes[suggestionType] = {
        files: undefined,
        state: SuggestionsState.NotLoaded,
        loadProgress: undefined,
      };
    }
    this.selectedSuggestionsType = SuggestionsType.BEST_SUGGESTIONS;
    this.showSuggestions = false;
    this.pluginStatus = undefined;
  }

  get selectedSuggestions() {
    return this.suggestionsByTypes[this.selectedSuggestionsType];
  }

  setBranchConfig(config) {
    if (this.branchConfig === config) return;
    this.branchConfig = config;
    this._firePropertyChanged('branchConfig');
  }

  setStatus(status) {
    if (this.status === status) return;
    this.status = status;
    this._firePropertyChanged('status');
  }

  setUserRole(userRole) {
    if (this.userRole === userRole) return;
    this.userRole = userRole;
    this._firePropertyChanged('userRole');
  }

  setIsCodeOwnerEnabled(enabled) {
    if (this.isCodeOwnerEnabled === enabled) return;
    this.isCodeOwnerEnabled = enabled;
    this._firePropertyChanged('isCodeOwnerEnabled');
  }

  setAreAllFilesApproved(approved) {
    if (this.areAllFilesApproved === approved) return;
    this.areAllFilesApproved = approved;
    this._firePropertyChanged('areAllFilesApproved');
  }

  setSuggestionsFiles(suggestionsType, files) {
    const suggestions = this.suggestionsByTypes[suggestionsType];
    if (suggestions.files === files) return;
    suggestions.files = files;
    this._fireSuggestionsChanged(suggestionsType, 'files');
  }

  setSuggestionsState(suggestionsType, state) {
    const suggestions = this.suggestionsByTypes[suggestionsType];
    if (suggestions.state === state) return;
    suggestions.state = state;
    this._fireSuggestionsChanged(suggestionsType, 'state');
  }

  setSuggestionsLoadProgress(suggestionsType, progress) {
    const suggestions = this.suggestionsByTypes[suggestionsType];
    if (suggestions.loadProgress === progress) return;
    suggestions.loadProgress = progress;
    this._fireSuggestionsChanged(suggestionsType, 'loadProgress');
  }

  setSelectedSuggestionType(suggestionsType) {
    if (this.selectedSuggestionsType === suggestionsType) return;
    this.selectedSuggestionsType = suggestionsType;
    this._firePropertyChanged('selectedSuggestionsType');
    this._firePropertyChanged('selectedSuggestions');
  }

  setShowSuggestions(show) {
    if (this.showSuggestions === show) return;
    this.showSuggestions = show;
    this._firePropertyChanged('showSuggestions');
  }

  setPluginEnabled(enabled) {
    this._setPluginStatus({state: enabled ?
      PluginState.Enabled : PluginState.Disabled});
  }

  setServerConfigurationError(failedMessage) {
    this._setPluginStatus({state: PluginState.ServerConfigurationError,
      failedMessage});
  }

  setPluginFailed(failedMessage) {
    this._setPluginStatus({state: PluginState.Failed, failedMessage});
  }

  _setPluginStatus(status) {
    if (this._arePluginStatusesEqual(this.pluginStatus, status)) return;
    this.pluginStatus = status;
    this._firePropertyChanged('pluginStatus');
  }

  _arePluginStatusesEqual(status1, status2) {
    if (status1 === undefined || status2 === undefined) {
      return status1 === status2;
    }
    if (status1.state !== status2.state) return false;
    return (status1.state === PluginState.Failed ||
        status1.state === PluginState.ServerConfigurationError)?
      status1.failedMessage === status2.failedMessage :
      true;
  }

  _firePropertyChanged(propertyName) {
    this.dispatchEvent(new CustomEvent('model-property-changed', {
      detail: {
        propertyName,
      },
    }));
  }

  _fireSuggestionsChanged(suggestionsType, propertyName) {
    this._firePropertyChanged(
        `suggestionsByTypes.${suggestionsType}.${propertyName}`);
    if (suggestionsType === this.selectedSuggestionsType) {
      this._firePropertyChanged(`selectedSuggestions.${propertyName}`);
    }
  }

  static getModel(change) {
    if (!this.model || this.model.change !== change) {
      this.model = new CodeOwnersModel(change);
    }
    return this.model;
  }
}
