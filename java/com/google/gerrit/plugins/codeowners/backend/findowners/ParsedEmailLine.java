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

package com.google.gerrit.plugins.codeowners.backend.findowners;

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerAnnotation;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;

/**
 * A parsed email line of an {@code OWNERS} file, consisting out of an email and optionally
 * annotations for that email.
 */
@AutoValue
abstract class ParsedEmailLine {
  abstract CodeOwnerReference codeOwnerReference();

  abstract ImmutableSet<CodeOwnerAnnotation> annotations();

  static ParsedEmailLine.Builder builder(String email) {
    requireNonNull(email, "email");
    return new AutoValue_ParsedEmailLine.Builder()
        .codeOwnerReference(CodeOwnerReference.create(email));
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder codeOwnerReference(CodeOwnerReference codeOwnerReference);

    abstract ImmutableSet.Builder<CodeOwnerAnnotation> annotationsBuilder();

    Builder addAnnotation(String annotation) {
      requireNonNull(annotation, "annotation");
      annotationsBuilder().add(CodeOwnerAnnotation.create(annotation));
      return this;
    }

    abstract ParsedEmailLine build();
  }
}
