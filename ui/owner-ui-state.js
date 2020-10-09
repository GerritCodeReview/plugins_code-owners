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
 * @enum
 */
const OwnerUIStateEventType = {
  EXPAND_SUGGESTION: 'expandSuggestion',
};

/**
 * For states used in code owners plugin across multiple components.
 */
class OwnerUIState {
  constructor() {
    this._expandSuggestion = false;
    this._listeners = new Map();
    this._listeners.set(OwnerUIStateEventType.EXPAND_SUGGESTION, []);
  }

  get expandSuggestion() {
    return this._expandSuggestion;
  }

  set expandSuggestion(value) {
    this._expandSuggestion = value;
    this._listeners.get(OwnerUIStateEventType.EXPAND_SUGGESTION).forEach(cb => {
      try {
        cb(value);
      } catch (e) {
        console.warn(e);
      }
    });
  }

  _subscribeEvent(eventType, cb) {
    this._listeners.get(eventType).push(cb);
    return () => {
      this._unsubscribeEvent(eventType, cb);
    };
  }

  _unsubscribeEvent(eventType, cb) {
    this._listeners.set(
        eventType,
        this._listeners.get(eventType).filter(handler => handler !== cb)
    );
  }

  onExpandSuggestionChange(cb) {
    return this._subscribeEvent(OwnerUIStateEventType.EXPAND_SUGGESTION, cb);
  }
}

export const ownerState = new OwnerUIState();
