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
import {CodeOwnersModelMixin} from './code-owners-model-mixin.js';
import {
  SuggestionsState,
  SuggestionsType,
} from './code-owners-model.js';
import {getDisplayOwnersGroups} from './suggest-owners-util.js';

const SUGGESTION_POLLING_INTERVAL = 1000;

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
        :host {
          display: block;
          max-height: 500px;
          overflow: auto;
        }
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

export class SuggestOwners extends CodeOwnersModelMixin(Polymer.Element) {
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
          padding: 0 var(--spacing-m);
          margin: var(--spacing-m) 0;
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
        .flex-break {
          height: 0;
          flex-basis: 100%;
        }
        .suggestion-row, .show-all-owners-row {
          display: flex;
          flex-direction: row;
          align-items: flex-start;
        }
        .suggestion-row {
          flex-wrap: wrap;
          border-top: 1px solid var(--border-color);
          padding: var(--spacing-s) 0;
        }
        .show-all-owners-row {
          padding: var(--spacing-m) var(--spacing-xl) var(--spacing-s) 0;
        }
        .show-all-owners-row .loading {
          padding: 0;
        }
        .show-all-owners-row .show-all-label {
          margin-left: auto; /* align label to the right */
        }
        .suggestion-row-indicator {
          margin-right: var(--spacing-s);
          visibility: hidden;
          line-height: 26px;
        }
        .suggestion-row-indicator[visible] {
          visibility: visible;
        }
        .suggestion-row-indicator[visible] iron-icon {
          color: var(--link-color);
          vertical-align: top;
          position: relative;
          --iron-icon-height: 18px;
          --iron-icon-width: 18px;
          top: 4px; /* (26-18)/2 - 26px line-height and 18px icon */
        }
        .suggestion-group-name {
          width: 260px;
          line-height: 26px;
          text-overflow: ellipsis;
          overflow: hidden;
          padding-right: var(--spacing-s);
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
          --account-gap: var(--spacing-xs);
          --negative-account-gap: calc(-1*var(--account-gap));
          margin: var(--negative-account-gap) 0 0 var(--negative-account-gap);
          flex: 1;
        }
        .fetch-error-content,
        .owned-by-all-users-content,
        .no-owners-content {
          line-height: 26px;
          flex: 1;
          padding-left: var(--spacing-m);
        }

        .owned-by-all-users-content iron-icon {
          width: 16px;
          height: 16px;
          padding-top: 5px;
        }
        
        .fetch-error-content {
          color: var(--error-text-color);
        }
        .no-owners-content a {
          padding-left: var(--spacing-s);
        }
        .no-owners-content a iron-icon {
          width: 16px;
          height: 16px;
          padding-top: 5px;
        }
        gr-account-label {
          display: inline-block;
          padding: var(--spacing-xs) var(--spacing-m);
          user-select: none;
          border: 1px solid transparent;
          --label-border-radius: 8px;
          /* account-max-length defines the max text width inside account-label.
           With 60px the gr-account-label always has width <= 100px and 5 labels
           are always fit in a single row */
          --account-max-length: 60px;
          border: 1px solid var(--border-color);
          margin: var(--account-gap) 0 0 var(--account-gap)
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
        gr-hovercard {
          max-width: 800px;
        }
        .loading {
          display: flex;
          align-content: center;
          align-items: center;
          justify-content: center;
          padding: var(--spacing-m);
        }
        .loadingSpin {
          width: 18px;
          height: 18px;
          margin-right: var(--spacing-m);
        }
      </style>
      <ul class="suggestion-container">
        <li class="show-all-owners-row">
          <p class="loading" hidden="[[!isLoading]]">
            <span class="loadingSpin"></span>
            [[progressText]]
          </p>
          <label class="show-all-label">
            <input
              id="showAllOwnersCheckbox"
              type="checkbox"
              checked="{{_showAllOwners::change}}"
            />
            Show all owners
          </label>
        </li>
      </ul>
      <ul class="suggestion-container">
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
              <div class="fetch-error-content">
                [[suggestion.error]]
                <a on-click="_showErrorDetails"
              </div>
            </template>
            <template is="dom-if" if="[[!suggestion.error]]">
              <template is="dom-if" if="[[!_areOwnersFound(suggestion.owners)]]">
                <div class="no-owners-content">
                  <span>Not found</span>
                  <a on-click="_reportDocClick" href="https://gerrit.googlesource.com/plugins/code-owners/+/HEAD/resources/Documentation/how-to-use.md#no-code-owners-found" target="_blank">
                    <iron-icon icon="gr-icons:help-outline" title="read documentation"></iron-icon>
                  </a>
                </div>
              </template>
              <template is="dom-if" if="[[suggestion.owners.owned_by_all_users]]">
                <div class="owned-by-all-users-content">
                  <iron-icon icon="gr-icons:info" ></iron-icon>
                  <span>[[_getOwnedByAllUsersContent(isLoading, suggestedOwners)]]</span>
                </div>
              </template>
              <template is="dom-if" if="[[!suggestion.owners.owned_by_all_users]]">
                <template is="dom-if" if="[[_showAllOwners]]">
                  <div class="flex-break"></div>
                </template>
                <ul class="suggested-owners">
                  <template
                    is="dom-repeat"
                    items="[[suggestion.owners.code_owners]]"
                    as="owner"
                    index-as="ownerIndex"
                  ><!--
                    --><gr-account-label
                      data-suggestion-index$="[[suggestionIndex]]"
                      data-owner-index$="[[ownerIndex]]"
                      account="[[owner.account]]"
                      selected$="[[isSelected(owner)]]"
                      on-click="toggleAccount">
                    </gr-account-label><!--
                --></template>
                </ul>
              </template>
            </template>
          </li>
        </template>
      </ul>
    `;
  }

  static get properties() {
    return {
      // @internal attributes
      hidden: {
        type: Boolean,
        value: true,
        reflectToAttribute: true,
        computed: '_isHidden(model.showSuggestions)',
      },
      suggestedOwners: Array,
      isLoading: {
        type: Boolean,
        value: true,
      },
      reviewers: {
        type: Array,
      },
      _reviewersIdSet: {
        type: Object,
        computed: '_getReviewersIdSet(reviewers)',
      },
      pendingReviewers: Array,
      _showAllOwners: {
        type: Boolean,
        value: false,
        observer: '_showAllOwnersChanged',
      },
      _allOwnersByPathMap: {
        type: Object,
        computed:
            `_getOwnersByPathMap(model.suggestionsByTypes.${SuggestionsType.ALL_SUGGESTIONS}.files)`,
      },
    };
  }

  static get observers() {
    return [
      '_onReviewerChanged(reviewers)',
      '_onShowSuggestionsChanged(model.showSuggestions)',
      '_onShowSuggestionsTypeChanged(model.showSuggestions,' +
        'model.selectedSuggestionsType)',
      '_onSuggestionsStateChanged(model.selectedSuggestions.state)',
      '_onSuggestionsFilesChanged(model.selectedSuggestions.files, ' +
        '_allOwnersByPathMap, _reviewersIdSet, model.selectedSuggestionsType,' +
        'model.selectedSuggestions.state)',
      '_onSuggestionsLoadProgressChanged(' +
        'model.selectedSuggestions.loadProgress)',
    ];
  }

  constructor() {
    super();
    // To prevent multiple reporting when switching back and forth showAllOwners
    this.reportedEvents = {};
    for (const suggestionType of Object.values(SuggestionsType)) {
      this.reportedEvents[suggestionType] = {
        fetchingStart: false,
        fetchingFinished: false,
      };
    }
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    this._stopUpdateProgressTimer();
    if (this.modelLoader) {
      this.modelLoader.pauseActiveSuggestedOwnersLoading();
    }
  }

  _onShowSuggestionsChanged(showSuggestions) {
    if (!showSuggestions) {
      return;
    }
    // this is more of a hack to let review input lose focus
    // to avoid suggestion dropdown
    // gr-autocomplete has a internal state for tracking focus
    // that will be canceled if any click happens outside of
    // it's target
    // Can not use `this.async` as it's only available in
    // legacy element mixin which not used in this plugin.
    Polymer.Async.timeOut.run(() => this.click(), 100);
  }

  _onShowSuggestionsTypeChanged(showSuggestion, selectedSuggestionsType) {
    if (!showSuggestion) {
      this.modelLoader.pauseActiveSuggestedOwnersLoading();
      return;
    }
    this.modelLoader.loadSuggestions(selectedSuggestionsType);
    // The progress is updated at the next _progressUpdateTimer tick.
    // Without excplicit call to updateLoadSuggestionsProgress it looks like
    // a slow reaction to checkbox.
    this.modelLoader.updateLoadSelectedSuggestionsProgress();

    if (!this.reportedEvents[selectedSuggestionsType].fetchingStart) {
      this.reportedEvents[selectedSuggestionsType].fetchingStart = true;
      this.reporting.reportLifeCycle('owners-suggestions-fetching-start', {
        type: selectedSuggestionsType,
      });
    }
  }

  _startUpdateProgressTimer() {
    if (this._progressUpdateTimer) return;
    this._progressUpdateTimer = setInterval(() => {
      this.modelLoader.updateLoadSelectedSuggestionsProgress();
    }, SUGGESTION_POLLING_INTERVAL);
  }

  _stopUpdateProgressTimer() {
    if (!this._progressUpdateTimer) return;
    clearInterval(this._progressUpdateTimer);
    this._progressUpdateTimer = undefined;
  }

  _onSuggestionsStateChanged(state) {
    this._stopUpdateProgressTimer();
    if (state === SuggestionsState.Loading) {
      this._startUpdateProgressTimer();
    }
    this.isLoading = state === SuggestionsState.Loading;
  }

  _isHidden(showSuggestions) {
    return !showSuggestions;
  }

  loadPropertiesAfterModelChanged() {
    super.loadPropertiesAfterModelChanged();
    this._stopUpdateProgressTimer();
    this.modelLoader.loadAreAllFilesApproved();
  }

  _getReviewersIdSet(reviewers) {
    return new Set((reviewers || []).map(account => account._account_id));
  }

  _onSuggestionsFilesChanged(files, allOwnersByPathMap, reviewersIdSet,
      selectedSuggestionsType, suggestionsState) {
    if (files === undefined || allOwnersByPathMap === undefined ||
        reviewersIdSet === undefined || selectedSuggestionsType === undefined ||
        suggestionsState === undefined) return;

    const groups = getDisplayOwnersGroups(
        files, allOwnersByPathMap, reviewersIdSet,
        selectedSuggestionsType !== SuggestionsType.ALL_SUGGESTIONS);
    // The updateLoadSuggestionsProgress method also updates suggestions
    this._updateSuggestions(groups);
    this._updateAllChips(this._currentReviewers);

    if (suggestionsState !== SuggestionsState.Loaded) return;
    if (!this.reportedEvents[selectedSuggestionsType].fetchingFinished) {
      this.reportedEvents[selectedSuggestionsType].fetchingFinished = true;
      const reportDetails = groups.reduce((details, cur) => {
        details.totalGroups++;
        details.stats.push([cur.files.length,
          cur.owners && cur.owners.code_owners ?
            cur.owners.code_owners.length : 0]);
        return details;
      }, {totalGroups: 0, stats: [], type: selectedSuggestionsType});
      this.reporting.reportLifeCycle(
          'owners-suggestions-fetching-finished', reportDetails);
    }
  }

  _getOwnersByPathMap(files) {
    return new Map((files || [])
        .filter(file => !file.info.error && file.info.owners)
        .map(file => [file.path, file.info.owners])
    );
  }

  _onSuggestionsLoadProgressChanged(progress) {
    this.progressText = progress;
  }

  _updateSuggestions(suggestions) {
    // update group names and files, no modification on owners or error
    const suggestedOwners = suggestions.map(suggestion => {
      return this.formatSuggestionInfo(suggestion);
    });
    // move owned_by_all_users to the bottom:
    const index = suggestedOwners
        .findIndex(suggestion => suggestion.owners.owned_by_all_users);
    if (index >= 0) {
      suggestedOwners.push(suggestedOwners.splice(index, 1)[0]);
    }
    this.suggestedOwners = suggestedOwners;
  }

  _onReviewerChanged(reviewers) {
    this._currentReviewers = reviewers;
    this._updateAllChips(reviewers);
  }

  formatSuggestionInfo(suggestion) {
    const res = {};
    res.groupName = suggestion.groupName;
    res.files = suggestion.files.slice();
    if (suggestion.owners) {
      const codeOwners = (suggestion.owners.code_owners || []).map(owner => {
        const updatedOwner = {...owner};
        const reviewers = this.change.reviewers.REVIEWER;
        if (reviewers &&
            reviewers.find(
                reviewer => reviewer._account_id === owner._account_id)
        ) {
          updatedOwner.selected = true;
        }
        return updatedOwner;
      });
      res.owners = {
        owned_by_all_users: !!suggestion.owners.owned_by_all_users,
        code_owners: codeOwners,
      };
    } else {
      res.owners = {
        owned_by_all_users: false,
        code_owners: [],
      };
    }

    res.error = suggestion.error;
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
        .owners.code_owners[grAccountLabel.dataset.ownerIndex];
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
      suggestion.owners.code_owners.forEach((owner, oId) => {
        if (accounts.some(
            account => account._account_id === owner.account._account_id)) {
          this.set(
              ['suggestedOwners', sId, 'owners', 'code_owners', oId],
              {...owner,
                selected: true,
              }
          );
          hasSelected = true;
        } else {
          this.set(
              ['suggestedOwners', sId, 'owners', 'code_owners', oId],
              {...owner, selected: false}
          );
        }
      });
      const nonServiceUser = account =>
        !account.tags || account.tags.indexOf('SERVICE_USER') < 0;
      if (suggestion.owners.owned_by_all_users &&
          accounts.some(nonServiceUser)) {
        hasSelected = true;
      }
      this.set(['suggestedOwners', sId, 'hasSelected'], hasSelected);
    });
  }

  isSelected(owner) {
    return owner.selected;
  }

  _reportDocClick() {
    this.reporting.reportInteraction('code-owners-doc-click',
        {section: 'no owners found'});
  }

  _areOwnersFound(owners) {
    return owners.code_owners.length > 0 || !!owners.owned_by_all_users;
  }

  _getOwnedByAllUsersContent(isLoading, suggestedOwners) {
    if (isLoading) {
      return 'Any user can approve';
    }
    // If all users own all the files in the change suggestedOwners.length === 1
    // (suggestedOwners - collection of owners groupbed by owners)
    return suggestedOwners && suggestedOwners.length === 1 ?
      'Any user can approve. Please select a user manually' :
      'Any user from the other files can approve';
  }

  _showAllOwnersChanged(showAll) {
    // The first call to this method happens before model is set.
    if (!this.model) return;
    this.model.setSelectedSuggestionType(showAll ?
      SuggestionsType.ALL_SUGGESTIONS : SuggestionsType.BEST_SUGGESTIONS);
  }
}

customElements.define(SuggestOwners.is, SuggestOwners);
