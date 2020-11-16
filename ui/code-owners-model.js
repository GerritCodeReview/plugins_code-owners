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
  Failed: 'Failed',
};

/**
 * Maintain the state of code-owners.
 * Raises 'property-changed' event when a property is changed.
 * The plugin shares the same model between all UI elements (if it is not,
 * the plugin can't maintain showSuggestions state across different UI elements).
 * UI elements use values from this model to display information
 * and listens for the property-changed event.
 *
 * The CodeOwnersModelLoader updates the model.
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
    this.suggestions = undefined;
    this.suggestionsState = SuggestionsState.NotLoaded;
    this.suggestionsLoadProgress = undefined;
    this.showSuggestions = false;
    this.pluginStatus = undefined;
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

  setSuggestions(suggestions) {
    if (this.suggestions === suggestions) return;
    this.suggestions = suggestions;
    this._firePropertyChanged('suggestions');
  }

  setSuggestionsState(state) {
    if (this.suggestionsState === state) return;
    this.suggestionsState = state;
    this._firePropertyChanged('suggestionsState');
  }

  setSuggestionsLoadProgress(progress) {
    if (this.suggestionsLoadProgress === progress) return;
    this.suggestionsLoadProgress = progress;
    this._firePropertyChanged('suggestionsLoadProgress');
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
    return status1.state === PluginState.Failed ?
      status1.failedMessage === status2.failedMessage :
      true;
  }

  _firePropertyChanged(propertyName) {
    this.dispatchEvent(new CustomEvent('property-changed', {
      detail: {
        propertyName,
      },
    }));
  }

  static getModel(change) {
    if (!this.model || this.model.change !== change) {
      this.model = new CodeOwnersModel(change);
    }
    return this.model;
  }
}
