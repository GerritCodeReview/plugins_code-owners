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

const MAGIC_FILES = ['/COMMIT_MSG', '/MERGE_LIST', '/PATCHSET_LEVEL'];

class BaseEl extends Polymer.Element {
  computeHidden(change, patchRange) {
    if ([change, patchRange].includes(undefined)) return true;
    const latestPatchset = change.revisions[change.current_revision];
    // only show if its comparing against base
    if (patchRange.basePatchNum !== 'PARENT') return true;
    // only show if its latest patchset
    if (`${patchRange.patchNum}` !== `${latestPatchset._number}`) return true;
    return false;
  }

  onInputChanged(restApi, change) {
    if ([restApi, change].includes(undefined)) return;
    this.ownerService = CodeOwnerService.getOwnerService(this.restApi, change);
  }
}

/**
 * Column header element for owner status.
 */
export class OwnerStatusColumnHeader extends BaseEl {
  static get is() {
    return 'owner-status-column-header';
  }

  static get template() {
    return Polymer.html`
        <style include="shared-styles">
        :host(:not([hidden])) {
          display: block;
          padding-right: var(--spacing-m);
          min-width: 5em;
        }
        </style>
        <div>Owner Status</div>
      `;
  }

  static get properties() {
    return {
      change: Object,
      patchRange: Object,
      hidden: {
        type: Boolean,
        reflectToAttribute: true,
        computed: 'computeHidden(change, patchRange)',
      },
      restApi: Object,
      ownerService: Object,
    };
  }

  static get observers() {
    return [
      'onInputChanged(restApi, change)',
      'onOwnerServiceChanged(ownerServive)',
    ];
  }

  connectedCallback() {
    super.connectedCallback();
  }

  onOwnerServiceChanged(ownerService) {
  }
}

customElements.define(OwnerStatusColumnHeader.is, OwnerStatusColumnHeader);

/**
 * Row content element for owner status.
 */
export class OwnerStatusColumnContent extends BaseEl {
  static get is() {
    return 'owner-status-column-content';
  }

  static get properties() {
    return {
      change: Object,
      path: String,
      patchRange: Object,
      hidden: {
        type: Boolean,
        reflectToAttribute: true,
        computed: 'computeHidden(change, patchRange)',
      },
      restApi: Object,
      ownerService: Object,
      statusIcon: String,
      statusInfo: String,
      status: {
        type: String,
        reflectToAttribute: true,
      },
    };
  }

  static get template() {
    return Polymer.html`
        <style include="shared-styles">
        :host(:not([hidden])) {
          display:block;
          padding-right: var(--spacing-m);
          min-width: 5em;
          text-align: center;
        }
        :host([status=approved]) iron-icon {
          color: var(--vote-color-approved);
        }
        :host([status=pending]) iron-icon {
          color: #ffa62f;
        }
        :host([status=missing]) iron-icon,
        :host([status=error]) iron-icon {
          color: var(--vote-color-disliked);
        }
        </style>
        <gr-tooltip-content title="[[statusInfo]]" has-tooltip>
          <iron-icon icon="[[statusIcon]]"></iron-icon>
        </gr-tooltip-content>
      `;
  }

  static get observers() {
    return [
      'onInputChanged(restApi, change)',
      'computeStatusIcon(ownerService, path)',
    ];
  }

  connectedCallback() {
    super.connectedCallback();
  }

  computeStatusIcon(ownerService, path) {
    if (MAGIC_FILES.includes(path)) return;
    if ([ownerService, path].includes(undefined)) return;
    ownerService.getStatusForPath(path)
        .then(status => {
          switch (status) {
            case OwnerStatus.APPROVED:
              this.statusIcon = 'gr-icons:check';
              this.status = 'approved';
              this.statusInfo = 'This file has been approved by the owner'
                 + 'or owned by you.';
              break;
            case OwnerStatus.INSUFFICIENT_REVIEWERS:
              this.statusIcon = 'gr-icons:info-outline';
              this.status = 'missing';
              this.statusInfo = 'Missing owner in reviewers, please add.';
              break;
            case OwnerStatus.PENDING:
              this.statusIcon = 'gr-icons:schedule';
              this.status = 'pending';
              this.statusInfo = 'Owner has not approved yet.';
              break;
            default:
              this.statusIcon = 'gr-icons:info-outline';
              this.status = 'error';
              this.statusInfo = 'Can not determine owner status for this file.';
              break;
          }
        })
        .catch(e => {
          this.statusIcon = 'gr-icons:info-outline';
          this.status = 'error';
          this.statusInfo = 'Can not determine owner status for this file.';
          throw e;
        });
  }
}

customElements.define(OwnerStatusColumnContent.is, OwnerStatusColumnContent);