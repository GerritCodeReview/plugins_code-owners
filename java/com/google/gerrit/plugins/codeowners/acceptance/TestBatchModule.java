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

package com.google.gerrit.plugins.codeowners.acceptance;

import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperationsImpl;
import com.google.gerrit.plugins.codeowners.module.BatchModule;
import com.google.inject.AbstractModule;

/**
 * Guice module that makes the code-owners batch module and the code owners test API available
 * during the test execution
 */
public class TestBatchModule extends AbstractModule {
  @Override
  public void configure() {
    install(new BatchModule());

    // Only add bindings here that are specifically required for tests, in order to keep the Guice
    // setup in tests as realistic as possible by delegating to the original module.
    bind(CodeOwnerConfigOperations.class).to(CodeOwnerConfigOperationsImpl.class);
  }
}
