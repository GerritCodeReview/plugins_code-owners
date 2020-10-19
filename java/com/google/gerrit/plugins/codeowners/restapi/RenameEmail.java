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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.codeowners.api.RenameEmailInput;
import com.google.gerrit.plugins.codeowners.api.RenameEmailResultInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwner;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigFileUpdateScanner;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
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
import java.util.regex.Pattern;
import org.eclipse.jgit.revwalk.RevCommit;

@Singleton
public class RenameEmail implements RestModifyView<BranchResource, RenameEmailInput> {
  @VisibleForTesting
  public static String DEFAULT_COMMIT_MESSAGE = "Rename email in code owner config files";

  private final Provider<CurrentUser> currentUser;
  private final PermissionBackend permissionBackend;
  private final CodeOwnerResolver codeOwnerResolver;
  private final CodeOwnerConfigFileUpdateScanner codeOwnerConfigFileUpdateScanner;

  @Inject
  public RenameEmail(
      Provider<CurrentUser> currentUser,
      PermissionBackend permissionBackend,
      CodeOwnerResolver codeOwnerResolver,
      CodeOwnerConfigFileUpdateScanner codeOwnerConfigFileUpdateScanner) {
    this.currentUser = currentUser;
    this.permissionBackend = permissionBackend;
    this.codeOwnerResolver = codeOwnerResolver;
    this.codeOwnerConfigFileUpdateScanner = codeOwnerConfigFileUpdateScanner;
  }

  @Override
  public Response<RenameEmailResultInfo> apply(
      BranchResource branchResource, RenameEmailInput input)
      throws AuthException, BadRequestException, ResourceConflictException,
          UnprocessableEntityException, PermissionBackendException, IOException {
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

    Optional<RevCommit> commitId =
        codeOwnerConfigFileUpdateScanner.update(
            branchResource.getBranchKey(),
            commitMessage,
            (codeOwnerConfigFilePath, codeOwnerConfigFileContent) ->
                renameEmailInCodeOwnerConfig(
                    codeOwnerConfigFileContent, input.oldEmail, input.newEmail));

    RenameEmailResultInfo result = new RenameEmailResultInfo();
    if (commitId.isPresent()) {
      result.commit = CommitUtil.toCommitInfo(commitId.get());
    }
    return Response.ok(result);
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

  private Account.Id resolveEmail(String email)
      throws ResourceConflictException, UnprocessableEntityException {
    requireNonNull(email, "email");
    ImmutableSet<CodeOwner> codeOwners =
        codeOwnerResolver.resolve(CodeOwnerReference.create(email)).collect(toImmutableSet());
    if (codeOwners.isEmpty()) {
      throw new UnprocessableEntityException(String.format("cannot resolve email %s", email));
    }
    if (codeOwners.size() > 1) {
      throw new ResourceConflictException(String.format("email %s is ambigious", email));
    }
    return Iterables.getOnlyElement(codeOwners).accountId();
  }

  private Optional<String> renameEmailInCodeOwnerConfig(
      String codeOwnerConfigFileContent, String oldEmail, String newEmail) {
    requireNonNull(codeOwnerConfigFileContent, "codeOwnerConfigFileContent");
    requireNonNull(oldEmail, "oldEmail");
    requireNonNull(newEmail, "newEmail");

    if (!codeOwnerConfigFileContent.contains(oldEmail)) {
      return Optional.empty();
    }

    return Optional.of(codeOwnerConfigFileContent.replaceAll(Pattern.quote(oldEmail), newEmail));
  }
}
