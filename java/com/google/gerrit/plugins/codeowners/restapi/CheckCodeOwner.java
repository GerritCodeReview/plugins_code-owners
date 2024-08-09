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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
import com.google.gerrit.plugins.codeowners.api.CodeOwnerConfigFileInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwner;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerAnnotations;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigHierarchy;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.plugins.codeowners.backend.CodeOwners;
import com.google.gerrit.plugins.codeowners.backend.FallbackCodeOwners;
import com.google.gerrit.plugins.codeowners.backend.OptionalResultWithMessages;
import com.google.gerrit.plugins.codeowners.backend.PathCodeOwners;
import com.google.gerrit.plugins.codeowners.backend.PathCodeOwnersResult;
import com.google.gerrit.plugins.codeowners.backend.UnresolvedImportFormatter;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.RequiredApproval;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.LabelPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.BranchResource;
import com.google.gerrit.server.restapi.account.AccountsCollection;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
  private final ChangeFinder changeFinder;
  private final CodeOwnerConfigFileJson codeOwnerConfigFileJson;

  private String email;
  private String path;
  private String change;
  private ChangeNotes changeNotes;
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
      UnresolvedImportFormatter unresolvedImportFormatter,
      ChangeFinder changeFinder,
      CodeOwnerConfigFileJson codeOwnerConfigFileJson) {
    this.checkCodeOwnerCapability = checkCodeOwnerCapability;
    this.permissionBackend = permissionBackend;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.codeOwnerConfigHierarchy = codeOwnerConfigHierarchy;
    this.pathCodeOwnersFactory = pathCodeOwnersFactory;
    this.codeOwnerResolverProvider = codeOwnerResolverProvider;
    this.codeOwners = codeOwners;
    this.accountsCollection = accountsCollection;
    this.unresolvedImportFormatter = unresolvedImportFormatter;
    this.changeFinder = changeFinder;
    this.codeOwnerConfigFileJson = codeOwnerConfigFileJson;
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
      name = "--change",
      usage =
          "change for which permissions should be checked,"
              + " if not specified change permissions are not checked")
  public void setChange(String change) {
    this.change = change;
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
          PermissionBackendException, ResourceNotFoundException {
    permissionBackend.currentUser().check(checkCodeOwnerCapability.getPermission());

    validateInput(branchResource);

    Path absolutePath = JgitPath.of(path).getAsAbsolutePath();
    ImmutableList.Builder<CodeOwnerConfigFileInfo> codeOwnerConfigFileInfosBuilder =
        ImmutableList.builder();
    List<String> messages = new ArrayList<>();
    List<Path> codeOwnerConfigFilePaths = new ArrayList<>();
    AtomicBoolean isCodeOwnershipAssignedToEmail = new AtomicBoolean(false);
    AtomicBoolean isCodeOwnershipAssignedToAllUsers = new AtomicBoolean(false);
    AtomicBoolean isDefaultCodeOwner = new AtomicBoolean(false);
    AtomicBoolean hasRevelantCodeOwnerDefinitions = new AtomicBoolean(false);
    AtomicBoolean parentCodeOwnersAreIgnored = new AtomicBoolean(false);
    Set<String> annotations = new HashSet<>();
    codeOwnerConfigHierarchy.visit(
        branchResource.getBranchKey(),
        ObjectId.fromString(branchResource.getRevision().get()),
        absolutePath,
        codeOwnerConfig -> {
          messages.add(
              String.format(
                  "checking code owner config file %s", codeOwnerConfig.key().format(codeOwners)));
          OptionalResultWithMessages<PathCodeOwnersResult> pathCodeOwnersResult =
              pathCodeOwnersFactory
                  .createWithoutCache(codeOwnerConfig, absolutePath)
                  .resolveCodeOwnerConfig();

          codeOwnerConfigFileInfosBuilder.add(
              codeOwnerConfigFileJson.format(
                  codeOwnerConfig,
                  pathCodeOwnersResult.get().resolvedImports(),
                  pathCodeOwnersResult.get().unresolvedImports()));

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
          if (codeOwnerReference.isPresent()
              && !CodeOwnerResolver.ALL_USERS_WILDCARD.equals(email)) {
            isCodeOwnershipAssignedToEmail.set(true);

            if (RefNames.isConfigRef(codeOwnerConfig.key().ref())) {
              messages.add(
                  String.format(
                      "found email %s as a code owner in the default code owner config", email));
              isDefaultCodeOwner.set(true);
            } else {
              Path codeOwnerConfigFilePath = codeOwners.getFilePath(codeOwnerConfig.key());
              messages.add(
                  String.format(
                      "found email %s as a code owner in %s", email, codeOwnerConfigFilePath));
              codeOwnerConfigFilePaths.add(codeOwnerConfigFilePath);
            }

            ImmutableSet<String> localAnnotations =
                pathCodeOwnersResult.get().getAnnotationsFor(email);
            if (!localAnnotations.isEmpty()) {
              messages.add(
                  String.format("email %s is annotated with %s", email, sort(localAnnotations)));
              annotations.addAll(localAnnotations);
            }
          }

          if (pathCodeOwnersResult.get().getPathCodeOwners().stream()
              .anyMatch(cor -> cor.email().equals(CodeOwnerResolver.ALL_USERS_WILDCARD))) {
            isCodeOwnershipAssignedToAllUsers.set(true);

            if (RefNames.isConfigRef(codeOwnerConfig.key().ref())) {
              messages.add(
                  String.format(
                      "found the all users wildcard ('%s') as a code owner in the default code"
                          + " owner config which makes %s a code owner",
                      CodeOwnerResolver.ALL_USERS_WILDCARD, email));
              isDefaultCodeOwner.set(true);
            } else {
              Path codeOwnerConfigFilePath = codeOwners.getFilePath(codeOwnerConfig.key());
              messages.add(
                  String.format(
                      "found the all users wildcard ('%s') as a code owner in %s which makes %s a"
                          + " code owner",
                      CodeOwnerResolver.ALL_USERS_WILDCARD, codeOwnerConfigFilePath, email));
              if (!codeOwnerConfigFilePaths.contains(codeOwnerConfigFilePath)) {
                codeOwnerConfigFilePaths.add(codeOwnerConfigFilePath);
              }
            }

            ImmutableSet<String> localAnnotations =
                pathCodeOwnersResult.get().getAnnotationsFor(CodeOwnerResolver.ALL_USERS_WILDCARD);
            if (!localAnnotations.isEmpty()) {
              messages.add(
                  String.format(
                      "found annotations for the all users wildcard ('%s') which apply to %s: %s",
                      CodeOwnerResolver.ALL_USERS_WILDCARD, email, sort(localAnnotations)));
              annotations.addAll(localAnnotations);
            }
          }

          if (codeOwnerResolverProvider
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

    boolean isGlobalCodeOwner = false;

    if (isGlobalCodeOwner(branchResource.getNameKey(), email)) {
      isGlobalCodeOwner = true;
      messages.add(String.format("found email %s as global code owner", email));
      isCodeOwnershipAssignedToEmail.set(true);
    }

    if (isGlobalCodeOwner(branchResource.getNameKey(), CodeOwnerResolver.ALL_USERS_WILDCARD)) {
      isGlobalCodeOwner = true;
      messages.add(
          String.format(
              "found email %s as global code owner", CodeOwnerResolver.ALL_USERS_WILDCARD));
      isCodeOwnershipAssignedToAllUsers.set(true);
    }

    boolean isResolvable;
    Boolean canReadRef = null;
    Boolean canSeeChange = null;
    Boolean canApproveChange = null;
    if (email.equals(CodeOwnerResolver.ALL_USERS_WILDCARD)) {
      isResolvable = true;
    } else {
      OptionalResultWithMessages<CodeOwner> isResolvableResult = isResolvable();
      isResolvable = isResolvableResult.isPresent();
      messages.addAll(isResolvableResult.messages());

      if (isResolvable) {
        PermissionBackend.WithUser withUser =
            permissionBackend.absentUser(isResolvableResult.get().accountId());
        canReadRef = withUser.ref(branchResource.getBranchKey()).test(RefPermission.READ);

        if (changeNotes != null) {
          PermissionBackend.ForChange forChange = withUser.change(changeNotes);
          canSeeChange = forChange.test(ChangePermission.READ);
          RequiredApproval requiredApproval =
              codeOwnersPluginConfiguration
                  .getProjectConfig(branchResource.getNameKey())
                  .getRequiredApproval();
          canApproveChange =
              forChange.test(
                  new LabelPermission.WithValue(
                      requiredApproval.labelType(), requiredApproval.value()));
        }
      }
    }

    ImmutableSet<String> unsupportedAnnotations =
        annotations.stream()
            .filter(annotation -> !CodeOwnerAnnotations.isSupported(annotation))
            .collect(toImmutableSet());
    if (!unsupportedAnnotations.isEmpty()) {
      messages.add(
          String.format(
              "dropping unsupported annotations for %s: %s", email, sort(unsupportedAnnotations)));
      annotations.removeAll(unsupportedAnnotations);
    }

    boolean isFallbackCodeOwner =
        !isCodeOwnershipAssignedToEmail.get()
            && !isCodeOwnershipAssignedToAllUsers.get()
            && !hasRevelantCodeOwnerDefinitions.get()
            && !parentCodeOwnersAreIgnored.get()
            && isFallbackCodeOwner(branchResource.getNameKey());

    CodeOwnerCheckInfo codeOwnerCheckInfo = new CodeOwnerCheckInfo();
    codeOwnerCheckInfo.isCodeOwner =
        (isCodeOwnershipAssignedToEmail.get()
                || isCodeOwnershipAssignedToAllUsers.get()
                || isFallbackCodeOwner)
            && isResolvable;
    codeOwnerCheckInfo.isResolvable = isResolvable;
    codeOwnerCheckInfo.codeOwnerConfigs = codeOwnerConfigFileInfosBuilder.build();
    codeOwnerCheckInfo.canReadRef = canReadRef;
    codeOwnerCheckInfo.canSeeChange = canSeeChange;
    codeOwnerCheckInfo.canApproveChange = canApproveChange;
    codeOwnerCheckInfo.codeOwnerConfigFilePaths =
        codeOwnerConfigFilePaths.stream().map(Path::toString).collect(toList());
    codeOwnerCheckInfo.isFallbackCodeOwner = isFallbackCodeOwner && isResolvable;
    codeOwnerCheckInfo.isDefaultCodeOwner = isDefaultCodeOwner.get();
    codeOwnerCheckInfo.isGlobalCodeOwner = isGlobalCodeOwner;
    codeOwnerCheckInfo.isOwnedByAllUsers = isCodeOwnershipAssignedToAllUsers.get();
    codeOwnerCheckInfo.annotations = sort(annotations);
    codeOwnerCheckInfo.debugLogs = messages;
    return Response.ok(codeOwnerCheckInfo);
  }

  private void validateInput(BranchResource branchResource)
      throws BadRequestException, AuthException, IOException, ConfigInvalidException,
          PermissionBackendException, ResourceNotFoundException {
    if (branchResource.getRevision().isEmpty()) {
      throw new ResourceNotFoundException(IdString.fromDecoded(branchResource.getName()));
    }

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
    if (change != null) {
      Optional<ChangeNotes> changeNotes = changeFinder.findOne(change);
      if (!changeNotes.isPresent()
          || !permissionBackend
              .currentUser()
              .change(changeNotes.get())
              .test(ChangePermission.READ)) {
        throw new BadRequestException(String.format("change %s not found", change));
      }
      if (!changeNotes.get().getChange().getDest().equals(branchResource.getBranchKey())) {
        throw new BadRequestException(
            "target branch of specified change must match branch from the request URL");
      }
      this.changeNotes = changeNotes.get();
    }
  }

  private boolean isGlobalCodeOwner(Project.NameKey projectName, String email) {
    return codeOwnersPluginConfiguration.getProjectConfig(projectName).getGlobalCodeOwners()
        .stream()
        .filter(cor -> cor.email().equals(email))
        .findAny()
        .isPresent();
  }

  private boolean isFallbackCodeOwner(Project.NameKey projectName) {
    FallbackCodeOwners fallbackCodeOwners =
        codeOwnersPluginConfiguration.getProjectConfig(projectName).getFallbackCodeOwners();
    switch (fallbackCodeOwners) {
      case NONE:
        return false;
      case ALL_USERS:
        return true;
    }
    throw new IllegalStateException(
        String.format(
            "unknown value %s for fallbackCodeOwners in project %s",
            fallbackCodeOwners.name(), projectName));
  }

  private OptionalResultWithMessages<CodeOwner> isResolvable() {
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
    if (resolveResult.isPresent()) {
      return OptionalResultWithMessages.create(resolveResult.get(), messages);
    }
    return OptionalResultWithMessages.createEmpty(messages);
  }

  private ImmutableList<String> sort(Set<String> set) {
    return set.stream().sorted().collect(toImmutableList());
  }
}
