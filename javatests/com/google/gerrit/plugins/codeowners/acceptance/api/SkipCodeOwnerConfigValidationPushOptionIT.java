// Copyright (C) 2025 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.common.ValidationOptionInfo;
import com.google.gerrit.extensions.common.ValidationOptionInfos;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.validation.SkipCodeOwnerConfigValidationCapability;
import com.google.gerrit.plugins.codeowners.validation.SkipCodeOwnerConfigValidationPushOption;
import com.google.inject.Inject;
import org.junit.Test;

public class SkipCodeOwnerConfigValidationPushOptionIT extends AbstractCodeOwnersIT {
  @Inject private ChangeOperations changeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void getCodeOwnersSkipOptionAsAdmin() throws Exception {
    // Use the admin user that has the SkipCodeOwnerConfigValidationCapability global capability
    // implicitly assigned.
    requestScopeOperations.setApiUser(admin.id());

    Change.Id changeId = changeOperations.newChange().project(project).create();

    ValidationOptionInfos validationOptionsInfos =
        gApi.changes().id(project.get(), changeId.get()).getValidationOptions();
    assertThat(validationOptionsInfos.validationOptions)
        .isEqualTo(
            ImmutableList.of(
                new ValidationOptionInfo(
                    "code-owners~" + SkipCodeOwnerConfigValidationPushOption.NAME,
                    SkipCodeOwnerConfigValidationPushOption.DESCRIPTION)));
  }

  @Test
  public void getCodeOwnersSkipOptionAsUserWithTheSkipCodeOwnerConfigValidationCapability()
      throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(
            allowCapability("code-owners-" + SkipCodeOwnerConfigValidationCapability.ID)
                .group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    Change.Id changeId = changeOperations.newChange().project(project).create();

    ValidationOptionInfos validationOptionsInfos =
        gApi.changes().id(project.get(), changeId.get()).getValidationOptions();
    assertThat(validationOptionsInfos.validationOptions)
        .isEqualTo(
            ImmutableList.of(
                new ValidationOptionInfo(
                    "code-owners~" + SkipCodeOwnerConfigValidationPushOption.NAME,
                    SkipCodeOwnerConfigValidationPushOption.DESCRIPTION)));
  }

  @Test
  public void codeOwnersSkipOptionIsOmittedIfUserCannotSkipTheCodeOwnersValidation()
      throws Exception {
    // Use non-admin user that doesn't have the SkipCodeOwnerConfigValidationCapability global
    // capability.
    requestScopeOperations.setApiUser(user.id());

    Change.Id changeId = changeOperations.newChange().project(project).create();
    ValidationOptionInfos validationOptionsInfos =
        gApi.changes().id(project.get(), changeId.get()).getValidationOptions();
    assertThat(validationOptionsInfos.validationOptions).isEmpty();
  }

  @GerritConfig(name = "plugin.code-owners.disabled", value = "true")
  @Test
  public void codeOwnersSkipOptionIsOmittedIfCodeOwnersFunctionalityIsDisabledForProject()
      throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).create();
    ValidationOptionInfos validationOptionsInfos =
        gApi.changes().id(project.get(), changeId.get()).getValidationOptions();
    assertThat(validationOptionsInfos.validationOptions).isEmpty();
  }

  @GerritConfig(name = "plugin.code-owners.disabledBranch", value = "refs/heads/master")
  @Test
  public void codeOwnersSkipOptionIsOmittedIfCodeOwnersFunctionalityIsDisabledForBranch()
      throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).branch("master").create();
    ValidationOptionInfos validationOptionsInfos =
        gApi.changes().id(project.get(), changeId.get()).getValidationOptions();
    assertThat(validationOptionsInfos.validationOptions).isEmpty();
  }
}
