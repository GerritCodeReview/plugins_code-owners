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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.PLUGIN;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.events.CommentAddedListener;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginProjectConfigSnapshot;
import com.google.gerrit.plugins.codeowners.backend.config.RequiredApproval;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.restapi.change.OnPostReview;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.AccountTemplateUtil;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.time.Instant;
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
class OnCodeOwnerApproval implements OnPostReview, CommentAddedListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String TAG_ADD_CODE_OWNER_APPROVAL =
      ChangeMessagesUtil.AUTOGENERATED_BY_GERRIT_TAG_PREFIX + "code-owners:addCodeOwnerApproval";

  private final WorkQueue workQueue;
  private final OneOffRequestContext oneOffRequestContext;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final CodeOwnerApprovalCheck codeOwnerApprovalCheck;
  private final CodeOwnerMetrics codeOwnerMetrics;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeMessagesUtil changeMessageUtil;
  private final Provider<CurrentUser> userProvider;
  private final RetryHelper retryHelper;

  @Inject
  OnCodeOwnerApproval(
      WorkQueue workQueue,
      OneOffRequestContext oneOffRequestContext,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerApprovalCheck codeOwnerApprovalCheck,
      CodeOwnerMetrics codeOwnerMetrics,
      ChangeNotes.Factory changeNotesFactory,
      ChangeMessagesUtil changeMessageUtil,
      Provider<CurrentUser> userProvider,
      RetryHelper retryHelper) {
    this.workQueue = workQueue;
    this.oneOffRequestContext = oneOffRequestContext;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.codeOwnerApprovalCheck = codeOwnerApprovalCheck;
    this.codeOwnerMetrics = codeOwnerMetrics;
    this.changeNotesFactory = changeNotesFactory;
    this.changeMessageUtil = changeMessageUtil;
    this.userProvider = userProvider;
    this.retryHelper = retryHelper;
  }

  @Override
  public Optional<String> getChangeMessageAddOn(
      Instant when,
      IdentifiedUser user,
      ChangeNotes changeNotes,
      PatchSet patchSet,
      Map<String, Short> oldApprovals,
      Map<String, Short> approvals) {
    CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig =
        codeOwnersPluginConfiguration.getProjectConfig(changeNotes.getProjectName());
    int maxPathsInChangeMessage = codeOwnersConfig.getMaxPathsInChangeMessages();
    if (codeOwnersConfig.isDisabled(changeNotes.getChange().getDest().branch())
        || maxPathsInChangeMessage <= 0) {
      logger.atFine().log("skip extending the change message since message posting is disabled");
      return Optional.empty();
    }
    if (codeOwnersConfig.enableAsyncMessageOnCodeOwnerApproval()) {
      // To avoid adding latency to PostReview post the change message asynchronously from
      // #onCommentAdded(Event) after PostReview is done.
      logger.atFine().log(
          "skip extending the change message since async message posting is enabled");
      return Optional.empty();
    }

    // code owner approvals are only computed for the current patch set
    if (!changeNotes.getChange().currentPatchSetId().equals(patchSet.id())) {
      logger.atFine().log("skip extending the change message on non-current patch set");
      return Optional.empty();
    }

    RequiredApproval requiredApproval = codeOwnersConfig.getRequiredApproval();

    if (oldApprovals.get(requiredApproval.labelType().getName()) == null) {
      // If oldApprovals doesn't contain the label or if the labels value in it is null, the label
      // was not changed.
      // This means that the user only voted on unrelated labels.
      return Optional.empty();
    }

    logger.atFine().log("post change message synchronously");
    try (Timer0.Context ctx = codeOwnerMetrics.extendChangeMessageOnPostReview.start()) {
      return buildMessageForCodeOwnerApproval(
          user,
          changeNotes,
          patchSet,
          oldApprovals,
          approvals,
          requiredApproval,
          maxPathsInChangeMessage);
    } catch (Exception e) {
      Optional<? extends Exception> configurationError =
          CodeOwnersExceptionHook.getCauseOfConfigurationError(e);
      if (configurationError.isPresent()) {
        logger.atWarning().log(
            "Failed to post code-owners change message for code owner approval on change %s"
                + " in project %s: %s",
            changeNotes.getChangeId(),
            changeNotes.getProjectName(),
            configurationError.get().getMessage());
      } else {
        logger.atSevere().withCause(e).log(
            "Failed to post code-owners change message for code owner approval on change %s"
                + " in project %s.",
            changeNotes.getChangeId(), changeNotes.getProjectName());
      }
      return Optional.empty();
    }
  }

  @Override
  public void onCommentAdded(Event event) {
    Project.NameKey projectName = Project.nameKey(event.getChange().project);
    CodeOwnersPluginProjectConfigSnapshot codeOwnersConfig =
        codeOwnersPluginConfiguration.getProjectConfig(projectName);
    int maxPathsInChangeMessage = codeOwnersConfig.getMaxPathsInChangeMessages();
    if (codeOwnersConfig.isDisabled(event.getChange().branch) || maxPathsInChangeMessage <= 0) {
      logger.atFine().log("skip posting the change message since message posting is disabled");
      return;
    }
    if (!codeOwnersConfig.enableAsyncMessageOnCodeOwnerApproval()) {
      // The change message has already been synchronously extended by #getChangeMessageAddOn(...).
      logger.atFine().log(
          "skip posting the change message since async message posting is disabled");
      return;
    }

    // post change message asynchronously to avoid adding latency to PostReview
    logger.atFine().log("schedule asynchronous posting of the change message");

    CurrentUser user = userProvider.get();
    if (!user.isIdentifiedUser()) {
      // cannot compute owned paths for non-identified user
      logger.atFine().log(
          "skip posting the change message for non-identified user %s", user.getLoggableName());
      return;
    }

    Change.Id changeId = Change.id(event.getChange()._number);
    ChangeNotes changeNotes = changeNotesFactory.create(projectName, changeId);
    PatchSet.Id patchSetId = PatchSet.id(changeId, event.getRevision()._number);
    RequiredApproval requiredApproval = codeOwnersConfig.getRequiredApproval();

    // code owner approvals are only computed for the current patch set
    PatchSet currentPatchSet = changeNotes.getCurrentPatchSet();
    if (!currentPatchSet.id().equals(patchSetId)) {
      logger.atFine().log("skip posting the change message on non-current patch set");
      return;
    }

    if (event.getOldApprovals().get(requiredApproval.labelType().getName()) == null) {
      // If oldApprovals doesn't contain the label or if the labels value in it is null, the label
      // was not changed.
      // This means that the user only voted on unrelated labels.
      return;
    }

    @SuppressWarnings("unused")
    WorkQueue.Task<?> possiblyIgnoredError =
        (WorkQueue.Task<?>)
            workQueue
                .getDefaultQueue()
                .submit(
                    () -> {
                      try (ManualRequestContext ignored =
                          oneOffRequestContext.openAs(user.getAccountId())) {
                        postChangeMessage(
                            event.getWhen(),
                            user.asIdentifiedUser(),
                            changeNotes,
                            currentPatchSet,
                            mapApprovalInfosToVotingValues(event.getOldApprovals()),
                            mapApprovalInfosToVotingValues(event.getApprovals()),
                            requiredApproval,
                            maxPathsInChangeMessage);
                      }
                    });
  }

  private void postChangeMessage(
      Instant when,
      IdentifiedUser user,
      ChangeNotes changeNotes,
      PatchSet patchSet,
      Map<String, Short> oldApprovals,
      Map<String, Short> approvals,
      RequiredApproval requiredApproval,
      int maxPathsInChangeMessages) {
    try (Timer0.Context ctx = codeOwnerMetrics.addChangeMessageOnCodeOwnerApproval.start()) {
      retryHelper
          .changeUpdate(
              "addCodeOwnersMessageOnCodeOwnerApproval",
              updateFactory -> {
                try (BatchUpdate batchUpdate =
                        updateFactory.create(changeNotes.getProjectName(), user, when);
                    RefUpdateContext pluginCtx = RefUpdateContext.open(PLUGIN);
                    RefUpdateContext changeCtx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
                  batchUpdate.addOp(
                      changeNotes.getChangeId(),
                      new Op(
                          user,
                          changeNotes,
                          patchSet,
                          oldApprovals,
                          approvals,
                          requiredApproval,
                          maxPathsInChangeMessages));
                  batchUpdate.execute();
                }
                return null;
              })
          .call();
    } catch (Exception e) {
      Optional<? extends Exception> configurationError =
          CodeOwnersExceptionHook.getCauseOfConfigurationError(e);
      if (configurationError.isPresent()) {
        logger.atWarning().log(
            "Failed to post code-owners change message for code owner approval on change %s in"
                + " project %s: %s",
            changeNotes.getChangeId(),
            changeNotes.getProjectName(),
            configurationError.get().getMessage());
      } else {
        logger.atSevere().withCause(e).log(
            "Failed to post code-owners change message for code owner approval on change %s in"
                + " project %s.",
            changeNotes.getChangeId(), changeNotes.getProjectName());
      }
    }
  }

  private Optional<String> buildMessageForCodeOwnerApproval(
      IdentifiedUser user,
      ChangeNotes changeNotes,
      PatchSet patchSet,
      Map<String, Short> oldApprovals,
      Map<String, Short> approvals,
      RequiredApproval requiredApproval,
      int limit) {
    LabelVote newVote = getNewVote(requiredApproval, approvals);

    // limit + 1, so that we can show an indicator if there are more than <limit> files.
    ImmutableList<Path> ownedPaths =
        OwnedChangedFile.getOwnedPaths(
            codeOwnerApprovalCheck.getOwnedPaths(
                changeNotes,
                changeNotes.getCurrentPatchSet(),
                user.getAccountId(),
                /* start= */ 0,
                limit + 1,
                /* checkReviewers= */ false));

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
        codeOwnersPluginConfiguration
                .getProjectConfig(changeNotes.getProjectName())
                .areImplicitApprovalsEnabled()
            && patchSet.uploader().equals(user.getAccountId());

    boolean noLongerExplicitlyApproved = false;
    StringBuilder message = new StringBuilder();
    if (isCodeOwnerApprovalNewlyApplied(requiredApproval, oldApprovals, newVote)) {
      if (hasImplicitApprovalByUser) {
        message.append(
            String.format(
                "By voting %s the following files are now explicitly code-owner approved by %s:\n",
                newVote, AccountTemplateUtil.getAccountTemplate(user.getAccountId())));
      } else {
        message.append(
            String.format(
                "By voting %s the following files are now code-owner approved by %s:\n",
                newVote, AccountTemplateUtil.getAccountTemplate(user.getAccountId())));
      }
    } else if (isCodeOwnerApprovalRemoved(requiredApproval, oldApprovals, newVote)) {
      if (newVote.value() == 0) {
        if (hasImplicitApprovalByUser) {
          noLongerExplicitlyApproved = true;
          message.append(
              String.format(
                  "By removing the %s vote the following files are no longer explicitly code-owner"
                      + " approved by %s:\n",
                  newVote.label(), AccountTemplateUtil.getAccountTemplate(user.getAccountId())));
        } else {
          message.append(
              String.format(
                  "By removing the %s vote the following files are no longer code-owner approved"
                      + " by %s:\n",
                  newVote.label(), AccountTemplateUtil.getAccountTemplate(user.getAccountId())));
        }
      } else {
        if (hasImplicitApprovalByUser) {
          noLongerExplicitlyApproved = true;
          message.append(
              String.format(
                  "By voting %s the following files are no longer explicitly code-owner approved by"
                      + " %s:\n",
                  newVote, AccountTemplateUtil.getAccountTemplate(user.getAccountId())));
        } else {
          message.append(
              String.format(
                  "By voting %s the following files are no longer code-owner approved by %s:\n",
                  newVote, AccountTemplateUtil.getAccountTemplate(user.getAccountId())));
        }
      }
    } else if (isCodeOwnerApprovalUpOrDowngraded(requiredApproval, oldApprovals, newVote)) {
      if (hasImplicitApprovalByUser) {
        message.append(
            String.format(
                "By voting %s the following files are still explicitly code-owner approved by"
                    + " %s:\n",
                newVote, AccountTemplateUtil.getAccountTemplate(user.getAccountId())));
      } else {
        message.append(
            String.format(
                "By voting %s the following files are still code-owner approved by %s:\n",
                newVote, AccountTemplateUtil.getAccountTemplate(user.getAccountId())));
      }
    } else {
      // non-approval was downgraded (e.g. -1 to -2)
      return Optional.empty();
    }

    if (ownedPaths.size() <= limit) {
      appendPaths(message, ownedPaths.stream());
    } else {
      appendPaths(message, ownedPaths.stream().limit(limit));
      message.append("(more files)\n");
    }

    if (hasImplicitApprovalByUser && noLongerExplicitlyApproved) {
      message.append(
          String.format(
              "\nThe listed files are still implicitly approved by %s.\n",
              AccountTemplateUtil.getAccountTemplate(user.getAccountId())));
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

  private static ImmutableMap<String, Short> mapApprovalInfosToVotingValues(
      Map<String, ApprovalInfo> approvals) {
    return approvals.entrySet().stream()
        .collect(
            toImmutableMap(
                Map.Entry::getKey,
                e -> e.getValue().value != null ? e.getValue().value.shortValue() : null));
  }

  private class Op implements BatchUpdateOp {
    private final IdentifiedUser user;
    private final ChangeNotes changeNotes;
    private final PatchSet patchSet;
    private final Map<String, Short> oldApprovals;
    private final Map<String, Short> approvals;
    private final RequiredApproval requiredApproval;
    private final int limit;

    Op(
        IdentifiedUser user,
        ChangeNotes changeNotes,
        PatchSet patchSet,
        Map<String, Short> oldApprovals,
        Map<String, Short> approvals,
        RequiredApproval requiredApproval,
        int limit) {
      this.user = user;
      this.changeNotes = changeNotes;
      this.patchSet = patchSet;
      this.oldApprovals = oldApprovals;
      this.approvals = approvals;
      this.requiredApproval = requiredApproval;
      this.limit = limit;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws Exception {
      Optional<String> message =
          buildMessageForCodeOwnerApproval(
              user, changeNotes, patchSet, oldApprovals, approvals, requiredApproval, limit);

      if (message.isEmpty()) {
        return false;
      }

      changeMessageUtil.setChangeMessage(ctx, message.get(), TAG_ADD_CODE_OWNER_APPROVAL);
      return true;
    }
  }
}
