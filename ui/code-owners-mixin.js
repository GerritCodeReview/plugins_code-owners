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

export const CodeOwnersMixin = Polymer.dedupingMixin(base => {
  return class extends base {
    static get properties() {
      return {
        change: Object,
        reporting: Object,
        restApi: Object,
        ownersState: {
          type: Object,
          observer: '_ownersStateChanged',
        },
      };
    }

    static get observers() {
      return ['onInputChanged(restApi, change)'];
    }

    onInputChanged(restApi, change) {
      if ([restApi, change].includes(undefined)) return;
      this.ownerService = CodeOwnerService.getOwnerService(
          this.restApi,
          this.change
      );
      this.ownersState = this.ownerService.state;
    }

    _ownersStateChanged(newState) {
      if (this.statePropertyChangedUnsubscriber) {
        this.statePropertyChangedUnsubscriber();
      }
      if (!newState) return;
      this.statePropertyChangedUnsubscriber =
          newState.subscribePropertyChanged(propertyName => {
            this.notifyPath(`ownersState.${propertyName}`);
          });
      this._loadDataAfterStateChanged();
    }

    _loadDataAfterStateChanged() {
    }
  };
});
