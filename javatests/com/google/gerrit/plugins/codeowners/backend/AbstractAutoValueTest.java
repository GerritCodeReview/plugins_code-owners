// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.plugins.codeowners.backend;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Base class for tests of AutoValue classes. */
abstract class AbstractAutoValueTest extends AbstractCodeOwnersTest {
  protected void assertThatToStringIncludesAllData(Object autoValueObjectToTest) throws Exception {
    for (Method method : autoValueObjectToTest.getClass().getDeclaredMethods()) {
      if (Modifier.isAbstract(method.getModifiers())) {
        Object result = method.invoke(autoValueObjectToTest);
        assertThat(autoValueObjectToTest.toString())
            .contains(String.format("%s=%s", method.getName(), result));
      }
    }
  }
}
