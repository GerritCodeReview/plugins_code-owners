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
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {CodeOwnersModelMixin} from './code-owners-model-mixin.js';
import {
  PluginState,
  isPluginErrorState,
  PluginStatus,
} from './code-owners-model.js';

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
let activeBanner: CodeOwnersBanner | undefined;
let activeStatusNotifier: CodeOwnersPluginStatusNotifier | undefined;

function setActiveBanner(banner: CodeOwnersBanner | undefined) {
  // banner is null when CodeOwnersBanner has been disconnected
  activeBanner = banner;
  if (activeStatusNotifier) {
    activeStatusNotifier.banner = banner;
  }
}

function setActiveStatusNotifier(
  notifier: CodeOwnersPluginStatusNotifier | undefined
) {
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

export const CODE_OWNERS_BANNER = 'gr-code-owners-banner';
@customElement(CODE_OWNERS_BANNER)
export class CodeOwnersBanner extends LitElement {
  @property({type: Object})
  pluginStatus?: PluginStatus;

  static override get styles() {
    return css`
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
    `;
  }

  override render() {
    if (!this.pluginStatus || !isPluginErrorState(this.pluginStatus.state))
      return nothing;
    return html`
      <span class="text">${this.getErrorText()}</span>
      <gr-button link @click=${this.showFailDetails}> Details </gr-button>
    `;
  }

  override connectedCallback() {
    super.connectedCallback();
    setActiveBanner(this);
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    setActiveBanner(undefined);
  }

  private getErrorText() {
    return !this.pluginStatus || this.pluginStatus.state === PluginState.Failed
      ? 'Error: Code-owners plugin has failed'
      : 'The code-owners plugin has configuration issue. ' +
          'Please contact the project owner or the host admin.';
  }

  private showFailDetails() {
    showPluginFailedMessage(this, this.pluginStatus!);
  }
}

export const CODE_OWNERS_PLUGIN_STATUS_NOTIFIER =
  'owners-plugin-status-notifier';
const base = CodeOwnersModelMixin(LitElement);
@customElement(CODE_OWNERS_PLUGIN_STATUS_NOTIFIER)
export class CodeOwnersPluginStatusNotifier extends base {
  @property({type: Object})
  banner?: CodeOwnersBanner;

  override connectedCallback() {
    super.connectedCallback();
    setActiveStatusNotifier(this);
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    setActiveStatusNotifier(undefined);
  }

  protected override willUpdate(changedProperties: PropertyValues) {
    super.willUpdate(changedProperties);
    if (!this.banner || !this.model) return;
    this.banner.pluginStatus = this.pluginStatus;
  }

  override loadPropertiesAfterModelChanged() {
    this.modelLoader?.loadPluginStatus();
    this.modelLoader?.loadBranchConfig();
  }
}

export function showPluginFailedMessage(
  sourceEl: HTMLElement,
  pluginStatus: PluginStatus
) {
  sourceEl.dispatchEvent(
    new CustomEvent('show-error', {
      detail: {message: pluginStatus.failedMessage},
      composed: true,
      bubbles: true,
    })
  );
}
