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

package com.google.gerrit.plugins.codeowners.testing.backend;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportMode;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.backend.findowners.FindOwnersBackend;
import com.google.gerrit.plugins.codeowners.backend.proto.ProtoBackend;
import com.google.gerrit.plugins.codeowners.config.BackendConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Test utitility to format code owner configs using the syntax of the used code owner backend. */
@Singleton
public class TestCodeOwnerConfigFormatter {
  private final CodeOwnerBackend codeOwnerBackend;

  @Inject
  TestCodeOwnerConfigFormatter(BackendConfig backendConfig) {
    this.codeOwnerBackend = backendConfig.getDefaultBackend();
  }

  /**
   * Formats the given code owner config using the syntax of the used code owner backend.
   *
   * @param codeOwnerConfig the code owner config that should be formatted
   * @return the formatted code owner config
   */
  public String format(CodeOwnerConfig codeOwnerConfig) {
    if (codeOwnerBackend instanceof FindOwnersBackend) {
      return formatWithFindOwnersSyntax(codeOwnerConfig);
    } else if (codeOwnerBackend instanceof ProtoBackend) {
      return formatWithProtoSyntax(codeOwnerConfig);
    }

    throw new IllegalStateException(
        String.format("unknown code owner backend: %s", codeOwnerBackend.getClass().getName()));
  }

  /**
   * Formats the given code owner config using the find-owners syntax.
   *
   * @param codeOwnerConfig the code owner config that should be formatted
   * @return the formatted code owner config
   */
  private String formatWithFindOwnersSyntax(CodeOwnerConfig codeOwnerConfig) {
    StringBuilder b = new StringBuilder();
    if (codeOwnerConfig.ignoreParentCodeOwners()) {
      b.append("set noparent\n");
    }

    codeOwnerConfig
        .imports()
        .forEach(
            codeOwnerConfigReference -> {
              String keyword;
              if (codeOwnerConfigReference.importMode().equals(CodeOwnerConfigImportMode.ALL)) {
                keyword = "include";
              } else {
                keyword = "file:";
              }
              b.append(
                  String.format(
                      "%s %s%s%s\n",
                      keyword,
                      codeOwnerConfigReference
                          .project()
                          .map(Project.NameKey::get)
                          .map(projectName -> projectName + ":")
                          .orElse(""),
                      codeOwnerConfigReference.branch().map(branch -> branch + ":").orElse(""),
                      codeOwnerConfigReference.filePath()));
            });

    // global code owners
    for (String email :
        codeOwnerConfig.codeOwnerSets().stream()
            .filter(codeOwnerSet -> codeOwnerSet.pathExpressions().isEmpty())
            .flatMap(codeOwnerSet -> codeOwnerSet.codeOwners().stream())
            .map(CodeOwnerReference::email)
            .sorted()
            .distinct()
            .collect(toImmutableList())) {
      b.append(email).append('\n');
    }

    // per-file code owners
    for (CodeOwnerSet codeOwnerSet :
        codeOwnerConfig.codeOwnerSets().stream()
            .filter(codeOwnerSet -> !codeOwnerSet.pathExpressions().isEmpty())
            .collect(toImmutableList())) {
      if (codeOwnerSet.ignoreGlobalAndParentCodeOwners()) {
        b.append(
            String.format(
                "per-file %s=set noparent\n",
                codeOwnerSet.pathExpressions().stream().sorted().collect(joining(","))));
      }
      for (CodeOwnerConfigReference codeOwnerConfigReference : codeOwnerSet.imports()) {
        b.append(
            String.format(
                "per-file %s=file: %s%s%s\n",
                codeOwnerSet.pathExpressions().stream().sorted().collect(joining(",")),
                codeOwnerConfigReference
                    .project()
                    .map(Project.NameKey::get)
                    .map(projectName -> projectName + ":")
                    .orElse(""),
                codeOwnerConfigReference.branch().map(branch -> branch + ":").orElse(""),
                codeOwnerConfigReference.filePath()));
      }
      if (!codeOwnerSet.codeOwners().isEmpty()) {
        b.append(
            String.format(
                "per-file %s=%s\n",
                codeOwnerSet.pathExpressions().stream().sorted().collect(joining(",")),
                codeOwnerSet.codeOwners().stream()
                    .map(CodeOwnerReference::email)
                    .sorted()
                    .collect(joining(","))));
      }
    }

    return b.toString();
  }

  /**
   * Formats the given code owner config using the proto syntax.
   *
   * @param codeOwnerConfig the code owner config that should be formatted
   * @return the formatted code owner config
   */
  private String formatWithProtoSyntax(CodeOwnerConfig codeOwnerConfig) {
    StringBuilder b = new StringBuilder();
    b.append("owners_config {\n");
    if (codeOwnerConfig.ignoreParentCodeOwners()) {
      b.append("  ignore_parent_owners: true\n");
    }
    for (CodeOwnerSet codeOwnerSet : codeOwnerConfig.codeOwnerSets()) {
      if (!codeOwnerSet.codeOwners().isEmpty()) {
        b.append("  owner_sets {\n");
        for (String pathExpression : codeOwnerSet.pathExpressions()) {
          b.append(String.format("    path_expressions: \"%s\"\n", pathExpression));
        }
        for (CodeOwnerReference codeOwnerReference : codeOwnerSet.codeOwners()) {
          b.append(
              String.format(
                  "    owners {\n      email: \"%s\"\n    }\n", codeOwnerReference.email()));
        }
        b.append("  }\n");
      }
    }
    b.append("}\n");
    return b.toString();
  }
}
