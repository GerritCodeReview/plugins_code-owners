// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.plugins.codeowners.module;

import com.google.gerrit.common.UsedAt;
import com.google.gerrit.plugins.codeowners.backend.BackendModule;
import com.google.inject.AbstractModule;

/**
 * Binds a subset of the code-owners plugin functionality that should be available in batch jobs.
 */
@UsedAt(UsedAt.Project.GOOGLE)
public class BatchModule extends AbstractModule {
  @Override
  protected void configure() {
    // We only need the CodeOwnerSubmitRule in batch jobs, but since the CodeOwnerSubmitRule depends
    // on the CodeOwnerBackend, CodeOwnerBackend must be bound too. This means we can simply install
    // the whole BackendModule.
    install(new BackendModule());
  }
}
