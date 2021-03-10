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

import {SuggestOwners} from './suggest-owners.js';
import {OwnerStatusColumnContent, OwnerStatusColumnHeader} from './owner-status-column.js';
import {OwnerRequirementValue} from './owner-requirement.js';
import {SuggestOwnersTrigger} from './suggest-owners-trigger.js';
import {CodeOwnersBanner, CodeOwnersPluginStatusNotifier} from './code-owners-banner.js';

// A temporary property for interop with find-owners plugin.
// It should be set as earlier as possible, so find-owners plugin can check
// it in Gerrit.install callback.
window.__gerrit_code_owners_plugin = {
  state: {
    // See CodeOwnersPluginStatusNotifier._stateForFindOwnersPlugin for
    // a list of possible values.
    branchState: 'LOADING',
  },
  stateChanged: new EventTarget(),
};

Gerrit.install(plugin => {
  const restApi = plugin.restApi();
  const reporting = plugin.reporting();

  plugin.registerCustomComponent('banner', CodeOwnersBanner.is);

  const stateForFindOwnerPluginChanged = evt => {
    window.__gerrit_code_owners_plugin.state = evt.detail.value;
    window.__gerrit_code_owners_plugin.stateChanged
        .dispatchEvent(new CustomEvent('state-changed'));
  };

  plugin.registerCustomComponent(
      'change-view-integration', CodeOwnersPluginStatusNotifier.is)
      .onAttached(view => {
        view.restApi = restApi;
        view.reporting = reporting;
        view.addEventListener('_state-for-find-owners-plugin-changed',
            stateForFindOwnerPluginChanged);
      })
      .onDetached(view => {
        view.removeEventListener('_state-for-find-owners-plugin-changed',
            stateForFindOwnerPluginChanged);
      });

  // owner status column / rows for file list
  plugin.registerDynamicCustomComponent(
      'change-view-file-list-header-prepend', OwnerStatusColumnHeader.is)
      .onAttached(view => {
        view.restApi = restApi;
        view.reporting = reporting;
      });
  plugin.registerDynamicCustomComponent(
      'change-view-file-list-content-prepend', OwnerStatusColumnContent.is)
      .onAttached(view => {
        view.restApi = restApi;
        view.reporting = reporting;
      });

  // submit requirement value for owner's requirement
  plugin.registerCustomComponent(
      'submit-requirement-item-code-owners',
      OwnerRequirementValue.is, {slot: 'value'}
  )
      .onAttached(view => {
        view.restApi = restApi;
        view.reporting = reporting;
      });

  // suggest owners for reply dialog
  plugin.registerCustomComponent(
      'reply-reviewers', SuggestOwnersTrigger.is, {slot: 'right'})
      .onAttached(view => {
        view.restApi = restApi;
        view.reporting = reporting;
      });
  plugin.registerCustomComponent(
      'reply-reviewers', SuggestOwners.is, {slot: 'below'})
      .onAttached(view => {
        view.restApi = restApi;
        view.reporting = reporting;
      });
});
