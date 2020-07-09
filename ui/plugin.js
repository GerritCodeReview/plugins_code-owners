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

import {SuggestOwners, SuggestOwnersTrigger} from './suggest-owners.js';
import {OwnerStatusColumnContent, OwnerStatusColumnHeader} from './owner-status-column.js';

Gerrit.install(plugin => {
  const restApi = plugin.restApi();

  // owner status column / rows for file list
  plugin.registerDynamicCustomComponent(
      'change-view-file-list-header-prepend', OwnerStatusColumnHeader.is)
      .onAttached(view => {
        view.restApi = restApi;
      });
  plugin.registerDynamicCustomComponent(
      'change-view-file-list-content-prepend', OwnerStatusColumnContent.is)
      .onAttached(view => {
        view.restApi = restApi;
      });

  // suggest owners for reply dialog
  plugin.registerCustomComponent(
      'reply-reviewers', SuggestOwnersTrigger.is, {slot: 'right'});
  plugin.registerCustomComponent(
      'reply-reviewers', SuggestOwners.is, {slot: 'below'})
      .onAttached(view => {
        view.restApi = restApi;
      });
}, undefined, 'http://localhost:8081/plugins_/hats/plugin.js');