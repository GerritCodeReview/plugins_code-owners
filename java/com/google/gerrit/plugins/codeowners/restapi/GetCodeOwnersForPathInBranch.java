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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.common.AccountVisibility;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.api.CodeOwnersInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigHierarchy;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.change.IncludedInResolver;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

/**
 * REST endpoint that gets the code owners for an arbitrary path in a branch.
 *
 * <p>This REST endpoint handles {@code GET
 * /projects/<project-name>/branches/<branch-name>/code_owners/<path>} requests.
 *
 * <p>The path may or may not exist in the branch.
 */
public class GetCodeOwnersForPathInBranch
    extends AbstractGetCodeOwnersForPath<CodeOwnersInBranchCollection.PathResource> {
  private final GitRepositoryManager repoManager;
  private String revision;

  @Option(
      name = "-revision",
      usage =
          "revision from which the code owner configs in the branch should be read (imports from"
              + " other branches or repositories as well as global code owners from refs/meta/config"
              + " are still read from the current revisions)")
  public void setRevision(String revision) {
    this.revision = revision;
  }

  @Inject
  GetCodeOwnersForPathInBranch(
      AccountVisibility accountVisibility,
      Accounts accounts,
      AccountControl.Factory accountControlFactory,
      PermissionBackend permissionBackend,
      CheckCodeOwnerCapability checkCodeOwnerCapability,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy,
      Provider<CodeOwnerResolver> codeOwnerResolver,
      CodeOwnerJson.Factory codeOwnerJsonFactory,
      GitRepositoryManager repoManager) {
    super(
        accountVisibility,
        accounts,
        accountControlFactory,
        permissionBackend,
        checkCodeOwnerCapability,
        codeOwnersPluginConfiguration,
        codeOwnerConfigHierarchy,
        codeOwnerResolver,
        codeOwnerJsonFactory);
    this.repoManager = repoManager;
  }

  @Override
  public Response<CodeOwnersInfo> apply(CodeOwnersInBranchCollection.PathResource rsrc)
      throws RestApiException, PermissionBackendException, IOException {
    if (revision != null) {
      validateRevision(rsrc.getBranch(), revision);
      rsrc = rsrc.forRevision(revision);
    }

    return super.applyImpl(rsrc);
  }

  private void validateRevision(BranchNameKey branchNameKey, String revision)
      throws BadRequestException, IOException {
    try (Repository repository = repoManager.openRepository(branchNameKey.project());
        RevWalk rw = new RevWalk(repository)) {
      ObjectId revisionId = ObjectId.fromString(revision);
      Ref ref = repository.exactRef(branchNameKey.branch());
      checkNotNull(
          ref,
          "branch %s not found in repository %s",
          branchNameKey.branch(),
          branchNameKey.project());
      RevCommit commit = rw.parseCommit(revisionId);
      if (!IncludedInResolver.includedInAny(repository, rw, commit, ImmutableSet.of(ref))) {
        throw new BadRequestException("unknown revision");
      }
    } catch (InvalidObjectIdException | IncorrectObjectTypeException e) {
      throw new BadRequestException("invalid revision", e);
    } catch (MissingObjectException e) {
      throw new BadRequestException("unknown revision", e);
    }
  }
}
