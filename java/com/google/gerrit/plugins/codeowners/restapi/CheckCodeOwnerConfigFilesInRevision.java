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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.plugins.codeowners.api.CheckCodeOwnerConfigFilesInRevisionInput;
import com.google.gerrit.plugins.codeowners.backend.ChangedFiles;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.validation.CodeOwnerConfigValidator;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * REST endpoint that checks/validates the code owner config files in a revision.
 *
 * <p>This REST endpoint handles {@code POST
 * /changes/<change-id>/revisions/<revision-id>/code_owners.check_config} requests.
 */
@Singleton
public class CheckCodeOwnerConfigFilesInRevision
    implements RestModifyView<RevisionResource, CheckCodeOwnerConfigFilesInRevisionInput> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final IdentifiedUser.GenericFactory genericUserFactory;
  private final GitRepositoryManager repoManager;
  private final ChangedFiles changedFiles;
  private final CodeOwnerConfigValidator codeOwnerConfigValidator;

  @Inject
  public CheckCodeOwnerConfigFilesInRevision(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      IdentifiedUser.GenericFactory genericUserFactory,
      GitRepositoryManager repoManager,
      ChangedFiles changedFiles,
      CodeOwnerConfigValidator codeOwnerConfigValidator) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.genericUserFactory = genericUserFactory;
    this.repoManager = repoManager;
    this.changedFiles = changedFiles;
    this.codeOwnerConfigValidator = codeOwnerConfigValidator;
  }

  @Override
  public Response<Map<String, List<ConsistencyProblemInfo>>> apply(
      RevisionResource revisionResource, CheckCodeOwnerConfigFilesInRevisionInput input)
      throws IOException, DiffNotAvailableException {
    logger.atFine().log(
        "checking code owner config files for revision %d of change %d (path = %s)",
        revisionResource.getPatchSet().number(),
        revisionResource.getChange().getChangeId(),
        input.path);

    CodeOwnerBackend codeOwnerBackend =
        codeOwnersPluginConfiguration
            .getProjectConfig(revisionResource.getProject())
            .getBackend(revisionResource.getChange().getDest().branch());

    IdentifiedUser uploader = genericUserFactory.create(revisionResource.getPatchSet().uploader());
    logger.atFine().log("uploader = %s", uploader.getLoggableName());

    try (Repository repository = repoManager.openRepository(revisionResource.getProject());
        RevWalk rw = new RevWalk(repository)) {
      RevCommit commit = rw.parseCommit(revisionResource.getPatchSet().commitId());
      return Response.ok(
          changedFiles.getFromDiffCache(revisionResource.getProject(), commit).stream()
              // filter out deletions (files without new path)
              .filter(changedFile -> changedFile.newPath().isPresent())
              // filter out non code owner config files
              .filter(
                  changedFile ->
                      codeOwnerBackend.isCodeOwnerConfigFile(
                          revisionResource.getProject(),
                          Paths.get(changedFile.newPath().get().toString())
                              .getFileName()
                              .toString()))
              .filter(
                  changedFile ->
                      input.path == null
                          || FileSystems.getDefault()
                              .getPathMatcher("glob:" + input.path)
                              .matches(changedFile.newPath().get()))
              .collect(
                  toImmutableMap(
                      changedFile -> changedFile.newPath().get().toString(),
                      changedFile ->
                          codeOwnerConfigValidator
                              .validateCodeOwnerConfig(
                                  uploader,
                                  codeOwnerBackend,
                                  revisionResource.getChange().getDest(),
                                  changedFile,
                                  commit)
                              .map(
                                  commitValidationMessage ->
                                      CheckCodeOwnerConfigFiles.createConsistencyProblemInfo(
                                          commitValidationMessage, input.verbosity))
                              .filter(Optional::isPresent)
                              .map(Optional::get)
                              .collect(toImmutableList()))));
    }
  }
}
