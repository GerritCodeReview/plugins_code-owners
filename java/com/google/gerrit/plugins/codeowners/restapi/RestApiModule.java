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

package com.google.gerrit.plugins.codeowners.restapi;

import static com.google.gerrit.server.change.ChangeResource.CHANGE_KIND;
import static com.google.gerrit.server.change.RevisionResource.REVISION_KIND;
import static com.google.gerrit.server.project.BranchResource.BRANCH_KIND;
import static com.google.gerrit.server.project.ProjectResource.PROJECT_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;

/** Guice module that binds the REST API for the code-owners plugin. */
public class RestApiModule extends com.google.gerrit.extensions.restapi.RestApiModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), CodeOwnerConfigsInBranchCollection.PathResource.PATH_KIND);
    child(BRANCH_KIND, "code_owners.config").to(CodeOwnerConfigsInBranchCollection.class);
    get(CodeOwnerConfigsInBranchCollection.PathResource.PATH_KIND)
        .to(GetCodeOwnerConfigForPathInBranch.class);
    get(BRANCH_KIND, "code_owners.config_files").to(GetCodeOwnerConfigFiles.class);

    factory(CodeOwnerJson.Factory.class);
    DynamicMap.mapOf(binder(), CodeOwnersInBranchCollection.PathResource.PATH_KIND);
    child(BRANCH_KIND, "code_owners").to(CodeOwnersInBranchCollection.class);
    get(CodeOwnersInBranchCollection.PathResource.PATH_KIND).to(GetCodeOwnersForPathInBranch.class);

    DynamicMap.mapOf(binder(), CodeOwnersInChangeCollection.PathResource.PATH_KIND);
    child(REVISION_KIND, "code_owners").to(CodeOwnersInChangeCollection.class);
    get(CodeOwnersInChangeCollection.PathResource.PATH_KIND).to(GetCodeOwnersForPathInChange.class);

    get(CHANGE_KIND, "code_owners.status").to(GetCodeOwnerStatus.class);

    post(REVISION_KIND, "code_owners.check_config").to(CheckCodeOwnerConfigFilesInRevision.class);

    get(PROJECT_KIND, "code_owners.project_config").to(GetCodeOwnerProjectConfig.class);
    post(PROJECT_KIND, "code_owners.check_config").to(CheckCodeOwnerConfigFiles.class);
  }
}
