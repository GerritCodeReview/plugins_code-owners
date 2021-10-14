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

import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.index.query.Matchable;
import com.google.gerrit.index.query.OperatorPredicate;
import com.google.gerrit.server.query.change.ChangeData;
import java.util.Optional;

/**
 * A predicate that checks if a given change has all necessary code owner approvals. This predicate
 * wraps the existing {@link CodeOwnerSubmitRule} to perform the logic.
 */
public class CodeOwnerApprovalPredicate extends OperatorPredicate<ChangeData>
    implements Matchable<ChangeData> {
  private final CodeOwnerSubmitRule codeOwnerSubmitRule;

  public CodeOwnerApprovalPredicate(CodeOwnerSubmitRule codeOwnerSubmitRule) {
    super("approval_code-owners", "");
    this.codeOwnerSubmitRule = codeOwnerSubmitRule;
  }

  @Override
  public boolean match(ChangeData changeData) {
    Optional<SubmitRecord> submitRecord = codeOwnerSubmitRule.evaluate(changeData);
    return submitRecord.isPresent() && submitRecord.get().status == SubmitRecord.Status.OK;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
