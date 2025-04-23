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
import {SuggestionsState, SuggestionsType} from './code-owners-model';
import {getDisplayOwnersGroups, GroupedFiles} from './suggest-owners-util';
import {customElement, property, state} from 'lit/decorators.js';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {when} from 'lit/directives/when';
import {ifDefined} from 'lit/directives/if-defined';
import {CodeOwnerInfo, CodeOwnersInfo, FetchedFile} from './code-owners-api';
import {
  AccountId,
  AccountInfo,
  GroupInfo,
} from '@gerritcodereview/typescript-api/rest-api';

const SUGGESTION_POLLING_INTERVAL = 1000;

export const OWNER_GROUP_FILE_LIST = 'owner-group-file-list';

@customElement(OWNER_GROUP_FILE_LIST)
export class OwnerGroupFileList extends LitElement {
  @property({type: Array})
  files?: Array<FetchedFile>;

  static override get styles() {
    return [
      css`
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
      `,
    ];
  }

  override render() {
    return html`
      <ul>
        ${this.files?.map(file => this.renderFile(file))}
      </ul>
    `;
  }

  private renderFile(file: FetchedFile) {
    return html`
      <li>
        ${this.computeFilePath(file)}
        <strong>${this.computeFileName(file)}</strong>
        ${when(
          !!file.status,
          () => html`
            <span class=${ifDefined(file.status)}> ${file.status} </span>
          `
        )}
      </li>
    `;
  }

  computeFilePath(file: FetchedFile) {
    const parts = file.path.split('/');
    return parts.slice(0, parts.length - 1).join('/') + '/';
  }

  computeFileName(file: FetchedFile) {
    const parts = file.path.split('/');
    return parts.pop();
  }
}

const base = CodeOwnersModelMixin(LitElement);
export const SUGGEST_OWNERS = 'suggest-owners';
@customElement(SUGGEST_OWNERS)
export class SuggestOwners extends base {
  private reportedEvents: Map<
    SuggestionsType,
    {
      fetchingStart: boolean;
      fetchingFinished: boolean;
    }
  >;

  // Bound by the extension point.
  @property({type: Array})
  reviewers?: Array<AccountInfo | GroupInfo>;

  @state()
  suggestedOwners?: Array<GroupedFiles>;

  @state()
  showAllOwners = false;

  private progressUpdateTimer?: number;

  constructor() {
    super();
    this.reportedEvents = new Map();
    for (const suggestionType of Object.values(SuggestionsType)) {
      this.reportedEvents.set(suggestionType, {
        fetchingStart: false,
        fetchingFinished: false,
      });
    }
  }

  static override get styles() {
    return [
      css`
        :host([show-suggestions]) {
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
          max-height: 300px;
          overflow-y: auto;
        }
        .flex-break {
          height: 0;
          flex-basis: 100%;
        }
        .suggestion-row,
        .show-all-owners-row {
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
        .suggestion-row-indicator[visible] gr-icon {
          color: var(--link-color);
          vertical-align: top;
          position: relative;
          font-size: 18px;
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
          --negative-account-gap: calc(-1 * var(--account-gap));
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

        .owned-by-all-users-content gr-icon {
          font-size: 16px;
          padding-top: 5px;
        }

        .fetch-error-content {
          color: var(--error-text-color);
        }
        .no-owners-content a {
          padding-left: var(--spacing-s);
        }
        .no-owners-content a gr-icon {
          font-size: 16px;
          padding-top: 5px;
        }
        gr-account-label {
          display: inline-block;
          padding: var(--spacing-xs) var(--spacing-m);
          user-select: none;
          border: 1px solid transparent;
          --label-border-radius: 8px;
          /* account-max-length defines the max text width inside account-label.
           With 59 the gr-account-label always has width <= 100px and 5 labels
           are always fit in a single row */
          --account-max-length: 59px;
          border: 1px solid var(--border-color);
          margin: var(--account-gap) 0 0 var(--account-gap);
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
        /* Copied from shared-styles */
        ul {
          border: 0;
          box-sizing: border-box;
          font-size: 100%;
          margin: 0;
          padding: 0;
          vertical-align: baseline;
        }
        *::after,
        *::before {
          box-sizing: border-box;
        }
      `,
    ];
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    this.stopUpdateProgressTimer();
    this.modelLoader?.pauseActiveSuggestedOwnersLoading();
  }

  protected override willUpdate(changedProperties: PropertyValues): void {
    super.willUpdate(changedProperties);
    if (changedProperties.has('showAllOwners')) {
      this.showAllOwnersChanged();
    }
    if (changedProperties.has('reviewers')) {
      this.onReviewerChanged();
    }
    if (changedProperties.has('showSuggestions')) {
      this.onShowSuggestionsChanged();
    }
    if (
      changedProperties.has('showSuggestions') ||
      changedProperties.has('selectedSuggestionsType')
    ) {
      this.onShowSuggestionsTypeChanged();
    }
    if (changedProperties.has('selectedSuggestionsState')) {
      this.onSuggestionsStateChanged();
    }
    if (
      changedProperties.has('selectedSuggestionsFiles') ||
      changedProperties.has('selectedSuggestionsType') ||
      changedProperties.has('selectedSuggestionsState') ||
      changedProperties.has('reviewers')
    ) {
      this.onSuggestionsFilesChanged();
    }
  }

  override render() {
    if (!this.showSuggestions) return nothing;
    return html`
      <ul class="suggestion-container">
        <li class="show-all-owners-row">
          ${this.renderLoading()} ${this.renderShowAllOwnersCheckbox()}
        </li>
      </ul>
      <ul class="suggestion-container">
        ${when(
          this.suggestedOwners && this.suggestedOwners.length > 0,
          () =>
            (this.suggestedOwners ?? []).map((s, i) =>
              this.renderSuggestion(s, i)
            ),
          () => html`<li class="suggestion-row">No owners found</li>`
        )}
      </ul>
    `;
  }

  private renderLoading() {
    const isLoading =
      this.selectedSuggestionsState === SuggestionsState.Loading;
    if (!isLoading) return nothing;
    return html`
      <p class="loading">
        <span class="loadingSpin"><gr-icon icon="sync"></gr-icon></span>
        ${this.selectedSuggestionsLoadProgress}
      </p>
    `;
  }

  private renderShowAllOwnersCheckbox() {
    return html`
      <label class="show-all-label">
        <input
          id="showAllOwnersCheckbox"
          type="checkbox"
          .checked=${this.showAllOwners}
          @click=${() => {
            this.showAllOwners = !this.showAllOwners;
          }}
        />
        Show all owners
      </label>
    `;
  }

  private renderSuggestion(suggestion: GroupedFiles, suggestionIndex: number) {
    return html`
      <li class="suggestion-row">
        <div
          class="suggestion-row-indicator"
          ?visible=${suggestion.hasSelected}
        >
          <gr-icon filled icon="check_circle"></gr-icon>
        </div>
        ${this.renderSuggestionGroupName(suggestion)}
        ${this.renderSuggestionOwners(suggestion, suggestionIndex)}
      </li>
    `;
  }

  private renderSuggestionGroupName(suggestion: GroupedFiles) {
    return html`
      <div class="suggestion-group-name">
        <div class="group-name-content">
          <span class="group-name"> ${suggestion.groupName.name} </span>
          ${when(
            suggestion.groupName.prefix,
            () => html`
              <span class="group-name-prefix">
                (${suggestion.groupName.prefix})
              </span>
            `
          )}
          ${when(
            !suggestion.expanded,
            () => html`
              <gr-hovercard>
                <owner-group-file-list
                  .files=${suggestion.files}
                ></owner-group-file-list>
              </gr-hovercard>
            `
          )}
        </div>
        ${when(
          suggestion.expanded,
          () => html`
            <owner-group-file-list
              .files=${suggestion.files}
            ></owner-group-file-list>
          `
        )}
      </div>
    `;
  }

  private renderSuggestionOwners(
    suggestion: GroupedFiles,
    suggestionIndex: number
  ) {
    if (suggestion.error) {
      return html` <div class="fetch-error-content">${suggestion.error}</div> `;
    }

    return html`
      ${this.renderSuggestionOwnersNotFound(suggestion)}
      ${this.renderSuggestedOwners(suggestion, suggestionIndex)}
    `;
  }

  private renderSuggestionOwnersNotFound(suggestion: GroupedFiles) {
    if (this.areOwnersFound(suggestion.owners)) return nothing;
    return html`
      <div class="no-owners-content">
        <span>Not found</span>
        <a
          @click=${this.reportDocClick}
          href="https://gerrit.googlesource.com/plugins/code-owners/+/HEAD/resources/Documentation/how-to-use.md#no-code-owners-found"
          target="_blank"
        >
          <gr-icon icon="help" title="read documentation"></gr-icon>
        </a>
      </div>
    `;
  }

  private renderSuggestedOwners(
    suggestion: GroupedFiles,
    suggestionIndex: number
  ) {
    if (suggestion.owners?.owned_by_all_users) {
      return html`
        <div class="owned-by-all-users-content">
          <gr-icon icon="info" filled></gr-icon>
          <span>${this.getOwnedByAllUsersContent()}</span>
        </div>
      `;
    }

    return html`
      ${when(this.showAllOwners, () => html`<div class="flex-break"></div>`)}
      <ul class="suggested-owners">
        ${(suggestion.owners?.code_owners ?? []).map((owner, ownerIndex) =>
          this.renderSuggestedOwner(owner, suggestionIndex, ownerIndex)
        )}
      </ul>
    `;
  }

  private renderSuggestedOwner(
    owner: CodeOwnerInfo,
    suggestionIndex: number,
    ownerIndex: number
  ) {
    return html`<gr-account-label
      .account=${owner.account}
      ?selected=${!!owner.selected}
      @click=${() => this.toggleAccount(suggestionIndex, ownerIndex)}
    ></gr-account-label>`;
  }

  private onShowSuggestionsChanged() {
    if (!this.showSuggestions) {
      return;
    }
    // this is more of a hack to let review input lose focus
    // to avoid suggestion dropdown
    // gr-autocomplete has a internal state for tracking focus
    // that will be canceled if any click happens outside of
    // it's target
    window.setTimeout(() => this.click(), 100);
  }

  private onShowSuggestionsTypeChanged() {
    if (!this.showSuggestions) {
      this.modelLoader?.pauseActiveSuggestedOwnersLoading();
      return;
    }
    const selectedSuggestionsType = this.selectedSuggestionsType;
    if (selectedSuggestionsType === undefined) {
      return;
    }
    this.modelLoader?.loadSuggestions(selectedSuggestionsType);
    // The progress is updated at the next _progressUpdateTimer tick.
    // Without explicit call to updateLoadSuggestionsProgress it looks like
    // a slow reaction to checkbox.
    this.modelLoader?.updateLoadSelectedSuggestionsProgress();

    if (!this.reportedEvents.get(selectedSuggestionsType)?.fetchingStart) {
      this.reportedEvents.get(selectedSuggestionsType)!.fetchingStart = true;
      this.reporting?.reportLifeCycle('owners-suggestions-fetching-start', {
        type: selectedSuggestionsType,
      });
    }
  }

  _startUpdateProgressTimer() {
    if (this.progressUpdateTimer) return;
    this.progressUpdateTimer = window.setInterval(() => {
      this.modelLoader?.updateLoadSelectedSuggestionsProgress();
    }, SUGGESTION_POLLING_INTERVAL);
  }

  private stopUpdateProgressTimer() {
    if (!this.progressUpdateTimer) return;
    window.clearInterval(this.progressUpdateTimer);
    this.progressUpdateTimer = undefined;
  }

  private onSuggestionsStateChanged() {
    this.stopUpdateProgressTimer();
    if (this.selectedSuggestionsState === SuggestionsState.Loading) {
      this._startUpdateProgressTimer();
    }
  }

  override loadPropertiesAfterModelChanged() {
    super.loadPropertiesAfterModelChanged();
    this.stopUpdateProgressTimer();
    this.modelLoader?.loadAreAllFilesApproved();
  }

  private getReviewersIdSet(): Set<AccountId> {
    return new Set(
      (this.reviewers ?? [])
        // Ugly hack, but we only care about accounts.
        .map(account => (account as unknown as AccountInfo)._account_id)
        .filter(account_id => account_id !== undefined)
    ) as Set<AccountId>;
  }

  private onSuggestionsFilesChanged() {
    if (
      this.selectedSuggestionsFiles === undefined ||
      this.reviewers === undefined ||
      this.selectedSuggestionsType === undefined ||
      this.selectedSuggestionsState === undefined
    )
      return;

    const allOwners =
      this.model?.getSuggestionsByType(SuggestionsType.ALL_SUGGESTIONS)
        ?.files ?? [];

    const allOwnersByPathMap = this.getOwnersByPathMap(allOwners);
    const reviewersIdSet = this.getReviewersIdSet();
    const selectedSuggestionsType = this.selectedSuggestionsType;
    const suggestionsState = this.selectedSuggestionsState;

    const groups = getDisplayOwnersGroups(
      this.selectedSuggestionsFiles,
      allOwnersByPathMap,
      reviewersIdSet,
      selectedSuggestionsType !== SuggestionsType.ALL_SUGGESTIONS
    );
    // The updateLoadSuggestionsProgress method also updates suggestions
    this.updateSuggestions(groups);
    this.updateAllChips(this.reviewers);

    if (suggestionsState !== SuggestionsState.Loaded) return;
    if (!this.reportedEvents.get(selectedSuggestionsType)!.fetchingFinished) {
      this.reportedEvents.get(selectedSuggestionsType)!.fetchingFinished = true;
      const reportDetails = groups.reduce(
        (details, cur) => {
          details.totalGroups++;
          details.stats.push([
            cur.files.length,
            cur.owners && cur.owners.code_owners
              ? cur.owners.code_owners.length
              : 0,
          ]);
          return details;
        },
        {
          totalGroups: 0,
          stats: [] as Array<Array<number>>,
          type: selectedSuggestionsType,
        }
      );
      this.reporting?.reportLifeCycle(
        'owners-suggestions-fetching-finished',
        reportDetails
      );
    }
  }

  private getOwnersByPathMap(files?: Array<FetchedFile>) {
    return new Map(
      (files || [])
        .filter(file => !file.info.error && file.info.owners)
        .map(file => [file.path, file.info.owners])
    );
  }

  private updateSuggestions(suggestions: Array<GroupedFiles>) {
    // update group names and files, no modification on owners or error
    const suggestedOwners = suggestions.map(suggestion =>
      this.formatSuggestionInfo(suggestion)
    );
    // move owned_by_all_users to the bottom:
    const index = suggestedOwners.findIndex(
      suggestion => suggestion.owners?.owned_by_all_users
    );
    if (index >= 0) {
      suggestedOwners.push(suggestedOwners.splice(index, 1)[0]);
    }
    this.suggestedOwners = suggestedOwners;
  }

  onReviewerChanged() {
    this.updateAllChips(this.reviewers);
  }

  formatSuggestionInfo(suggestion: GroupedFiles): GroupedFiles {
    const res = {
      groupName: suggestion.groupName,
      files: suggestion.files.slice(),
      owners: undefined as CodeOwnersInfo | undefined,
      error: undefined as Error | undefined,
    } as GroupedFiles;
    if (suggestion.owners) {
      const codeOwners = (suggestion.owners.code_owners || []).map(owner => {
        const updatedOwner = {...owner};
        const reviewers = this.change?.reviewers.REVIEWER;
        if (
          reviewers &&
          reviewers.find(
            reviewer => reviewer._account_id === owner.account?._account_id
          )
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

  addAccount(owner: CodeOwnerInfo) {
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
    this.reporting?.reportInteraction('add-reviewer');
  }

  removeAccount(owner: CodeOwnerInfo) {
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
    this.reporting?.reportInteraction('remove-reviewer');
  }

  toggleAccount(suggestionIndex: number, ownerIndex: number) {
    const owner =
      this.suggestedOwners![suggestionIndex]?.owners?.code_owners[ownerIndex];
    if (!owner) return;
    if (owner.selected) {
      this.removeAccount(owner);
    } else {
      this.addAccount(owner);
    }
  }

  private updateAllChips(accounts: Array<AccountInfo> | undefined) {
    if (!this.suggestedOwners || !accounts) return;
    // update all occurences
    this.suggestedOwners.forEach((suggestion, sId) => {
      let hasSelected = false;
      suggestion.owners?.code_owners.forEach((owner, oId) => {
        if (
          accounts.some(
            account => account._account_id === owner!.account!._account_id
          )
        ) {
          this.suggestedOwners![sId].owners!.code_owners[oId].selected = true;
          hasSelected = true;
        } else {
          this.suggestedOwners![sId].owners!.code_owners[oId].selected = false;
        }
      });
      const nonServiceUser = (account: AccountInfo) =>
        !account.tags || account.tags.indexOf('SERVICE_USER') < 0;
      if (
        suggestion.owners?.owned_by_all_users &&
        accounts.some(nonServiceUser)
      ) {
        hasSelected = true;
      }
      this.suggestedOwners![sId].hasSelected = hasSelected;
    });
  }

  private reportDocClick() {
    this.reporting?.reportInteraction('code-owners-doc-click', {
      section: 'no owners found',
    });
  }

  private areOwnersFound(owners: CodeOwnersInfo | undefined) {
    return (
      owners && (owners.code_owners.length > 0 || !!owners.owned_by_all_users)
    );
  }

  private getOwnedByAllUsersContent() {
    if (this.selectedSuggestionsState === SuggestionsState.Loading) {
      return 'All users are considered owners';
    }
    // If all users own all the files in the change suggestedOwners.length === 1
    // (suggestedOwners - collection of owners groupbed by owners)
    return this.suggestedOwners && this.suggestedOwners.length === 1
      ? 'All users are considered owners. Please select a user manually'
      : 'Any user from the other files can approve';
  }

  private showAllOwnersChanged() {
    // The first call to this method happens before model is set.
    if (!this.model) return;
    this.model.setSelectedSuggestionType(
      this.showAllOwners
        ? SuggestionsType.ALL_SUGGESTIONS
        : SuggestionsType.BEST_SUGGESTIONS
    );
  }
}
