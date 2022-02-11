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

package com.google.gerrit.plugins.codeowners.validation;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.git.receive.PluginPushOption;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.MergeValidationListener;
import com.google.gerrit.server.git.validators.RefOperationValidationListener;
import com.google.inject.AbstractModule;

/** Guice module that registers validation extensions of the code-owners plugin. */
public class ValidationModule extends AbstractModule {
  @Override
  protected void configure() {
    DynamicSet.bind(binder(), CommitValidationListener.class).to(CodeOwnerConfigValidator.class);
    DynamicSet.bind(binder(), MergeValidationListener.class).to(CodeOwnerConfigValidator.class);
    DynamicSet.bind(binder(), RefOperationValidationListener.class)
        .to(CodeOwnerConfigValidator.class);

    bind(CapabilityDefinition.class)
        .annotatedWith(Exports.named(SkipCodeOwnerConfigValidationCapability.ID))
        .to(SkipCodeOwnerConfigValidationCapability.class);
    DynamicSet.bind(binder(), PluginPushOption.class)
        .to(SkipCodeOwnerConfigValidationPushOption.class);
  }
}
