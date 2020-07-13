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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.server.project.ProjectState;
import java.util.Optional;

/**
 * Approval that is required from code owners to approve the files in a change.
 *
 * <p>Defines which approval counts as code owner approval.
 */
@AutoValue
public abstract class RequiredApproval {
  /** By default a {@code Code-Review+1} vote from a code owner approves the file. */
  @VisibleForTesting static final String DEFAULT_LABEL = "Code-Review";

  @VisibleForTesting static final short DEFAULT_VALUE = 1;

  /** The label on which an approval from a code owner is required. */
  public abstract LabelType labelType();

  /** The voting value that is required on the {@link #labelType()} . */
  public abstract short value();

  /**
   * Whether the given patch set approval is a code owner approval.
   *
   * @param patchSetApproval the patch set approval for which it should be checked whether it is a
   *     code owner approval
   */
  public boolean isCodeOwnerApproval(PatchSetApproval patchSetApproval) {
    requireNonNull(patchSetApproval, "patchSetApproval");
    return labelType().getLabelId().equals(patchSetApproval.key().labelId())
        && patchSetApproval.value() >= value();
  }

  @Override
  public String toString() {
    return labelType().getName() + "+" + value();
  }

  /**
   * Parses a string-representation of a {@link RequiredApproval}.
   *
   * @param projectState the project for which the required approval should be parsed
   * @param requiredApprovalString the string-representation of a {@link RequiredApproval}, must
   *     have the format "<label-name>+<label-vote>"
   * @return the parsed {@link RequiredApproval}
   * @throws IllegalArgumentException thrown if the given string cannot be parsed as {@link
   *     RequiredApproval} or if it contains an invalid label name or voting value
   * @throws IllegalStateException thrown if the parsed label doesn't exist on the project, or if
   *     the parsed voting value is not allowed for the label
   */
  public static RequiredApproval parse(ProjectState projectState, String requiredApprovalString)
      throws IllegalArgumentException, IllegalStateException {
    requireNonNull(projectState, "projectState");
    requireNonNull(requiredApprovalString, "requiredApprovalString");

    int pos = requiredApprovalString.lastIndexOf('+');
    checkArgument(pos > 0, "Invalid format, expected '<label-name>+<label-value>'.");
    String labelName = requiredApprovalString.substring(0, pos);
    LabelType.checkName(labelName);

    Optional<LabelType> labelType =
        projectState.getLabelTypes().getLabelTypes().stream()
            .filter(lt -> lt.getName().equals(labelName))
            .findFirst();
    checkState(
        labelType.isPresent(),
        "Label %s doesn't exist for project %s.",
        labelName,
        projectState.getName());

    String votingValueString = requiredApprovalString.substring(pos + 1);
    Integer votingValue = Ints.tryParse(votingValueString);
    checkArgument(votingValue != null, "Invalid voting value: %s", votingValueString);
    checkArgument(votingValue > 0, "Voting value must be positive: %s", votingValueString);
    checkState(
        labelType.get().getByValue().containsKey(votingValue.shortValue()),
        "Label %s on project %s doesn't allow value %s.",
        labelName,
        projectState.getName(),
        votingValue);

    return create(labelType.get(), votingValue.shortValue());
  }

  /**
   * Creates the default {@link RequiredApproval} if no {@link RequiredApproval} was configured.
   *
   * @param projectState the project for which the default required approval should be returned
   * @return the default {@link RequiredApproval}
   * @throws IllegalStateException thrown if the default label doesn't exist on the project, or if
   *     the default voting value is not allowed for the label
   */
  public static RequiredApproval createDefault(ProjectState projectState)
      throws IllegalStateException {
    Optional<LabelType> labelType =
        projectState.getLabelTypes().getLabelTypes().stream()
            .filter(lt -> lt.getName().equals(DEFAULT_LABEL))
            .findFirst();
    checkState(
        labelType.isPresent(),
        "Default label %s doesn't exist for project %s.",
        DEFAULT_LABEL,
        projectState.getName());
    checkState(
        labelType.get().getByValue().containsKey(DEFAULT_VALUE),
        "Label %s on project %s doesn't allow default value %s.",
        DEFAULT_LABEL,
        projectState.getName(),
        DEFAULT_VALUE);
    return create(labelType.get(), DEFAULT_VALUE);
  }

  @VisibleForTesting
  static RequiredApproval create(LabelType labelType, short vote) {
    return new AutoValue_RequiredApproval(labelType, vote);
  }
}
