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
import {CodeOwnerService} from './code-owners-service.js';
import {ModelLoader} from './code-owners-model-loader.js';
import {CodeOwnersModel} from './code-owners-model.js';

/**
 * The CodeOwnersMixin adds several properties to a class and translates
 * 'property-changed' events from the model to the notifyPath calls.
 * This allows to use properties of the model in observers, calculated
 * properties and bindings.
 * It is guaranteed, that model and modelLoader are set only if change,
 * reporting and restApi properties are set.
 */
export const CodeOwnersModelMixin = Polymer.dedupingMixin(base => {
  return class extends base {
    static get properties() {
      return {
        change: Object,
        reporting: Object,
        restApi: Object,
        /**
         * The modelLoader allows an element to request a property
         * Typically should be used in _requirePropertiesAfterModelChanged
         * to ensure that all required model properties are loaded
         */
        modelLoader: Object,
        model: {
          type: Object,
          observer: '_modelChanged',
        },
      };
    }

    static get observers() {
      return ['onInputChanged(restApi, change, reporting)'];
    }

    onInputChanged(restApi, change, reporting) {
      if ([restApi, change, reporting].includes(undefined)) {
        this.model = undefined;
        this.modelLoader = undefined;
        return;
      }
      const ownerService = CodeOwnerService.getOwnerService(
          this.restApi,
          this.change
      );
      const model = CodeOwnersModel.getModel(change);
      this.modelLoader = new ModelLoader(ownerService, model);
      // Assign model after modelLoader, so modelLoader can be used in
      // the _requirePropertiesAfterModelChanged method
      this.model = model;
    }

    _modelChanged(newModel) {
      if (this.modelPropertyChangedUnsubscriber) {
        this.modelPropertyChangedUnsubscriber();
        this.modelPropertyChangedUnsubscriber = undefined;
      }
      if (!newModel) return;
      const propertyChangedListener = e => {
        this.notifyPath(`model.${e.detail.propertyName}`);
      };
      newModel.addEventListener('property-changed',
          propertyChangedListener);
      this.modelPropertyChangedUnsubscriber = () => {
        newModel.removeEventListener('property-changed',
            propertyChangedListener);
      };
      this.loadPropertiesAfterModelChanged();
    }

    loadPropertiesAfterModelChanged() {
    }
  };
});
