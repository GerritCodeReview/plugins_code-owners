/**
 * @license
 * Copyright (C) 2024 The Android Open Source Project
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

import {customElement, query, property, state} from 'lit/decorators.js';
import {css, CSSResult, html, LitElement} from 'lit';
import {classMap} from 'lit/directives/class-map.js';
import {PluginApi} from '@gerritcodereview/typescript-api/plugin';

declare global {
  interface Window {
    CANONICAL_PATH?: string;
  }
}

// https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#capability-info
export interface AccountCapabilityInfo {
  administrateServer: boolean;
  'code-owners-checkCodeOwner': boolean;
}

@customElement('gr-check-code-owner')
export class GrCheckCodeOwner extends LitElement {
  @query('#projectInput')
  projectInput!: HTMLInputElement;

  @query('#branchInput')
  branchInput!: HTMLInputElement;

  @query('#emailInput')
  emailInput!: HTMLInputElement;

  @query('#pathInput')
  pathInput!: HTMLInputElement;

  @query('#userInput')
  userInput!: HTMLInputElement;

  @query('#resultOutput')
  resultOutput!: HTMLTextAreaElement;

  @query('#noteAboutLimitedDebugInformation')
  noteAboutLimitedDebugInformation!: HTMLInputElement;

  @property()
  plugin!: PluginApi;

  @state()
  dataValid = false;

  @state()
  isChecking = false;

  @state()
  hasAdminPermissions = false;

  static override get styles() {
    return [
      window.Gerrit?.styles.font as CSSResult,
      window.Gerrit?.styles.form as CSSResult,
      css`
        main {
          margin: 2em auto;
          max-width: 50em;
        }
        .heading {
          font-size: x-large;
          font-weight: 500;
        }
        .output {
          min-width: 50em;
        }
        .hidden {
          display: none;
        }
      `,
    ];
  }

  override render() {
    return html`
      <main class="gr-form-styles read-only">
        <div>
          <h1 class="heading">Check Code Owner</h1>
        </div>
        <p>
          Checks the code ownership of a user for a path in a branch, see
          <a href="${window.CANONICAL_PATH || ''}/plugins/code-owners/Documentation/rest-api.html#check-code-owner" target="_blank">documentation<a/>.
        </p>
        <p>Required fields:</p>
        <fieldset>
          <section>
            <span class="title">
              <gr-tooltip-content
                has-tooltip
                title="The project from which the code owner configuration should be read."
              >
                Project* <gr-icon icon="info"></gr-icon>:
              </gr-tooltip-content>
            </span>
            <span class="value">
              <input
                id="projectInput"
                type="text"
                @input=${this.validateData}
              />
            </span>
          </section>
          <section>
            <span class="title">
              <gr-tooltip-content
                has-tooltip
                title="The branch from which the code owner configuration should be read."
              >
                Branch* <gr-icon icon="info"></gr-icon>:
              </gr-tooltip-content>
            </span>
            <span class="value">
              <input
                id="branchInput"
                type="text"
                @input=${this.validateData}
              />
            </span>
          </section>
          <section>
            <span class="title">
              <gr-tooltip-content
                has-tooltip
                title="Email for which the code ownership should be checked."
              >
                Email* <gr-icon icon="info"></gr-icon>:
              </gr-tooltip-content>
            </span>
            <span class="value">
              <input
                id="emailInput"
                type="text"
                @input=${this.validateData}
              />
            </span>
          </section>
          <section>
            <span class="title">
              <gr-tooltip-content
                has-tooltip
                title="Path for which the code ownership should be checked."
              >
                Path* <gr-icon icon="info"></gr-icon>:
              </gr-tooltip-content>
            </span>
            <span class="value">
              <input
                id="pathInput"
                type="text"
                placeholder="/path/to/file.ext"
                @input=${this.validateData}
              />
            </span>
          </section>
        </fieldset>
        <p>Admin options (usage requires having the
          <a href="${window.CANONICAL_PATH || ''}/plugins/code-owners/Documentation/rest-api.html#checkCodeOwner" target="_blank">Check Code Owner</a>
          or the
          <a href="${window.CANONICAL_PATH || ''}/Documentation/access-control.html#capability_administrateServer" target="_blank">Administrate Server</a>
          global capability):
        <fieldset>
          <section>
            <span class="title">
              <gr-tooltip-content
                has-tooltip
                title="User for which the code owner visibility should be checked.\nCan be any account identifier (e.g. email, account ID).\nIf not specified the code owner visibility is not checked.\nCan be used to investigate why a code owner is not shown/suggested to this user."
              >
                Calling User (optional) <gr-icon icon="info"></gr-icon>:
              </gr-tooltip-content>
            </span>
            <span class="value">
              <input
                id="userInput"
                type="text"
                ?disabled=${!this.hasAdminPermissions}
                @input=${this.validateData}
              />
            </span>
          </section>
        </fieldset>
        <gr-button
          @click=${this.handleCheckCodeOwner}
          ?disabled="${!this.dataValid || this.isChecking}"
        >
          Check Code Owner
        </gr-button>
        <p>
          See
          <a href="${window.CANONICAL_PATH || ''}/plugins/code-owners/Documentation/rest-api.html#code-owner-check-info" target="_blank">CheckCodeOwnerInfo</a>
          for an explanation of the JSON fields.
        </p>
        <section>
          <span class="value">
            <textarea
              class="output"
              id="resultOutput"
              readonly
            >
            </textarea>
          </span>
        </section>
        <p
          class=${classMap({hidden: this.hasAdminPermissions})}
        >
          Note: The calling user doesn't have the
          <a href="${window.CANONICAL_PATH || ''}/plugins/code-owners/Documentation/rest-api.html#checkCodeOwner" target="_blank">Check Code Owner</a>
          or the
          <a href="${window.CANONICAL_PATH || ''}/Documentation/access-control.html#capability_administrateServer" target="_blank">Administrate Server</a>
          global capability, hence the returned debug information (field
          'debug_logs') is limited. If more information is needed, please reach
          out to a host administrator to check the code ownership.
        </p>
      </main>
    `;
  }

  override connectedCallback() {
    super.connectedCallback();

    document.title = 'Check Code Owner';

    this.checkAdminPermissions();
  }

  private async checkAdminPermissions() {
    await this.plugin
      .restApi()
      .get<AccountCapabilityInfo>('/accounts/self/capabilities/')
      .then(capabilities => {
        this.hasAdminPermissions = capabilities &&
          (capabilities['administrateServer'] ||
            capabilities['code-owners-checkCodeOwner']);
      });
  }

  private validateData() {
    this.dataValid =
      this.validateHasValue(this.projectInput.value) &&
      this.validateHasValue(this.branchInput.value) &&
      this.validateEmail(this.emailInput.value) &&
      this.validateHasValue(this.pathInput.value);
  }

  private validateHasValue(value: string) {
    if (value && value.trim().length > 0) {
      return true;
    }

    return false;
  }

  private validateEmail(email: string) {
    if (email && email.includes('@')) {
      return true;
    }

    return false;
  }

  private async handleCheckCodeOwner() {
    this.isChecking = true;

    var project = this.projectInput.value.trim();
    var branch = this.branchInput.value.trim();
    var email = this.emailInput.value.trim();
    var path = this.pathInput.value.trim();

    var url = `/projects/${encodeURIComponent(project)}/branches/${encodeURIComponent(branch)}/code_owners.check/?email=${encodeURIComponent(email)}&path=${encodeURIComponent(path)}`;
    if (this.userInput.value) {
      url = url + `&user=${encodeURIComponent(this.userInput.value.trim())}`
    }
    url = url + "&pp=1";

    try {
      await this.plugin
        .restApi()
        .get<String>(url)
        .then(response => {
          this.resultOutput.value = JSON.stringify(response, null, 2);
        })
        .catch(error => {
          this.dispatchEvent(
            new CustomEvent('show-error', {
              detail: {message: error},
              bubbles: true,
              composed: true,
            })
          );
        });
    } finally {
      this.isChecking = false;
    }
  }
}
