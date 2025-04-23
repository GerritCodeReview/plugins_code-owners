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
import {Subscription} from 'rxjs';
import {CodeOwnerService} from './code-owners-service';
import {ModelLoader} from './code-owners-model-loader';
import {
  CodeOwnersModel,
  OwnedPathsInfoOpt,
  PluginStatus,
  Status,
  SuggestionsState,
  SuggestionsType,
  UserRole,
} from './code-owners-model';
import {LitElement, PropertyValues} from 'lit';
import {property, state} from 'lit/decorators.js';
import {ChangeInfo} from '@gerritcodereview/typescript-api/rest-api';
import {ReportingPluginApi} from '@gerritcodereview/typescript-api/reporting';
import {RestPluginApi} from '@gerritcodereview/typescript-api/rest';
import {CodeOwnerBranchConfigInfo, FetchedFile} from './code-owners-api';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type Constructor<T> = new (...args: any[]) => T;

export interface CodeOwnersModelMixinInterface {
  change?: ChangeInfo;
  reporting?: ReportingPluginApi;
  restApi?: RestPluginApi;
  modelLoader?: ModelLoader;
  model?: CodeOwnersModel;

  pluginStatus?: PluginStatus;
  branchConfig?: CodeOwnerBranchConfigInfo;
  userRole?: UserRole;
  areAllFilesApproved?: boolean;
  status?: Status;
  showSuggestions?: boolean;
  allOwnersLoaded?: boolean;
  selectedSuggestionsType?: SuggestionsType;
  selectedSuggestionsFiles?: Array<FetchedFile>;
  selectedSuggestionsState?: SuggestionsState;
  selectedSuggestionsLoadProgress?: string;
  ownedPaths?: OwnedPathsInfoOpt;

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
    @property({type: Object})
    change?: ChangeInfo;

    @property({type: Object})
    reporting?: ReportingPluginApi;

    @property({type: Object})
    restApi?: RestPluginApi;

    @state()
    pluginStatus?: PluginStatus;

    @state()
    branchConfig?: CodeOwnerBranchConfigInfo;

    @state()
    userRole?: UserRole;

    @state()
    areAllFilesApproved?: boolean;

    @state()
    status?: Status;

    @property({type: Boolean, attribute: 'show-suggestions', reflect: true})
    showSuggestions?: boolean;

    @state()
    selectedSuggestionsType?: SuggestionsType;

    @state()
    selectedSuggestionsFiles?: Array<FetchedFile>;

    @state()
    selectedSuggestionsState?: SuggestionsState;

    @state()
    selectedSuggestionsLoadProgress?: string;

    @state()
    ownedPaths?: OwnedPathsInfoOpt;

    private model_?: CodeOwnersModel;

    private subscriptions: Array<Subscription> = [];

    modelLoader?: ModelLoader;

    get model() {
      return this.model_;
    }

    set model(model: CodeOwnersModel | undefined) {
      if (this.model_ === model) return;
      for (const s of this.subscriptions) {
        s.unsubscribe();
      }
      this.subscriptions = [];
      this.model_ = model;
      if (!model) return;
      this.subscriptions.push(
        model.state$.subscribe(s => {
          this.pluginStatus = s.pluginStatus;
        })
      );
      this.subscriptions.push(
        model.state$.subscribe(s => {
          this.branchConfig = s.branchConfig;
        })
      );
      this.subscriptions.push(
        model.state$.subscribe(s => {
          this.status = s.status;
        })
      );
      this.subscriptions.push(
        model.state$.subscribe(s => {
          this.userRole = s.userRole;
        })
      );
      this.subscriptions.push(
        model.state$.subscribe(s => {
          this.areAllFilesApproved = s.areAllFilesApproved;
        })
      );
      this.subscriptions.push(
        model.showSuggestions$.subscribe(s => {
          this.showSuggestions = s;
        })
      );
      this.subscriptions.push(
        model.selectedSuggestionsType$.subscribe(s => {
          this.selectedSuggestionsType = s;
        })
      );
      this.subscriptions.push(
        model.selectedSuggestionsFiles$.subscribe(f => {
          this.selectedSuggestionsFiles = f;
        })
      );
      this.subscriptions.push(
        model.selectedSuggestionsLoadProgress$.subscribe(p => {
          this.selectedSuggestionsLoadProgress = p;
        })
      );
      this.subscriptions.push(
        model.selectedSuggestionsState$.subscribe(s => {
          this.selectedSuggestionsState = s;
        })
      );
      this.subscriptions.push(
        model.ownedPaths$.subscribe(s => {
          this.ownedPaths = s;
        })
      );
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
