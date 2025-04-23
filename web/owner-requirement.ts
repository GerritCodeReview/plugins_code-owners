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

import {CodeOwnersModelMixin} from './code-owners-model-mixin';
import {showPluginFailedMessage} from './code-owners-banner';
import {isPluginErrorState, UserRole} from './code-owners-model';
import {css, html, LitElement} from 'lit';
import {customElement} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {
  ApprovalInfo,
  DetailedLabelInfo,
} from '@gerritcodereview/typescript-api/rest-api';
import {OwnerStatus} from './code-owners-api';

const base = CodeOwnersModelMixin(LitElement);

export const OWNER_REQUIREMENT_VALUE = 'owner-requirement-value';
/**
 * Owner requirement control for `submit-requirement-item-code-owners` endpoint.
 *
 * This will show the status and suggest owners button next to
 * the code-owners submit requirement.
 */
@customElement(OWNER_REQUIREMENT_VALUE)
export class OwnerRequirementValue extends base {
  static override get styles() {
    return [
      css`
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
        gr-button::part(paper-button) {
          padding: 0 var(--spacing-s);
        }
        a {
          text-decoration: none;
        }
      `,
    ];
  }

  override render() {
    // Compute whether plugin failed first because it might mean some of the
    // model parameters are not set which would result in a loading screen.
    if (this.pluginFailed()) {
      return html`
        <span>Code-owners plugin has failed</span>
        <gr-button link @click=${this.showFailDetails}>Details</gr-button>
      `;
    }
    if (!this.branchConfig || !this.status || !this.userRole) {
      return html`
        <p class="loading">
          <span class="loadingSpin"></span>
          Loading status ...
        </p>
      `;
    }

    if (this.status.newerPatchsetUploaded) {
      return html`<span>A newer patch set has been uploaded.</span>`;
    }
    const overrideInfoUrl = this.computeOverrideInfoUrl();
    const statusCount = this.getStatusCount();
    this.reporting?.reportLifeCycle('owners-submit-requirement-summary-shown', {
      ...statusCount,
      user_role: this.userRole,
    });
    return html`
      <span>${this.computeStatusText(statusCount)}</span>
      ${when(
        !!overrideInfoUrl,
        () => html`
          <a
            @click=${this.reportDocClick}
            href=${overrideInfoUrl}
            target="_blank"
          >
            <gr-icon
              icon="help"
              title="Documentation for overriding code owners"
            ></gr-icon>
          </a>
        `
      )}
      ${when(
        this.computeIsSignedInUser(this.userRole),
        () => html`
          <gr-button link @click=${this.openReplyDialog}>
            ${this.getSuggestOwnersText(statusCount)}
          </gr-button>
        `
      )}
    `;
  }

  override loadPropertiesAfterModelChanged() {
    super.loadPropertiesAfterModelChanged();
    this.reporting?.reportLifeCycle('owners-submit-requirement-summary-start');
    this.modelLoader?.loadBranchConfig();
    this.modelLoader?.loadStatus();
    this.modelLoader?.loadUserRole();
  }

  private computeIsSignedInUser(userRole: UserRole) {
    return userRole && userRole !== UserRole.ANONYMOUS;
  }

  private pluginFailed() {
    return this.pluginStatus && isPluginErrorState(this.pluginStatus.state);
  }

  private computeOverrideInfoUrl() {
    if (!this.branchConfig) {
      return '';
    }
    return this.branchConfig.general &&
      this.branchConfig.general.override_info_url
      ? this.branchConfig.general.override_info_url
      : '';
  }

  private computeIsOverriden() {
    if (
      !this.change ||
      !this.branchConfig ||
      !this.branchConfig['override_approval']
    ) {
      // no override labels configured
      return false;
    }

    for (const requiredApprovalInfo of this.branchConfig['override_approval']) {
      const overridenLabel = requiredApprovalInfo.label;
      const overridenValue = Number(requiredApprovalInfo.value);
      if (isNaN(overridenValue)) continue;

      if (this.change.labels?.[overridenLabel]) {
        const votes =
          (this.change.labels[overridenLabel] as DetailedLabelInfo).all || [];
        if (
          votes.find((v: ApprovalInfo) => Number(v.value) >= overridenValue)
        ) {
          return true;
        }
      }
    }

    // otherwise always reset it to false
    return false;
  }

  private getSuggestOwnersText(statusCount: {
    missing: number;
    pending: number;
    approved: number;
  }) {
    return statusCount && statusCount.missing === 0
      ? 'Add owners'
      : 'Suggest owners';
  }

  private getStatusCount() {
    return (this.status?.rawStatuses ?? []).reduce(
      (prev, cur) => {
        const oldPathStatus = cur.old_path_status;
        const newPathStatus = cur.new_path_status;
        if (newPathStatus && this.isMissing(newPathStatus.status)) {
          prev.missing++;
        } else if (newPathStatus && this.isPending(newPathStatus.status)) {
          prev.pending++;
        } else if (oldPathStatus) {
          // check oldPath if newPath approved or the file is deleted
          if (this.isMissing(oldPathStatus.status)) {
            prev.missing++;
          } else if (this.isPending(oldPathStatus.status)) {
            prev.pending++;
          }
        } else {
          prev.approved++;
        }
        return prev;
      },
      {missing: 0, pending: 0, approved: 0}
    );
  }

  private computeStatusText(statusCount: {
    missing: number;
    pending: number;
    approved: number;
  }) {
    if (this.model === undefined || this.change === undefined) return '';
    const isOverriden = this.computeIsOverriden();
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

  private isMissing(status: OwnerStatus | undefined) {
    return status === OwnerStatus.INSUFFICIENT_REVIEWERS;
  }

  private isPending(status: OwnerStatus | undefined) {
    return status === OwnerStatus.PENDING;
  }

  private openReplyDialog() {
    this.model!.setShowSuggestions(true);
    this.dispatchEvent(
      new CustomEvent('open-reply-dialog', {
        detail: {},
        composed: true,
        bubbles: true,
      })
    );
    this.reporting?.reportInteraction(
      'suggest-owners-from-submit-requirement',
      {
        user_role: this.userRole,
      }
    );
  }

  private reportDocClick() {
    this.reporting?.reportInteraction('code-owners-doc-click', {
      section: 'no owners found',
    });
  }

  private showFailDetails() {
    showPluginFailedMessage(this, this.pluginStatus!);
  }
}
