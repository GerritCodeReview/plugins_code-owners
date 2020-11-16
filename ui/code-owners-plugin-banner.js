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
import {PluginState} from './code-owners-service.js';
import {CodeOwnersMixin} from './code-owners-mixin.js';

export class PluginStateChangedListener {
  constructor() {
    this.latestPluginStateInfo = undefined;
    this.subscribers = [];
  }

  install() {
    document.addEventListener('code-owners-plugin-state-changed', e => {
      this.latestPluginStateInfo = {
        state: e.detail.state,
        failedReason: e.detail.failedReason,
      };
      this.subscribers.forEach(el => this._updateSubscriber(el));
    });
  }

  subscribeElement(el) {
    this.subscribers.push(el);
    this._updateSubscriber(el);
  }

  unsubscribeElement(el) {
    this.subscribers = this.subscribeElement.filter(sEl => sEl != el);
  }

  _updateSubscriber(el) {
    el.pluginStateInfo = this.latestPluginStateInfo;
  }
}

export class CodeOwnersBanner extends Polymer.Element {
  static get is() { return 'gr-code-owners-banner'; }

  static get template() {
    return Polymer.html`
      <style include="shared-styles">
        :host {
          display: block;
          overflow: hidden;
          background: red;
        }
        .text {        
          color: white;
          font-family: var(--header-font-family);
          font-size: var(--font-size-h3);
          font-weight: var(--font-weight-h3);
          line-height: var(--line-height-h3);
          margin-left: var(--spacing-l);
        }
      </style>
      <span class="text">Error: Code-owners plugin has failed</span>
      <gr-button link on-click="_showFailDetails">
        Details
      </gr-button>
    `;
  }

  static get properties() {
    return {
      // @internal attributes
      hidden: {
        type: Boolean,
        value: true,
        reflectToAttribute: true,
        computed: '_computeHidden(pluginStateInfo.state)',
      },
      pluginStateInfo: {
        type: Object,
      },
    };
  }

  _computeHidden(pluginState) {
    return pluginState !== PluginState.Failed;
  }

  _showFailDetails() {
    this.dispatchEvent(
        new CustomEvent('show-error', {
          detail: {message: this.pluginStateInfo.failedReason},
          composed: true,
          bubbles: true,
        })
    );
  }
}

customElements.define(CodeOwnersBanner.is, CodeOwnersBanner);

export class CodeOwnersPluginStatusNotifier extends
  CodeOwnersMixin(Polymer.Element) {
  static get is() {
    return 'owners-plugin-status-notifier';
  }

  static get observers() {
    return [
      '_pluginStateChanged(ownersState.pluginState)',
    ];
  }

  _pluginStateChanged(state) {
    this.dispatchEvent(new CustomEvent('code-owners-plugin-state-changed', {
      detail: {
        state,
        failedReason: this.ownersState.failedReason,
      },
      composed: true,
      bubbles: true,
    }));
  }

  _loadDataAfterStateChanged() {
    this.ownerService.ensureIsCodeOwnerEnabledLoaded();
  }
}

customElements.define(CodeOwnersPluginStatusNotifier.is,
    CodeOwnersPluginStatusNotifier);
