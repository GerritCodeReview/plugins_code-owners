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

import com.google.gerrit.index.query.Matchable;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.server.query.change.ChangeData;
import java.util.Collection;

public class CodeOwnerEnabledPredicate extends Predicate<ChangeData>
    implements Matchable<ChangeData> {
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;

  public CodeOwnerEnabledPredicate(CodeOwnersPluginConfiguration codeOwnersPluginConfiguration) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
  }

  @Override
  public boolean match(ChangeData changeData) {
    if (codeOwnersPluginConfiguration
        .getProjectConfig(changeData.project())
        .isDisabled(changeData.change().getDest().branch())) {
      return false;
    }
    return true;
  }

  @Override
  public int getCost() {
    return 1;
  }

  @Override
  public Predicate<ChangeData> copy(Collection<? extends Predicate<ChangeData>> children) {
    return null;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public boolean equals(Object other) {
    return false;
  }
}
