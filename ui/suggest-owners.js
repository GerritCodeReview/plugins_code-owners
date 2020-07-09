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
import { CodeOwnerService } from "./code-owners-service.js";

const onToggleButtonClicks = [];
function toggleButtonClicked(expanded) {
  onToggleButtonClicks.forEach((cb) => {
    cb(expanded);
  });
}

export class SuggestOwnersTrigger extends Polymer.Element {
  static get is() {
    return "suggest-owners-trigger";
  }

  static get properties() {
    return {
      change: Object,
      expanded: {
        type: Boolean,
        value: false,
      },
    };
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

  toggleControlContent() {
    this.expanded = !this.expanded;
    toggleButtonClicked(this.expanded);
  }

  computeButtonText(expanded) {
    return expanded ? "Hide owners" : "Suggest owners";
  }
}

customElements.define(SuggestOwnersTrigger.is, SuggestOwnersTrigger);

class OwnerGroupFileList extends Polymer.Element {
  static get is() {
    return "owner-group-file-list";
  }

  static get properties() {
    return {
      files: Array,
    };
  }

  static get template() {
    return Polymer.html`
      <style include="shared-styles"></style>
      <ul>
        <template
          is="dom-repeat"
          items="[[files]]"
          as="file"
        >
          <li>[[file]]</li>
        </template>
      </ul>
    `;
  }
}

customElements.define(OwnerGroupFileList.is, OwnerGroupFileList);

export class SuggestOwners extends Polymer.Element {
  static get is() {
    return "suggest-owners";
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
      pendingReviewers: Array,
    };
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
          padding: 0 var(--spacing-m) 0 var(--spacing-s);
          margin-right: var(--spacing-m);
          user-select: none;
          display: inline-block;
          --label-border-radius: 10px;
        }
        gr-account-label:focus {
          outline: none;
        }
        gr-account-label:hover {
          box-shadow: var(--elevation-level-1);
          cursor: pointer;
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
                <template
                  is="dom-if"
                  if="[[hasMoreThanOneFile(suggestion.files)]]"
                >
                  <gr-hovercard hidden="[[suggestion.expanded]]">
                    <owner-group-file-list
                      files="[[suggestion.files]]"
                    >
                    </owner-group-file-list>
                  </gr-hovercard>
                </template>
              <span>
              <owner-group-file-list
                hidden="[[!suggestion.expanded]]"
                files="[[suggestion.files]]"
              ></owner-group-file-list>
            </div>
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
                  account="[[owner]]"
                  hide-hovercard
                  blurred$="[[!isAdded(owner)]]"
                  on-click="toggleAccount">
                </gr-account-label>
              </template>
            </ul>
          </li>
        </template>
      </ul>
    `;
  }

  static get observers() {
    return ["onInputChanged(restApi, change)"];
  }

  connectedCallback() {
    super.connectedCallback();
    onToggleButtonClicks.push((expanded) => {
      this.hidden = !expanded;
    });
  }

  onInputChanged(restApi, change) {
    if ([restApi, change].includes(undefined)) return;
    this.isLoading = true;
    this.ownerService = CodeOwnerService.getOwnerService(this.restApi, change);

    this.ownerService.getSuggestedOwners().then((suggestedOwners) => {
      this.isLoading = false;
      this.suggestedOwners = suggestedOwners.map((suggestion) => {
        return this.formatSuggestionInfo(suggestion);
      });
    });
  }

  formatSuggestionInfo(suggestion) {
    const res = {};
    res.groupName = suggestion.groupName;
    res.files = suggestion.files.slice();
    res.owners = suggestion.owners.map((owner) => {
      const updatedOwner = Object.assign({}, owner);
      const reviewers = this.change.reviewers.REVIEWER;
      if (
        reviewers &&
        reviewers.find(
          (reviewer) => reviewer._account_id === owner._account_id
        )
      ) {
        updatedOwner.added = true;
      }
      return updatedOwner;
    });
    return res.sort((groupA, groupB) => {
      // Not fullfilled should show at the top
      return 0;
    });
  }

  hasMoreThanOneFile(files) {
    return files.length > 1;
  }

  addAccount(reviewer) {
    this.dispatchEvent(
      new CustomEvent("add-reviewer", {
        detail: {
          reviewer,
        },
        composed: true,
        bubbles: true,
      })
    );
  }

  removeAccount(reviewer) {
    this.dispatchEvent(
      new CustomEvent("remove-reviewer", {
        detail: {
          reviewer,
        },
        composed: true,
        bubbles: true,
      })
    );
  }

  toggleAccount(e) {
    const account = e.currentTarget.account;
    const sId = e.currentTarget.dataset.suggestionIndex;
    const oId = e.currentTarget.dataset.ownerIndex;
    const modifiedOwner = this.suggestedOwners[sId].owners[oId];
    if (account.added || account._pendingAdd) {
      this.removeAccount(account);
    } else {
      this.addAccount(account);
    }
    // update all occurences
    this.suggestedOwners.forEach((suggestion, sId) => {
      suggestion.owners.forEach((owner, oId) => {
        if (owner._account_id === modifiedOwner._account_id) {
          if (account.added || account._pendingAdd) {
            this.set(
              ["suggestedOwners", sId, "owners", oId],
              Object.assign({}, modifiedOwner, { _pendingAdd: false })
            );
          } else {
            this.set(
              ["suggestedOwners", sId, "owners", oId],
              Object.assign({}, modifiedOwner, {
                added: false,
                _pendingAdd: true,
              })
            );
          }
        }
      });
    });
  }

  isAdded(owner) {
    return owner.added || owner._pendingAdd;
  }
}

customElements.define(SuggestOwners.is, SuggestOwners);
