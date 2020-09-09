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
import {ownerState} from './owner-ui-state.js';

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
      .Renamed {
        background-color: var(--dark-remove-highlight-color);
        margin: var(--spacing-s) 0;
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
              <span class$="[[file.status]]">
                [[file.status]]
              </span>
            </template>
          </li>
        </template>
      </ul>
    `;
  }

  computeFilePath(file) {
    const parts = file.path.split('/');
    return parts.slice(0, parts.length - 1).join('/') + '/';
  }

  computeFileName(file) {
    const parts = file.path.split('/');
    return parts.pop();
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
          margin: var(--spacing-m) 0;
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
        .suggestion-row:last-of-type {
          border-bottom: none;
        }
        .suggestion-row-indicator {
          margin-right: var(--spacing-m);
          visibility: hidden;
        }
        .suggestion-row-indicator[visible] {
          visibility: visible;
        }
        .suggestion-row-indicator[visible] iron-icon {
          color: var(--link-color);
        }
        .suggestion-group-name {
          width: 200px;
          line-height: 26px;
          text-overflow: ellipsis;
          overflow: hidden;
          padding-right: var(--spacing-l);
          white-space: nowrap;
        }
        .group-name-content {
          display: flex;
          align-items: center;
        }
        .group-name-content .group-name {
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .group-name-prefix {
          padding-left: var(--spacing-s);
          white-space: nowrap;
          color: var(--deemphasized-text-color);
        }
        .suggested-owners {
          flex: 1;
        }
        .no-owners-content {
          line-height: 26px;
          flex: 1;
        }
        .no-owners-content a {
          padding-left: var(--spacing-s);
        }
        .no-owners-content a iron-icon {
          width: 16px;
          height: 16px;
        }
        gr-account-label {
          background-color: var(--background-color-tertiary);
          display: inline-block;
          padding: var(--spacing-xs) var(--spacing-m);
          user-select: none;
          border: 1px solid transparent;
          --label-border-radius: 8px;
          --account-max-length: 100px;
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
            <div
              class="suggestion-row-indicator"
              visible$="[[suggestion.hasSelected]]"
            >
              <iron-icon icon="gr-icons:check-circle"></iron-icon>
            </div>
            <div class="suggestion-group-name">
              <div class="group-name-content">
                <span class="group-name">
                  [[suggestion.groupName.name]]
                </span>
                <template is="dom-if" if="[[suggestion.groupName.prefix]]">
                  <span class="group-name-prefix">
                    ([[suggestion.groupName.prefix]])
                  </span>
                </template>
                <gr-hovercard hidden="[[suggestion.expanded]]">
                  <owner-group-file-list
                    files="[[suggestion.files]]"
                  >
                  </owner-group-file-list>
                </gr-hovercard>
              </div>
              <owner-group-file-list
                hidden="[[!suggestion.expanded]]"
                files="[[suggestion.files]]"
              ></owner-group-file-list>
            </div>
            <template is="dom-if" if="[[suggestion.error]]">
              [[suggestion.error]]
            </template>
            <template is="dom-if" if="[[!suggestion.error]]">
              <template is="dom-if" if="[[!suggestion.owners.length]]">
                <div class="no-owners-content">
                  <span>Not found</span>
                  <a href="https://gerrit-review.googlesource.com/Documentation/plugin-code-owners.html" target="_blank">
                    <iron-icon icon="gr-icons:help-outline" title="read documentation"></iron-icon>
                  </a>
                </div>
              </template>
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
      reporting: Object,

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
      if (expanded) {
        // this is more of a hack to let reivew input lose focus
        // to avoid suggestion dropdown
        // gr-autocomplete has a internal state for tracking focus
        // that will be canceled if any click happens outside of
        // it's target
        // Can not use `this.async` as it's only available in
        // legacy element mixin which not used in this plugin.
        Polymer.Async.timeOut.run(() => this.click(), 100);
      }
    });
  }

  onInputChanged(restApi, change) {
    ownerState.expandSuggestion = false;
    if ([restApi, change].includes(undefined)) return;
    this.isLoading = true;
    this.ownerService = CodeOwnerService.getOwnerService(this.restApi, change);

    // if all approved, no need to show the container
    this.ownerService.areAllFilesApproved().then(approved => {
      if (approved) {
        this.hidden = approved;
      }
    });

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
    this.reporting.reportInteraction('add-reviewer');
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
    this.reporting.reportInteraction('remove-reviewer');
  }

  toggleAccount(e) {
    const grAccountLabel = e.currentTarget;
    const owner = this.suggestedOwners[grAccountLabel.dataset.suggestionIndex]
        .owners[grAccountLabel.dataset.ownerIndex];
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
      let hasSelected = false;
      suggestion.owners.forEach((owner, oId) => {
        if (
          accounts.some(account => account._account_id
              === owner.account._account_id)
        ) {
          this.set(
              ['suggestedOwners', sId, 'owners', oId],
              {...owner,
                selected: true,
              }
          );
          hasSelected = true;
        } else {
          this.set(
              ['suggestedOwners', sId, 'owners', oId],
              {...owner, selected: false}
          );
        }
      });
      this.set(['suggestedOwners', sId, 'hasSelected'], hasSelected);
    });
  }

  isSelected(owner) {
    return owner.selected;
  }
}

customElements.define(SuggestOwners.is, SuggestOwners);
