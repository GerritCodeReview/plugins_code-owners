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

import {CodeOwnerService, OwnerStatus} from './code-owners-service.js';
import {ownerState} from './owner-ui-state.js';

/**
 * Owner requirement control for `submit-requirement-item-code-owners` endpoint.
 *
 * This will show the status and suggest owners button next to
 * the code-owners submit requirement.
 */
export class OwnerRequirementValue extends Polymer.Element {
  static get is() {
    return 'owner-requirement-value';
  }

  static get template() {
    return Polymer.html`
        <style include="shared-styles">
        :host {
          color: var(--deemphasized-text-color);

          --gr-button: {
            padding: 0px;
          }
        }
        p.loading {
          text-align: center;
        }
        .loadingSpin {
          display: inline-block;
        }
        gr-button {
          padding-left: var(--spacing-m);
        }
        </style>
        <p class="loading" hidden="[[!isLoading]]">
          <span class="loadingSpin"></span>
          loading...
        </p>
        <template is="dom-if" if="[[!isLoading]]">
          <span>[[statusText]]</span>
          <gr-button link on-click="_openReplyDialog">Suggest owners</gr-button>
        </template>
      `;
  }

  static get properties() {
    return {
      change: Object,
      statusText: String,
      restApi: Object,
      ownerService: Object,
      isLoading: Boolean,
    };
  }

  static get observers() {
    return [
      'onInputChanged(restApi, change)',
    ];
  }

  _updateStatus(ownerService) {
    this.isLoading = true;
    return ownerService.getStatus()
        .then(({rawStatuses}) => {
          const statusText = [];
          const statusCount = this._computeStatusCount(rawStatuses);
          if (statusCount.missing) {
            statusText.push(`${statusCount.missing} missing`);
          }

          if (statusCount.pending) {
            statusText.push(`${statusCount.pending} pending`);
          }

          if (!statusText.length) {
            statusText.push('approved');
          }

          this.statusText = statusText.join(', ');
        })
        .finally(() => {
          this.isLoading = false;
        });
  }

  _computeStatusCount(rawStatuses) {
    return rawStatuses
        .reduce((prev, cur) => {
          const oldPathStatus = cur.old_path_status;
          const newPathStatus = cur.new_path_status;
          if (this._isMissing(newPathStatus.code_owner_status)) {
            prev.missing ++;
          } else if (this._isPending(newPathStatus.code_owner_status)) {
            prev.pending ++;
          } else if (oldPathStatus) {
            // check oldPath if newPath approved
            if (this._isMissing(oldPathStatus.code_owner_status)) {
              prev.missing ++;
            } else if (this._isPending(oldPathStatus.code_owner_status)) {
              prev.pending ++;
            }
          }
          return prev;
        }, {missing: 0, pending: 0});
  }

  _isMissing(status) {
    return status === OwnerStatus.INSUFFICIENT_REVIEWERS;
  }

  _isPending(status) {
    return status === OwnerStatus.PENDING;
  }

  onInputChanged(restApi, change) {
    if ([restApi, change].includes(undefined)) return;
    this.ownerService = CodeOwnerService.getOwnerService(this.restApi, change);
    this._updateStatus(this.ownerService);
  }

  _openReplyDialog() {
    this.dispatchEvent(
        new CustomEvent('open-reply-dialog', {
          detail: {},
          composed: true,
          bubbles: true,
        })
    );
    ownerState.expandSuggestion = true;
  }
}

customElements.define(OwnerRequirementValue.is, OwnerRequirementValue);