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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.util.time.TimeUtil;
import java.util.Arrays;
import org.junit.Test;

/** Tests for {@link RequiredApproval}. */
public class RequiredApprovalTest extends AbstractCodeOwnersTest {
  @Test
  public void cannotCheckIsCodeOwnerApprovalForNullPatchSetApproval() throws Exception {
    LabelType labelType = createLabelType("Foo", -2, -1, 0, 1, 2);
    RequiredApproval requiredApproval = RequiredApproval.create(labelType, (short) 1);
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> requiredApproval.isCodeOwnerApproval(null));
    assertThat(npe).hasMessageThat().isEqualTo("patchSetApproval");
  }

  @Test
  public void isCodeOwnerApproval() throws Exception {
    LabelType labelType = createLabelType("Foo", -2, -1, 0, 1, 2);
    RequiredApproval requiredApproval = RequiredApproval.create(labelType, (short) 1);
    assertThat(requiredApproval.isCodeOwnerApproval(createApproval(labelType, -2))).isFalse();
    assertThat(requiredApproval.isCodeOwnerApproval(createApproval(labelType, -1))).isFalse();
    assertThat(requiredApproval.isCodeOwnerApproval(createApproval(labelType, 0))).isFalse();
    assertThat(requiredApproval.isCodeOwnerApproval(createApproval(labelType, 1))).isTrue();
    assertThat(requiredApproval.isCodeOwnerApproval(createApproval(labelType, 2))).isTrue();
  }

  @Test
  public void cannotParseNullAsRequiredApproval() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    NullPointerException npe =
        assertThrows(NullPointerException.class, () -> RequiredApproval.parse(projectState, null));
    assertThat(npe).hasMessageThat().isEqualTo("requiredApprovalString");
  }

  @Test
  public void parseRequiresProjectState() throws Exception {
    NullPointerException npe =
        assertThrows(
            NullPointerException.class, () -> RequiredApproval.parse(null, "Code-Review+1"));
    assertThat(npe).hasMessageThat().isEqualTo("projectState");
  }

  @Test
  public void parse() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    RequiredApproval requiredApproval = RequiredApproval.parse(projectState, "Code-Review+1");
    assertThat(requiredApproval.labelType().getName()).isEqualTo("Code-Review");
    assertThat(requiredApproval.value()).isEqualTo(1);
  }

  @Test
  public void cannotParseInvalidString() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> RequiredApproval.parse(projectState, "invalid"));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("Invalid format, expected '<label-name>+<label-value>'.");
  }

  @Test
  public void cannotParseStringWithInvalidValue() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    String invalidVotingValue = "not-a-number";
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> RequiredApproval.parse(projectState, "Code-Review+" + invalidVotingValue));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("Invalid voting value: %s", invalidVotingValue));
  }

  @Test
  public void cannotParseStringWithNegativeValue() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> RequiredApproval.parse(projectState, "Code-Review+-1"));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("Voting value must be positive: -1"));
  }

  @Test
  public void parseRequiresThatLabelExists() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    String nonExistingLabel = "Non-Existing";
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> RequiredApproval.parse(projectState, nonExistingLabel + "+1"));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Label %s doesn't exist for project %s.", nonExistingLabel, project.get()));
  }

  @Test
  public void parseRequiresThatLabelValueExists() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> RequiredApproval.parse(projectState, "Code-Review+3"));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format("Label Code-Review on project %s doesn't allow value 3.", project.get()));
  }

  @Test
  public void createDefault() throws Exception {
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    RequiredApproval requiredApproval = RequiredApproval.createDefault(projectState);
    assertThat(requiredApproval.labelType().getName()).isEqualTo(RequiredApproval.DEFAULT_LABEL);
    assertThat(requiredApproval.value()).isEqualTo(RequiredApproval.DEFAULT_VALUE);
  }

  @Test
  public void createDefaultRequiresThatLabelExists() throws Exception {
    gApi.projects().name(allProjects.get()).label(RequiredApproval.DEFAULT_LABEL).delete();
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> RequiredApproval.createDefault(projectState));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Default label %s doesn't exist for project %s.",
                RequiredApproval.DEFAULT_LABEL, project.get()));
  }

  @Test
  public void createDefaultRequiresThatLabelValueExists() throws Exception {
    LabelDefinitionInput labelDefinitionInput = new LabelDefinitionInput();
    labelDefinitionInput.values = ImmutableMap.of("-1", "Bad", "0", "Good");
    gApi.projects()
        .name(allProjects.get())
        .label(RequiredApproval.DEFAULT_LABEL)
        .update(labelDefinitionInput);
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> RequiredApproval.createDefault(projectState));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Label Code-Review on project %s doesn't allow default value 1.", project.get()));
  }

  private static LabelType createLabelType(String labelName, int firstValue, int... furtherValues) {
    ImmutableList.Builder<LabelValue> labelValues = ImmutableList.builder();
    labelValues.add(LabelValue.create((short) firstValue, "Value " + firstValue));
    Arrays.stream(furtherValues)
        .forEach(value -> labelValues.add(LabelValue.create((short) value, "Value " + value)));
    return LabelType.builder(labelName, labelValues.build()).build();
  }

  private PatchSetApproval createApproval(LabelType labelType, int value) {
    return PatchSetApproval.builder()
        .key(PatchSetApproval.key(PatchSet.id(Change.id(1), 1), admin.id(), labelType.getLabelId()))
        .value(value)
        .granted(TimeUtil.nowTs())
        .build();
  }
}
