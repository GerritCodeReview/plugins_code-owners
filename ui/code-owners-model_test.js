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
import '../../../.ts-out/polygerrit-ui/app/test/common-test-setup-karma.js';
import {CodeOwnersModel} from './code-owners-model.js';
import {createChange} from '../../../.ts-out/polygerrit-ui/app/test/test-data-generators';
import {hasOwnProperty} from '../../../.ts-out/polygerrit-ui/app/utils/common-util';
import {SuggestionsState} from './code-owners-model';

suite('suite example', () => {
  let model;
  function testModelProperty(propertyName, options) {
    assert.isTrue(hasOwnProperty(model, propertyName));
    assert.strictEqual(model[propertyName, options.defaultValue]);

    if (options.setDefaultValue) {
      options.setDefaultValue();
      assert.strictEqual(model[propertyName], options.defaultValue);
    }
    const firstValue = options.setFirstValue();
    assert.strictEqual(model[propertyName], firstValue);
    options.setFirstValue();
    assert.strictEqual(model[propertyName], firstValue);

    const secondValue = options.setSecondValue();
    assert.strictEqual(model[propertyName], secondValue);
    options.setSecondValue();
    assert.strictEqual(model[propertyName], secondValue);

    if (options.setDefaultValue) {
      options.setDefaultValue();
      assert.strictEqual(model[propertyName], options.defaultValue);
    }
  }
  function testSimpleModelProperty(propertyName, defaultValue, value1, value2) {
    const setFunction = (newValue) => {
      model['set' + propertyName[0].toUpperCase() + propertyName.substring(1)].apply(model, newValue);
      return newValue;
    }

    testModelProperty(propertyName, {
      defaultValue,
      setDefaultValue: () => setFunction(defaultValue),
      setFirstValue: () => setFunction(value1),
      setSecondValue: () => setFunction(value2),
    })
  }

  setup(() => {
    const change = createChange();
    model = new CodeOwnersModel(change);
  });
  test('branchConfig', () => {
    testSimpleModelProperty('branchConfig', undefined, {}, {some_val: true});
  });

  test('status', () => {
    testSimpleModelProperty('status', undefined, {}, {some_val: true});
  });

  test('userRole', () => {
    testSimpleModelProperty('userRole', undefined, 'ROLE_1', 'ROLE_2');
  });

  test('isCodeOwnerEnabled', () => {
    testSimpleModelProperty('isCodeOwnerEnabled', undefined, true, false);
  });

  test('areAllFilesApproved', () => {
    testSimpleModelProperty('areAllFilesApproved', undefined, true, false);
  });

  test('suggestions', () => {
    testSimpleModelProperty('suggestions', undefined, [], [{suggestion: 'abc'}]);
  });

  test('suggestionsState', () => {
    testSimpleModelProperty('suggestionsState', SuggestionsState.NotLoaded, SuggestionsState.Loading, SuggestionsState.Loaded);
  });

  test('suggestionsLoadProgress', () => {
    testSimpleModelProperty('suggestionsLoadProgress', undefined, {progress: 'xyz'}, {progress: 'def'});
  });

  test('showSuggestions', () => {
    testSimpleModelProperty('showSuggestions', false, true, false);
  });

  test('pluginStatus - setEnabled', () => {

  });
});
