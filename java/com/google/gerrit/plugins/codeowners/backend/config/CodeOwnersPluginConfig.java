// Copyright (C) 2021 The Android Open Source Project
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

import static com.google.gerrit.server.project.ProjectCache.noSuchProject;

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Arrays;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

/**
 * Class to read the {@code code-owners.config} file in the {@code refs/meta/config} branch of a
 * project with taking inherited config parameters from parent projects into account.
 *
 * <p>For inheriting config parameters from parent projects we rely on base config support in JGit's
 * {@link Config} class.
 *
 * <p>For single-value parameters (string, boolean, enum, int, long) this means:
 *
 * <ul>
 *   <li>If a parameter is not set, it is read from the parent project.
 *   <li>If a parameter is set, it overrides any value that is set in the parent project.
 * </ul>
 *
 * <p>For multi-value parameters (string list) this means:
 *
 * <ul>
 *   <li>If a parameter is not set, the values are read from the parent projects.
 *   <li>If any value for the parameter is set, it is added to the inherited value list (the
 *       inherited value list is extended).
 *   <li>The inherited value list cannot be overridden (this means the inherited values cannot be
 *       unset/overridden).
 * </ul>
 *
 * <p>Please note that this inheritance behavior is different from what {@link
 * com.google.gerrit.server.config.PluginConfigFactory} does. {@code PluginConfigFactory} has 2
 * modes:
 *
 * <ul>
 *   <li>merge = false: Inherited list values are overridden.
 *   <li>merge = true: Inherited list values are extended the same way as in this class, but for
 *       single-value parameters the inherited value from the parent project takes precedence.
 * </ul>
 *
 * <p>For the {@code code-owners.config} we want that:
 *
 * <ul>
 *   <li>Single-value parameters override inherited settings so that they can be controlled per
 *       project (e.g. whether validation of OWNERS files should be done).
 *   <li>Multi-value parameters cannot be overridden, but only extended (e.g. this allows to enforce
 *       global code owners or exempted users globally).
 * </ul>
 */
public class CodeOwnersPluginConfig {
  public interface Factory {
    CodeOwnersPluginConfig create(Project.NameKey projectName);
  }

  private static final String CONFIG_EXTENSION = ".config";

  private final String pluginName;
  private final ProjectCache projectCache;
  private final Project.NameKey projectName;
  private Config config;

  @Inject
  CodeOwnersPluginConfig(
      @PluginName String pluginName,
      ProjectCache projectCache,
      @Assisted Project.NameKey projectName) {
    this.pluginName = pluginName;
    this.projectCache = projectCache;
    this.projectName = projectName;
  }

  public Config get() {
    if (config == null) {
      config = load();
    }
    return config;
  }

  /**
   * Load the {@code code-owners.config} file of the project and sets all parent {@code
   * code-owners.config}s as base configs.
   *
   * <p>Fails with {@link IllegalStateException} if the project doesn't exist.
   */
  private Config load() {
    try {
      ProjectState projectState =
          projectCache.get(projectName).orElseThrow(noSuchProject(projectName));
      String fileName = pluginName + CONFIG_EXTENSION;

      Config mergedConfig = null;

      // Iterate in-order from All-Projects through the project hierarchy to this project. For each
      // project read the code-owners.config and set the parent code-owners.config as base config.
      for (ProjectState p : projectState.treeInOrder()) {
        Config currentConfig = p.getConfig(fileName).get();
        if (mergedConfig == null) {
          mergedConfig = currentConfig;
        } else {
          mergedConfig = createConfigWithBase(currentConfig, mergedConfig);
        }
      }
      return mergedConfig;
    } catch (NoSuchProjectException e) {
      throw new IllegalStateException(
          String.format(
              "cannot get %s plugin config for non-existing project %s", pluginName, projectName),
          e);
    }
  }

  /**
   * Creates a copy of the given {@code config} with the given {@code baseConfig} as base config.
   *
   * <p>JGit doesn't allow to set a base config on an existing {@link Config}. Hence create a new
   * (empty) config with the base config and then copy over all sections and subsection.
   *
   * @param config config that should be copied
   * @param baseConfig config that should be set as base config
   */
  private Config createConfigWithBase(Config config, Config baseConfig) {
    // Create a new Config with the parent Config as base config.
    Config configWithBase = new Config(baseConfig);

    // Copy all sections and subsections from the
    for (String section : config.getSections()) {
      for (String name : config.getNames(section)) {
        configWithBase.setStringList(
            section, null, name, Arrays.asList(config.getStringList(section, null, name)));
      }

      for (String subsection : config.getSubsections(section)) {
        Set<String> allNames = config.getNames(section, subsection);
        if (allNames.isEmpty()) {
          // Set empty subsection.
          configWithBase.setString(section, subsection, null, null);
        } else {
          for (String name : allNames) {
            configWithBase.setStringList(
                section,
                subsection,
                name,
                Arrays.asList(config.getStringList(section, subsection, name)));
          }
        }
      }
    }

    return configWithBase;
  }
}
