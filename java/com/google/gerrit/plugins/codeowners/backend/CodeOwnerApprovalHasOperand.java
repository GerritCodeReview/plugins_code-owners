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

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.ChangeHasOperandFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;

/** A class contributing a "approval_code-owners" operand to the "has" predicate. */
public class CodeOwnerApprovalHasOperand implements ChangeHasOperandFactory {
  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(ChangeHasOperandFactory.class)
          .annotatedWith(Exports.named("approval"))
          .to(CodeOwnerApprovalHasOperand.class);
    }
  }

  private final CodeOwnerSubmitRule codeOwnerSubmitRule;

  @Inject
  public CodeOwnerApprovalHasOperand(CodeOwnerSubmitRule codeOwnerSubmitRule) {
    this.codeOwnerSubmitRule = codeOwnerSubmitRule;
  }

  @Override
  public Predicate<ChangeData> create(ChangeQueryBuilder builder) throws QueryParseException {
    return new CodeOwnerApprovalPredicate(codeOwnerSubmitRule);
  }
}
