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

import {
  AccountInfo,
  BasePatchSetNum,
  RevisionPatchSetNum,
} from '@gerritcodereview/typescript-api/rest-api';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {OwnerStatus} from './code-owners-api';
import {FileStatus, PluginState} from './code-owners-model';
import {CodeOwnersModelMixin} from './code-owners-model-mixin';

// TODO: Extend the API for plugins.
export interface PatchRange {
  patchNum: RevisionPatchSetNum;
  basePatchNum: BasePatchSetNum;
}

const ACCOUNT_TEMPLATE_REGEX = '<GERRIT_ACCOUNT_(\\d+)>';

const MAGIC_FILES = ['/COMMIT_MSG', '/MERGE_LIST', '/PATCHSET_LEVEL'];
const STATUS_CODE = {
  NO_STATUS: 'no-status',
  PENDING: 'pending',
  PENDING_OLD_PATH: 'pending-old-path',
  MISSING: 'missing',
  MISSING_OLD_PATH: 'missing-old-path',
  APPROVED: 'approved',
};

const STATUS_PRIORITY_ORDER = [
  STATUS_CODE.NO_STATUS,
  STATUS_CODE.MISSING,
  STATUS_CODE.PENDING,
  STATUS_CODE.MISSING_OLD_PATH,
  STATUS_CODE.PENDING_OLD_PATH,
  STATUS_CODE.APPROVED,
];

const STATUS_ICON = {
  [STATUS_CODE.PENDING]: 'schedule',
  [STATUS_CODE.MISSING]: 'close',
  [STATUS_CODE.PENDING_OLD_PATH]: 'schedule',
  [STATUS_CODE.MISSING_OLD_PATH]: 'close',
  [STATUS_CODE.APPROVED]: 'check',
  [STATUS_CODE.NO_STATUS]: 'check_circle',
};

const STATUS_SUMMARY = {
  [STATUS_CODE.PENDING]: 'Pending',
  [STATUS_CODE.MISSING]: 'Missing',
  [STATUS_CODE.PENDING_OLD_PATH]: 'Pending Old Path',
  [STATUS_CODE.MISSING_OLD_PATH]: 'Missing Old Path',
  [STATUS_CODE.APPROVED]: 'Approved',
  [STATUS_CODE.NO_STATUS]: 'Does not need approval',
};

const STATUS_TOOLTIP = {
  [STATUS_CODE.PENDING]: 'Pending code owner approval',
  [STATUS_CODE.MISSING]: 'Missing code owner approval',
  [STATUS_CODE.PENDING_OLD_PATH]:
    'Pending code owner approval on pre-renamed file',
  [STATUS_CODE.MISSING_OLD_PATH]:
    'Missing code owner approval on pre-renamed file',
  [STATUS_CODE.APPROVED]: 'Approved by code owner',
  [STATUS_CODE.NO_STATUS]: 'Does not need approval',
};

export function hasPath(ownedPaths: Set<string>, path: string | undefined) {
  if (!path) return false;
  if (path.charAt(0) !== '/') path = '/' + path;
  return ownedPaths.has(path);
}

export function getOwners(
  owners: Map<string, Array<AccountInfo>>,
  path: string | undefined
): Array<AccountInfo> {
  if (!path) return [];
  if (path.charAt(0) !== '/') path = '/' + path;
  return owners.get(path) ?? [];
}

export function uniqueAccountId(
  account: AccountInfo,
  index: number,
  accountArray: AccountInfo[]
) {
  return (
    index ===
    accountArray.findIndex(other => account._account_id === other._account_id)
  );
}

const base = CodeOwnersModelMixin(LitElement);

class BaseEl extends base {
  @property({type: Object})
  patchRange?: PatchRange;

  protected override willUpdate(changedProperties: PropertyValues): void {
    super.willUpdate(changedProperties);
    this.hidden = this.computeHidden();
  }

  computeHidden() {
    const newerPatchsetUploaded = this.status?.newerPatchsetUploaded;
    if (
      this.change === undefined ||
      this.patchRange === undefined ||
      newerPatchsetUploaded === undefined
    ) {
      return true;
    }
    if (this.pluginStatus?.state !== PluginState.Enabled) return true;
    if (this.change.status === 'MERGED') return true;
    // if code-owners is not a submit requirement, don't show status column
    if (
      this.change.requirements &&
      !this.change.requirements.find(r => r.type === 'code-owners')
    ) {
      return false;
    }

    if (newerPatchsetUploaded) return true;

    const latestPatchset =
      this.change.revisions![this.change.current_revision!];
    // Note: in some special cases, patchNum is undefined on latest patchset
    // like after publishing the edit, still show for them
    // TODO: this should be fixed in Gerrit
    if (this.patchRange?.patchNum === undefined) return false;
    // only show if its latest patchset
    if (`${this.patchRange.patchNum}` !== `${latestPatchset._number}`)
      return true;
    return false;
  }
}

export const OWNERS_STATUS_COLUMN_HEADER = 'owner-status-column-header';
/**
 * Column header element for owner status.
 */
@customElement(OWNERS_STATUS_COLUMN_HEADER)
export class OwnerStatusColumnHeader extends BaseEl {
  static override get styles() {
    return [
      css`
        :host() {
          display: block;
          padding-right: var(--spacing-m);
          width: 5em;
        }
        :host[hidden] {
          display: none;
        }
      `,
    ];
  }

  override render() {
    if (this.computeHidden()) return nothing;
    return html`<div>Owners</div>`;
  }
}

export const OWNER_STATUS_COLUMN_CONTENT = 'owner-status-column-content';
/**
 * Row content element for owner status.
 */
@customElement(OWNER_STATUS_COLUMN_CONTENT)
export class OwnerStatusColumnContent extends BaseEl {
  @property({type: String})
  path?: string;

  @property({type: String})
  oldPath?: string;

  @property({type: Array})
  cleanlyMergedPaths?: Array<string>;

  @property({type: Array})
  cleanlyMergedOldPaths?: Array<string>;

  @property({type: String, reflect: true, attribute: 'owner-status'})
  ownerStatus?: string;

  @property({type: Array})
  ownerReasons?: Array<string>;

  static override get styles() {
    return [
      css`
        :host {
          display: flex;
          padding-right: var(--spacing-m);
          width: 5em;
          text-align: center;
        }
        :host[hidden] {
          display: none;
        }
        gr-icon {
          padding: var(--spacing-xs) 0px;
        }
        :host([owner-status='approved']) gr-icon.status {
          color: var(--positive-green-text-color);
        }
        :host([owner-status='pending']) gr-icon.status {
          color: #ffa62f;
        }
        :host([owner-status='missing']) gr-icon.status {
          color: var(--negative-red-text-color);
        }
        gr-avatar-stack {
          padding: var(--spacing-xs) 0px;
          display: flex;
          --avatar-size: 20px;
        }
        .ellipsis {
          /* These are required to get the ... to line up with the bottom of
             the avatar icons. */
          margin-bottom: -2px;
          display: flex;
          align-items: flex-end;
        }
        .error {
          color: var(--negative-red-text-color);
        }
        .fallback-icon {
          /* Undo the padding for the gr-avatar-stack in case of fallback */
          padding: calc(-1 * var(--spacing-xs)) 0px;
        }
      `,
    ];
  }

  protected override willUpdate(changedProperties: PropertyValues): void {
    super.willUpdate(changedProperties);
    this.computeStatus();
  }

  private renderReason(reason: string): string {
    let reasonWithAccounts = reason.replace(
      new RegExp(ACCOUNT_TEMPLATE_REGEX, 'g'),
      (_accountIdTemplate, accountId) => {
        const parsedAccountId = Number(accountId);
        const accountInText = (this.status?.accounts || {})[parsedAccountId];
        if (!accountInText) {
          return `Gerrit Account ${parsedAccountId}`;
        }
        return accountInText.display_name ?? accountInText.name ?? '';
      }
    );
    return (
      reasonWithAccounts.charAt(0).toUpperCase() + reasonWithAccounts.slice(1)
    );
  }

  override render() {
    if (
      this.computeHidden() ||
      this.status === undefined ||
      !this.path ||
      MAGIC_FILES.includes(this.path)
    )
      return nothing;
    return html`${this.renderStatus()}${this.renderOwnership()}`;
  }

  private renderStatus() {
    let info = STATUS_TOOLTIP[this.ownerStatus!];
    if (this.ownerReasons) {
      info = this.ownerReasons.map(r => this.renderReason(r)).join('\n');
    }
    const summary = STATUS_SUMMARY[this.ownerStatus!];
    const icon = STATUS_ICON[this.ownerStatus!];
    return html`
      <gr-tooltip-content
        title=${info}
        aria-label=${summary}
        aria-description=${info}
        has-tooltip
      >
        <gr-icon class="status" icon=${icon} aria-hidden="true"></gr-icon>
      </gr-tooltip-content>
    `;
  }

  private renderOwnership() {
    if (this.isOwned()) {
      return html`
        <gr-tooltip-content
          title="You are in OWNERS for this file"
          aria-label="owned"
          aria-description="You are an owner of this file"
          has-tooltip
        >
          <gr-icon filled icon="policy" aria-hidden="true"></gr-icon>
        </gr-tooltip-content>
      `;
    } else if (this.ownedPaths) {
      let oldOwners = getOwners(this.ownedPaths.oldPathOwners, this.oldPath);
      const newOwners = getOwners(this.ownedPaths.newPathOwners, this.path);
      if (this.oldPath === undefined || this.oldPath === null) {
        // In case of a file deletion, the Gerrit FE gives 'path' but not 'oldPath'
        // but code-owners considers a deleted file an oldpath so check the oldpath owners.
        oldOwners = getOwners(this.ownedPaths.oldPathOwners, this.path);
      }
      const allOwners = oldOwners.concat(newOwners).filter(uniqueAccountId);

      return html` <gr-avatar-stack
          .accounts=${allOwners.slice(0, 3)}
          .forceFetch=${true}
          .enableHover=${true}
        >
          <gr-tooltip-content
            slot="fallback"
            title="No reviewer owns this file"
            aria-label="missing owner"
            aria-description="No reviewer owns this file"
            has-tooltip
          >
            <gr-icon icon="help" class="error fallback-icon"></gr-icon>
          </gr-tooltip-content>
        </gr-avatar-stack>
        ${allOwners.length > 3
          ? html`<div class="ellipsis">…</div>`
          : nothing}`;
    }
    return nothing;
  }

  private isOwned() {
    if (!this.ownedPaths) return false;
    if (
      hasPath(this.ownedPaths.newPaths, this.path) ||
      hasPath(this.ownedPaths.oldPaths, this.oldPath) ||
      // In case of deletions, the FE gives a path, but code-owners
      // computes this as being part of the old path.
      ((this.oldPath === undefined || this.oldPath === null) &&
        hasPath(this.ownedPaths.oldPaths, this.path))
    )
      return true;
    return false;
  }

  override loadPropertiesAfterModelChanged() {
    super.loadPropertiesAfterModelChanged();
    this.modelLoader?.loadStatus();
    this.modelLoader?.loadOwnedPaths();
  }

  private computeStatus() {
    if (
      this.status === undefined ||
      (this.cleanlyMergedPaths === undefined &&
        (this.path === undefined || this.oldPath === undefined))
    ) {
      return;
    }

    const codeOwnerStatusMap = this.status.codeOwnerStatusMap;
    const paths =
      this.path === undefined ? this.cleanlyMergedPaths : [this.path];
    const oldPaths =
      this.oldPath === undefined ? this.cleanlyMergedOldPaths : [this.oldPath];

    const statuses = (paths ?? [])
      .filter(path => !MAGIC_FILES.includes(path))
      .map(path => ({
        status: this.extractStatus(codeOwnerStatusMap.get(path), false),
        reasons: codeOwnerStatusMap.get(path)?.reasons,
      }));
    // oldPath may contain null, so filter that as well.
    const oldStatuses = (oldPaths ?? [])
      .filter(path => !MAGIC_FILES.includes(path) && !!path)
      .map(path => ({
        status: this.extractStatus(codeOwnerStatusMap.get(path), true),
        reasons: codeOwnerStatusMap.get(path)?.reasons,
      }));
    const allStatuses = statuses.concat(oldStatuses);
    if (allStatuses.length === 0) {
      return;
    }
    const computedStatus = allStatuses.reduce((a, b) =>
      STATUS_PRIORITY_ORDER.indexOf(a.status) <
      STATUS_PRIORITY_ORDER.indexOf(b.status)
        ? a
        : b
    );
    this.ownerStatus = computedStatus.status;
    this.ownerReasons = computedStatus.reasons;
  }

  private extractStatus(statusItem: FileStatus | undefined, oldPath: boolean) {
    if (statusItem === undefined) {
      return STATUS_CODE.NO_STATUS;
    } else if (statusItem.status === OwnerStatus.INSUFFICIENT_REVIEWERS) {
      return oldPath ? STATUS_CODE.MISSING_OLD_PATH : STATUS_CODE.MISSING;
    } else if (statusItem.status === OwnerStatus.PENDING) {
      return oldPath ? STATUS_CODE.PENDING_OLD_PATH : STATUS_CODE.PENDING;
    } else {
      return STATUS_CODE.APPROVED;
    }
  }
}
