1;
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

/**
 * !IMPORTANT: This should be removed if Gerrit decides to
 * show submit requirement even if its approved. (currently its hidden)
 */
export class OwnerRequirementApproved extends Polymer.Element {
  static get is() {
    return 'owner-requirement-approved';
  }

  static get template() {
    return Polymer.html`
      <style include="shared-styles">
      .title {
        min-width: 10em;
        padding: var(--spacing-s) var(--spacing-m) 0
                 var(--requirements-horizontal-padding);
        display: table-cell;
        vertical-align: top;
      }
      .title::after {
        content: "Approved";
        color: var(--deemphasized-text-color);
        position: relative;
        right: -68px;
      }
      .status iron-icon {
        color: var(--positive-green-text-color);
      }
      </style>
      <div class="title" title="Approved">
        <span class="status">
          <iron-icon
            class="icon"
            icon="gr-icons:check"
          ></iron-icon>
          Code Owners
        </span>
      </div>
      `;
  }

  static get properties() {
    return {
      change: Object,
      hidden: {
        type: Boolean,
        reflectToAttribute: !0,
        computed: '_computeHidden(change)',
      },
    };
  }

  _computeHidden(e) {
    return !(
      e &&
      e.requirements &&
      !e.requirements.find(e => 'code-owners' === e.type)
    );
  }
}
customElements.define(OwnerRequirementApproved.is, OwnerRequirementApproved);
