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
import {CodeOwnerService, OwnerStatus, RenamedFileChip} from './code-owners-service.js';
import {ownerState} from './owner-ui-state.js';

export class SuggestOwnersTrigger extends Polymer.Element {
  static get is() {
    return 'suggest-owners-trigger';
  }

  static get properties() {
    return {
      change: Object,
      expanded: {
        type: Boolean,
        value: false,
      },
      restApi: Object,
      hidden: {
        type: Boolean,
        value: true,
        reflectToAttribute: true,
      },
    };
  }


  static get observers() {
    return [
      'onInputChanged(restApi, change)',
    ];
  }

  static get template() {
    return Polymer.html`
      <style include="shared-styles">
        iron-icon {
          padding-left: var(--spacing-m);
        }
      </style>
      <gr-button
        on-click="toggleControlContent"
        has-tooltip
        title="Suggest owners for your change"
      >
        [[computeButtonText(expanded)]]
        <iron-icon icon="gr-icons:info-outline"></iron-icon>
      </gr-button>
    `;
  }

  connectedCallback() {
    super.connectedCallback();
    ownerState.onExpandSuggestionChange(expanded => {
      this.expanded = expanded;
    });
  }

  onInputChanged(restApi, change) {
    if ([restApi, change].includes(undefined)) return;
    this.ownerService = CodeOwnerService
        .getOwnerService(this.restApi, this.change);
    this.ownerService.getStatus().then(({rawStatuses}) => {
      const notAllApproved = rawStatuses.some(status => {
        const oldPathStatus = status.old_path_status;
        const newPathStatus = status.new_path_status;
        if (newPathStatus.status !== OwnerStatus.APPROVED) {
          return true;
        }
        return oldPathStatus && oldPathStatus.status !== OwnerStatus.APPROVED;
      });
      this.hidden = !notAllApproved;
    });
  }

  toggleControlContent() {
    this.expanded = !this.expanded;
    ownerState.expandSuggestion = this.expanded;
  }

  computeButtonText(expanded) {
    return expanded ? 'Hide owners' : 'Suggest owners';
  }
}

customElements.define(SuggestOwnersTrigger.is, SuggestOwnersTrigger);

class OwnerGroupFileList extends Polymer.Element {
  static get is() {
    return 'owner-group-file-list';
  }

  static get properties() {
    return {
      files: Array,
    };
  }

  static get template() {
    return Polymer.html`
      <style include="shared-styles">
      span {
        display: inline-block;
        border-radius: var(--border-radius);
        margin-left: var(--spacing-s);
        padding: 0 var(--spacing-m);
        color: var(--primary-text-color);
        font-size: var(--font-size-small);
      }
      span.renamed-old {
        background-color: var(--dark-remove-highlight-color);
      }
      span.renamed-new {
        background-color: var(--dark-add-highlight-color);
      }
      </style>
      <ul>
        <template
          is="dom-repeat"
          items="[[files]]"
          as="file"
        >
          <li>
            [[computeFilePath(file)]]<!--
            --><strong>[[computeFileName(file)]]</strong>
            <template is="dom-if" if="[[file.status]]">
              <span class$="[[computeStatusClass(file)]]">[[computeFileStatus(file)]]</span>
            </template>
          </li>
        </template>
      </ul>
    `;
  }

  computeFilePath(file) {
    const parts = file.path.split("/");
    return parts.slice(0, parts.length - 2).join("/") + "/";
  }

  computeFileName(file) {
    const parts = file.path.split("/");
    return parts.pop();
  }

  computeFileStatus(file) {
    return file.status;
  }

  computeStatusClass(file) {
    if (!file.status) return '';
    return file.status === RenamedFileChip.NEW ? 'renamed-new' : 'renamed-old';
  }

}

customElements.define(OwnerGroupFileList.is, OwnerGroupFileList);

export class SuggestOwners extends Polymer.Element {
  static get is() {
    return 'suggest-owners';
  }

  static get template() {
    return Polymer.html`
      <style include="shared-styles">
        :host {
          display: block;
          background-color: var(--view-background-color);
          border: 1px solid var(--view-background-color);
          border-radius: var(--border-radius);
          box-shadow: var(--elevation-level-1);
          padding: var(--spacing-m);
        }
        p.loading {
          text-align: center;
        }
        .loadingSpin {
          display: inline-block;
        }
        li {
          list-style: none;
        }
        .suggestion-container {
          /* TODO: TBD */
          max-height: 300px;
          overflow-y: auto;
        }
        .suggestion-row {
          display: flex;
          flex-direction: row;
          align-items: flex-start;
          border-bottom: 1px solid var(--border-color);
          padding: var(--spacing-s) var(--spacing-xs);
        }
        .suggestion-row:hover {
          background: var(--hover-background-color);
        }
        .suggestion-grou-name {
          width: 200px;
          text-overflow: ellipsis;
          overflow: hidden;
          padding-right: var(--spacing-l);
          white-space: nowrap;
        }
        .suggested-owners {
          flex: 1;
        }
        gr-account-label {
          background-color: var(--background-color-tertiary);
          display: inline-block;
          padding: var(--spacing-xs) var(--spacing-m);
          user-select: none;
          --label-border-radius: 8px;
          border: 1px solid transparent;
        }
        gr-account-label:focus {
          outline: none;
        }
        gr-account-label:hover {
          box-shadow: var(--elevation-level-1);
          cursor: pointer;
        }
        gr-account-label[selected] {
          background-color: var(--chip-selected-background-color);
          border: 1px solid var(--chip-selected-background-color);
          color: var(--chip-selected-text-color);
        }
      </style>
      <p class="loading" hidden="[[!isLoading]]">
        <span class="loadingSpin"></span>
        loading...
      </p>
      <ul class="suggestion-container" hidden="[[isLoading]]">
        <template
          is="dom-repeat"
          items="[[suggestedOwners]]"
          as="suggestion"
          index-as="suggestionIndex"
        >
          <li class="suggestion-row">
            <div class="suggestion-grou-name">
              <span>
                [[suggestion.groupName]]
                <gr-hovercard hidden="[[suggestion.expanded]]">
                  <owner-group-file-list
                    files="[[suggestion.files]]"
                  >
                  </owner-group-file-list>
                </gr-hovercard>
              <span>
              <owner-group-file-list
                hidden="[[!suggestion.expanded]]"
                files="[[suggestion.files]]"
              ></owner-group-file-list>
            </div>
            <template is="dom-if" if="[[suggestion.error]]">
              [[suggestion.error]]
            </template>
            <template is="dom-if" if="[[!suggestion.error]]">
              <ul class="suggested-owners">
                <template
                  is="dom-repeat"
                  items="[[suggestion.owners]]"
                  as="owner"
                  index-as="ownerIndex"
                >
                  <gr-account-label
                    data-suggestion-index$="[[suggestionIndex]]"
                    data-owner-index$="[[ownerIndex]]"
                    account="[[owner.account]]"
                    hide-hovercard
                    selected$="[[isSelected(owner)]]"
                    on-click="toggleAccount">
                  </gr-account-label>
                </template>
              </ul>
            </template>
          </li>
        </template>
      </ul>
    `;
  }

  static get properties() {
    return {
      // @input
      change: Object,
      restApi: Object,

      // @internal attributes
      hidden: {
        type: Boolean,
        value: true,
        reflectToAttribute: true,
      },
      ownerService: Object,
      suggestedOwners: Array,
      isLoading: {
        type: Boolean,
        value: true,
      },
      reviewers: {
        type: Array,
      },
      pendingReviewers: Array,
    };
  }

  static get observers() {
    return [
      'onInputChanged(restApi, change)',
      'onReviewerChange(reviewers)',
    ];
  }

  connectedCallback() {
    super.connectedCallback();
    ownerState.onExpandSuggestionChange(expanded => {
      this.hidden = !expanded;
    });
  }

  onInputChanged(restApi, change) {
    if ([restApi, change].includes(undefined)) return;
    this.isLoading = true;
    this.ownerService = CodeOwnerService.getOwnerService(this.restApi, change);

    this.ownerService
        .getSuggestedOwners()
        .then(suggestedOwners => {
          this.isLoading = false;
          this.suggestedOwners = suggestedOwners.map(suggestion => {
            return this.formatSuggestionInfo(suggestion);
          });

          // in case `_updateAllChips` called before suggestedOwners ready
          // from onReviewerChange
          this._updateAllChips(this._currentReviewers);
        });
  }

  onReviewerChange(reviewers) {
    this._currentReviewers = reviewers;
    this._updateAllChips(reviewers);
  }

  formatSuggestionInfo(suggestion) {
    const res = {};
    res.groupName = suggestion.groupName;
    res.files = suggestion.files.slice();
    res.owners = suggestion.owners.map(owner => {
      const updatedOwner = {...owner};
      const reviewers = this.change.reviewers.REVIEWER;
      if (
        reviewers &&
        reviewers.find(reviewer => reviewer._account_id === owner._account_id)
      ) {
        updatedOwner.selected = true;
      }
      return updatedOwner;
    });
    return res;
  }

  addAccount(owner) {
    owner.selected = true;
    this.dispatchEvent(
        new CustomEvent('add-reviewer', {
          detail: {
            reviewer: {...owner.account, _pendingAdd: true},
          },
          composed: true,
          bubbles: true,
        })
    );
  }

  removeAccount(owner) {
    owner.selected = false;
    this.dispatchEvent(
        new CustomEvent('remove-reviewer', {
          detail: {
            reviewer: {...owner.account, _pendingAdd: true},
          },
          composed: true,
          bubbles: true,
        })
    );
  }

  toggleAccount(e) {
    const grAccountLabel = e.currentTarget;
    const owner = this.suggestedOwners[grAccountLabel.dataset.suggestionIndex].owners[grAccountLabel.dataset.ownerIndex];
    if (this.isSelected(owner)) {
      this.removeAccount(owner);
    } else {
      this.addAccount(owner);
    }
  }

  _updateAllChips(accounts) {
    if (!this.suggestedOwners || !accounts) return;
    // update all occurences
    this.suggestedOwners.forEach((suggestion, sId) => {
      suggestion.owners.forEach((owner, oId) => {
        if (
          accounts.some(account => account._account_id === owner.account._account_id)
        ) {
          this.set(
              ['suggestedOwners', sId, 'owners', oId],
              {...owner,
                selected: true,
              }
          );
        } else {
          this.set(
              ['suggestedOwners', sId, 'owners', oId],
              {...owner, selected: false}
          );
        }
      });
    });
  }

  isSelected(owner) {
    return owner.selected;
  }
}

customElements.define(SuggestOwners.is, SuggestOwners);
