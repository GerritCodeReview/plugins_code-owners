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

import static java.util.stream.Collectors.toList;

import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.plugins.codeowners.JgitPath;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerCheckInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwner;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigHierarchy;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.plugins.codeowners.backend.CodeOwners;
import com.google.gerrit.plugins.codeowners.backend.OptionalResultWithMessages;
import com.google.gerrit.plugins.codeowners.backend.PathCodeOwners;
import com.google.gerrit.plugins.codeowners.backend.PathCodeOwnersResult;
import com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.BranchResource;
import com.google.gerrit.server.restapi.account.AccountsCollection;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.kohsuke.args4j.Option;

/**
 * REST endpoint that checks the code ownership of a user for a path in a branch.
 *
 * <p>This REST endpoint handles {@code GET
 * /projects/<project-name>/branches/<branch-name>/code_owners.check} requests.
 */
public class CheckCodeOwner implements RestReadView<BranchResource> {
  private final PermissionBackend permissionBackend;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final CodeOwnerConfigHierarchy codeOwnerConfigHierarchy;
  private final PathCodeOwners.Factory pathCodeOwnersFactory;
  private final Provider<CodeOwnerResolver> codeOwnerResolverProvider;
  private final CodeOwners codeOwners;
  private final AccountsCollection accountsCollection;

  private String email;
  private String path;
  private String user;
  private IdentifiedUser identifiedUser;

  @Inject
  public CheckCodeOwner(
      PermissionBackend permissionBackend,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy,
      PathCodeOwners.Factory pathCodeOwnersFactory,
      Provider<CodeOwnerResolver> codeOwnerResolverProvider,
      CodeOwners codeOwners,
      AccountsCollection accountsCollection) {
    this.permissionBackend = permissionBackend;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.codeOwnerConfigHierarchy = codeOwnerConfigHierarchy;
    this.pathCodeOwnersFactory = pathCodeOwnersFactory;
    this.codeOwnerResolverProvider = codeOwnerResolverProvider;
    this.codeOwners = codeOwners;
    this.accountsCollection = accountsCollection;
  }

  @Option(name = "--email", usage = "email for which the code ownership should be checked")
  public void setEmail(String email) {
    this.email = email;
  }

  @Option(name = "--path", usage = "path for which the code ownership should be checked")
  public void setPath(String path) {
    this.path = path;
  }

  @Option(
      name = "--user",
      usage =
          "user for which the code owner visibility should be checked,"
              + " if not specified the code owner visibility is not checked")
  public void setUser(String user) {
    this.user = user;
  }

  @Override
  public Response<CodeOwnerCheckInfo> apply(BranchResource branchResource)
      throws BadRequestException, AuthException, IOException, ConfigInvalidException,
          PermissionBackendException {
    permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);

    validateInput();

    Path absolutePath = JgitPath.of(path).getAsAbsolutePath();
    List<String> messages = new ArrayList<>();
    List<Path> codeOwnerConfigFilePaths = new ArrayList<>();
    AtomicBoolean isCodeOwnershipAssignedToEmail = new AtomicBoolean(false);
    AtomicBoolean isDefaultCodeOwner = new AtomicBoolean(false);
    codeOwnerConfigHierarchy.visit(
        branchResource.getBranchKey(),
        ObjectId.fromString(branchResource.getRevision()),
        absolutePath,
        codeOwnerConfig -> {
          Path codeOwnerConfigFilePath = codeOwners.getFilePath(codeOwnerConfig.key());
          messages.add(
              String.format(
                  "checking code owner config file %s:%s:%s",
                  codeOwnerConfig.key().project(),
                  codeOwnerConfig.key().shortBranchName(),
                  codeOwnerConfigFilePath));
          PathCodeOwnersResult pathCodeOwnersResult =
              pathCodeOwnersFactory.create(codeOwnerConfig, absolutePath).resolveCodeOwnerConfig();
          pathCodeOwnersResult
              .unresolvedImports()
              .forEach(
                  unresolvedImport ->
                      messages.add(unresolvedImport.format(codeOwnersPluginConfiguration)));
          Optional<CodeOwnerReference> codeOwnerReference =
              pathCodeOwnersResult.getPathCodeOwners().stream()
                  .filter(cor -> cor.email().equals(email))
                  .findAny();
          if (codeOwnerReference.isPresent()) {
            isCodeOwnershipAssignedToEmail.set(true);

            if (RefNames.isConfigRef(codeOwnerConfig.key().ref())) {
              messages.add(
                  String.format(
                      "found email %s as code owner in default code owner config", email));
              isDefaultCodeOwner.set(true);
            } else {
              messages.add(
                  String.format(
                      "found email %s as code owner in %s", email, codeOwnerConfigFilePath));
              codeOwnerConfigFilePaths.add(codeOwnerConfigFilePath);
            }
          }

          if (pathCodeOwnersResult.ignoreParentCodeOwners()) {
            messages.add("parent code owners are ignored");
          }

          return !pathCodeOwnersResult.ignoreParentCodeOwners();
        });

    boolean isGlobalCodeOwner = false;
    Optional<CodeOwnerReference> codeOwnerReference =
        codeOwnersPluginConfiguration.getGlobalCodeOwners(branchResource.getNameKey()).stream()
            .filter(cor -> cor.email().equals(email))
            .findAny();
    if (codeOwnerReference.isPresent()) {
      messages.add(String.format("found email %s as global code owner", email));
      isGlobalCodeOwner = true;
      isCodeOwnershipAssignedToEmail.set(true);
    }

    boolean isResolvable = false;
    if (email.equals(CodeOwnerResolver.ALL_USERS_WILDCARD)) {
      isResolvable = true;
    } else {
      messages.add(String.format("trying to resolve email %s", email));
      CodeOwnerResolver codeOwnerResolver = codeOwnerResolverProvider.get();
      if (identifiedUser != null) {
        codeOwnerResolver.forUser(identifiedUser);
      } else {
        codeOwnerResolver.enforceVisibility(false);
      }
      OptionalResultWithMessages<CodeOwner> resolveResult =
          codeOwnerResolver.resolveWithMessages(CodeOwnerReference.create(email));
      messages.addAll(resolveResult.messages());
      if (resolveResult.isPresent()) {
        isResolvable = true;
      }
    }

    boolean isCodeOwner = isCodeOwnershipAssignedToEmail.get() && isResolvable;
    if (isCodeOwner) {
      messages.add(String.format("email %s is a code owner of path '%s'", email, path));
    } else {
      messages.add(String.format("email %s is not a code owner of path '%s'", email, path));
    }

    CodeOwnerCheckInfo codeOwnerCheckInfo = new CodeOwnerCheckInfo();
    codeOwnerCheckInfo.isCodeOwner = isCodeOwner;
    codeOwnerCheckInfo.isResolvable = isResolvable;
    codeOwnerCheckInfo.codeOwnerConfigFilePaths =
        codeOwnerConfigFilePaths.stream().map(Path::toString).collect(toList());
    codeOwnerCheckInfo.isDefaultCodeOwner = isDefaultCodeOwner.get();
    codeOwnerCheckInfo.isGlobalCodeOwner = isGlobalCodeOwner;
    codeOwnerCheckInfo.debugLogs = messages;
    return Response.ok(codeOwnerCheckInfo);
  }

  private void validateInput()
      throws BadRequestException, AuthException, IOException, ConfigInvalidException {
    if (email == null) {
      throw new BadRequestException("email required");
    }
    if (path == null) {
      throw new BadRequestException("path required");
    }
    if (user != null) {
      try {
        identifiedUser =
            accountsCollection
                .parse(TopLevelResource.INSTANCE, IdString.fromDecoded(user))
                .getUser();
      } catch (ResourceNotFoundException e) {
        throw new BadRequestException(String.format("user %s not found", user), e);
      }
    }
  }
}
