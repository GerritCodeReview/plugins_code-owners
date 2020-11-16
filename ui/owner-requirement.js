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
          --gr-button: {
            padding: 0px;
          }
        }
        p.loading {
          display: flex;
          align-content: center;
          align-items: center;
          justify-content: center;
        }
        .loadingSpin {
          display: inline-block;
          margin-right: var(--spacing-m);
          width: 18px;
          height: 18px;
        }
        gr-button {
          padding-left: var(--spacing-m);
        }
        a {
          text-decoration: none;
        }
        </style>
        <p class="loading" hidden="[[!_isLoading]]">
          <span class="loadingSpin"></span>
          Loading status ...
        </p>
        <template is="dom-if" if="[[!_isLoading]]">
          <span>[[_computeStatusText(_statusCount, _isOverriden)]]</span>
          <template is="dom-if" if="[[_overrideInfoUrl]]">
            <a on-click="_reportDocClick" href="[[_overrideInfoUrl]]" target="_blank">
              <iron-icon icon="gr-icons:help-outline" title="Documentation for overriding code owners"></iron-icon>
            </a>
          </template>
          <template is="dom-if" if="[[!_allApproved]]">
            <gr-button link on-click="_openReplyDialog">
            Suggest owners
          </gr-button>
          </template>
        </template>
      `;
  }

  static get properties() {
    return {
      change: Object,
      reporting: Object,
      restApi: Object,

      ownerService: Object,
      ownersState: {
        type: Object,
        observer: '_ownersStateChanged',
      },

      _statusCount: Object,
      _isLoading: {
        type: Boolean,
        computed: '_computeIsLoading(ownersState.branchConfig, ' +
            'ownersState.status)',
      },
      _allApproved: {
        type: Boolean,
        computed: '_computeAllApproved(_statusCount)',
      },
      _isOverriden: {
        type: Boolean,
        computed: '_computeIsOverriden(ownersState.branchConfig)',
      },
      _overrideInfoUrl: {
        type: String,
        computed: '_computeOverrideInfoUrl(ownersState.branchConfig)',
      },
    };
  }

  static get observers() {
    return [
      'onInputChanged(restApi, change, reporting)',
      '_onStatusChanged(ownersState.status)',
    ];
  }

  _computeIsLoading(branchConfig, status) {
    return !branchConfig || !status;
  }

  _requestStatusUpdate() {
    this.reporting.reportLifeCycle('owners-submit-requirement-summary-start');
    this.ownerService.ensureStatusLoaded();
  }

  _onStatusChanged(status) {
    if (!status) {
      this._statusCount = 0;
      return;
    }
    const rawStatuses = status.rawStatuses;
    this._statusCount = this._getStatusCount(rawStatuses);
    this.reportLifeCycleWithRole(
        'owners-submit-requirement-summary-shown', {...this._statusCount});
  }

  reportLifeCycleWithRole(name, data) {}

  _requestBranchConfigUpdate() {
    this.ownerService.ensureBranchConfigLoaded();
  }

  _computeOverrideInfoUrl(branchConfig) {
    if (!branchConfig) {
      return '';
    }
    return branchConfig.general && branchConfig.general.override_info_url
      ? branchConfig.general.override_info_url : '';
  }

  _computeIsOverriden(branchConfig) {
    if (!branchConfig || !branchConfig['override_approval']) {
      // no override label configured
      return false;
    }

    const overridenLabel = branchConfig['override_approval'].label;
    const overridenValue = branchConfig['override_approval'].value;

    if (this.change.labels[overridenLabel]) {
      const votes = this.change.labels[overridenLabel].all || [];
      if (votes.find(v => `${v.value}` === `${overridenValue}`)) {
        return true;
      }
    }

    // otherwise always reset it to false
    return false;
  }

  _computeAllApproved(statusCount) {
    return statusCount.missing === 0
            && statusCount.pending === 0;
  }

  _getStatusCount(rawStatuses) {
    return rawStatuses
        .reduce((prev, cur) => {
          const oldPathStatus = cur.old_path_status;
          const newPathStatus = cur.new_path_status;
          if (newPathStatus && this._isMissing(newPathStatus.status)) {
            prev.missing ++;
          } else if (newPathStatus && this._isPending(newPathStatus.status)) {
            prev.pending ++;
          } else if (oldPathStatus) {
            // check oldPath if newPath approved or the file is deleted
            if (this._isMissing(oldPathStatus.status)) {
              prev.missing ++;
            } else if (this._isPending(oldPathStatus.status)) {
              prev.pending ++;
            }
          } else {
            prev.approved ++;
          }
          return prev;
        }, {missing: 0, pending: 0, approved: 0});
  }

  _computeStatusText(statusCount, isOverriden) {
    if (statusCount === undefined || isOverriden === undefined) return '';
    const statusText = [];
    if (statusCount.missing) {
      statusText.push(`${statusCount.missing} missing`);
    }

    if (statusCount.pending) {
      statusText.push(`${statusCount.pending} pending`);
    }

    if (!statusText.length) {
      statusText.push(isOverriden ? 'Approved (Owners-Override)' : 'Approved');
    }

    return statusText.join(', ');
  }

  _isMissing(status) {
    return status === OwnerStatus.INSUFFICIENT_REVIEWERS;
  }

  _isPending(status) {
    return status === OwnerStatus.PENDING;
  }

  onInputChanged(restApi, change, reporting) {
    if ([restApi, change, reporting].includes(undefined)) return;
    this.ownerService = CodeOwnerService.getOwnerService(this.restApi, change);
    this.ownersState = this.ownerService.state;
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
    this.ownerService.getLoggedInUserInitialRole().then(role => {
      this.reporting.reportInteraction(
          'suggest-owners-from-submit-requirement', {user_role: role});
    });
  }

  _ownersStateChanged(newState) {
    if (this.statePropertyChangedUnsubscriber) {
      this.oldStateUnsubscriber();
    }
    if (!newState) return;
    this.statePropertyChangedUnsubscriber =
        newState.subscribePropertyChanged(propertyName => {
          this.notifyPath(`ownersState.${propertyName}`);
        });
    this._requestStatusUpdate();
    this._requestBranchConfigUpdate();
  }
}

customElements.define(OwnerRequirementValue.is, OwnerRequirementValue);
