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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfigSnapshot;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.RequiredApproval;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.restapi.change.OnPostReview;
import com.google.gerrit.server.util.LabelVote;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Callback that is invoked on post review and that extends the change message if a code owner
 * override was changed.
 *
 * <p>If a code owner override was added, removed or changed, include in the change message that is
 * being posted on vote, that the vote is a code owner override to let users know about its effect.
 */
@Singleton
class OnCodeOwnerOverride implements OnPostReview {
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final CodeOwnerMetrics codeOwnerMetrics;

  @Inject
  OnCodeOwnerOverride(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerMetrics codeOwnerMetrics) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.codeOwnerMetrics = codeOwnerMetrics;
  }

  @Override
  public Optional<String> getChangeMessageAddOn(
      IdentifiedUser user,
      ChangeNotes changeNotes,
      PatchSet patchSet,
      Map<String, Short> oldApprovals,
      Map<String, Short> approvals) {
    CodeOwnersPluginConfigSnapshot codeOwnersConfig =
        codeOwnersPluginConfiguration.getProjectConfig(changeNotes.getProjectName());
    if (codeOwnersConfig.isDisabled(changeNotes.getChange().getDest().branch())) {
      return Optional.empty();
    }

    // code owner overrides are only relevant for the current patch set
    if (!changeNotes.getChange().currentPatchSetId().equals(patchSet.id())) {
      return Optional.empty();
    }

    ImmutableList<RequiredApproval> appliedOverrideApprovals =
        codeOwnersConfig.getOverrideApprovals().stream()
            .sorted(comparing(RequiredApproval::toString))
            // If oldApprovals doesn't contain the label or if the labels value in it is null, the
            // label was not changed.
            .filter(
                overrideApproval ->
                    oldApprovals.get(overrideApproval.labelType().getName()) != null)
            .collect(toImmutableList());

    if (appliedOverrideApprovals.isEmpty()) {
      return Optional.empty();
    }

    try (Timer0.Context ctx = codeOwnerMetrics.extendChangeMessageOnPostReview.start()) {
      List<String> messages = new ArrayList<>();
      appliedOverrideApprovals.forEach(
          overrideApproval ->
              buildMessageForCodeOwnerOverride(
                      user, patchSet, oldApprovals, approvals, overrideApproval)
                  .ifPresent(messages::add));
      if (messages.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(Joiner.on("\n\n").join(messages));
    }
  }

  private Optional<String> buildMessageForCodeOwnerOverride(
      IdentifiedUser user,
      PatchSet patchSet,
      Map<String, Short> oldApprovals,
      Map<String, Short> approvals,
      RequiredApproval overrideApproval) {
    LabelVote newVote = getNewVote(overrideApproval, approvals);

    if (isIgnoredDueToSelfApproval(user, patchSet, overrideApproval)) {
      if (isCodeOwnerOverrideNewlyApplied(overrideApproval, oldApprovals, newVote)
          || isCodeOwnerOverrideUpOrDowngraded(overrideApproval, oldApprovals, newVote)) {
        return Optional.of(
            String.format(
                "The vote %s is ignored as code-owner override since the label doesn't allow"
                    + " self approval of the patch set uploader.",
                newVote));
      }
      return Optional.empty();
    }

    if (isCodeOwnerOverrideNewlyApplied(overrideApproval, oldApprovals, newVote)) {
      return Optional.of(
          String.format(
              "By voting %s the code-owners submit requirement is overridden by %s",
              newVote, user.getName()));
    } else if (isCodeOwnerOverrideRemoved(overrideApproval, oldApprovals, newVote)) {
      if (newVote.value() == 0) {
        return Optional.of(
            String.format(
                "By removing the %s vote the code-owners submit requirement is no longer overridden"
                    + " by %s",
                newVote.label(), user.getName()));
      }
      return Optional.of(
          String.format(
              "By voting %s the code-owners submit requirement is no longer overridden by %s",
              newVote, user.getName()));
    } else if (isCodeOwnerOverrideUpOrDowngraded(overrideApproval, oldApprovals, newVote)) {
      return Optional.of(
          String.format(
              "By voting %s the code-owners submit requirement is still overridden by %s",
              newVote, user.getName()));
    }
    // non-approval was downgraded (e.g. -1 to -2)
    return Optional.empty();
  }

  private boolean isIgnoredDueToSelfApproval(
      IdentifiedUser user, PatchSet patchSet, RequiredApproval requiredApproval) {
    return patchSet.uploader().equals(user.getAccountId())
        && requiredApproval.labelType().isIgnoreSelfApproval();
  }

  private boolean isCodeOwnerOverrideNewlyApplied(
      RequiredApproval requiredApproval, Map<String, Short> oldApprovals, LabelVote newVote) {
    String labelName = requiredApproval.labelType().getName();
    return oldApprovals.get(labelName) < requiredApproval.value()
        && newVote.value() >= requiredApproval.value();
  }

  private boolean isCodeOwnerOverrideRemoved(
      RequiredApproval requiredApproval, Map<String, Short> oldApprovals, LabelVote newVote) {
    String labelName = requiredApproval.labelType().getName();
    return oldApprovals.get(labelName) >= requiredApproval.value()
        && newVote.value() < requiredApproval.value();
  }

  private boolean isCodeOwnerOverrideUpOrDowngraded(
      RequiredApproval requiredApproval, Map<String, Short> oldApprovals, LabelVote newVote) {
    String labelName = requiredApproval.labelType().getName();
    return oldApprovals.get(labelName) >= requiredApproval.value()
        && newVote.value() >= requiredApproval.value();
  }

  private LabelVote getNewVote(RequiredApproval requiredApproval, Map<String, Short> approvals) {
    String labelName = requiredApproval.labelType().getName();
    checkState(
        approvals.containsKey(labelName),
        "expected that approval on label %s exists (approvals = %s)",
        labelName,
        approvals);
    return LabelVote.create(labelName, approvals.get(labelName));
  }
}
