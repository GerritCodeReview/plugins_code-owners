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

import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerCheckInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwner;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigHierarchy;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.plugins.codeowners.backend.CodeOwners;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnersInternalServerErrorException;
import com.google.gerrit.plugins.codeowners.backend.FallbackCodeOwners;
import com.google.gerrit.plugins.codeowners.backend.OptionalResultWithMessages;
import com.google.gerrit.plugins.codeowners.backend.PathCodeOwners;
import com.google.gerrit.plugins.codeowners.backend.PathCodeOwnersResult;
import com.google.gerrit.plugins.codeowners.backend.UnresolvedImportFormatter;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
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
  private final CheckCodeOwnerCapability checkCodeOwnerCapability;
  private final PermissionBackend permissionBackend;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final CodeOwnerConfigHierarchy codeOwnerConfigHierarchy;
  private final PathCodeOwners.Factory pathCodeOwnersFactory;
  private final Provider<CodeOwnerResolver> codeOwnerResolverProvider;
  private final CodeOwners codeOwners;
  private final AccountsCollection accountsCollection;
  private final UnresolvedImportFormatter unresolvedImportFormatter;

  private String email;
  private String path;
  private String user;
  private IdentifiedUser identifiedUser;

  @Inject
  public CheckCodeOwner(
      CheckCodeOwnerCapability checkCodeOwnerCapability,
      PermissionBackend permissionBackend,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy,
      PathCodeOwners.Factory pathCodeOwnersFactory,
      Provider<CodeOwnerResolver> codeOwnerResolverProvider,
      CodeOwners codeOwners,
      AccountsCollection accountsCollection,
      UnresolvedImportFormatter unresolvedImportFormatter) {
    this.checkCodeOwnerCapability = checkCodeOwnerCapability;
    this.permissionBackend = permissionBackend;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.codeOwnerConfigHierarchy = codeOwnerConfigHierarchy;
    this.pathCodeOwnersFactory = pathCodeOwnersFactory;
    this.codeOwnerResolverProvider = codeOwnerResolverProvider;
    this.codeOwners = codeOwners;
    this.accountsCollection = accountsCollection;
    this.unresolvedImportFormatter = unresolvedImportFormatter;
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
    permissionBackend.currentUser().check(checkCodeOwnerCapability.getPermission());

    validateInput();

    Path absolutePath = JgitPath.of(path).getAsAbsolutePath();
    List<String> messages = new ArrayList<>();
    List<Path> codeOwnerConfigFilePaths = new ArrayList<>();
    AtomicBoolean isCodeOwnershipAssignedToEmail = new AtomicBoolean(false);
    AtomicBoolean isDefaultCodeOwner = new AtomicBoolean(false);
    AtomicBoolean hasRevelantCodeOwnerDefinitions = new AtomicBoolean(false);
    AtomicBoolean parentCodeOwnersAreIgnored = new AtomicBoolean(false);
    codeOwnerConfigHierarchy.visit(
        branchResource.getBranchKey(),
        ObjectId.fromString(branchResource.getRevision()),
        absolutePath,
        codeOwnerConfig -> {
          messages.add(
              String.format(
                  "checking code owner config file %s", codeOwnerConfig.key().format(codeOwners)));
          OptionalResultWithMessages<PathCodeOwnersResult> pathCodeOwnersResult =
              pathCodeOwnersFactory
                  .createWithoutCache(codeOwnerConfig, absolutePath)
                  .resolveCodeOwnerConfig();
          messages.addAll(pathCodeOwnersResult.messages());
          pathCodeOwnersResult
              .get()
              .unresolvedImports()
              .forEach(
                  unresolvedImport ->
                      messages.add(unresolvedImportFormatter.format(unresolvedImport)));
          Optional<CodeOwnerReference> codeOwnerReference =
              pathCodeOwnersResult.get().getPathCodeOwners().stream()
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
              Path codeOwnerConfigFilePath = codeOwners.getFilePath(codeOwnerConfig.key());
              messages.add(
                  String.format(
                      "found email %s as code owner in %s", email, codeOwnerConfigFilePath));
              codeOwnerConfigFilePaths.add(codeOwnerConfigFilePath);
            }
          } else if (codeOwnerResolverProvider
              .get()
              .resolvePathCodeOwners(codeOwnerConfig, absolutePath)
              .hasRevelantCodeOwnerDefinitions()) {
            hasRevelantCodeOwnerDefinitions.set(true);
          }

          if (pathCodeOwnersResult.get().ignoreParentCodeOwners()) {
            messages.add("parent code owners are ignored");
            parentCodeOwnersAreIgnored.set(true);
          }

          return !pathCodeOwnersResult.get().ignoreParentCodeOwners();
        });

    boolean isGlobalCodeOwner = isGlobalCodeOwner(branchResource.getNameKey());
    if (isGlobalCodeOwner) {
      messages.add(String.format("found email %s as global code owner", email));
      isCodeOwnershipAssignedToEmail.set(true);
    }

    OptionalResultWithMessages<Boolean> isResolvableResult = isResolvable();
    boolean isResolvable = isResolvableResult.get();
    messages.addAll(isResolvableResult.messages());

    boolean isFallbackCodeOwner =
        !isCodeOwnershipAssignedToEmail.get()
            && !hasRevelantCodeOwnerDefinitions.get()
            && !parentCodeOwnersAreIgnored.get()
            && isFallbackCodeOwner(branchResource.getNameKey());

    CodeOwnerCheckInfo codeOwnerCheckInfo = new CodeOwnerCheckInfo();
    codeOwnerCheckInfo.isCodeOwner =
        (isCodeOwnershipAssignedToEmail.get() || isFallbackCodeOwner) && isResolvable;
    codeOwnerCheckInfo.isResolvable = isResolvable;
    codeOwnerCheckInfo.codeOwnerConfigFilePaths =
        codeOwnerConfigFilePaths.stream().map(Path::toString).collect(toList());
    codeOwnerCheckInfo.isFallbackCodeOwner = isFallbackCodeOwner && isResolvable;
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

  private boolean isGlobalCodeOwner(Project.NameKey projectName) {
    return codeOwnersPluginConfiguration.getGlobalCodeOwners(projectName).stream()
        .filter(cor -> cor.email().equals(email))
        .findAny()
        .isPresent();
  }

  private boolean isFallbackCodeOwner(Project.NameKey projectName) {
    FallbackCodeOwners fallbackCodeOwners =
        codeOwnersPluginConfiguration.getFallbackCodeOwners(projectName);
    switch (fallbackCodeOwners) {
      case NONE:
        return false;
      case PROJECT_OWNERS:
        return isProjectOwner(projectName);
      case ALL_USERS:
        return true;
    }
    throw new IllegalStateException(
        String.format(
            "unknown value %s for fallbackCodeOwners in project %s",
            fallbackCodeOwners.name(), projectName));
  }

  private boolean isProjectOwner(Project.NameKey projectName) {
    try {
      AccountResource accountResource =
          accountsCollection.parse(TopLevelResource.INSTANCE, IdString.fromDecoded(email));
      // There is no dedicated project owner permission, but project owners are detected by checking
      // the permission to write the project config. Only project owners can do this.
      return permissionBackend
          .absentUser(accountResource.getUser().getAccountId())
          .project(projectName)
          .test(ProjectPermission.WRITE_CONFIG);
    } catch (PermissionBackendException
        | ResourceNotFoundException
        | AuthException
        | IOException
        | ConfigInvalidException e) {
      throw new CodeOwnersInternalServerErrorException(
          String.format("failed if email %s is owner of project %s", email, projectName.get()), e);
    }
  }

  private OptionalResultWithMessages<Boolean> isResolvable() {
    if (email.equals(CodeOwnerResolver.ALL_USERS_WILDCARD)) {
      return OptionalResultWithMessages.create(true);
    }

    CodeOwnerResolver codeOwnerResolver = codeOwnerResolverProvider.get();
    if (identifiedUser != null) {
      codeOwnerResolver.forUser(identifiedUser);
    } else {
      codeOwnerResolver.enforceVisibility(false);
    }
    OptionalResultWithMessages<CodeOwner> resolveResult =
        codeOwnerResolver.resolveWithMessages(CodeOwnerReference.create(email));

    List<String> messages = new ArrayList<>();
    messages.add(String.format("trying to resolve email %s", email));
    messages.addAll(resolveResult.messages());
    return OptionalResultWithMessages.create(resolveResult.isPresent(), messages);
  }
}
