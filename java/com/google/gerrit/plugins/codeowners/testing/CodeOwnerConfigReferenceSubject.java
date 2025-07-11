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

import static com.google.common.truth.PathSubject.paths;
import static com.google.gerrit.truth.OptionalSubject.optionals;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.PathSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.gerrit.truth.OptionalSubject;

/** {@link Subject} for doing assertions on {@link CodeOwnerConfigReference}s. */
public class CodeOwnerConfigReferenceSubject extends Subject {
  public static Factory<CodeOwnerConfigReferenceSubject, CodeOwnerConfigReference>
      codeOwnerConfigReferences() {
    return CodeOwnerConfigReferenceSubject::new;
  }

  private final CodeOwnerConfigReference codeOwnerConfigReference;

  private CodeOwnerConfigReferenceSubject(
      FailureMetadata metadata, CodeOwnerConfigReference codeOwnerConfigReference) {
    super(metadata, codeOwnerConfigReference);
    this.codeOwnerConfigReference = codeOwnerConfigReference;
  }

  /** Returns a subject for the import mode. */
  public Subject hasImportModeThat() {
    return check("importMode()").that(codeOwnerConfigReference().importMode());
  }

  /** Returns a subject for the project. */
  public OptionalSubject<Subject, ?> hasProjectThat() {
    return check("project()")
        .about(optionals())
        .that(codeOwnerConfigReference().project().map(Project.NameKey::get));
  }

  /** Returns a subject for the branch. */
  public OptionalSubject<Subject, ?> hasBranchThat() {
    return check("branch()").about(optionals()).that(codeOwnerConfigReference().branch());
  }

  /** Returns a subject for the file path. */
  public PathSubject hasFilePathThat() {
    return check("filePath()").that(codeOwnerConfigReference().filePath());
  }

  private CodeOwnerConfigReference codeOwnerConfigReference() {
    isNotNull();
    return codeOwnerConfigReference;
  }
}
