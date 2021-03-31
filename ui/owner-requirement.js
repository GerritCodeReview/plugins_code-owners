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

import {OwnerStatus} from './code-owners-fetcher.js';
import {CodeOwnersModelMixin} from './code-owners-model-mixin.js';
import {showPluginFailedMessage} from './code-owners-banner.js';
import {PluginState} from './code-owners-model.js';

/**
 * Owner requirement control for `submit-requirement-item-code-owners` endpoint.
 *
 * This will show the status and suggest owners button next to
 * the code-owners submit requirement.
 */
export class OwnerRequirementValue extends
  CodeOwnersModelMixin(Polymer.Element) {
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
          <template is="dom-if" if="[[!_pluginFailed(model.pluginStatus)]]">
            <template is="dom-if" if="[[!model.branchConfig.no_code_owners_defined]]">              
              <span>[[_computeStatusText(_statusCount, _isOverriden)]]</span>
              <template is="dom-if" if="[[_overrideInfoUrl]]">
                <a on-click="_reportDocClick" href="[[_overrideInfoUrl]]"
                  target="_blank">
                  <iron-icon icon="gr-icons:help-outline"
                    title="Documentation for overriding code owners"></iron-icon>
                </a>
              </template>
              <gr-button link on-click="_openReplyDialog">
                [[_getSuggestOwnersText(_statusCount)]]
              </gr-button>
            </template>
            <template is="dom-if" if="[[model.branchConfig.no_code_owners_defined]]">                
              <span>No code-owners file</span>
              <a href="https://gerrit.googlesource.com/plugins/code-owners/+/master/resources/Documentation/user-guide.md#how-to-submit-changes-with-files-that-have-no-code-owners" target="_blank">
                <iron-icon icon="gr-icons:help-outline"
                  title="Documentation about submitting changes with files that have no code owners?"></iron-icon>
              </a>
            </template>
          </template>
          <template is="dom-if" if="[[_pluginFailed(model.pluginStatus)]]">
            <span>Code-owners plugin has failed</span>
            <gr-button link on-click="_showFailDetails">
              Details
            </gr-button>
          </template>
        </template>
      `;
  }

  static get properties() {
    return {
      _statusCount: Object,
      _isLoading: {
        type: Boolean,
        computed: '_computeIsLoading(model.branchConfig, model.status, '
            + 'model.userRole, model.pluginStatus)',
      },
      _isOverriden: {
        type: Boolean,
        computed: '_computeIsOverriden(change, model.branchConfig)',
      },
      _overrideInfoUrl: {
        type: String,
        computed: '_computeOverrideInfoUrl(model.branchConfig)',
      },
    };
  }

  static get observers() {
    return [
      '_onStatusChanged(model.status, model.userRole)',
    ];
  }

  loadPropertiesAfterModelChanged() {
    super.loadPropertiesAfterModelChanged();
    this.reporting.reportLifeCycle('owners-submit-requirement-summary-start');
    this.modelLoader.loadBranchConfig();
    this.modelLoader.loadStatus();
    this.modelLoader.loadUserRole();
  }

  _computeIsLoading(branchConfig, status, userRole, pluginStatus) {
    if (this._pluginFailed(pluginStatus)) {
      return false;
    }
    return !branchConfig || !status || !userRole;
  }

  _pluginFailed(pluginStatus) {
    return pluginStatus && (pluginStatus.state === PluginState.Failed ||
      pluginStatus.state === PluginState.ServerConfigurationError);
  }

  _onStatusChanged(status, userRole) {
    if (!status || !userRole) {
      this._statusCount = undefined;
      return;
    }
    const rawStatuses = status.rawStatuses;
    this._statusCount = this._getStatusCount(rawStatuses);
    this.reporting.reportLifeCycle('owners-submit-requirement-summary-shown',
        {...this._statusCount, user_role: userRole});
  }

  _computeOverrideInfoUrl(branchConfig) {
    if (!branchConfig) {
      return '';
    }
    return branchConfig.general && branchConfig.general.override_info_url
      ? branchConfig.general.override_info_url : '';
  }

  _computeIsOverriden(change, branchConfig) {
    if (!change || !branchConfig || !branchConfig['override_approval']) {
      // no override labels configured
      return false;
    }

    for (const requiredApprovalInfo of branchConfig['override_approval']) {
      const overridenLabel = requiredApprovalInfo.label;
      const overridenValue = Number(requiredApprovalInfo.value);
      if (isNaN(overridenValue)) continue;

      if (this.change.labels[overridenLabel]) {
        const votes = change.labels[overridenLabel].all || [];
        if (votes.find(v => Number(v.value) >= overridenValue)) {
          return true;
        }
      }
    }

    // otherwise always reset it to false
    return false;
  }

  _getSuggestOwnersText(statusCount) {
    return statusCount && statusCount.missing === 0 ?
      'Add owners' : 'Suggest owners';
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

  _openReplyDialog() {
    this.model.setShowSuggestions(true);
    this.dispatchEvent(
        new CustomEvent('open-reply-dialog', {
          detail: {},
          composed: true,
          bubbles: true,
        })
    );
    this.reporting.reportInteraction('suggest-owners-from-submit-requirement',
        {user_role: this.model.userRole});
  }

  _showFailDetails() {
    showPluginFailedMessage(this, this.model.pluginStatus);
  }
}

customElements.define(OwnerRequirementValue.is, OwnerRequirementValue);
