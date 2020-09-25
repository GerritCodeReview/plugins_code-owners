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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigScanner;
import com.google.gerrit.plugins.codeowners.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.server.project.BranchResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.List;

/**
 * REST endpoint that lists the code owner config files in a branch.
 *
 * <p>This REST endpoint handles {@code GET
 * /projects/<project-name>/branches/<branch-name>/code_owners.config/} requests.
 *
 * <p>The implementation of this REST endpoint iterates over all code owner config files in the
 * branch. This means the expected performance of this REST endpoint is rather low and it should not
 * be used in any critical path where performance matters.
 */
@Singleton
public class GetCodeOwnerConfigFiles implements RestReadView<BranchResource> {
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final CodeOwnerConfigScanner codeOwnerConfigScanner;

  @Inject
  public GetCodeOwnerConfigFiles(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerConfigScanner codeOwnerConfigScanner) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.codeOwnerConfigScanner = codeOwnerConfigScanner;
  }

  @Override
  public Response<List<String>> apply(BranchResource resource) {
    CodeOwnerBackend codeOwnerBackend =
        codeOwnersPluginConfiguration.getBackend(resource.getBranchKey());
    ImmutableList.Builder<Path> codeOwnerConfigs = ImmutableList.builder();
    codeOwnerConfigScanner.visit(
        resource.getBranchKey(),
        codeOwnerConfig -> {
          codeOwnerConfigs.add(codeOwnerBackend.getFilePath(codeOwnerConfig.key()));
          return true;
        });
    return Response.ok(
        codeOwnerConfigs.build().stream().map(Path::toString).collect(toImmutableList()));
  }
}
