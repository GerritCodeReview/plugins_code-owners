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

package com.google.gerrit.plugins.codeowners.backend;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.events.ReviewerAddedListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfigSnapshot;
import com.google.gerrit.server.ExceptionHook;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.restapi.change.OnPostReview;
import com.google.inject.Provides;

/** Guice module to bind code owner backends. */
public class BackendModule extends FactoryModule {
  @Override
  protected void configure() {
    factory(CodeOwnersUpdate.Factory.class);
    factory(CodeOwnerConfigScanner.Factory.class);
    factory(CodeOwnersPluginConfigSnapshot.Factory.class);

    DynamicMap.mapOf(binder(), CodeOwnerBackend.class);

    // Register all code owner backends.
    // New code owner backends should be added to CodeOwnerBackendId so that they get registered
    // by the following code (do not add code to bind new code owner backends individually here).
    for (CodeOwnerBackendId codeOwnerBackendId : CodeOwnerBackendId.values()) {
      bind(CodeOwnerBackend.class)
          .annotatedWith(Exports.named(codeOwnerBackendId.getBackendId()))
          .to(codeOwnerBackendId.getCodeOwnerBackendClass());
    }

    install(new CodeOwnerSubmitRule.Module());

    DynamicSet.bind(binder(), ExceptionHook.class).to(CodeOwnersExceptionHook.class);
    DynamicSet.bind(binder(), OnPostReview.class).to(OnCodeOwnerApproval.class);
    DynamicSet.bind(binder(), OnPostReview.class).to(OnCodeOwnerOverride.class);
    DynamicSet.bind(binder(), ReviewerAddedListener.class).to(CodeOwnersOnAddReviewer.class);
  }

  @Provides
  @ServerInitiated
  CodeOwnersUpdate provideServerInitiatedCodeOwnersUpdate(
      CodeOwnersUpdate.Factory codeOwnersUpdateFactory) {
    return codeOwnersUpdateFactory.createWithServerIdent();
  }

  @Provides
  @UserInitiated
  CodeOwnersUpdate provideUserInitiatedCodeOwnersUpdate(
      CodeOwnersUpdate.Factory codeOwnersUpdateFactory, IdentifiedUser currentUser) {
    return codeOwnersUpdateFactory.create(currentUser);
  }
}
