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

package com.google.gerrit.plugins.codeowners.testing;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assert_;

import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.truth.NullAwareCorrespondence;

/** {@link Subject} for doing assertions on {@link CodeOwnerSet}s. */
public class CodeOwnerSetSubject extends Subject {
  /** {@link Correspondence} that maps {@link CodeOwnerReference}s to emails. */
  public static final Correspondence<CodeOwnerReference, String> hasEmail() {
    return NullAwareCorrespondence.transforming(CodeOwnerReference::email, "has email");
  }

  /**
   * Starts fluent chain to do assertions on a {@link CodeOwnerSet}.
   *
   * @param codeOwnerSet the code owner set on which assertions should be done
   * @return the created {@link CodeOwnerSetSubject}
   */
  public static CodeOwnerSetSubject assertThat(CodeOwnerSet codeOwnerSet) {
    return assertAbout(CodeOwnerSetSubject::new).that(codeOwnerSet);
  }

  /**
   * Starts fluent chain to do assertions on a {@link CodeOwnerSet} with a message.
   *
   * @param codeOwnerSet the code owner set on which assertions should be done
   * @param format the message format
   * @param args the message args
   * @return the created {@link CodeOwnerSetSubject}
   */
  public static CodeOwnerSetSubject assertWithMessage(
      CodeOwnerSet codeOwnerSet, String format, Object... args) {
    return assert_().withMessage(format, args).about(CodeOwnerSetSubject::new).that(codeOwnerSet);
  }

  private final CodeOwnerSet codeOwnerSet;

  private CodeOwnerSetSubject(FailureMetadata metadata, CodeOwnerSet codeOwnerSet) {
    super(metadata, codeOwnerSet);
    this.codeOwnerSet = codeOwnerSet;
  }

  /**
   * Returns an {@link IterableSubject} for the code owners in the code owner set.
   *
   * @return {@link IterableSubject} for the code owners in the code owner set
   */
  public IterableSubject hasCodeOwnersThat() {
    return check("codeOwners()").that(codeOwnerSet().codeOwners());
  }

  /**
   * Returns an {@link IterableSubject} for the code owner emails in the code owner set.
   *
   * @return {@link IterableSubject} for the code owner emails in the code owner set
   */
  public IterableSubject.UsingCorrespondence<CodeOwnerReference, String> hasCodeOwnersEmailsThat() {
    return hasCodeOwnersThat().comparingElementsUsing(hasEmail());
  }

  /**
   * Returns an {@link IterableSubject} for the path expressions in the code owner set.
   *
   * @return {@link IterableSubject} for the path expressions in the code owner set
   */
  public IterableSubject hasPathExpressionsThat() {
    return check("pathExpressions()").that(codeOwnerSet().pathExpressions());
  }

  private CodeOwnerSet codeOwnerSet() {
    isNotNull();
    return codeOwnerSet;
  }
}
