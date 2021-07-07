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

const MAGIC_FILES = ['/COMMIT_MSG', '/MERGE_LIST', '/PATCHSET_LEVEL'];
const STATUS_CODE = {
  PENDING: 'pending',
  PENDING_OLD_PATH: 'pending-old-path',
  MISSING: 'missing',
  MISSING_OLD_PATH: 'missing-old-path',
  APPROVED: 'approved',
  ERROR: 'error',
  ERROR_OLD_PATH: 'error-old-path',
};
const STATUS_PRIORITY_ORDER = [
  STATUS_CODE.ERROR,
  STATUS_CODE.ERROR_OLD_PATH,
  STATUS_CODE.MISSING,
  STATUS_CODE.PENDING,
  STATUS_CODE.MISSING_OLD_PATH,
  STATUS_CODE.PENDING_OLD_PATH,
  STATUS_CODE.APPROVED,
];
const STATUS_ICON = {
  [STATUS_CODE.PENDING]: 'gr-icons:schedule',
  [STATUS_CODE.MISSING]: 'gr-icons:close',
  [STATUS_CODE.PENDING_OLD_PATH]: 'gr-icons:schedule',
  [STATUS_CODE.MISSING_OLD_PATH]: 'gr-icons:close',
  [STATUS_CODE.APPROVED]: 'gr-icons:check',
  [STATUS_CODE.ERROR]: 'gr-icons:info-outline',
  [STATUS_CODE.ERROR]: 'gr-icons:info-outline',
};
const STATUS_TOOLTIP = {
  [STATUS_CODE.PENDING]: 'Pending code owner approval',
  [STATUS_CODE.MISSING]: 'Missing code owner approval',
  [STATUS_CODE.PENDING_OLD_PATH]:
    'Pending code owner approval on pre-renamed file',
  [STATUS_CODE.MISSING_OLD_PATH]:
    'Missing code owner approval on pre-renamed file',
  [STATUS_CODE.APPROVED]: 'Approved by code owner',
  [STATUS_CODE.ERROR]: 'Failed to fetch code owner status',
  [STATUS_CODE.ERROR_OLD_PATH]: 'Failed to fetch code owner status',
};

class BaseEl extends CodeOwnersModelMixin(Polymer.Element) {
  static get properties() {
    return {
      patchRange: Object,

      hidden: {
        type: Boolean,
        reflectToAttribute: true,
        computed: 'computeHidden(change, patchRange, ' +
            'model.status.newerPatchsetUploaded)',
      },
    };
  }

  computeHidden(change, patchRange, newerPatchsetUploaded) {
    if ([change, patchRange, newerPatchsetUploaded].includes(undefined)) {
      return true;
    }
    // if code-owners is not a submit requirement, don't show status column
    if (change.requirements &&
        !change.requirements.find(r => r.type === 'code-owners')) {
      return true;
    }

    if (newerPatchsetUploaded) return true;

    const latestPatchset = change.revisions[change.current_revision];
    // Note: in some special cases, patchNum is undefined on latest patchset
    // like after publishing the edit, still show for them
    // TODO: this should be fixed in Gerrit
    if (patchRange.patchNum === undefined) return false;
    // only show if its latest patchset
    if (`${patchRange.patchNum}` !== `${latestPatchset._number}`) return true;
    return false;
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
          width: 3em;
        }
        </style>
        <div></div>
      `;
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
      path: String,
      oldPath: String,
      cleanlyMergedPaths: Array,
      cleanlyMergedOldPaths: Array,
      ownerService: Object,
      statusIcon: {
        type: String,
        computed: '_computeIcon(status)',
      },
      statusInfo: {
        type: String,
        computed: '_computeTooltip(status)',
      },
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
          width: 3em;
          text-align: center;
        }
        iron-icon {
          padding: var(--spacing-xs) 0px;
        }
        :host([status=approved]) iron-icon {
          color: var(--positive-green-text-color);
        }
        :host([status=pending]) iron-icon {
          color: #ffa62f;
        }
        :host([status=missing]) iron-icon,
        :host([status=error]) iron-icon {
          color: var(--negative-red-text-color);
        }
        </style>
        <gr-tooltip-content title="[[statusInfo]]" has-tooltip>
          <iron-icon icon="[[statusIcon]]"></iron-icon>
        </gr-tooltip-content>
      `;
  }

  static get observers() {
    return [
      'computeStatusIcon(model.status,path, oldPath, cleanlyMergedPaths, ' +
        'cleanlyMergedOldPaths)',
    ];
  }

  loadPropertiesAfterModelChanged() {
    super.loadPropertiesAfterModelChanged();
    this.modelLoader.loadStatus();
  }

  computeStatusIcon(
      modelStatus,
      path,
      oldPath,
      cleanlyMergedPaths,
      cleanlyMergedOldPaths
  ) {
    if (
      modelStatus === undefined ||
      ([path, oldPath].includes(undefined) && cleanlyMergedPaths === undefined)
    ) {
      return;
    }
    const codeOwnerStatusMap = modelStatus.codeOwnerStatusMap;
    const paths = path === undefined ? cleanlyMergedPaths : [path];
    const oldPaths = oldPath === undefined ? cleanlyMergedOldPaths : [oldPath];

    const statuses = paths
        .filter(path => !MAGIC_FILES.includes(path))
        .map(path => this._computeStatus(codeOwnerStatusMap.get(path)));
    const oldStatuses = oldPaths
        .filter(path => !MAGIC_FILES.includes(path) && !!path)
        .map(path => this._computeStatus(codeOwnerStatusMap.get(path), true));
    const allStatuses = statuses.concat(oldStatuses);
    if (allStatuses.length === 0) {
      return;
    }
    this.status = allStatuses.reduce((a, b) => {
      return STATUS_PRIORITY_ORDER.indexOf(a) <
        STATUS_PRIORITY_ORDER.indexOf(b)
        ? a
        : b;
    });
  }

  _computeIcon(status) {
    return STATUS_ICON[status];
  }

  _computeTooltip(status) {
    return STATUS_TOOLTIP[status];
  }

  _computeStatus(statusItem, oldPath = false) {
    if (statusItem === undefined) {
      return oldPath ? STATUS_CODE.ERROR_OLD_PATH : STATUS_CODE.ERROR;
    } else if (statusItem.status === OwnerStatus.INSUFFICIENT_REVIEWERS) {
      return oldPath ? STATUS_CODE.MISSING_OLD_PATH : STATUS_CODE.MISSING;
    } else if (statusItem.status === OwnerStatus.PENDING) {
      return oldPath ? STATUS_CODE.PENDING_OLD_PATH : STATUS_CODE.PENDING;
    } else {
      return STATUS_CODE.APPROVED;
    }
  }
}

customElements.define(OwnerStatusColumnContent.is, OwnerStatusColumnContent);
