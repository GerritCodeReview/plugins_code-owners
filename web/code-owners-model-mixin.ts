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
import {CodeOwnerService} from './code-owners-service';
import {ModelLoader} from './code-owners-model-loader';
import {CodeOwnersModel} from './code-owners-model';
import {LitElement, PropertyValues} from 'lit';
import {property} from 'lit/decorators';
import {ChangeInfo} from '@gerritcodereview/typescript-api/rest-api';
import {ReportingPluginApi} from '@gerritcodereview/typescript-api/reporting';
import {RestPluginApi} from '@gerritcodereview/typescript-api/rest';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type Constructor<T> = new (...args: any[]) => T;

export interface CodeOwnersModelMixinInterface {
  change?: ChangeInfo;
  reporting?: ReportingPluginApi;
  restApi?: RestPluginApi;
  model?: CodeOwnersModel;
  modelLoader?: ModelLoader;

  loadPropertiesAfterModelChanged(): void;
}
/**
 * The CodeOwnersMixin adds several properties to a class and translates
 * 'model-property-changed' events from the model to the notifyPath calls.
 * This allows to use properties of the model in observers, calculated
 * properties and bindings.
 * It is guaranteed, that model and modelLoader are set only if change,
 * reporting and restApi properties are set.
 */

export const CodeOwnersModelMixin = <T extends Constructor<LitElement>>(
  superClass: T
) => {
  /**
   * @lit
   * @mixinClass
   */
  class Mixin extends superClass {
    private modelPropertyChangedUnsubscriber?: () => void;

    @property({type: Object})
    change?: ChangeInfo;

    @property({type: Object})
    reporting?: ReportingPluginApi;

    @property({type: Object})
    restApi?: RestPluginApi;

    private model_?: CodeOwnersModel;

    modelLoader?: ModelLoader;

    get model() {
      return this.model_;
    }

    set model(model: CodeOwnersModel | undefined) {
      if (this.model_ === model) return;
      if (this.modelPropertyChangedUnsubscriber) {
        this.modelPropertyChangedUnsubscriber();
        this.modelPropertyChangedUnsubscriber = undefined;
      }
      this.model_ = model;
      if (!model) return;
      const propertyChangedListener = (e: unknown) => {
        console.error(e);
        this.requestUpdate('model');
      };
      model.addEventListener('model-property-changed', propertyChangedListener);
      this.modelPropertyChangedUnsubscriber = () => {
        model.removeEventListener(
          'model-property-changed',
          propertyChangedListener
        );
      };
      this.loadPropertiesAfterModelChanged();
    }

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    constructor(...args: any[]) {
      super(...args);
    }

    protected override willUpdate(changedProperties: PropertyValues): void {
      super.willUpdate(changedProperties);

      if (
        changedProperties.has('change') ||
        changedProperties.has('restApi') ||
        changedProperties.has('reporting')
      ) {
        if (!this.restApi || !this.change || !this.reporting) {
          this.model = undefined;
          this.modelLoader = undefined;
          return;
        }
        const ownerService = CodeOwnerService.getOwnerService(
          this.restApi,
          this.change
        );
        const model = CodeOwnersModel.getModel(this.change);
        this.modelLoader = new ModelLoader(ownerService, model);
        // Assign model after modelLoader, so modelLoader can be used in
        // the loadPropertiesAfterModelChanged method
        this.model = model;
      }
    }

    protected loadPropertiesAfterModelChanged() {}
  }

  return Mixin as unknown as T & Constructor<CodeOwnersModelMixinInterface>;
};
