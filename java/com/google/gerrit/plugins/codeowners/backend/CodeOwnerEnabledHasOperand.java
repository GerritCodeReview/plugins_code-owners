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
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.ChangeHasOperandFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** A class contributing a "enabled_code-owners" operand to the "has" predicate. */
@Singleton
public class CodeOwnerEnabledHasOperand implements ChangeHasOperandFactory {
  static final String OPERAND = "enabled";

  public static class Module extends FactoryModule {
    @Override
    protected void configure() {
      bind(ChangeHasOperandFactory.class)
          .annotatedWith(Exports.named(OPERAND))
          .to(CodeOwnerEnabledHasOperand.class);

      factory(CodeOwnerEnabledPredicate.Factory.class);
    }
  }

  private final CodeOwnerEnabledPredicate.Factory codeOwnerEnabledPredicateFactory;

  @Inject
  public CodeOwnerEnabledHasOperand(
      CodeOwnerEnabledPredicate.Factory codeOwnerEnabledPredicateFactory) {
    this.codeOwnerEnabledPredicateFactory = codeOwnerEnabledPredicateFactory;
  }

  @Override
  public Predicate<ChangeData> create(ChangeQueryBuilder builder) throws QueryParseException {
    return codeOwnerEnabledPredicateFactory.create();
  }
}
