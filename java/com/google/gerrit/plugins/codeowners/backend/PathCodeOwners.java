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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Class to compute the code owners for a path from a {@link CodeOwnerConfig}.
 *
 * <p>Code owners from inherited code owner configs are not considered.
 */
public class PathCodeOwners {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Singleton
  public static class Factory {
    private final CodeOwnerMetrics codeOwnerMetrics;
    private final ProjectCache projectCache;
    private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
    private final CodeOwners codeOwners;

    @Inject
    Factory(
        CodeOwnerMetrics codeOwnerMetrics,
        ProjectCache projectCache,
        CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
        CodeOwners codeOwners) {
      this.codeOwnerMetrics = codeOwnerMetrics;
      this.projectCache = projectCache;
      this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
      this.codeOwners = codeOwners;
    }

    public PathCodeOwners createWithoutCache(CodeOwnerConfig codeOwnerConfig, Path absolutePath) {
      requireNonNull(codeOwnerConfig, "codeOwnerConfig");
      return new PathCodeOwners(
          codeOwnerMetrics,
          projectCache,
          /* transientCodeOwnerConfigCache= */ null,
          codeOwners,
          codeOwnerConfig,
          absolutePath,
          getMatcher(codeOwnerConfig.key()));
    }

    public Optional<PathCodeOwners> create(
        TransientCodeOwnerConfigCache transientCodeOwnerConfigCache,
        CodeOwnerConfig.Key codeOwnerConfigKey,
        ObjectId revision,
        Path absolutePath) {
      requireNonNull(transientCodeOwnerConfigCache, "transientCodeOwnerConfigCache");
      requireNonNull(codeOwnerConfigKey, "codeOwnerConfigKey");
      requireNonNull(revision, "revision");
      return transientCodeOwnerConfigCache
          .get(codeOwnerConfigKey, revision)
          .map(
              codeOwnerConfig ->
                  new PathCodeOwners(
                      codeOwnerMetrics,
                      projectCache,
                      transientCodeOwnerConfigCache,
                      codeOwners,
                      codeOwnerConfig,
                      absolutePath,
                      getMatcher(codeOwnerConfigKey)));
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
          codeOwnersPluginConfiguration
              .getProjectConfig(codeOwnerConfigKey.project())
              .getBackend(codeOwnerConfigKey.branchNameKey().branch());
      return codeOwnerBackend
          .getPathExpressionMatcher()
          .orElse((pathExpression, relativePath) -> false);
    }
  }

  private final CodeOwnerMetrics codeOwnerMetrics;
  private final ProjectCache projectCache;
  private final CodeOwnerConfigLoader codeOwnerConfigLoader;
  private final CodeOwners codeOwners;
  private final CodeOwnerConfig codeOwnerConfig;
  private final Path path;
  private final PathExpressionMatcher pathExpressionMatcher;

  private OptionalResultWithMessages<PathCodeOwnersResult> pathCodeOwnersResult;

  private PathCodeOwners(
      CodeOwnerMetrics codeOwnerMetrics,
      ProjectCache projectCache,
      @Nullable TransientCodeOwnerConfigCache transientCodeOwnerConfigCache,
      CodeOwners codeOwners,
      CodeOwnerConfig codeOwnerConfig,
      Path path,
      PathExpressionMatcher pathExpressionMatcher) {
    this.codeOwnerMetrics = requireNonNull(codeOwnerMetrics, "codeOwnerMetrics");
    this.projectCache = requireNonNull(projectCache, "projectCache");
    this.codeOwnerConfigLoader =
        transientCodeOwnerConfigCache != null ? transientCodeOwnerConfigCache : codeOwners;
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

  /** Returns the absolute path for which code owners were computed. */
  public Path getPath() {
    return path;
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
   * <p>When resolving imports, cycles are detected and code owner configs that have been seen
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
  public OptionalResultWithMessages<PathCodeOwnersResult> resolveCodeOwnerConfig() {
    if (this.pathCodeOwnersResult != null) {
      return this.pathCodeOwnersResult;
    }

    try (Timer0.Context ctx = codeOwnerMetrics.resolveCodeOwnerConfig.start()) {
      logger.atFine().log(
          "resolve code owners for %s from code owner config %s", path, codeOwnerConfig.key());

      List<String> messages = new ArrayList<>();

      // Create a code owner config builder to create the resolved code owner config (= code owner
      // config that is scoped to the path and which has imports resolved)
      CodeOwnerConfig.Builder resolvedCodeOwnerConfigBuilder =
          CodeOwnerConfig.builder(codeOwnerConfig.key(), codeOwnerConfig.revision());

      // Add all data from the original code owner config that is relevant for the path
      // (ignoreParentCodeOwners flag, global code owner sets and matching per-file code owner
      // sets). Effectively this means we are dropping all non-matching per-file rules.
      resolvedCodeOwnerConfigBuilder.setIgnoreParentCodeOwners(
          codeOwnerConfig.ignoreParentCodeOwners());
      getGlobalCodeOwnerSets(codeOwnerConfig)
          .forEach(resolvedCodeOwnerConfigBuilder::addCodeOwnerSet);
      for (CodeOwnerSet codeOwnerSet :
          getMatchingPerFileCodeOwnerSets(codeOwnerConfig).collect(toImmutableSet())) {
        messages.add(
            String.format(
                "per-file code owner set with path expressions %s matches",
                codeOwnerSet.pathExpressions()));
        resolvedCodeOwnerConfigBuilder.addCodeOwnerSet(codeOwnerSet);
      }

      // Resolve all global imports.
      Set<CodeOwnerConfigImport> globalImports = getGlobalImports(0, codeOwnerConfig);
      OptionalResultWithMessages<List<UnresolvedImport>> unresolvedGlobalImports =
          resolveImports(codeOwnerConfig.key(), globalImports, resolvedCodeOwnerConfigBuilder);
      messages.addAll(unresolvedGlobalImports.messages());

      // Remove all global code owner sets if any per-file code owner set has the
      // ignoreGlobalAndParentCodeOwners flag set to true (as in this case they are ignored and
      // hence not relevant).
      // In this case also set ignoreParentCodeOwners to true, so that we do not need to inspect the
      // ignoreGlobalAndParentCodeOwners flags on per-file code owner sets again, but can just rely
      // on the global ignoreParentCodeOwners flag.
      Optional<CodeOwnerSet> matchingPerFileCodeOwnerSetThatIgnoresGlobalAndParentCodeOwners =
          getMatchingPerFileCodeOwnerSets(resolvedCodeOwnerConfigBuilder.build())
              .filter(CodeOwnerSet::ignoreGlobalAndParentCodeOwners)
              .findAny();
      if (matchingPerFileCodeOwnerSetThatIgnoresGlobalAndParentCodeOwners.isPresent()) {
        logger.atFine().log("remove folder code owner sets and set ignoreParentCodeOwners to true");
        messages.add(
            String.format(
                "found matching per-file code owner set (with path expressions = %s) that ignores"
                    + " parent code owners, hence ignoring the folder code owners",
                matchingPerFileCodeOwnerSetThatIgnoresGlobalAndParentCodeOwners
                    .get()
                    .pathExpressions()));
        resolvedCodeOwnerConfigBuilder =
            resolvedCodeOwnerConfigBuilder
                .build()
                .toBuilder()
                .setIgnoreParentCodeOwners()
                .setCodeOwnerSets(
                    resolvedCodeOwnerConfigBuilder.codeOwnerSets().stream()
                        .filter(codeOwnerSet -> !codeOwnerSet.pathExpressions().isEmpty())
                        .collect(toImmutableSet()));
      }

      // Resolve per-file imports.
      Set<CodeOwnerConfigImport> perFileImports =
          getPerFileImports(
              0, codeOwnerConfig.key(), resolvedCodeOwnerConfigBuilder.codeOwnerSets());
      OptionalResultWithMessages<List<UnresolvedImport>> unresolvedPerFileImports =
          resolveImports(codeOwnerConfig.key(), perFileImports, resolvedCodeOwnerConfigBuilder);
      messages.addAll(unresolvedPerFileImports.messages());

      this.pathCodeOwnersResult =
          OptionalResultWithMessages.create(
              PathCodeOwnersResult.create(
                  path,
                  resolvedCodeOwnerConfigBuilder.build(),
                  Stream.concat(
                          unresolvedGlobalImports.get().stream(),
                          unresolvedPerFileImports.get().stream())
                      .collect(toImmutableList())),
              messages);
      logger.atFine().log("path code owners result = %s", pathCodeOwnersResult);
      return this.pathCodeOwnersResult;
    }
  }

  /**
   * Resolve the imports of the given code owner config.
   *
   * @param keyOfImportingCodeOwnerConfig the key of the importing code owner config
   * @param codeOwnerConfigImports the code owner configs that should be imported
   * @param resolvedCodeOwnerConfigBuilder the builder for the resolved code owner config
   * @return list of unresolved imports, empty list if all imports were successfully resolved
   */
  private OptionalResultWithMessages<List<UnresolvedImport>> resolveImports(
      CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig,
      Set<CodeOwnerConfigImport> codeOwnerConfigImports,
      CodeOwnerConfig.Builder resolvedCodeOwnerConfigBuilder) {
    ImmutableList.Builder<UnresolvedImport> unresolvedImports = ImmutableList.builder();
    StringBuilder messageBuilder = new StringBuilder();
    try (Timer0.Context ctx = codeOwnerMetrics.resolveCodeOwnerConfigImports.start()) {
      logger.atFine().log("resolve imports of codeOwnerConfig %s", keyOfImportingCodeOwnerConfig);

      // To detect cyclic dependencies we keep track of all seen code owner configs.
      Set<CodeOwnerConfig.Key> seenCodeOwnerConfigs = new HashSet<>();
      seenCodeOwnerConfigs.add(codeOwnerConfig.key());

      // To ensure that code owner configs from the same project/branch are imported from the same
      // revision we keep track of the revisions.
      Map<BranchNameKey, ObjectId> revisionMap = new HashMap<>();
      revisionMap.put(codeOwnerConfig.key().branchNameKey(), codeOwnerConfig.revision());

      Queue<CodeOwnerConfigImport> codeOwnerConfigsToImport = new ArrayDeque<>();
      codeOwnerConfigsToImport.addAll(codeOwnerConfigImports);
      if (!codeOwnerConfigsToImport.isEmpty()) {
        messageBuilder.append(
            String.format(
                "Code owner config %s imports:\n",
                keyOfImportingCodeOwnerConfig.format(codeOwners)));
      }
      while (!codeOwnerConfigsToImport.isEmpty()) {
        CodeOwnerConfigImport codeOwnerConfigImport = codeOwnerConfigsToImport.poll();
        messageBuilder.append(codeOwnerConfigImport.format());

        CodeOwnerConfigReference codeOwnerConfigReference =
            codeOwnerConfigImport.referenceToImportedCodeOwnerConfig();
        CodeOwnerConfig.Key keyOfImportedCodeOwnerConfig =
            createKeyForImportedCodeOwnerConfig(
                keyOfImportingCodeOwnerConfig, codeOwnerConfigReference);

        try (Timer0.Context ctx2 = codeOwnerMetrics.resolveCodeOwnerConfigImport.start()) {
          logger.atFine().log(
              "resolve import of code owner config %s", keyOfImportedCodeOwnerConfig);

          Optional<ProjectState> projectState =
              projectCache.get(keyOfImportedCodeOwnerConfig.project());
          if (!projectState.isPresent()) {
            unresolvedImports.add(
                UnresolvedImport.create(
                    codeOwnerConfigImport.importingCodeOwnerConfig(),
                    keyOfImportedCodeOwnerConfig,
                    codeOwnerConfigReference,
                    String.format(
                        "project %s not found", keyOfImportedCodeOwnerConfig.project().get())));
            messageBuilder.append(
                codeOwnerConfigImport.formatSubItem("failed to resolve (project not found)\n"));
            continue;
          }
          if (!projectState.get().statePermitsRead()) {
            unresolvedImports.add(
                UnresolvedImport.create(
                    codeOwnerConfigImport.importingCodeOwnerConfig(),
                    keyOfImportedCodeOwnerConfig,
                    codeOwnerConfigReference,
                    String.format(
                        "state of project %s doesn't permit read",
                        keyOfImportedCodeOwnerConfig.project().get())));
            messageBuilder.append(
                codeOwnerConfigImport.formatSubItem(
                    "failed to resolve (project state doesn't allow read)\n"));
            continue;
          }

          Optional<ObjectId> revision =
              Optional.ofNullable(revisionMap.get(keyOfImportedCodeOwnerConfig.branchNameKey()));
          logger.atFine().log(
              "import from %s",
              revision.isPresent() ? "revision " + revision.get().name() : "current revision");

          Optional<CodeOwnerConfig> mayBeImportedCodeOwnerConfig =
              revision.isPresent()
                  ? codeOwnerConfigLoader.get(keyOfImportedCodeOwnerConfig, revision.get())
                  : codeOwnerConfigLoader.getFromCurrentRevision(keyOfImportedCodeOwnerConfig);

          if (!mayBeImportedCodeOwnerConfig.isPresent()) {
            unresolvedImports.add(
                UnresolvedImport.create(
                    codeOwnerConfigImport.importingCodeOwnerConfig(),
                    keyOfImportedCodeOwnerConfig,
                    codeOwnerConfigReference,
                    String.format(
                        "code owner config does not exist (revision = %s)",
                        revision.map(ObjectId::name).orElse("current"))));
            messageBuilder.append(
                codeOwnerConfigImport.formatSubItem(
                    "failed to resolve (code owner config not found)\n"));
            continue;
          }

          CodeOwnerConfig importedCodeOwnerConfig = mayBeImportedCodeOwnerConfig.get();
          CodeOwnerConfigImportMode importMode = codeOwnerConfigReference.importMode();
          logger.atFine().log("import mode = %s", importMode.name());

          revisionMap.putIfAbsent(
              keyOfImportedCodeOwnerConfig.branchNameKey(), importedCodeOwnerConfig.revision());

          if (importMode.importIgnoreParentCodeOwners()
              && importedCodeOwnerConfig.ignoreParentCodeOwners()) {
            logger.atFine().log("import ignoreParentCodeOwners flag");
            resolvedCodeOwnerConfigBuilder.setIgnoreParentCodeOwners();
          }

          if (importMode.importGlobalCodeOwnerSets()) {
            logger.atFine().log("import global code owners");
            getGlobalCodeOwnerSets(importedCodeOwnerConfig)
                .forEach(resolvedCodeOwnerConfigBuilder::addCodeOwnerSet);
          }

          ImmutableSet<CodeOwnerSet> matchingPerFileCodeOwnerSets =
              getMatchingPerFileCodeOwnerSets(importedCodeOwnerConfig).collect(toImmutableSet());
          if (importMode.importPerFileCodeOwnerSets()) {
            logger.atFine().log("import per-file code owners");
            matchingPerFileCodeOwnerSets.forEach(
                codeOwnerSet -> {
                  messageBuilder.append(
                      codeOwnerConfigImport.formatSubItem(
                          String.format(
                              "per-file code owner set with path expressions %s matches\n",
                              codeOwnerSet.pathExpressions())));
                  resolvedCodeOwnerConfigBuilder.addCodeOwnerSet(codeOwnerSet);
                });
          }

          if (importMode.resolveImportsOfImport()
              && seenCodeOwnerConfigs.add(keyOfImportedCodeOwnerConfig)) {
            logger.atFine().log("resolve imports of imported code owner config");
            Set<CodeOwnerConfigImport> transitiveImports = new HashSet<>();
            transitiveImports.addAll(
                getGlobalImports(codeOwnerConfigImport.importLevel() + 1, importedCodeOwnerConfig));
            transitiveImports.addAll(
                getPerFileImports(
                    codeOwnerConfigImport.importLevel() + 1,
                    importedCodeOwnerConfig.key(),
                    matchingPerFileCodeOwnerSets));

            if (importMode == CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY) {
              // If only global code owners should be imported, transitive imports should also only
              // import global code owners, no matter which import mode is specified in the imported
              // code owner configs.
              logger.atFine().log(
                  "import transitive imports with mode %s",
                  CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY);
              transitiveImports =
                  transitiveImports.stream()
                      .map(
                          codeOwnerCfgImport ->
                              CodeOwnerConfigImport.create(
                                  codeOwnerCfgImport.importLevel(),
                                  codeOwnerCfgImport.importingCodeOwnerConfig(),
                                  CodeOwnerConfigReference.copyWithNewImportMode(
                                      codeOwnerCfgImport.referenceToImportedCodeOwnerConfig(),
                                      CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY),
                                  codeOwnerCfgImport.codeOwnerSet()))
                      .collect(toSet());
            }

            logger.atFine().log("transitive imports = %s", transitiveImports);
            codeOwnerConfigsToImport.addAll(transitiveImports);
          }
        }
      }
    }
    String message = messageBuilder.toString();
    if (message.endsWith("\n")) {
      message = message.substring(0, message.length() - 1);
    }
    return OptionalResultWithMessages.create(
        unresolvedImports.build(),
        !message.isEmpty() ? ImmutableList.of(message) : ImmutableList.of());
  }

  private Set<CodeOwnerConfigImport> getGlobalImports(
      int importLevel, CodeOwnerConfig codeOwnerConfig) {
    return codeOwnerConfig.imports().stream()
        .map(
            codeOwnerConfigReference ->
                CodeOwnerConfigImport.create(
                    importLevel, codeOwnerConfig.key(), codeOwnerConfigReference))
        .collect(toImmutableSet());
  }

  private Set<CodeOwnerConfigImport> getPerFileImports(
      int importLevel,
      CodeOwnerConfig.Key importingCodeOwnerConfig,
      Set<CodeOwnerSet> codeOwnerSets) {
    Set<CodeOwnerConfigImport> codeOwnerConfigImports = new HashSet<>();
    for (CodeOwnerSet codeOwnerSet : codeOwnerSets) {
      codeOwnerSet.imports().stream()
          .forEach(
              codeOwnerConfigReference ->
                  codeOwnerConfigImports.add(
                      CodeOwnerConfigImport.create(
                          importLevel,
                          importingCodeOwnerConfig,
                          codeOwnerConfigReference,
                          codeOwnerSet)));
    }
    return codeOwnerConfigImports;
  }

  public static CodeOwnerConfig.Key createKeyForImportedCodeOwnerConfig(
      CodeOwnerConfig.Key keyOfImportingCodeOwnerConfig,
      CodeOwnerConfigReference codeOwnerConfigReference) {
    // if the code owner config reference doesn't have a project, the imported code owner config
    // file is contained in the same project as the importing code owner config
    Project.NameKey project =
        codeOwnerConfigReference.project().orElse(keyOfImportingCodeOwnerConfig.project());

    // if the code owner config reference doesn't have a branch, the imported code owner config file
    // is imported from the same branch in which the importing code owner config is stored
    String branch =
        codeOwnerConfigReference
            .branch()
            .orElse(keyOfImportingCodeOwnerConfig.branchNameKey().branch());

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

  private static Stream<CodeOwnerSet> getGlobalCodeOwnerSets(CodeOwnerConfig codeOwnerConfig) {
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

  @AutoValue
  abstract static class CodeOwnerConfigImport {
    /**
     * The import level.
     *
     * <p>{@code 0} for direct import, {@code 1} if imported by a directly imported file, {@code 2},
     * if imported by a file that was imported by an directly imported file, etc.
     */
    public abstract int importLevel();

    /** The key of the code owner config that contains the import. */
    public abstract CodeOwnerConfig.Key importingCodeOwnerConfig();

    /** The reference to the imported code owner config */
    public abstract CodeOwnerConfigReference referenceToImportedCodeOwnerConfig();

    /** The code owner set that specified the import, empty if it is a global import. */
    public abstract Optional<CodeOwnerSet> codeOwnerSet();

    public String format() {
      if (codeOwnerSet().isPresent()) {
        return getPrefix()
            + String.format(
                "* %s (per-file import, import mode = %s, path expressions = %s)\n",
                referenceToImportedCodeOwnerConfig().format(),
                referenceToImportedCodeOwnerConfig().importMode(),
                codeOwnerSet().get().pathExpressions());
      }
      return getPrefix()
          + String.format(
              "* %s (global import, import mode = %s)\n",
              referenceToImportedCodeOwnerConfig().format(),
              referenceToImportedCodeOwnerConfig().importMode());
    }

    public String formatSubItem(String message) {
      return getPrefixForSubItem() + message;
    }

    private String getPrefix() {
      return getPrefix(importLevel());
    }

    private String getPrefixForSubItem() {
      return getPrefix(importLevel() + 1) + "* ";
    }

    private String getPrefix(int levels) {
      // 2 spaces per level
      //
      // String.format("%<num>s", "") creates a string with <num> spaces:
      // * '%' introduces a format sequence
      // * <num> means that the resulting string should be <num> characters long
      // * 's' is the character string format code, and ends the format sequence
      // * the second parameter for String.format, is the string that should be
      //   prefixed with as many spaces as are needed to make the string <num>
      //   characters long
      // * <num> must be > 0, hence we special case the handling of levels == 0
      return levels > 0 ? String.format("%" + (levels * 2) + "s", "") : "";
    }

    public static CodeOwnerConfigImport create(
        int importLevel,
        CodeOwnerConfig.Key importingCodeOwnerConfig,
        CodeOwnerConfigReference codeOwnerConfigReference) {
      return create(
          importLevel, importingCodeOwnerConfig, codeOwnerConfigReference, Optional.empty());
    }

    public static CodeOwnerConfigImport create(
        int importLevel,
        CodeOwnerConfig.Key importingCodeOwnerConfig,
        CodeOwnerConfigReference codeOwnerConfigReference,
        CodeOwnerSet codeOwnerSet) {
      return create(
          importLevel,
          importingCodeOwnerConfig,
          codeOwnerConfigReference,
          Optional.of(codeOwnerSet));
    }

    public static CodeOwnerConfigImport create(
        int importLevel,
        CodeOwnerConfig.Key importingCodeOwnerConfig,
        CodeOwnerConfigReference codeOwnerConfigReference,
        Optional<CodeOwnerSet> codeOwnerSet) {
      return new AutoValue_PathCodeOwners_CodeOwnerConfigImport(
          importLevel, importingCodeOwnerConfig, codeOwnerConfigReference, codeOwnerSet);
    }
  }
}
