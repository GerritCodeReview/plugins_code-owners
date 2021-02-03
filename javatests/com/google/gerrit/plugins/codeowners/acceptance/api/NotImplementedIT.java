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

package com.google.gerrit.plugins.codeowners.acceptance.api;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.api.BranchCodeOwners;
import com.google.gerrit.plugins.codeowners.api.ChangeCodeOwners;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerConfigs;
import com.google.gerrit.plugins.codeowners.api.CodeOwners;
import com.google.gerrit.plugins.codeowners.api.ProjectCodeOwners;
import com.google.gerrit.plugins.codeowners.api.RevisionCodeOwners;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.Test;

/** Tests the {@code NotImplemented} classes of the code owners Java API. */
public class NotImplementedIT extends AbstractCodeOwnersTest {
  @Test
  public void allMethodsThrowNotImplementedException() throws Exception {
    assertNotImplemented(BranchCodeOwners.NotImplemented.class);
    assertNotImplemented(ChangeCodeOwners.NotImplemented.class);
    assertNotImplemented(CodeOwners.NotImplemented.class);
    assertNotImplemented(ProjectCodeOwners.NotImplemented.class);
    assertNotImplemented(RevisionCodeOwners.NotImplemented.class);
    assertNotImplemented(CodeOwnerConfigs.NotImplemented.class);
  }

  private <T> void assertNotImplemented(Class<T> notImplementedClass) throws Exception {
    // Instantiate the NotImplemented class.
    T instance = notImplementedClass.getDeclaredConstructor().newInstance();

    // NotImplemented classes should have exactly 1 interface.
    Class<?> apiInterface =
        Iterables.getOnlyElement(Arrays.asList(notImplementedClass.getInterfaces()));

    // Iterator over all methods from the interface (if we would iterator over all methods from the
    // class, we would also get the methods that are inherited from Object, which we want to skip).
    for (Method method : apiInterface.getMethods()) {
      Object[] parameters = new Object[method.getParameterCount()];
      for (int i = 0; i < method.getParameterCount(); i++) {
        // For invoking the method use null for each parameter, except if it's a String, then use an
        // empty String (that's needed because there are some default method implementations that
        // fail with NPE if a null String is passed in).
        parameters[i] = method.getParameterTypes()[i].equals(String.class) ? "" : null;
      }
      // Invoking the method should throw a NotImplementedException. Since we use reflection to
      // invoke it, the NotImplementedException is wrapped in a InvocationTargetException.
      InvocationTargetException ex =
          assertThrows(InvocationTargetException.class, () -> method.invoke(instance, parameters));
      assertWithMessage(method.getName())
          .that(ex)
          .hasCauseThat()
          .isInstanceOf(NotImplementedException.class);
    }
  }
}
