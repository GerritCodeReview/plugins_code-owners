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

package com.google.gerrit.plugins.codeowners.backend.config;

import static java.util.Objects.requireNonNull;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.PerThreadCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * The configuration of the code-owners plugin.
 *
 * <p>The global configuration of the code-owners plugin is stored in the {@code gerrit.config} file
 * in the {@code plugin.code-owners} subsection.
 *
 * <p>In addition there is configuration on project level that is stored in {@code
 * code-owners.config} files that are stored in the {@code refs/meta/config} branches of the
 * projects.
 *
 * <p>Parameters that are not set for a project are inherited from the parent project.
 */
@Singleton
public class CodeOwnersPluginConfiguration {
  public static final String SECTION_CODE_OWNERS = "codeOwners";

  private static final String GLOBAL_CONFIG_IDENTIFIER = "GLOBAL_CONFIG";

  private final CodeOwnersPluginGlobalConfigSnapshot.Factory
      codeOwnersPluginGlobalConfigSnapshotFactory;
  private final CodeOwnersPluginProjectConfigSnapshot.Factory
      codeOwnersPluginProjectConfigSnapshotFactory;

  @Inject
  CodeOwnersPluginConfiguration(
      CodeOwnersPluginGlobalConfigSnapshot.Factory codeOwnersPluginGlobalConfigSnapshotFactory,
      CodeOwnersPluginProjectConfigSnapshot.Factory codeOwnersPluginProjectConfigSnapshotFactory) {
    this.codeOwnersPluginGlobalConfigSnapshotFactory = codeOwnersPluginGlobalConfigSnapshotFactory;
    this.codeOwnersPluginProjectConfigSnapshotFactory =
        codeOwnersPluginProjectConfigSnapshotFactory;
  }

  /** Returns the global code-owner plugin configuration. */
  public CodeOwnersPluginGlobalConfigSnapshot getGlobalConfig() {
    return PerThreadCache.getOrCompute(
        PerThreadCache.Key.create(
            CodeOwnersPluginGlobalConfigSnapshot.class, GLOBAL_CONFIG_IDENTIFIER),
        () -> codeOwnersPluginGlobalConfigSnapshotFactory.create());
  }

  /**
   * Returns the code-owner plugin configuration for the given project.
   *
   * <p>Callers must ensure that the project of the specified branch exists. If the project doesn't
   * exist the call fails with {@link IllegalStateException}.
   */
  public CodeOwnersPluginProjectConfigSnapshot getProjectConfig(Project.NameKey projectName) {
    requireNonNull(projectName, "projectName");
    return PerThreadCache.getOrCompute(
        PerThreadCache.Key.create(CodeOwnersPluginProjectConfigSnapshot.class, projectName),
        () -> codeOwnersPluginProjectConfigSnapshotFactory.create(projectName));
  }
}
