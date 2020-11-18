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

package com.google.gerrit.plugins.codeowners.backend;

import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.SubmitRecordSubject.assertThatOptional;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.testing.SubmitRecordSubject;
import com.google.gerrit.plugins.codeowners.testing.SubmitRequirementSubject;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CodeOwnerSubmitRule}. */
public class CodeOwnerSubmitRuleTest extends AbstractCodeOwnersTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  private CodeOwnerConfigOperations codeOwnerConfigOperations;
  private CodeOwnerSubmitRule codeOwnerSubmitRule;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerConfigOperations =
        plugin.getSysInjector().getInstance(CodeOwnerConfigOperations.class);
    codeOwnerSubmitRule = plugin.getSysInjector().getInstance(CodeOwnerSubmitRule.class);
  }

  @Test
  public void emptyIfCodeOwnersFunctionalityIsDisabled() throws Exception {
    disableCodeOwnersForProject(project);
    ChangeData changeData = createChange().getChange();
    assertThat(codeOwnerSubmitRule.evaluate(changeData)).isEmpty();
  }

  @Test
  public void notReady() throws Exception {
    // create arbitrary code owner config to avoid entering the bootstrapping code path in
    // CodeOwnerApprovalCheck
    createArbitraryCodeOwnerConfigFile();

    ChangeData changeData = createChange().getChange();
    SubmitRecordSubject submitRecordSubject =
        assertThatOptional(codeOwnerSubmitRule.evaluate(changeData)).value();
    submitRecordSubject.hasStatusThat().isNotReady();
    SubmitRequirementSubject submitRequirementSubject =
        submitRecordSubject.hasSubmitRequirementsThat().onlyElement();
    submitRequirementSubject.hasTypeThat().isEqualTo("code-owners");
    submitRequirementSubject.hasFallbackTextThat().isEqualTo("Code Owners");
  }

  @Test
  public void ok() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    String path = "foo/bar.baz";
    ChangeData changeData = createChange("Change Adding A File", path, "file content").getChange();

    // Add a Code-Review+1 from a code owner (by default this counts as code owner approval).
    requestScopeOperations.setApiUser(user.id());
    recommend(changeData.change().getKey().get());

    SubmitRecordSubject submitRecordSubject =
        assertThatOptional(codeOwnerSubmitRule.evaluate(changeData)).value();
    submitRecordSubject.hasStatusThat().isOk();
    SubmitRequirementSubject submitRequirementSubject =
        submitRecordSubject.hasSubmitRequirementsThat().onlyElement();
    submitRequirementSubject.hasTypeThat().isEqualTo("code-owners");
    submitRequirementSubject.hasFallbackTextThat().isEqualTo("Code Owners");
  }

  @Test
  public void ruleError() throws Exception {
    ChangeData changeData = createChange().getChange();

    // Create a ChangeData without change notes to trigger an error.
    // Set change and current patch set, so that this info can be included into the error message.
    ChangeData changeDataWithoutChangeNotes = mock(ChangeData.class);
    when(changeDataWithoutChangeNotes.change()).thenReturn(changeData.change());
    when(changeDataWithoutChangeNotes.currentPatchSet()).thenReturn(changeData.currentPatchSet());

    SubmitRecordSubject submitRecordSubject =
        assertThatOptional(codeOwnerSubmitRule.evaluate(changeDataWithoutChangeNotes)).value();
    submitRecordSubject.hasStatusThat().isRuleError();
    submitRecordSubject
        .hasErrorMessageThat()
        .isEqualTo(
            String.format(
                "Failed to evaluate code owner statuses for patch set %d of change %d.",
                changeData.change().currentPatchSetId().get(), changeData.change().getId().get()));
  }

  @Test
  public void ruleErrorWhenChangeDataIsNull() throws Exception {
    SubmitRecordSubject submitRecordSubject =
        assertThatOptional(
                codeOwnerSubmitRule.evaluate(
                    /** changeData */
                    null))
            .value();
    submitRecordSubject.hasStatusThat().isRuleError();
    submitRecordSubject.hasErrorMessageThat().isEqualTo("Failed to evaluate code owner statuses.");
  }
}
