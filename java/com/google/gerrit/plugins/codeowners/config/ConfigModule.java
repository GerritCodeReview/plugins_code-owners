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

package com.google.gerrit.plugins.codeowners.config;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.ExceptionHook;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.inject.AbstractModule;

/** Guice module that registers config extensions of the code-owners plugin. */
public class ConfigModule extends AbstractModule {
  @Override
  protected void configure() {
    DynamicSet.bind(binder(), CommitValidationListener.class)
        .to(CodeOwnersPluginConfigValidator.class);
    DynamicSet.bind(binder(), ExceptionHook.class)
        .to(InvalidPluginConfigurationException.ExceptionHook.class);
  }
}
