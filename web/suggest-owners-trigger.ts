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
import {css, html, LitElement, nothing} from 'lit';
import {customElement} from 'lit/decorators.js';

const base = CodeOwnersModelMixin(LitElement);

export const SUGGEST_OWNERS_TRIGGER = 'suggest-owners-trigger';

@customElement(SUGGEST_OWNERS_TRIGGER)
export class SuggestOwnersTrigger extends base {
  static override get styles() {
    return [
      css`
        :host {
          display: flex;
        }
        a {
          text-decoration: none;
        }
        gr-button {
          --gr-button-padding: var(--spacing-s);
        }
        .extras {
          padding-top: var(--spacing-s);
        }
      `,
    ];
  }

  override render() {
    if (!this.status?.enabled) return nothing;
    return html`
      <gr-button
        link
        @click=${this.toggleControlContent}
        has-tooltip
        title="Suggest owners for your change"
      >
        ${this.computeButtonText()}
      </gr-button>
      <span class="extras">
        <a
          @click=${this.reportDocClick}
          href="https://gerrit.googlesource.com/plugins/code-owners/+/HEAD/resources/Documentation/how-to-use.md"
          target="_blank"
        >
          <gr-icon icon="help" title="read documentation"></gr-icon>
        </a>
      </span>
    `;
  }

  override loadPropertiesAfterModelChanged() {
    super.loadPropertiesAfterModelChanged();
    this.modelLoader?.loadUserRole();
    this.modelLoader?.loadPluginStatus();
    this.modelLoader?.loadAreAllFilesApproved();
    this.modelLoader?.loadBranchConfig();
  }

  toggleControlContent() {
    this.model?.setShowSuggestions(!this.showSuggestions);
    this.reporting?.reportInteraction('toggle-suggest-owners', {
      expanded: this.showSuggestions,
      user_role: this.userRole ? this.userRole : 'UNKNOWN',
    });
  }

  computeButtonText() {
    return this.showSuggestions ? 'Hide owners' : 'Suggest owners';
  }

  private reportDocClick() {
    this.reporting?.reportInteraction('code-owners-doc-click');
  }
}
