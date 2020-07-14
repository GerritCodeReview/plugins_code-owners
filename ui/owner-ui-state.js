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
 * For states used in code owners plugin across multiple components.
 */
class OwnerUIState {
  constructor() {
    this._expandSuggestion = false;
    this._listeners = new Map();
    this._listeners.set('expandSuggestion', []);
  }

  get expandSuggestion() {
    return this._expandSuggestion;
  }

  set expandSuggestion(value) {
    this._expandSuggestion = value;
    this._listeners.get('expandSuggestion').forEach(cb => {
      try {
        cb(value);
      } catch (e) {
        console.warn(e);
      }
    });
  }

  onExpandSuggestionChange(cb) {
    this._listeners.get('expandSuggestion').push(cb);
  }
}

export const ownerState = new OwnerUIState();