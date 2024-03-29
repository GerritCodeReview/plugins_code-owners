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

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.SubmitRequirementPredicate;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * A predicate that returns true if the code-owners functionality is enabled for a given change.
 *
 * <p>We implement the {@link SubmitRequirementPredicate} interface to make this predicate available
 * for submit requirement expressions. As a consequence, this predicate does not work with search
 * queries. We do that since the computation of code owner approvals is expensive.
 */
@Singleton
public class CodeOwnerEnabledPredicate extends SubmitRequirementPredicate {
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;

  @Inject
  public CodeOwnerEnabledPredicate(
      @PluginName String pluginName, CodeOwnersPluginConfiguration codeOwnersPluginConfiguration) {
    super("has", CodeOwnerEnabledHasOperand.OPERAND + "_" + pluginName);
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
  }

  @Override
  public boolean match(ChangeData changeData) {
    return !codeOwnersPluginConfiguration
        .getProjectConfig(changeData.project())
        .isDisabled(changeData.change().getDest().branch());
  }

  @Override
  public int getCost() {
    return 1;
  }
}
