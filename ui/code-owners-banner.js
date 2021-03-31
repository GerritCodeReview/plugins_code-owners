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
import {PluginState, isPluginErrorState} from './code-owners-model.js';

// There are 2 elements in this file:
// CodeOwnersBanner - visual elements. This element is shown at the top
// of the page when pluginStatus are changed to failed.
// The CodeOwnersBanner is added to the 'banner' endpoint. The endpoint
// is placed at the top level of the gerrit and doesn't provide a current
// change, so it is impossible to get the pluginStatus directly in the
// CodeOwnersBanner.
// To solve this problem, this files provides non-visual
// CodeOwnersPluginStatusNotifier element. This element is added to the
// change-view-integration endpoint, so it can track the plugin state.
// When a plugin state is changed, the CodeOwnersPluginStatusNotifier updates
// the pluginStatus property of the banner.

// CodeOwnersBanner and CodeOwnersPluginStatusNotifier can be attached and
// detached in any order. The following piece of code ensures that these
// banner and notifier are always connected correctly. The code assumes,
// that max one banner and one notifier can be connected at any time.
let activeBanner = undefined;
let activeStatusNotifier = undefined;

function setActiveBanner(banner) {
  // banner is null when CodeOwnersBanner has been disconnected
  activeBanner = banner;
  if (activeStatusNotifier) {
    activeStatusNotifier.banner = banner;
  }
}

function setActiveStatusNotifier(notifier) {
  // notifier is null when CodeOwnersBanner has been disconnected
  if (activeStatusNotifier) {
    if (activeStatusNotifier.banner) {
      activeStatusNotifier.banner.pluginStatus = undefined;
    }
    activeStatusNotifier.banner = undefined;
  }
  activeStatusNotifier = notifier;
  if (activeStatusNotifier) {
    activeStatusNotifier.banner = activeBanner;
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
      <span class="text">[[_getErrorText(pluginStatus)]]</span>
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
        computed: '_computeHidden(pluginStatus)',
      },
      pluginStatus: {
        type: Object,
      },
    };
  }

  connectedCallback() {
    super.connectedCallback();
    setActiveBanner(this);
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    setActiveBanner(undefined);
  }

  _computeHidden(pluginStatus) {
    return !pluginStatus || !isPluginErrorState(pluginStatus.state);
  }

  _getErrorText(pluginStatus) {
    return !pluginStatus || pluginStatus.state === PluginState.Failed ?
      'Error: Code-owners plugin has failed' :
      'The code-owners plugin has configuration issue. ' +
      'Please contact the project owner or the host admin.';
  }

  _showFailDetails() {
    showPluginFailedMessage(this, this.pluginStatus);
  }
}

customElements.define(CodeOwnersBanner.is, CodeOwnersBanner);

export class CodeOwnersPluginStatusNotifier extends
  CodeOwnersModelMixin(Polymer.Element) {
  static get is() {
    return 'owners-plugin-status-notifier';
  }

  static get properties() {
    return {
      banner: {
        type: Object,
      },
      /**
       * This is a temporary property for interop with find-owner plugin.
       * It stores information about the change and code-owners status for this
       * change. The possible values for branchState are:
       * LOADING - branch config are requesting
       * FAILED - plugin are in failed state (server returned error, etc...)
       * ENABLED - code-owners is enabled for the change
       * DISABLED - code-owners is disable for the change
       *
       * ENABLED/DISABLED values are different from the pluginStatus.
       * For merged and abandoned changes the model.pluginStatus value is
       * always DISABLED, even if code-owners is enabled for a project.
       */
      _stateForFindOwnersPlugin: {
        type: Object,
        notify: true,
        computed:
            '_getStateForFindOwners(model.pluginStatus, model.branchConfig,' +
              ' change)',
      },
    };
  }

  static get observers() {
    return [
      '_pluginStatusOrBannerChanged(model.pluginStatus, banner)',
    ];
  }

  connectedCallback() {
    super.connectedCallback();
    setActiveStatusNotifier(this);
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    setActiveStatusNotifier(undefined);
  }

  _pluginStatusOrBannerChanged(status, banner) {
    if (!banner) return;
    banner.pluginStatus = status;
  }

  _loadDataAfterStateChanged() {
    this.modelLoader.loadPluginStatus();
    this.modelLoader.loadBranchConfig();
  }

  _getStateForFindOwners(pluginStatus, branchConfig, change) {
    if (pluginStatus === undefined || branchConfig === undefined ||
        change == undefined) {
      return {
        branchState: 'LOADING',
      };
    }
    if (isPluginErrorState(pluginStatus.state)) {
      return {
        change,
        branchState: 'FAILED',
      };
    }
    return {
      change,
      branchState: branchConfig.disabled ? 'DISABLED' : 'ENABLED',
    };
  }
}

customElements.define(CodeOwnersPluginStatusNotifier.is,
    CodeOwnersPluginStatusNotifier);

export function showPluginFailedMessage(sourceEl, pluginStatus) {
  sourceEl.dispatchEvent(
      new CustomEvent('show-error', {
        detail: {message: pluginStatus.failedMessage},
        composed: true,
        bubbles: true,
      })
  );
}
