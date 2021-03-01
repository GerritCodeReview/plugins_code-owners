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

package com.google.gerrit.plugins.codeowners.restapi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.codeowners.api.RenameEmailInput;
import com.google.gerrit.plugins.codeowners.api.RenameEmailResultInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwner;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackendId;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigFileUpdateScanner;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.CommitUtil;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.BranchResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.revwalk.RevCommit;

@Singleton
public class RenameEmail implements RestModifyView<BranchResource, RenameEmailInput> {
  @VisibleForTesting
  public static final String DEFAULT_COMMIT_MESSAGE = "Rename email in code owner config files";

  private final Provider<CurrentUser> currentUser;
  private final PermissionBackend permissionBackend;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final CodeOwnerResolver codeOwnerResolver;
  private final CodeOwnerConfigFileUpdateScanner codeOwnerConfigFileUpdateScanner;

  @Inject
  public RenameEmail(
      Provider<CurrentUser> currentUser,
      PermissionBackend permissionBackend,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerResolver codeOwnerResolver,
      CodeOwnerConfigFileUpdateScanner codeOwnerConfigFileUpdateScanner) {
    this.currentUser = currentUser;
    this.permissionBackend = permissionBackend;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.codeOwnerResolver = codeOwnerResolver;
    this.codeOwnerConfigFileUpdateScanner = codeOwnerConfigFileUpdateScanner;
  }

  @Override
  public Response<RenameEmailResultInfo> apply(
      BranchResource branchResource, RenameEmailInput input)
      throws AuthException, BadRequestException, ResourceConflictException,
          MethodNotAllowedException, UnprocessableEntityException, PermissionBackendException,
          IOException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    // caller needs to be project owner or have direct push permissions for the branch
    if (!permissionBackend
        .currentUser()
        .project(branchResource.getNameKey())
        .test(ProjectPermission.WRITE_CONFIG)) {
      permissionBackend
          .currentUser()
          .ref(branchResource.getBranchKey())
          .check(RefPermission.UPDATE);
    }

    validateInput(input);

    CodeOwnerBackend codeOwnerBackend =
        codeOwnersPluginConfiguration
            .getProjectConfig(branchResource.getNameKey())
            .getBackend(branchResource.getBranchKey().branch());

    Account.Id accountOwningOldEmail = resolveEmail(input.oldEmail);
    Account.Id accountOwningNewEmail = resolveEmail(input.newEmail);
    if (!accountOwningOldEmail.equals(accountOwningNewEmail)) {
      throw new BadRequestException(
          String.format(
              "emails must belong to the same account"
                  + " (old email %s is owned by account %d, new email %s is owned by account %d)",
              input.oldEmail,
              accountOwningOldEmail.get(),
              input.newEmail,
              accountOwningNewEmail.get()));
    }

    String inputMessage = Strings.nullToEmpty(input.message).trim();
    String commitMessage = !inputMessage.isEmpty() ? inputMessage : DEFAULT_COMMIT_MESSAGE;

    try {
      Optional<RevCommit> commitId =
          codeOwnerConfigFileUpdateScanner.update(
              branchResource.getBranchKey(),
              commitMessage,
              (codeOwnerConfigFilePath, codeOwnerConfigFileContent) ->
                  renameEmailInCodeOwnerConfig(
                      codeOwnerBackend,
                      codeOwnerConfigFileContent,
                      input.oldEmail,
                      input.newEmail));

      RenameEmailResultInfo result = new RenameEmailResultInfo();
      if (commitId.isPresent()) {
        result.commit = CommitUtil.toCommitInfo(commitId.get());
      }
      return Response.ok(result);
    } catch (NotImplementedException e) {
      throw new MethodNotAllowedException(
          String.format(
              "rename email not supported by %s backend",
              CodeOwnerBackendId.getBackendId(codeOwnerBackend.getClass())),
          e);
    }
  }

  private void validateInput(RenameEmailInput input) throws BadRequestException {
    if (input.oldEmail == null) {
      throw new BadRequestException("old email is required");
    }
    if (input.newEmail == null) {
      throw new BadRequestException("new email is required");
    }
    if (input.oldEmail.equals(input.newEmail)) {
      throw new BadRequestException("old and new email must differ");
    }
  }

  private Account.Id resolveEmail(String email) throws UnprocessableEntityException {
    requireNonNull(email, "email");

    Optional<CodeOwner> codeOwner = codeOwnerResolver.resolve(CodeOwnerReference.create(email));
    if (!codeOwner.isPresent()) {
      throw new UnprocessableEntityException(String.format("cannot resolve email %s", email));
    }
    return codeOwner.get().accountId();
  }

  /**
   * Renames an email in the given code owner config.
   *
   * @param codeOwnerBackend the code owner backend that is being used
   * @param codeOwnerConfigFileContent the content of the code owner config file
   * @param oldEmail the old email that should be replaced by the new email
   * @param newEmail the new email that should replace the old email
   * @return the updated code owner config file content if an update was performed, {@link
   *     Optional#empty()} if no update was done
   */
  private Optional<String> renameEmailInCodeOwnerConfig(
      CodeOwnerBackend codeOwnerBackend,
      String codeOwnerConfigFileContent,
      String oldEmail,
      String newEmail) {
    requireNonNull(codeOwnerConfigFileContent, "codeOwnerConfigFileContent");
    requireNonNull(oldEmail, "oldEmail");
    requireNonNull(newEmail, "newEmail");

    String updatedCodeOwnerConfigFileContent =
        codeOwnerBackend.replaceEmail(codeOwnerConfigFileContent, oldEmail, newEmail);
    if (codeOwnerConfigFileContent.equals(updatedCodeOwnerConfigFileContent)) {
      return Optional.empty();
    }
    return Optional.of(updatedCodeOwnerConfigFileContent);
  }
}
