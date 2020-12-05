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

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.plugins.codeowners.api.impl.ApiModule;
import com.google.gerrit.plugins.codeowners.backend.BackendModule;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.ConfigModule;
import com.google.gerrit.plugins.codeowners.common.CodeOwnerMergeCommitStrategy;
import com.google.gerrit.plugins.codeowners.restapi.RestApiModule;
import com.google.gerrit.plugins.codeowners.validation.ValidationModule;

/** Guice module that registers the extensions of the code-owners plugin. */
public class Module extends FactoryModule {
  @Override
  protected void configure() {
    install(new ApiModule());
    install(new BackendModule());
    install(new ConfigModule());
    install(new RestApiModule());
    install(new ValidationModule());
    bind(CodeOwnerMergeCommitStrategy.class).to(CodeOwnersPluginConfiguration.class);
  }
}
