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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfiguration;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Class to compute the code owners for a path from a {@link CodeOwnerConfig}.
 *
 * <p>Code owners from inherited code owner configs are not considered.
 */
class PathCodeOwners {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Singleton
  public static class Factory {
    private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
    private final CodeOwners codeOwners;

    @Inject
    Factory(CodeOwnersPluginConfiguration codeOwnersPluginConfiguration, CodeOwners codeOwners) {
      this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
      this.codeOwners = codeOwners;
    }

    public PathCodeOwners create(CodeOwnerConfig codeOwnerConfig, Path absolutePath) {
      requireNonNull(codeOwnerConfig, "codeOwnerConfig");
      return new PathCodeOwners(
          codeOwners, codeOwnerConfig, absolutePath, getMatcher(codeOwnerConfig.key()));
    }

    public Optional<PathCodeOwners> create(
        CodeOwnerConfig.Key codeOwnerConfigKey, ObjectId revision, Path absolutePath) {
      return codeOwners
          .get(codeOwnerConfigKey, revision)
          .map(
              codeOwnerConfig ->
                  new PathCodeOwners(
                      codeOwners, codeOwnerConfig, absolutePath, getMatcher(codeOwnerConfigKey)));
    }

    /**
     * Gets the {@link PathExpressionMatcher} that should be used for the specified code owner
     * config.
     *
     * <p>Checks which {@link CodeOwnerBackend} is responsible for the specified code owner config
     * and retrieves the {@link PathExpressionMatcher} from it.
     *
     * <p>If the {@link CodeOwnerBackend} doesn't support path expressions and doesn't provide a
     * {@link PathExpressionMatcher} a {@link PathExpressionMatcher} that never matches is returned.
     * This way {@link CodeOwnerSet}s that have path expressions are ignored and will not have any
     * effect.
     *
     * @param codeOwnerConfigKey the key of the code owner config for which the path expression
     *     matcher should be returned
     * @return the {@link PathExpressionMatcher} that should be used for the specified code owner
     *     config
     */
    private PathExpressionMatcher getMatcher(CodeOwnerConfig.Key codeOwnerConfigKey) {
      CodeOwnerBackend codeOwnerBackend =
          codeOwnersPluginConfiguration.getBackend(codeOwnerConfigKey.branchNameKey());
      return codeOwnerBackend
          .getPathExpressionMatcher()
          .orElse((pathExpression, relativePath) -> false);
    }
  }

  private final CodeOwners codeOwners;
  private final CodeOwnerConfig codeOwnerConfig;
  private final Path path;
  private final PathExpressionMatcher pathExpressionMatcher;

  private CodeOwnerConfig resolvedCodeOwnerConfig;

  private PathCodeOwners(
      CodeOwners codeOwners,
      CodeOwnerConfig codeOwnerConfig,
      Path path,
      PathExpressionMatcher pathExpressionMatcher) {
    this.codeOwners = requireNonNull(codeOwners, "codeOwners");
    this.codeOwnerConfig = requireNonNull(codeOwnerConfig, "codeOwnerConfig");
    this.path = requireNonNull(path, "path");
    this.pathExpressionMatcher = requireNonNull(pathExpressionMatcher, "pathExpressionMatcher");

    checkState(path.isAbsolute(), "path %s must be absolute", path);
  }

  /** Returns the local code owner config. */
  public CodeOwnerConfig getCodeOwnerConfig() {
    return codeOwnerConfig;
  }

  /**
   * Gets the code owners from the code owner config that apply to the path.
   *
   * <p>Code owners from inherited code owner configs are not considered.
   *
   * @return the code owners of the path
   */
  public ImmutableSet<CodeOwnerReference> get() {
    return resolveCodeOwnerConfig().codeOwnerSets().stream()
        .flatMap(codeOwnerSet -> codeOwnerSet.codeOwners().stream())
        .collect(toImmutableSet());
  }

  /**
   * Whether parent code owners should be ignored for the path.
   *
   * @return whether parent code owners should be ignored for the path
   */
  public boolean ignoreParentCodeOwners() {
    return resolveCodeOwnerConfig().ignoreParentCodeOwners();
  }

  /**
   * Resolves the {@link #codeOwnerConfig}.
   *
   * <p>Resolving means that:
   *
   * <ul>
   *   <li>non-matching per-file code owner sets are dropped (since code owner sets that do not
   *       match the {@link #path} are not relevant)
   *   <li>imported code owner configs are loaded and replaced with the parts of them which should
   *       be imported (depends on the {@link CodeOwnerConfigImportMode}) and that are relevant for
   *       the {@link #path}
   *   <li>global code owner sets are dropped if any matching per-file code owner set has the
   *       ignoreGlobalAndParentCodeOwners flag set to {@code true} (since in this case global code
   *       owners should be ignored and then the global code owner sets are not relevant)
   *   <li>the ignoreParentCodeOwners flag is set to {@code true} if any matching per-file code
   *       owner set has the ignoreGlobalAndParentCodeOwners flag set to true (since in this case
   *       code owners from parent configurations should be ignored)
   * </ul>
   *
   * <p>When resolving imports cycles are detected and code owner configs that have been seen
   * already are not evaluated again.
   *
   * <p>Non-resolvable imports are silently ignored.
   *
   * <p>Imports that are loaded from the same project/branch as {@link #codeOwnerConfig} are
   * imported from the same revision from which {@link #codeOwnerConfig} was loaded. Imports that
   * are loaded from other projects/branches are imported from the current revision. If several
   * imports are loaded from the same project/branch we guarantee that they are all loaded from the
   * same revision, even if the current revision is changed by a concurrent request while the
   * resolution is being performed.
   *
   * <p>Imports from other projects are always loaded from the same branch from which the importing
   * code owner config was loaded.
   *
   * @return the resolved code owner config
   */
  private CodeOwnerConfig resolveCodeOwnerConfig() {
    if (this.resolvedCodeOwnerConfig != null) {
      return this.resolvedCodeOwnerConfig;
    }

    CodeOwnerConfig.Builder resolvedCodeOwnerConfigBuilder =
        CodeOwnerConfig.builder(codeOwnerConfig.key(), codeOwnerConfig.revision());

    // Add all data from the importing code owner config.
    resolvedCodeOwnerConfigBuilder.setIgnoreParentCodeOwners(
        codeOwnerConfig.ignoreParentCodeOwners());
    getGlobalCodeOwnerSets(codeOwnerConfig)
        .forEach(resolvedCodeOwnerConfigBuilder::addCodeOwnerSet);
    getMatchingPerFileCodeOwnerSets(codeOwnerConfig)
        .forEach(resolvedCodeOwnerConfigBuilder::addCodeOwnerSet);

    // To detect cyclic dependencies we keep track of all seen code owner configs.
    Set<CodeOwnerConfig.Key> seenCodeOwnerConfigs = new HashSet<>();
    seenCodeOwnerConfigs.add(codeOwnerConfig.key());

    // To ensure that code owner configs from the same project/branch are imported from the same
    // revision we keep track of the revisions.
    Map<BranchNameKey, ObjectId> revisionMap = new HashMap<>();
    revisionMap.put(codeOwnerConfig.key().branchNameKey(), codeOwnerConfig.revision());

    resolveImports(
        seenCodeOwnerConfigs, revisionMap, codeOwnerConfig, resolvedCodeOwnerConfigBuilder);

    CodeOwnerConfig resolvedCodeOwnerConfig = resolvedCodeOwnerConfigBuilder.build();

    // Remove global code owner sets if any per-file code owner set has the
    // ignoreGlobalAndParentCodeOwners flag set to true.
    // In this case also set ignoreParentCodeOwners to true, so that we do not need to inspect the
    // ignoreGlobalAndParentCodeOwners flags again.
    if (getMatchingPerFileCodeOwnerSets(resolvedCodeOwnerConfig)
        .anyMatch(codeOwnerSet -> codeOwnerSet.ignoreGlobalAndParentCodeOwners())) {
      resolvedCodeOwnerConfig =
          resolvedCodeOwnerConfig
              .toBuilder()
              .setIgnoreParentCodeOwners()
              .setCodeOwnerSets(
                  resolvedCodeOwnerConfig.codeOwnerSets().stream()
                      .filter(codeOwnerSet -> !codeOwnerSet.pathExpressions().isEmpty())
                      .collect(toImmutableSet()))
              .build();
    }

    this.resolvedCodeOwnerConfig = resolvedCodeOwnerConfig;
    return this.resolvedCodeOwnerConfig;
  }

  private void resolveImports(
      Set<CodeOwnerConfig.Key> seenCodeOwnerConfigs,
      Map<BranchNameKey, ObjectId> revisionMap,
      CodeOwnerConfig importingCodeOwnerConfig,
      CodeOwnerConfig.Builder resolvedCodeOwnerConfigBuilder) {
    for (CodeOwnerConfigReference codeOwnerConfigReference : importingCodeOwnerConfig.imports()) {
      CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
          createKeyForImportedCodeOwnerConfig(
              importingCodeOwnerConfig.key(), codeOwnerConfigReference);

      Optional<ObjectId> revision =
          Optional.ofNullable(revisionMap.get(keyOfImportedCodeOwnerConfig.branchNameKey()));

      Optional<CodeOwnerConfig> mayBeImportedCodeOwnerConfig =
          revision.isPresent()
              ? codeOwners.get(keyOfImportedCodeOwnerConfig, revision.get())
              : codeOwners.getFromCurrentRevision(keyOfImportedCodeOwnerConfig);

      if (!mayBeImportedCodeOwnerConfig.isPresent()) {
        logger.atWarning().log(
            "cannot resolve code owner config %s that is imported by code owner config %s"
                + " (revision = %s)",
            keyOfImportedCodeOwnerConfig,
            importingCodeOwnerConfig.key(),
            revision.map(ObjectId::name).orElse("current"));
        continue;
      }

      CodeOwnerConfig importedCodeOwnerConfig = mayBeImportedCodeOwnerConfig.get();
      CodeOwnerConfigImportMode importMode = codeOwnerConfigReference.importMode();

      if (!revisionMap.containsKey(keyOfImportedCodeOwnerConfig.branchNameKey())) {
        revisionMap.put(
            keyOfImportedCodeOwnerConfig.branchNameKey(), importedCodeOwnerConfig.revision());
      }

      if (importMode.importIgnoreParentCodeOwners()
          && importedCodeOwnerConfig.ignoreParentCodeOwners()) {
        resolvedCodeOwnerConfigBuilder.setIgnoreParentCodeOwners();
      }

      if (importMode.importGlobalCodeOwnerSets()) {
        getGlobalCodeOwnerSets(importedCodeOwnerConfig)
            .forEach(resolvedCodeOwnerConfigBuilder::addCodeOwnerSet);
      }

      if (importMode.importPerFileCodeOwnerSets()) {
        getMatchingPerFileCodeOwnerSets(importedCodeOwnerConfig)
            .forEach(resolvedCodeOwnerConfigBuilder::addCodeOwnerSet);
      }

      if (importMode.resolveImportsOfImport()
          && seenCodeOwnerConfigs.add(keyOfImportedCodeOwnerConfig)) {
        resolveImports(
            seenCodeOwnerConfigs,
            revisionMap,
            importedCodeOwnerConfig,
            resolvedCodeOwnerConfigBuilder);
      }
    }
  }

  private CodeOwnerConfig.Key createKeyForImportedCodeOwnerConfig(
      CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig,
      CodeOwnerConfigReference codeOwnerConfigReference) {
    // if the code owner config reference doesn't have a project, the imported code owner config
    // file is contained in the same project as the importing code owner config
    Project.NameKey project =
        codeOwnerConfigReference.project().orElse(keyOfImportingCodeOwnerConfig.project());

    // code owner configs are always imported from the same branch in which the importing code
    // owner config is stored
    String branch = keyOfImportingCodeOwnerConfig.branchNameKey().branch();

    // if the path of the imported code owner config is relative, it should be resolved against
    // the folder path of the importing code owner config
    Path folderPath =
        keyOfImportingCodeOwnerConfig
            .folderPath()
            .resolve(codeOwnerConfigReference.path())
            .normalize();

    return CodeOwnerConfig.Key.create(
        BranchNameKey.create(project, branch), folderPath, codeOwnerConfigReference.fileName());
  }

  private Stream<CodeOwnerSet> getGlobalCodeOwnerSets(CodeOwnerConfig codeOwnerConfig) {
    return codeOwnerConfig.codeOwnerSets().stream()
        .filter(codeOwnerSet -> codeOwnerSet.pathExpressions().isEmpty());
  }

  private Stream<CodeOwnerSet> getMatchingPerFileCodeOwnerSets(CodeOwnerConfig codeOwnerConfig) {
    return codeOwnerConfig.codeOwnerSets().stream()
        .filter(codeOwnerSet -> !codeOwnerSet.pathExpressions().isEmpty())
        .filter(codeOwnerSet -> matches(codeOwnerSet, getRelativePath(), pathExpressionMatcher));
  }

  private Path getRelativePath() {
    return codeOwnerConfig.relativize(path);
  }

  /**
   * Whether the given code owner set matches the given path.
   *
   * <p>A path matches the code owner set, if any of its path expressions matches the path.
   *
   * <p>The passed in code owner set must have at least one path expression.
   *
   * @param codeOwnerSet the code owner set for which it should be checked if it matches the given
   *     path, must have at least one path expression
   * @param relativePath path for which it should be checked whether it matches the given owner set;
   *     the path must be relative to the path in which the {@link CodeOwnerConfig} is stored that
   *     contains the code owner set; can be the path of a file or folder; the path may or may not
   *     exist
   * @param matcher the {@link PathExpressionMatcher} that should be used to match path expressions
   *     against the given path
   * @return whether this owner set matches the given path
   */
  @VisibleForTesting
  static boolean matches(
      CodeOwnerSet codeOwnerSet, Path relativePath, PathExpressionMatcher matcher) {
    requireNonNull(codeOwnerSet, "codeOwnerSet");
    requireNonNull(relativePath, "relativePath");
    requireNonNull(matcher, "matcher");
    checkState(!relativePath.isAbsolute(), "path %s must be relative", relativePath);
    checkState(
        !codeOwnerSet.pathExpressions().isEmpty(), "code owner set must have path expressions");

    return codeOwnerSet.pathExpressions().stream()
        .anyMatch(pathExpression -> matcher.matches(pathExpression, relativePath));
  }
}
