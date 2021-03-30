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
import {PluginState} from './code-owners-model.js';

export class SuggestOwnersTrigger extends
  CodeOwnersModelMixin(Polymer.Element) {
  static get is() {
    return 'suggest-owners-trigger';
  }

  static get properties() {
    return {
      hidden: {
        type: Boolean,
        computed: '_computeHidden(model.pluginStatus, model.branchConfig)',
        reflectToAttribute: true,
      },
    };
  }

  static get template() {
    return Polymer.html`
        <style include="shared-styles">
          :host {
            display: flex;
          }
          a {
            text-decoration: none;
          }
          gr-button {
            --padding: var(--spacing-xs) var(--spacing-s);
          }
        </style>
        <gr-button
          link
          on-click="toggleControlContent"
          has-tooltip
          title="Suggest owners for your change"
        >
          [[computeButtonText(model.showSuggestions)]]
        </gr-button>
        <span>
          <a on-click="_reportBugClick" href="https://bugs.chromium.org/p/gerrit/issues/entry?template=code-owners-plugin" target="_blank">
            <iron-icon icon="gr-icons:bug" title="report a problem"></iron-icon>
          </a>
          <a on-click="_reportDocClick" href="https://gerrit.googlesource.com/plugins/code-owners/+/master/resources/Documentation/how-to-use.md" target="_blank">
            <iron-icon icon="gr-icons:help-outline" title="read documentation"></iron-icon>
          </a>
        </span>
      `;
  }

  loadPropertiesAfterModelChanged() {
    super.loadPropertiesAfterModelChanged();
    this.modelLoader.loadUserRole();
    this.modelLoader.loadPluginStatus();
    this.modelLoader.loadAreAllFilesApproved();
    this.modelLoader.loadBranchConfig();
  }

  _computeHidden(pluginStatus, branchConfig) {
    if (pluginStatus === undefined || branchConfig === undefined) {
      return true;
    }
    return !!branchConfig.no_code_owners_defined ||
        pluginStatus.state !== PluginState.Enabled;
  }

  toggleControlContent() {
    this.model.setShowSuggestions(!this.model.showSuggestions);
    this.reporting.reportInteraction('toggle-suggest-owners', {
      expanded: this.expanded,
      user_role: this.model.userRole ?
        this.model.userRole : 'UNKNOWN',
    });
  }

  computeButtonText(expanded) {
    return expanded ? 'Hide owners' : 'Suggest owners';
  }

  _reportDocClick() {
    this.reporting.reportInteraction('code-owners-doc-click');
  }

  _reportBugClick() {
    this.reporting.reportInteraction('code-owners-bug-click');
  }
}

customElements.define(SuggestOwnersTrigger.is, SuggestOwnersTrigger);
