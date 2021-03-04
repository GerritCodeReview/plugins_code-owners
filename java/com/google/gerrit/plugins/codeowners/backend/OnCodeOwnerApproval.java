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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfigSnapshot;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.RequiredApproval;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.restapi.change.OnPostReview;
import com.google.gerrit.server.util.LabelVote;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Callback that is invoked on post review and that extends the change message if a code owner
 * approval was changed.
 *
 * <p>If a code owner approval was added, removed or changed, include in the change message that is
 * being posted on vote, which of the files:
 *
 * <ul>
 *   <li>are approved now
 *   <li>are no longer approved
 *   <li>are still approved
 * </ul>
 */
@Singleton
class OnCodeOwnerApproval implements OnPostReview {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final CodeOwnerApprovalCheck codeOwnerApprovalCheck;
  private final CodeOwnerMetrics codeOwnerMetrics;

  @Inject
  OnCodeOwnerApproval(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerApprovalCheck codeOwnerApprovalCheck,
      CodeOwnerMetrics codeOwnerMetrics) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.codeOwnerApprovalCheck = codeOwnerApprovalCheck;
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

    // code owner approvals are only computed for the current patch set
    if (!changeNotes.getChange().currentPatchSetId().equals(patchSet.id())) {
      return Optional.empty();
    }

    RequiredApproval requiredApproval = codeOwnersConfig.getRequiredApproval();

    if (oldApprovals.get(requiredApproval.labelType().getName()) == null) {
      // If oldApprovals doesn't contain the label or if the labels value in it is null, the label
      // was not changed.
      // This means that the user only voted on unrelated labels.
      return Optional.empty();
    }

    try (Timer0.Context ctx = codeOwnerMetrics.extendChangeMessageOnPostReview.start()) {
      return buildMessageForCodeOwnerApproval(
          user, changeNotes, patchSet, oldApprovals, approvals, requiredApproval);
    }
  }

  private Optional<String> buildMessageForCodeOwnerApproval(
      IdentifiedUser user,
      ChangeNotes changeNotes,
      PatchSet patchSet,
      Map<String, Short> oldApprovals,
      Map<String, Short> approvals,
      RequiredApproval requiredApproval) {
    CodeOwnersPluginConfigSnapshot codeOwnersConfig =
        codeOwnersPluginConfiguration.getProjectConfig(changeNotes.getProjectName());
    int maxPathsInChangeMessage = codeOwnersConfig.getMaxPathsInChangeMessages();
    if (maxPathsInChangeMessage <= 0) {
      return Optional.empty();
    }

    LabelVote newVote = getNewVote(requiredApproval, approvals);

    ImmutableList<Path> ownedPaths;
    try {
      ownedPaths =
          codeOwnerApprovalCheck.getOwnedPaths(
              changeNotes, changeNotes.getCurrentPatchSet(), user.getAccountId());
    } catch (RestApiException e) {
      logger.atFine().withCause(e).log(
          "Couldn't compute owned paths of change %s for account %s",
          changeNotes.getChangeId(), user.getAccountId().get());
      return Optional.empty();
    }

    if (ownedPaths.isEmpty()) {
      // the user doesn't own any of the modified paths
      return Optional.empty();
    }

    if (isIgnoredDueToSelfApproval(user, patchSet, requiredApproval)) {
      if (isCodeOwnerApprovalNewlyApplied(requiredApproval, oldApprovals, newVote)
          || isCodeOwnerApprovalUpOrDowngraded(requiredApproval, oldApprovals, newVote)) {
        return Optional.of(
            String.format(
                "The vote %s is ignored as code-owner approval since the label doesn't allow"
                    + " self approval of the patch set uploader.",
                newVote));
      }
      return Optional.empty();
    }

    boolean hasImplicitApprovalByUser =
        codeOwnersConfig.areImplicitApprovalsEnabled()
            && patchSet.uploader().equals(user.getAccountId());

    boolean noLongerExplicitlyApproved = false;
    StringBuilder message = new StringBuilder();
    if (isCodeOwnerApprovalNewlyApplied(requiredApproval, oldApprovals, newVote)) {
      if (hasImplicitApprovalByUser) {
        message.append(
            String.format(
                "By voting %s the following files are now explicitly code-owner approved by %s:\n",
                newVote, user.getName()));
      } else {
        message.append(
            String.format(
                "By voting %s the following files are now code-owner approved by %s:\n",
                newVote, user.getName()));
      }
    } else if (isCodeOwnerApprovalRemoved(requiredApproval, oldApprovals, newVote)) {
      if (newVote.value() == 0) {
        if (hasImplicitApprovalByUser) {
          noLongerExplicitlyApproved = true;
          message.append(
              String.format(
                  "By removing the %s vote the following files are no longer explicitly code-owner"
                      + " approved by %s:\n",
                  newVote.label(), user.getName()));
        } else {
          message.append(
              String.format(
                  "By removing the %s vote the following files are no longer code-owner approved"
                      + " by %s:\n",
                  newVote.label(), user.getName()));
        }
      } else {
        if (hasImplicitApprovalByUser) {
          noLongerExplicitlyApproved = true;
          message.append(
              String.format(
                  "By voting %s the following files are no longer explicitly code-owner approved by"
                      + " %s:\n",
                  newVote, user.getName()));
        } else {
          message.append(
              String.format(
                  "By voting %s the following files are no longer code-owner approved by %s:\n",
                  newVote, user.getName()));
        }
      }
    } else if (isCodeOwnerApprovalUpOrDowngraded(requiredApproval, oldApprovals, newVote)) {
      if (hasImplicitApprovalByUser) {
        message.append(
            String.format(
                "By voting %s the following files are still explicitly code-owner approved by"
                    + " %s:\n",
                newVote, user.getName()));
      } else {
        message.append(
            String.format(
                "By voting %s the following files are still code-owner approved by %s:\n",
                newVote, user.getName()));
      }
    } else {
      // non-approval was downgraded (e.g. -1 to -2)
      return Optional.empty();
    }

    if (ownedPaths.size() <= maxPathsInChangeMessage) {
      appendPaths(message, ownedPaths.stream());
    } else {
      // -1 so that we never show "(1 more files)"
      int limit = maxPathsInChangeMessage - 1;
      appendPaths(message, ownedPaths.stream().limit(limit));
      message.append(String.format("(%s more files)\n", ownedPaths.size() - limit));
    }

    if (hasImplicitApprovalByUser && noLongerExplicitlyApproved) {
      message.append(
          String.format(
              "\nThe listed files are still implicitly approved by %s.\n", user.getName()));
    }

    return Optional.of(message.toString());
  }

  private void appendPaths(StringBuilder message, Stream<Path> pathsToAppend) {
    pathsToAppend.forEach(path -> message.append(String.format("* %s\n", JgitPath.of(path).get())));
  }

  private boolean isIgnoredDueToSelfApproval(
      IdentifiedUser user, PatchSet patchSet, RequiredApproval requiredApproval) {
    return patchSet.uploader().equals(user.getAccountId())
        && requiredApproval.labelType().isIgnoreSelfApproval();
  }

  private boolean isCodeOwnerApprovalNewlyApplied(
      RequiredApproval requiredApproval, Map<String, Short> oldApprovals, LabelVote newVote) {
    String labelName = requiredApproval.labelType().getName();
    return oldApprovals.get(labelName) < requiredApproval.value()
        && newVote.value() >= requiredApproval.value();
  }

  private boolean isCodeOwnerApprovalRemoved(
      RequiredApproval requiredApproval, Map<String, Short> oldApprovals, LabelVote newVote) {
    String labelName = requiredApproval.labelType().getName();
    return oldApprovals.get(labelName) >= requiredApproval.value()
        && newVote.value() < requiredApproval.value();
  }

  private boolean isCodeOwnerApprovalUpOrDowngraded(
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
