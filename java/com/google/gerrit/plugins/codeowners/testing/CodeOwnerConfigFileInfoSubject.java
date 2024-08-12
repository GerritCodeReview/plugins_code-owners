// Copyright (C) 2023 The Android Open Source Project
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
import static com.google.gerrit.truth.ListSubject.elements;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerConfigFileInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportMode;
import com.google.gerrit.truth.ListSubject;

/** {@link Subject} for doing assertions on {@link CodeOwnerConfigFileInfo}s. */
public class CodeOwnerConfigFileInfoSubject extends Subject {
  /**
   * Starts fluent chain to do assertions on a {@link CodeOwnerConfigFileInfo}.
   *
   * @param codeOwnerConfigFileInfo the code owner config file info on which assertions should be
   *     done
   * @return the created {@link CodeOwnerConfigFileInfoSubject}
   */
  public static CodeOwnerConfigFileInfoSubject assertThat(
      CodeOwnerConfigFileInfo codeOwnerConfigFileInfo) {
    return assertAbout(codeOwnerConfigFileInfos()).that(codeOwnerConfigFileInfo);
  }

  public static Factory<CodeOwnerConfigFileInfoSubject, CodeOwnerConfigFileInfo>
      codeOwnerConfigFileInfos() {
    return CodeOwnerConfigFileInfoSubject::new;
  }

  private final CodeOwnerConfigFileInfo codeOwnerConfigFileInfo;

  protected CodeOwnerConfigFileInfoSubject(
      FailureMetadata metadata, CodeOwnerConfigFileInfo codeOwnerConfigFileInfo) {
    super(metadata, codeOwnerConfigFileInfo);
    this.codeOwnerConfigFileInfo = codeOwnerConfigFileInfo;
  }

  /** Returns a subject for the project of the code owner config file info. */
  public StringSubject hasProjectThat() {
    return check("project()").that(codeOwnerConfigFileInfo().project);
  }

  /** Returns a subject for the branch of the code owner config file info. */
  public StringSubject hasBranchThat() {
    return check("branch()").that(codeOwnerConfigFileInfo().branch);
  }

  /** Returns a subject for the path of the code owner config file info. */
  public StringSubject hasPathThat() {
    return check("path()").that(codeOwnerConfigFileInfo().path);
  }

  public IterableSubject hasWebLinksThat() {
    return check("webLinks()").that(codeOwnerConfigFileInfo().webLinks);
  }

  @CanIgnoreReturnValue
  public CodeOwnerConfigFileInfoSubject assertNoWebLinks() {
    hasWebLinksThat().isNull();
    return this;
  }

  /**
   * Returns a {@link ListSubject} for the (resolved) imports of the code owner config file info.
   */
  public ListSubject<CodeOwnerConfigFileInfoSubject, CodeOwnerConfigFileInfo> hasImportsThat() {
    return check("imports()")
        .about(elements())
        .thatCustom(codeOwnerConfigFileInfo().imports, codeOwnerConfigFileInfos());
  }

  /**
   * Returns a {@link ListSubject} for the unresolved imports of the code owner config file info.
   */
  public ListSubject<CodeOwnerConfigFileInfoSubject, CodeOwnerConfigFileInfo>
      hasUnresolvedImportsThat() {
    return check("unresolvedimports()")
        .about(elements())
        .thatCustom(codeOwnerConfigFileInfo().unresolvedImports, codeOwnerConfigFileInfos());
  }

  /** Returns a subject for the import mode of the code owner config file info. */
  public Subject hasImportModeThat() {
    return check("importMode()").that(codeOwnerConfigFileInfo().importMode);
  }

  /** Returns a subject for the unresolved error message of the code owner config file info. */
  public Subject hasUnresolvedErrorMessageThat() {
    return check("unresolvedErrorMessage()").that(codeOwnerConfigFileInfo().unresolvedErrorMessage);
  }

  @CanIgnoreReturnValue
  public CodeOwnerConfigFileInfoSubject assertKey(
      CodeOwnerBackend codeOwnerBackend, CodeOwnerConfig.Key codeOwnerConfigKey) {
    hasProjectThat().isEqualTo(codeOwnerConfigKey.project().get());
    hasBranchThat().isEqualTo(codeOwnerConfigKey.branchNameKey().branch());
    hasPathThat().isEqualTo(codeOwnerBackend.getFilePath(codeOwnerConfigKey).toString());
    return this;
  }

  @CanIgnoreReturnValue
  public CodeOwnerConfigFileInfoSubject assertNoResolvedImports() {
    hasImportsThat().isNull();
    return this;
  }

  @CanIgnoreReturnValue
  public CodeOwnerConfigFileInfoSubject assertResolvedImport(
      CodeOwnerBackend codeOwnerBackend,
      CodeOwnerConfig.Key codeOwnerConfigKey,
      CodeOwnerConfigImportMode importMode) {
    hasImportsThat().hasSize(1);
    CodeOwnerConfigFileInfoSubject subjectForResolvedImport = hasImportsThat().element(0);
    subjectForResolvedImport
        .assertKey(codeOwnerBackend, codeOwnerConfigKey)
        .assertImportMode(importMode)
        .assertNoUnresolvedErrorMessage();
    return this;
  }

  @CanIgnoreReturnValue
  public CodeOwnerConfigFileInfoSubject assertNoUnresolvedImports() {
    hasUnresolvedImportsThat().isNull();
    return this;
  }

  @CanIgnoreReturnValue
  public CodeOwnerConfigFileInfoSubject assertUnresolvedImport(
      CodeOwnerBackend codeOwnerBackend,
      CodeOwnerConfig.Key codeOwnerConfigKey,
      CodeOwnerConfigImportMode importMode,
      String unresolvedErrorMessage) {
    hasUnresolvedImportsThat().hasSize(1);
    CodeOwnerConfigFileInfoSubject subjectForUnresolvedImport =
        hasUnresolvedImportsThat().element(0);
    subjectForUnresolvedImport
        .assertKey(codeOwnerBackend, codeOwnerConfigKey)
        .assertImportMode(importMode)
        .assertUnresolvedErrorMessage(unresolvedErrorMessage);
    return this;
  }

  @CanIgnoreReturnValue
  public CodeOwnerConfigFileInfoSubject assertNoImports() {
    assertNoResolvedImports();
    assertNoUnresolvedImports();
    return this;
  }

  @CanIgnoreReturnValue
  public CodeOwnerConfigFileInfoSubject assertImportMode(CodeOwnerConfigImportMode importMode) {
    hasImportModeThat().isEqualTo(importMode);
    return this;
  }

  @CanIgnoreReturnValue
  public CodeOwnerConfigFileInfoSubject assertNoImportMode() {
    hasImportModeThat().isNull();
    return this;
  }

  @CanIgnoreReturnValue
  public CodeOwnerConfigFileInfoSubject assertUnresolvedErrorMessage(
      String unresolvedErrorMessage) {
    hasUnresolvedErrorMessageThat().isEqualTo(unresolvedErrorMessage);
    return this;
  }

  @CanIgnoreReturnValue
  public CodeOwnerConfigFileInfoSubject assertNoUnresolvedErrorMessage() {
    hasUnresolvedErrorMessageThat().isNull();
    return this;
  }

  private CodeOwnerConfigFileInfo codeOwnerConfigFileInfo() {
    isNotNull();
    return codeOwnerConfigFileInfo;
  }
}
