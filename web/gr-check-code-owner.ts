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

import {customElement, query, property, state} from 'lit/decorators';
import {css, CSSResult, html, LitElement} from 'lit';
import {PluginApi} from '@gerritcodereview/typescript-api/plugin';

declare global {
  interface Window {
    CANONICAL_PATH?: string;
  }
}

export interface CheckCodeOwnerInfo {
  is_code_owner: boolean;
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

  @query('#resultOutput')
  resultOutput!: HTMLInputElement;

  @property()
  plugin!: PluginApi;

  @state()
  dataValid = false;

  @state()
  isChecking = false;

  @state()
  project = '';

  @state()
  branch = ''

  @state()
  email = '';

  @state()
  path = '';

  static override get styles() {
    return [
      window.Gerrit.styles.font as CSSResult,
      window.Gerrit.styles.form as CSSResult,
      window.Gerrit.styles.modal as CSSResult,
      window.Gerrit.styles.subPage as CSSResult,
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
      `,
    ];
  }

  override render() {
    return html`
      <main class="gr-form-styles read-only">
        <div class="topHeader">
          <h1 class="heading">Check Code Owner</h1>
        </div>
        <fieldset>
          Checks the code ownership of a user for a path in a branch, see
          <a href="${window.CANONICAL_PATH || ''}/plugins/code-owners/Documentation/rest-api.html#check-code-owner">documentation<a/>.
          <br/><br/>
          Requires that the caller has the
          <a href="${window.CANONICAL_PATH || ''}/plugins/code-owners/Documentation/rest-api.html#checkCodeOwner">Check Code Owner</a>
          or the
          <a href="${window.CANONICAL_PATH || ''}/Documentation/access-control.html#capability_administrateServer">Administrate Server</a>
          global capability.
          <br/><br/>
          All fields are required.
        </fieldset>
        <fieldset>
          <section>
            <span class="title">Project*:</span>
            <span class="value">
              <input
                id="projectInput"
                value="${this.project}"
                type="text"
                @input="${this.validateData}"
              />
            </span>
          </section>
          <section>
            <span class="title">Branch*:</span>
            <span class="value">
              <input
                id="branchInput"
                value="${this.branch}"
                type="text"
                @input="${this.validateData}"
              />
            </span>
          </section>
          <section>
            <span class="title">Email*:</span>
            <span class="value">
              <input
                id="emailInput"
                value="${this.email}"
                type="text"
                @input="${this.validateData}"
              />
            </span>
          </section>
          <section>
            <span class="title">Path*:</span>
            <span class="value">
              <input
                id="pathInput"
                value="${this.path}"
                type="text"
                @input="${this.validateData}"
              />
            </span>
          </section>
        </fieldset>
        <gr-button
          id="checkCodeOwnerButton"
          @click=${this.handleCheckCodeOwner}
          ?disabled="${!this.dataValid || this.isChecking}"
        >
          Check Code Owner
        </gr-button>
        <br/><br/><br/>
        <fieldset>
          See
          <a href="${window.CANONICAL_PATH || ''}/plugins/code-owners/Documentation/rest-api.html#code-owner-check-info">CheckCodeOwnerInfo</a>
          for an explanation of the JSON fields.
        </fieldset>
        <fieldset>
          <section>
            <span class="value">
              <iron-autogrow-textarea
                class="output"
                id="resultOutput"
                readonly
              >
              </iron-autogrow-textarea>
            </span>
          </section>
        </fieldset>
      </main>
    `;
  }

  private validateData() {
    this.dataValid =
      this.validateProject(this.projectInput.value) &&
      this.validateBranch(this.branchInput.value) &&
      this.validateEmail(this.emailInput.value) &&
      this.validatePath(this.pathInput.value);
  }

  private validateProject(project: string) {
    if (project && project.trim().length > 0) {
      this.project = project;
      return true;
    }

    return false;
  }

  private validateBranch(branch: string) {
    if (branch && branch.trim().length > 0) {
      this.branch = branch;
      return true;
    }

    return false;
  }

  private validateEmail(email: string) {
    if (!email || email.trim().length === 0 || email.includes('@')) {
      this.email = email;
      return true;
    }

    return false;
  }

  private validatePath(path: string) {
    if (path && path.trim().length > 0) {
      this.path = path;
      return true;
    }

    return false;
  }

  private handleCheckCodeOwner() {
    this.isChecking = true;
    return this.plugin
      .restApi()
      .get<CheckCodeOwnerInfo>(
        `/a/projects/${encodeURIComponent(this.project)}/branches/${encodeURIComponent(this.branch)}/code_owners.check/?email=${encodeURIComponent(this.email)}&path=${encodeURIComponent(this.path)}&pp=1`
      )
      .then(response => {
        this.resultOutput.value = JSON.stringify(response, null, 2);
        this.isChecking = false;
      })
      .catch(response => {
        this.dispatchEvent(
          new CustomEvent('show-error', {
            detail: {message: response},
            bubbles: true,
            composed: true,
          })
        );
        this.isChecking = false;
      });
  }
}
