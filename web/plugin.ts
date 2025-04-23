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

import {SUGGEST_OWNERS} from './suggest-owners';
import {OWNER_REQUIREMENT_VALUE} from './owner-requirement';
import {SUGGEST_OWNERS_TRIGGER} from './suggest-owners-trigger';
import '@gerritcodereview/typescript-api/gerrit';
import {
  CODE_OWNERS_BANNER,
  CODE_OWNERS_PLUGIN_STATUS_NOTIFIER,
} from './code-owners-banner';
import {
  OWNERS_STATUS_COLUMN_HEADER,
  OWNER_STATUS_COLUMN_CONTENT,
} from './owner-status-column';
import {CodeOwnersModelMixinInterface} from './code-owners-model-mixin';
import './gr-check-code-owner';

window.Gerrit?.install(plugin => {
  const restApi = plugin.restApi();
  const reporting = plugin.reporting();

  plugin.registerCustomComponent('banner', CODE_OWNERS_BANNER);

  plugin
    .registerCustomComponent(
      'change-view-integration',
      CODE_OWNERS_PLUGIN_STATUS_NOTIFIER
    )
    .onAttached(view => {
      (view as unknown as CodeOwnersModelMixinInterface).restApi = restApi;
      (view as unknown as CodeOwnersModelMixinInterface).reporting = reporting;
    });
  // owner status column / rows for file list
  plugin
    .registerDynamicCustomComponent(
      'change-view-file-list-header-prepend',
      OWNERS_STATUS_COLUMN_HEADER
    )
    .onAttached(view => {
      (view as unknown as CodeOwnersModelMixinInterface).restApi = restApi;
      (view as unknown as CodeOwnersModelMixinInterface).reporting = reporting;
    });
  plugin
    .registerDynamicCustomComponent(
      'change-view-file-list-content-prepend',
      OWNER_STATUS_COLUMN_CONTENT
    )
    .onAttached(view => {
      (view as unknown as CodeOwnersModelMixinInterface).restApi = restApi;
      (view as unknown as CodeOwnersModelMixinInterface).reporting = reporting;
    });

  // old submit requirement value for owner's requirement
  plugin
    .registerCustomComponent(
      'submit-requirement-item-code-owners',
      OWNER_REQUIREMENT_VALUE,
      {slot: 'value'}
    )
    .onAttached(view => {
      (view as unknown as CodeOwnersModelMixinInterface).restApi = restApi;
      (view as unknown as CodeOwnersModelMixinInterface).reporting = reporting;
    });

  // new submit requirement value for owner's requirement
  plugin
    .registerCustomComponent(
      'submit-requirement-codeowners',
      OWNER_REQUIREMENT_VALUE,
      {replace: true}
    )
    .onAttached(view => {
      (view as unknown as CodeOwnersModelMixinInterface).restApi = restApi;
      (view as unknown as CodeOwnersModelMixinInterface).reporting = reporting;
    });

  // suggest owners for reply dialog
  plugin
    .registerCustomComponent('reply-reviewers', SUGGEST_OWNERS_TRIGGER, {
      slot: 'right',
    })
    .onAttached(view => {
      (view as unknown as CodeOwnersModelMixinInterface).restApi = restApi;
      (view as unknown as CodeOwnersModelMixinInterface).reporting = reporting;
    });
  plugin
    .registerCustomComponent('reply-reviewers', SUGGEST_OWNERS, {
      slot: 'below',
    })
    .onAttached(view => {
      (view as unknown as CodeOwnersModelMixinInterface).restApi = restApi;
      (view as unknown as CodeOwnersModelMixinInterface).reporting = reporting;
    });
  plugin.screen('check-code-owner', 'gr-check-code-owner');
});
